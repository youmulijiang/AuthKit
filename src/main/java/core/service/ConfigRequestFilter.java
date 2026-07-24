package core.service;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.requests.HttpRequest;
import model.ConfigModel;
import utils.UrlPathUtils;

import java.net.URI;

/**
 * 基于 {@link ConfigModel} 的请求过滤实现。
 * <p>
 * 从 ConfigModel 抽离的过滤决策逻辑：读取配置开关与解析后的数据，
 * 判定请求是否应被插件处理。纯数据访问（getter/解析）仍保留在 ConfigModel。
 */
public class ConfigRequestFilter implements RequestFilter {

    private final ConfigModel config;

    public ConfigRequestFilter(ConfigModel config) {
        this.config = config;
    }

    @Override
    public boolean shouldProcess(HttpRequest request, int statusCode, ToolType toolType) {
        if (!config.isEnabled()) {
            return false;
        }
        if (shouldFilterToolType(toolType)) {
            return false;
        }
        if (shouldFilterMethod(request.method())) {
            return false;
        }
        if (config.isDomainFilterEnabled()) {
            HostPort target = resolveHostPort(request);
            if (shouldFilterHostPort(target.host(), target.port())) {
                return false;
            }
        }
        if (shouldFilterPath(request.path())) {
            return false;
        }
        if (shouldFilterExtension(request.path())) {
            return false;
        }
        if (shouldFilterStatusCode(statusCode)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldFilterDomain(String domain) {
        if (!config.isDomainFilterEnabled()) {
            return false;
        }
        if (domain == null || domain.isEmpty()) {
            return true;
        }
        HostPort parsed = parseHostPort(domain);
        return shouldFilterHostPort(parsed.host(), parsed.port());
    }

    @Override
    public boolean shouldFilterMethod(String method) {
        if (!config.isMethodFilterEnabled()) {
            return false;
        }
        return config.getFilterMethods().contains(method);
    }

    @Override
    public boolean shouldFilterPath(String path) {
        if (!config.isPathFilterEnabled()) {
            return false;
        }
        for (String filterPath : config.getFilterPaths()) {
            if (path.startsWith(filterPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldFilterStatusCode(int statusCode) {
        if (!config.isStatusCodeFilterEnabled()) {
            return false;
        }
        return config.getFilterStatusCodes().contains(statusCode);
    }

    @Override
    public boolean shouldFilterExtension(String path) {
        if (!config.isExtensionFilterEnabled()) {
            return false;
        }
        String extension = UrlPathUtils.extractExtension(path);
        if (extension.isEmpty()) {
            return false;
        }
        return config.getExtensionBlacklist().contains(extension);
    }

    @Override
    public boolean shouldFilterToolType(ToolType toolType) {
        if (toolType == null) {
            return false;
        }
        return switch (toolType) {
            case PROXY -> !config.isProxyScopeEnabled();
            case REPEATER -> !config.isRepeaterScopeEnabled();
            case INTRUDER -> !config.isIntruderScopeEnabled();
            case EXTENSIONS -> !config.isExtensionsScopeEnabled();
            default -> true;
        };
    }

    /**
     * 白名单未命中则过滤。支持域名、IP、host:port / ip:port。
     * 白名单仅写 host/IP 时匹配任意端口；写明端口时需端口一致。
     */
    private boolean shouldFilterHostPort(String host, int port) {
        if (host == null || host.isEmpty()) {
            return true;
        }
        String normalizedHost = normalizeHost(host);
        for (String entry : config.getDomains()) {
            if (matchesWhitelistEntry(entry, normalizedHost, port)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesWhitelistEntry(String entry, String requestHost, int requestPort) {
        if (entry == null || entry.isEmpty()) {
            return false;
        }
        HostPort configured = parseHostPort(entry.trim());
        if (configured.host() == null || configured.host().isEmpty()) {
            return false;
        }
        if (!normalizeHost(configured.host()).equalsIgnoreCase(requestHost)) {
            return false;
        }
        if (configured.port() < 0) {
            return true;
        }
        return requestPort >= 0 && configured.port() == requestPort;
    }

    private HostPort resolveHostPort(HttpRequest request) {
        try {
            if (request.httpService() != null) {
                String host = request.httpService().host();
                if (host != null && !host.isEmpty()) {
                    return new HostPort(host, request.httpService().port());
                }
            }
        } catch (Exception ignored) {
        }
        return extractHostPort(request.url());
    }

    private HostPort extractHostPort(String url) {
        if (url == null || url.isEmpty()) {
            return HostPort.empty();
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host != null && !host.isEmpty()) {
                return new HostPort(host, port);
            }
            if (uri.getAuthority() != null && !uri.getAuthority().isEmpty()) {
                return parseHostPort(uri.getAuthority());
            }
        } catch (Exception ignored) {
        }
        return parseHostPort(url);
    }

    /**
     * 解析 host 或 host:port（含 IPv6：[::1]、[::1]:8080）
     */
    static HostPort parseHostPort(String value) {
        if (value == null) {
            return HostPort.empty();
        }
        String input = value.trim();
        if (input.isEmpty()) {
            return HostPort.empty();
        }

        if (input.startsWith("[")) {
            int close = input.indexOf(']');
            if (close > 1) {
                String host = input.substring(1, close);
                if (close + 1 < input.length() && input.charAt(close + 1) == ':') {
                    int port = parsePort(input.substring(close + 2));
                    return new HostPort(host, port);
                }
                return new HostPort(host, -1);
            }
        }

        int colon = input.lastIndexOf(':');
        if (colon > 0 && input.indexOf(':') == colon) {
            String host = input.substring(0, colon);
            int port = parsePort(input.substring(colon + 1));
            if (port >= 0 && isHostLike(host)) {
                return new HostPort(host, port);
            }
        }
        return new HostPort(input, -1);
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            return port >= 0 && port <= 65535 ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isHostLike(String host) {
        return host != null && !host.isEmpty() && host.indexOf('/') < 0;
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    record HostPort(String host, int port) {
        static HostPort empty() {
            return new HostPort("", -1);
        }
    }
}
