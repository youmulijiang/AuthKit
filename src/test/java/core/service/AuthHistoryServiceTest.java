package core.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.repeater.Repeater;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.InvocationType;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthHistoryService 单元测试
 * 验证从历史选择鉴权后，只读视图（代理历史）应发送到 Repeater，可写编辑器才回写。
 */
class AuthHistoryServiceTest {

    @Test
    @DisplayName("代理历史右键应用鉴权头应发送到 Repeater 而不是回写只读编辑器")
    void applyAuthHeaders_fromProxyHistory_shouldSendToRepeater() {
        Repeater repeater = mock(Repeater.class);
        AuthHistoryService service = newService(repeater);
        HttpRequest original = mockRequestWithAuth("Cookie", "old");
        HttpRequest source = mockRequestWithAuth("Cookie", "new");
        HttpRequest updated = mock(HttpRequest.class);
        when(original.withHeader("Cookie", "new")).thenReturn(updated);

        ContextMenuEvent event = mockProxyHistoryEvent(original);

        service.applyAuthHeaders(event, original, source);

        verify(repeater).sendToRepeater(updated, "AuthKit - Updated Auth");
        verify(event.messageEditorRequestResponse().get(), never()).setRequest(any());
    }

    @Test
    @DisplayName("只读消息查看器应用鉴权头应发送到 Repeater")
    void applyAuthHeaders_fromMessageViewer_shouldSendToRepeater() {
        Repeater repeater = mock(Repeater.class);
        AuthHistoryService service = newService(repeater);
        HttpRequest original = mockRequestWithAuth("Authorization", "Bearer old");
        HttpRequest source = mockRequestWithAuth("Authorization", "Bearer new");
        HttpRequest updated = mock(HttpRequest.class);
        when(original.withHeader("Authorization", "Bearer new")).thenReturn(updated);

        ContextMenuEvent event = mockEditorEvent(original, InvocationType.MESSAGE_VIEWER_REQUEST, List.of());

        service.applyAuthHeaders(event, original, source);

        verify(repeater).sendToRepeater(updated, "AuthKit - Updated Auth");
        verify(event.messageEditorRequestResponse().get(), never()).setRequest(any());
    }

    @Test
    @DisplayName("可写 Request 编辑器应用鉴权头应直接回写编辑器")
    void applyAuthHeaders_fromRequestEditor_shouldWriteEditor() {
        Repeater repeater = mock(Repeater.class);
        AuthHistoryService service = newService(repeater);
        HttpRequest original = mockRequestWithAuth("Cookie", "old");
        HttpRequest source = mockRequestWithAuth("Cookie", "new");
        HttpRequest updated = mock(HttpRequest.class);
        when(original.withHeader("Cookie", "new")).thenReturn(updated);

        ContextMenuEvent event = mockEditorEvent(original, InvocationType.MESSAGE_EDITOR_REQUEST, List.of());

        service.applyAuthHeaders(event, original, source);

        verify(event.messageEditorRequestResponse().get()).setRequest(updated);
        verify(repeater, never()).sendToRepeater(any(), anyString());
    }

    @Test
    @DisplayName("更新为最新鉴权在代理历史中应发送到 Repeater")
    void updateToLatestAuth_fromProxyHistory_shouldSendToRepeater() {
        Repeater repeater = mock(Repeater.class);
        Proxy proxy = mock(Proxy.class);
        AuthHistoryService service = newService(repeater, proxy);

        HttpRequest original = mockRequestWithAuth("Cookie", "old");
        HttpService httpService = mockService("example.com");
        when(original.httpService()).thenReturn(httpService);
        when(original.headerValue("Cookie")).thenReturn("old");

        HttpRequest latestRequest = mockRequestWithAuth("Cookie", "new");
        HttpRequest updated = mock(HttpRequest.class);
        when(original.withHeader("Cookie", "new")).thenReturn(updated);

        ProxyHttpRequestResponse historyItem = mock(ProxyHttpRequestResponse.class);
        when(historyItem.host()).thenReturn("example.com");
        when(historyItem.request()).thenReturn(latestRequest);
        when(historyItem.time()).thenReturn(ZonedDateTime.now());
        when(proxy.history()).thenReturn(List.of(historyItem));

        HttpRequestResponse selected = mock(HttpRequestResponse.class);
        when(selected.request()).thenReturn(original);
        ContextMenuEvent event = mockProxyHistoryEvent(original);

        service.updateToLatestAuth(event, List.of(selected));

        verify(repeater).sendToRepeater(updated, "AuthKit - Updated Auth");
        verify(event.messageEditorRequestResponse().get(), never()).setRequest(any());
    }

    @Test
    @DisplayName("删除鉴权字段在代理历史中应发送到 Repeater")
    void deleteAuthFields_fromProxyHistory_shouldSendToRepeater() {
        Repeater repeater = mock(Repeater.class);
        AuthHistoryService service = newService(repeater);
        HttpRequest original = mock(HttpRequest.class);
        HttpHeader cookieHeader = mock(HttpHeader.class);
        ParsedHttpParameter cookieParam = mock(ParsedHttpParameter.class);
        HttpRequest afterHeaders = mock(HttpRequest.class);
        HttpRequest afterCookieParams = mock(HttpRequest.class);
        when(cookieHeader.name()).thenReturn("Cookie");
        when(original.headers()).thenReturn(List.of(cookieHeader));
        when(original.parameters(HttpParameterType.COOKIE)).thenReturn(List.of(cookieParam));
        when(original.withRemovedHeaders(List.of(cookieHeader))).thenReturn(afterHeaders);
        when(afterHeaders.withRemovedParameters(List.of(cookieParam))).thenReturn(afterCookieParams);

        HttpRequestResponse selected = mock(HttpRequestResponse.class);
        when(selected.request()).thenReturn(original);
        ContextMenuEvent event = mockProxyHistoryEvent(original);

        service.deleteAuthFields(event, List.of(selected), List.of());

        verify(repeater).sendToRepeater(afterCookieParams, "AuthKit - Removed Auth");
        verify(event.messageEditorRequestResponse().get(), never()).setRequest(any());
    }

    @Test
    @DisplayName("可写编辑器且无表格选中项时应允许回写")
    void canWriteToRequestEditor_writableEditorWithoutSelection_shouldReturnTrue() {
        ContextMenuEvent event = mockEditorEvent(mock(HttpRequest.class),
                InvocationType.MESSAGE_EDITOR_REQUEST, List.of());

        assertTrue(AuthHistoryService.canWriteToRequestEditor(event));
    }

    @Test
    @DisplayName("代理历史选中项即使带有消息查看器也不能回写")
    void canWriteToRequestEditor_proxyHistory_shouldReturnFalse() {
        assertFalse(AuthHistoryService.canWriteToRequestEditor(
                mockProxyHistoryEvent(mock(HttpRequest.class))));
    }

    private AuthHistoryService newService(Repeater repeater) {
        return newService(repeater, mock(Proxy.class));
    }

    private AuthHistoryService newService(Repeater repeater, Proxy proxy) {
        MontoyaApi api = mock(MontoyaApi.class);
        when(api.repeater()).thenReturn(repeater);
        when(api.proxy()).thenReturn(proxy);
        return new AuthHistoryService(api);
    }

    private ContextMenuEvent mockProxyHistoryEvent(HttpRequest request) {
        HttpRequestResponse selected = mock(HttpRequestResponse.class);
        when(selected.request()).thenReturn(request);
        return mockEditorEvent(request, InvocationType.MESSAGE_VIEWER_REQUEST, List.of(selected));
    }

    private ContextMenuEvent mockEditorEvent(HttpRequest request, InvocationType type,
                                             List<HttpRequestResponse> selectedItems) {
        HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
        when(reqResp.request()).thenReturn(request);
        MessageEditorHttpRequestResponse editor = mock(MessageEditorHttpRequestResponse.class);
        when(editor.requestResponse()).thenReturn(reqResp);

        ContextMenuEvent event = mock(ContextMenuEvent.class);
        when(event.selectedRequestResponses()).thenReturn(selectedItems);
        when(event.messageEditorRequestResponse()).thenReturn(Optional.of(editor));
        when(event.isFrom(eq(InvocationType.MESSAGE_EDITOR_REQUEST)))
                .thenReturn(type == InvocationType.MESSAGE_EDITOR_REQUEST);
        when(event.invocationType()).thenReturn(type);
        return event;
    }

    private HttpRequest mockRequestWithAuth(String name, String value) {
        HttpRequest request = mock(HttpRequest.class);
        HttpHeader header = mock(HttpHeader.class);
        when(header.name()).thenReturn(name);
        when(header.value()).thenReturn(value);
        when(request.headers()).thenReturn(List.of(header));
        when(request.parameters(any())).thenReturn(List.of());
        return request;
    }

    private HttpService mockService(String host) {
        HttpService service = mock(HttpService.class);
        when(service.host()).thenReturn(host);
        return service;
    }
}
