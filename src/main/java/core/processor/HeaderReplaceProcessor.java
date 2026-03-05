package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;

import java.util.Map;

/**
 * 认证头替换处理器
 * 将请求中的认证头替换为用户配置的值。
 * 使用 withHeader 方法：头存在则更新，不存在则添加。
 */
public class HeaderReplaceProcessor implements RequestProcessor {

    /**
     * 用户启用了头替换且有配置头时才启用
     */
    @Override
    public boolean isEnabled(AuthUserModel user) {
        return user.isHeaderReplaceEnabled() && !user.getHeaders().isEmpty();
    }

    /**
     * 替换请求中的认证头为用户配置的值
     */
    @Override
    public HttpRequest process(HttpRequest request, AuthUserModel user) {
        Map<String, String> headers = user.getHeaders();
        HttpRequest current = request;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            current = current.withHeader(entry.getKey(), entry.getValue());
        }
        return current;
    }
}

