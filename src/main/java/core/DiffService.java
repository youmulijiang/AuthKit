package core;

/**
 * Diff 服务接口（策略模式）
 * 定义文本差异比较的标准接口，支持不同的 Diff 实现。
 */
public interface DiffService {

    /**
     * 比较两段文本的差异
     *
     * @param original 原始文本
     * @param modified 修改后的文本
     * @return 差异结果的文本表示
     */
    String diff(String original, String modified);
}

