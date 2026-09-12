package core;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    @DisplayName("随机 IP 爆破应只写入标记头，避免整轮爆破共用同一个 XFF")
    void addBruteForceHeaders_shouldMarkRequestInsteadOfWritingFixedIp() {
        HttpRequest request = mock(HttpRequest.class);
        when(request.hasHeader(FakeIpService.BRUTE_FORCE_MARKER_HEADER)).thenReturn(false);
        when(request.withAddedHeader(FakeIpService.BRUTE_FORCE_MARKER_HEADER, FakeIpService.BRUTE_FORCE_MARKER_VALUE))
                .thenReturn(request);

        HttpRequest updated = service.addBruteForceHeaders(request);

        assertSame(request, updated);
        verify(request).withAddedHeader(FakeIpService.BRUTE_FORCE_MARKER_HEADER, FakeIpService.BRUTE_FORCE_MARKER_VALUE);
        verify(request, never()).withAddedHeader(eq("X-Forwarded-For"), anyString());
    }

    @Test
    @DisplayName("应生成 X-Forwarded-For 请求头 payload")
    void buildXffHeaderPayload_shouldReturnHeaderLine() {
        String payload = service.buildXffHeaderPayload("8.8.8.8");

        assertEquals("X-Forwarded-For: 8.8.8.8", payload);
    }

    @Test
    @DisplayName("连续两次随机伪造 IP 应使用不同地址")
    void addRandomIpHeaders_shouldUseDifferentIpEachCall() {
        java.security.SecureRandom sequential = new java.security.SecureRandom() {
            private int calls;

            @Override
            public int nextInt(int bound) {
                // 第一次选网段 0、偏移 0；第二次选网段 1、偏移 1，保证两次 IP 不同
                return calls++ < 2 ? 0 : 1;
            }
        };
        FakeIpService sequentialService = new FakeIpService(sequential);
        HttpRequest first = mock(HttpRequest.class);
        HttpRequest second = mock(HttpRequest.class);
        when(first.hasHeader(anyString())).thenReturn(false);
        when(second.hasHeader(anyString())).thenReturn(false);
        when(first.withAddedHeader(anyString(), anyString())).thenReturn(first);
        when(second.withAddedHeader(anyString(), anyString())).thenReturn(second);

        sequentialService.addRandomIpHeaders(first);
        sequentialService.addRandomIpHeaders(second);

        org.mockito.ArgumentCaptor<String> firstIp = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> secondIp = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(first).withAddedHeader(eq("X-Forwarded-For"), firstIp.capture());
        verify(second).withAddedHeader(eq("X-Forwarded-For"), secondIp.capture());
        assertTrue(sequentialService.isValidIpv4(firstIp.getValue()));
        assertTrue(sequentialService.isValidIpv4(secondIp.getValue()));
        assertNotEquals(firstIp.getValue(), secondIp.getValue());
    }

    private HttpRequest mockRequest(String expectedIp) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.hasHeader(anyString())).thenReturn(false);
        when(request.withAddedHeader(anyString(), eq(expectedIp))).thenReturn(request);
        return request;
    }
}
