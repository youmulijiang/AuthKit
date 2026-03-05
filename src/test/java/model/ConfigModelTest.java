package model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigModel 单元测试
 */
class ConfigModelTest {

    @Test
    @DisplayName("默认状态应为启用")
    void defaultState_shouldBeEnabled() {
        ConfigModel config = new ConfigModel();
        assertTrue(config.isEnabled());
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
    }

    @Test
    @DisplayName("shouldFilterRequest 域名过滤启用时应过滤不在白名单的域名")
    void shouldFilter_domainNotInWhitelist() {
        ConfigModel config = new ConfigModel();
        config.setDomainFilterEnabled(true);
        config.setRawDomains("example.com");

        assertTrue(config.shouldFilterDomain("other.com"));
        assertFalse(config.shouldFilterDomain("example.com"));
    }

    @Test
    @DisplayName("shouldFilterRequest 域名过滤关闭时不应过滤任何域名")
    void shouldFilter_domainFilterDisabled() {
        ConfigModel config = new ConfigModel();
        config.setDomainFilterEnabled(false);

        assertFalse(config.shouldFilterDomain("any.com"));
    }

    @Test
    @DisplayName("shouldFilterMethod 方法过滤启用时应过滤指定方法")
    void shouldFilter_methodInFilterList() {
        ConfigModel config = new ConfigModel();
        config.setMethodFilterEnabled(true);
        config.setRawFilterMethods("OPTIONS, HEAD");

        assertTrue(config.shouldFilterMethod("OPTIONS"));
        assertFalse(config.shouldFilterMethod("GET"));
    }

    @Test
    @DisplayName("shouldFilterStatusCode 状态码过滤启用时应过滤指定状态码")
    void shouldFilter_statusCodeInFilterList() {
        ConfigModel config = new ConfigModel();
        config.setStatusCodeFilterEnabled(true);
        config.setRawFilterStatusCodes("304, 204");

        assertTrue(config.shouldFilterStatusCode(304));
        assertFalse(config.shouldFilterStatusCode(200));
    }
}

