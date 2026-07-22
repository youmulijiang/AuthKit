package utils;

/**
 * URL/路径工具类（纯函数，无状态）
 * 提供 URL 路径解析、文件后缀提取等通用方法。
 */
public final class UrlPathUtils {

    private UrlPathUtils() {
    }

    /**
     * 从请求路径中提取文件后缀（不含点号，已转小写）
     * <p>
     * 例如："/api/file.js?v=1" → "js"，"/api/user" → ""
     *
     * @param path 请求路径（含或不含查询参数均可）
     * @return 文件后缀（小写），无后缀返回空字符串
     */
    public static String extractExtension(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        // 去掉查询参数
        int queryIndex = path.indexOf('?');
        String cleanPath = queryIndex >= 0 ? path.substring(0, queryIndex) : path;
        int dotIndex = cleanPath.lastIndexOf('.');
        int slashIndex = cleanPath.lastIndexOf('/');
        if (dotIndex > slashIndex && dotIndex < cleanPath.length() - 1) {
            return cleanPath.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}
