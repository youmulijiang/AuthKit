package core;

/**
 * 哈希计算服务
 * 使用 Java 原生 hashCode() 快速计算响应体哈希，用于比较响应体是否相同。
 */
public final class HashService {

    private HashService() {
    }

    /**
     * 计算字符串的 hashCode
     *
     * @param content 输入字符串（通常为响应体 bodyToString）
     * @return hashCode 值，null 输入返回 0
     */
    public static int hash(String content) {
        if (content == null) {
            return 0;
        }
        return content.hashCode();
    }
}

