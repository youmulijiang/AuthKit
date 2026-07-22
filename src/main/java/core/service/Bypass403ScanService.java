package core.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import core.Bypass403PayloadService;
import core.Bypass403RequestVariant;
import core.Bypass403ScanResult;
import view.dialog.Bypass403ScanDialog;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 403 绕过扫描执行服务（M层领域服务）
 * <p>
 * 负责生成请求变体、并发发包、回填扫描结果到对话框。对话框的创建与事件绑定
 * 由上层控制器完成，本类只承担扫描执行逻辑。
 */
public class Bypass403ScanService {

    private final MontoyaApi montoyaApi;
    private final Bypass403PayloadService payloadService;

    public Bypass403ScanService(MontoyaApi montoyaApi, Bypass403PayloadService payloadService) {
        this.montoyaApi = montoyaApi;
        this.payloadService = payloadService;
    }

    /**
     * 执行 403 绕过扫描
     *
     * @param items           选中的请求响应对
     * @param dialog          扫描结果对话框
     * @param threadCount     并发线程数
     * @param followRedirects 是否跟随重定向
     * @param scanPoolRef     扫描线程池引用持有（供关闭时 shutdown）
     * @param stopRequested   停止标志
     */
    public void executeScan(List<HttpRequestResponse> items, Bypass403ScanDialog dialog,
                            int threadCount, boolean followRedirects,
                            AtomicReference<ExecutorService> scanPoolRef,
                            AtomicBoolean stopRequested) {
        // 生成所有变体
        List<Bypass403RequestVariant> variants = new ArrayList<>();
        for (HttpRequestResponse item : items) {
            if (stopRequested.get()) return;
            if (item != null && item.request() != null) {
                variants.addAll(payloadService.generateVariants(item.request()));
            }
        }
        if (stopRequested.get()) return;

        final int totalCount = variants.size();
        SwingUtilities.invokeLater(() -> dialog.setTotal(totalCount));

        if (totalCount == 0) {
            SwingUtilities.invokeLater(dialog::finish);
            return;
        }

        // 构建请求选项（重定向控制）
        RequestOptions requestOptions = RequestOptions.requestOptions()
                .withRedirectionMode(followRedirects ? RedirectionMode.ALWAYS : RedirectionMode.NEVER);

        // 创建用户配置线程数的扫描线程池
        ExecutorService scanPool = Executors.newFixedThreadPool(threadCount);
        scanPoolRef.set(scanPool);

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger indexCounter = new AtomicInteger(1);

        for (Bypass403RequestVariant variant : variants) {
            final int idx = indexCounter.getAndIncrement();
            scanPool.submit(() -> {
                if (!stopRequested.get()) {
                    Bypass403ScanResult result;
                    long start = System.currentTimeMillis();
                    try {
                        HttpRequestResponse response =
                                montoyaApi.http().sendRequest(variant.request(), requestOptions);
                        result = Bypass403ScanResult.success(idx, variant, response,
                                System.currentTimeMillis() - start);
                    } catch (Exception ex) {
                        result = Bypass403ScanResult.failure(idx, variant, ex);
                    }
                    Bypass403ScanResult finalResult = result;
                    SwingUtilities.invokeLater(() -> dialog.addResult(finalResult));
                }
                if (completed.incrementAndGet() >= totalCount && !stopRequested.get()) {
                    SwingUtilities.invokeLater(dialog::finish);
                    scanPool.shutdown();
                }
            });
        }
    }
}
