package core;

import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.requests.HttpRequest;

/**
 * Intruder 爆破请求伪造 IP 处理器。
 * 当请求来源为 Intruder 时，在发送前为每个数据包注入一组随机伪造 IP 请求头。
 */
public class FakeIpIntruderHttpHandler implements HttpHandler {

    private final FakeIpService fakeIpService;

    public FakeIpIntruderHttpHandler(FakeIpService fakeIpService) {
        this.fakeIpService = fakeIpService;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        if (!shouldApply(requestToBeSent)) {
            return RequestToBeSentAction.continueWith(requestToBeSent);
        }
        HttpRequest updatedRequest = fakeIpService.addRandomIpHeaders(requestToBeSent);
        return RequestToBeSentAction.continueWith(updatedRequest);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    boolean shouldApply(HttpRequestToBeSent requestToBeSent) {
        if (requestToBeSent == null) {
            return false;
        }
        ToolSource toolSource = requestToBeSent.toolSource();
        ToolType toolType = toolSource != null ? toolSource.toolType() : null;
        return shouldApply(toolType);
    }

    boolean shouldApply(ToolType toolType) {
        return ToolType.INTRUDER.equals(toolType);
    }
}
