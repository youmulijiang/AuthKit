package view.component;

import burp.api.montoya.MontoyaApi;

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

    /** 选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> sourcePanels;
    /** 被选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> targetPanels;

    /** Montoya API 引用，用于运行时创建新的 MessagePanel */
    private final MontoyaApi api;

    private ComparePanel(Builder builder) {
        this.api = builder.api;
        this.tabbedSource = builder.tabbedSource;
        this.tabbedTarget = builder.tabbedTarget;
        this.editorPaneDiff = builder.editorPaneDiff;
        this.btnDiff = builder.btnDiff;
        this.sourcePanels = builder.sourcePanels;
        this.targetPanels = builder.targetPanels;
        initLayout();
        initTabSync();
    }

    /** 初始化垂直三分布局 */
    private void initLayout() {
        setLayout(new BorderLayout());

        // 区域1: 选择区
        JPanel panelSource = new JPanel(new BorderLayout());
        panelSource.add(new JLabel("  Source"), BorderLayout.NORTH);
        panelSource.add(tabbedSource, BorderLayout.CENTER);

        // 区域2: 被选择区
        JPanel panelTarget = new JPanel(new BorderLayout());
        panelTarget.add(new JLabel("  Target"), BorderLayout.NORTH);
        panelTarget.add(tabbedTarget, BorderLayout.CENTER);

        // 区域3: Diff 展示区（含 Diff 按钮）
        JPanel panelDiffHeader = new JPanel(new BorderLayout());
        panelDiffHeader.add(new JLabel("  Diff"), BorderLayout.WEST);
        panelDiffHeader.add(btnDiff, BorderLayout.EAST);

        JPanel panelDiff = new JPanel(new BorderLayout());
        panelDiff.add(panelDiffHeader, BorderLayout.NORTH);
        panelDiff.add(new JScrollPane(editorPaneDiff), BorderLayout.CENTER);

        JSplitPane splitBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelTarget, panelDiff);
        splitBottom.setResizeWeight(0.5);

        JSplitPane splitMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelSource, splitBottom);
        splitMain.setResizeWeight(0.33);

        add(splitMain, BorderLayout.CENTER);
    }

    /**
     * 初始化 Source/Target 内部 Tab 同步
     * 当 Source 选中的 MessagePanel 切换 Request/Response Tab 时，
     * Target 选中的 MessagePanel 也同步切换到相同的 Tab。
     */
    private void initTabSync() {
        // 监听 Source 中每个 MessagePanel 的内部 Tab 切换
        for (MessagePanel panel : sourcePanels.values()) {
            panel.getTabbedMessage().addChangeListener(e -> syncTargetTab());
        }
        // 监听 Source 外层 Tab 切换（切换鉴权对象时也同步）
        tabbedSource.addChangeListener(e -> syncTargetTab());
    }

    /**
     * 同步 Target 当前选中的 MessagePanel 的内部 Tab 到与 Source 一致
     */
    private void syncTargetTab() {
        MessagePanel sourcePanel = getSelectedSourcePanel();
        MessagePanel targetPanel = getSelectedTargetPanel();
        if (sourcePanel != null && targetPanel != null) {
            int sourceTabIndex = sourcePanel.getSelectedTabIndex();
            if (targetPanel.getSelectedTabIndex() != sourceTabIndex) {
                targetPanel.getTabbedMessage().setSelectedIndex(sourceTabIndex);
            }
        }
    }

    /** 获取 Source 当前选中的 MessagePanel */
    public MessagePanel getSelectedSourcePanel() {
        int idx = tabbedSource.getSelectedIndex();
        if (idx < 0) return null;
        String name = tabbedSource.getTitleAt(idx);
        return sourcePanels.get(name);
    }

    /** 获取 Target 当前选中的 MessagePanel */
    public MessagePanel getSelectedTargetPanel() {
        int idx = tabbedTarget.getSelectedIndex();
        if (idx < 0) return null;
        String name = tabbedTarget.getTitleAt(idx);
        return targetPanels.get(name);
    }

    /** 获取 Source 当前选中的鉴权对象名称 */
    public String getSelectedSourceName() {
        int idx = tabbedSource.getSelectedIndex();
        return idx >= 0 ? tabbedSource.getTitleAt(idx) : null;
    }

    /** 获取 Target 当前选中的鉴权对象名称 */
    public String getSelectedTargetName() {
        int idx = tabbedTarget.getSelectedIndex();
        return idx >= 0 ? tabbedTarget.getTitleAt(idx) : null;
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
        tabbedSource.addTab(name, sourcePanel);
        // 注册新 Tab 的内部 Tab 同步监听
        sourcePanel.getTabbedMessage().addChangeListener(e -> syncTargetTab());

        MessagePanel targetPanel = new MessagePanel(api);
        targetPanels.put(name, targetPanel);
        tabbedTarget.addTab(name, targetPanel);
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
                tabbedSource.setTitleAt(idx, newName);
            }
        }

        // 更新 Target
        MessagePanel targetPanel = targetPanels.remove(oldName);
        if (targetPanel != null) {
            targetPanels.put(newName, targetPanel);
            int idx = tabbedTarget.indexOfComponent(targetPanel);
            if (idx >= 0) {
                tabbedTarget.setTitleAt(idx, newName);
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
        private final Map<String, MessagePanel> sourcePanels;
        private final Map<String, MessagePanel> targetPanels;

        public Builder(MontoyaApi api) {
            this.api = api;
            this.tabbedSource = new JTabbedPane();
            this.tabbedTarget = new JTabbedPane();
            this.editorPaneDiff = new JEditorPane("text/html", "");
            this.editorPaneDiff.setEditable(false);
            this.btnDiff = new JButton("Diff");
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
            tabbedSource.addTab(name, sourcePanel);

            MessagePanel targetPanel = new MessagePanel(api);
            targetPanels.put(name, targetPanel);
            tabbedTarget.addTab(name, targetPanel);
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
