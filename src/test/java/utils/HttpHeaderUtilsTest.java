package utils;

import burp.api.montoya.http.message.HttpHeader;
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
}
