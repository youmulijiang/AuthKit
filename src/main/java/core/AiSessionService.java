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
     * 从 JSON 文件恢复对话历史（不含 system 提示词，由调用方注入）
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
        int cursor = 0;
        while (true) {
            int roleIdx = array.indexOf("\"role\"", cursor);
            if (roleIdx < 0) {
                break;
            }
            String role = AiChatService.extractStringField(array.substring(roleIdx), "role");
            if (role == null || role.isBlank()) {
                throw new IOException("Invalid session file: message without role");
            }
            String content = AiChatService.extractStringField(array.substring(roleIdx), "content");
            messages.add(new AiChatService.ChatMessage(role, content != null ? content : ""));
            int contentIdx = array.indexOf("\"content\"", roleIdx);
            cursor = (contentIdx >= 0 ? contentIdx : roleIdx) + 1;
        }
        return messages;
    }
}
