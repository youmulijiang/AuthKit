package core.processor;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;
import model.ConfigModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HeaderRemoveProcessor 单元测试
 * 该处理器用于未授权场景，按内置关键字和配置关键字的并集移除鉴权头，不区分大小写。
 */
class HeaderRemoveProcessorTest {

    private ConfigModel configModel;
    private HeaderRemoveProcessor processor;

    @BeforeEach
    void setUp() {
        configModel = new ConfigModel();
        configModel.setRawAuthHeaders("X-Custom-Auth");
        processor = new HeaderRemoveProcessor(configModel);
    }

    @Test
    @DisplayName("isEnabled 应始终返回 true（未授权场景总是需要移除头）")
    void isEnabled_shouldAlwaysReturnTrue() {
        AuthUserModel user = new AuthUserModel("Unauthorized");
        assertTrue(processor.isEnabled(user));
    }

    @Test
    @DisplayName("process 配置非空时应同时移除内置关键字和配置匹配的认证头")
    void process_shouldRemoveBothBuiltInAndConfiguredHeaders() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader cookie = mock(HttpHeader.class);
        when(cookie.name()).thenReturn("Cookie");
        HttpHeader customAuth = mock(HttpHeader.class);
        when(customAuth.name()).thenReturn("X-Custom-Auth");
        when(original.headers()).thenReturn(List.of(cookie, customAuth));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());

        HttpRequest afterRemove = mock(HttpRequest.class);
        when(original.withRemovedHeaders(List.of(cookie, customAuth))).thenReturn(afterRemove);

        AuthUserModel user = new AuthUserModel("Unauthorized");
        HttpRequest result = processor.process(original, user);

        assertSame(afterRemove, result);
        verify(original).withRemovedHeaders(List.of(cookie, customAuth));
    }

    @Test
    @DisplayName("process 应按配置关键字包含匹配移除请求头")
    void process_shouldRemoveHeadersContainingConfiguredKeyword() {
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader customAuth = mock(HttpHeader.class);
        when(customAuth.name()).thenReturn("X-Custom-Auth-Token");
        when(original.headers()).thenReturn(List.of(customAuth));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());

        HttpRequest afterRemove = mock(HttpRequest.class);
        when(original.withRemovedHeaders(List.of(customAuth))).thenReturn(afterRemove);

        AuthUserModel user = new AuthUserModel("Unauthorized");
        HttpRequest result = processor.process(original, user);

        assertSame(afterRemove, result);
        verify(original).withRemovedHeaders(List.of(customAuth));
    }

    @Test
    @DisplayName("无鉴权头且配置为空时应返回原始请求不变")
    void process_emptyHeaders_shouldReturnOriginal() {
        ConfigModel emptyConfig = new ConfigModel();
        emptyConfig.setRawAuthHeaders("");
        HeaderRemoveProcessor emptyProcessor = new HeaderRemoveProcessor(emptyConfig);

        HttpRequest original = mock(HttpRequest.class);
        when(original.headers()).thenReturn(List.of());
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of());
        AuthUserModel user = new AuthUserModel("Unauthorized");

        HttpRequest result = emptyProcessor.process(original, user);
        assertSame(original, result);
    }
}
