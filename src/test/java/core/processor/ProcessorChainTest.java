package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ProcessorChain 单元测试
 */
class ProcessorChainTest {

    @Test
    @DisplayName("空处理器链应返回原始请求不变")
    void emptyChain_shouldReturnOriginalRequest() {
        ProcessorChain chain = new ProcessorChain(List.of());
        HttpRequest request = mock(HttpRequest.class);
        AuthUserModel user = new AuthUserModel("User1");

        HttpRequest result = chain.execute(request, user);
        assertSame(request, result);
    }

    @Test
    @DisplayName("启用的处理器应被执行")
    void enabledProcessor_shouldBeExecuted() {
        HttpRequest original = mock(HttpRequest.class);
        HttpRequest processed = mock(HttpRequest.class);
        AuthUserModel user = new AuthUserModel("User1");

        RequestProcessor processor = mock(RequestProcessor.class);
        when(processor.isEnabled(user)).thenReturn(true);
        when(processor.process(original, user)).thenReturn(processed);

        ProcessorChain chain = new ProcessorChain(List.of(processor));
        HttpRequest result = chain.execute(original, user);

        assertSame(processed, result);
        verify(processor).process(original, user);
    }

    @Test
    @DisplayName("禁用的处理器应被跳过")
    void disabledProcessor_shouldBeSkipped() {
        HttpRequest original = mock(HttpRequest.class);
        AuthUserModel user = new AuthUserModel("User1");

        RequestProcessor processor = mock(RequestProcessor.class);
        when(processor.isEnabled(user)).thenReturn(false);

        ProcessorChain chain = new ProcessorChain(List.of(processor));
        HttpRequest result = chain.execute(original, user);

        assertSame(original, result);
        verify(processor, never()).process(any(), any());
    }

    @Test
    @DisplayName("多个处理器应按顺序链式执行")
    void multipleProcessors_shouldExecuteInOrder() {
        HttpRequest req0 = mock(HttpRequest.class);
        HttpRequest req1 = mock(HttpRequest.class);
        HttpRequest req2 = mock(HttpRequest.class);
        AuthUserModel user = new AuthUserModel("User1");

        RequestProcessor p1 = mock(RequestProcessor.class);
        when(p1.isEnabled(user)).thenReturn(true);
        when(p1.process(req0, user)).thenReturn(req1);

        RequestProcessor p2 = mock(RequestProcessor.class);
        when(p2.isEnabled(user)).thenReturn(true);
        when(p2.process(req1, user)).thenReturn(req2);

        ProcessorChain chain = new ProcessorChain(List.of(p1, p2));
        HttpRequest result = chain.execute(req0, user);

        assertSame(req2, result);
        verify(p1).process(req0, user);
        verify(p2).process(req1, user);
    }

    @Test
    @DisplayName("混合启用/禁用处理器应正确执行")
    void mixedProcessors_shouldSkipDisabledOnes() {
        HttpRequest req0 = mock(HttpRequest.class);
        HttpRequest req1 = mock(HttpRequest.class);
        AuthUserModel user = new AuthUserModel("User1");

        RequestProcessor enabled = mock(RequestProcessor.class);
        when(enabled.isEnabled(user)).thenReturn(true);
        when(enabled.process(req0, user)).thenReturn(req1);

        RequestProcessor disabled = mock(RequestProcessor.class);
        when(disabled.isEnabled(user)).thenReturn(false);

        ProcessorChain chain = new ProcessorChain(List.of(disabled, enabled));
        HttpRequest result = chain.execute(req0, user);

        assertSame(req1, result);
        verify(disabled, never()).process(any(), any());
        verify(enabled).process(req0, user);
    }
}

