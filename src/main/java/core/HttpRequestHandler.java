package core;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import model.ConfigModel;
import utils.LogUtils;

import java.net.URI;
import java.util.function.BiConsumer;

/**
 * HTTP 请求拦截处理器
 * 实现 Montoya HttpHandler 接口，在响应接收时根据配置过滤请求，
 * 通过过滤的请求交给回调函数处理（由 AuthController 注册）。
 */
public class HttpRequestHandler implements HttpHandler {

    private final ConfigModel configModel;
    private final BiConsumer<HttpRequest, HttpResponse> onRequestCaptured;

    /**
     * 构造 HTTP 请求处理器
     *
     * @param configModel       插件配置模型
     * @param onRequestCaptured 请求捕获回调（参数: 原始请求, 原始响应）
     */
    public HttpRequestHandler(ConfigModel configModel,
                              BiConsumer<HttpRequest, HttpResponse> onRequestCaptured) {
        this.configModel = configModel;
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
        if (!configModel.isEnabled()) {
            return false;
        }
        if (configModel.shouldFilterMethod(request.method())) {
            return false;
        }
        if (configModel.isDomainFilterEnabled()) {
            String host = extractHost(request.url());
            if (configModel.shouldFilterDomain(host)) {
                return false;
            }
        }
        if (configModel.shouldFilterPath(request.path())) {
            return false;
        }
        if (configModel.shouldFilterExtension(request.path())) {
            return false;
        }
        if (configModel.shouldFilterStatusCode(statusCode)) {
            return false;
        }
        return true;
    }

    /**
     * 处理响应：过滤检查 + 触发回调
     * 过滤掉插件自身发出的请求（EXTENSIONS），避免循环发包。
     */
    private void processResponse(HttpResponseReceived responseReceived) {
        // 过滤掉插件自身重放的请求，防止无限循环
        if (responseReceived.toolSource().isFromTool(ToolType.EXTENSIONS)) {
            return;
        }
        HttpRequest request = responseReceived.initiatingRequest();
        if (shouldProcess(request, responseReceived.statusCode())) {
            onRequestCaptured.accept(request, responseReceived);
        }
    }

    /**
     * 从 URL 中提取主机名
     */
    private String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }
}

