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
    @DisplayName("getSamples 应返回快照，后续 clearAll 不应影响已获取结果")
    void getSamples_shouldReturnSnapshot() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/snapshot");

        HttpRequestResponse unauthReqResp = mock(HttpRequestResponse.class);
        when(replayService.replayUnauthorized(eq(request), anyList())).thenReturn(unauthReqResp);
        when(replayService.buildMessageData(unauthReqResp))
                .thenReturn(new MessageDataModel("r", "r", 200, 10, 4001));

        MessageDataModel originalData = new MessageDataModel("req", "resp", 200, 100, 4002);
        controller.processRequest(request, response, originalData, List.of());

        List<CompareSampleModel> snapshot = controller.getSamples();
        controller.clearAll();

        assertEquals(1, snapshot.size());
        assertTrue(controller.getSamples().isEmpty());
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


    @Test
    @DisplayName("isNewRequest 三参重载应将相同 method+url+body 视为重复，不同 body 视为新请求")
    void isNewRequest_withBodyHash_shouldDeduplicateByBody() {
        String bodyHashA = Integer.toHexString("body-a".hashCode());
        String bodyHashB = Integer.toHexString("body-b".hashCode());

        assertTrue(controller.isNewRequest("POST", "http://example.com/api", bodyHashA));
        assertFalse(controller.isNewRequest("POST", "http://example.com/api", bodyHashA));
        // 同 URL 不同请求体：不应被去重拦截
        assertTrue(controller.isNewRequest("POST", "http://example.com/api", bodyHashB));
        // 无请求体（空串）与两参重载等价
        assertTrue(controller.isNewRequest("POST", "http://example.com/api", ""));
        assertFalse(controller.isNewRequest("POST", "http://example.com/api"));
    }

    @Test
    @DisplayName("forgetRequest 应撤销去重标记，允许失败请求重新被处理")
    void forgetRequest_shouldAllowReprocessing() {
        String bodyHash = Integer.toHexString("body".hashCode());
        assertTrue(controller.isNewRequest("POST", "http://example.com/api", bodyHash));
        assertFalse(controller.isNewRequest("POST", "http://example.com/api", bodyHash));

        controller.forgetRequest("POST", "http://example.com/api", bodyHash);

        assertTrue(controller.isNewRequest("POST", "http://example.com/api", bodyHash));
    }

    @Test
    @DisplayName("renameAuthUser 应同步所有历史样本的鉴权对象名称")
    void renameAuthUser_shouldRenameAcrossSamples() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/rename");

        HttpRequestResponse unauthReqResp = mock(HttpRequestResponse.class);
        when(replayService.replayUnauthorized(eq(request), anyList())).thenReturn(unauthReqResp);
        when(replayService.buildMessageData(unauthReqResp))
                .thenReturn(new MessageDataModel("r", "r", 200, 10, 5001));

        AuthUserModel user = new AuthUserModel("User1");
        HttpRequestResponse userReqResp = mock(HttpRequestResponse.class);
        when(replayService.replay(request, user)).thenReturn(userReqResp);
        MessageDataModel userData = new MessageDataModel("r1", "r1", 200, 20, 5002);
        when(replayService.buildMessageData(userReqResp)).thenReturn(userData);

        MessageDataModel originalData = new MessageDataModel("req", "resp", 200, 100, 5003);
        CompareSampleModel sample = controller.processRequest(request, response, originalData, List.of(user));

        controller.renameAuthUser("User1", "admin");

        assertNull(sample.getMessageData("User1"));
        assertSame(userData, sample.getMessageData("admin"));
        // 顺序保持：Original / Unauthorized / 重命名后的 admin
        assertEquals("Original", sample.getAuthNamesOrdered().get(0));
        assertEquals("Unauthorized", sample.getAuthNamesOrdered().get(1));
        assertEquals("admin", sample.getAuthNamesOrdered().get(2));
    }

    @Test
    @DisplayName("removeAuthUser 应从所有历史样本中移除该鉴权对象数据")
    void removeAuthUser_shouldRemoveAcrossSamples() {
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(request.method()).thenReturn("GET");
        when(request.url()).thenReturn("http://example.com/remove");

        HttpRequestResponse unauthReqResp = mock(HttpRequestResponse.class);
        when(replayService.replayUnauthorized(eq(request), anyList())).thenReturn(unauthReqResp);
        when(replayService.buildMessageData(unauthReqResp))
                .thenReturn(new MessageDataModel("r", "r", 200, 10, 6001));

        AuthUserModel user = new AuthUserModel("User1");
        HttpRequestResponse userReqResp = mock(HttpRequestResponse.class);
        when(replayService.replay(request, user)).thenReturn(userReqResp);
        when(replayService.buildMessageData(userReqResp))
                .thenReturn(new MessageDataModel("r1", "r1", 200, 20, 6002));

        MessageDataModel originalData = new MessageDataModel("req", "resp", 200, 100, 6003);
        CompareSampleModel sample = controller.processRequest(request, response, originalData, List.of(user));

        controller.removeAuthUser("User1");

        assertNull(sample.getMessageData("User1"));
        assertNotNull(sample.getMessageData("Original"));
        assertNotNull(sample.getMessageData("Unauthorized"));
    }
}
