package core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiSessionService 单元测试：会话保存/加载往返与格式校验
 */
class AiSessionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("保存后加载应完整还原 user/assistant 消息且不含 system")
    void saveAndLoad_shouldRoundTripWithoutSystem() throws IOException {
        AiSessionService service = new AiSessionService();
        List<AiChatService.ChatMessage> history = List.of(
                AiChatService.ChatMessage.system("system prompt"),
                AiChatService.ChatMessage.user("分析这个包\n第二行"),
                AiChatService.ChatMessage.assistant("回复内容 \"引号\" 和\\反斜杠"),
                AiChatService.ChatMessage.user("继续"));
        Path file = tempDir.resolve("session.json");

        service.save(history, file);
        List<AiChatService.ChatMessage> loaded = service.load(file);

        assertEquals(3, loaded.size());
        assertEquals("user", loaded.get(0).role());
        assertEquals("分析这个包\n第二行", loaded.get(0).content());
        assertEquals("assistant", loaded.get(1).role());
        assertEquals("回复内容 \"引号\" 和\\反斜杠", loaded.get(1).content());
        assertEquals("user", loaded.get(2).role());
        assertEquals("继续", loaded.get(2).content());
    }

    @Test
    @DisplayName("加载含特殊字符的会话应正确处理 unicode 与转义")
    void load_shouldHandleEscapes() throws IOException {
        Path file = tempDir.resolve("escaped.json");
        Files.writeString(file, "{\"version\":\"1\",\"messages\":["
                + "{\"role\":\"user\",\"content\":\"中文\\u0041\\t\\\"引号\\\"\"}]}");

        List<AiChatService.ChatMessage> loaded = new AiSessionService().load(file);

        assertEquals(1, loaded.size());
        assertEquals("中文A\t\"引号\"", loaded.get(0).content());
    }

    @Test
    @DisplayName("加载非法会话文件应抛出 IOException")
    void load_invalidFile_shouldThrow() throws IOException {
        Path file = tempDir.resolve("bad.json");
        Files.writeString(file, "not a session file");

        assertThrows(IOException.class, () -> new AiSessionService().load(file));
    }

    @Test
    @DisplayName("保存空会话应生成空 messages 数组且加载返回空列表")
    void saveEmpty_shouldRoundTrip() throws IOException {
        Path file = tempDir.resolve("empty.json");

        new AiSessionService().save(List.of(), file);
        List<AiChatService.ChatMessage> loaded = new AiSessionService().load(file);

        assertTrue(loaded.isEmpty());
    }
}
