package core;

import model.MessageDataModel;

/**
 * 鉴权风险评分服务
 * 使用加权多维度评分算法，将目标鉴权对象的响应与 Original 进行比较，
 * 量化越权风险。分数越高，越可能存在越权漏洞。
 * Original 固定 100 分。
 *
 * 评分维度及权重：
 * - StatusCode:  状态码相同 → 满分，不同 → 0          权重 0.30
 * - Length:      响应体长度相似度归一化               权重 0.30
 * - Hash:        响应体哈希完全相同 → 满分，不同 → 0  权重 0.25
 * - Attribute:   attributes 个数相似度归一化           权重 0.15
 */
public final class RankService {

    /** Original 固定分数 */
    public static final int ORIGINAL_RANK = 100;

    // 各维度满分
    private static final double STATUS_CODE_MAX = 30.0;
    private static final double LENGTH_MAX = 30.0;
    private static final double HASH_MAX = 25.0;
    private static final double ATTRIBUTE_MAX = 15.0;

    private RankService() {
    }

    /**
     * 计算目标鉴权对象相对于 Original 的越权风险分数
     *
     * @param original Original 的 MessageDataModel
     * @param target   目标鉴权对象的 MessageDataModel
     * @return 0~100 的风险分数
     */
    public static int calculateRank(MessageDataModel original, MessageDataModel target) {
        if (original == null || target == null) {
            return 0;
        }

        double score = 0.0;

        // 1. 状态码评分：相同得满分，不同得 0
        score += scoreStatusCode(original.getStatusCode(), target.getStatusCode());

        // 2. 包长度评分：归一化相似度
        score += scoreLength(original.getLength(), target.getLength());

        // 3. 哈希评分：完全相同得满分，不同得 0
        score += scoreHash(original.getHash(), target.getHash());

        // 4. Attribute 个数评分：归一化相似度
        score += scoreAttribute(original.getAttributeCount(), target.getAttributeCount());

        return (int) Math.round(score);
    }

    /**
     * 状态码评分：相同 → 30，不同 → 0
     */
    static double scoreStatusCode(int original, int target) {
        return original == target ? STATUS_CODE_MAX : 0.0;
    }

    /**
     * 包长度评分：1 - |a-b| / max(a,b)，归一化到 0~30
     * 两者都为 0 时视为完全相同。
     */
    static double scoreLength(int original, int target) {
        if (original == 0 && target == 0) {
            return LENGTH_MAX;
        }
        int maxLen = Math.max(original, target);
        if (maxLen == 0) {
            return LENGTH_MAX;
        }
        double similarity = 1.0 - (double) Math.abs(original - target) / maxLen;
        return similarity * LENGTH_MAX;
    }

    /**
     * 哈希评分：完全相同 → 25，不同 → 0
     */
    static double scoreHash(int original, int target) {
        return original == target ? HASH_MAX : 0.0;
    }

    /**
     * Attribute 个数评分：1 - |a-b| / max(a,b)，归一化到 0~15
     * 两者都为 0 时视为完全相同。
     */
    static double scoreAttribute(int original, int target) {
        if (original == 0 && target == 0) {
            return ATTRIBUTE_MAX;
        }
        int maxAttr = Math.max(original, target);
        if (maxAttr == 0) {
            return ATTRIBUTE_MAX;
        }
        double similarity = 1.0 - (double) Math.abs(original - target) / maxAttr;
        return similarity * ATTRIBUTE_MAX;
    }
}

