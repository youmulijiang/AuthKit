package core.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import core.IdorPayloadService;
import core.IdorScanResult;
import core.IdorScanVariant;
import utils.LogUtils;
import view.dialog.IdorScanDialog;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IDOR 越权扫描执行服务（M层领域服务）
 * <p>
 * 通过三种策略（删鉴权、数字参数变形、历史参数不同值）检测越权漏洞。
 * 负责基线响应获取、变体生成、并发发包、哈希染色结果回填。
 */
public class IdorScanService {

    private final MontoyaApi montoyaApi;
    private final IdorPayloadService payloadService;

    public IdorScanService(MontoyaApi montoyaApi, IdorPayloadService payloadService) {
        this.montoyaApi = montoyaApi;
        this.payloadService = payloadService;
    }

    /**
     * 执行 IDOR 扫描
     *
     * @param items         选中的请求响应对（取第一条作为基线）
     * @param dialog        扫描结果对话框
     * @param threadCount   并发线程数
     * @param scanPoolRef   扫描线程池引用持有（供关闭时 shutdown）
     * @param stopRequested 停止标志
     */
    public void executeScan(List<HttpRequestResponse> items, IdorScanDialog dialog,
                            int threadCount, AtomicReference<ExecutorService> scanPoolRef,
                            AtomicBoolean stopRequested) {
        try {
            HttpRequestResponse item = items.get(0);
            HttpRequest baseRequest = item.request();
            if (baseRequest == null) return;

            // 获取原始响应（如果没有就先发包获取基线）
            HttpResponse baseResponse = item.response();
            if (baseResponse == null) {
                HttpRequestResponse sent = montoyaApi.http().sendRequest(baseRequest);
                baseResponse = sent != null ? sent.response() : null;
            }

            // 使用 Java hashCode 计算原始响应体哈希
            String originalBody = baseResponse != null && baseResponse.bodyToString() != null
                    ? baseResponse.bodyToString() : "";
            final int originalHash = originalBody.hashCode();

            // 获取代理历史供历史参数策略使用
            List<ProxyHttpRequestResponse> history = montoyaApi.proxy().history();

            // 生成所有 IDOR 变体
            List<IdorScanVariant> variants = payloadService.generateVariants(baseRequest, history);
            if (stopRequested.get()) return;

            final int totalCount = variants.size();
            SwingUtilities.invokeLater(() -> dialog.setTotal(totalCount));

            if (totalCount == 0) {
                SwingUtilities.invokeLater(dialog::finish);
                return;
            }

            // 创建用户配置线程数的扫描线程池
            ExecutorService scanPool = Executors.newFixedThreadPool(threadCount);
            scanPoolRef.set(scanPool);

            AtomicInteger completed = new AtomicInteger(0);
            AtomicInteger indexCounter = new AtomicInteger(1);

            for (IdorScanVariant variant : variants) {
                final int idx = indexCounter.getAndIncrement();
                scanPool.submit(() -> {
                    if (!stopRequested.get()) {
                        IdorScanResult result;
                        long start = System.currentTimeMillis();
                        try {
                            HttpRequestResponse response =
                                    montoyaApi.http().sendRequest(variant.request());
                            result = IdorScanResult.success(idx, variant, response, originalHash,
                                    System.currentTimeMillis() - start);
                        } catch (Exception ex) {
                            result = IdorScanResult.failure(idx, variant, ex);
                        }
                        IdorScanResult finalResult = result;
                        SwingUtilities.invokeLater(() -> dialog.addResult(finalResult));
                    }
                    if (completed.incrementAndGet() >= totalCount && !stopRequested.get()) {
                        SwingUtilities.invokeLater(dialog::finish);
                        scanPool.shutdown();
                    }
                });
            }
        } catch (Exception ex) {
            LogUtils.INSTANCE.error("IDOR 扫描出错", ex);
        }
    }
}
