package core;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AiToolService 单元测试
 */
class AiToolServiceTest {

    private Http http;
    private AiToolService service;
    /** 捕获传给解析器的原始请求文本 */
    private String parsedRequestText;

    @BeforeEach
    void setUp() {
        http = mock(Http.class);
        parsedRequestText = null;
        // 测试解析器：绕过 Montoya 静态工厂，记录输入并返回 mock
        service = new AiToolService(http, text -> {
            parsedRequestText = text;
            return mock(HttpRequest.class);
        });
    }

    @Test
    @DisplayName("parseToolCall 识别发包标记块并返回请求文本")
    void parseToolCall_sendRequestBlock() {
        String reply = "让我验证一下。\n<<<SEND_HTTP_REQUEST>>>\nGET /api/users/1 HTTP/1.1\n"
                + "Host: example.com\n\n<<<END>>>";
        AiToolService.ToolCall call = AiToolService.parseToolCall(reply);
        assertEquals(AiToolService.ToolType.SEND_REQUEST, call.type());
        assertEquals("GET /api/users/1 HTTP/1.1\nHost: example.com", call.payload());
    }

    @Test
    @DisplayName("parseToolCall 识别数据包标记块")
    void parseToolCall_packetsBlock() {
        String reply = "我需要看数据包。\n<<<GET_PACKETS>>>\n<<<END>>>";
        AiToolService.ToolCall call = AiToolService.parseToolCall(reply);
        assertEquals(AiToolService.ToolType.GET_PACKETS, call.type());
        assertEquals("", call.payload());
    }

    @Test
    @DisplayName("parseToolCall 同时存在两种标记块时取最先出现的")
    void parseToolCall_bothBlocks_earliestWins() {
        String packetsFirst = "<<<GET_PACKETS>>><<<END>>>\n<<<SEND_HTTP_REQUEST>>>\nGET / HTTP/1.1\n<<<END>>>";
        assertEquals(AiToolService.ToolType.GET_PACKETS,
                AiToolService.parseToolCall(packetsFirst).type());

        String sendFirst = "<<<SEND_HTTP_REQUEST>>>\nGET / HTTP/1.1\n<<<END>>>\n<<<GET_PACKETS>>><<<END>>>";
        assertEquals(AiToolService.ToolType.SEND_REQUEST,
                AiToolService.parseToolCall(sendFirst).type());
    }

    @Test
    @DisplayName("parseToolCall 识别数据包来源标记块并返回关键词")
    void parseToolCall_packetSourceBlocks() {
        AiToolService.ToolCall proxy = AiToolService.parseToolCall(
                "我需要更多数据包。\n<<<GET_PROXY_HISTORY>>>api/users\n<<<END>>>");
        assertEquals(AiToolService.ToolType.PROXY_HISTORY, proxy.type());
        assertEquals("api/users", proxy.payload());

        AiToolService.ToolCall siteMap = AiToolService.parseToolCall(
                "<<<GET_SITEMAP>>>\n<<<END>>>");
        assertEquals(AiToolService.ToolType.SITEMAP, siteMap.type());
        assertEquals("", siteMap.payload());
    }

    @Test
    @DisplayName("parseToolCall 四类标记共存时取最先出现的")
    void parseToolCall_allBlocks_earliestWins() {
        String reply = "<<<GET_SITEMAP>>><<<END>>>\n"
                + "<<<GET_PROXY_HISTORY>>><<<END>>>\n"
                + "<<<GET_PACKETS>>><<<END>>>\n"
                + "<<<SEND_HTTP_REQUEST>>>\nGET / HTTP/1.1\n<<<END>>>";
        assertEquals(AiToolService.ToolType.SITEMAP, AiToolService.parseToolCall(reply).type());
    }

    @Test
    @DisplayName("parseToolCall 对缺失结束标记、空文本与普通回复返回 NONE")
    void parseToolCall_incompleteOrPlain_none() {
        assertEquals(AiToolService.ToolType.NONE,
                AiToolService.parseToolCall("<<<SEND_HTTP_REQUEST>>>GET / HTTP/1.1").type());
        assertEquals(AiToolService.ToolType.NONE,
                AiToolService.parseToolCall("<<<GET_PACKETS>>>未闭合").type());
        assertEquals(AiToolService.ToolType.NONE, AiToolService.parseToolCall(null).type());
        assertEquals(AiToolService.ToolType.NONE, AiToolService.parseToolCall("").type());
        assertEquals(AiToolService.ToolType.NONE,
                AiToolService.parseToolCall("普通回复，没有工具调用。").type());
    }

    @Test
    @DisplayName("extractRequestText 提取标记块内请求并去掉首尾空白")
    void extractRequestText_extractsTrimmedRequest() {
        String reply = "说明文字\n<<<SEND_HTTP_REQUEST>>>\n\nGET /api/users/2 HTTP/1.1\n"
                + "Host: example.com\n\n\n<<<END>>>\n后续文字";
        String request = service.extractRequestText(reply);
        assertEquals("GET /api/users/2 HTTP/1.1\nHost: example.com", request);
    }

    @Test
    @DisplayName("extractRequestText 无完整块时返回 null")
    void extractRequestText_noBlock_null() {
        assertNull(service.extractRequestText("没有标记的文本"));
        assertNull(service.extractRequestText(null));
        assertNull(service.extractRequestText("<<<SEND_HTTP_REQUEST>>>不完整"));
    }

    @Test
    @DisplayName("executeToolCall 发送请求并返回状态码与响应文本")
    void executeToolCall_sendsRequestAndReturnsFeedback() {
        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        burp.api.montoya.http.message.responses.HttpResponse mockResp =
                mock(burp.api.montoya.http.message.responses.HttpResponse.class);
        when(mockResp.statusCode()).thenReturn((short) 200);
        when(mockResp.toString()).thenReturn("HTTP/1.1 200 OK\n{\"id\":2}");
        when(mockResponse.response()).thenReturn(mockResp);
        when(http.sendRequest(any(HttpRequest.class))).thenReturn(mockResponse);

        String feedback = service.executeToolCall(
                "GET /api/users/2 HTTP/1.1\nHost: example.com\n");

        assertTrue(feedback.contains("Status: 200"));
        assertTrue(feedback.contains("HTTP/1.1 200 OK"));
        assertEquals("GET /api/users/2 HTTP/1.1\nHost: example.com\n", parsedRequestText);
        verify(http).sendRequest(any(HttpRequest.class));
    }

    @Test
    @DisplayName("executeToolCall 无响应时返回占位文本")
    void executeToolCall_noResponse_placeholder() {
        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        when(mockResponse.response()).thenReturn(null);
        when(http.sendRequest(any(HttpRequest.class))).thenReturn(mockResponse);

        String feedback = service.executeToolCall("GET / HTTP/1.1\nHost: x\n");
        assertTrue(feedback.contains("(no response)"));
    }

    @Test
    @DisplayName("executeToolCall 长响应被截断")
    void executeToolCall_longResponse_truncated() {
        HttpRequestResponse mockResponse = mock(HttpRequestResponse.class);
        burp.api.montoya.http.message.responses.HttpResponse mockResp =
                mock(burp.api.montoya.http.message.responses.HttpResponse.class);
        when(mockResp.statusCode()).thenReturn((short) 200);
        when(mockResp.toString()).thenReturn("x".repeat(20000));
        when(mockResponse.response()).thenReturn(mockResp);
        when(http.sendRequest(any(HttpRequest.class))).thenReturn(mockResponse);

        String feedback = service.executeToolCall("GET / HTTP/1.1\nHost: x\n");
        assertTrue(feedback.contains("(truncated)"));
        assertTrue(feedback.length() < 10000);
    }
}
