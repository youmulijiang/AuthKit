package core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话会话持久化服务
 * <p>
 * 将对话历史（user / assistant 消息，不含 system 提示词）序列化为 JSON 文件，
 * 并支持从文件恢复。JSON 采用手工拼装与转义感知解析，与 {@link AiChatService} 风格一致。
 */
public class AiSessionService {

    /** 会话文件格式版本 */
    private static final String FORMAT_VERSION = "1";

    /**
     * 将对话历史保存为 JSON 文件（跳过 system 消息，恢复时由面板重新注入）
     *
     * @param history  对话历史（含 system 提示词）
     * @param path     目标文件路径
     * @throws IOException 文件写入失败
     */
    public void save(List<AiChatService.ChatMessage> history, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"version\":\"").append(FORMAT_VERSION).append("\",\"messages\":[");
        boolean first = true;
        for (AiChatService.ChatMessage msg : history) {
            if (msg == null || "system".equals(msg.role())) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"role\":\"").append(AiChatService.jsonEscape(msg.role()))
                    .append("\",\"content\":\"").append(AiChatService.jsonEscape(msg.content()))
                    .append("\"}");
        }
        sb.append("]}");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 从 JSON 文件恢复对话历史（不含 system 提示词，由调用方注入）。
     * 每条消息的字段搜索以"role"键出现位置为界分段，避免某条消息缺失 content 时
     * 跨边界读到下一条消息的内容；system 角色消息直接跳过（恢复时由面板重新注入）。
     *
     * @param path 会话文件路径
     * @return 按时间顺序的 user / assistant 消息列表
     * @throws IOException 文件读取失败或格式非法
     */
    public List<AiChatService.ChatMessage> load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        if (!json.contains("\"messages\"")) {
            throw new IOException("Invalid session file: missing \"messages\" array");
        }
        List<AiChatService.ChatMessage> messages = new ArrayList<>();
        int arrayStart = json.indexOf('[');
        int arrayEnd = json.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < arrayStart) {
            throw new IOException("Invalid session file: malformed messages array");
        }
        String array = json.substring(arrayStart + 1, arrayEnd);

        // 收集所有 "role" 键位置，相邻两个键之间即一条消息的字段范围
        List<Integer> roleIndices = new ArrayList<>();
        int idx = array.indexOf("\"role\"");
        while (idx >= 0) {
            roleIndices.add(idx);
            idx = array.indexOf("\"role\"", idx + 1);
        }
        if (roleIndices.isEmpty()) {
            return messages;
        }

        for (int i = 0; i < roleIndices.size(); i++) {
            int start = roleIndices.get(i);
            int end = i + 1 < roleIndices.size() ? roleIndices.get(i + 1) : array.length();
            String segment = array.substring(start, end);
            String role = AiChatService.extractStringField(segment, "role");
            if (role == null || role.isBlank()) {
                throw new IOException("Invalid session file: message without role");
            }
            if ("system".equals(role)) {
                continue;
            }
            String content = AiChatService.extractStringField(segment, "content");
            messages.add(new AiChatService.ChatMessage(role, content != null ? content : ""));
        }
        return messages;
    }
}
