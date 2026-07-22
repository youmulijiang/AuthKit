package utils;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 请求头工具类（纯函数，无状态）
 * 提供鉴权头识别、提取、替换、删除等通用方法。
 * 被 AuthContextMenuProvider、AuthHistoryService、ContextMenuController 等共同复用。
 */
public final class HttpHeaderUtils {

    /**
     * 常见鉴权头关键字（小写模糊匹配），请求头名称包含任一关键字即视为鉴权头。
     */
    public static final List<String> AUTH_HEADER_KEYWORDS = List.of(
            "cookie", "token", "authorization", "auth",
            "session", "jwt", "bearer", "api-key", "apikey",
            "x-csrf", "x-xsrf", "access-key", "accesskey",
            "secret"
    );

    private HttpHeaderUtils() {
    }

    /**
     * 判断请求头名称是否为鉴权头（模糊匹配）
     *
     * @param headerName 请求头名称
     * @return 包含任一鉴权关键字则返回 true
     */
    public static boolean isAuthHeader(String headerName) {
        if (headerName == null || headerName.isEmpty()) {
            return false;
        }
        String lowerName = headerName.toLowerCase();
        for (String keyword : AUTH_HEADER_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从选中的请求列表中提取所有鉴权头文本（每行一条，格式：Name: Value）
     *
     * @param selectedItems 请求响应列表
     * @return 提取到的鉴权头文本，未找到返回空字符串
     */
    public static String extractAuthHeaders(List<HttpRequestResponse> selectedItems) {
        StringBuilder sb = new StringBuilder();
        for (HttpRequestResponse reqResp : selectedItems) {
            HttpRequest request = reqResp.request();
            if (request == null) {
                continue;
            }
            for (HttpHeader header : request.headers()) {
                if (isAuthHeader(header.name())) {
                    sb.append(header.name()).append(": ").append(header.value()).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 将选中请求的鉴权头格式化为 curl -H 参数（各字段以英文逗号隔开）
     *
     * @param selectedItems 请求响应列表
     * @return curl 格式的鉴权头字符串
     */
    public static String extractAuthHeadersAsCurl(List<HttpRequestResponse> selectedItems) {
        List<String> parts = new ArrayList<>();
        for (HttpRequestResponse reqResp : selectedItems) {
            HttpRequest request = reqResp.request();
            if (request == null) {
                continue;
            }
            for (HttpHeader header : request.headers()) {
                if (isAuthHeader(header.name())) {
                    parts.add(header.name() + ":" + header.value() + ";");
                }
            }
        }
        return String.join(",", parts);
    }

    /**
     * 将 source 请求的所有鉴权头应用到 original 请求（存在则替换，不存在则追加）
     *
     * @param original 原始请求
     * @param source   鉴权头来源请求
     * @return 替换后的新 HttpRequest 对象
     */
    public static HttpRequest replaceAuthHeaders(HttpRequest original, HttpRequest source) {
        HttpRequest updated = original;
        for (HttpHeader header : source.headers()) {
            if (isAuthHeader(header.name())) {
                updated = updated.withHeader(header.name(), header.value());
            }
        }
        return updated;
    }

    /**
     * 删除请求中的所有鉴权头
     *
     * @param original 原始请求
     * @return 移除所有鉴权头后的新 HttpRequest 对象
     */
    public static HttpRequest removeAuthHeaders(HttpRequest original) {
        List<HttpHeader> toRemove = new ArrayList<>();
        for (HttpHeader header : original.headers()) {
            if (isAuthHeader(header.name())) {
                toRemove.add(header);
            }
        }
        return toRemove.isEmpty() ? original : original.withRemovedHeaders(toRemove);
    }

    /**
     * 构建请求鉴权头的去重键（用于代理历史鉴权上下文去重）
     * 将所有鉴权头 name=value 排序后拼接，相同组合视为同一鉴权上下文。
     *
     * @param request HTTP 请求
     * @return 去重键字符串，无鉴权头时返回空字符串
     */
    public static String buildAuthDeduplicationKey(HttpRequest request) {
        List<String> parts = new ArrayList<>();
        for (HttpHeader header : request.headers()) {
            if (isAuthHeader(header.name())) {
                parts.add(header.name().toLowerCase() + "=" + header.value());
            }
        }
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }
}
