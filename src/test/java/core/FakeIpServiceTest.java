package core;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FakeIpServiceTest {

    private final FakeIpService service = new FakeIpService();

    @Test
    @DisplayName("应向请求添加全部伪造 IP 请求头")
    void addFakeIpHeaders_shouldAddAllHeaders() {
        HttpRequest request = mockRequest("8.8.8.8");

        HttpRequest updated = service.addFakeIpHeaders(request, "8.8.8.8");

        assertSame(request, updated);
        verify(request).withAddedHeader("X-Forwarded-For", "8.8.8.8");
        verify(request).withAddedHeader("True-Client-IP", "8.8.8.8");
        verify(request).withAddedHeader("X-Custom-IP-Authorization", "8.8.8.8");
        verify(request, times(service.getFakeIpHeaders().size())).withAddedHeader(anyString(), eq("8.8.8.8"));
    }

    @Test
    @DisplayName("随机 IP 应返回合法 IPv4")
    void randomPublicIp_shouldReturnValidIpv4() {
        for (int i = 0; i < 20; i++) {
            assertTrue(service.isValidIpv4(service.randomPublicIp()));
        }
    }

    @Test
    @DisplayName("非法 IPv4 应拒绝注入")
    void addFakeIpHeaders_shouldRejectInvalidIpv4() {
        HttpRequest request = mock(HttpRequest.class);

        assertThrows(IllegalArgumentException.class,
                () -> service.addFakeIpHeaders(request, "999.1.1.1"));
    }

    @Test
    @DisplayName("随机 IP 爆破应把伪造 IP 写入请求头而不是 payload 位置")
    void addBruteForceHeaders_shouldUseHeaderValue() {
        HttpRequest request = mock(HttpRequest.class);
        when(request.hasHeader(anyString())).thenReturn(false);
        when(request.withAddedHeader(anyString(), anyString())).thenReturn(request);

        HttpRequest updated = service.addBruteForceHeaders(request);

        assertSame(request, updated);
        verify(request).withAddedHeader(eq("X-Forwarded-For"), argThat(service::isValidIpv4));
    }

    private HttpRequest mockRequest(String expectedIp) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.hasHeader(anyString())).thenReturn(false);
        when(request.withAddedHeader(anyString(), eq(expectedIp))).thenReturn(request);
        return request;
    }
}
