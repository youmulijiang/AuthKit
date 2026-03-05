package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HeaderReplaceProcessor 单元测试
 * 该处理器用于替换请求中的认证头为用户配置的值。
 */
class HeaderReplaceProcessorTest {

    @Test
    @DisplayName("用户启用头替换时 isEnabled 应返回 true")
    void isEnabled_headerReplaceEnabled_shouldReturnTrue() {
        HeaderReplaceProcessor processor = new HeaderReplaceProcessor();
        AuthUserModel user = new AuthUserModel("User1");
        user.setHeaderReplaceEnabled(true);
        user.setRawHeaders("Cookie: abc");

        assertTrue(processor.isEnabled(user));
    }

    @Test
    @DisplayName("用户禁用头替换时 isEnabled 应返回 false")
    void isEnabled_headerReplaceDisabled_shouldReturnFalse() {
        HeaderReplaceProcessor processor = new HeaderReplaceProcessor();
        AuthUserModel user = new AuthUserModel("User1");
        user.setHeaderReplaceEnabled(false);

        assertFalse(processor.isEnabled(user));
    }

    @Test
    @DisplayName("用户启用头替换但无配置头时 isEnabled 应返回 false")
    void isEnabled_noHeaders_shouldReturnFalse() {
        HeaderReplaceProcessor processor = new HeaderReplaceProcessor();
        AuthUserModel user = new AuthUserModel("User1");
        user.setHeaderReplaceEnabled(true);
        user.setRawHeaders("");

        assertFalse(processor.isEnabled(user));
    }

    @Test
    @DisplayName("process 应使用 withHeader 替换所有配置的认证头")
    void process_shouldReplaceAllHeaders() {
        HeaderReplaceProcessor processor = new HeaderReplaceProcessor();

        HttpRequest original = mock(HttpRequest.class);
        HttpRequest afterCookie = mock(HttpRequest.class);
        HttpRequest afterAuth = mock(HttpRequest.class);

        when(original.withHeader("Cookie", "JSESSIONID=abc")).thenReturn(afterCookie);
        when(afterCookie.withHeader("Authorization", "Bearer token123")).thenReturn(afterAuth);

        AuthUserModel user = new AuthUserModel("User1");
        user.setRawHeaders("Cookie: JSESSIONID=abc\nAuthorization: Bearer token123");

        HttpRequest result = processor.process(original, user);

        assertSame(afterAuth, result);
        verify(original).withHeader("Cookie", "JSESSIONID=abc");
        verify(afterCookie).withHeader("Authorization", "Bearer token123");
    }

    @Test
    @DisplayName("无认证头配置时应返回原始请求不变")
    void process_emptyHeaders_shouldReturnOriginal() {
        HeaderReplaceProcessor processor = new HeaderReplaceProcessor();
        HttpRequest original = mock(HttpRequest.class);

        AuthUserModel user = new AuthUserModel("User1");
        user.setRawHeaders("");

        HttpRequest result = processor.process(original, user);
        assertSame(original, result);
    }
}

