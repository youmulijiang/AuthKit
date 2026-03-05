package model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthUserModel 单元测试
 */
class AuthUserModelTest {

    @Test
    @DisplayName("构造函数应正确设置名称和默认启用状态")
    void constructor_shouldSetNameAndDefaultEnabled() {
        AuthUserModel user = new AuthUserModel("User1");
        assertEquals("User1", user.getName());
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("parseHeaders 应正确解析多行认证头文本")
    void parseHeaders_shouldParseMultiLineText() {
        AuthUserModel user = new AuthUserModel("User1");
        user.setRawHeaders("Cookie: JSESSIONID=abc\nAuthorization: Bearer token123");

        Map<String, String> headers = user.getHeaders();
        assertEquals(2, headers.size());
        assertEquals("JSESSIONID=abc", headers.get("Cookie"));
        assertEquals("Bearer token123", headers.get("Authorization"));
    }

    @Test
    @DisplayName("parseHeaders 应忽略空行和无效行")
    void parseHeaders_shouldIgnoreEmptyAndInvalidLines() {
        AuthUserModel user = new AuthUserModel("User1");
        user.setRawHeaders("Cookie: abc\n\ninvalid_line\n  \nToken: xyz");

        Map<String, String> headers = user.getHeaders();
        assertEquals(2, headers.size());
        assertEquals("abc", headers.get("Cookie"));
        assertEquals("xyz", headers.get("Token"));
    }

    @Test
    @DisplayName("parseParams 应正确解析多行参数替换文本")
    void parseParams_shouldParseMultiLineText() {
        AuthUserModel user = new AuthUserModel("User1");
        user.setRawParams("userId=testuser\nrole=guest");

        Map<String, String> params = user.getParams();
        assertEquals(2, params.size());
        assertEquals("testuser", params.get("userId"));
        assertEquals("guest", params.get("role"));
    }

    @Test
    @DisplayName("parseParams 应忽略空行和无效行")
    void parseParams_shouldIgnoreEmptyAndInvalidLines() {
        AuthUserModel user = new AuthUserModel("User1");
        user.setRawParams("userId=test\n\nno_equals\n  \nrole=admin");

        Map<String, String> params = user.getParams();
        assertEquals(2, params.size());
        assertEquals("test", params.get("userId"));
        assertEquals("admin", params.get("role"));
    }

    @Test
    @DisplayName("空文本应返回空 Map")
    void emptyText_shouldReturnEmptyMap() {
        AuthUserModel user = new AuthUserModel("User1");
        user.setRawHeaders("");
        user.setRawParams("");

        assertTrue(user.getHeaders().isEmpty());
        assertTrue(user.getParams().isEmpty());
    }

    @Test
    @DisplayName("isHeaderReplaceEnabled 默认应为 true")
    void headerReplaceEnabled_shouldDefaultTrue() {
        AuthUserModel user = new AuthUserModel("User1");
        assertTrue(user.isHeaderReplaceEnabled());
    }

    @Test
    @DisplayName("isParamReplaceEnabled 默认应为 true")
    void paramReplaceEnabled_shouldDefaultTrue() {
        AuthUserModel user = new AuthUserModel("User1");
        assertTrue(user.isParamReplaceEnabled());
    }

    @Test
    @DisplayName("可以独立控制头替换和参数替换的开关")
    void canToggleHeaderAndParamReplace() {
        AuthUserModel user = new AuthUserModel("User1");
        user.setHeaderReplaceEnabled(false);
        user.setParamReplaceEnabled(false);

        assertFalse(user.isHeaderReplaceEnabled());
        assertFalse(user.isParamReplaceEnabled());
    }
}

