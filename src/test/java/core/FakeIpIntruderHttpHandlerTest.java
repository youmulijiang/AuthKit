package core;

import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FakeIpIntruderHttpHandlerTest {

    private final FakeIpService fakeIpService = new FakeIpService();
    private final FakeIpIntruderHttpHandler handler = new FakeIpIntruderHttpHandler(fakeIpService);

    @Test
    @DisplayName("仅 Intruder 来源才可能注入随机伪造 IP")
    void shouldApply_shouldOnlyMatchIntruderTool() {
        assertTrue(handler.shouldApply(ToolType.INTRUDER));
        assertFalse(handler.shouldApply(ToolType.REPEATER));
        assertFalse(handler.shouldApply(ToolType.PROXY));
        assertFalse(handler.shouldApply((ToolType) null));
    }

    @Test
    @DisplayName("未标记的 Intruder 请求不应注入伪造 IP")
    void shouldApply_unmarkedIntruderRequest_shouldReturnFalse() {
        HttpRequestToBeSent request = mockIntruderRequest(false);

        assertFalse(handler.shouldApply(request));
    }

    @Test
    @DisplayName("带爆破标记的 Intruder 请求应注入伪造 IP")
    void shouldApply_markedIntruderRequest_shouldReturnTrue() {
        HttpRequestToBeSent request = mockIntruderRequest(true);

        assertTrue(handler.shouldApply(request));
    }

    @Test
    @DisplayName("带爆破标记的 Repeater 请求不应注入伪造 IP")
    void shouldApply_markedRepeaterRequest_shouldReturnFalse() {
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        ToolSource toolSource = mock(ToolSource.class);
        when(request.toolSource()).thenReturn(toolSource);
        when(toolSource.toolType()).thenReturn(ToolType.REPEATER);
        when(request.hasHeader(FakeIpService.BRUTE_FORCE_MARKER_HEADER, FakeIpService.BRUTE_FORCE_MARKER_VALUE))
                .thenReturn(true);

        assertFalse(handler.shouldApply(request));
    }

    private HttpRequestToBeSent mockIntruderRequest(boolean marked) {
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        ToolSource toolSource = mock(ToolSource.class);
        when(request.toolSource()).thenReturn(toolSource);
        when(toolSource.toolType()).thenReturn(ToolType.INTRUDER);
        when(request.hasHeader(FakeIpService.BRUTE_FORCE_MARKER_HEADER, FakeIpService.BRUTE_FORCE_MARKER_VALUE))
                .thenReturn(marked);
        return request;
    }
}