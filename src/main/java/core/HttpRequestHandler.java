package core;

import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import core.service.RequestFilter;
import utils.LogUtils;

import java.util.function.BiConsumer;

/**
 * HTTP 请求拦截处理器
 * 实现 Montoya HttpHandler 接口，在响应接收时根据 {@link RequestFilter} 过滤请求，
 * 通过过滤的请求交给回调函数处理（由 AuthController 注册）。
 */
public class HttpRequestHandler implements HttpHandler {

    private final RequestFilter requestFilter;
    private final BiConsumer<HttpRequest, HttpResponseReceived> onRequestCaptured;

    /**
     * 构造 HTTP 请求处理器
     *
     * @param requestFilter     请求过滤策略
     * @param onRequestCaptured 请求捕获回调（参数: 原始请求, 拦截响应）
     */
    public HttpRequestHandler(RequestFilter requestFilter,
                              BiConsumer<HttpRequest, HttpResponseReceived> onRequestCaptured) {
        this.requestFilter = requestFilter;
        this.onRequestCaptured = onRequestCaptured;
    }

    /**
     * 请求发送前：直接放行，不做修改
     */
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    /**
     * 响应接收后：根据配置过滤，通过过滤的请求触发回调
     */
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        try {
            processResponse(responseReceived);
        } catch (Exception e) {
            LogUtils.INSTANCE.error("Error processing response: " + e.getMessage());
        }
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    /**
     * 判断请求是否应被处理（通过所有过滤条件）
     * 此方法可被单元测试直接调用。
     *
     * @param request    原始请求
     * @param statusCode 响应状态码
     * @return true 表示应处理，false 表示应过滤
     */
    public boolean shouldProcess(HttpRequest request, int statusCode) {
        return shouldProcess(request, statusCode, null);
    }

    /**
     * 判断请求是否应被处理（通过所有过滤条件）
     *
     * @param request    原始请求
     * @param statusCode 响应状态码
     * @param toolType   请求来源 ToolType
     * @return true 表示应处理，false 表示应过滤
     */
    public boolean shouldProcess(HttpRequest request, int statusCode, ToolType toolType) {
        return requestFilter.shouldProcess(request, statusCode, toolType);
    }

    /**
     * 处理响应：过滤检查 + 触发回调
     * 过滤掉插件自身发出的请求（EXTENSIONS），避免循环发包。
     */
    private void processResponse(HttpResponseReceived responseReceived) {
        HttpRequest request = responseReceived.initiatingRequest();
        ToolSource toolSource = responseReceived.toolSource();
        ToolType toolType = toolSource != null ? toolSource.toolType() : null;
        if (shouldProcess(request, responseReceived.statusCode(), toolType)) {
            onRequestCaptured.accept(request, responseReceived);
        }
    }
}
