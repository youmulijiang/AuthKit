package core.processor;

import burp.api.montoya.http.message.requests.HttpRequest;
import model.AuthUserModel;

/**
 * 请求处理器接口（责任链模式）
 * 每个处理器负责对原始请求进行一种变换（如替换认证头、替换参数等）。
 * 处理器是否执行由用户配置决定，通过 isEnabled 方法判断。
 */
public interface RequestProcessor {

    /**
     * 判断该处理器是否对指定用户启用
     *
     * @param user 鉴权用户配置
     * @return true 表示启用，将执行 process 方法
     */
    boolean isEnabled(AuthUserModel user);

    /**
     * 处理请求，返回变换后的新请求
     *
     * @param request 当前请求（可能已被前序处理器变换过）
     * @param user    鉴权用户配置
     * @return 变换后的新请求
     */
    HttpRequest process(HttpRequest request, AuthUserModel user);
}

