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
 * 仅处理由 AuthKit「随机 IP 爆破」标记过的 Intruder 请求，
 * 每个数据包在发送前注入一组新的随机伪造 IP（含 X-Forwarded-For）。
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
        HttpRequest unmarked = fakeIpService.unmarkIntruderBruteForce(requestToBeSent);
        HttpRequest updatedRequest = fakeIpService.addRandomIpHeaders(unmarked);
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
        return shouldApply(toolType) && fakeIpService.isMarkedForIntruderBruteForce(requestToBeSent);
    }

    boolean shouldApply(ToolType toolType) {
        return ToolType.INTRUDER.equals(toolType);
    }
}
