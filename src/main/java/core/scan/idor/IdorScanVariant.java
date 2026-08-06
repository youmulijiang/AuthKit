package core.scan.idor;

import burp.api.montoya.http.message.requests.HttpRequest;

/** IDOR 扫描请求变体。 */
public record IdorScanVariant(String technique, String paramName, String originalValue,
                               String newValue, HttpRequest request) {

    public String comment() {
        if (paramName == null || paramName.isEmpty()) {
            return technique;
        }
        String orig = originalValue != null ? originalValue : "";
        String nv = newValue != null ? newValue : "";
        return paramName + ": " + orig + " → " + nv;
    }
}
