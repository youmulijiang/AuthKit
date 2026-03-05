package core;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 文本 Diff 服务实现
 * 使用 java-diff-utils 库进行文本差异比较，输出 Unified Diff 格式。
 */
public class TextDiffService implements DiffService {

    private static final int CONTEXT_LINES = 3;

    /**
     * 比较两段文本的差异，返回 Unified Diff 格式结果
     *
     * @param original 原始文本
     * @param modified 修改后的文本
     * @return Unified Diff 格式的差异文本，无差异返回空字符串
     */
    @Override
    public String diff(String original, String modified) {
        List<String> originalLines = toLines(original);
        List<String> modifiedLines = toLines(modified);

        Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);

        if (patch.getDeltas().isEmpty()) {
            return "";
        }

        List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                "original", "modified",
                originalLines, patch, CONTEXT_LINES
        );

        return String.join("\n", unifiedDiff);
    }

    /**
     * 将文本按行分割为列表
     */
    private List<String> toLines(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(text.split("\n", -1));
    }
}

