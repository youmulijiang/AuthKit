package core.scan.idor;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** IDOR 扫描结果。hashChanged 为 true 表示响应体哈希与原始响应不同，可能存在越权。 */
public record IdorScanResult(
        int index, String time, String technique, String paramName,
        String oldValue, String newValue, String method, String host,
        String path, String query, int statusCode, int length,
        boolean hashChanged, long durationMs, String comment,
        HttpRequestResponse requestResponse, String error) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static IdorScanResult success(int index, IdorScanVariant variant,
                                          HttpRequestResponse reqResp,
                                          int originalHash, long durationMs) {
        HttpRequest req = reqResp.request();
        HttpResponse resp = reqResp.response();
        int statusCode = resp != null ? resp.statusCode() : 0;
        int length = resp != null && resp.body() != null ? resp.body().length() : 0;
        String responseBody = resp != null && resp.bodyToString() != null ? resp.bodyToString() : "";
        boolean hashChanged = responseBody.hashCode() != originalHash;
        return new IdorScanResult(
                index, LocalTime.now().format(FORMATTER),
                variant.technique(), variant.paramName(),
                variant.originalValue(), variant.newValue(),
                safe(req.method()), safeHost(req),
                safe(req.pathWithoutQuery()), safe(req.query()),
                statusCode, length, hashChanged, durationMs,
                variant.comment(), reqResp, "");
    }

    public static IdorScanResult failure(int index, IdorScanVariant variant, Exception error) {
        HttpRequest req = variant.request();
        return new IdorScanResult(
                index, LocalTime.now().format(FORMATTER),
                variant.technique(), variant.paramName(),
                variant.originalValue(), variant.newValue(),
                safe(req.method()), safeHost(req),
                safe(req.pathWithoutQuery()), safe(req.query()),
                0, 0, false, 0,
                variant.comment(), null,
                error != null ? safe(error.getMessage()) : "");
    }

    private static String safeHost(HttpRequest request) {
        try {
            return request.httpService() != null ? request.httpService().host() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
