package core;

import model.MessageDataModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RankService 单元测试
 */
class RankServiceTest {

    private MessageDataModel buildModel(int statusCode, int length, int hash, int attrCount) {
        MessageDataModel model = new MessageDataModel();
        model.setStatusCode(statusCode);
        model.setLength(length);
        model.setHash(hash);
        model.setAttributeCount(attrCount);
        return model;
    }

    @Test
    @DisplayName("完全相同的响应应得 100 分")
    void identicalResponse_shouldReturn100() {
        MessageDataModel original = buildModel(200, 1500, 12345, 8);
        MessageDataModel target = buildModel(200, 1500, 12345, 8);
        assertEquals(100, RankService.calculateRank(original, target));
    }

    @Test
    @DisplayName("完全不同的响应应得 0 分")
    void totallyDifferent_shouldReturn0() {
        MessageDataModel original = buildModel(200, 1500, 12345, 8);
        MessageDataModel target = buildModel(403, 0, 99999, 0);
        assertEquals(0, RankService.calculateRank(original, target));
    }

    @Test
    @DisplayName("仅状态码相同应得约 30 分")
    void onlyStatusCodeSame_shouldReturn30() {
        MessageDataModel original = buildModel(200, 1500, 12345, 8);
        MessageDataModel target = buildModel(200, 0, 99999, 0);
        assertEquals(30, RankService.calculateRank(original, target));
    }

    @Test
    @DisplayName("仅哈希相同应得约 25 分")
    void onlyHashSame_shouldReturn25() {
        MessageDataModel original = buildModel(200, 1500, 12345, 8);
        MessageDataModel target = buildModel(403, 0, 12345, 0);
        assertEquals(25, RankService.calculateRank(original, target));
    }

    @Test
    @DisplayName("状态码和哈希相同、长度相近应得高分")
    void statusAndHashSame_lengthClose_shouldReturnHigh() {
        MessageDataModel original = buildModel(200, 1500, 12345, 8);
        MessageDataModel target = buildModel(200, 1480, 12345, 7);
        int rank = RankService.calculateRank(original, target);
        // 30 + ~29.6 + 25 + ~13.1 ≈ 98
        assertTrue(rank >= 95, "Expected high rank but got: " + rank);
    }

    @Test
    @DisplayName("典型未授权拦截场景应得低分")
    void typicalUnauthorized_shouldReturnLow() {
        MessageDataModel original = buildModel(200, 1500, 12345, 8);
        MessageDataModel target = buildModel(403, 50, 99999, 2);
        int rank = RankService.calculateRank(original, target);
        // 0 + 1.0 + 0 + 3.75 ≈ 5
        assertTrue(rank <= 10, "Expected low rank but got: " + rank);
    }

    @Test
    @DisplayName("null original 应返回 0")
    void nullOriginal_shouldReturn0() {
        MessageDataModel target = buildModel(200, 100, 123, 5);
        assertEquals(0, RankService.calculateRank(null, target));
    }

    @Test
    @DisplayName("null target 应返回 0")
    void nullTarget_shouldReturn0() {
        MessageDataModel original = buildModel(200, 100, 123, 5);
        assertEquals(0, RankService.calculateRank(original, null));
    }

    @Test
    @DisplayName("两者长度都为 0 时长度评分应满分")
    void bothLengthZero_shouldGetFullLengthScore() {
        MessageDataModel original = buildModel(200, 0, 12345, 0);
        MessageDataModel target = buildModel(200, 0, 12345, 0);
        assertEquals(100, RankService.calculateRank(original, target));
    }

    @Test
    @DisplayName("ORIGINAL_RANK 常量应为 100")
    void originalRank_shouldBe100() {
        assertEquals(100, RankService.ORIGINAL_RANK);
    }

    @Test
    @DisplayName("长度差异较大时长度评分应较低")
    void largeLengthDifference_shouldReturnLowLengthScore() {
        // length: 1000 vs 100 → similarity = 1 - 900/1000 = 0.1 → 0.1 * 30 = 3
        double score = RankService.scoreLength(1000, 100);
        assertEquals(3.0, score, 0.01);
    }

    @Test
    @DisplayName("attribute 差异较大时 attribute 评分应较低")
    void largeAttributeDifference_shouldReturnLowAttrScore() {
        // attr: 10 vs 2 → similarity = 1 - 8/10 = 0.2 → 0.2 * 15 = 3
        double score = RankService.scoreAttribute(10, 2);
        assertEquals(3.0, score, 0.01);
    }
}

