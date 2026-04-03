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
 * 3. Diff 展示区 - 自动对比，含进度条和 JEditorPane（HTML）展示比较结果，支持颜色高亮。
 */
public class ComparePanel extends JPanel {

    private static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    /**
     * Diff 回调接口，当 Source/Target 选项卡切换时自动调用
     */
    @FunctionalInterface
    public interface DiffCallback {
        void onDiffRequested(ComparePanel panel);
    }

    /** 选择区：外层 TabbedPane，选项卡 = 鉴权对象 */
    private final JTabbedPane tabbedSource;
    /** 被选择区：外层 TabbedPane，选项卡 = 鉴权对象 */
    private final JTabbedPane tabbedTarget;
    /** Diff 差异展示区 — 统一视图（HTML 渲染） */
    private final JEditorPane editorPaneDiff;
    /** Diff 差异展示区 — 并排视图左侧 */
    private final JEditorPane editorPaneDiffLeft;
    /** Diff 差异展示区 — 并排视图右侧 */
    private final JEditorPane editorPaneDiffRight;
    /** Diff 进度条 */
    private final JProgressBar progressBar;
    private final JLabel labelTarget;
    private final JLabel labelSource;
    private final JLabel labelDiff;

    /** 当前 Diff 视图模式：true = 并排双面板，false = 统一视图 */
    private boolean sideBySideMode = false;
    /** 统一视图容器 */
    private JScrollPane scrollUnifiedDiff;
    /** 并排视图容器 */
    private JSplitPane splitSideBySide;
    /** Diff 内容区域使用 CardLayout 切换 */
    private JPanel cardDiffContent;
    private CardLayout cardLayout;
    private static final String CARD_UNIFIED = "unified";
    private static final String CARD_SIDE_BY_SIDE = "sideBySide";

    /** 选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> sourcePanels;
    /** 被选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> targetPanels;

    /** Montoya API 引用，用于运行时创建新的 MessagePanel */
    private final MontoyaApi api;

    /** 三个区域面板引用，用于展开/折叠 */
    private JPanel panelTarget;
    private JPanel panelSource;
    private JPanel panelDiff;
    private JSplitPane splitMain;
    private JSplitPane splitBottom;

    /** 展开/折叠按钮 */
    private final JToggleButton btnExpandTarget = new JToggleButton("Expand");
    private final JToggleButton btnExpandSource = new JToggleButton("Expand");
    private final JToggleButton btnExpandDiff = new JToggleButton("Expand");

    /** 防止 Source/Target 双向同步时递归触发 */
    private boolean syncingMessageTabs;

    /** Diff 回调，当 tab 切换时自动触发 */
    private volatile DiffCallback diffCallback;

    private ComparePanel(Builder builder) {
        this.api = builder.api;
        this.tabbedSource = builder.tabbedSource;
        this.tabbedTarget = builder.tabbedTarget;
        this.editorPaneDiff = builder.editorPaneDiff;
        this.editorPaneDiffLeft = new JEditorPane("text/html", "");
        this.editorPaneDiffLeft.setEditable(false);
        this.editorPaneDiffRight = new JEditorPane("text/html", "");
        this.editorPaneDiffRight.setEditable(false);
        this.progressBar = new JProgressBar();
        this.progressBar.setIndeterminate(true);
        this.progressBar.setStringPainted(true);
        this.progressBar.setString("Comparing...");
        this.progressBar.setVisible(false);
        this.labelTarget = builder.labelTarget;
        this.labelSource = builder.labelSource;
        this.labelDiff = builder.labelDiff;
        this.sourcePanels = builder.sourcePanels;
        this.targetPanels = builder.targetPanels;
        initLayout();
        initTabSync();
        initDiffContextMenu();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
    }

    /** 初始化垂直三分布局 */
    private void initLayout() {
        setLayout(new BorderLayout());

        // 区域1: Target (被选择区)
        JPanel panelTargetHeader = new JPanel(new BorderLayout());
        panelTargetHeader.add(labelTarget, BorderLayout.CENTER);
        panelTargetHeader.add(btnExpandTarget, BorderLayout.EAST);

        panelTarget = new JPanel(new BorderLayout());
        panelTarget.add(panelTargetHeader, BorderLayout.NORTH);
        panelTarget.add(tabbedTarget, BorderLayout.CENTER);

        // 区域2: Source (选择区)
        JPanel panelSourceHeader = new JPanel(new BorderLayout());
        panelSourceHeader.add(labelSource, BorderLayout.CENTER);
        panelSourceHeader.add(btnExpandSource, BorderLayout.EAST);

        panelSource = new JPanel(new BorderLayout());
        panelSource.add(panelSourceHeader, BorderLayout.NORTH);
        panelSource.add(tabbedSource, BorderLayout.CENTER);

        // 区域3: Diff 展示区（含进度条和展开按钮）
        JPanel panelDiffHeader = new JPanel(new BorderLayout());
        panelDiffHeader.add(labelDiff, BorderLayout.WEST);
        panelDiffHeader.add(progressBar, BorderLayout.CENTER);
        panelDiffHeader.add(btnExpandDiff, BorderLayout.EAST);

        // 统一视图
        scrollUnifiedDiff = new JScrollPane(editorPaneDiff);

        // 并排视图
        JScrollPane scrollLeft = new JScrollPane(editorPaneDiffLeft);
        JScrollPane scrollRight = new JScrollPane(editorPaneDiffRight);
        // 同步滚动（使用标志位防止循环触发）
        boolean[] syncing = {false};
        scrollLeft.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (syncing[0]) return;
            syncing[0] = true;
            scrollRight.getVerticalScrollBar().setValue(e.getValue());
            syncing[0] = false;
        });
        scrollRight.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (syncing[0]) return;
            syncing[0] = true;
            scrollLeft.getVerticalScrollBar().setValue(e.getValue());
            syncing[0] = false;
        });
        // 同步水平滚动
        boolean[] syncingH = {false};
        scrollLeft.getHorizontalScrollBar().addAdjustmentListener(e -> {
            if (syncingH[0]) return;
            syncingH[0] = true;
            scrollRight.getHorizontalScrollBar().setValue(e.getValue());
            syncingH[0] = false;
        });
        scrollRight.getHorizontalScrollBar().addAdjustmentListener(e -> {
            if (syncingH[0]) return;
            syncingH[0] = true;
            scrollLeft.getHorizontalScrollBar().setValue(e.getValue());
            syncingH[0] = false;
        });
        splitSideBySide = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollLeft, scrollRight);
        splitSideBySide.setResizeWeight(0.5);

        // CardLayout 切换
        cardLayout = new CardLayout();
        cardDiffContent = new JPanel(cardLayout);
        cardDiffContent.add(scrollUnifiedDiff, CARD_UNIFIED);
        cardDiffContent.add(splitSideBySide, CARD_SIDE_BY_SIDE);

        panelDiff = new JPanel(new BorderLayout());
        panelDiff.add(panelDiffHeader, BorderLayout.NORTH);
        panelDiff.add(cardDiffContent, BorderLayout.CENTER);

        splitBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelSource, panelDiff);
        splitBottom.setResizeWeight(0.5);

        splitMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelTarget, splitBottom);
        splitMain.setResizeWeight(0.33);

        add(splitMain, BorderLayout.CENTER);

        // 绑定展开/折叠事件
        btnExpandTarget.addActionListener(e -> toggleExpand(panelTarget, btnExpandTarget));
        btnExpandSource.addActionListener(e -> toggleExpand(panelSource, btnExpandSource));
        btnExpandDiff.addActionListener(e -> toggleExpand(panelDiff, btnExpandDiff));
    }

    /** 展开或折叠指定面板 */
    private void toggleExpand(JPanel panel, JToggleButton clickedBtn) {
        if (clickedBtn.isSelected()) {
            // 展开：只显示当前面板
            removeAll();
            add(panel, BorderLayout.CENTER);
            clickedBtn.setText("Collapse");
            // 取消其他按钮的选中状态
            for (JToggleButton btn : new JToggleButton[]{btnExpandTarget, btnExpandSource, btnExpandDiff}) {
                if (btn != clickedBtn) {
                    btn.setSelected(false);
                    btn.setText("Expand");
                }
            }
        } else {
            // 折叠：恢复三分布局
            removeAll();
            restoreSplitLayout();
            clickedBtn.setText("Expand");
        }
        revalidate();
        repaint();
    }

    /** 恢复三分布局 */
    private void restoreSplitLayout() {
        splitBottom.setTopComponent(panelSource);
        splitBottom.setBottomComponent(panelDiff);
        splitBottom.setResizeWeight(0.5);
        splitMain.setTopComponent(panelTarget);
        splitMain.setBottomComponent(splitBottom);
        splitMain.setResizeWeight(0.33);
        add(splitMain, BorderLayout.CENTER);
    }

    /** 初始化 Diff 区域的右键菜单，用于切换统一视图和并排视图 */
    private void initDiffContextMenu() {
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem itemUnified = new JMenuItem(
                I18n.getInstance().text("compare", "menu.viewUnified"));
        JMenuItem itemSideBySide = new JMenuItem(
                I18n.getInstance().text("compare", "menu.viewSideBySide"));

        itemUnified.addActionListener(e -> switchDiffView(false));
        itemSideBySide.addActionListener(e -> switchDiffView(true));

        popupMenu.add(itemUnified);
        popupMenu.add(itemSideBySide);

        // 注册语言切换刷新
        I18n.getInstance().addLanguageChangeListener(() -> {
            I18n i18n = I18n.getInstance();
            itemUnified.setText(i18n.text("compare", "menu.viewUnified"));
            itemSideBySide.setText(i18n.text("compare", "menu.viewSideBySide"));
        });

        // 给所有 diff 相关组件注册右键菜单
        editorPaneDiff.setComponentPopupMenu(popupMenu);
        editorPaneDiffLeft.setComponentPopupMenu(popupMenu);
        editorPaneDiffRight.setComponentPopupMenu(popupMenu);
        cardDiffContent.setComponentPopupMenu(popupMenu);
    }

    /** 切换 Diff 视图模式 */
    private void switchDiffView(boolean sideBySide) {
        if (this.sideBySideMode == sideBySide) {
            return;
        }
        this.sideBySideMode = sideBySide;
        cardLayout.show(cardDiffContent, sideBySide ? CARD_SIDE_BY_SIDE : CARD_UNIFIED);
        // 触发重新 diff 以填充对应面板
        triggerAutoDiff();
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

        tabbedSource.addChangeListener(e -> {
            syncSelectedMessageTab(true);
            triggerAutoDiff();
        });
        tabbedTarget.addChangeListener(e -> {
            syncSelectedMessageTab(false);
            triggerAutoDiff();
        });
    }

    /** 给 MessagePanel 注册双向同步监听 */
    private void bindMessagePanelSync(MessagePanel panel, boolean sourceSide) {
        panel.getTabbedMessage().addChangeListener(e -> {
            syncSelectedMessageTab(sourceSide);
            triggerAutoDiff();
        });
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

    /** 设置 Diff 回调，当 tab 切换时自动触发 */
    public void setDiffCallback(DiffCallback callback) {
        this.diffCallback = callback;
    }

    /** 显示进度条（正在比较中） */
    public void showProgress() {
        progressBar.setVisible(true);
    }

    /** 隐藏进度条（比较完成） */
    public void hideProgress() {
        progressBar.setVisible(false);
    }

    /** 触发自动 diff */
    private void triggerAutoDiff() {
        DiffCallback cb = this.diffCallback;
        if (cb != null) {
            cb.onDiffRequested(this);
        }
    }

    /** 对外触发一次自动 diff */
    public void requestDiff() {
        triggerAutoDiff();
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

    /** 设置 Diff 展示内容（HTML 格式）— 统一视图 */
    public void setDiffContent(String diffHtml) {
        editorPaneDiff.setText(diffHtml);
        editorPaneDiff.setCaretPosition(0);
    }

    /** 设置 Diff 展示内容 — 并排视图（左 Source / 右 Target） */
    public void setDiffContentSideBySide(String leftHtml, String rightHtml) {
        editorPaneDiffLeft.setText(leftHtml);
        editorPaneDiffLeft.setCaretPosition(0);
        editorPaneDiffRight.setText(rightHtml);
        editorPaneDiffRight.setCaretPosition(0);
    }

    /** 当前是否为并排双面板模式 */
    public boolean isSideBySideMode() {
        return sideBySideMode;
    }

    /** 清空所有区域内容 */
    public void clearAll() {
        sourcePanels.values().forEach(MessagePanel::clearContent);
        targetPanels.values().forEach(MessagePanel::clearContent);
        hideProgress();
        editorPaneDiff.setText("");
        editorPaneDiffLeft.setText("");
        editorPaneDiffRight.setText("");
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
        progressBar.setString(I18n.getInstance().text("compare", "message.comparing"));
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
