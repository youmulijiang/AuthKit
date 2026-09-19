package core;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import core.processor.ProcessorChain;
import model.AuthUserModel;
import model.MessageDataModel;
import utils.HttpHeaderUtils;

import java.util.List;

/**
 * 请求重放服务
 * 负责将原始请求经过处理器链变换后发送，并将响应封装为 MessageDataModel。
 */
public class RequestReplayService {

    private final Http http;
    private final ProcessorChain processorChain;

    /**
     * 构造请求重放服务
     *
     * @param http           Montoya HTTP API
     * @param processorChain 处理器责任链
     */
    public RequestReplayService(Http http, ProcessorChain processorChain) {
        this.http = http;
        this.processorChain = processorChain;
    }

    /**
     * 使用指定鉴权用户的配置重放请求
     * 请求经过 ProcessorChain 处理后发送。
     *
     * @param originalRequest 原始请求
     * @param user            鉴权用户配置
     * @return 请求响应对
     */
    public HttpRequestResponse replay(HttpRequest originalRequest, AuthUserModel user) {
        HttpRequest processedRequest = processorChain.execute(originalRequest, user);
        return http.sendRequest(processedRequest);
    }

    /**
     * 以未授权方式重放请求（移除所有认证头后发送）
     * <p>
     * 配置非空时只按配置关键字移除；配置为空时回退到内置鉴权头关键字。
     *
     * @param originalRequest 原始请求
     * @param authHeaders     配置的认证头名称关键字列表（可为空）
     * @return 请求响应对
     */
    public HttpRequestResponse replayUnauthorized(HttpRequest originalRequest, List<String> authHeaders) {
        HttpRequest current = HttpHeaderUtils.removeAuthHeaders(originalRequest, authHeaders);
        return http.sendRequest(current);
    }

    /**
     * 直接发送原始请求（不做任何处理），返回响应
     *
     * @param request 原始请求
     * @return HTTP 响应
     */
    public HttpResponse sendRaw(HttpRequest request) {
        HttpRequestResponse reqResp = http.sendRequest(request);
        return reqResp.response();
    }

    /**
     * 从请求响应对中构建 MessageDataModel
     *
     * @param requestResponse 请求响应对
     * @return 报文数据模型
     */
    public MessageDataModel buildMessageData(HttpRequestResponse requestResponse) {
        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();

        String requestStr = request.toString();
        String responseStr = response.toString();
        int statusCode = response.statusCode();
        int length = response.bodyToString().length();
        int hash = HashService.hash(response.bodyToString());
        int attributeCount = response.attributes().size();

        // 从 HttpRequestResponse 获取 annotations notes
        String note = "";
        if (requestResponse.annotations() != null && requestResponse.annotations().hasNotes()) {
            note = requestResponse.annotations().notes();
        }

        // 从响应头获取 Content-Type
        String contentType = response.headerValue("Content-Type");

        MessageDataModel model = new MessageDataModel(requestStr, responseStr, statusCode, length, hash, request, response);
        model.setBody(response.bodyToString());
        model.setAttributeCount(attributeCount);
        model.setNote(note);
        model.setContentType(contentType != null ? contentType : "");
        return model;
    }
}

