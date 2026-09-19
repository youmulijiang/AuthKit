package core;

import model.MessageDataModel;


/**
 * 鉴权风险评分服务
 * 将目标鉴权对象的响应与 Original 进行递进归一化比较，
 * 量化越权风险。分数越高，越可能存在越权漏洞。
 * Original 固定 100 分。
 *
 * 归一化策略：对响应体按 L0（原文）→ L1（标准：日期/epoch/UUID/JWT/hex token/空白）
 * → L2（激进：base64 token/易变 JSON key/长数字串）逐级归一化后比较哈希，
 * 命中级别越低信号越干净；L1/L2 命中时哈希分打折，防止过度归一化掩盖真实差异。
 *
 * 评分维度及权重：
 * - StatusCode:  状态码相同 → 满分，不同 → 0          权重 0.30
 * - Hash:        归一化后响应体全等 → 满分，不同 → 0   权重 0.50
 *                （L0 全等 50 / L1 全等 42.5 / L2 全等 35）
 * - Length:      L1 归一化后响应体长度相似度归一化      权重 0.20
 */
public final class RankService {

    /** Original 固定分数 */
    public static final int ORIGINAL_RANK = 100;

    /** 越权判定阈值建议值（供展示层参考） */
    public static final int UNAUTHORIZED_THRESHOLD = 85;

    // 各维度满分
    private static final double STATUS_CODE_MAX = 30.0;
    private static final double HASH_MAX = 50.0;
    private static final double LENGTH_MAX = 20.0;

    /** L1/L2 命中时哈希分折扣 */
    private static final double HASH_L1_FACTOR = 0.85;
    private static final double HASH_L2_FACTOR = 0.70;

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

        // 2. 哈希评分：递进归一化比较，命中级别越低折扣越大
        score += scoreHash(original, target);

        // 3. 长度评分：基于 L1 归一化后的响应体长度
        score += scoreLength(normalizedLength(original, 1), normalizedLength(target, 1));

        return (int) Math.round(score);
    }

    /**
     * 状态码评分：相同 → 30，不同 → 0
     */
    static double scoreStatusCode(int original, int target) {
        return original == target ? STATUS_CODE_MAX : 0.0;
    }

    /**
     * 哈希评分：递进归一化后响应体全等判定。
     * L0 全等 → 50；L1 全等 → 42.5；L2 全等 → 35；均不等 → 0。
     */
    static double scoreHash(MessageDataModel original, MessageDataModel target) {
        String origBody = original.getBody();
        String targetBody = target.getBody();

        if (origBody != null && targetBody != null) {
            if (origBody.equals(targetBody)) {
                return HASH_MAX;
            }
            if (original.getNormalizedBody(1).equals(target.getNormalizedBody(1))) {
                return HASH_MAX * HASH_L1_FACTOR;
            }
            if (original.getNormalizedBody(2).equals(target.getNormalizedBody(2))) {
                return HASH_MAX * HASH_L2_FACTOR;
            }
            return 0.0;
        }
        return origBody == targetBody ? HASH_MAX : 0.0;
    }

    /**
     * 包长度评分：1 - |a-b| / max(a,b)，归一化到 0~20
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

    /** 获取指定模型响应体在指定归一化级别下的长度（走模型缓存） */
    private static int normalizedLength(MessageDataModel model, int level) {
        if (model.getBody() == null) {
            return 0;
        }
        return model.getNormalizedBody(level).length();
    }
}
