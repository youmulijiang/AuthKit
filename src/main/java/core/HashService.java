package core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 哈希计算服务
 * 提供 MD5 哈希计算，用于快速比较响应体是否相同。
 */
public final class HashService {

    private HashService() {
    }

    /**
     * 计算字符串的 MD5 哈希值
     *
     * @param content 输入字符串
     * @return 小写十六进制哈希字符串，null 输入返回空字符串
     */
    public static String md5(String content) {
        if (content == null) {
            return "";
        }
        return md5(content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5 哈希值
     *
     * @param data 输入字节数组
     * @return 小写十六进制哈希字符串，null 输入返回空字符串
     */
    public static String md5(byte[] data) {
        if (data == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * 字节数组转小写十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

