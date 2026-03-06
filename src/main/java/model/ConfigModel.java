package model;

import burp.api.montoya.core.ToolType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 插件配置数据模型
 * 封装 ConfigurationPanel 中的所有配置项，提供结构化的配置数据访问。
 */
public class ConfigModel {

    /** 默认后缀黑名单：静态资源、字体、图片、媒体等（不含办公文件） */
    public static final String DEFAULT_EXTENSION_BLACKLIST =
            "css, js, jpg, jpeg, png, gif, svg, ico, bmp, webp, " +
            "woff, woff2, ttf, eot, otf, " +
            "mp3, mp4, avi, mov, wmv, flv, webm, " +
            "map, less, scss, sass";

    private boolean enabled;
    private boolean domainFilterEnabled;
    private boolean methodFilterEnabled;
    private boolean pathFilterEnabled;
    private boolean statusCodeFilterEnabled;
    private boolean extensionFilterEnabled;
    private boolean proxyScopeEnabled;
    private boolean repeaterScopeEnabled;
    private boolean intruderScopeEnabled;
    private boolean extensionsScopeEnabled;

    private String rawDomains;
    private String rawFilterMethods;
    private String rawFilterPaths;
    private String rawFilterStatusCodes;
    private String rawAuthHeaders;
    private String rawExtensionBlacklist;

    public ConfigModel() {
        this.enabled = false;
        this.domainFilterEnabled = false;
        this.methodFilterEnabled = false;
        this.pathFilterEnabled = false;
        this.statusCodeFilterEnabled = true;
        this.extensionFilterEnabled = true;
        this.proxyScopeEnabled = true;
        this.repeaterScopeEnabled = true;
        this.intruderScopeEnabled = false;
        this.extensionsScopeEnabled = false;
        this.rawDomains = "";
        this.rawFilterMethods = "";
        this.rawFilterPaths = "";
        this.rawFilterStatusCodes = "";
        this.rawAuthHeaders = "";
        this.rawExtensionBlacklist = DEFAULT_EXTENSION_BLACKLIST;
    }

    /** 插件是否启用 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置插件启用状态 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 域名过滤是否启用 */
    public boolean isDomainFilterEnabled() {
        return domainFilterEnabled;
    }

    /** 设置域名过滤开关 */
    public void setDomainFilterEnabled(boolean domainFilterEnabled) {
        this.domainFilterEnabled = domainFilterEnabled;
    }

    /** 方法过滤是否启用 */
    public boolean isMethodFilterEnabled() {
        return methodFilterEnabled;
    }

    /** 设置方法过滤开关 */
    public void setMethodFilterEnabled(boolean methodFilterEnabled) {
        this.methodFilterEnabled = methodFilterEnabled;
    }

    /** 路径过滤是否启用 */
    public boolean isPathFilterEnabled() {
        return pathFilterEnabled;
    }

    /** 设置路径过滤开关 */
    public void setPathFilterEnabled(boolean pathFilterEnabled) {
        this.pathFilterEnabled = pathFilterEnabled;
    }

    /** 状态码过滤是否启用 */
    public boolean isStatusCodeFilterEnabled() {
        return statusCodeFilterEnabled;
    }

    /** 设置状态码过滤开关 */
    public void setStatusCodeFilterEnabled(boolean statusCodeFilterEnabled) {
        this.statusCodeFilterEnabled = statusCodeFilterEnabled;
    }

    /** 设置原始域名文本 */
    public void setRawDomains(String rawDomains) {
        this.rawDomains = rawDomains != null ? rawDomains : "";
    }

    /** 设置原始过滤方法文本 */
    public void setRawFilterMethods(String rawFilterMethods) {
        this.rawFilterMethods = rawFilterMethods != null ? rawFilterMethods : "";
    }

    /** 设置原始过滤路径文本 */
    public void setRawFilterPaths(String rawFilterPaths) {
        this.rawFilterPaths = rawFilterPaths != null ? rawFilterPaths : "";
    }

    /** 设置原始过滤状态码文本 */
    public void setRawFilterStatusCodes(String rawFilterStatusCodes) {
        this.rawFilterStatusCodes = rawFilterStatusCodes != null ? rawFilterStatusCodes : "";
    }

    /** 设置原始认证头文本 */
    public void setRawAuthHeaders(String rawAuthHeaders) {
        this.rawAuthHeaders = rawAuthHeaders != null ? rawAuthHeaders : "";
    }

    /** 后缀黑名单过滤是否启用 */
    public boolean isExtensionFilterEnabled() {
        return extensionFilterEnabled;
    }

    /** 设置后缀黑名单过滤开关 */
    public void setExtensionFilterEnabled(boolean extensionFilterEnabled) {
        this.extensionFilterEnabled = extensionFilterEnabled;
    }

    /** Proxy Scope 是否启用 */
    public boolean isProxyScopeEnabled() {
        return proxyScopeEnabled;
    }

    /** 设置 Proxy Scope 开关 */
    public void setProxyScopeEnabled(boolean proxyScopeEnabled) {
        this.proxyScopeEnabled = proxyScopeEnabled;
    }

    /** Repeater Scope 是否启用 */
    public boolean isRepeaterScopeEnabled() {
        return repeaterScopeEnabled;
    }

    /** 设置 Repeater Scope 开关 */
    public void setRepeaterScopeEnabled(boolean repeaterScopeEnabled) {
        this.repeaterScopeEnabled = repeaterScopeEnabled;
    }

    /** Intruder Scope 是否启用 */
    public boolean isIntruderScopeEnabled() {
        return intruderScopeEnabled;
    }

    /** 设置 Intruder Scope 开关 */
    public void setIntruderScopeEnabled(boolean intruderScopeEnabled) {
        this.intruderScopeEnabled = intruderScopeEnabled;
    }

    /** Extensions Scope 是否启用 */
    public boolean isExtensionsScopeEnabled() {
        return extensionsScopeEnabled;
    }

    /** 设置 Extensions Scope 开关 */
    public void setExtensionsScopeEnabled(boolean extensionsScopeEnabled) {
        this.extensionsScopeEnabled = extensionsScopeEnabled;
    }

    /** 设置原始后缀黑名单文本 */
    public void setRawExtensionBlacklist(String rawExtensionBlacklist) {
        this.rawExtensionBlacklist = rawExtensionBlacklist != null ? rawExtensionBlacklist : "";
    }

    /** 获取原始后缀黑名单文本 */
    public String getRawExtensionBlacklist() {
        return rawExtensionBlacklist;
    }

    /** 解析后缀黑名单集合（逗号分隔，转小写） */
    public Set<String> getExtensionBlacklist() {
        Set<String> result = new HashSet<>();
        if (rawExtensionBlacklist == null || rawExtensionBlacklist.isBlank()) {
            return result;
        }
        for (String item : rawExtensionBlacklist.split(",")) {
            String trimmed = item.trim().toLowerCase();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 判断是否应根据文件后缀过滤请求
     *
     * @param path 请求路径
     * @return true 表示应过滤
     */
    public boolean shouldFilterExtension(String path) {
        if (!extensionFilterEnabled) {
            return false;
        }
        String extension = extractExtension(path);
        if (extension.isEmpty()) {
            return false;
        }
        return getExtensionBlacklist().contains(extension.toLowerCase());
    }

    /**
     * 从路径中提取文件后缀（不含点号）
     */
    private String extractExtension(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        // 去掉查询参数
        int queryIndex = path.indexOf('?');
        String cleanPath = queryIndex >= 0 ? path.substring(0, queryIndex) : path;
        int dotIndex = cleanPath.lastIndexOf('.');
        int slashIndex = cleanPath.lastIndexOf('/');
        if (dotIndex > slashIndex && dotIndex < cleanPath.length() - 1) {
            return cleanPath.substring(dotIndex + 1);
        }
        return "";
    }

    /** 解析域名白名单列表 */
    public List<String> getDomains() {
        return parseLines(rawDomains);
    }

    /** 解析过滤方法集合（逗号分隔） */
    public Set<String> getFilterMethods() {
        return parseCommaSeparatedSet(rawFilterMethods);
    }

    /** 解析过滤路径列表 */
    public List<String> getFilterPaths() {
        return parseLines(rawFilterPaths);
    }

    /** 解析过滤状态码集合（逗号分隔） */
    public Set<Integer> getFilterStatusCodes() {
        Set<Integer> codes = new HashSet<>();
        for (String item : rawFilterStatusCodes.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                try {
                    codes.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return codes;
    }

    /** 解析认证头名称列表 */
    public List<String> getAuthHeaders() {
        return parseLines(rawAuthHeaders);
    }


    /**
     * 判断是否应过滤指定域名
     *
     * @param domain 请求域名
     * @return true 表示应过滤（不处理），false 表示放行
     */
    public boolean shouldFilterDomain(String domain) {
        if (!domainFilterEnabled) {
            return false;
        }
        List<String> domains = getDomains();
        return !domains.contains(domain);
    }

    /**
     * 判断是否应过滤指定 HTTP 方法
     *
     * @param method HTTP 方法
     * @return true 表示应过滤
     */
    public boolean shouldFilterMethod(String method) {
        if (!methodFilterEnabled) {
            return false;
        }
        return getFilterMethods().contains(method);
    }

    /**
     * 判断是否应过滤指定路径
     *
     * @param path 请求路径
     * @return true 表示应过滤
     */
    public boolean shouldFilterPath(String path) {
        if (!pathFilterEnabled) {
            return false;
        }
        List<String> paths = getFilterPaths();
        for (String filterPath : paths) {
            if (path.startsWith(filterPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否应过滤指定状态码
     *
     * @param statusCode 响应状态码
     * @return true 表示应过滤
     */
    public boolean shouldFilterStatusCode(int statusCode) {
        if (!statusCodeFilterEnabled) {
            return false;
        }
        return getFilterStatusCodes().contains(statusCode);
    }

    /** 判断是否应按 Tool Type 过滤 */
    public boolean shouldFilterToolType(ToolType toolType) {
        if (toolType == null) {
            return false;
        }

        return switch (toolType) {
            case PROXY -> !proxyScopeEnabled;
            case REPEATER -> !repeaterScopeEnabled;
            case INTRUDER -> !intruderScopeEnabled;
            case EXTENSIONS -> !extensionsScopeEnabled;
            default -> true;
        };
    }

    /** 解析多行文本为列表（去空行、去首尾空格） */
    private List<String> parseLines(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 解析逗号分隔文本为 Set（去空格） */
    private Set<String> parseCommaSeparatedSet(String text) {
        Set<String> result = new HashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        for (String item : text.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
