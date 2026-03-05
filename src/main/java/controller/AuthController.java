package controller;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import core.DiffService;
import core.HashService;
import core.RequestReplayService;
import model.AuthUserModel;
import model.CompareSampleModel;
import model.ConfigModel;
import model.MessageDataModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /** 记录编号计数器 */
    private final AtomicInteger idCounter;

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
        this.idCounter = new AtomicInteger(0);
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

        // 1. 记录原始请求数据
        sample.putMessageData("Original", originalData);

        // 2. 重放未授权请求
        List<String> authHeaders = configModel.getAuthHeaders();
        HttpRequestResponse unauthReqResp = replayService.replayUnauthorized(
                originalRequest, authHeaders);
        MessageDataModel unauthData = replayService.buildMessageData(unauthReqResp);
        sample.putMessageData("Unauthorized", unauthData);

        // 3. 为每个启用的用户重放请求
        for (AuthUserModel user : users) {
            if (!user.isEnabled()) {
                continue;
            }
            HttpRequestResponse userReqResp = replayService.replay(originalRequest, user);
            MessageDataModel userData = replayService.buildMessageData(userReqResp);
            sample.putMessageData(user.getName(), userData);
        }

        samples.add(sample);
        return sample;
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
        return Collections.unmodifiableList(samples);
    }

    /**
     * 根据索引获取指定记录
     *
     * @param index 记录索引
     * @return 对应的 CompareSampleModel，索引越界返回 null
     */
    public CompareSampleModel getSample(int index) {
        if (index < 0 || index >= samples.size()) {
            return null;
        }
        return samples.get(index);
    }

    /** 获取插件配置模型 */
    public ConfigModel getConfigModel() {
        return configModel;
    }

    /** 清空所有记录 */
    public void clearAll() {
        samples.clear();
        idCounter.set(0);
    }
}

