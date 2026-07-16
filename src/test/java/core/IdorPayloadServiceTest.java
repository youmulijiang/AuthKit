package core;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IdorPayloadServiceTest {

    private final IdorPayloadService service = new IdorPayloadService();

    /** 过滤出 technique == "numeric-path" 的变体 */
    private List<IdorScanVariant> pathVariants(List<IdorScanVariant> all) {
        return all.stream().filter(v -> "numeric-path".equals(v.technique())).collect(Collectors.toList());
    }

    private HttpRequest mockRequest(String path) {
        HttpRequest base = mock(HttpRequest.class);
        HttpRequest mutated = mock(HttpRequest.class);
        when(base.pathWithoutQuery()).thenReturn(path);
        when(base.withPath(anyString())).thenReturn(mutated);
        when(base.parameters()).thenReturn(List.of());
        when(base.httpService()).thenReturn(null);
        // removeAuthHeaders() calls headers() — return empty list so it's a no-op
        when(base.headers()).thenReturn(List.of());
        return base;
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
}
