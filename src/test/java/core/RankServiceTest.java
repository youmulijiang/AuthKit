package core;

import model.MessageDataModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RankService 单元测试
 * 验证递进归一化评分体系（StatusCode 30 / Hash 50 递进折扣 / Length 20）。
 */
class RankServiceTest {

    /** 构建带响应体的模型 */
    private MessageDataModel buildModel(int statusCode, String body) {
        MessageDataModel model = new MessageDataModel();
        model.setStatusCode(statusCode);
        model.setBody(body);
        if (body != null) {
            model.setLength(body.length());
        }
        return model;
    }

    // ===== 基础场景 =====

    @Test
    @DisplayName("响应体原文完全相同应得 100 分")
    void identicalBody_shouldReturn100() {
        String body = "{\"userId\":1001,\"name\":\"alice\"}";
        assertEquals(100, RankService.calculateRank(buildModel(200, body), buildModel(200, body)));
    }

    @Test
    @DisplayName("状态码不同应扣除 30 分")
    void differentStatusCode_shouldLose30() {
        String body = "{\"userId\":1001}";
        assertEquals(70, RankService.calculateRank(buildModel(200, body), buildModel(403, body)));
    }

    @Test
    @DisplayName("完全不同的响应应得低分")
    void totallyDifferent_shouldReturnLow() {
        MessageDataModel original = buildModel(200,
                "{\"data\":{\"userId\":1001,\"profile\":\"complete admin profile with lots of sensitive content here\"}}");
        MessageDataModel target = buildModel(403, "{\"error\":\"denied\"}");
        assertTrue(RankService.calculateRank(original, target) <= 10,
                "Expected low rank but got: " + RankService.calculateRank(original, target));
    }

    @Test
    @DisplayName("null original 应返回 0")
    void nullOriginal_shouldReturn0() {
        assertEquals(0, RankService.calculateRank(null, buildModel(200, "body")));
    }

    @Test
    @DisplayName("null target 应返回 0")
    void nullTarget_shouldReturn0() {
        assertEquals(0, RankService.calculateRank(buildModel(200, "body"), null));
    }

    @Test
    @DisplayName("body 均为 null 且状态码相同应得满分（空响应等同）")
    void bothBodyNull_shouldGetFullHashScore() {
        assertEquals(100, RankService.calculateRank(buildModel(200, null), buildModel(200, null)));
    }

    @Test
    @DisplayName("ORIGINAL_RANK 常量应为 100")
    void originalRank_shouldBe100() {
        assertEquals(100, RankService.ORIGINAL_RANK);
    }

    // ===== L0 / L1 / L2 递进折扣 =====

    @Test
    @DisplayName("仅时间戳不同的响应应在 L1 命中，得 92.5 分")
    void dynamicTimestampOnly_shouldHitL1() {
        String original = "{\"userId\":1001,\"created\":\"2026-09-19T10:00:00Z\"}";
        String replayed = "{\"userId\":1001,\"created\":\"2025-01-01T08:30:00.123+08:00\"}";
        // 30 + 42.5 + 20 = 92.5 → 93
        int rank = RankService.calculateRank(buildModel(200, original), buildModel(200, replayed));
        assertEquals(93, rank);
    }

    @Test
    @DisplayName("仅 JWT/UUID 不同的响应应在 L1 命中")
    void dynamicTokenOnly_shouldHitL1() {
        String original = "token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJVadQssw5c";
        String replayed = "token=eyJhbGciOiJSUzI1NiJ9.eyJ1c2VyIjoiYWRtaW4ifQ.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertEquals(93, RankService.calculateRank(buildModel(200, original), buildModel(200, replayed)));
    }

    @Test
    @DisplayName("仅易变 JSON key 值不同应在 L2 命中，得 85 分")
    void volatileKeyOnly_shouldHitL2() {
        String original = "{\"userId\":1001,\"nonce\":\"aaa\",\"traceId\":111}";
        String replayed = "{\"userId\":1001,\"nonce\":\"bbb\",\"traceId\":222}";
        // 30 + 35 + 20 = 85
        assertEquals(85, RankService.calculateRank(buildModel(200, original), buildModel(200, replayed)));
    }

    @Test
    @DisplayName("L1 命中的分数应低于 L0 命中（折扣递进）")
    void l1Hit_shouldScoreLowerThanL0() {
        String base = "{\"userId\":1001,";
        String l0a = base + "\"x\":\"a\"}";
        String l1a = base + "\"created\":\"2026-01-01T00:00:00Z\"}";
        String l1b = base + "\"created\":\"2020-01-01T00:00:00Z\"}";
        int l0 = RankService.calculateRank(buildModel(200, l0a), buildModel(200, l0a));
        int l1 = RankService.calculateRank(buildModel(200, l1a), buildModel(200, l1b));
        assertEquals(100, l0);
        assertTrue(l1 < l0, "L1 hit should score lower than L0 hit");
    }

    @Test
    @DisplayName("L2 命中的分数应低于 L1 命中（折扣递进）")
    void l2Hit_shouldScoreLowerThanL1() {
        String l1a = "{\"userId\":1001,\"created\":\"2026-01-01T00:00:00Z\"}";
        String l1b = "{\"userId\":1001,\"created\":\"2020-01-01T00:00:00Z\"}";
        String l2a = "{\"userId\":1001,\"nonce\":\"aaa\"}";
        String l2b = "{\"userId\":1001,\"nonce\":\"bbb\"}";
        int l1 = RankService.calculateRank(buildModel(200, l1a), buildModel(200, l1b));
        int l2 = RankService.calculateRank(buildModel(200, l2a), buildModel(200, l2b));
        assertTrue(l2 < l1, "L2 hit should score lower than L1 hit");
    }

    // ===== 典型越权场景 =====

    @Test
    @DisplayName("典型 API 越权（响应带时间戳）应达到越权阈值")
    void apiBypassWithDynamicContent_shouldReachThreshold() {
        String original = "{\"code\":0,\"data\":{\"userId\":1001,\"role\":\"admin\"},"
                + "\"serverTime\":\"2026-09-19T10:00:00Z\",\"traceId\":\"550e8400-e29b-41d4-a716-446655440000\"}";
        String replayed = "{\"code\":0,\"data\":{\"userId\":1001,\"role\":\"admin\"},"
                + "\"serverTime\":\"2025-03-08T22:15:30.456Z\",\"traceId\":\"aabbccdd-eeff-0011-2233-445566778899\"}";
        int rank = RankService.calculateRank(buildModel(200, original), buildModel(200, replayed));
        assertTrue(rank >= RankService.UNAUTHORIZED_THRESHOLD,
                "API bypass with dynamic content should reach threshold, got: " + rank);
    }

    @Test
    @DisplayName("典型未授权拦截（403 + 错误页）应远低于阈值")
    void typicalUnauthorizedBlock_shouldBeLow() {
        MessageDataModel original = buildModel(200,
                "{\"data\":{\"userId\":1001,\"profile\":\"full user profile data ...\"}}");
        MessageDataModel target = buildModel(403,
                "{\"error\":\"Unauthorized\",\"message\":\"access denied\"}");
        int rank = RankService.calculateRank(original, target);
        assertTrue(rank < 30, "Unauthorized block should be low, got: " + rank);
    }

    @Test
    @DisplayName("同 200 但内容不同的 SPA 错误壳不应达到阈值")
    void sameStatusDifferentContent_shouldNotReachThreshold() {
        MessageDataModel original = buildModel(200,
                "{\"profile\":\"complete admin profile with sensitive fields a b c d e f g\"}");
        MessageDataModel target = buildModel(200,
                "{\"profile\":\"minimal public view with totally different content x y z\"}");
        int rank = RankService.calculateRank(original, target);
        assertTrue(rank < RankService.UNAUTHORIZED_THRESHOLD,
                "Different content should not reach threshold, got: " + rank);
    }

    // ===== 长度评分 =====

    @Test
    @DisplayName("长度相近的响应体长度评分应接近满分")
    void closeLength_shouldGetHighLengthScore() {
        double score = RankService.scoreLength(1500, 1480);
        assertTrue(score >= 19.0, "Expected near-full length score but got: " + score);
    }

    @Test
    @DisplayName("长度差异较大时长度评分应较低")
    void largeLengthDifference_shouldReturnLowLengthScore() {
        // length: 1000 vs 100 → similarity = 0.1 → 0.1 * 20 = 2
        double score = RankService.scoreLength(1000, 100);
        assertEquals(2.0, score, 0.01);
    }

    @Test
    @DisplayName("两者长度都为 0 时长度评分应满分")
    void bothLengthZero_shouldGetFullLengthScore() {
        assertEquals(20.0, RankService.scoreLength(0, 0), 0.01);
    }

    @Test
    @DisplayName("归一化后长度评分使用 L1 文本长度")
    void lengthScore_shouldUseNormalizedLength() {
        // 两个响应仅时间戳位数不同，L1 归一化后长度完全一致 → 长度满分
        String a = "{\"ts\":\"2026-09-19T10:00:00Z\",\"pad\":\"\"}";
        String b = "{\"ts\":\"2025-01-01T08:30:00.123+08:00\",\"pad\":\"\"}";
        MessageDataModel original = buildModel(200, a);
        MessageDataModel target = buildModel(200, b);
        int rank = RankService.calculateRank(original, target);
        // 30 + 42.5 + 20 = 92.5 → 93（若用原始长度会比较 44 vs 50，扣分）
        assertEquals(93, rank);
    }
}
