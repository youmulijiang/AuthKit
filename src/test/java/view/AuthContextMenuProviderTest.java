package view;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
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
}
