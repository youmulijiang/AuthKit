package utils;

/**
 * HTTP 原始报文文本工具类（纯函数，无状态）
 * 用于解析原始 HTTP 请求/响应文本字符串中的字段信息。
 */
public final class HttpTextUtils {

    private HttpTextUtils() {
    }

    /**
     * 从原始 HTTP 请求报文文本中提取 Host 头的值
     *
     * @param requestText 原始 HTTP 请求报文文本
     * @return Host 头的值，未找到返回 null
     */
    public static String extractHost(String requestText) {
        if (requestText == null || requestText.isEmpty()) {
            return null;
        }
        for (String line : requestText.split("\r?\n")) {
            if (line.toLowerCase().startsWith("host:")) {
                return line.substring(5).trim();
            }
        }
        return null;
    }
}
