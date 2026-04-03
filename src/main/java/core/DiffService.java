package core;

/**
 * Diff 服务接口（策略模式）
 * 定义文本差异比较的标准接口，支持不同的 Diff 实现。
 */
public interface DiffService {

    /**
     * 并排 Diff 结果
     *
     * @param leftHtml       左侧（Source）HTML 片段
     * @param rightHtml      右侧（Target）HTML 片段
     * @param hasDifferences 是否存在差异
     */
    record SideBySideDiffResult(String leftHtml, String rightHtml, boolean hasDifferences) {
    }

    /**
     * 比较两段文本的差异
     *
     * @param original 原始文本
     * @param modified 修改后的文本
     * @return 差异结果的文本表示
     */
    String diff(String original, String modified);

    /**
     * 并排比较两段文本的差异，返回左右两侧 HTML 片段
     *
     * @param original 原始文本（Source）
     * @param modified 修改后的文本（Target）
     * @return 左右两侧 HTML 及是否有差异
     */
    SideBySideDiffResult diffSideBySide(String original, String modified);
}

