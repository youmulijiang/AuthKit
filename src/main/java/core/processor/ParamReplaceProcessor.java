package core.processor;

import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;

import java.util.List;
import java.util.Map;

/**
 * 参数替换处理器
 * 将请求中匹配的参数值替换为用户配置的新值。
 * 支持 URL 参数和 Body 参数，根据原始参数的类型保持一致。
 */
public class ParamReplaceProcessor implements RequestProcessor {

    /**
     * 用户启用了参数替换且有配置参数时才启用
     */
    @Override
    public boolean isEnabled(AuthUserModel user) {
        return user.isParamReplaceEnabled() && !user.getParams().isEmpty();
    }

    /**
     * 替换请求中匹配的参数值
     * 遍历用户配置的参数替换规则，在请求的现有参数中查找同名参数，
     * 使用 withParameter 方法替换其值（保持原参数类型不变）。
     */
    @Override
    public HttpRequest process(HttpRequest request, AuthUserModel user) {
        Map<String, String> paramReplacements = user.getParams();
        List<ParsedHttpParameter> existingParams = request.parameters();

        HttpRequest current = request;
        for (Map.Entry<String, String> entry : paramReplacements.entrySet()) {
            String paramName = entry.getKey();
            String newValue = entry.getValue();

            // 在现有参数中查找同名参数，获取其类型
            for (ParsedHttpParameter existing : existingParams) {
                if (existing.name().equals(paramName)) {
                    HttpParameter newParam = createParameter(paramName, newValue, existing.type());
                    current = current.withUpdatedParameters(newParam);
                    break;
                }
            }
        }
        return current;
    }

    /**
     * 根据参数类型创建对应的 HttpParameter
     */
    private HttpParameter createParameter(String name, String value, HttpParameterType type) {
        return switch (type) {
            case URL -> HttpParameter.urlParameter(name, value);
            case BODY -> HttpParameter.bodyParameter(name, value);
            case COOKIE -> HttpParameter.cookieParameter(name, value);
            default -> HttpParameter.urlParameter(name, value);
        };
    }
}

