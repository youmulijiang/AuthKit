package core.scan.bypass403;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 403 bypass 扫描结果。 */
public record Bypass403ScanResult(
        int index,
        String time,
        String technique,
        String method,
        String host,
        String path,
        String query,
        int paramCount,
        int statusCode,
        int length,
        long durationMs,
        String comment,
        HttpRequestResponse requestResponse,
        String error) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static Bypass403ScanResult success(int index, Bypass403RequestVariant variant,
                                               HttpRequestResponse requestResponse, long durationMs) {
        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();
        int length = response != null && response.body() != null ? response.body().length() : 0;
        int statusCode = response != null ? response.statusCode() : 0;
        return new Bypass403ScanResult(index, LocalTime.now().format(FORMATTER), variant.technique(),
                safe(request.method()), safeHost(request), safe(request.pathWithoutQuery()), safe(request.query()),
                safeParamCount(request), statusCode, length, durationMs, variant.comment(), requestResponse, "");
    }

    public static Bypass403ScanResult failure(int index, Bypass403RequestVariant variant, Exception error) {
        HttpRequest request = variant.request();
        return new Bypass403ScanResult(index, LocalTime.now().format(FORMATTER), variant.technique(),
                safe(request.method()), safeHost(request), safe(request.pathWithoutQuery()), safe(request.query()),
                safeParamCount(request), 0, 0, 0, variant.comment(), null,
                error != null ? safe(error.getMessage()) : "");
    }

    private static String safeHost(HttpRequest request) {
        try {
            return request.httpService() != null ? request.httpService().host() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static int safeParamCount(HttpRequest request) {
        try {
            return request.parameters() != null ? request.parameters().size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
