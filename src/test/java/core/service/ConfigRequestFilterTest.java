package core.service;

import burp.api.montoya.core.ToolType;
import model.ConfigModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigRequestFilter 单元测试
 * 覆盖从 ConfigModel 抽离的过滤决策逻辑。
 */
class ConfigRequestFilterTest {

    @Test
    @DisplayName("Tool Type Scope 默认应仅放行 Proxy 和 Repeater")
    void toolTypeScope_shouldHaveCorrectDefaults() {
        ConfigRequestFilter filter = new ConfigRequestFilter(new ConfigModel());

        assertFalse(filter.shouldFilterToolType(ToolType.PROXY));
        assertFalse(filter.shouldFilterToolType(ToolType.REPEATER));
        assertTrue(filter.shouldFilterToolType(ToolType.INTRUDER));
        assertTrue(filter.shouldFilterToolType(ToolType.EXTENSIONS));
    }

    @Test
    @DisplayName("Tool Type Scope 自定义开关应生效")
    void toolTypeScope_shouldRespectCustomSettings() {
        ConfigModel config = new ConfigModel();
        config.setProxyScopeEnabled(false);
        config.setIntruderScopeEnabled(true);
        config.setExtensionsScopeEnabled(true);
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertTrue(filter.shouldFilterToolType(ToolType.PROXY));
        assertFalse(filter.shouldFilterToolType(ToolType.INTRUDER));
        assertFalse(filter.shouldFilterToolType(ToolType.EXTENSIONS));
    }

    @Test
    @DisplayName("shouldFilterDomain 域名过滤启用时应过滤不在白名单的域名")
    void shouldFilter_domainNotInWhitelist() {
        ConfigModel config = new ConfigModel();
        config.setDomainFilterEnabled(true);
        config.setRawDomains("example.com");
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertTrue(filter.shouldFilterDomain("other.com"));
        assertFalse(filter.shouldFilterDomain("example.com"));
    }

    @Test
    @DisplayName("shouldFilterDomain 域名过滤关闭时不应过滤任何域名")
    void shouldFilter_domainFilterDisabled() {
        ConfigRequestFilter filter = new ConfigRequestFilter(new ConfigModel());

        assertFalse(filter.shouldFilterDomain("any.com"));
    }

    @Test
    @DisplayName("shouldFilterDomain 应支持纯 IP 白名单")
    void shouldFilter_ipWhitelist() {
        ConfigModel config = new ConfigModel();
        config.setDomainFilterEnabled(true);
        config.setRawDomains("192.168.1.1");
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertFalse(filter.shouldFilterDomain("192.168.1.1"));
        assertFalse(filter.shouldFilterDomain("192.168.1.1:8080"));
        assertTrue(filter.shouldFilterDomain("10.0.0.1"));
    }

    @Test
    @DisplayName("shouldFilterDomain 应支持 IP:port 白名单")
    void shouldFilter_ipPortWhitelist() {
        ConfigModel config = new ConfigModel();
        config.setDomainFilterEnabled(true);
        config.setRawDomains("192.168.1.1:8080");
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertFalse(filter.shouldFilterDomain("192.168.1.1:8080"));
        assertTrue(filter.shouldFilterDomain("192.168.1.1:9090"));
        assertTrue(filter.shouldFilterDomain("192.168.1.1"));
        assertTrue(filter.shouldFilterDomain("10.0.0.1:8080"));
    }

    @Test
    @DisplayName("shouldFilterDomain 应支持域名:port 白名单")
    void shouldFilter_domainPortWhitelist() {
        ConfigModel config = new ConfigModel();
        config.setDomainFilterEnabled(true);
        config.setRawDomains("api.example.com:8443");
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertFalse(filter.shouldFilterDomain("api.example.com:8443"));
        assertTrue(filter.shouldFilterDomain("api.example.com:443"));
        assertTrue(filter.shouldFilterDomain("api.example.com"));
    }

    @Test
    @DisplayName("parseHostPort 应正确解析 IP、域名与端口")
    void parseHostPort_shouldParseHostAndPort() {
        assertEquals("example.com", ConfigRequestFilter.parseHostPort("example.com").host());
        assertEquals(-1, ConfigRequestFilter.parseHostPort("example.com").port());

        assertEquals("192.168.1.1", ConfigRequestFilter.parseHostPort("192.168.1.1").host());
        assertEquals("192.168.1.1", ConfigRequestFilter.parseHostPort("192.168.1.1:8080").host());
        assertEquals(8080, ConfigRequestFilter.parseHostPort("192.168.1.1:8080").port());

        assertEquals("::1", ConfigRequestFilter.parseHostPort("[::1]").host());
        assertEquals("::1", ConfigRequestFilter.parseHostPort("[::1]:8080").host());
        assertEquals(8080, ConfigRequestFilter.parseHostPort("[::1]:8080").port());
    }

    @Test
    @DisplayName("shouldFilterMethod 方法过滤启用时应过滤指定方法")
    void shouldFilter_methodInFilterList() {
        ConfigModel config = new ConfigModel();
        config.setMethodFilterEnabled(true);
        config.setRawFilterMethods("OPTIONS, HEAD");
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertTrue(filter.shouldFilterMethod("OPTIONS"));
        assertFalse(filter.shouldFilterMethod("GET"));
    }

    @Test
    @DisplayName("shouldFilterStatusCode 状态码过滤启用时应过滤指定状态码")
    void shouldFilter_statusCodeInFilterList() {
        ConfigModel config = new ConfigModel();
        config.setStatusCodeFilterEnabled(true);
        config.setRawFilterStatusCodes("304, 204");
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertTrue(filter.shouldFilterStatusCode(304));
        assertFalse(filter.shouldFilterStatusCode(200));
    }

    @Test
    @DisplayName("shouldFilterExtension 启用时应过滤黑名单中的后缀")
    void shouldFilter_extensionInBlacklist() {
        ConfigModel config = new ConfigModel();
        config.setExtensionFilterEnabled(true);
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertTrue(filter.shouldFilterExtension("/style.css"));
        assertTrue(filter.shouldFilterExtension("/app.js"));
        assertTrue(filter.shouldFilterExtension("/logo.png"));
    }

    @Test
    @DisplayName("shouldFilterExtension 启用时不应过滤非黑名单后缀")
    void shouldFilter_extensionNotInBlacklist() {
        ConfigModel config = new ConfigModel();
        config.setExtensionFilterEnabled(true);
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertFalse(filter.shouldFilterExtension("/api/users"));
        assertFalse(filter.shouldFilterExtension("/report.pdf"));
        assertFalse(filter.shouldFilterExtension("/file.doc"));
    }

    @Test
    @DisplayName("shouldFilterExtension 关闭时不应过滤任何后缀")
    void shouldFilter_extensionFilterDisabled() {
        ConfigModel config = new ConfigModel();
        config.setExtensionFilterEnabled(false);
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertFalse(filter.shouldFilterExtension("/style.css"));
    }

    @Test
    @DisplayName("shouldFilterExtension 自定义黑名单应生效")
    void shouldFilter_customExtensionBlacklist() {
        ConfigModel config = new ConfigModel();
        config.setExtensionFilterEnabled(true);
        config.setRawExtensionBlacklist("abc, xyz");
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertTrue(filter.shouldFilterExtension("/test.abc"));
        assertTrue(filter.shouldFilterExtension("/test.xyz"));
        assertFalse(filter.shouldFilterExtension("/test.css"));
    }

    @Test
    @DisplayName("shouldFilterExtension 无后缀路径不应被过滤")
    void shouldFilter_noExtension_shouldNotFilter() {
        ConfigModel config = new ConfigModel();
        config.setExtensionFilterEnabled(true);
        ConfigRequestFilter filter = new ConfigRequestFilter(config);

        assertFalse(filter.shouldFilterExtension("/api/users"));
        assertFalse(filter.shouldFilterExtension("/api/v1/login"));
    }
}
