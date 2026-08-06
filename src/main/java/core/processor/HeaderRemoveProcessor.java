package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;
import model.ConfigModel;
import utils.HttpHeaderUtils;

import java.util.List;

/**
 * 认证头移除处理器
 * 用于未授权场景：配置非空时只按配置移除，配置为空时回退到内置鉴权头关键字。
 */
public class HeaderRemoveProcessor implements RequestProcessor {

    private final ConfigModel configModel;

    /**
     * 构造认证头移除处理器
     *
     * @param configModel 插件配置模型，提供需要额外移除的认证头列表
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
     * 按配置或默认规则移除认证头
     */
    @Override
    public HttpRequest process(HttpRequest request, AuthUserModel user) {
        List<String> authHeaders = configModel.getAuthHeaders();
        return HttpHeaderUtils.removeAuthHeaders(request, authHeaders);
    }
}
