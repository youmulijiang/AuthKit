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
            String host = extractHost(request.url());
            if (shouldFilterDomain(host)) {
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
        return !config.getDomains().contains(domain);
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
     * 从 URL 中提取主机名
     */
    private String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
