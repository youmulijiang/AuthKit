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
    @DisplayName("随机 IP 爆破菜单应把随机 IP 写入请求头后发送到 Intruder")
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

        verify(request).withAddedHeader(eq("X-Forwarded-For"), argThat(new FakeIpService()::isValidIpv4));
        assertEquals(List.of(request), intruderRequests);
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
    @DisplayName("403 Bypass Scan 菜单应触发扫描回调")
    void bypass403Menu_shouldTriggerScanHandler() {
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

            JMenuItem bypass403Item = (JMenuItem) provider.provideMenuItems(event).get(3);
            assertEquals("403 Bypass Scan", bypass403Item.getText());
            bypass403Item.doClick();

            assertEquals(List.of(reqResp), scannedItems);
        } finally {
            i18n.setLanguage(originalLanguage);
        }
    }

    @Test
    @DisplayName("JWT Scan 菜单应触发扫描回调")
    void jwtScanMenu_shouldTriggerScanHandler() {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        i18n.setLanguage(I18n.Language.ENGLISH);
        try {
            HttpRequestResponse reqResp = mock(HttpRequestResponse.class);
            MessageEditorHttpRequestResponse editorContext = mock(MessageEditorHttpRequestResponse.class);
            when(editorContext.requestResponse()).thenReturn(reqResp);
            List<HttpRequestResponse> scanned = new ArrayList<>();
            AuthContextMenuProvider provider = new AuthContextMenuProvider(
                    List::of, () -> true, () -> {}, items -> {}, (auth, user) -> {}, auth -> null);
            provider.setJwtScanHandler(scanned::addAll);

            List<Component> items = provider.provideMenuItems(mockContextMenuEvent(editorContext));
            JMenuItem jwtItem = (JMenuItem) items.get(5);
            assertEquals("JWT Scan", jwtItem.getText());
            jwtItem.doClick();

            assertEquals(List.of(reqResp), scanned);
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
