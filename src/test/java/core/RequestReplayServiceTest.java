package core;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import core.processor.ProcessorChain;
import model.AuthUserModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RequestReplayService 单元测试
 */
class RequestReplayServiceTest {

    private Http http;
    private ProcessorChain processorChain;
    private RequestReplayService service;

    @BeforeEach
    void setUp() {
        http = mock(Http.class);
        processorChain = mock(ProcessorChain.class);
        service = new RequestReplayService(http, processorChain);
    }

    @Test
    @DisplayName("replay 应通过 ProcessorChain 处理请求后发送")
    void replay_shouldProcessAndSend() {
        HttpRequest original = mock(HttpRequest.class);
        HttpRequest processed = mock(HttpRequest.class);
        AuthUserModel user = new AuthUserModel("User1");

        when(processorChain.execute(original, user)).thenReturn(processed);

        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(processed)).thenReturn(mockResponse);

        HttpRequestResponse result = service.replay(original, user);

        assertSame(mockResponse, result);
        verify(processorChain).execute(original, user);
        verify(http).sendRequest(processed);
    }

    @Test
    @DisplayName("replayUnauthorized 应移除认证头后发送")
    void replayUnauthorized_shouldRemoveHeadersAndSend() {
        HttpRequest original = mock(HttpRequest.class);
        HttpRequest afterCookie = mock(HttpRequest.class);
        HttpRequest afterAuth = mock(HttpRequest.class);
        HttpRequest afterToken = mock(HttpRequest.class);

        when(original.withRemovedHeader("Cookie")).thenReturn(afterCookie);
        when(afterCookie.withRemovedHeader("Authorization")).thenReturn(afterAuth);
        when(afterAuth.withRemovedHeader("Token")).thenReturn(afterToken);

        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(afterToken)).thenReturn(mockResponse);

        List<String> authHeaders = List.of("Cookie", "Authorization", "Token");
        HttpRequestResponse result = service.replayUnauthorized(original, authHeaders);

        assertSame(mockResponse, result);
        verify(original).withRemovedHeader("Cookie");
        verify(afterCookie).withRemovedHeader("Authorization");
        verify(afterAuth).withRemovedHeader("Token");
        verify(http).sendRequest(afterToken);
    }

    @Test
    @DisplayName("replayUnauthorized 空认证头列表应直接发送原始请求")
    void replayUnauthorized_emptyHeaders_shouldSendOriginal() {
        HttpRequest original = mock(HttpRequest.class);
        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(original)).thenReturn(mockResponse);

        HttpRequestResponse result = service.replayUnauthorized(original, List.of());

        assertSame(mockResponse, result);
        verify(http).sendRequest(original);
    }
}

