package controller;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import core.DiffService;
import core.HashService;
import core.RankService;
import core.RequestReplayService;
import model.AuthUserModel;
import model.CompareSampleModel;
import model.ConfigModel;
import model.MessageDataModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 核心控制器
 * 协调 HttpRequestHandler 捕获的请求与 RequestReplayService 的重放逻辑，
 * 管理 CompareSampleModel 数据集合，提供 Diff 功能。
 * UI 交互由外部（AuthKit 入口）通过回调绑定。
 */
public class AuthController {

    private final ConfigModel configModel;
    private final RequestReplayService replayService;
    private final DiffService diffService;

    /** 所有鉴权比较记录 */
    private final List<CompareSampleModel> samples;

    /** samples 读写锁 */
    private final Object samplesLock;

    /** 记录编号计数器 */
    private final AtomicInteger idCounter;

    /** 已处理请求的去重集合（key = method + url [+ body 哈希]） */
    private final Set<String> processedRequests;

    /** 去重集合容量上限：超过后整体清空，防止长时间挂机内存无限增长（代价是可能重复捕获） */
    private static final int MAX_PROCESSED_REQUESTS = 10_000;

    /**
     * 构造核心控制器
     *
     * @param configModel   插件配置模型
     * @param replayService 请求重放服务
     * @param diffService   Diff 服务
     */
    public AuthController(ConfigModel configModel,
                          RequestReplayService replayService,
                          DiffService diffService) {
        this.configModel = configModel;
        this.replayService = replayService;
        this.diffService = diffService;
        this.samples = new ArrayList<>();
        this.samplesLock = new Object();
        this.idCounter = new AtomicInteger(0);
        this.processedRequests = ConcurrentHashMap.newKeySet();
    }

    /**
     * 检查请求是否已处理过（去重，仅按 method + url）
     *
     * @param method HTTP 方法
     * @param url    请求 URL
     * @return true 表示是新请求（未处理过），false 表示重复请求
     */
    public boolean isNewRequest(String method, String url) {
        return markProcessed(buildKey(method, url, null));
    }

    /**
     * 检查请求是否已处理过（去重，键包含请求体哈希：同一 URL 不同 POST 体视为不同请求）
     *
     * @param method   HTTP 方法
     * @param url      请求 URL
     * @param bodyHash 请求体哈希（十六进制），无请求体传 null 或空串
     * @return true 表示是新请求（未处理过），false 表示重复请求
     */
    public boolean isNewRequest(String method, String url, String bodyHash) {
        return markProcessed(buildKey(method, url, bodyHash));
    }

    /**
     * 处理失败时撤销去重标记，允许该请求下次被重新捕获处理
     */
    public void forgetRequest(String method, String url, String bodyHash) {
        processedRequests.remove(buildKey(method, url, bodyHash));
    }

    /** 撤销仅按 method + url 记录的去重标记 */
    public void forgetRequest(String method, String url) {
        processedRequests.remove(buildKey(method, url, null));
    }

    private boolean markProcessed(String key) {
        if (processedRequests.size() >= MAX_PROCESSED_REQUESTS) {
            processedRequests.clear();
        }
        return processedRequests.add(key);
    }

    private static String buildKey(String method, String url, String bodyHash) {
        if (bodyHash == null || bodyHash.isEmpty()) {
            return method + " " + url;
        }
        return method + " " + url + " #" + bodyHash;
    }

    /**
     * 处理捕获的请求：记录原始数据、重放未授权请求、为每个启用的用户重放请求
     *
     * @param originalRequest  原始请求
     * @param originalResponse 原始响应
     * @param originalData     原始报文数据模型（已构建好）
     * @param users            鉴权用户列表
     * @return 构建好的 CompareSampleModel
     */
    public CompareSampleModel processRequest(HttpRequest originalRequest,
                                              HttpResponse originalResponse,
                                              MessageDataModel originalData,
                                              List<AuthUserModel> users) {
        int id = idCounter.incrementAndGet();
        CompareSampleModel sample = new CompareSampleModel(
                id, originalRequest.method(), originalRequest.url());

        // 1. 记录原始请求数据，Original 固定 100 分
        originalData.setRank(RankService.ORIGINAL_RANK);
        Map<String, MessageDataModel> collected = new LinkedHashMap<>();
        collected.put("Original", originalData);

        // 2. 重放未授权请求
        List<String> authHeaders = configModel.getAuthHeaders();
        HttpRequestResponse unauthReqResp = replayService.replayUnauthorized(
                originalRequest, authHeaders);
        MessageDataModel unauthData = replayService.buildMessageData(unauthReqResp);
        unauthData.setBody(unauthReqResp.response() != null
                ? unauthReqResp.response().bodyToString() : null);
        unauthData.setRank(RankService.calculateRank(originalData, unauthData));
        collected.put("Unauthorized", unauthData);

        // 3. 为每个启用的用户重放请求
        for (AuthUserModel user : users) {
            if (!user.isEnabled()) {
                continue;
            }
            HttpRequestResponse userReqResp = replayService.replay(originalRequest, user);
            MessageDataModel userData = replayService.buildMessageData(userReqResp);
            userData.setBody(userReqResp.response() != null
                    ? userReqResp.response().bodyToString() : null);
            userData.setRank(RankService.calculateRank(originalData, userData));
            collected.put(user.getName(), userData);
        }

        // 报文写入与样本登记在同一临界区完成，保证与 renameAuthUser/removeAuthUser 互斥
        synchronized (samplesLock) {
            collected.forEach(sample::putMessageData);
            samples.add(sample);
        }
        return sample;
    }

    /**
     * 用户重命名后同步历史样本：将所有已捕获样本中的旧鉴权对象名更新为新名称，
     * 避免数据表/对比面板按新名称取不到历史报文。
     */
    public void renameAuthUser(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) {
            return;
        }
        synchronized (samplesLock) {
            for (CompareSampleModel sample : samples) {
                sample.renameAuthName(oldName, newName);
            }
        }
    }

    /**
     * 用户删除后同步历史样本：移除所有已捕获样本中该鉴权对象的报文数据。
     */
    public void removeAuthUser(String name) {
        if (name == null) {
            return;
        }
        synchronized (samplesLock) {
            for (CompareSampleModel sample : samples) {
                sample.removeAuthName(name);
            }
        }
    }

    /**
     * 执行文本 Diff 比较
     *
     * @param original 原始文本
     * @param modified 修改后的文本
     * @return Diff 结果
     */
    public String diff(String original, String modified) {
        return diffService.diff(original, modified);
    }

    /**
     * 获取所有鉴权比较记录
     *
     * @return 不可修改的记录列表
     */
    public List<CompareSampleModel> getSamples() {
        synchronized (samplesLock) {
            return List.copyOf(samples);
        }
    }

    /**
     * 根据索引获取指定记录
     *
     * @param index 记录索引
     * @return 对应的 CompareSampleModel，索引越界返回 null
     */
    public CompareSampleModel getSample(int index) {
        synchronized (samplesLock) {
            if (index < 0 || index >= samples.size()) {
                return null;
            }
            return samples.get(index);
        }
    }

    /** 获取插件配置模型 */
    public ConfigModel getConfigModel() {
        return configModel;
    }

    /** 清空所有记录 */
    public void clearAll() {
        synchronized (samplesLock) {
            samples.clear();
        }
        idCounter.set(0);
        processedRequests.clear();
    }
}

