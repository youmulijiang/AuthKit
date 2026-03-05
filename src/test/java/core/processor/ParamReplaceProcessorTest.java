package core.processor;

import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ParamReplaceProcessor 单元测试
 * 该处理器用于替换请求中的参数为用户配置的值。
 */
class ParamReplaceProcessorTest {

    @Test
    @DisplayName("用户启用参数替换时 isEnabled 应返回 true")
    void isEnabled_paramReplaceEnabled_shouldReturnTrue() {
        ParamReplaceProcessor processor = new ParamReplaceProcessor();
        AuthUserModel user = new AuthUserModel("User1");
        user.setParamReplaceEnabled(true);
        user.setRawParams("userId=test");

        assertTrue(processor.isEnabled(user));
    }

    @Test
    @DisplayName("用户禁用参数替换时 isEnabled 应返回 false")
    void isEnabled_paramReplaceDisabled_shouldReturnFalse() {
        ParamReplaceProcessor processor = new ParamReplaceProcessor();
        AuthUserModel user = new AuthUserModel("User1");
        user.setParamReplaceEnabled(false);

        assertFalse(processor.isEnabled(user));
    }

    @Test
    @DisplayName("用户启用参数替换但无配置参数时 isEnabled 应返回 false")
    void isEnabled_noParams_shouldReturnFalse() {
        ParamReplaceProcessor processor = new ParamReplaceProcessor();
        AuthUserModel user = new AuthUserModel("User1");
        user.setParamReplaceEnabled(true);
        user.setRawParams("");

        assertFalse(processor.isEnabled(user));
    }

    @Test
    @DisplayName("process 应使用 withUpdatedParameters 替换匹配的 URL 参数")
    void process_shouldReplaceUrlParam() {
        ParamReplaceProcessor processor = new ParamReplaceProcessor();

        // 模拟请求中有 URL 参数 userId
        ParsedHttpParameter existingParam = mock(ParsedHttpParameter.class);
        when(existingParam.name()).thenReturn("userId");
        when(existingParam.type()).thenReturn(HttpParameterType.URL);

        HttpRequest original = mock(HttpRequest.class);
        when(original.parameters()).thenReturn(List.of(existingParam));

        HttpRequest afterReplace = mock(HttpRequest.class);
        when(original.withUpdatedParameters(any(HttpParameter.class))).thenReturn(afterReplace);

        HttpParameter mockParam = mock(HttpParameter.class);

        AuthUserModel user = new AuthUserModel("User1");
        user.setRawParams("userId=testuser");

        try (MockedStatic<HttpParameter> paramStatic = mockStatic(HttpParameter.class)) {
            paramStatic.when(() -> HttpParameter.urlParameter("userId", "testuser"))
                    .thenReturn(mockParam);

            HttpRequest result = processor.process(original, user);

            assertSame(afterReplace, result);
            verify(original).withUpdatedParameters(any(HttpParameter.class));
        }
    }

    @Test
    @DisplayName("请求中不存在配置的参数时应返回原始请求不变")
    void process_noMatchingParam_shouldReturnOriginal() {
        ParamReplaceProcessor processor = new ParamReplaceProcessor();

        HttpRequest original = mock(HttpRequest.class);
        when(original.parameters()).thenReturn(List.of());

        AuthUserModel user = new AuthUserModel("User1");
        user.setRawParams("nonExistent=value");

        HttpRequest result = processor.process(original, user);
        assertSame(original, result);
    }
}

