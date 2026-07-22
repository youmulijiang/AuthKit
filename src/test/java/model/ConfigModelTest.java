package model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigModel 单元测试
 * 仅覆盖数据存储与原始文本解析 getter；过滤决策逻辑见 {@code ConfigRequestFilterTest}。
 */
class ConfigModelTest {

    @Test
    @DisplayName("默认状态应为未启用")
    void defaultState_shouldBeDisabled() {
        ConfigModel config = new ConfigModel();
        assertFalse(config.isEnabled());
    }

    @Test
    @DisplayName("parseDomains 应正确解析多行域名文本")
    void parseDomains_shouldParseMultiLineText() {
        ConfigModel config = new ConfigModel();
        config.setRawDomains("example.com\ntest.org\n  api.dev  ");

        List<String> domains = config.getDomains();
        assertEquals(3, domains.size());
        assertEquals("example.com", domains.get(0));
        assertEquals("test.org", domains.get(1));
        assertEquals("api.dev", domains.get(2));
    }

    @Test
    @DisplayName("parseDomains 应忽略空行")
    void parseDomains_shouldIgnoreEmptyLines() {
        ConfigModel config = new ConfigModel();
        config.setRawDomains("example.com\n\n\ntest.org");

        List<String> domains = config.getDomains();
        assertEquals(2, domains.size());
    }

    @Test
    @DisplayName("parseFilterMethods 应正确解析逗号分隔的方法列表")
    void parseFilterMethods_shouldParseCommaSeparated() {
        ConfigModel config = new ConfigModel();
        config.setRawFilterMethods("OPTIONS, HEAD, CONNECT");

        Set<String> methods = config.getFilterMethods();
        assertEquals(3, methods.size());
        assertTrue(methods.contains("OPTIONS"));
        assertTrue(methods.contains("HEAD"));
        assertTrue(methods.contains("CONNECT"));
    }

    @Test
    @DisplayName("parseFilterPaths 应正确解析多行路径文本")
    void parseFilterPaths_shouldParseMultiLineText() {
        ConfigModel config = new ConfigModel();
        config.setRawFilterPaths("/logout\n/health\n  /static  ");

        List<String> paths = config.getFilterPaths();
        assertEquals(3, paths.size());
        assertTrue(paths.contains("/logout"));
    }

    @Test
    @DisplayName("parseFilterStatusCodes 应正确解析逗号分隔的状态码")
    void parseFilterStatusCodes_shouldParseCommaSeparated() {
        ConfigModel config = new ConfigModel();
        config.setRawFilterStatusCodes("304, 204");

        Set<Integer> codes = config.getFilterStatusCodes();
        assertEquals(2, codes.size());
        assertTrue(codes.contains(304));
        assertTrue(codes.contains(204));
    }

    @Test
    @DisplayName("parseAuthHeaders 应正确解析多行认证头名称")
    void parseAuthHeaders_shouldParseMultiLineText() {
        ConfigModel config = new ConfigModel();
        config.setRawAuthHeaders("Cookie\nAuthorization\nToken");

        List<String> headers = config.getAuthHeaders();
        assertEquals(3, headers.size());
        assertEquals("Cookie", headers.get(0));
        assertEquals("Authorization", headers.get(1));
        assertEquals("Token", headers.get(2));
    }

    @Test
    @DisplayName("各过滤开关默认状态应正确")
    void filterSwitches_shouldHaveCorrectDefaults() {
        ConfigModel config = new ConfigModel();
        assertFalse(config.isDomainFilterEnabled());
        assertFalse(config.isMethodFilterEnabled());
        assertFalse(config.isPathFilterEnabled());
        assertTrue(config.isStatusCodeFilterEnabled());
        assertTrue(config.isProxyScopeEnabled());
        assertTrue(config.isRepeaterScopeEnabled());
        assertFalse(config.isIntruderScopeEnabled());
        assertFalse(config.isExtensionsScopeEnabled());
    }

    @Test
    @DisplayName("后缀黑名单默认应包含常见静态资源后缀")
    void extensionBlacklist_shouldHaveDefaults() {
        ConfigModel config = new ConfigModel();
        Set<String> exts = config.getExtensionBlacklist();
        assertTrue(exts.contains("css"));
        assertTrue(exts.contains("js"));
        assertTrue(exts.contains("png"));
        assertTrue(exts.contains("woff2"));
        assertFalse(exts.contains("doc"));
        assertFalse(exts.contains("pdf"));
    }
}
