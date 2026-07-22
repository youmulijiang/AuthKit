package core.service;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.requests.HttpRequest;

/**
 * 请求过滤策略接口（策略模式）
 * <p>
 * 封装"某条请求是否应被插件处理"的判定规则。将过滤决策从数据模型中抽离，
 * 便于替换实现或在单元测试中注入 mock。
 */
public interface RequestFilter {

    /**
     * 综合判定请求是否应被处理（通过所有过滤条件）
     *
     * @param request    原始请求
     * @param statusCode 响应状态码
     * @param toolType   请求来源 ToolType（可为 null）
     * @return true 表示应处理，false 表示应过滤
     */
    boolean shouldProcess(HttpRequest request, int statusCode, ToolType toolType);

    /** 判定是否应按域名过滤 */
    boolean shouldFilterDomain(String domain);

    /** 判定是否应按 HTTP 方法过滤 */
    boolean shouldFilterMethod(String method);

    /** 判定是否应按路径过滤 */
    boolean shouldFilterPath(String path);

    /** 判定是否应按状态码过滤 */
    boolean shouldFilterStatusCode(int statusCode);

    /** 判定是否应按文件后缀黑名单过滤 */
    boolean shouldFilterExtension(String path);

    /** 判定是否应按 Tool Type 过滤 */
    boolean shouldFilterToolType(ToolType toolType);
}
