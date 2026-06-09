package core;

import burp.api.montoya.core.ToolType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FakeIpIntruderHttpHandlerTest {

    private final FakeIpIntruderHttpHandler handler = new FakeIpIntruderHttpHandler(new FakeIpService());

    @Test
    @DisplayName("仅 Intruder 请求应自动注入随机伪造 IP")
    void shouldApply_shouldOnlyMatchIntruder() {
        assertTrue(handler.shouldApply(ToolType.INTRUDER));
        assertFalse(handler.shouldApply(ToolType.REPEATER));
        assertFalse(handler.shouldApply(ToolType.PROXY));
        assertFalse(handler.shouldApply((ToolType) null));
    }
}