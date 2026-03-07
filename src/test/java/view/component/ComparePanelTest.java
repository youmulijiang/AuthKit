package view.component;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.I18n;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComparePanelTest {

    @Test
    @DisplayName("Target 区域应位于 Source 区域上方")
    void targetPanel_shouldBePlacedAboveSourcePanel() throws Exception {
        ComparePanel comparePanel = createComparePanel();

        SwingUtilities.invokeAndWait(() -> {
            JSplitPane splitMain = (JSplitPane) comparePanel.getComponent(0);
            JPanel topPanel = (JPanel) splitMain.getTopComponent();

            assertEquals(I18n.getInstance().text("compare", "label.target"), findLabelText(topPanel));
        });
    }

    @Test
    @DisplayName("Request Response 页签应支持 Source 与 Target 双向同步")
    void messageTabs_shouldSyncBothDirections() throws Exception {
        ComparePanel comparePanel = createComparePanel();

        SwingUtilities.invokeAndWait(() -> {
            MessagePanel sourcePanel = comparePanel.getSelectedSourcePanel();
            MessagePanel targetPanel = comparePanel.getSelectedTargetPanel();

            sourcePanel.setSelectedTabIndex(MessagePanel.RESPONSE_TAB_INDEX);
            assertEquals(MessagePanel.RESPONSE_TAB_INDEX, targetPanel.getSelectedTabIndex());

            targetPanel.setSelectedTabIndex(MessagePanel.REQUEST_TAB_INDEX);
            assertEquals(MessagePanel.REQUEST_TAB_INDEX, sourcePanel.getSelectedTabIndex());
        });
    }

    @Test
    @DisplayName("应支持按鉴权对象名称切换 Target 并同步到 Response")
    void targetSelection_shouldSwitchByAuthNameAndSyncResponseTab() throws Exception {
        ComparePanel comparePanel = createComparePanel();

        SwingUtilities.invokeAndWait(() -> {
            assertTrue(comparePanel.selectTargetTab("Unauthorized"));
            comparePanel.selectTargetMessageTab(MessagePanel.RESPONSE_TAB_INDEX);

            assertEquals("Unauthorized", comparePanel.getSelectedTargetName());
            assertEquals(MessagePanel.RESPONSE_TAB_INDEX,
                    comparePanel.getSelectedTargetPanel().getSelectedTabIndex());
            assertEquals(MessagePanel.RESPONSE_TAB_INDEX,
                    comparePanel.getSelectedSourcePanel().getSelectedTabIndex());
        });
    }

    @Test
    @DisplayName("切换语言后应刷新显示标题，但保持逻辑鉴权对象名称不变")
    void languageSwitch_shouldRefreshDisplayTitlesButKeepLogicalAuthNames() throws Exception {
        I18n i18n = I18n.getInstance();
        I18n.Language originalLanguage = i18n.getCurrentLanguage();
        try {
            setLanguage(I18n.Language.ENGLISH);
            ComparePanel comparePanel = createComparePanel();

            SwingUtilities.invokeAndWait(() -> {
                assertEquals("Target", findLabelText((Container) ((JSplitPane) comparePanel.getComponent(0)).getTopComponent()));
                assertEquals(i18n.translateAuthObjectName("Original"), comparePanel.getTabbedSource().getTitleAt(0));
                assertEquals("Original", comparePanel.getSelectedSourceName());
            });

            setLanguage(I18n.Language.CHINESE);

            SwingUtilities.invokeAndWait(() -> {
                JSplitPane splitMain = (JSplitPane) comparePanel.getComponent(0);
                JPanel topPanel = (JPanel) splitMain.getTopComponent();

                assertEquals(i18n.text("compare", "label.target"), findLabelText(topPanel));
                assertEquals(i18n.translateAuthObjectName("Original"), comparePanel.getTabbedSource().getTitleAt(0));
                assertEquals(i18n.translateAuthObjectName("Unauthorized"), comparePanel.getTabbedTarget().getTitleAt(1));
                assertEquals("Original", comparePanel.getSelectedSourceName());
                assertTrue(comparePanel.selectTargetTab("Unauthorized"));
                assertEquals("Unauthorized", comparePanel.getSelectedTargetName());
            });
        } finally {
            setLanguage(originalLanguage);
        }
    }

    private ComparePanel createComparePanel() {
        MontoyaApi api = mock(MontoyaApi.class);
        UserInterface userInterface = mock(UserInterface.class);
        when(api.userInterface()).thenReturn(userInterface);
        when(userInterface.createHttpRequestEditor(any(EditorOptions[].class)))
                .thenAnswer(invocation -> mockRequestEditor());
        when(userInterface.createHttpResponseEditor(any(EditorOptions[].class)))
                .thenAnswer(invocation -> mockResponseEditor());
        return new ComparePanel.Builder(api).build();
    }

    private HttpRequestEditor mockRequestEditor() {
        HttpRequestEditor editor = mock(HttpRequestEditor.class);
        when(editor.uiComponent()).thenReturn(new JPanel());
        return editor;
    }

    private HttpResponseEditor mockResponseEditor() {
        HttpResponseEditor editor = mock(HttpResponseEditor.class);
        when(editor.uiComponent()).thenReturn(new JPanel());
        return editor;
    }

    private void setLanguage(I18n.Language language) throws Exception {
        SwingUtilities.invokeAndWait(() -> I18n.getInstance().setLanguage(language));
    }

    private String findLabelText(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) {
                return label.getText().trim();
            }
        }
        fail("Expected label not found");
        return "";
    }
}