package core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 数据包来源服务：按需读取 Burp Proxy 历史 / 站点地图数据包。
 * <p>
 * 读取结果只作为**候选**返回，是否提供给 AI 由用户在弹窗中选择（不默认全量读取）；
 * 每个候选附带启发式评分（越权测试价值），用于弹窗中的建议预选。
 * 数据来源通过构造注入，便于单测替换 Montoya 调用。
 */
public class PacketSourceService {

    /** 数据包来源 */
    public enum Source {
        /** Burp Proxy HTTP 历史 */
        PROXY_HISTORY,
        /** 站点地图 */
        SITEMAP
    }

    /**
     * 候选数据包
     *
     * @param requestResponse 可直接发送/展示的报文
     * @param method          HTTP 方法
     * @param url             完整 URL
     * @param statusCode      响应状态码，无响应为 0
     * @param length          响应体长度，无响应为 0
     * @param score           越权测试价值评分（越高越值得测）
     */
    public record Candidate(HttpRequestResponse requestResponse, String method, String url,
                            int statusCode, int length, int score) {
    }

    /** 候选列表上限，避免弹窗与上下文过大 */
    private static final int MAX_CANDIDATES = 300;
    /** 静态资源后缀：无越权测试价值，直接排除 */
    private static final List<String> STATIC_EXTENSIONS = List.of(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico",
            ".woff", ".woff2", ".ttf", ".map", ".mp4", ".webp", ".mp3");
    /** 路径中的对象标识符：/users/123 形式 */
    private static final Pattern PATH_ID = Pattern.compile(".*/\\d+([/?#].*)?$");
    /** 查询参数中的对象标识符：id=123 / user_id=123 等 */
    private static final Pattern QUERY_ID = Pattern.compile(
            ".*[?&](id|uid|user_?id|account_?id|order_?id|tenant_?id|pid|gid)=\\d+.*");

    private final Supplier<List<ProxyHttpRequestResponse>> proxyHistorySupplier;
    private final Supplier<List<HttpRequestResponse>> siteMapSupplier;
    /**
     * 报文组装函数。默认走 Montoya 静态工厂 {@code HttpRequestResponse.httpRequestResponse}，
     * 该工厂依赖 ObjectFactoryLocator，单测环境为 null，因此允许注入替换。
     */
    private final java.util.function.BiFunction<HttpRequest, HttpResponse, HttpRequestResponse>
            responseFactory;

    public PacketSourceService(MontoyaApi api) {
        this(() -> api.proxy().history(), () -> api.siteMap().requestResponses(),
                HttpRequestResponse::httpRequestResponse);
    }

    /**
     * 构造数据包来源服务（测试用，直接注入数据来源与报文组装函数）
     *
     * @param proxyHistorySupplier Proxy 历史提供者
     * @param siteMapSupplier      站点地图提供者
     * @param responseFactory      请求 + 响应 → 报文 的组装函数
     */
    PacketSourceService(Supplier<List<ProxyHttpRequestResponse>> proxyHistorySupplier,
                        Supplier<List<HttpRequestResponse>> siteMapSupplier,
                        java.util.function.BiFunction<HttpRequest, HttpResponse,
                                HttpRequestResponse> responseFactory) {
        this.proxyHistorySupplier = proxyHistorySupplier;
        this.siteMapSupplier = siteMapSupplier;
        this.responseFactory = responseFactory;
    }

    /**
     * 读取候选数据包：按来源读取后过滤（静态资源、可选关键词）并按评分降序排列。
     *
     * @param source  数据包来源
     * @param keyword 可选关键词（大小写不敏感，匹配 URL），空表示不过滤
     * @return 候选列表，读取失败返回空列表
     */
    public List<Candidate> read(Source source, String keyword) {
        // 第一遍只读取廉价字段（方法 / URL / 状态码 / Content-Type），
        // 不解码响应体、也不组装报文对象——大历史（上万条）下这是主要耗时来源。
        List<RawItem> rawItems = new ArrayList<>();
        if (source == Source.PROXY_HISTORY) {
            for (ProxyHttpRequestResponse item : safeGet(proxyHistorySupplier, "proxy history")) {
                if (item == null || item.request() == null) {
                    continue;
                }
                rawItems.add(toRawItem(item.request(), item.response()));
            }
        } else {
            for (HttpRequestResponse item : safeGet(siteMapSupplier, "site map")) {
                if (item == null || item.request() == null) {
                    continue;
                }
                rawItems.add(toRawItem(item.request(), item.response()));
            }
        }

        String needle = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<RawItem> filtered = new ArrayList<>();
        for (RawItem item : rawItems) {
            if (isStaticResource(item.url())) {
                continue;
            }
            if (!needle.isEmpty() && !item.url().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            filtered.add(item);
        }
        filtered.sort(Comparator.comparingInt(RawItem::score).reversed());

        // 第二遍只对最终展示的候选（≤ MAX_CANDIDATES）组装报文、读取长度
        int limit = Math.min(filtered.size(), MAX_CANDIDATES);
        List<Candidate> candidates = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            candidates.add(toCandidate(filtered.get(i)));
        }
        return candidates;
    }

    /** 中间态条目：仅含评分与展示所需字段，响应体保持未解码 */
    private record RawItem(HttpRequest request, HttpResponse response, String method,
                           String url, int statusCode, int score) {
    }

    private RawItem toRawItem(HttpRequest request, HttpResponse response) {
        String method = request.method();
        String url = request.url();
        String safeUrl = url != null ? url : "";
        int statusCode = response != null ? response.statusCode() : 0;
        boolean json = isJsonResponse(response);
        return new RawItem(request, response, method, safeUrl, statusCode,
                score(method, safeUrl, statusCode, json));
    }

    /** 组装候选：长度取响应体长度（无响应置 0） */
    private Candidate toCandidate(RawItem item) {
        int length = 0;
        if (item.response() != null) {
            try {
                length = item.response().body().length();
            } catch (Exception ignored) {
                length = 0;
            }
        }
        return new Candidate(
                responseFactory.apply(item.request(),
                        item.response() != null ? item.response() : HttpResponse.httpResponse("")),
                item.method(), item.url(), item.statusCode(), length, item.score());
    }

    /** 小响应允许嗅探响应体判断 JSON，避免大响应逐条解码 */
    private static final int JSON_PEEK_LIMIT = 4096;

    /**
     * 响应是否形似 JSON：优先看 Content-Type，缺失时仅对小响应体做前缀嗅探。
     * 大响应体不做解码——历史数据量大时 bodyToString() 是主要性能瓶颈。
     */
    static boolean isJsonResponse(HttpResponse response) {
        if (response == null) {
            return false;
        }
        try {
            String contentType = response.headerValue("Content-Type");
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
                return true;
            }
            if (response.body().length() > JSON_PEEK_LIMIT) {
                return false;
            }
            String body = response.bodyToString();
            return body != null && body.trim().startsWith("{");
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * 启发式评分：对象标识符最值得测，其次查询参数与 JSON 响应；
     * 静态方法（OPTIONS/HEAD/TRACE）与服务端错误降权。
     *
     * @param method     HTTP 方法
     * @param url        完整 URL
     * @param statusCode 响应状态码
     * @param jsonResponse 响应体是否形似 JSON
     * @return 评分（可为负）
     */
    static int score(String method, String url, int statusCode, boolean jsonResponse) {
        int score = 0;
        if (hasObjectIdentifier(url)) {
            score += 40;
        }
        if (url.contains("?")) {
            score += 20;
        }
        if (jsonResponse) {
            score += 15;
        }
        String upper = method == null ? "" : method.toUpperCase(Locale.ROOT);
        switch (upper) {
            case "GET", "POST", "PUT", "PATCH", "DELETE" -> score += 10;
            case "OPTIONS", "HEAD", "TRACE" -> score -= 20;
            default -> { }
        }
        if (statusCode >= 200 && statusCode < 300) {
            score += 10;
        }
        if (statusCode >= 500) {
            score -= 30;
        }
        return score;
    }

    /** URL 是否包含对象标识符（路径数字段或 id 类查询参数） */
    public static boolean hasObjectIdentifier(String url) {
        if (url == null) {
            return false;
        }
        return PATH_ID.matcher(url).matches() || QUERY_ID.matcher(url).matches();
    }

    /**
     * 从模型给出的范围提示中提取更可能命中 URL 的关键词。
     * <p>
     * 模型有时把整句描述写进标记块（例如「example.com 的订单接口」），直接当作关键词过滤
     * 会导致候选全被滤掉；这里取首个空白分隔片段，URL 形式则退化为 host。
     *
     * @param hint 原始提示
     * @return 可用于过滤的关键词；无法提取（纯非 ASCII 描述等）时返回空串
     */
    public static String sanitizeKeyword(String hint) {
        if (hint == null) {
            return "";
        }
        String text = hint.trim();
        if (text.isEmpty()) {
            return "";
        }
        String token = text.split("\\s+")[0];
        if (!token.chars().allMatch(c -> c < 128)) {
            return "";
        }
        int schemeEnd = token.indexOf("://");
        if (schemeEnd >= 0) {
            String rest = token.substring(schemeEnd + 3);
            int slash = rest.indexOf('/');
            return slash >= 0 ? rest.substring(0, slash) : rest;
        }
        return token;
    }

    /** 是否为静态资源（按路径后缀判断） */
    static boolean isStaticResource(String url) {
        if (url == null) {
            return false;
        }
        String path = url.toLowerCase(Locale.ROOT);
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        for (String ext : STATIC_EXTENSIONS) {
            if (path.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** 读取数据来源，失败时记录日志并返回空列表 */
    private static <T> List<T> safeGet(Supplier<List<T>> supplier, String label) {
        try {
            List<T> items = supplier.get();
            return items != null ? items : List.of();
        } catch (Exception ex) {
            utils.LogUtils.INSTANCE.error("Failed to read " + label, ex);
            return List.of();
        }
    }
}
