package core.scan.jwt;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** JWT 扫描结果以及与原始基线响应的相似度。 */
public record JwtScanResult(
        int index, String time, String technique, String tokenLocation,
        String method, String host, String path, int statusCode, int length,
        int similarity, boolean possibleVulnerability, long durationMs,
        String comment, HttpRequestResponse requestResponse, String error) {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static JwtScanResult success(int index, JwtScanVariant variant,
                                        HttpRequestResponse requestResponse,
                                        HttpResponse baselineResponse, long durationMs) {
        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();
        int statusCode = response != null ? response.statusCode() : 0;
        int length = response != null && response.body() != null ? response.body().length() : 0;
        int similarity = similarity(baselineResponse, response);
        boolean possible = !variant.baseline() && statusCode > 0 && statusCode < 400
                && baselineResponse != null && statusCode == baselineResponse.statusCode()
                && similarity >= 80;
        return new JwtScanResult(index, LocalTime.now().format(FORMATTER), variant.technique(),
                variant.tokenLocation(), safe(request.method()), safeHost(request),
                safe(request.pathWithoutQuery()), statusCode, length, similarity, possible,
                durationMs, variant.comment(), requestResponse, "");
    }

    public static JwtScanResult failure(int index, JwtScanVariant variant, Exception error) {
        HttpRequest request = variant.request();
        return new JwtScanResult(index, LocalTime.now().format(FORMATTER), variant.technique(),
                variant.tokenLocation(), safe(request.method()), safeHost(request),
                safe(request.pathWithoutQuery()), 0, 0, 0, false, 0,
                variant.comment(), null, error != null ? safe(error.getMessage()) : "");
    }

    static int similarity(HttpResponse baseline, HttpResponse response) {
        if (baseline == null || response == null) return 0;
        String left = safe(baseline.bodyToString());
        String right = safe(response.bodyToString());
        if (left.equals(right)) return 100;
        int max = Math.max(left.length(), right.length());
        if (max == 0) return 100;
        int lengthScore = 100 - (int) Math.min(100L,
                Math.abs((long) left.length() - right.length()) * 100L / max);
        return baseline.statusCode() == response.statusCode() ? lengthScore : lengthScore / 2;
    }

    private static String safeHost(HttpRequest request) {
        try {
            return request.httpService() != null ? request.httpService().host() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
