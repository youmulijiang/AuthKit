package core;

import burp.api.montoya.http.message.requests.HttpRequest;

/** JWT 主动扫描请求变体。 */
public record JwtScanVariant(String technique, String tokenLocation, String comment,
                             HttpRequest request, boolean baseline) {
}
