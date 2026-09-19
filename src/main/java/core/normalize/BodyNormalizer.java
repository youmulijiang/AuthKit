package core.normalize;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 响应体递进归一化器（纯函数，无状态）
 * <p>
 * 用于消除响应中的动态内容（时间戳、nonce、token、UUID 等），
 * 使哈希比较聚焦于业务数据差异。定义三个递进级别：
 * <ul>
 *   <li>L0：原文，不做处理</li>
 *   <li>L1 标准：空白折叠、ISO-8601 日期、epoch 时间戳、UUID、长 hex token、JWT 形态串。
 *       覆盖大部分动态噪声，不触碰业务数据</li>
 *   <li>L2 激进：L1 基础上追加长 base64 token、易变 JSON key 值清空、长纯数字串。
 *       可能误伤真实业务差异（如自增 ID），由评分降权补偿</li>
 * </ul>
 * 每级输出确定性结果，供 RankService 递进比较使用。
 */
public final class BodyNormalizer {

    /** L2 激进归一化时被清空的易变 JSON key（小写） */
    private static final List<String> VOLATILE_JSON_KEYS = List.of(
            "timestamp", "createdat", "updatedat", "createtime", "updatetime",
            "nonce", "csrf", "csrftoken", "xsrf", "traceid", "requestid",
            "correlationid", "sessionid", "expiresat", "expiresin", "issuedat",
            "jti", "requesttoken", "refreshtoken", "accesstoken"
    );

    // ===== L1 模式 =====

    /** 空白折叠：连续空白（含换行）压为单个空格 */
    private static final Pattern P_WHITESPACE = Pattern.compile("\\s+");

    /** ISO-8601 日期时间：2026-09-19T12:34:56(.789)?(Z|[+-]HH:MM)? 或 2026-09-19 12:34:56 */
    private static final Pattern P_ISO_DATE = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?");

    /** epoch 毫秒（13 位）或秒（10 位，限 2001~2286 年区间避免误伤普通长数字） */
    private static final Pattern P_EPOCH = Pattern.compile(
            "\\b(?:1\\d{12}|[12]\\d{9})\\b");

    /** UUID（含大括号/无连字符变体） */
    private static final Pattern P_UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
                    + "|\\b[0-9a-fA-F]{32}\\b"
                    + "|\\{[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\}");

    /** 长 hex token：连续 16 位以上 hex 字符（UUID 归一化后剩余的散列/摘要形态） */
    private static final Pattern P_HEX_TOKEN = Pattern.compile(
            "\\b[0-9a-fA-F]{16,}\\b");

    /** JWT 形态串：三段 base64url，第二段包含点分隔 */
    private static final Pattern P_JWT = Pattern.compile(
            "\\bey[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}\\b");

    // ===== L2 模式 =====

    /** 长 base64 token：连续 40 位以上 base64url 字符 */
    private static final Pattern P_BASE64_TOKEN = Pattern.compile(
            "\\b[A-Za-z0-9+/=_-]{40,}\\b");

    /** 长纯数字串：6 位以上（自增 ID、订单号等，误伤风险由降权补偿） */
    private static final Pattern P_LONG_NUMBER = Pattern.compile(
            "\\b\\d{6,}\\b");

    /** 易变 JSON key 的值清空：匹配 "key":"value" 或 "key":123 等（JSON 与松散 JS 文本通用） */
    private static final Pattern P_VOLATILE_JSON_VALUE = buildVolatileJsonValuePattern();

    private BodyNormalizer() {
    }

    /**
     * 按指定级别归一化响应体
     *
     * @param body  响应体原文
     * @param level 归一化级别（0=原文，1=标准，2=激进）
     * @return 归一化后的文本，输入为 null 时返回空串
     */
    public static String normalize(String body, int level) {
        if (body == null) {
            return "";
        }
        if (level <= 0) {
            return body;
        }

        // L1 标准归一化
        String current = body;
        current = P_JWT.matcher(current).replaceAll("<JWT>");
        current = P_ISO_DATE.matcher(current).replaceAll("<DATE>");
        current = P_UUID.matcher(current).replaceAll("<UUID>");
        current = P_EPOCH.matcher(current).replaceAll("<EPOCH>");
        current = P_HEX_TOKEN.matcher(current).replaceAll("<HEX>");
        current = P_WHITESPACE.matcher(current).replaceAll(" ");

        if (level < 2) {
            return current;
        }

        // L2 激进归一化
        current = P_VOLATILE_JSON_VALUE.matcher(current).replaceAll("$1:\"<VOLATILE>\"");
        current = P_BASE64_TOKEN.matcher(current).replaceAll("<B64>");
        current = P_LONG_NUMBER.matcher(current).replaceAll("<NUM>");
        return current;
    }

    /**
     * 构建易变 JSON key 值清空模式。
     * 形如 "timestamp":"..."、"createdAt":169...、"expires_in":3600，
     * key 名大小写不敏感，捕获组保留 key 原文以便替换时回填。
     */
    private static Pattern buildVolatileJsonValuePattern() {
        StringBuilder keyPattern = new StringBuilder();
        for (int i = 0; i < VOLATILE_JSON_KEYS.size(); i++) {
            if (i > 0) {
                keyPattern.append('|');
            }
            keyPattern.append(Pattern.quote(VOLATILE_JSON_KEYS.get(i)));
        }
        // "key"(可选下划线/驼峰由枚举覆盖) : (value)
        return Pattern.compile(
                "([\"']?(?:" + keyPattern + ")['\"]?)\\s*:\\s*(?:\"[^\"]*\"|'[^']*'|[0-9.eE+-]+)",
                Pattern.CASE_INSENSITIVE);
    }
}
