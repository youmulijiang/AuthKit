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
    private static final int CONTEXT_LINES = 3;
    private static final int FULL_RENDER_LINE_THRESHOLD = 800;
    private static final int LARGE_TEXT_LINE_THRESHOLD = 2500;
    private static final int LARGE_TEXT_CHAR_THRESHOLD = 200_000;
    private static final int MAX_CONTEXT_RENDER_LINES = 400;
    private static final int MAX_SUMMARY_RENDER_LINES = 160;

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

        int totalLines = originalLines.size() + modifiedLines.size();
        int totalChars = safeLength(original) + safeLength(modified);
        boolean summaryMode = totalLines > LARGE_TEXT_LINE_THRESHOLD || totalChars > LARGE_TEXT_CHAR_THRESHOLD;
        boolean contextMode = summaryMode || totalLines > FULL_RENDER_LINE_THRESHOLD;

        return contextMode
                ? buildContextHtmlDiff(originalLines, patch, summaryMode)
                : buildFullHtmlDiff(originalLines, patch);
    }

    /**
     * 构建完整 HTML 格式的 diff 输出
     */
    private String buildFullHtmlDiff(List<String> original, Patch<String> patch) {
        DiffStats stats = calculateStats(patch);
        StringBuilder html = buildStatsHeader(stats);
        html.append("<p style='font-family:Courier New;font-size:13pt;'>");

        int origPos = 0;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            Chunk<String> src = delta.getSource();
            Chunk<String> tgt = delta.getTarget();

            for (int i = origPos; i < src.getPosition(); i++) {
                html.append(escapeHtml(original.get(i))).append("<br>");
            }

            appendDeletedLines(html, src.getLines());
            appendInsertedLines(html, tgt.getLines());
            origPos = src.getPosition() + src.size();
        }

        for (int i = origPos; i < original.size(); i++) {
            html.append(escapeHtml(original.get(i))).append("<br>");
        }

        html.append("</p>");
        return html.toString();
    }

    /**
     * 构建上下文模式 HTML diff，避免大文本时生成超大 HTML 卡住 UI
     */
    private String buildContextHtmlDiff(List<String> original, Patch<String> patch, boolean summaryMode) {
        DiffStats stats = calculateStats(patch);
        StringBuilder html = buildStatsHeader(stats);
        if (summaryMode) {
            html.append("<span style='color:#666666;'>Large diff detected. Rendering a summarized view for responsiveness.</span><br><br>");
        }
        html.append("<p style='font-family:Courier New;font-size:13pt;'>");

        int renderedLines = 0;
        int maxRenderLines = summaryMode ? MAX_SUMMARY_RENDER_LINES : MAX_CONTEXT_RENDER_LINES;
        int renderedUntil = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            Chunk<String> src = delta.getSource();
            Chunk<String> tgt = delta.getTarget();

            int contextStart = Math.max(renderedUntil, src.getPosition() - CONTEXT_LINES);
            if (contextStart > renderedUntil) {
                renderedLines += appendOmittedLines(html, contextStart - renderedUntil);
            }

            for (int i = contextStart; i < src.getPosition(); i++) {
                html.append(escapeHtml(original.get(i))).append("<br>");
                renderedLines++;
                if (renderedLines >= maxRenderLines) {
                    return finishTruncatedHtml(html);
                }
            }

            renderedLines += appendDeletedLines(html, src.getLines());
            if (renderedLines >= maxRenderLines) {
                return finishTruncatedHtml(html);
            }

            renderedLines += appendInsertedLines(html, tgt.getLines());
            if (renderedLines >= maxRenderLines) {
                return finishTruncatedHtml(html);
            }

            int afterStart = src.getPosition() + src.size();
            int afterEnd = Math.min(original.size(), afterStart + CONTEXT_LINES);
            for (int i = Math.max(afterStart, renderedUntil); i < afterEnd; i++) {
                html.append(escapeHtml(original.get(i))).append("<br>");
                renderedLines++;
                if (renderedLines >= maxRenderLines) {
                    return finishTruncatedHtml(html);
                }
            }

            renderedUntil = Math.max(renderedUntil, afterEnd);
        }

        if (renderedUntil < original.size()) {
            appendOmittedLines(html, original.size() - renderedUntil);
        }

        html.append("</p>");
        return html.toString();
    }

    private DiffStats calculateStats(Patch<String> patch) {
        int inserts = 0;
        int deletes = 0;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            deletes += delta.getSource().getLines().size();
            inserts += delta.getTarget().getLines().size();
        }
        return new DiffStats(inserts, deletes);
    }

    private StringBuilder buildStatsHeader(DiffStats stats) {
        StringBuilder html = new StringBuilder();
        html.append("<span style='background-color:").append(COLOR_INSERT_BG)
                .append(";color:#000000;'>Inserts: ").append(stats.inserts()).append("</span>");
        html.append("&nbsp;&nbsp;&nbsp;");
        html.append("<span style='background-color:").append(COLOR_DELETE_BG)
                .append(";color:#000000;'>Deletes: ").append(stats.deletes()).append("</span>");
        html.append("<br><br>");
        return html;
    }

    private int appendDeletedLines(StringBuilder html, List<String> lines) {
        for (String line : lines) {
            html.append("<span style='background-color:")
                    .append(COLOR_DELETE_BG).append(";color:#000000;'>")
                    .append(escapeHtml(line)).append("</span><br>");
        }
        return lines.size();
    }

    private int appendInsertedLines(StringBuilder html, List<String> lines) {
        for (String line : lines) {
            html.append("<span style='background-color:")
                    .append(COLOR_INSERT_BG).append(";color:#000000;'>")
                    .append(escapeHtml(line)).append("</span><br>");
        }
        return lines.size();
    }

    private int appendOmittedLines(StringBuilder html, int omittedLines) {
        if (omittedLines <= 0) {
            return 0;
        }
        html.append("<span style='color:#666666;'>... ")
                .append(omittedLines)
                .append(" unchanged lines omitted ...</span><br>");
        return 1;
    }

    private String finishTruncatedHtml(StringBuilder html) {
        html.append("<span style='color:#666666;'>... diff output truncated ...</span><br>");
        html.append("</p>");
        return html.toString();
    }

    /**





     * 并排比较两段文本的差异，返回左右两侧 HTML 片段。
     * 对大文本自动启用上下文模式，只渲染差异附近的行。
     */
    @Override
    public SideBySideDiffResult diffSideBySide(String original, String modified) {
        List<String> originalLines = toLines(original);
        List<String> modifiedLines = toLines(modified);

        Patch<String> patch = DiffUtils.diff(originalLines, modifiedLines);

        if (patch.getDeltas().isEmpty()) {
            String html = buildPlainHtml(originalLines);
            return new SideBySideDiffResult(html, html, false);
        }

        int totalLines = originalLines.size() + modifiedLines.size();
        int totalChars = safeLength(original) + safeLength(modified);
        boolean summaryMode = totalLines > LARGE_TEXT_LINE_THRESHOLD || totalChars > LARGE_TEXT_CHAR_THRESHOLD;
        boolean contextMode = summaryMode || totalLines > FULL_RENDER_LINE_THRESHOLD;

        if (contextMode) {
            return buildContextSideBySide(originalLines, patch, summaryMode);
        } else {
            return buildFullSideBySide(originalLines, patch);
        }
    }

    /** 并排视图 — 完整渲染（小文本） */
    private SideBySideDiffResult buildFullSideBySide(List<String> originalLines, Patch<String> patch) {
        DiffStats stats = calculateStats(patch);
        StringBuilder leftHtml = new StringBuilder();
        StringBuilder rightHtml = new StringBuilder();
        appendSideBySideStatsHeader(leftHtml, rightHtml, stats);
        appendSideBySideBodyOpen(leftHtml, rightHtml);

        int origPos = 0;
        for (AbstractDelta<String> delta : patch.getDeltas()) {
            Chunk<String> src = delta.getSource();
            Chunk<String> tgt = delta.getTarget();

            for (int i = origPos; i < src.getPosition(); i++) {
                appendSameLineBoth(leftHtml, rightHtml, originalLines.get(i));
            }
            appendDeltaSideBySide(leftHtml, rightHtml, src, tgt);
            origPos = src.getPosition() + src.size();
        }

        for (int i = origPos; i < originalLines.size(); i++) {
            appendSameLineBoth(leftHtml, rightHtml, originalLines.get(i));
        }

        appendSideBySideBodyClose(leftHtml, rightHtml);
        return new SideBySideDiffResult(leftHtml.toString(), rightHtml.toString(), true);
    }

    /** 并排视图 — 上下文渲染（大文本，只显示差异附近的行） */
    private SideBySideDiffResult buildContextSideBySide(List<String> originalLines,
                                                         Patch<String> patch, boolean summaryMode) {
        DiffStats stats = calculateStats(patch);
        StringBuilder leftHtml = new StringBuilder();
        StringBuilder rightHtml = new StringBuilder();
        appendSideBySideStatsHeader(leftHtml, rightHtml, stats);
        if (summaryMode) {
            String hint = "<span style='color:#666666;'>Large diff detected. Rendering a summarized view for responsiveness.</span><br><br>";
            leftHtml.append(hint);
            rightHtml.append(hint);
        }
        appendSideBySideBodyOpen(leftHtml, rightHtml);

        int maxRenderLines = summaryMode ? MAX_SUMMARY_RENDER_LINES : MAX_CONTEXT_RENDER_LINES;
        int renderedLines = 0;
        int renderedUntil = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            Chunk<String> src = delta.getSource();
            Chunk<String> tgt = delta.getTarget();

            int contextStart = Math.max(renderedUntil, src.getPosition() - CONTEXT_LINES);
            if (contextStart > renderedUntil) {
                appendOmittedLinesBoth(leftHtml, rightHtml, contextStart - renderedUntil);
                renderedLines++;
            }

            for (int i = contextStart; i < src.getPosition(); i++) {
                appendSameLineBoth(leftHtml, rightHtml, originalLines.get(i));
                renderedLines++;
                if (renderedLines >= maxRenderLines) {
                    return finishTruncatedSideBySide(leftHtml, rightHtml);
                }
            }

            appendDeltaSideBySide(leftHtml, rightHtml, src, tgt);
            renderedLines += Math.max(src.size(), tgt.size());
            if (renderedLines >= maxRenderLines) {
                return finishTruncatedSideBySide(leftHtml, rightHtml);
            }

            int afterStart = src.getPosition() + src.size();
            int afterEnd = Math.min(originalLines.size(), afterStart + CONTEXT_LINES);
            for (int i = Math.max(afterStart, renderedUntil); i < afterEnd; i++) {
                appendSameLineBoth(leftHtml, rightHtml, originalLines.get(i));
                renderedLines++;
                if (renderedLines >= maxRenderLines) {
                    return finishTruncatedSideBySide(leftHtml, rightHtml);
                }
            }

            renderedUntil = Math.max(renderedUntil, afterEnd);
        }

        if (renderedUntil < originalLines.size()) {
            appendOmittedLinesBoth(leftHtml, rightHtml, originalLines.size() - renderedUntil);
        }

        appendSideBySideBodyClose(leftHtml, rightHtml);
        return new SideBySideDiffResult(leftHtml.toString(), rightHtml.toString(), true);
    }

    // ── 并排视图辅助方法 ──

    private void appendSideBySideStatsHeader(StringBuilder left, StringBuilder right, DiffStats stats) {
        String statsHtml = "<span style='background-color:" + COLOR_INSERT_BG
                + ";color:#000000;'>Inserts: " + stats.inserts() + "</span>"
                + "&nbsp;&nbsp;&nbsp;"
                + "<span style='background-color:" + COLOR_DELETE_BG
                + ";color:#000000;'>Deletes: " + stats.deletes() + "</span><br><br>";
        left.append(statsHtml);
        right.append(statsHtml);
    }

    private void appendSideBySideBodyOpen(StringBuilder left, StringBuilder right) {
        String open = "<p style='font-family:Courier New;font-size:13pt;'>";
        left.append(open);
        right.append(open);
    }

    private void appendSideBySideBodyClose(StringBuilder left, StringBuilder right) {
        left.append("</p>");
        right.append("</p>");
    }

    private void appendSameLineBoth(StringBuilder left, StringBuilder right, String line) {
        String escaped = escapeHtml(line);
        left.append(escaped).append("<br>");
        right.append(escaped).append("<br>");
    }

    private void appendOmittedLinesBoth(StringBuilder left, StringBuilder right, int count) {
        if (count <= 0) return;
        String omitted = "<span style='color:#666666;'>... " + count + " unchanged lines omitted ...</span><br>";
        left.append(omitted);
        right.append(omitted);
    }

    private void appendDeltaSideBySide(StringBuilder left, StringBuilder right,
                                        Chunk<String> src, Chunk<String> tgt) {
        for (String line : src.getLines()) {
            left.append("<span style='background-color:")
                    .append(COLOR_DELETE_BG).append(";color:#000000;'>")
                    .append(escapeHtml(line)).append("</span><br>");
        }
        for (String line : tgt.getLines()) {
            right.append("<span style='background-color:")
                    .append(COLOR_INSERT_BG).append(";color:#000000;'>")
                    .append(escapeHtml(line)).append("</span><br>");
        }
        int diff = src.size() - tgt.size();
        if (diff > 0) {
            for (int i = 0; i < diff; i++) {
                right.append("<span style='background-color:#eeeeee;'>&nbsp;</span><br>");
            }
        } else if (diff < 0) {
            for (int i = 0; i < -diff; i++) {
                left.append("<span style='background-color:#eeeeee;'>&nbsp;</span><br>");
            }
        }
    }

    private SideBySideDiffResult finishTruncatedSideBySide(StringBuilder left, StringBuilder right) {
        String truncated = "<span style='color:#666666;'>... diff output truncated ...</span><br></p>";
        left.append(truncated);
        right.append(truncated);
        return new SideBySideDiffResult(left.toString(), right.toString(), true);
    }

    private String buildPlainHtml(List<String> lines) {
        StringBuilder html = new StringBuilder();
        html.append("<p style='font-family:Courier New;font-size:13pt;'>");
        for (String line : lines) {
            html.append(escapeHtml(line)).append("<br>");
        }
        html.append("</p>");
        return html.toString();
    }

    private int safeLength(String text) {
        return text != null ? text.length() : 0;
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

    private record DiffStats(int inserts, int deletes) {
    }
}

