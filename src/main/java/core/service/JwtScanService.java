package core.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import core.JwtPayloadService;
import core.JwtScanResult;
import core.JwtScanVariant;
import view.dialog.JwtScanDialog;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** JWT 主动扫描编排服务。 */
public class JwtScanService {

    private final MontoyaApi montoyaApi;
    private final JwtPayloadService payloadService;

    public JwtScanService(MontoyaApi montoyaApi, JwtPayloadService payloadService) {
        this.montoyaApi = montoyaApi;
        this.payloadService = payloadService;
    }

    public void executeScan(List<HttpRequestResponse> items, JwtScanDialog dialog,
                            int threadCount, boolean followRedirects,
                            AtomicReference<ExecutorService> scanPoolRef,
                            AtomicBoolean stopRequested) {
        List<ScanGroup> groups = new ArrayList<>();
        int total = 0;
        for (HttpRequestResponse item : items) {
            if (stopRequested.get()) return;
            if (item == null || item.request() == null) continue;
            List<JwtScanVariant> variants = payloadService.generateVariants(item.request());
            List<JwtScanVariant> currentGroup = null;
            for (JwtScanVariant variant : variants) {
                if (variant.baseline()) {
                    currentGroup = new ArrayList<>();
                    groups.add(new ScanGroup(currentGroup));
                }
                if (currentGroup != null) currentGroup.add(variant);
            }
            total += variants.size();
        }

        final int totalCount = total;
        SwingUtilities.invokeLater(() -> dialog.setTotal(totalCount));
        if (totalCount == 0) {
            SwingUtilities.invokeLater(dialog::finish);
            return;
        }

        RequestOptions options = RequestOptions.requestOptions().withRedirectionMode(
                followRedirects ? RedirectionMode.ALWAYS : RedirectionMode.NEVER);
        ExecutorService scanPool = Executors.newFixedThreadPool(Math.max(1, threadCount));
        scanPoolRef.set(scanPool);
        AtomicInteger index = new AtomicInteger(1);
        AtomicInteger completed = new AtomicInteger(0);

        for (ScanGroup group : groups) {
            scanPool.submit(() -> executeBaselineAndScheduleMutations(group, options, dialog, scanPool,
                    index, completed, totalCount, stopRequested));
        }
    }

    private void executeBaselineAndScheduleMutations(ScanGroup group, RequestOptions options,
                                                     JwtScanDialog dialog, ExecutorService scanPool,
                                                     AtomicInteger index, AtomicInteger completed,
                                                     int totalCount, AtomicBoolean stopRequested) {
        if (stopRequested.get() || group.variants().isEmpty()) return;
        JwtScanVariant baselineVariant = group.variants().get(0);
        HttpResponse baselineResponse = null;
        try {
            HttpRequestResponse response = send(baselineVariant, options, dialog, index, null);
            baselineResponse = response != null ? response.response() : null;
        } finally {
            markCompleted(dialog, scanPool, completed, totalCount, stopRequested);
        }

        final HttpResponse finalBaseline = baselineResponse;
        for (int i = 1; i < group.variants().size(); i++) {
            JwtScanVariant variant = group.variants().get(i);
            scanPool.submit(() -> {
                if (!stopRequested.get()) {
                    try {
                        send(variant, options, dialog, index, finalBaseline);
                    } finally {
                        markCompleted(dialog, scanPool, completed, totalCount, stopRequested);
                    }
                } else {
                    markCompleted(dialog, scanPool, completed, totalCount, stopRequested);
                }
            });
        }
    }

    private HttpRequestResponse send(JwtScanVariant variant, RequestOptions options,
                                     JwtScanDialog dialog, AtomicInteger index,
                                     HttpResponse baseline) {
        int resultIndex = index.getAndIncrement();
        JwtScanResult result;
        HttpRequestResponse response = null;
        long start = System.currentTimeMillis();
        try {
            response = montoyaApi.http().sendRequest(variant.request(), options);
            HttpResponse comparison = variant.baseline() ? response.response() : baseline;
            result = JwtScanResult.success(resultIndex, variant, response, comparison,
                    System.currentTimeMillis() - start);
        } catch (Exception ex) {
            result = JwtScanResult.failure(resultIndex, variant, ex);
        }
        JwtScanResult finalResult = result;
        SwingUtilities.invokeLater(() -> dialog.addResult(finalResult));
        return response;
    }

    private void markCompleted(JwtScanDialog dialog, ExecutorService scanPool,
                               AtomicInteger completed, int totalCount,
                               AtomicBoolean stopRequested) {
        if (completed.incrementAndGet() >= totalCount && !stopRequested.get()) {
            SwingUtilities.invokeLater(dialog::finish);
            scanPool.shutdown();
        }
    }

    private record ScanGroup(List<JwtScanVariant> variants) {
    }
}
