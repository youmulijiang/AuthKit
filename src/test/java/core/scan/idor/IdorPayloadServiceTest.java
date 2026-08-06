package core.scan.idor;

import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class IdorPayloadServiceTest {

    private final IdorPayloadService service = new IdorPayloadService();

    /** 过滤出 technique == "numeric-path" 的变体 */
    private List<IdorScanVariant> pathVariants(List<IdorScanVariant> all) {
        return all.stream().filter(v -> "numeric-path".equals(v.technique())).collect(Collectors.toList());
    }

    private List<IdorScanVariant> commonParamVariants(List<IdorScanVariant> all) {
        return all.stream().filter(v -> "common-param".equals(v.technique())).collect(Collectors.toList());
    }

    private HttpRequest mockRequest(String path) {
        HttpRequest base = mock(HttpRequest.class);
        HttpRequest mutated = mock(HttpRequest.class);
        when(base.pathWithoutQuery()).thenReturn(path);
        when(base.query()).thenReturn("");
        when(base.withPath(anyString())).thenReturn(mutated);
        when(base.parameters()).thenReturn(List.of());
        when(base.httpService()).thenReturn(null);
        // removeAuthHeaders() calls headers() — return empty list so it's a no-op
        when(base.headers()).thenReturn(List.of());
        return base;
    }

    private HttpRequest mockRequest(String path, String host) {
        HttpRequest base = mockRequest(path);
        HttpService service = mock(HttpService.class);
        when(service.host()).thenReturn(host);
        when(base.httpService()).thenReturn(service);
        return base;
    }

    private ProxyHttpRequestResponse mockProxyHistoryItem(String host, HttpRequest request) {
        ProxyHttpRequestResponse item = mock(ProxyHttpRequestResponse.class);
        when(item.host()).thenReturn(host);
        when(item.request()).thenReturn(request);
        when(item.response()).thenReturn(null);
        return item;
    }

    private HttpRequestResponse mockSiteMapItem(HttpRequest request) {
        HttpRequestResponse item = mock(HttpRequestResponse.class);
        when(item.request()).thenReturn(request);
        when(item.response()).thenReturn(null);
        return item;
    }

    @Test
    @DisplayName("单个数字段应生成最多6个不重复且非原值的变体")
    void singleNumericSegment_generates6Variants() {
        HttpRequest base = mockRequest("/api/users/123");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        assertEquals(6, variants.size());
        List<String> newValues = variants.stream().map(IdorScanVariant::newValue).collect(Collectors.toList());
        assertTrue(newValues.contains("124"),  "n+1");
        assertTrue(newValues.contains("122"),  "n-1");
        assertTrue(newValues.contains("1"),    "first record");
        assertTrue(newValues.contains("0"),    "zero boundary");
        assertTrue(newValues.contains("99999"),"large number");
        assertTrue(newValues.contains("1230"), "append zero");

        // 所有变体的元数据一致
        variants.forEach(v -> {
            assertEquals("numeric-path", v.technique());
            assertEquals("path[3]", v.paramName());
            assertEquals("123", v.originalValue());
        });
    }

    @Test
    @DisplayName("多个数字段各自独立产生变体，互不干扰")
    void multipleNumericSegments_independentVariantsPerSegment() {
        HttpRequest base = mockRequest("/api/users/10/orders/20");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        // 每段最多6个，两段共12个
        assertEquals(12, variants.size());

        long seg10 = variants.stream().filter(v -> "path[3]".equals(v.paramName())).count();
        long seg20 = variants.stream().filter(v -> "path[5]".equals(v.paramName())).count();
        assertEquals(6, seg10);
        assertEquals(6, seg20);
    }

    @Test
    @DisplayName("无数字段的路径不产生 numeric-path 变体")
    void noNumericSegments_noPathVariants() {
        HttpRequest base = mockRequest("/api/users/profile");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        assertTrue(variants.isEmpty());
    }

    @Test
    @DisplayName("数字段为 0 时：n-1 为负数跳过，'0' 等于原值跳过")
    void segmentIsZero_skipsNegativeAndOriginal() {
        HttpRequest base = mockRequest("/api/items/0");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        List<String> newValues = variants.stream().map(IdorScanVariant::newValue).collect(Collectors.toList());
        assertFalse(newValues.contains("-1"),  "负数应跳过");
        assertFalse(newValues.contains("0"),   "原值应跳过");
        assertTrue(newValues.contains("1"),    "n+1");
        assertTrue(newValues.contains("99999"));
        assertTrue(newValues.contains("00"),   "append zero");
    }

    @Test
    @DisplayName("数字段为 1 时：'1' 等于原值跳过，'0' 由 n-1 产生不重复")
    void segmentIsOne_deduplicatesCorrectly() {
        HttpRequest base = mockRequest("/api/items/1");

        List<IdorScanVariant> variants = pathVariants(service.generateVariants(base, List.of()));

        List<String> newValues = variants.stream().map(IdorScanVariant::newValue).collect(Collectors.toList());
        assertFalse(newValues.contains("1"), "原值应跳过");
        assertTrue(newValues.contains("2"),  "n+1");
        assertTrue(newValues.contains("0"),  "n-1");
        assertEquals(1, newValues.stream().filter("0"::equals).count(), "'0' 不应重复出现");
        assertTrue(newValues.contains("99999"));
        assertTrue(newValues.contains("10"), "append zero");
    }

    @Test
    @DisplayName("withPath 按正确的新路径被调用")
    void withPath_calledWithCorrectPath() {
        HttpRequest base = mockRequest("/api/users/5");

        service.generateVariants(base, List.of());

        // n+1 → /api/users/6
        verify(base).withPath("/api/users/6");
        // n-1 → /api/users/4
        verify(base).withPath("/api/users/4");
        // 1   → /api/users/1
        verify(base).withPath("/api/users/1");
        // 0   → /api/users/0
        verify(base).withPath("/api/users/0");
        // 99999 → /api/users/99999
        verify(base).withPath("/api/users/99999");
        // append zero → /api/users/50
        verify(base).withPath("/api/users/50");
    }

    @Test
    @DisplayName("版本路径段应生成 v1 到 v2 的 fuzz 变体")
    void versionSegment_shouldGenerateCommonPathVariants() {
        HttpRequest base = mockRequest("/api/v1/users");

        List<IdorScanVariant> variants = service.generateVariants(base, List.of());

        assertTrue(variants.stream().anyMatch(v -> "common-path".equals(v.technique())
                && "v1".equals(v.originalValue()) && "v2".equals(v.newValue())));
        verify(base).withPath("/api/v2/users");
    }

    @Test
    @DisplayName("admin 和 user 路径段应互换")
    void adminUserSegments_shouldSwap() {
        HttpRequest admin = mockRequest("/api/admin/profile");
        HttpRequest user = mockRequest("/api/user/profile");

        List<IdorScanVariant> adminVariants = service.generateVariants(admin, List.of());
        List<IdorScanVariant> userVariants = service.generateVariants(user, List.of());

        assertTrue(adminVariants.stream().anyMatch(v -> "common-path".equals(v.technique())
                && "admin".equals(v.originalValue()) && "user".equals(v.newValue())));
        assertTrue(userVariants.stream().anyMatch(v -> "common-path".equals(v.technique())
                && "user".equals(v.originalValue()) && "admin".equals(v.newValue())));
        verify(admin).withPath("/api/user/profile");
        verify(user).withPath("/api/admin/profile");
    }

    @Test
    @DisplayName("proxy history 为空时应从 sitemap 提取 UUID 路径段替换")
    void emptyProxyHistory_shouldUseSiteMapUuidPathReplacement() {
        String baseUuid = "11111111-1111-4111-8111-111111111111";
        String otherUuid = "22222222-2222-4222-8222-222222222222";
        HttpRequest base = mockRequest("/api/users/" + baseUuid, "example.com");
        HttpRequest siteMapRequest = mockRequest("/api/users/" + otherUuid, "example.com");
        HttpRequestResponse siteMapItem = mockSiteMapItem(siteMapRequest);

        List<IdorScanVariant> variants = service.generateVariants(base, List.of(), List.of(siteMapItem));

        assertTrue(variants.stream().anyMatch(v -> "history-path".equals(v.technique())
                && baseUuid.equals(v.originalValue()) && otherUuid.equals(v.newValue())));
        verify(base).withPath("/api/users/" + otherUuid);
    }

    @Test
    @DisplayName("proxy history 非空时应优先使用 proxy 而不使用 sitemap")
    void nonEmptyProxyHistory_shouldPreferProxyHistoryOverSiteMap() {
        HttpRequest base = mockRequest("/api/users/100", "example.com");
        HttpRequest proxyRequest = mockRequest("/api/users/200", "example.com");
        HttpRequest siteMapRequest = mockRequest("/api/users/300", "example.com");
        ProxyHttpRequestResponse proxyItem = mockProxyHistoryItem("example.com", proxyRequest);
        HttpRequestResponse siteMapItem = mockSiteMapItem(siteMapRequest);

        List<IdorScanVariant> variants = service.generateVariants(base, List.of(proxyItem), List.of(siteMapItem));

        assertTrue(variants.stream().anyMatch(v -> "history-path".equals(v.technique())
                && "100".equals(v.originalValue()) && "200".equals(v.newValue())));
        assertFalse(variants.stream().anyMatch(v -> "history-path".equals(v.technique())
                && "300".equals(v.newValue())));
    }

    @Test
    @DisplayName("无鉴权常见参数时应生成 1 个 common-param 注入变体")
    void noCommonAuthParams_shouldInjectAllCommonParamsOnce() {
        HttpRequest base = mockRequest("/api/list");

        List<IdorScanVariant> variants = commonParamVariants(service.generateVariants(base, List.of()));

        assertEquals(1, variants.size());
        IdorScanVariant v = variants.get(0);
        assertEquals("common-param", v.technique());
        assertEquals("common-auth", v.paramName());
        assertNull(v.originalValue());
        assertTrue(v.newValue().contains("id=1"));
        assertTrue(v.newValue().contains("email=admin@test.com"));
        assertTrue(v.newValue().contains("user=admin"));
        for (String name : IdorPayloadService.COMMON_AUTH_PARAM_NAMES) {
            assertTrue(v.newValue().contains(name + "="), "missing " + name);
        }
        verify(base).withPath(argThat(path -> path != null
                && path.startsWith("/api/list?")
                && path.contains("id=1")
                && path.contains("email=admin@test.com")));
    }

    @Test
    @DisplayName("已有任一鉴权常见参数时不触发 common-param")
    void existingCommonAuthParam_shouldSkipCommonParamStrategy() {
        HttpRequest base = mockRequest("/api/list");
        ParsedHttpParameter existing = mock(ParsedHttpParameter.class);
        when(existing.name()).thenReturn("id");
        when(existing.value()).thenReturn("5");
        when(base.parameters()).thenReturn(List.of(existing));

        List<IdorScanVariant> variants = commonParamVariants(service.generateVariants(base, List.of()));

        assertTrue(variants.isEmpty());
        verify(base, never()).withPath(argThat(path -> path != null && path.contains("email=")));
    }

    @Test
    @DisplayName("email 等特殊参数使用对应格式的 fuzz 值")
    void commonParamFuzzValues_shouldMatchParamSemantics() {
        assertEquals("admin@test.com", IdorPayloadService.fuzzValueForCommonParam("email"));
        assertEquals("admin", IdorPayloadService.fuzzValueForCommonParam("user"));
        assertEquals("admin", IdorPayloadService.fuzzValueForCommonParam("account"));
        assertEquals("admin", IdorPayloadService.fuzzValueForCommonParam("profile"));
        assertEquals("1", IdorPayloadService.fuzzValueForCommonParam("id"));
        assertEquals("1", IdorPayloadService.fuzzValueForCommonParam("order"));
        assertEquals("1", IdorPayloadService.fuzzValueForCommonParam("key"));
    }

    @Test
    @DisplayName("已有 query 时应追加而非覆盖 common-param")
    void existingQuery_shouldAppendCommonParams() {
        HttpRequest base = mockRequest("/api/list");
        when(base.query()).thenReturn("page=1");

        commonParamVariants(service.generateVariants(base, List.of()));

        verify(base).withPath(argThat(path -> path != null
                && path.startsWith("/api/list?page=1&")
                && path.contains("id=1")));
    }
}
