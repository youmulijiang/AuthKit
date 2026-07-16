package core;

import burp.api.montoya.http.message.MimeType;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import view.AuthContextMenuProvider;

import java.util.*;

/**
 * IDOR 扫描 payload 生成服务。
 * 三种策略：
 * 1. 删除鉴权字段（no-auth）
 * 2. 数字参数后追加 "0"，启发式生成 3 个变体
 * 3. 同 host 代理历史中相同参数名的不同值替换
 */
public class IdorPayloadService {

    private static final int MAX_MUTATIONS_PER_PARAM = 3;
    private static final int MAX_MUTATIONS_PER_PATH_SEGMENT = 6;

    public List<IdorScanVariant> generateVariants(HttpRequest baseRequest,
                                                   List<ProxyHttpRequestResponse> proxyHistory) {
        if (baseRequest == null) return List.of();
        List<IdorScanVariant> variants = new ArrayList<>();
        addNoAuthVariant(baseRequest, variants);
        addNumericParamVariants(baseRequest, variants);
        addNumericPathVariants(baseRequest, variants);
        addProxyHistoryVariants(baseRequest, proxyHistory, variants);
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
            mutations.add(String.valueOf(numValue + 1));
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

                HttpRequest mutated;
                try {
                    mutated = baseRequest.withPath(newPath);
                } catch (Exception e) {
                    continue;
                }
                variants.add(new IdorScanVariant("numeric-path", "path[" + i + "]",
                        segment, newValue, mutated));
                count++;
            }
        }
    }

    /** 策略 3：在同 host 的代理历史请求和响应体中查找相同参数名的不同值进行替换 */
    private void addProxyHistoryVariants(HttpRequest baseRequest,
                                          List<ProxyHttpRequestResponse> proxyHistory,
                                          List<IdorScanVariant> variants) {
        if (proxyHistory == null || proxyHistory.isEmpty()) return;

        String targetHost = safeHost(baseRequest);
        if (targetHost == null || targetHost.isEmpty()) return;

        List<ParsedHttpParameter> baseParams = baseRequest.parameters();
        if (baseParams == null || baseParams.isEmpty()) return;

        // paramName(lowercase) → 原始参数对象
        Map<String, ParsedHttpParameter> baseParamMap = new LinkedHashMap<>();
        for (ParsedHttpParameter p : baseParams) {
            baseParamMap.put(p.name().toLowerCase(), p);
        }

        // paramName(lowercase) → 历史中发现的不同值（有序，先出现先排）
        Map<String, LinkedHashSet<String>> historyValues = new LinkedHashMap<>();

        for (ProxyHttpRequestResponse histItem : proxyHistory) {
            if (!targetHost.equals(histItem.host())) continue;

            // 扫描历史请求参数
            List<ParsedHttpParameter> histParams = histItem.request().parameters();
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
            if (histItem.response() != null) {
                MimeType mimeType = histItem.response().mimeType();
                if (isExtractableMimeType(mimeType)) {
                    String responseBody = histItem.response().bodyToString();
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
}
