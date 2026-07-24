package core;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JwtPayloadServiceTest {

    private final JwtPayloadService service = new JwtPayloadService();

    @Test
    @DisplayName("应从请求头 JWT 生成签名绕过和 kid 注入变体")
    void shouldGenerateSignatureAndKidVariantsFromHeader() {
        String token = token("HS256", "not-in-dictionary", "{\"sub\":\"1\"}");
        HttpRequest request = mockRequest("Bearer " + token, "");

        List<JwtScanVariant> variants = service.generateVariants(request);
        Set<String> techniques = variants.stream().map(JwtScanVariant::technique).collect(Collectors.toSet());

        assertTrue(techniques.containsAll(Set.of("baseline", "unsigned-payload", "invalid-signature",
                "empty-signature", "alg-none", "kid-path-traversal", "kid-sql-injection")));
        assertTrue(variants.stream().allMatch(v -> v.tokenLocation().startsWith("Header:")));
        verify(request, atLeastOnce()).withUpdatedHeader(eq("Authorization"), anyString());
    }

    @Test
    @DisplayName("发现常见 HMAC 弱密钥后应生成重签名和声明校验变体")
    void shouldGenerateWeakSecretAndClaimVariants() {
        String token = token("HS256", "secret", "{\"sub\":\"1\",\"role\":\"user\"}");
        HttpRequest request = mockRequest("Bearer " + token, "");

        Set<String> techniques = service.generateVariants(request).stream()
                .map(JwtScanVariant::technique).collect(Collectors.toSet());

        assertTrue(techniques.containsAll(Set.of("weak-hmac-secret", "claim-privilege-escalation",
                "expired-token", "future-nbf")));
    }

    @Test
    @DisplayName("应识别请求体中的 JWT 并使用 withBody 生成变体")
    void shouldFindJwtInBody() {
        String token = token("RS256", "unused", "{\"sub\":\"1\"}");
        HttpRequest request = mockRequest("", "{\"token\":\"" + token + "\"}");

        List<JwtScanVariant> variants = service.generateVariants(request);

        assertFalse(variants.isEmpty());
        assertTrue(variants.stream().allMatch(v -> v.tokenLocation().equals("Body")));
        verify(request, atLeastOnce()).withBody(anyString());
    }

    @Test
    @DisplayName("不含 JWT 的请求不应生成扫描数据包")
    void shouldIgnoreRequestWithoutJwt() {
        HttpRequest request = mockRequest("Bearer opaque-token", "plain body");

        assertFalse(service.containsJwt(request));
        assertTrue(service.generateVariants(request).isEmpty());
    }

    private HttpRequest mockRequest(String authorization, String body) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.path()).thenReturn("/api");
        when(request.bodyToString()).thenReturn(body);
        List<HttpHeader> headers;
        if (authorization.isEmpty()) {
            headers = List.of();
        } else {
            HttpHeader header = mock(HttpHeader.class);
            when(header.name()).thenReturn("Authorization");
            when(header.value()).thenReturn(authorization);
            headers = List.of(header);
        }
        when(request.headers()).thenReturn(headers);
        when(request.withUpdatedHeader(anyString(), anyString())).thenReturn(request);
        when(request.withBody(anyString())).thenReturn(request);
        when(request.withPath(anyString())).thenReturn(request);
        return request;
    }

    private String token(String algorithm, String secret, String payloadJson) {
        try {
            String header = encode("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}");
            String payload = encode(payloadJson);
            String input = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return input + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
