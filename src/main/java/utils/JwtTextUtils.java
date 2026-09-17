package utils;

import com.auth0.jwt.JWT;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT 文本工具
 * 提供 JWT 正则提取、合法性校验与 JSON 美化格式化，
 * 供 JWT 请求/响应编辑器选项卡等 UI 共用。
 */
public final class JwtTextUtils {

    /** JWT 正则：匹配三段式 Base64URL token。不能限定 eyJ，否则格式化 JSON 重新编码后可能无法识别。 */
    public static final Pattern JWT_PATTERN =
            Pattern.compile("([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*)");

    private JwtTextUtils() {
    }

    /**
     * 从任意文本中提取所有合法的 JWT（按出现顺序，允许重复）。
     *
     * @param text 待扫描文本，null 返回空列表
     * @return 合法 JWT 列表
     */
    public static List<String> extractJwts(CharSequence text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) {
            return tokens;
        }
        Matcher matcher = JWT_PATTERN.matcher(text);
        while (matcher.find()) {
            String jwt = matcher.group(1);
            if (isValidJwt(jwt)) {
                tokens.add(jwt);
            }
        }
        return tokens;
    }

    /** 校验字符串是否为可解码的 JWT（三段结构 + Base64URL 可解）。 */
    public static boolean isValidJwt(String jwt) {
        if (jwt == null || jwt.isEmpty()) {
            return false;
        }
        try {
            JWT.decode(jwt);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 简单 JSON 格式化（缩进美化），非 JSON 输入原样返回。 */
    public static String formatJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (!inString) {
                switch (c) {
                    case '{', '[' -> {
                        sb.append(c);
                        sb.append('\n');
                        indent++;
                        sb.append("  ".repeat(indent));
                    }
                    case '}', ']' -> {
                        sb.append('\n');
                        indent--;
                        sb.append("  ".repeat(Math.max(0, indent)));
                        sb.append(c);
                    }
                    case ',' -> {
                        sb.append(c);
                        sb.append('\n');
                        sb.append("  ".repeat(indent));
                    }
                    case ':' -> sb.append(": ");
                    case ' ', '\t', '\n', '\r' -> { /* skip */ }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
