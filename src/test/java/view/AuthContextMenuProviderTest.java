package view;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import core.FakeIpService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthContextMenuProviderTest {

    @Test
    @DisplayName("Request Raw Editor 右键菜单应使用当前语言翻译 Send to AuthKit")
    void requestRawEditorContextMenu_shouldTranslateSendToAuthKit() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        try {
            i18n.setLanguage(I18n.Language.CHINESE);
            HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
            MessageEditorHttpRequestResponse editorContext = mock(MessageEditorHttpRequestResponse.class);
            when(editorContext.requestResponse()).thenReturn(reqResp);
            ContextMenuEvent event = mock(ContextMenuEvent.class);
            when(event.selectedRequestResponses()).thenReturn(List.of());
            when(event.messageEditorRequestResponse()).thenReturn(Optional.of(editorContext));

            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null);

            List<Component> items = provider.provideMenuItems(event);

            assertFalse(items.isEmpty());
            assertInstanceOf(JMenuItem.class, items.get(0));
            assertEquals("发送到 AuthKit", ((JMenuItem) items.get(0)).getText());
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("Fake IP 本地 IP 菜单应向 Request Raw Editor 回写伪造 IP 头")
    void fakeIpLocalMenu_shouldUpdateEditorRequest() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
        HttpRequest request = mockFakeIpRequest(FakeIpService.LOCALHOST_IP);
        MessageEditorHttpRequestResponse editorContext = mockEditorContext(request);
        ContextMenuEvent event = mockContextMenuEvent(editorContext);
        AuthContextMenuProvider provider = new AuthContextMenuProvider(
                List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null,
                new FakeIpService(), request1 -> {}, request1 -> {});

        JMenu fakeIpMenu = findMenu(provider.provideMenuItems(event), "Fake IP");
        ((JMenuItem) fakeIpMenu.getMenuComponent(1)).doClick();

        verify(request).withAddedHeader("X-Forwarded-For", FakeIpService.LOCALHOST_IP);
        verify(editorContext).setRequest(request);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("随机 IP 爆破菜单应标记请求后发送到 Intruder")
    void fakeIpBruteforceMenu_shouldSendHeaderRequestToIntruder() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
        HttpRequest request = mock(HttpRequest.class);
        when(request.hasHeader(anyString())).thenReturn(false);
        when(request.withAddedHeader(anyString(), anyString())).thenReturn(request);
        MessageEditorHttpRequestResponse editorContext = mockEditorContext(request);
        ContextMenuEvent event = mockContextMenuEvent(editorContext);
        List<HttpRequest> intruderRequests = new ArrayList<>();
        AuthContextMenuProvider provider = new AuthContextMenuProvider(
                List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null,
                new FakeIpService(), request1 -> {}, intruderRequests::add);

        JMenu fakeIpMenu = findMenu(provider.provideMenuItems(event), "Fake IP");
        ((JMenuItem) fakeIpMenu.getMenuComponent(3)).doClick();

        verify(request).withAddedHeader(FakeIpService.BRUTE_FORCE_MARKER_HEADER, FakeIpService.BRUTE_FORCE_MARKER_VALUE);
        assertEquals(List.of(request), intruderRequests);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("Fake IP 本地 IP 菜单应把普通选中请求加头后发送到 Repeater")
    void fakeIpLocalMenu_shouldSendSelectedRequestToRepeater() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            HttpRequest request = mockFakeIpRequest(FakeIpService.LOCALHOST_IP);
            HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
            when(reqResp.request()).thenReturn(request);
            ContextMenuEvent event = mock(ContextMenuEvent.class);
            when(event.selectedRequestResponses()).thenReturn(List.of(reqResp));
            when(event.messageEditorRequestResponse()).thenReturn(Optional.empty());
            List<HttpRequest> repeaterRequests = new ArrayList<>();
            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null,
                    new FakeIpService(), repeaterRequests::add, request1 -> {});

            JMenu fakeIpMenu = findMenu(provider.provideMenuItems(event), "Fake IP");
            ((JMenuItem) fakeIpMenu.getMenuComponent(1)).doClick();

            verify(request).withAddedHeader("X-Forwarded-For", FakeIpService.LOCALHOST_IP);
            assertEquals(List.of(request), repeaterRequests);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("Fake IP 菜单在同时存在选中项和编辑器上下文时应优先处理选中请求")
    void fakeIpLocalMenu_shouldPreferSelectedRequestResponsesOverEditorContext() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            HttpRequest selectedRequest = mockFakeIpRequest(FakeIpService.LOCALHOST_IP);
            HttpRequest editorRequest = mockFakeIpRequest(FakeIpService.LOCALHOST_IP);
            HttpRequestResponse selectedReqResp = mock(HttpRequestResponse.class);
            when(selectedReqResp.request()).thenReturn(selectedRequest);
            MessageEditorHttpRequestResponse editorContext = mockEditorContext(editorRequest);
            ContextMenuEvent event = mockContextMenuEvent(editorContext);
            when(event.selectedRequestResponses()).thenReturn(List.of(selectedReqResp));
            List<HttpRequest> repeaterRequests = new ArrayList<>();
            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null,
                    new FakeIpService(), repeaterRequests::add, request1 -> {});

            JMenu fakeIpMenu = findMenu(provider.provideMenuItems(event), "Fake IP");
            ((JMenuItem) fakeIpMenu.getMenuComponent(1)).doClick();

            verify(selectedRequest).withAddedHeader("X-Forwarded-For", FakeIpService.LOCALHOST_IP);
            assertEquals(List.of(selectedRequest), repeaterRequests);
            verify(editorContext, org.mockito.Mockito.never()).setRequest(editorRequest);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("Fake IP 菜单应支持中文翻译")
    void fakeIpMenu_shouldTranslateChinese() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        try {
            i18n.setLanguage(I18n.Language.CHINESE);
            HttpRequest request = mockFakeIpRequest(FakeIpService.LOCALHOST_IP);
            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null);

            JMenu fakeIpMenu = findMenu(provider.provideMenuItems(mockContextMenuEvent(mockEditorContext(request))), "伪造 IP");

            assertEquals("伪造指定 IP", ((JMenuItem) fakeIpMenu.getMenuComponent(0)).getText());
            assertEquals("随机 IP 爆破", ((JMenuItem) fakeIpMenu.getMenuComponent(3)).getText());
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("Scan 一级菜单应包含全部扫描项，且全部扫描触发回调")
    @SuppressWarnings("unchecked")
    void scanMenu_shouldContainAllScanItems_andScanAllShouldTriggerHandler() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            HttpRequest request = mock(HttpRequest.class);
            HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
            when(reqResp.request()).thenReturn(request);
            MessageEditorHttpRequestResponse editorContext = mock(MessageEditorHttpRequestResponse.class);
            when(editorContext.requestResponse()).thenReturn(reqResp);
            ContextMenuEvent event = mockContextMenuEvent(editorContext);
            List<HttpRequestResponse> scannedItems = new ArrayList<>();
            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null,
                    new FakeIpService(), request1 -> {}, request1 -> {}, scannedItems::addAll);
            provider.setBypass403ScanHandler(scannedItems::addAll);
            provider.setIdorScanHandler(scannedItems::addAll);
            provider.setJwtScanHandler(scannedItems::addAll);
            provider.setAiScanHandler(scannedItems::addAll);

            JMenu scanMenu = (JMenu) provider.provideMenuItems(event).get(3);
            assertEquals("Scan", scanMenu.getText());

            // 二级菜单依次为：全部扫描 / 分隔符 / 403 Bypass / IDOR / JWT / AI 越权扫描
            assertEquals("Scan All", ((JMenuItem) scanMenu.getMenuComponent(0)).getText());
            assertEquals("403 Bypass Scan", ((JMenuItem) scanMenu.getMenuComponent(2)).getText());
            assertEquals("IDOR Scan", ((JMenuItem) scanMenu.getMenuComponent(3)).getText());
            assertEquals("JWT Scan", ((JMenuItem) scanMenu.getMenuComponent(4)).getText());
            assertEquals("AI Authorization Scan", ((JMenuItem) scanMenu.getMenuComponent(5)).getText());

            ((JMenuItem) scanMenu.getMenuComponent(0)).doClick();
            assertEquals(List.of(reqResp), scannedItems);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("Scan 二级菜单中各扫描项应分别触发对应回调")
    void scanMenu_eachItem_shouldTriggerItsOwnHandler() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            HttpRequest request = mock(HttpRequest.class);
            HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
            when(reqResp.request()).thenReturn(request);
            MessageEditorHttpRequestResponse editorContext = mock(MessageEditorHttpRequestResponse.class);
            when(editorContext.requestResponse()).thenReturn(reqResp);
            ContextMenuEvent event = mockContextMenuEvent(editorContext);
            List<HttpRequestResponse> bypassItems = new ArrayList<>();
            List<HttpRequestResponse> idorItems = new ArrayList<>();
            List<HttpRequestResponse> jwtItems = new ArrayList<>();
            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null,
                    new FakeIpService(), request1 -> {}, request1 -> {}, items -> {});
            provider.setBypass403ScanHandler(bypassItems::addAll);
            provider.setIdorScanHandler(idorItems::addAll);
            provider.setJwtScanHandler(jwtItems::addAll);

            JMenu scanMenu = (JMenu) provider.provideMenuItems(event).get(3);

            ((JMenuItem) scanMenu.getMenuComponent(2)).doClick();
            assertEquals(List.of(reqResp), bypassItems);
            assertTrue(idorItems.isEmpty());

            ((JMenuItem) scanMenu.getMenuComponent(3)).doClick();
            assertEquals(List.of(reqResp), idorItems);

            ((JMenuItem) scanMenu.getMenuComponent(4)).doClick();
            assertEquals(List.of(reqResp), jwtItems);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("Extract Auth 菜单应只提取配置面板认证头（配置非空时内置关键字不参与）")
    void extractMenu_shouldExtractOnlyConfiguredAuthHeaders() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
            HttpRequest request = mock(HttpRequest.class);
            burp.api.montoya.http.message.HttpHeader cookie = mock(burp.api.montoya.http.message.HttpHeader.class);
            burp.api.montoya.http.message.HttpHeader custom = mock(burp.api.montoya.http.message.HttpHeader.class);
            when(reqResp.request()).thenReturn(request);
            when(cookie.name()).thenReturn("Cookie");
            when(cookie.value()).thenReturn("sid=abc");
            when(custom.name()).thenReturn("X-AuthKit-Session");
            when(custom.value()).thenReturn("custom");
            when(request.headers()).thenReturn(List.of(cookie, custom));
            MessageEditorHttpRequestResponse editorContext = mockEditorContext(request);
            when(editorContext.requestResponse()).thenReturn(reqResp);

            List<String> extracted = new ArrayList<>();
            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    () -> List.of("User1"), () -> true, () -> {}, items -> {},
                    (auth, user) -> extracted.add(user + "=" + auth), auth -> null,
                    () -> List.of("AuthKit"), new FakeIpService(), request1 -> {}, request1 -> {});

            JMenu extractMenu = findMenu(provider.provideMenuItems(mockContextMenuEvent(editorContext)), "Extract Auth to User");
            ((JMenuItem) extractMenu.getMenuComponent(0)).doClick();

            assertEquals(List.of("User1=X-AuthKit-Session: custom"), extracted);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    private HttpRequest mockFakeIpRequest(String expectedIp) {
        HttpRequest request = mock(HttpRequest.class);
        when(request.hasHeader(anyString())).thenReturn(false);
        when(request.withAddedHeader(anyString(), eq(expectedIp))).thenReturn(request);
        return request;
    }

    private MessageEditorHttpRequestResponse mockEditorContext(HttpRequest request) {
        HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
        when(reqResp.request()).thenReturn(request);
        MessageEditorHttpRequestResponse editorContext = mock(MessageEditorHttpRequestResponse.class);
        when(editorContext.requestResponse()).thenReturn(reqResp);
        return editorContext;
    }

    private ContextMenuEvent mockContextMenuEvent(MessageEditorHttpRequestResponse editorContext) {
        ContextMenuEvent event = mock(ContextMenuEvent.class);
        when(event.selectedRequestResponses()).thenReturn(List.of());
        when(event.messageEditorRequestResponse()).thenReturn(Optional.of(editorContext));
        return event;
    }

    private JMenu findMenu(List<Component> items, String text) {
        for (Component item : items) {
            if (item instanceof JMenu menu && text.equals(menu.getText())) {
                return menu;
            }
        }
        fail("Expected menu not found: " + text);
        return null;
    }
}
