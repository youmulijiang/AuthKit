package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;
import model.ConfigModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HeaderRemoveProcessor 单元测试
 * 该处理器用于未授权场景，移除 ConfigModel 中配置的认证头。
 */
class HeaderRemoveProcessorTest {

    private ConfigModel configModel;
    private HeaderRemoveProcessor processor;

    @BeforeEach
    void setUp() {
        configModel = new ConfigModel();
        configModel.setRawAuthHeaders("Cookie\nAuthorization\nToken");
        processor = new HeaderRemoveProcessor(configModel);
    }

    @Test
    @DisplayName("isEnabled 应始终返回 true（未授权场景总是需要移除头）")
    void isEnabled_shouldAlwaysReturnTrue() {
        AuthUserModel user = new AuthUserModel("Unauthorized");
        assertTrue(processor.isEnabled(user));
    }

    @Test
    @DisplayName("process 应移除配置中指定的所有认证头")
    void process_shouldRemoveAllConfiguredHeaders() {
        HttpRequest original = mock(HttpRequest.class);
        HttpRequest afterCookie = mock(HttpRequest.class);
        HttpRequest afterAuth = mock(HttpRequest.class);
        HttpRequest afterToken = mock(HttpRequest.class);

        when(original.withRemovedHeader("Cookie")).thenReturn(afterCookie);
        when(afterCookie.withRemovedHeader("Authorization")).thenReturn(afterAuth);
        when(afterAuth.withRemovedHeader("Token")).thenReturn(afterToken);

        AuthUserModel user = new AuthUserModel("Unauthorized");
        HttpRequest result = processor.process(original, user);

        assertSame(afterToken, result);
        verify(original).withRemovedHeader("Cookie");
        verify(afterCookie).withRemovedHeader("Authorization");
        verify(afterAuth).withRemovedHeader("Token");
    }

    @Test
    @DisplayName("认证头列表为空时应返回原始请求不变")
    void process_emptyHeaders_shouldReturnOriginal() {
        ConfigModel emptyConfig = new ConfigModel();
        emptyConfig.setRawAuthHeaders("");
        HeaderRemoveProcessor emptyProcessor = new HeaderRemoveProcessor(emptyConfig);

        HttpRequest original = mock(HttpRequest.class);
        AuthUserModel user = new AuthUserModel("Unauthorized");

        HttpRequest result = emptyProcessor.process(original, user);
        assertSame(original, result);
    }
}

