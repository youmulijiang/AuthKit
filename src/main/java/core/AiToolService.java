package core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AI 内置工具服务
 * <p>
 * 解析模型回复中的工具调用标记块，支持四类工具：
 * <ul>
 *   <li>{@code <<<SEND_HTTP_REQUEST>>>原始请求<<<END>>>}：通过 Montoya API 实际发送请求
 *       并返回原始响应文本，使 AI 能在对话中迭代验证越权漏洞</li>
 *   <li>{@code <<<GET_PACKETS>>><<<END>>>}：按需拉取数据表当前选中的数据包</li>
 *   <li>{@code <<<GET_PROXY_HISTORY>>>关键词<<<END>>>}：请求读取 Proxy 历史数据包，
 *       由用户在弹窗中选择具体报文</li>
 *   <li>{@code <<<GET_SITEMAP>>>关键词<<<END>>>}：请求读取站点地图数据包，同样经用户选择</li>
 * </ul>
 * 后三类只识别标记，实际取数与用户确认由调用方（对话面板）完成。
 */
public class AiToolService {

    /** 工具调用起始标记：发送 HTTP 请求 */
    public static final String TOOL_BEGIN = "<<<SEND_HTTP_REQUEST>>>";
    /** 工具调用起始标记：拉取数据表当前选中的数据包 */
    public static final String PACKETS_BEGIN = "<<<GET_PACKETS>>>";
    /** 工具调用起始标记：读取 Proxy 历史数据包（需用户选择） */
    public static final String PROXY_HISTORY_BEGIN = "<<<GET_PROXY_HISTORY>>>";
    /** 工具调用起始标记：读取站点地图数据包（需用户选择） */
    public static final String SITEMAP_BEGIN = "<<<GET_SITEMAP>>>";
    /** 工具调用起始标记：向用户提问并给出建议选项 */
    public static final String ASK_USER_BEGIN = "<<<ASK_USER>>>";
    /** 工具调用结束标记 */
    public static final String TOOL_END = "<<<END>>>";

    /** 询问工具的建议选项上限（与询问卡片一致） */
    public static final int MAX_ASK_OPTIONS = 3;

    /** 单次工具调用响应回填的最大长度，防止对话历史无限膨胀 */
    private static final int MAX_RESPONSE_FEEDBACK_LENGTH = 8000;

    /** 工具调用类型 */
    public enum ToolType {
        /** 无工具调用 */
        NONE,
        /** 发送 HTTP 请求 */
        SEND_REQUEST,
        /** 拉取数据表当前选中的数据包 */
        GET_PACKETS,
        /** 读取 Proxy 历史数据包 */
        PROXY_HISTORY,
        /** 读取站点地图数据包 */
        SITEMAP,
        /** 向用户提问并给出建议选项 */
        ASK_USER
    }

    /**
     * 询问请求解析结果
     *
     * @param question 问题文本
     * @param options  建议选项（最多 {@link #MAX_ASK_OPTIONS} 个）
     */
    public record AskRequest(String question, List<String> options) {
    }

    /** 标记块起始标记与工具类型的对应关系（顺序即优先级相同的比较基准） */
    private static final Object[][] TOOL_MARKERS = {
            {TOOL_BEGIN, ToolType.SEND_REQUEST},
            {PACKETS_BEGIN, ToolType.GET_PACKETS},
            {PROXY_HISTORY_BEGIN, ToolType.PROXY_HISTORY},
            {SITEMAP_BEGIN, ToolType.SITEMAP},
            {ASK_USER_BEGIN, ToolType.ASK_USER}
    };

    /**
     * 工具调用解析结果
     *
     * @param type    工具类型
     * @param payload 标记块内文本（发送请求为原始请求文本，拉取数据包为空串）
     */
    public record ToolCall(ToolType type, String payload) {

        /** 无工具调用 */
        public static final ToolCall NONE = new ToolCall(ToolType.NONE, "");
    }

    private final Http http;
    private final java.util.function.Function<String, HttpRequest> requestParser;

    /**
     * 构造 AI 发包工具服务
     *
     * @param api MontoyaApi 实例
     */
    public AiToolService(MontoyaApi api) {
        this(api.http());
    }

    /**
     * 构造 AI 发包工具服务（测试用，直接注入 Http）
     *
     * @param http Montoya Http API
     */
    AiToolService(Http http) {
        this(http, HttpRequest::httpRequest);
    }

    /**
     * 构造 AI 发包工具服务（注入请求解析器，便于单测替换静态工厂）
     *
     * @param http          Montoya Http API
     * @param requestParser 原始文本到 HttpRequest 的解析函数
     */
    AiToolService(Http http, java.util.function.Function<String, HttpRequest> requestParser) {
        this.http = http;
        this.requestParser = requestParser;
    }

    /**
     * 解析模型回复中的工具调用：取最先出现且带结束标记的一处标记块。
     * 纯字符串处理，不依赖 Montoya，可按需在任意线程调用。
     *
     * @param replyContent 模型回复文本
     * @return 工具调用；无完整标记块返回 {@link ToolCall#NONE}
     */
    public static ToolCall parseToolCall(String replyContent) {
        if (replyContent == null) {
            return ToolCall.NONE;
        }
        String earliestMarker = null;
        ToolType earliestType = ToolType.NONE;
        int earliestIndex = Integer.MAX_VALUE;
        for (Object[] entry : TOOL_MARKERS) {
            String marker = (String) entry[0];
            int index = completeBlockIndex(replyContent, marker);
            if (index >= 0 && index < earliestIndex) {
                earliestIndex = index;
                earliestMarker = marker;
                earliestType = (ToolType) entry[1];
            }
        }
        return earliestMarker == null
                ? ToolCall.NONE
                : new ToolCall(earliestType, blockBody(replyContent, earliestMarker));
    }

    /**
     * 提取第一处发包工具块内的原始 HTTP 请求文本
     *
     * @param replyContent 模型回复文本
     * @return 原始请求文本，未找到完整块返回 null
     */
    public String extractRequestText(String replyContent) {
        ToolCall call = parseToolCall(replyContent);
        return call.type() == ToolType.SEND_REQUEST ? call.payload() : null;
    }

    /** 返回带结束标记的起始下标，无完整块返回 -1 */
    private static int completeBlockIndex(String content, String begin) {
        int beginIndex = content.indexOf(begin);
        if (beginIndex < 0) {
            return -1;
        }
        return content.indexOf(TOOL_END, beginIndex + begin.length()) >= 0 ? beginIndex : -1;
    }

    /** 提取标记块正文并去掉首尾空白（调用前已确认存在完整块） */
    private static String blockBody(String content, String begin) {
        int start = content.indexOf(begin) + begin.length();
        int end = content.indexOf(TOOL_END, start);
        return stripLeadingNoise(content.substring(start, end));
    }

    /**
     * 解析询问工具的正文：以 {@code -} 或 {@code *} 开头的行是建议选项（最多
     * {@link #MAX_ASK_OPTIONS} 个），其余行合并为问题文本（去掉 "question:" 之类前缀）。
     *
     * @param payload 标记块正文
     * @return 问题与选项；正文为空时问题为空串、选项为空列表
     */
    public static AskRequest parseAskRequest(String payload) {
        StringBuilder questionBuf = new StringBuilder();
        List<String> options = new ArrayList<>();
        if (payload != null) {
            for (String rawLine : payload.split("\\r?\\n")) {
                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("-") || line.startsWith("*")) {
                    String option = line.substring(1).trim();
                    if (!option.isEmpty() && options.size() < MAX_ASK_OPTIONS) {
                        options.add(option);
                    }
                } else {
                    if (questionBuf.length() > 0) {
                        questionBuf.append(' ');
                    }
                    questionBuf.append(line);
                }
            }
        }
        return new AskRequest(stripAskLabel(questionBuf.toString()), options);
    }

    /** 去掉问题文本可能带的 "question:" / "问题：" 前缀 */
    private static String stripAskLabel(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String prefix : new String[]{"question:", "question：", "问题:", "问题："}) {
            if (lower.startsWith(prefix)) {
                return text.substring(prefix.length()).trim();
            }
        }
        return text;
    }

    /**
     * 执行工具调用：将原始请求文本发送出去并返回响应反馈文本
     *
     * @param requestText 原始 HTTP 请求文本
     * @return 回填给模型的响应反馈（含状态码与原始响应，超长截断）
     * @throws IllegalArgumentException 请求文本无法解析
     */
    public String executeToolCall(String requestText) {
        return describeResponse(executeToolCallWithResponse(requestText));
    }

    /**
     * 执行工具调用并返回完整报文（供 UI 展示 AI 实际发送的数据包）
     *
     * @param requestText 原始 HTTP 请求文本
     * @return 请求与响应报文
     * @throws IllegalArgumentException 请求文本无法解析
     */
    public HttpRequestResponse executeToolCallWithResponse(String requestText) {
        HttpRequest request = requestParser.apply(requestText);
        return http.sendRequest(request);
    }

    /**
     * 将报文转换为回填给模型的响应反馈文本
     *
     * @param requestResponse 发送结果
     * @return 含状态码与响应原文的反馈（超长截断）
     */
    public static String describeResponse(HttpRequestResponse requestResponse) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HTTP Response ===\n");
        if (requestResponse.response() != null) {
            sb.append("Status: ").append(requestResponse.response().statusCode()).append('\n');
            sb.append(truncate(requestResponse.response().toString(),
                    MAX_RESPONSE_FEEDBACK_LENGTH));
        } else {
            sb.append("(no response)");
        }
        return sb.toString();
    }

    /** 去掉标记块内请求正文前的空行/空白噪声 */
    private static String stripLeadingNoise(String text) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        int end = text.length();
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n... (truncated)";
    }
}
