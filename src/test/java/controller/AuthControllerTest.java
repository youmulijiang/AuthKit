package controller;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import core.DiffService;
import core.RequestReplayService;
import core.processor.ProcessorChain;
import model.AuthUserModel;
import model.CompareSampleModel;
import model.ConfigModel;
import model.MessageDataModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AuthController 单元测试
 * 测试核心协调逻辑（不依赖 Swing UI）
 */
class AuthControllerTest {

    private ConfigModel configModel;
    private RequestReplayService replayService;
    private DiffService diffService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        configModel = new ConfigModel();
        configModel.setEnabled(true);
        configModel.setRawAuthHeaders("Cookie\nAuthorization");
        replayService = mock(RequestReplayService.class);
        diffService = mock(DiffService.class);
        controller = new AuthController(configModel, replayService, diffService);
    }

    @Test
    @DisplayName("buildConfigModelFromUI 应从 ConfigurationPanel 提取配置")
    void configModel_shouldBeAccessible() {
        assertSame(configModel, controller.getConfigModel());
    }

    @Test
    @DisplayName("processRequest 应记录原始请求并重放未授权请求")
    void processRequest_shouldRecordOriginalAndReplayUnauthorized() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);

        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/api");

        // mock replayUnauthorized
        HttpRequestResponse unauthReqResp = mock(HttpRequestResponse.class);
        when(replayService.replayUnauthorized(eq(request), anyList())).thenReturn(unauthReqResp);

        MessageDataModel originalData = new MessageDataModel("req", "resp", 200, 100, 12345);
        MessageDataModel unauthData = new MessageDataModel("req2", "resp2", 403, 50, 67890);
        when(replayService.buildMessageData(unauthReqResp)).thenReturn(unauthData);

        // 执行
        CompareSampleModel sample = controller.processRequest(request, response, originalData, List.of());

        assertNotNull(sample);
        assertEquals("GET", sample.getMethod());
        assertEquals("http://example.com/api", sample.getUrl());
        assertSame(originalData, sample.getMessageData("Original"));
        assertSame(unauthData, sample.getMessageData("Unauthorized"));
    }

    @Test
    @DisplayName("processRequest 应为每个启用的用户重放请求")
    void processRequest_shouldReplayForEnabledUsers() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.method()).thenReturn("POST");
        when(request.url()).thenReturn("http://example.com/submit");

        HttpRequestResponse unauthReqResp = mock(HttpRequestResponse.class);
        when(replayService.replayUnauthorized(eq(request), anyList())).thenReturn(unauthReqResp);
        MessageDataModel unauthData = new MessageDataModel("r", "r", 200, 10, 1001);
        when(replayService.buildMessageData(unauthReqResp)).thenReturn(unauthData);

        // 两个用户
        AuthUserModel user1 = new AuthUserModel("User1");
        user1.setEnabled(true);
        AuthUserModel user2 = new AuthUserModel("User2");
        user2.setEnabled(false); // 禁用

        HttpRequestResponse user1ReqResp = mock(HttpRequestResponse.class);
        when(replayService.replay(request, user1)).thenReturn(user1ReqResp);
        MessageDataModel user1Data = new MessageDataModel("r1", "r1", 200, 20, 1002);
        when(replayService.buildMessageData(user1ReqResp)).thenReturn(user1Data);

        MessageDataModel originalData = new MessageDataModel("req", "resp", 200, 100, 11111);

        CompareSampleModel sample = controller.processRequest(
                request, response, originalData, List.of(user1, user2));

        // User1 应有数据
        assertNotNull(sample.getMessageData("User1"));
        assertSame(user1Data, sample.getMessageData("User1"));

        // User2 禁用，不应有数据
        assertNull(sample.getMessageData("User2"));

        // 验证只对 User1 调用了 replay
        verify(replayService).replay(request, user1);
        verify(replayService, never()).replay(request, user2);
    }

    @Test
    @DisplayName("diff 应调用 DiffService 比较两段文本")
    void diff_shouldCallDiffService() {
        when(diffService.diff("text1", "text2")).thenReturn("--- a\n+++ b\n@@ -1 +1 @@\n-text1\n+text2");

        String result = controller.diff("text1", "text2");

        assertNotNull(result);
        verify(diffService).diff("text1", "text2");
    }

    @Test
    @DisplayName("getSamples 应返回所有记录")
    void getSamples_shouldReturnAllRecords() {
        assertTrue(controller.getSamples().isEmpty());

        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/test");

        HttpRequestResponse unauthReqResp = mock(HttpRequestResponse.class);
        when(replayService.replayUnauthorized(eq(request), anyList())).thenReturn(unauthReqResp);
        when(replayService.buildMessageData(unauthReqResp))
                .thenReturn(new MessageDataModel("r", "r", 200, 10, 2001));

        MessageDataModel originalData = new MessageDataModel("req", "resp", 200, 100, 2002);
        controller.processRequest(request, response, originalData, List.of());

        assertEquals(1, controller.getSamples().size());
    }

    @Test
    @DisplayName("isNewRequest 应对相同 method+url 去重")
    void isNewRequest_shouldDeduplicateSameMethodUrl() {
        assertTrue(controller.isNewRequest("GET", "http://example.com/api"));
        assertFalse(controller.isNewRequest("GET", "http://example.com/api"));
        assertTrue(controller.isNewRequest("POST", "http://example.com/api"));
        assertTrue(controller.isNewRequest("GET", "http://example.com/other"));
    }

    @Test
    @DisplayName("clearAll 应同时清空去重集合")
    void clearAll_shouldClearDeduplicationSet() {
        controller.isNewRequest("GET", "http://example.com/api");
        assertFalse(controller.isNewRequest("GET", "http://example.com/api"));
        controller.clearAll();
        assertTrue(controller.isNewRequest("GET", "http://example.com/api"));
    }


    @Test
    @DisplayName("clearAll 应清空所有记录")
    void clearAll_shouldClearAllRecords() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/test");

        HttpRequestResponse unauthReqResp = mock(HttpRequestResponse.class);
        when(replayService.replayUnauthorized(eq(request), anyList())).thenReturn(unauthReqResp);
        when(replayService.buildMessageData(unauthReqResp))
                .thenReturn(new MessageDataModel("r", "r", 200, 10, 3001));

        MessageDataModel originalData = new MessageDataModel("req", "resp", 200, 100, 3002);
        controller.processRequest(request, response, originalData, List.of());

        assertFalse(controller.getSamples().isEmpty());
        controller.clearAll();
        assertTrue(controller.getSamples().isEmpty());
    }
}

