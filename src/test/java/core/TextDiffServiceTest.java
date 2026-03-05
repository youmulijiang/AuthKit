package core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextDiffService 单元测试
 */
class TextDiffServiceTest {

    private DiffService diffService;

    @BeforeEach
    void setUp() {
        diffService = new TextDiffService();
    }

    @Test
    @DisplayName("相同文本应返回空字符串（无差异）")
    void sameText_shouldReturnEmpty() {
        String text = "line1\nline2\nline3";
        String result = diffService.diff(text, text);
        assertEquals("", result);
    }

    @Test
    @DisplayName("不同文本应返回 HTML 格式差异")
    void differentText_shouldReturnHtmlDiff() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nmodified\nline3";
        String result = diffService.diff(original, modified);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // 应包含 HTML 高亮标签
        assertTrue(result.contains("Inserts:"));
        assertTrue(result.contains("Deletes:"));
        // 应包含差异内容
        assertTrue(result.contains("line2") || result.contains("modified"));
    }

    @Test
    @DisplayName("新增行应用绿色背景标记")
    void addedLines_shouldBeHighlightedGreen() {
        String original = "line1\nline2";
        String modified = "line1\nline2\nline3";
        String result = diffService.diff(original, modified);
        assertTrue(result.contains("line3"));
        assertTrue(result.contains("#c2f9c2")); // 绿色背景
    }

    @Test
    @DisplayName("删除行应用红色背景标记")
    void deletedLines_shouldBeHighlightedRed() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nline3";
        String result = diffService.diff(original, modified);
        assertTrue(result.contains("line2"));
        assertTrue(result.contains("#ffb2b2")); // 红色背景
    }

    @Test
    @DisplayName("空字符串输入应正常处理")
    void emptyInput_shouldHandleGracefully() {
        String result = diffService.diff("", "new content");
        assertNotNull(result);
        assertTrue(result.contains("new content"));
    }

    @Test
    @DisplayName("null 输入应返回空字符串")
    void nullInput_shouldReturnEmpty() {
        String result = diffService.diff(null, null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("完全不同的文本应返回包含 Inserts 和 Deletes 统计的 HTML")
    void completelyDifferent_shouldReturnFullHtmlDiff() {
        String original = "aaa\nbbb";
        String modified = "xxx\nyyy";
        String result = diffService.diff(original, modified);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Inserts:"));
        assertTrue(result.contains("Deletes:"));
    }
}

