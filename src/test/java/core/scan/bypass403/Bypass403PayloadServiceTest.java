package core.scan.bypass403;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class Bypass403PayloadServiceTest {

    private final Bypass403PayloadService service = new Bypass403PayloadService();

    @Test
    @DisplayName("应按403bypasser策略生成方法、路径、Header和Rewrite Header变体")
    void generateVariants_shouldCreateExpectedPayloads() {
        HttpRequest base = mock(HttpRequest.class);
        HttpRequest variant = mock(HttpRequest.class);
        when(base.pathWithoutQuery()).thenReturn("/admin/");
        when(base.withMethod("POST")).thenReturn(variant);
        when(base.withPath(anyString())).thenReturn(variant);
        when(base.hasHeader(anyString())).thenReturn(false);
        when(base.withAddedHeader(anyString(), anyString())).thenReturn(variant);
        when(variant.hasHeader(anyString())).thenReturn(false);
        when(variant.withAddedHeader(anyString(), anyString())).thenReturn(variant);

        List<Bypass403RequestVariant> variants = service.generateVariants(base);

        assertEquals(159, variants.size());
        verify(base).withMethod("POST");
        verify(base).withPath("//admin//");
        verify(base).withPath("/%2e/admin");
        verify(base).withPath("/admin%00");
        verify(base).withAddedHeader("X-Forwarded-For", "127.0.0.1");
        verify(variant).withAddedHeader("X-Original-URL", "/admin");
        verify(variant).withAddedHeader("X-Rewrite-URL", "/admin");
    }

    @Test
    @DisplayName("路径payload应兼容无前导斜杠和末尾斜杠")
    void generatePathPayloads_shouldNormalizePath() {
        List<String> paths = service.generatePathPayloads("secret/");

        assertEquals("/secret", paths.get(0));
        assertTrue(paths.contains("//secret//"));
        assertTrue(paths.contains("/./secret/./"));
        assertTrue(paths.contains("/secret?testparam"));
    }
}
