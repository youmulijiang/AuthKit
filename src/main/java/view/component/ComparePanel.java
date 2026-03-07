package view.component;

import burp.api.montoya.MontoyaApi;
import utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报文对比面板
 * 位于右侧，包含三个区域（垂直三分）：
 * 1. 选择区 (Source) - 外层 JTabbedPane 选项卡为鉴权对象（Original / Unauthorized / User1 ...），
 *    每个选项卡内嵌一个 MessagePanel（含 Request / Response 两个 Tab）。
 * 2. 被选择区 (Target) - 结构与选择区相同，用于选择被比较的鉴权对象。
 * 3. Diff 展示区 - 含 Diff 按钮和 JEditorPane（HTML）展示比较结果，支持颜色高亮。
 */
public class ComparePanel extends JPanel {

    private static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    /** 选择区：外层 TabbedPane，选项卡 = 鉴权对象 */
    private final JTabbedPane tabbedSource;
    /** 被选择区：外层 TabbedPane，选项卡 = 鉴权对象 */
    private final JTabbedPane tabbedTarget;
    /** Diff 差异展示区（HTML 渲染） */
    private final JEditorPane editorPaneDiff;
    /** Diff 按钮 */
    private final JButton btnDiff;
    private final JLabel labelTarget;
    private final JLabel labelSource;
    private final JLabel labelDiff;

    /** 选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> sourcePanels;
    /** 被选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> targetPanels;

    /** Montoya API 引用，用于运行时创建新的 MessagePanel */
    private final MontoyaApi api;

    /** 防止 Source/Target 双向同步时递归触发 */
    private boolean syncingMessageTabs;

    private ComparePanel(Builder builder) {
        this.api = builder.api;
        this.tabbedSource = builder.tabbedSource;
        this.tabbedTarget = builder.tabbedTarget;
        this.editorPaneDiff = builder.editorPaneDiff;
        this.btnDiff = builder.btnDiff;
        this.labelTarget = builder.labelTarget;
        this.labelSource = builder.labelSource;
        this.labelDiff = builder.labelDiff;
        this.sourcePanels = builder.sourcePanels;
        this.targetPanels = builder.targetPanels;
        initLayout();
        initTabSync();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
    }

    /** 初始化垂直三分布局 */
    private void initLayout() {
        setLayout(new BorderLayout());

        // 区域1: 被选择区
        JPanel panelTarget = new JPanel(new BorderLayout());
        panelTarget.add(labelTarget, BorderLayout.NORTH);
        panelTarget.add(tabbedTarget, BorderLayout.CENTER);

        // 区域2: 选择区
        JPanel panelSource = new JPanel(new BorderLayout());
        panelSource.add(labelSource, BorderLayout.NORTH);
        panelSource.add(tabbedSource, BorderLayout.CENTER);

        // 区域3: Diff 展示区（含 Diff 按钮）
        JPanel panelDiffHeader = new JPanel(new BorderLayout());
        panelDiffHeader.add(labelDiff, BorderLayout.WEST);
        panelDiffHeader.add(btnDiff, BorderLayout.EAST);

        JPanel panelDiff = new JPanel(new BorderLayout());
        panelDiff.add(panelDiffHeader, BorderLayout.NORTH);
        panelDiff.add(new JScrollPane(editorPaneDiff), BorderLayout.CENTER);

        JSplitPane splitBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelSource, panelDiff);
        splitBottom.setResizeWeight(0.5);

        JSplitPane splitMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelTarget, splitBottom);
        splitMain.setResizeWeight(0.33);

        add(splitMain, BorderLayout.CENTER);
    }

    /**
     * 初始化 Source/Target 内部 Tab 双向同步
     */
    private void initTabSync() {
        for (MessagePanel panel : sourcePanels.values()) {
            bindMessagePanelSync(panel, true);
        }
        for (MessagePanel panel : targetPanels.values()) {
            bindMessagePanelSync(panel, false);
        }

        tabbedSource.addChangeListener(e -> syncSelectedMessageTab(true));
        tabbedTarget.addChangeListener(e -> syncSelectedMessageTab(false));
    }

    /** 给 MessagePanel 注册双向同步监听 */
    private void bindMessagePanelSync(MessagePanel panel, boolean sourceSide) {
        panel.getTabbedMessage().addChangeListener(e -> syncSelectedMessageTab(sourceSide));
    }

    /** 按当前选中的 Source/Target 同步内部 Request/Response 页签 */
    private void syncSelectedMessageTab(boolean sourceSide) {
        MessagePanel fromPanel = sourceSide ? getSelectedSourcePanel() : getSelectedTargetPanel();
        MessagePanel toPanel = sourceSide ? getSelectedTargetPanel() : getSelectedSourcePanel();
        if (fromPanel == null || toPanel == null || syncingMessageTabs) {
            return;
        }

        int selectedTabIndex = fromPanel.getSelectedTabIndex();
        if (toPanel.getSelectedTabIndex() == selectedTabIndex) {
            return;
        }

        syncingMessageTabs = true;
        try {
            toPanel.setSelectedTabIndex(selectedTabIndex);
        } finally {
            syncingMessageTabs = false;
        }
    }

    /** 获取 Source 当前选中的 MessagePanel */
    public MessagePanel getSelectedSourcePanel() {
        return findMessagePanelByComponent(sourcePanels, tabbedSource.getSelectedComponent());
    }

    /** 获取 Target 当前选中的 MessagePanel */
    public MessagePanel getSelectedTargetPanel() {
        return findMessagePanelByComponent(targetPanels, tabbedTarget.getSelectedComponent());
    }

    /** 获取 Source 当前选中的鉴权对象名称 */
    public String getSelectedSourceName() {
        return findAuthNameByPanel(sourcePanels, tabbedSource.getSelectedComponent());
    }

    /** 获取 Target 当前选中的鉴权对象名称 */
    public String getSelectedTargetName() {
        return findAuthNameByPanel(targetPanels, tabbedTarget.getSelectedComponent());
    }

    /** 获取选择区外层 TabbedPane */
    public JTabbedPane getTabbedSource() {
        return tabbedSource;
    }

    /** 获取被选择区外层 TabbedPane */
    public JTabbedPane getTabbedTarget() {
        return tabbedTarget;
    }

    /** 获取 Diff 展示区 */
    public JEditorPane getEditorPaneDiff() {
        return editorPaneDiff;
    }

    /** 获取 Diff 按钮 */
    public JButton getBtnDiff() {
        return btnDiff;
    }

    /** 根据名称获取选择区中的 MessagePanel */
    public MessagePanel getSourcePanel(String name) {
        return sourcePanels.get(name);
    }

    /** 根据名称获取被选择区中的 MessagePanel */
    public MessagePanel getTargetPanel(String name) {
        return targetPanels.get(name);
    }

    /** 根据鉴权对象名称切换 Target 外层选项卡 */
    public boolean selectTargetTab(String name) {
        MessagePanel panel = targetPanels.get(name);
        if (panel == null) {
            return false;
        }
        tabbedTarget.setSelectedComponent(panel);
        return true;
    }

    /** 切换 Source 当前选中对象的报文页签 */
    public void selectSourceMessageTab(int tabIndex) {
        MessagePanel panel = getSelectedSourcePanel();
        if (panel != null) {
            panel.setSelectedTabIndex(tabIndex);
        }
    }

    /** 切换 Target 当前选中对象的报文页签 */
    public void selectTargetMessageTab(int tabIndex) {
        MessagePanel panel = getSelectedTargetPanel();
        if (panel != null) {
            panel.setSelectedTabIndex(tabIndex);
        }
    }

    /** 获取选择区所有 MessagePanel 映射 */
    public Map<String, MessagePanel> getSourcePanels() {
        return Map.copyOf(sourcePanels);
    }

    /** 获取被选择区所有 MessagePanel 映射 */
    public Map<String, MessagePanel> getTargetPanels() {
        return Map.copyOf(targetPanels);
    }

    /** 设置 Diff 展示内容（HTML 格式） */
    public void setDiffContent(String diffHtml) {
        editorPaneDiff.setText(diffHtml);
        editorPaneDiff.setCaretPosition(0);
    }

    /** 清空所有区域内容 */
    public void clearAll() {
        sourcePanels.values().forEach(MessagePanel::clearContent);
        targetPanels.values().forEach(MessagePanel::clearContent);
        editorPaneDiff.setText("");
    }

    /**
     * 运行时动态添加一个鉴权对象，同时在 Source 和 Target 创建对应的 Tab
     *
     * @param name 鉴权对象名称（如 "User1"）
     */
    public void addAuthObject(String name) {
        if (sourcePanels.containsKey(name)) {
            return;
        }

        MessagePanel sourcePanel = new MessagePanel(api);
        sourcePanels.put(name, sourcePanel);
        tabbedSource.addTab(I18n.getInstance().translateAuthObjectName(name), sourcePanel);
        bindMessagePanelSync(sourcePanel, true);

        MessagePanel targetPanel = new MessagePanel(api);
        targetPanels.put(name, targetPanel);
        tabbedTarget.addTab(I18n.getInstance().translateAuthObjectName(name), targetPanel);
        bindMessagePanelSync(targetPanel, false);
    }

    /**
     * 运行时重命名一个鉴权对象，同时更新 Source 和 Target 的 Tab 标题和 Map key
     *
     * @param oldName 旧名称
     * @param newName 新名称
     */
    public void renameAuthObject(String oldName, String newName) {
        // 更新 Source
        MessagePanel sourcePanel = sourcePanels.remove(oldName);
        if (sourcePanel != null) {
            sourcePanels.put(newName, sourcePanel);
            int idx = tabbedSource.indexOfComponent(sourcePanel);
            if (idx >= 0) {
                tabbedSource.setTitleAt(idx, I18n.getInstance().translateAuthObjectName(newName));
            }
        }

        // 更新 Target
        MessagePanel targetPanel = targetPanels.remove(oldName);
        if (targetPanel != null) {
            targetPanels.put(newName, targetPanel);
            int idx = tabbedTarget.indexOfComponent(targetPanel);
            if (idx >= 0) {
                tabbedTarget.setTitleAt(idx, I18n.getInstance().translateAuthObjectName(newName));
            }
        }
    }

    private MessagePanel findMessagePanelByComponent(Map<String, MessagePanel> panels, Component component) {
        if (component instanceof MessagePanel panel) {
            return panel;
        }
        for (MessagePanel panel : panels.values()) {
            if (panel == component) {
                return panel;
            }
        }
        return null;
    }

    private String findAuthNameByPanel(Map<String, MessagePanel> panels, Component component) {
        if (component == null) {
            return null;
        }
        for (Map.Entry<String, MessagePanel> entry : panels.entrySet()) {
            if (entry.getValue() == component) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void refreshTexts() {
        labelTarget.setText("  " + I18n.getInstance().text("compare", "label.target"));
        labelSource.setText("  " + I18n.getInstance().text("compare", "label.source"));
        labelDiff.setText("  " + I18n.getInstance().text("compare", "label.diff"));
        btnDiff.setText(I18n.getInstance().text("compare", "button.diff"));
        refreshAuthObjectTabTitles(tabbedSource, sourcePanels);
        refreshAuthObjectTabTitles(tabbedTarget, targetPanels);
    }

    private void refreshAuthObjectTabTitles(JTabbedPane tabbedPane, Map<String, MessagePanel> panels) {
        for (Map.Entry<String, MessagePanel> entry : panels.entrySet()) {
            int idx = tabbedPane.indexOfComponent(entry.getValue());
            if (idx >= 0) {
                tabbedPane.setTitleAt(idx, I18n.getInstance().translateAuthObjectName(entry.getKey()));
            }
        }
    }

    /**
     * 运行时动态移除一个鉴权对象，同时从 Source 和 Target 移除对应的 Tab
     *
     * @param name 鉴权对象名称
     */
    public void removeAuthObject(String name) {
        MessagePanel sourcePanel = sourcePanels.remove(name);
        if (sourcePanel != null) {
            int idx = tabbedSource.indexOfComponent(sourcePanel);
            if (idx >= 0) {
                tabbedSource.removeTabAt(idx);
            }
        }

        MessagePanel targetPanel = targetPanels.remove(name);
        if (targetPanel != null) {
            int idx = tabbedTarget.indexOfComponent(targetPanel);
            if (idx >= 0) {
                tabbedTarget.removeTabAt(idx);
            }
        }
    }

    /**
     * 报文对比面板建造器
     * 通过 addAuthObject() 添加鉴权对象，会同时在选择区和被选择区创建对应的 Tab。
     */
    public static class Builder {

        private final MontoyaApi api;
        private final JTabbedPane tabbedSource;
        private final JTabbedPane tabbedTarget;
        private final JEditorPane editorPaneDiff;
        private final JButton btnDiff;
        private final JLabel labelTarget;
        private final JLabel labelSource;
        private final JLabel labelDiff;
        private final Map<String, MessagePanel> sourcePanels;
        private final Map<String, MessagePanel> targetPanels;

        public Builder(MontoyaApi api) {
            this.api = api;
            this.tabbedSource = new JTabbedPane();
            this.tabbedTarget = new JTabbedPane();
            this.editorPaneDiff = new JEditorPane("text/html", "");
            this.editorPaneDiff.setEditable(false);
            this.btnDiff = new JButton();
            this.labelTarget = new JLabel();
            this.labelSource = new JLabel();
            this.labelDiff = new JLabel();
            this.sourcePanels = new LinkedHashMap<>();
            this.targetPanels = new LinkedHashMap<>();
        }

        /**
         * 添加一个鉴权对象，会同时在选择区和被选择区创建对应的选项卡
         *
         * @param name 鉴权对象名称（如 "Original" / "Unauthorized" / "User1"）
         * @return Builder 自身
         */
        public Builder addAuthObject(String name) {
            MessagePanel sourcePanel = new MessagePanel(api);
            sourcePanels.put(name, sourcePanel);
            tabbedSource.addTab(I18n.getInstance().translateAuthObjectName(name), sourcePanel);

            MessagePanel targetPanel = new MessagePanel(api);
            targetPanels.put(name, targetPanel);
            tabbedTarget.addTab(I18n.getInstance().translateAuthObjectName(name), targetPanel);
            return this;
        }

        /** 构建报文对比面板 */
        public ComparePanel build() {
            if (sourcePanels.isEmpty()) {
                addAuthObject("Original");
                addAuthObject("Unauthorized");
            }
            return new ComparePanel(this);
        }
    }
}
