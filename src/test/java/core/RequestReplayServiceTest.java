package core;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameterType;
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
    @DisplayName("replayUnauthorized 应按关键字移除鉴权头后发送")
    void replayUnauthorized_shouldRemoveKeywordAuthHeadersAndSend() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader cookie = mock(HttpHeader.class);
        when(cookie.name()).thenReturn("Cookie");
        when(original.headers()).thenReturn(List.of(cookie));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());

        HttpRequest afterKeywordRemove = mock(HttpRequest.class);
        when(original.withRemovedHeaders(List.of(cookie))).thenReturn(afterKeywordRemove);

        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(afterKeywordRemove)).thenReturn(mockResponse);

        HttpRequestResponse result = service.replayUnauthorized(original, List.of());

        assertSame(mockResponse, result);
        verify(original).withRemovedHeaders(List.of(cookie));
        verify(http).sendRequest(afterKeywordRemove);
    }

    @Test
    @DisplayName("replayUnauthorized 应同时移除内置关键字和配置匹配的认证头")
    void replayUnauthorized_shouldRemoveBothBuiltInAndConfiguredHeaders() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader authHeader = mock(HttpHeader.class);
        HttpHeader customHeader = mock(HttpHeader.class);
        when(authHeader.name()).thenReturn("Authorization");
        when(customHeader.name()).thenReturn("X-MyApp-Id");
        when(original.headers()).thenReturn(List.of(authHeader, customHeader));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());

        HttpRequest afterRemove = mock(HttpRequest.class);
        when(original.withRemovedHeaders(List.of(authHeader, customHeader))).thenReturn(afterRemove);

        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(afterRemove)).thenReturn(mockResponse);

        HttpRequestResponse result = service.replayUnauthorized(original, List.of("MyApp"));

        assertSame(mockResponse, result);
        verify(original).withRemovedHeaders(List.of(authHeader, customHeader));
        verify(http).sendRequest(afterRemove);
    }

    @Test
    @DisplayName("replayUnauthorized 应按配置关键字包含匹配移除请求头")
    void replayUnauthorized_shouldRemoveHeadersContainingConfiguredKeyword() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader customHeader = mock(HttpHeader.class);
        when(customHeader.name()).thenReturn("X-MyApp-Id");
        when(original.headers()).thenReturn(List.of(customHeader));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());

        HttpRequest afterCustom = mock(HttpRequest.class);
        when(original.withRemovedHeaders(List.of(customHeader))).thenReturn(afterCustom);

        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(afterCustom)).thenReturn(mockResponse);

        HttpRequestResponse result = service.replayUnauthorized(original, List.of("myapp"));

        assertSame(mockResponse, result);
        verify(original).withRemovedHeaders(List.of(customHeader));
        verify(http).sendRequest(afterCustom);
    }

    @Test
    @DisplayName("replayUnauthorized 无鉴权头且配置为空时应直接发送原始请求")
    void replayUnauthorized_noAuthHeaders_shouldSendOriginal() {
        HttpRequest original = mock(HttpRequest.class);
        when(original.headers()).thenReturn(List.of());
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());
        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(http.sendRequest(original)).thenReturn(mockResponse);

        HttpRequestResponse result = service.replayUnauthorized(original, List.of());

        assertSame(mockResponse, result);
        verify(http).sendRequest(original);
    }
}
