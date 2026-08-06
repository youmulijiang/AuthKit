package core.scan.bypass403;

import burp.api.montoya.http.message.requests.HttpRequest;

/** 403 bypass 扫描请求变体。 */
public record Bypass403RequestVariant(String technique, String comment, HttpRequest request) {
}
