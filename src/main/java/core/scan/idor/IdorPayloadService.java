package core.scan.idor;

import burp.api.montoya.http.message.MimeType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import view.AuthContextMenuProvider;

import java.util.*;
import java.util.regex.Pattern;

/**
 * IDOR 扫描 payload 生成服务。
 * 策略：
 * 1. 删除鉴权字段（no-auth）
 * 2. 数字参数启发式变形（numeric-param）
 * 3. 数字路径段启发式替换（numeric-path）
 * 4. 常见权限/版本路径段互换（common-path）
 * 5. 同 host 历史参数/路径值替换（history-param / history-path）
 * 6. 无鉴权常见参数时一次性注入 API 分配 fuzz（common-param）
 */
public class IdorPayloadService {

    private static final int MAX_MUTATIONS_PER_PARAM = 3;
    private static final int MAX_MUTATIONS_PER_PATH_SEGMENT = 6;
    private static final Pattern VERSION_SEGMENT = Pattern.compile("(?i)v\\d+");
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    /** 鉴权/对象引用常见参数名，用于 API 参数分配探测 */
    static final List<String> COMMON_AUTH_PARAM_NAMES = List.of(
            "id", "user", "account", "number", "order", "no", "doc",
            "key", "email", "group", "profile", "edit", "report"
    );

    public List<IdorScanVariant> generateVariants(HttpRequest baseRequest,
                                                   List<ProxyHttpRequestResponse> proxyHistory) {
        return generateVariants(baseRequest, proxyHistory, List.of());
    }

    public List<IdorScanVariant> generateVariants(HttpRequest baseRequest,
                                                   List<ProxyHttpRequestResponse> proxyHistory,
                                                   List<HttpRequestResponse> siteMapItems) {
        if (baseRequest == null) return List.of();
        List<IdorScanVariant> variants = new ArrayList<>();
        addNoAuthVariant(baseRequest, variants);
        addNumericParamVariants(baseRequest, variants);
        addNumericPathVariants(baseRequest, variants);
        addCommonPathVariants(baseRequest, variants);
        addHistoryVariants(baseRequest, collectHistoryItems(proxyHistory, siteMapItems), variants);
        addCommonParamVariants(baseRequest, variants);
        return variants;
    }

    /** 策略 1：删除所有鉴权字段后发包 */
    private void addNoAuthVariant(HttpRequest baseRequest, List<IdorScanVariant> variants) {
        HttpRequest noAuth = AuthContextMenuProvider.removeAuthHeaders(baseRequest);
        variants.add(new IdorScanVariant("no-auth", null, null, null, noAuth));
    }

    /** 策略 2：对纯数字参数值追加 "0"，启发式生成最多 3 个变体（如 1 → 10, 20, 2） */
    private void addNumericParamVariants(HttpRequest baseRequest, List<IdorScanVariant> variants) {
        List<ParsedHttpParameter> params = baseRequest.parameters();
        if (params == null) return;

        for (ParsedHttpParameter param : params) {
            String value = param.value();
            if (!isAllDigits(value)) continue;

            long numValue;
            try {
                numValue = Long.parseLong(value);
            } catch (NumberFormatException e) {
                continue;
            }

            // 启发式：追加"0"（×10）、×20、+1，去重后最多取 3 个
            LinkedHashSet<String> mutations = new LinkedHashSet<>();
            mutations.add(value + "0");
            mutations.add(String.valueOf(numValue * 20));
            mutations.add(String.valueOf(numValue + 1));

            int count = 0;
            for (String newValue : mutations) {
                if (count >= MAX_MUTATIONS_PER_PARAM) break;
                if (newValue.equals(value)) continue;
                HttpRequest mutated = replaceParam(baseRequest, param, newValue);
                if (mutated != null) {
                    variants.add(new IdorScanVariant("numeric-param", param.name(), value, newValue, mutated));
                    count++;
                }
            }
        }
    }

    /** 策略 4：对 URL path 中的纯数字路径段启发式替换，每段独立生成最多 6 个变体 */
    private void addNumericPathVariants(HttpRequest baseRequest, List<IdorScanVariant> variants) {
        String path = baseRequest.pathWithoutQuery();
        if (path == null || path.isEmpty()) return;

        String[] segments = path.split("/", -1);

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (!isAllDigits(segment)) continue;

            long numValue;
            try {
                numValue = Long.parseLong(segment);
            } catch (NumberFormatException e) {
                continue;
            }

            // 按优先级构建去重变体集，跳过原值和负数
            LinkedHashSet<String> mutations = new LinkedHashSet<>();
            if (numValue < Long.MAX_VALUE) mutations.add(String.valueOf(numValue + 1));
            if (numValue - 1 >= 0) mutations.add(String.valueOf(numValue - 1));
            mutations.add("1");
            mutations.add("0");
            mutations.add("99999");
            mutations.add(segment + "0");

            int count = 0;
            for (String newValue : mutations) {
                if (count >= MAX_MUTATIONS_PER_PATH_SEGMENT) break;
                if (newValue.equals(segment)) continue;

                segments[i] = newValue;
                String newPath = String.join("/", segments);
                segments[i] = segment; // restore

                String query = baseRequest.query();
                String fullPath = (query != null && !query.isEmpty()) ? newPath + "?" + query : newPath;
                HttpRequest mutated;
                try {
                    mutated = baseRequest.withPath(fullPath);
                } catch (Exception e) {
                    continue;
                }
                variants.add(new IdorScanVariant("numeric-path", "path[" + i + "]",
                        segment, newValue, mutated));
                count++;
            }
        }
    }

    /** 策略 5：常见权限/版本路径段互换，如 /admin/ ↔ /user/、/api/v1/ → /api/v2/。 */
    private void addCommonPathVariants(HttpRequest baseRequest, List<IdorScanVariant> variants) {
        String path = baseRequest.pathWithoutQuery();
        if (path == null || path.isEmpty()) return;

        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            LinkedHashSet<String> mutations = new LinkedHashSet<>();
            addVersionSegmentMutations(segment, mutations);
            if ("admin".equalsIgnoreCase(segment)) mutations.add(matchCase(segment, "user"));
            if ("user".equalsIgnoreCase(segment)) mutations.add(matchCase(segment, "admin"));

            int count = 0;
            for (String newValue : mutations) {
                if (count >= MAX_MUTATIONS_PER_PATH_SEGMENT) break;
                HttpRequest mutated = replacePathSegment(baseRequest, segments, i, newValue);
                if (mutated != null) {
                    variants.add(new IdorScanVariant("common-path", "path[" + i + "]",
                            segment, newValue, mutated));
                    count++;
                }
            }
        }
    }

    /**
     * 策略 6：若请求中不包含任何鉴权常见参数，则一次性注入全部常见参数做 API 分配 fuzz。
     * 普通对象 ID 类参数使用 "1"；email 等特殊类型使用对应格式样本。
     * 通过追加 query 实现，避免依赖 Montoya HttpParameter factory（便于单测）。
     */
    private void addCommonParamVariants(HttpRequest baseRequest, List<IdorScanVariant> variants) {
        Set<String> existingNames = new HashSet<>();
        List<ParsedHttpParameter> params = baseRequest.parameters();
        if (params != null) {
            for (ParsedHttpParameter param : params) {
                if (param != null && param.name() != null) {
                    existingNames.add(param.name().toLowerCase(Locale.ROOT));
                }
            }
        }

        for (String name : COMMON_AUTH_PARAM_NAMES) {
            if (existingNames.contains(name)) return;
        }

        StringBuilder injectedQuery = new StringBuilder();
        for (String name : COMMON_AUTH_PARAM_NAMES) {
            String value = fuzzValueForCommonParam(name);
            if (!injectedQuery.isEmpty()) injectedQuery.append('&');
            injectedQuery.append(name).append('=').append(value);
        }

        String path = baseRequest.pathWithoutQuery();
        if (path == null || path.isEmpty()) path = "/";
        String existingQuery = baseRequest.query();
        String fullPath;
        if (existingQuery != null && !existingQuery.isEmpty()) {
            fullPath = path + "?" + existingQuery + "&" + injectedQuery;
        } else {
            fullPath = path + "?" + injectedQuery;
        }

        try {
            HttpRequest mutated = baseRequest.withPath(fullPath);
            if (mutated != null) {
                variants.add(new IdorScanVariant(
                        "common-param",
                        "common-auth",
                        null,
                        injectedQuery.toString(),
                        mutated));
            }
        } catch (Exception ignored) {
            // withPath 失败时跳过该策略
        }
    }

    /** 按参数语义生成 API 分配 fuzz 样本值 */
    static String fuzzValueForCommonParam(String paramName) {
        if (paramName == null) return "1";
        return switch (paramName.toLowerCase(Locale.ROOT)) {
            case "email" -> "admin@test.com";
            case "user", "account", "profile" -> "admin";
            case "key" -> "1";
            default -> "1";
        };
    }

    /** 策略 3：在同 host 的代理历史或 sitemap 请求/响应体中查找可替换的参数和路径段。 */
    private void addHistoryVariants(HttpRequest baseRequest,
                                    List<HistoryItem> historyItems,
                                    List<IdorScanVariant> variants) {
        if (historyItems == null || historyItems.isEmpty()) return;

        String targetHost = safeHost(baseRequest);
        if (targetHost == null || targetHost.isEmpty()) return;

        List<ParsedHttpParameter> baseParams = baseRequest.parameters();
        if (baseParams == null) baseParams = List.of();

        // paramName(lowercase) → 原始参数对象
        Map<String, ParsedHttpParameter> baseParamMap = new LinkedHashMap<>();
        for (ParsedHttpParameter p : baseParams) {
            baseParamMap.put(p.name().toLowerCase(), p);
        }

        // paramName(lowercase) → 历史中发现的不同值（有序，先出现先排）
        Map<String, LinkedHashSet<String>> historyValues = new LinkedHashMap<>();
        Map<Integer, LinkedHashSet<String>> historyPathValues = new LinkedHashMap<>();
        String basePath = baseRequest.pathWithoutQuery();
        String[] baseSegments = basePath != null ? basePath.split("/", -1) : new String[0];

        for (HistoryItem histItem : historyItems) {
            if (!targetHost.equals(histItem.host())) continue;
            HttpRequest histRequest = histItem.request();
            if (histRequest == null) continue;

            collectHistoryPathValues(baseSegments, histRequest.pathWithoutQuery(), historyPathValues);

            // 扫描历史请求参数
            List<ParsedHttpParameter> histParams = histRequest.parameters();
            if (histParams != null) {
                for (ParsedHttpParameter histParam : histParams) {
                    String key = histParam.name().toLowerCase();
                    if (!baseParamMap.containsKey(key)) continue;
                    String originalValue = baseParamMap.get(key).value();
                    String histValue = histParam.value();
                    if (!histValue.equals(originalValue) && !histValue.isEmpty()) {
                        historyValues.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(histValue);
                    }
                }
            }

            // 扫描历史响应体中的参数值（按 MIME 类型选择提取策略）
            HttpResponse histResponse = histItem.response();
            if (histResponse != null) {
                MimeType mimeType = histResponse.mimeType();
                if (isExtractableMimeType(mimeType)) {
                    String responseBody = histResponse.bodyToString();
                    if (responseBody != null && !responseBody.isEmpty()) {
                        for (Map.Entry<String, ParsedHttpParameter> entry : baseParamMap.entrySet()) {
                            String key = entry.getKey();
                            String paramName = entry.getValue().name();
                            String originalValue = entry.getValue().value();
                            String found = extractValueFromBody(responseBody, paramName, mimeType);
                            if (found != null && !found.equals(originalValue) && !found.isEmpty()) {
                                historyValues.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(found);
                            }
                        }
                    }
                }
            }
        }

        // 为每个有历史不同值的参数生成最多 3 个替换变体
        for (Map.Entry<String, LinkedHashSet<String>> entry : historyValues.entrySet()) {
            ParsedHttpParameter baseParam = baseParamMap.get(entry.getKey());
            if (baseParam == null) continue;
            int count = 0;
            for (String newValue : entry.getValue()) {
                if (count >= MAX_MUTATIONS_PER_PARAM) break;
                HttpRequest mutated = replaceParam(baseRequest, baseParam, newValue);
                if (mutated != null) {
                    variants.add(new IdorScanVariant("history-param", baseParam.name(),
                            baseParam.value(), newValue, mutated));
                    count++;
                }
            }
        }

        for (Map.Entry<Integer, LinkedHashSet<String>> entry : historyPathValues.entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= baseSegments.length) continue;
            String original = baseSegments[index];
            int count = 0;
            for (String newValue : entry.getValue()) {
                if (count >= MAX_MUTATIONS_PER_PATH_SEGMENT) break;
                HttpRequest mutated = replacePathSegment(baseRequest, baseSegments, index, newValue);
                if (mutated != null) {
                    variants.add(new IdorScanVariant("history-path", "path[" + index + "]",
                            original, newValue, mutated));
                    count++;
                }
            }
        }
    }

    private List<HistoryItem> collectHistoryItems(List<ProxyHttpRequestResponse> proxyHistory,
                                                  List<HttpRequestResponse> siteMapItems) {
        List<HistoryItem> items = new ArrayList<>();
        if (proxyHistory != null) {
            for (ProxyHttpRequestResponse item : proxyHistory) {
                if (item == null) continue;
                items.add(new HistoryItem(proxyHistoryHost(item), item.request(), item.response()));
            }
        }
        if ((proxyHistory == null || proxyHistory.isEmpty()) && siteMapItems != null) {
            for (HttpRequestResponse item : siteMapItems) {
                if (item == null) continue;
                HttpRequest request = item.request();
                String host = safeHost(request);
                if ((host == null || host.isEmpty()) && item.httpService() != null) {
                    host = item.httpService().host();
                }
                items.add(new HistoryItem(host, request, item.response()));
            }
        }
        return items;
    }

    private void collectHistoryPathValues(String[] baseSegments, String historyPath,
                                          Map<Integer, LinkedHashSet<String>> historyPathValues) {
        if (baseSegments.length == 0 || historyPath == null || historyPath.isEmpty()) return;
        String[] historySegments = historyPath.split("/", -1);
        if (historySegments.length != baseSegments.length) return;

        for (int i = 0; i < baseSegments.length; i++) {
            String original = baseSegments[i];
            String candidate = historySegments[i];
            if (candidate == null || candidate.isEmpty() || candidate.equals(original)) continue;
            if (isCompatiblePathReplacement(original, candidate)) {
                historyPathValues.computeIfAbsent(i, key -> new LinkedHashSet<>()).add(candidate);
            }
        }
    }

    /** 判断该 MIME 类型的响应体是否值得尝试提取参数值 */
    private boolean isExtractableMimeType(MimeType mimeType) {
        if (mimeType == null) return true;
        return switch (mimeType) {
            case JSON, HTML, XML, PLAIN_TEXT, NONE -> true;
            default -> false; // 图片、二进制、音视频、字体等直接跳过
        };
    }

    /**
     * 按 MIME 类型路由，从响应体中提取指定参数名的值。
     * JSON → 只走 JSON 提取；其余类型先尝试 JSON 再尝试 form。
     */
    private String extractValueFromBody(String body, String paramName, MimeType mimeType) {
        if (body == null || paramName == null || paramName.isEmpty()) return null;
        if (mimeType == MimeType.JSON) {
            return extractFromJson(body, paramName);
        }
        // HTML / XML / PLAIN_TEXT / NONE 等：可能内嵌 JSON，先 JSON 再 form
        String result = extractFromJson(body, paramName);
        return result != null ? result : extractFromForm(body, paramName);
    }

    /** 从 JSON 体中提取参数值（支持字符串值和数字值） */
    private String extractFromJson(String body, String paramName) {
        // JSON 字符串值："paramName":"value" 或 "paramName": "value"
        for (String pattern : new String[]{"\"" + paramName + "\":\"", "\"" + paramName + "\": \""}) {
            int idx = body.indexOf(pattern);
            if (idx >= 0) {
                int start = idx + pattern.length();
                int end = body.indexOf("\"", start);
                if (end > start) return body.substring(start, end);
            }
        }
        // JSON 数字值："paramName":123 或 "paramName": 123
        for (String prefix : new String[]{"\"" + paramName + "\":", "\"" + paramName + "\": "}) {
            int idx = body.indexOf(prefix);
            if (idx >= 0) {
                int start = idx + prefix.length();
                while (start < body.length() && body.charAt(start) == ' ') start++;
                if (start < body.length() && (Character.isDigit(body.charAt(start))
                        || body.charAt(start) == '-')) {
                    int end = start;
                    while (end < body.length() && (Character.isDigit(body.charAt(end))
                            || body.charAt(end) == '-')) end++;
                    if (end > start) return body.substring(start, end);
                }
            }
        }
        return null;
    }

    /** 从 form-encoded 体中提取参数值（paramName=value&...） */
    private String extractFromForm(String body, String paramName) {
        String formPattern = paramName + "=";
        int idx = body.indexOf(formPattern);
        if (idx >= 0) {
            int start = idx + formPattern.length();
            int end = body.indexOf("&", start);
            if (end < 0) end = body.length();
            String val = body.substring(start, end).trim();
            if (!val.isEmpty()) return val;
        }
        return null;
    }

    private HttpRequest replaceParam(HttpRequest request, ParsedHttpParameter param, String newValue) {
        try {
            HttpParameter newParam = switch (param.type()) {
                case URL -> HttpParameter.urlParameter(param.name(), newValue);
                case BODY -> HttpParameter.bodyParameter(param.name(), newValue);
                case COOKIE -> HttpParameter.cookieParameter(param.name(), newValue);
                default -> HttpParameter.urlParameter(param.name(), newValue);
            };
            return request.withUpdatedParameters(newParam);
        } catch (Exception e) {
            return null;
        }
    }

    private HttpRequest replacePathSegment(HttpRequest baseRequest, String[] segments, int index, String newValue) {
        if (newValue == null || newValue.isEmpty() || newValue.equals(segments[index])) return null;
        String original = segments[index];
        segments[index] = newValue;
        String newPath = String.join("/", segments);
        segments[index] = original;

        String query = baseRequest.query();
        String fullPath = (query != null && !query.isEmpty()) ? newPath + "?" + query : newPath;
        try {
            return baseRequest.withPath(fullPath);
        } catch (Exception e) {
            return null;
        }
    }

    private void addVersionSegmentMutations(String segment, LinkedHashSet<String> mutations) {
        if (!isVersionSegment(segment)) return;
        long version = Long.parseLong(segment.substring(1));
        if (version < Long.MAX_VALUE) mutations.add("v" + (version + 1));
        if (version > 0) mutations.add("v" + (version - 1));
        mutations.add("v1");
        mutations.add("v2");
    }

    private boolean isCompatiblePathReplacement(String original, String candidate) {
        if (original == null || candidate == null || original.isEmpty() || candidate.isEmpty()) return false;
        if (isAllDigits(original) && isAllDigits(candidate)) return true;
        if (isVersionSegment(original) && isVersionSegment(candidate)) return true;
        if (isUuid(original) && isUuid(candidate)) return true;
        return ("admin".equalsIgnoreCase(original) && "user".equalsIgnoreCase(candidate))
                || ("user".equalsIgnoreCase(original) && "admin".equalsIgnoreCase(candidate));
    }

    private boolean isVersionSegment(String value) {
        return value != null && VERSION_SEGMENT.matcher(value).matches();
    }

    private boolean isUuid(String value) {
        return value != null && UUID_SEGMENT.matcher(value).matches();
    }

    private String matchCase(String original, String replacement) {
        if (original.equals(original.toUpperCase(Locale.ROOT))) return replacement.toUpperCase(Locale.ROOT);
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }
        return replacement;
    }

    private boolean isAllDigits(String value) {
        if (value == null || value.isEmpty()) return false;
        for (char c : value.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    private String safeHost(HttpRequest request) {
        try {
            return request.httpService() != null ? request.httpService().host() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String proxyHistoryHost(ProxyHttpRequestResponse item) {
        try {
            if (item.httpService() != null) {
                return item.httpService().host();
            }
        } catch (Exception ignored) {
        }
        try {
            return item.host();
        } catch (Exception e) {
            return "";
        }
    }

    private record HistoryItem(String host, HttpRequest request, HttpResponse response) {
    }
}
