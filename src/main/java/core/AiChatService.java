package core;

import model.AiConfigModel;
import utils.LogUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话核心服务
 * <p>
 * 将对话历史与提示词发送到配置的 LLM 接口（OpenAI 兼容 / Anthropic / Ollama），
 * 返回模型回复文本。纯 Java HttpClient 实现，JSON 手工拼装，
 * 响应中的回复内容通过转义感知的字段提取器解析。
 */
public class AiChatService {

    /** 单条对话消息 */
    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }

    /** 模型回复：reasoning 为思考过程（可能为 null），content 为最终回答 */
    public record Reply(String reasoning, String content) {
    }

    private final AiConfigModel config;
    private final HttpClient httpClient;

    public AiChatService(AiConfigModel config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 发送对话请求并返回模型回复（含思考过程）
     *
     * @param history 对话历史（不含系统提示词，按时间顺序）
     * @return 模型回复（reasoning 可能为 null）
     * @throws Exception 网络、鉴权或解析失败
     */
    public Reply chatDetailed(List<ChatMessage> history) throws Exception {
        String url = buildUrl();
        String body = buildRequestBody(history);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            if (AiConfigModel.FORMAT_ANTHROPIC.equals(config.getRequestFormat())) {
                requestBuilder.header("x-api-key", apiKey.trim());
                requestBuilder.header("anthropic-version", "2023-06-01");
            } else {
                requestBuilder.header("Authorization", "Bearer " + apiKey.trim());
            }
        }

        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode()
                    + ": " + truncate(response.body(), 500));
        }
        String reasoning = extractReasoning(response.body());
        String content = extractReplyContent(response.body());
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("AI 响应中未找到回复内容: "
                    + truncate(response.body(), 500));
        }
        return new Reply(reasoning, content);
    }

    /**
     * 发送对话请求并仅返回模型回答文本
     *
     * @param history 对话历史
     * @return 模型回复文本
     * @throws Exception 网络、鉴权或解析失败
     */
    public String chat(List<ChatMessage> history) throws Exception {
        return chatDetailed(history).content();
    }

    /** 根据请求格式拼接接口地址 */
    private String buildUrl() {
        String base = config.getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("Base URL 未配置");
        }
        base = base.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return switch (config.getRequestFormat()) {
            case AiConfigModel.FORMAT_ANTHROPIC -> base + "/v1/messages";
            case AiConfigModel.FORMAT_OLLAMA -> base + "/api/chat";
            default -> base + "/chat/completions";
        };
    }

    /** 根据请求格式构建请求体 JSON（手工拼装，字符串内容做转义） */
    private String buildRequestBody(List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>(history);
        if (AiConfigModel.FORMAT_ANTHROPIC.equals(config.getRequestFormat())) {
            // Anthropic: system 单独传递，role 仅支持 user/assistant
            String system = null;
            List<ChatMessage> dialog = new ArrayList<>();
            for (ChatMessage msg : messages) {
                if ("system".equals(msg.role())) {
                    system = msg.content();
                } else {
                    dialog.add(msg);
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\"model\":\"").append(jsonEscape(config.getModel())).append('"');
            sb.append(",\"max_tokens\":4096");
            if (system != null && !system.isBlank()) {
                sb.append(",\"system\":\"").append(jsonEscape(system)).append('"');
            }
            sb.append(",\"messages\":[");
            appendMessages(sb, dialog);
            sb.append("]}");
            return sb.toString();
        }

        // OpenAI / Ollama: messages 数组
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(jsonEscape(config.getModel())).append('"');
        sb.append(",\"stream\":false");
        if (AiConfigModel.FORMAT_OLLAMA.equals(config.getRequestFormat())) {
            sb.append(",\"messages\":[");
            appendMessages(sb, messages);
            sb.append("]}");
        } else {
            sb.append(",\"messages\":[");
            appendMessages(sb, messages);
            sb.append("]}");
        }
        return sb.toString();
    }

    /** 追加消息数组内容 */
    private void appendMessages(StringBuilder sb, List<ChatMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"role\":\"").append(jsonEscape(msg.role()))
                    .append("\",\"content\":\"").append(jsonEscape(msg.content())).append("\"}");
        }
    }

    /**
     * 从响应 JSON 中提取回复内容：
     * OpenAI 取 choices[0].message.content，Anthropic 取 content[0].text，Ollama 取 message.content。
     * 通用做法：在 JSON 中按深度优先顺序查找目标字段名的字符串值。
     */
    private String extractReplyContent(String json) {
        String value = null;
        switch (config.getRequestFormat()) {
            case AiConfigModel.FORMAT_ANTHROPIC -> {
                // content 数组中第一段 type=text 的 text
                value = extractStringField(json, "text");
            }
            case AiConfigModel.FORMAT_OLLAMA -> {
                value = extractOllamaMessage(json);
            }
            default -> {
                value = extractOpenAiMessage(json);
            }
        }
        if (value == null) {
            // 兜底：通用查找常见字段
            value = firstNonBlank(
                    extractStringField(json, "content"),
                    extractStringField(json, "text"),
                    extractStringField(json, "response"));
        }
        return value;
    }

    /** OpenAI: 取 choices[0].message.content（截取 message 对象后再找 content） */
    private String extractOpenAiMessage(String json) {
        int choicesIdx = json.indexOf("\"choices\"");
        if (choicesIdx < 0) {
            return null;
        }
        int messageIdx = json.indexOf("\"message\"", choicesIdx);
        if (messageIdx < 0) {
            return extractStringField(json.substring(choicesIdx), "content");
        }
        return extractStringField(json.substring(messageIdx), "content");
    }

    /** Ollama: 取 message.content */
    private String extractOllamaMessage(String json) {
        int messageIdx = json.indexOf("\"message\"");
        if (messageIdx < 0) {
            return null;
        }
        return extractStringField(json.substring(messageIdx), "content");
    }

    /**
     * 在 JSON 文本中查找指定字段名的第一个字符串值（转义感知，跳过字符串字面量内的匹配）。
     */
    static String extractStringField(String json, String fieldName) {
        String needle = "\"" + fieldName + "\"";
        int searchFrom = 0;
        while (true) {
            int keyIdx = indexOfJsonKey(json, needle, searchFrom);
            if (keyIdx < 0) {
                return null;
            }
            int colonIdx = json.indexOf(':', keyIdx + needle.length());
            if (colonIdx < 0) {
                return null;
            }
            int valueStart = colonIdx + 1;
            while (valueStart < json.length()
                    && Character.isWhitespace(json.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
                // 值不是字符串，继续向后找下一个同名字段
                searchFrom = colonIdx + 1;
                continue;
            }
            return parseJsonString(json, valueStart);
        }
    }

    /** 查找 JSON 中作为 key 出现的字符串位置（跳过字符串字面量内部） */
    private static int indexOfJsonKey(String json, String needle, int from) {
        int idx = from;
        while (true) {
            idx = json.indexOf(needle, idx);
            if (idx < 0) {
                return -1;
            }
            // 检查 needle 前一个非空白字符是否为 { 或 ,
            int prev = idx - 1;
            while (prev >= 0 && Character.isWhitespace(json.charAt(prev))) {
                prev--;
            }
            char prevChar = prev >= 0 ? json.charAt(prev) : '{';
            if (prevChar == '{' || prevChar == ',') {
                return idx;
            }
            idx += needle.length();
        }
    }

    /** 从 json 的 valueStart（指向开引号）解析完整字符串值，处理转义 */
    private static String parseJsonString(String json, int start) {
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(
                                        json.substring(i + 2, i + 6), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                // 非法 unicode 转义，原样保留
                            }
                        }
                    }
                    default -> sb.append(next);
                }
                i += 2;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    /** JSON 字符串内容转义 */
    static String jsonEscape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** 快速连通性测试：发送一句问候，返回模型回复或抛出异常 */
    public String testConnection() throws Exception {
        List<ChatMessage> history = List.of(
                ChatMessage.system("You are a connection test."),
                ChatMessage.user("ping"));
        return chat(history);
    }

    /**
     * 从响应 JSON 中提取思考过程（reasoning）内容。
     * 兼容 DeepSeek 风格 reasoning_content、Anthropic thinking、Ollama thinking 字段。
     */
    private String extractReasoning(String json) {
        return firstNonBlank(
                extractStringField(json, "reasoning_content"),
                extractStringField(json, "thinking"));
    }

    /** 记录错误日志的便捷方法 */
    public void logError(String message, Throwable ex) {
        LogUtils.INSTANCE.error(message, ex);
    }
}
