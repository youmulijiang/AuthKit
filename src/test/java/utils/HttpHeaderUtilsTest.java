package utils;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HttpHeaderUtils 单元测试
 */
class HttpHeaderUtilsTest {

    @Test
    @DisplayName("removeAuthHeaders 应删除 Cookie 头及其 Cookie 参数")
    void removeAuthHeaders_shouldRemoveCookieHeaderAndCookieParameters() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader cookieHeader = mock(HttpHeader.class);
        ParsedHttpParameter cookieParam = mock(ParsedHttpParameter.class);
        HttpRequest afterHeaders = mock(HttpRequest.class);
        HttpRequest afterCookieParams = mock(HttpRequest.class);

        when(cookieHeader.name()).thenReturn("Cookie");
        when(original.headers()).thenReturn(List.of(cookieHeader));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of(cookieParam));
        when(original.withRemovedHeaders(List.of(cookieHeader))).thenReturn(afterHeaders);
        when(afterHeaders.withRemovedParameters(List.of(cookieParam))).thenReturn(afterCookieParams);

        HttpRequest result = HttpHeaderUtils.removeAuthHeaders(original, List.of());

        assertSame(afterCookieParams, result);
        verify(original).withRemovedHeaders(List.of(cookieHeader));
        verify(afterHeaders).withRemovedParameters(List.of(cookieParam));
    }

    @Test
    @DisplayName("removeAuthHeaders 配置非空时应同时移除内置关键字和配置匹配的认证头")
    void removeAuthHeaders_withConfiguredHeaders_shouldRemoveBothBuiltInAndConfigured() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader cookieHeader = mock(HttpHeader.class);
        HttpHeader customHeader = mock(HttpHeader.class);
        HttpRequest afterHeaders = mock(HttpRequest.class);

        when(cookieHeader.name()).thenReturn("Cookie");
        when(customHeader.name()).thenReturn("X-MyApp-Id");
        when(original.headers()).thenReturn(List.of(cookieHeader, customHeader));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());
        when(original.withRemovedHeaders(List.of(cookieHeader, customHeader))).thenReturn(afterHeaders);

        HttpRequest result = HttpHeaderUtils.removeAuthHeaders(original, List.of("MyApp"));

        assertSame(afterHeaders, result);
        verify(original).withRemovedHeaders(List.of(cookieHeader, customHeader));
    }

    @Test
    @DisplayName("removeAuthHeaders 应不区分大小写匹配鉴权头")
    void removeAuthHeaders_shouldMatchCaseInsensitively() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader upperAuth = mock(HttpHeader.class);
        HttpHeader mixedCustom = mock(HttpHeader.class);
        HttpRequest afterHeaders = mock(HttpRequest.class);

        when(upperAuth.name()).thenReturn("AUTHORIZATION");
        when(mixedCustom.name()).thenReturn("X-MyApp-Id");
        when(original.headers()).thenReturn(List.of(upperAuth, mixedCustom));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());
        when(original.withRemovedHeaders(List.of(upperAuth, mixedCustom))).thenReturn(afterHeaders);

        HttpRequest result = HttpHeaderUtils.removeAuthHeaders(original, List.of("MYAPP"));

        assertSame(afterHeaders, result);
        verify(original).withRemovedHeaders(List.of(upperAuth, mixedCustom));
    }

    @Test
    @DisplayName("extractAuthHeaders 配置非空时应同时提取内置关键字和配置匹配的认证头")
    void extractAuthHeaders_withConfiguredHeaders_shouldExtractBothBuiltInAndConfigured() {
        HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
        HttpRequest request = mock(HttpRequest.class);
        HttpHeader cookieHeader = mock(HttpHeader.class);
        HttpHeader customHeader = mock(HttpHeader.class);

        when(reqResp.request()).thenReturn(request);
        when(cookieHeader.name()).thenReturn("Cookie");
        when(cookieHeader.value()).thenReturn("sid=abc");
        when(customHeader.name()).thenReturn("X-MyApp-Id");
        when(customHeader.value()).thenReturn("custom");
        when(request.headers()).thenReturn(List.of(cookieHeader, customHeader));

        String result = HttpHeaderUtils.extractAuthHeaders(List.of(reqResp), List.of("MyApp"));

        assertEquals("Cookie: sid=abc\nX-MyApp-Id: custom", result);
    }
}
