package core;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import model.ConfigModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HttpRequestHandler 单元测试
 * 测试 shouldProcess 过滤逻辑（避免依赖 Burp 内部工厂的静态方法）
 */
class HttpRequestHandlerTest {

    private ConfigModel configModel;
    private BiConsumer<HttpRequest, HttpResponse> callback;
    private HttpRequestHandler handler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        configModel = new ConfigModel();
        configModel.setEnabled(true);
        callback = mock(BiConsumer.class);
        handler = new HttpRequestHandler(configModel, callback);
    }

    @Test
    @DisplayName("插件禁用时 shouldProcess 应返回 false")
    void pluginDisabled_shouldReturnFalse() {
        configModel.setEnabled(false);
        HttpRequest request = mock(HttpRequest.class);
        assertFalse(handler.shouldProcess(request, 200));
    }

    @Test
    @DisplayName("方法过滤启用时应过滤指定方法")
    void methodFilter_shouldFilterSpecifiedMethods() {
        configModel.setMethodFilterEnabled(true);
        configModel.setRawFilterMethods("OPTIONS, HEAD");

        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn("OPTIONS");

        assertFalse(handler.shouldProcess(request, 200));
    }

    @Test
    @DisplayName("方法过滤启用时不应过滤未指定的方法")
    void methodFilter_shouldNotFilterUnspecifiedMethods() {
        configModel.setMethodFilterEnabled(true);
        configModel.setRawFilterMethods("OPTIONS, HEAD");

        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/api");
        when(request.path()).thenReturn("/api");

        assertTrue(handler.shouldProcess(request, 200));
    }

    @Test
    @DisplayName("状态码过滤启用时应过滤指定状态码")
    void statusCodeFilter_shouldFilterSpecifiedCodes() {
        configModel.setStatusCodeFilterEnabled(true);
        configModel.setRawFilterStatusCodes("304, 204");

        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/api");
        when(request.path()).thenReturn("/api");

        assertFalse(handler.shouldProcess(request, 304));
    }

    @Test
    @DisplayName("通过所有过滤条件时 shouldProcess 应返回 true")
    void passAllFilters_shouldReturnTrue() {
        configModel.setMethodFilterEnabled(true);
        configModel.setRawFilterMethods("OPTIONS");
        configModel.setStatusCodeFilterEnabled(true);
        configModel.setRawFilterStatusCodes("304");

        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/api");
        when(request.path()).thenReturn("/api");

        assertTrue(handler.shouldProcess(request, 200));
    }

    @Test
    @DisplayName("域名过滤启用时应过滤不在白名单的域名")
    void domainFilter_shouldFilterNonWhitelistedDomains() {
        configModel.setDomainFilterEnabled(true);
        configModel.setRawDomains("example.com");

        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://other.com/api");

        assertFalse(handler.shouldProcess(request, 200));
    }

    @Test
    @DisplayName("域名过滤启用时白名单域名应通过")
    void domainFilter_whitelistedDomain_shouldPass() {
        configModel.setDomainFilterEnabled(true);
        configModel.setRawDomains("example.com");

        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/api");
        when(request.path()).thenReturn("/api");

        assertTrue(handler.shouldProcess(request, 200));
    }

    @Test
    @DisplayName("路径过滤启用时应过滤指定路径")
    void pathFilter_shouldFilterSpecifiedPaths() {
        configModel.setPathFilterEnabled(true);
        configModel.setRawFilterPaths("/logout\n/health");

        HttpRequest request = mock(HttpRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/logout");
        when(request.path()).thenReturn("/logout");

        assertFalse(handler.shouldProcess(request, 200));
    }
}

