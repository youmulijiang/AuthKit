package core;

import model.CompareSampleModel;
import model.MessageDataModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthResultExportServiceTest {

    private final AuthResultExportService service = new AuthResultExportService();

    @Test
    @DisplayName("CSV 导出应包含选中样本和各鉴权对象 metadata")
    void toCsv_shouldIncludeMetadata() {
        CompareSampleModel sample = sample();

        String csv = service.toCsv(List.of(sample), List.of("Original", "Unauthorized"));

        assertTrue(csv.startsWith("ID,Method,URL,Auth Object,Status Code"));
        assertTrue(csv.contains("1,GET,https://example.com/a,Original,200,100,123,2,100,text/html,note-1"));
        assertTrue(csv.contains("1,GET,https://example.com/a,Unauthorized,403,20,456,1,10,application/json,"));
    }

    @Test
    @DisplayName("HTML 导出应为黑白表格并提供前端筛选能力")
    void toHtml_shouldIncludeFilterableBlackWhiteTable() {
        String html = service.toHtml(List.of(sample()), List.of("Original"));

        assertTrue(html.contains("#111"));
        assertTrue(html.contains("filterRows()"));
        assertTrue(html.contains("<table id=\"resultTable\">"));
        assertTrue(html.contains("https://example.com/a"));
        assertTrue(html.contains("text/html"));
    }

    @Test
    @DisplayName("HTML 导出应转义特殊字符")
    void toHtml_shouldEscapeSpecialChars() {
        CompareSampleModel sample = new CompareSampleModel(2, "POST", "https://e.com/?q=<x>");
        MessageDataModel data = new MessageDataModel();
        data.setNote("<script>alert(1)</script>");
        sample.putMessageData("Original", data);

        String html = service.toHtml(List.of(sample), List.of("Original"));

        assertTrue(html.contains("&lt;x&gt;"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains("<script>alert(1)</script>"));
    }

    @Test
    @DisplayName("HTML 导出长 URL 应省略显示并可点击复制完整 URL")
    void toHtml_shouldTruncateLongUrlAndKeepCopyValue() {
        String longUrl = "https://example.com/api/" + "a".repeat(120) + "?token=secret";
        CompareSampleModel sample = new CompareSampleModel(3, "GET", longUrl);
        sample.putMessageData("Original", new MessageDataModel());

        String html = service.toHtml(List.of(sample), List.of("Original"));

        assertTrue(html.contains("class=\"url-cell\""));
        assertTrue(html.contains("onclick=\"copyText(this.dataset.copy)\""));
        assertTrue(html.contains("data-copy=\"" + longUrl + "\""));
        assertTrue(html.contains("..."));
        assertTrue(html.contains("querySelectorAll('[data-copy]')"));
    }

    private CompareSampleModel sample() {
        CompareSampleModel sample = new CompareSampleModel(1, "GET", "https://example.com/a");
        MessageDataModel original = new MessageDataModel();
        original.setStatusCode(200);
        original.setLength(100);
        original.setHash(123);
        original.setAttributeCount(2);
        original.setRank(100);
        original.setContentType("text/html");
        original.setNote("note-1");
        sample.putMessageData("Original", original);

        MessageDataModel unauthorized = new MessageDataModel();
        unauthorized.setStatusCode(403);
        unauthorized.setLength(20);
        unauthorized.setHash(456);
        unauthorized.setAttributeCount(1);
        unauthorized.setRank(10);
        unauthorized.setContentType("application/json");
        sample.putMessageData("Unauthorized", unauthorized);
        return sample;
    }
}
