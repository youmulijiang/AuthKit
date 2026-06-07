package core;

import model.CompareSampleModel;
import model.MessageDataModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 鉴权结果导出服务。
 * 只负责把领域模型转换为可导出的 CSV / HTML 文本，避免 UI 组件承担业务格式化逻辑。
 */
public class AuthResultExportService {

    private static final int URL_COLUMN_INDEX = 2;
    private static final int URL_DISPLAY_LIMIT = 96;

    private static final String[] HEADERS = {
            "ID", "Method", "URL", "Auth Object", "Status Code", "Length",
            "Hash", "Attribute Num", "Rank", "Content-Type", "Note"
    };

    public String toCsv(List<CompareSampleModel> samples, List<String> authColumns) {
        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, List.of(HEADERS));
        for (CompareSampleModel sample : samples) {
            for (String authName : authColumns) {
                appendCsvRow(csv, buildRow(sample, authName));
            }
        }
        return csv.toString();
    }

    public String toHtml(List<CompareSampleModel> samples, List<String> authColumns) {
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset=\"UTF-8\">")
                .append("<title>AuthKit Export</title>")
                .append("<style>body{background:#fff;color:#111;font-family:Arial,sans-serif;margin:24px;}")
                .append("input{padding:8px;width:360px;border:1px solid #111;margin-bottom:12px;}")
                .append("table{border-collapse:collapse;width:100%;font-size:13px;}")
                .append("th,td{border:1px solid #111;padding:6px 8px;text-align:left;vertical-align:top;}")
                .append("td.url-cell{cursor:pointer;}td.url-cell span{display:block;max-width:420px;")
                .append("white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-decoration:underline;}")
                .append("th{background:#111;color:#fff;}tr:nth-child(even){background:#f4f4f4;}")
                .append("</style></head><body>")
                .append("<h1>AuthKit Export</h1>")
                .append("<input id=\"filter\" placeholder=\"Filter table...\" onkeyup=\"filterRows()\">")
                .append("<table id=\"resultTable\"><thead><tr>");
        for (String header : HEADERS) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (CompareSampleModel sample : samples) {
            for (String authName : authColumns) {
                List<String> values = buildRow(sample, authName);
                html.append("<tr>");
                for (int i = 0; i < values.size(); i++) {
                    if (i == URL_COLUMN_INDEX) {
                        appendUrlCell(html, values.get(i));
                    } else {
                        html.append("<td>").append(escapeHtml(values.get(i))).append("</td>");
                    }
                }
                html.append("</tr>");
            }
        }
        html.append("</tbody></table><script>")
                .append("function fallbackCopy(t){")
                .append("var a=document.createElement('textarea');a.value=t;document.body.appendChild(a);")
                .append("a.select();document.execCommand('copy');a.remove();}")
                .append("function copyText(t){if(navigator.clipboard&&window.isSecureContext){")
                .append("navigator.clipboard.writeText(t).catch(function(){fallbackCopy(t);});")
                .append("}else{fallbackCopy(t);}}")
                .append("function filterRows(){var q=document.getElementById('filter').value.toLowerCase();")
                .append("document.querySelectorAll('#resultTable tbody tr').forEach(function(r){")
                .append("var extra=Array.from(r.querySelectorAll('[data-copy]')).map(function(e){return e.dataset.copy;}).join(' ');")
                .append("var text=(r.innerText+' '+extra).toLowerCase();")
                .append("r.style.display=text.indexOf(q)>=0?'':'none';});}")
                .append("</script></body></html>");
        return html.toString();
    }

    private void appendUrlCell(StringBuilder html, String url) {
        String safeUrl = safe(url);
        html.append("<td class=\"url-cell\" data-copy=\"").append(escapeHtml(safeUrl))
                .append("\" onclick=\"copyText(this.dataset.copy)\"><span title=\"")
                .append(escapeHtml(safeUrl)).append("\">")
                .append(escapeHtml(shortenUrl(safeUrl))).append("</span></td>");
    }

    private String shortenUrl(String url) {
        if (url.length() <= URL_DISPLAY_LIMIT) {
            return url;
        }
        return url.substring(0, URL_DISPLAY_LIMIT - 3) + "...";
    }

    public void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private List<String> buildRow(CompareSampleModel sample, String authName) {
        MessageDataModel data = sample.getMessageData(authName);
        return List.of(
                String.valueOf(sample.getId()),
                safe(sample.getMethod()),
                safe(sample.getUrl()),
                safe(authName),
                data != null ? String.valueOf(data.getStatusCode()) : "",
                data != null ? String.valueOf(data.getLength()) : "",
                data != null ? String.valueOf(data.getHash()) : "",
                data != null ? String.valueOf(data.getAttributeCount()) : "",
                data != null ? String.valueOf(data.getRank()) : "",
                data != null ? safe(data.getContentType()) : "",
                data != null ? safe(data.getNote()) : ""
        );
    }

    private void appendCsvRow(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(values.get(i)));
        }
        csv.append("\r\n");
    }

    private String escapeCsv(String value) {
        String safeValue = safe(value);
        if (safeValue.contains(",") || safeValue.contains("\n") || safeValue.contains("\r") || safeValue.contains("\"")) {
            return "\"" + safeValue.replace("\"", "\"\"") + "\"";
        }
        return safeValue;
    }

    private String escapeHtml(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}