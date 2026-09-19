package core.normalize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BodyNormalizer 单元测试
 * 验证 L0/L1/L2 三级递进归一化对动态内容的处理。
 */
class BodyNormalizerTest {

    // ===== L0 =====

    @Test
    @DisplayName("L0 应返回原文不做处理")
    void level0_shouldReturnOriginal() {
        String body = "{\"time\": 2026-09-19T10:00:00Z, id: 123456}";
        assertEquals(body, BodyNormalizer.normalize(body, 0));
    }

    @Test
    @DisplayName("null 输入应返回空串")
    void nullInput_shouldReturnEmpty() {
        assertEquals("", BodyNormalizer.normalize(null, 1));
    }

    // ===== L1 标准归一化 =====

    @Test
    @DisplayName("ISO-8601 日期应被替换")
    void isoDate_shouldBeReplaced() {
        String a = BodyNormalizer.normalize("{\"created\":\"2026-09-19T10:00:00Z\"}", 1);
        String b = BodyNormalizer.normalize("{\"created\":\"2025-01-01T08:30:00.123+08:00\"}", 1);
        assertEquals(a, b);
        assertTrue(a.contains("<DATE>"));
    }

    @Test
    @DisplayName("空格分隔的日期时间格式也应被替换")
    void spaceDateTime_shouldBeReplaced() {
        String a = BodyNormalizer.normalize("generated at 2026-09-19 10:00:00", 1);
        String b = BodyNormalizer.normalize("generated at 2020-01-01 23:59:59", 1);
        assertEquals(a, b);
    }

    @Test
    @DisplayName("epoch 毫秒与秒时间戳应被替换")
    void epoch_shouldBeReplaced() {
        String a = BodyNormalizer.normalize("{\"ts\":1758249600123,\"s\":1758249600}", 1);
        String b = BodyNormalizer.normalize("{\"ts\":1600000000000,\"s\":1600000000}", 1);
        assertEquals(a, b);
        assertTrue(a.contains("<EPOCH>"));
    }

    @Test
    @DisplayName("UUID（含大括号变体）应被替换")
    void uuid_shouldBeReplaced() {
        String a = BodyNormalizer.normalize("id=550e8400-e29b-41d4-a716-446655440000", 1);
        String b = BodyNormalizer.normalize("id={A1B2C3D4-E5F6-7890-ABCD-EF1234567890}", 1);
        assertEquals(a, b);
        assertTrue(a.contains("<UUID>"));
    }

    @Test
    @DisplayName("无连字符的 32 位 hex UUID 应被替换")
    void compactHexUuid_shouldBeReplaced() {
        String a = BodyNormalizer.normalize("trace=550e8400e29b41d4a716446655440000", 1);
        String b = BodyNormalizer.normalize("trace=aabbccddeeff00112233445566778899", 1);
        assertEquals(a, b);
    }

    @Test
    @DisplayName("JWT 形态串应被整体替换")
    void jwt_shouldBeReplaced() {
        String a = BodyNormalizer.normalize(
                "token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJVadQssw5c", 1);
        String b = BodyNormalizer.normalize(
                "token=eyJhbGciOiJSUzI1NiJ9.eyJ1c2VyIjoiYWRtaW4ifQ.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk", 1);
        assertEquals(a, b);
        assertTrue(a.contains("<JWT>"));
    }

    @Test
    @DisplayName("16 位以上 hex token 应被替换（40 位避开 32 位 UUID 变体）")
    void longHexToken_shouldBeReplaced() {
        String a = BodyNormalizer.normalize("csrf=0123456789abcdef0123456789abcdef0123456789", 1);
        String b = BodyNormalizer.normalize("csrf=ffffffffffffffffffffffffffffffffffffffff", 1);
        assertEquals(a, b);
        assertTrue(a.contains("<HEX>"));
    }

    @Test
    @DisplayName("恰好 32 位 hex 应作为紧凑 UUID 被替换")
    void compact32Hex_shouldBeReplacedAsUuid() {
        String a = BodyNormalizer.normalize("trace=550e8400e29b41d4a716446655440000", 1);
        assertTrue(a.contains("<UUID>"));
    }

    @Test
    @DisplayName("短 hex 不应被替换（避免误伤业务数据）")
    void shortHex_shouldNotBeReplaced() {
        String result = BodyNormalizer.normalize("color=#ff0000, count=12345", 1);
        assertTrue(result.contains("#ff0000"));
        assertTrue(result.contains("12345"));
    }

    @Test
    @DisplayName("连续空白应折叠为单个空格")
    void whitespace_shouldBeCollapsed() {
        String a = BodyNormalizer.normalize("hello   \n\t  world", 1);
        String b = BodyNormalizer.normalize("hello world", 1);
        assertEquals(a, b);
    }

    @Test
    @DisplayName("L1 不应清空易变 JSON key 的值")
    void level1_shouldNotTouchVolatileKeys() {
        String result = BodyNormalizer.normalize("{\"nonce\":\"abc123\"}", 1);
        assertTrue(result.contains("abc123"));
    }

    @Test
    @DisplayName("含多种动态内容的 JSON 响应应在 L1 后一致")
    void mixedDynamicJson_shouldBeIdenticalAfterL1() {
        String original = "{\"userId\":1001,\"created\":\"2026-09-19T10:00:00Z\","
                + "\"token\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123def456ghi789\","
                + "\"traceId\":\"550e8400-e29b-41d4-a716-446655440000\",\"name\":\"alice\"}";
        String replayed = "{\"userId\":1001,\"created\":\"2025-01-01T00:00:00.000Z\","
                + "\"token\":\"eyJhbGciOiJSUzI1NiJ9.eyJ1c2VyIjoiYm9iIn0.xyz789abc123def456ghi\","
                + "\"traceId\":\"aabbccdd-eeff-0011-2233-445566778899\",\"name\":\"alice\"}";
        assertEquals(BodyNormalizer.normalize(original, 1), BodyNormalizer.normalize(replayed, 1));
    }

    // ===== L2 激进归一化 =====

    @Test
    @DisplayName("易变 JSON key 的值应被清空")
    void volatileJsonKeys_shouldBeCleared() {
        String a = BodyNormalizer.normalize("{\"nonce\":\"xyz\",\"requestId\":12345}", 2);
        String b = BodyNormalizer.normalize("{\"nonce\":\"fff\",\"requestId\":67890}", 2);
        assertEquals(a, b);
        assertTrue(a.contains("<VOLATILE>"));
    }

    @Test
    @DisplayName("易变 key 匹配应大小写不敏感且支持驼峰")
    void volatileKeys_shouldMatchCamelCase() {
        String a = BodyNormalizer.normalize("{\"createdAt\":\"2026-01-01\"}", 2);
        assertTrue(a.contains("<VOLATILE>"));
    }

    @Test
    @DisplayName("长 base64 token 应在 L2 被替换（使用非 hex 字符避开 L1 hex 规则）")
    void longBase64_shouldBeReplacedAtL2() {
        String a = BodyNormalizer.normalize("data=zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz==", 2);
        String b = BodyNormalizer.normalize("data=QQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ==", 2);
        assertEquals(a, b);
        assertTrue(a.contains("<B64>"));
    }

    @Test
    @DisplayName("长纯数字串应在 L2 被替换")
    void longNumber_shouldBeReplacedAtL2() {
        String a = BodyNormalizer.normalize("{\"orderId\":100200300,\"code\":42}", 2);
        String b = BodyNormalizer.normalize("{\"orderId\":999888777,\"code\":42}", 2);
        assertEquals(a, b);
        assertTrue(a.contains("<NUM>"));
        assertFalse(b.contains("999888777"));
    }

    @Test
    @DisplayName("短数字串不应在 L2 被替换")
    void shortNumber_shouldNotBeReplacedAtL2() {
        String result = BodyNormalizer.normalize("{\"id\":1001}", 2);
        assertTrue(result.contains("1001"));
    }

    @Test
    @DisplayName("L2 归一化应包含 L1 的所有处理")
    void level2_shouldIncludeLevel1Processing() {
        String result = BodyNormalizer.normalize("time=2026-09-19T10:00:00Z trace=550e8400e29b41d4a716446655440000", 2);
        assertTrue(result.contains("<DATE>"));
        assertTrue(result.contains("<UUID>"));
    }

    // ===== 确定性 =====

    @Test
    @DisplayName("同一输入多次归一化结果应确定一致")
    void normalize_shouldBeDeterministic() {
        String body = "{\"ts\":1758249600123,\"nonce\":\"abc\"}";
        assertEquals(BodyNormalizer.normalize(body, 2), BodyNormalizer.normalize(body, 2));
    }

    @Test
    @DisplayName("空串输入应返回空串")
    void emptyInput_shouldReturnEmpty() {
        assertEquals("", BodyNormalizer.normalize("", 1));
        assertEquals("", BodyNormalizer.normalize("", 2));
    }
}
