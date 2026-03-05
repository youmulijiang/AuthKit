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
    @DisplayName("相同文本应返回无差异结果")
    void sameText_shouldReturnNoDiff() {
        String text = "line1\nline2\nline3";
        String result = diffService.diff(text, text);
        // 相同文本不应有差异标记
        assertFalse(result.contains("+"));
        assertFalse(result.contains("-"));
    }

    @Test
    @DisplayName("不同文本应返回差异结果")
    void differentText_shouldReturnDiff() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nmodified\nline3";
        String result = diffService.diff(original, modified);
        assertNotNull(result);
        assertFalse(result.isEmpty());
        // 应包含差异信息
        assertTrue(result.contains("line2") || result.contains("modified"));
    }

    @Test
    @DisplayName("新增行应在 diff 中标记")
    void addedLines_shouldBeMarked() {
        String original = "line1\nline2";
        String modified = "line1\nline2\nline3";
        String result = diffService.diff(original, modified);
        assertTrue(result.contains("line3"));
    }

    @Test
    @DisplayName("删除行应在 diff 中标记")
    void deletedLines_shouldBeMarked() {
        String original = "line1\nline2\nline3";
        String modified = "line1\nline3";
        String result = diffService.diff(original, modified);
        assertTrue(result.contains("line2"));
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
    @DisplayName("完全不同的文本应返回完整差异")
    void completelyDifferent_shouldReturnFullDiff() {
        String original = "aaa\nbbb";
        String modified = "xxx\nyyy";
        String result = diffService.diff(original, modified);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}

