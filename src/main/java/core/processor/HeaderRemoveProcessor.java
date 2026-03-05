package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;
import model.ConfigModel;

import java.util.List;

/**
 * 认证头移除处理器
 * 用于未授权场景，移除 ConfigModel 中配置的所有认证头（如 Cookie、Authorization、Token）。
 */
public class HeaderRemoveProcessor implements RequestProcessor {

    private final ConfigModel configModel;

    /**
     * 构造认证头移除处理器
     *
     * @param configModel 插件配置模型，提供需要移除的认证头列表
     */
    public HeaderRemoveProcessor(ConfigModel configModel) {
        this.configModel = configModel;
    }

    /**
     * 未授权场景始终启用
     */
    @Override
    public boolean isEnabled(AuthUserModel user) {
        return true;
    }

    /**
     * 移除配置中指定的所有认证头
     */
    @Override
    public HttpRequest process(HttpRequest request, AuthUserModel user) {
        List<String> authHeaders = configModel.getAuthHeaders();
        HttpRequest current = request;
        for (String headerName : authHeaders) {
            current = current.withRemovedHeader(headerName);
        }
        return current;
    }
}

