package core;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.security.SecureRandom;
import java.util.List;

/**
 * 伪造 IP 服务。
 * 负责生成伪造 IP、校验 IP，并将常见可伪造来源 IP 的请求头注入 HTTP 请求。
 */
public class FakeIpService {

    public static final String LOCALHOST_IP = "127.0.0.1";
    public static final String INTRUDER_PAYLOAD_NAME = "fakeIpPayloads";

    private static final List<String> FAKE_IP_HEADERS = List.of(
            "X-Forwarded-For", "X-Forwarded", "Forwarded-For", "Forwarded",
            "X-Requested-With", "X-Forwarded-Proto", "X-Forwarded-Host",
            "X-remote-IP", "X-remote-addr", "True-Client-IP", "X-Client-IP", "Client-IP", "X-Real-IP",
            "Ali-CDN-Real-IP", "Cdn-Src-Ip", "Cdn-Real-Ip", "CF-Connecting-IP", "X-Cluster-Client-IP",
            "WL-Proxy-Client-IP", "Proxy-Client-IP", "Fastly-Client-Ip", "True-Client-Ip", "X-Originating-IP",
            "X-Host", "X-Custom-IP-Authorization", "X-Api-Version"
    );

    private static final int[][] PUBLIC_IP_RANGES = {
            {607649792, 608174079},
            {1038614528, 1039007743},
            {1783627776, 1784676351},
            {2035023872, 2035154943},
            {2078801920, 2079064063},
            {-1950089216, -1948778497},
            {-1425539072, -1425014785},
            {-1236271104, -1235419137},
            {-770113536, -768606209},
            {-569376768, -564133889}
    };

    private final SecureRandom random;

    public FakeIpService() {
        this(new SecureRandom());
    }

    FakeIpService(SecureRandom random) {
        this.random = random;
    }

    public List<String> getFakeIpHeaders() {
        return FAKE_IP_HEADERS;
    }

    public HttpRequest addFakeIpHeaders(HttpRequest request, String ip) {
        if (request == null) {
            return null;
        }
        if (!isValidIpv4(ip)) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }
        HttpRequest updated = request;
        for (String header : FAKE_IP_HEADERS) {
            updated = updated.hasHeader(header)
                    ? updated.withUpdatedHeader(header, ip)
                    : updated.withAddedHeader(header, ip);
        }
        return updated;
    }

    public HttpRequest addLocalIpHeaders(HttpRequest request) {
        return addFakeIpHeaders(request, LOCALHOST_IP);
    }

    public HttpRequest addRandomIpHeaders(HttpRequest request) {
        return addFakeIpHeaders(request, randomPublicIp());
    }

    public HttpRequest addBruteForceHeaders(HttpRequest request) {
        return addRandomIpHeaders(request);
    }

    public String randomPublicIp() {
        int[] range = PUBLIC_IP_RANGES[random.nextInt(PUBLIC_IP_RANGES.length)];
        int value = range[0] + random.nextInt(range[1] - range[0]);
        return intToIpv4(value);
    }

    public boolean isValidIpv4(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String[] parts = ip.trim().split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
            int value = Integer.parseInt(part);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private String intToIpv4(int ip) {
        return ((ip >> 24) & 0xff) + "." + ((ip >> 16) & 0xff) + "."
                + ((ip >> 8) & 0xff) + "." + (ip & 0xff);
    }
}
