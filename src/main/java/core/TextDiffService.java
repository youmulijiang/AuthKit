package core;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 文本 Diff 服务实现
 * 使用 java-diff-utils 库进行文本差异比较，输出 HTML 格式带颜色高亮。
 * 插入行使用绿色背景，删除行使用红色背景，相同行无背景。
 */
public class TextDiffService implements DiffService {

    private static final String COLOR_INSERT_BG = "#c2f9c2";
    private static final String COLOR_DELETE_BG = "#ffb2b2";

    /**
     * 比较两段文本的差异，返回 HTML 格式的高亮结果
     *
     * @param original 原始文本（Source）
     * @param modified 修改后的文本（Target）
     * @return HTML 格式的差异文本，无差异返回空字符串
     */
    @Override
    public String diff(String original, String modified) {
        List<String> originalLines = toLines(original);
        List<String> modifiedLines = toLines(modified);

        Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);

        if (patch.getDeltas().isEmpty()) {
            return "";
        }

        return buildHtmlDiff(originalLines, modifiedLines, patch);
    }

    /**
     * 构建 HTML 格式的 diff 输出
     */
    private String buildHtmlDiff(List<String> original, List<String> modified,
                                  Patch<String> patch) {
        int inserts = 0;
        int deletes = 0;

        // 统计插入和删除数
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            deletes += delta.getSource().getLines().size();
            inserts += delta.getTarget().getLines().size();
            if (delta.getType() == DeltaType.CHANGE) {
                // CHANGE 同时包含删除和插入，已在上面统计
            }
        }

        // 构建逐行 HTML
        StringBuilder html = new StringBuilder();
        html.append("<span style='background-color:").append(COLOR_INSERT_BG)
                .append(";color:#000000;'>Inserts: ").append(inserts).append("</span>");
        html.append("&nbsp;&nbsp;&nbsp;");
        html.append("<span style='background-color:").append(COLOR_DELETE_BG)
                .append(";color:#000000;'>Deletes: ").append(deletes).append("</span>");
        html.append("<br><br>");

        html.append("<p style='font-family:Courier New;font-size:13pt;'>");

        int origPos = 0;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            Chunk<String> src = delta.getSource();
            Chunk<String> tgt = delta.getTarget();

            // 输出 delta 之前的相同行
            for (int i = origPos; i < src.getPosition(); i++) {
                html.append(escapeHtml(original.get(i))).append("<br>");
            }

            // 输出删除行（红色背景）
            for (String line : src.getLines()) {
                html.append("<span style='background-color:")
                        .append(COLOR_DELETE_BG).append(";color:#000000;'>")
                        .append(escapeHtml(line)).append("</span><br>");
            }

            // 输出插入行（绿色背景）
            for (String line : tgt.getLines()) {
                html.append("<span style='background-color:")
                        .append(COLOR_INSERT_BG).append(";color:#000000;'>")
                        .append(escapeHtml(line)).append("</span><br>");
            }

            origPos = src.getPosition() + src.size();
        }

        // 输出剩余的相同行
        for (int i = origPos; i < original.size(); i++) {
            html.append(escapeHtml(original.get(i))).append("<br>");
        }

        html.append("</p>");
        return html.toString();
    }

    /**
     * HTML 转义
     */
    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
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

