package core;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PacketSourceService 单元测试：候选过滤、评分与来源读取。
 */
class PacketSourceServiceTest {

    /** 构造响应 mock（状态码 + 响应体）。注意：须在 when(...) 之前调用，避免 Mockito 嵌套 stubbing */
    private static HttpResponse mockResponse(int statusCode, String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn((short) statusCode);
        ByteArray bytes = mock(ByteArray.class);
        when(bytes.length()).thenReturn(body.length());
        when(response.body()).thenReturn(bytes);
        when(response.bodyToString()).thenReturn(body);
        return response;
    }

    /** 构造 Proxy 历史条目 mock */
    private static ProxyHttpRequestResponse proxyItem(String method, String url,
                                                      int statusCode, String body) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn(method);
        when(request.url()).thenReturn(url);
        HttpResponse resp = mockResponse(statusCode, body);
        ProxyHttpRequestResponse item = mock(ProxyHttpRequestResponse.class);
        when(item.request()).thenReturn(request);
        when(item.response()).thenReturn(resp);
        return item;
    }

    /** 构造站点地图条目 mock */
    private static HttpRequestResponse siteMapItem(String method, String url) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn(method);
        when(request.url()).thenReturn(url);
        HttpResponse resp = mockResponse(200, "{}");
        HttpRequestResponse item = mock(HttpRequestResponse.class);
        when(item.request()).thenReturn(request);
        when(item.response()).thenReturn(resp);
        return item;
    }

    @Test
    @DisplayName("read 排除静态资源候选")
    void read_excludesStaticResources() {
        PacketSourceService service = new PacketSourceService(
                () -> List.of(
                        proxyItem("GET", "https://x/app.js", 200, ""),
                        proxyItem("GET", "https://x/logo.png", 200, ""),
                        proxyItem("GET", "https://x/api/users/1", 200, "{}")),
                List::of, (req, resp) -> mock(HttpRequestResponse.class));

        List<PacketSourceService.Candidate> candidates =
                service.read(PacketSourceService.Source.PROXY_HISTORY, "");

        assertEquals(1, candidates.size());
        assertEquals("https://x/api/users/1", candidates.get(0).url());
    }

    @Test
    @DisplayName("read 关键词过滤大小写不敏感")
    void read_keywordFilterIgnoreCase() {
        PacketSourceService service = new PacketSourceService(
                () -> List.of(
                        proxyItem("GET", "https://x/api/Users/1", 200, "{}"),
                        proxyItem("GET", "https://x/api/orders/9", 200, "{}")),
                List::of, (req, resp) -> mock(HttpRequestResponse.class));

        List<PacketSourceService.Candidate> candidates =
                service.read(PacketSourceService.Source.PROXY_HISTORY, "users");

        assertEquals(1, candidates.size());
        assertTrue(candidates.get(0).url().contains("Users"));
    }

    @Test
    @DisplayName("read 按越权测试价值评分降序排列")
    void read_sortedByScore() {
        PacketSourceService service = new PacketSourceService(
                () -> List.of(
                        proxyItem("GET", "https://x/health", 500, ""),
                        proxyItem("GET", "https://x/api/orders/9", 200, "{\"id\":9}"),
                        proxyItem("GET", "https://x/api/search?q=a", 200, "{}")),
                List::of, (req, resp) -> mock(HttpRequestResponse.class));

        List<PacketSourceService.Candidate> candidates =
                service.read(PacketSourceService.Source.PROXY_HISTORY, "");

        assertEquals("https://x/api/orders/9", candidates.get(0).url());
        assertEquals("https://x/api/search?q=a", candidates.get(1).url());
        assertEquals("https://x/health", candidates.get(2).url());
    }

    @Test
    @DisplayName("read 按来源选择对应数据源")
    void read_usesRequestedSource() {
        PacketSourceService service = new PacketSourceService(
                () -> List.of(proxyItem("GET", "https://proxy/api/1", 200, "{}")),
                () -> List.of(siteMapItem("GET", "https://sitemap/api/2")),
                (req, resp) -> mock(HttpRequestResponse.class));

        assertEquals("https://proxy/api/1",
                service.read(PacketSourceService.Source.PROXY_HISTORY, "").get(0).url());
        assertEquals("https://sitemap/api/2",
                service.read(PacketSourceService.Source.SITEMAP, "").get(0).url());
    }

    @Test
    @DisplayName("read 数据源异常时返回空列表")
    void read_supplierThrows_empty() {
        PacketSourceService service = new PacketSourceService(
                () -> {
                    throw new IllegalStateException("burp unavailable");
                },
                () -> {
                    throw new IllegalStateException("burp unavailable");
                },
                (req, resp) -> mock(HttpRequestResponse.class));

        assertTrue(service.read(PacketSourceService.Source.PROXY_HISTORY, "").isEmpty());
        assertTrue(service.read(PacketSourceService.Source.SITEMAP, "").isEmpty());
    }

    @Test
    @DisplayName("hasObjectIdentifier 识别路径数字段与 id 类参数")
    void hasObjectIdentifier_detectsIds() {
        assertTrue(PacketSourceService.hasObjectIdentifier("https://x/api/users/123"));
        assertTrue(PacketSourceService.hasObjectIdentifier("https://x/api/users/123?tab=a"));
        assertTrue(PacketSourceService.hasObjectIdentifier("https://x/api/user?id=42"));
        assertTrue(PacketSourceService.hasObjectIdentifier("https://x/api/user?user_id=42"));
        assertFalse(PacketSourceService.hasObjectIdentifier("https://x/api/users/list"));
        assertFalse(PacketSourceService.hasObjectIdentifier("https://x/api/123abc"));
        assertFalse(PacketSourceService.hasObjectIdentifier(null));
    }

    @Test
    @DisplayName("score 对静态方法与服务端错误降权")
    void score_penalizesLowValue() {
        int idScore = PacketSourceService.score("GET", "https://x/api/users/1", 200, true);
        int optionScore = PacketSourceService.score("OPTIONS", "https://x/", 500, false);
        assertTrue(idScore > optionScore);
        assertTrue(optionScore < 0);
    }
}
