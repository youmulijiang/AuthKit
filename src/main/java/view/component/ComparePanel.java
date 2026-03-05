package view.component;

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
 * 3. Diff 展示区 - JTextArea 展示两个区域的比较结果。
 */
public class ComparePanel extends JPanel {

    private static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    /** 选择区：外层 TabbedPane，选项卡 = 鉴权对象 */
    private final JTabbedPane tabbedSource;
    /** 被选择区：外层 TabbedPane，选项卡 = 鉴权对象 */
    private final JTabbedPane tabbedTarget;
    /** Diff 差异展示文本区 */
    private final JTextArea textAreaDiff;

    /** 选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> sourcePanels;
    /** 被选择区中每个鉴权对象名称 -> MessagePanel 的映射 */
    private final Map<String, MessagePanel> targetPanels;

    private ComparePanel(Builder builder) {
        this.tabbedSource = builder.tabbedSource;
        this.tabbedTarget = builder.tabbedTarget;
        this.textAreaDiff = builder.textAreaDiff;
        this.sourcePanels = builder.sourcePanels;
        this.targetPanels = builder.targetPanels;
        initLayout();
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

        // 区域3: Diff 展示区
        JPanel panelDiff = new JPanel(new BorderLayout());
        panelDiff.add(new JLabel("  Diff"), BorderLayout.NORTH);
        panelDiff.add(new JScrollPane(textAreaDiff), BorderLayout.CENTER);

        JSplitPane splitBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelTarget, panelDiff);
        splitBottom.setResizeWeight(0.5);

        JSplitPane splitMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelSource, splitBottom);
        splitMain.setResizeWeight(0.33);

        add(splitMain, BorderLayout.CENTER);
    }

    /** 获取选择区外层 TabbedPane */
    public JTabbedPane getTabbedSource() {
        return tabbedSource;
    }

    /** 获取被选择区外层 TabbedPane */
    public JTabbedPane getTabbedTarget() {
        return tabbedTarget;
    }

    /** 获取 Diff 展示文本区 */
    public JTextArea getTextAreaDiff() {
        return textAreaDiff;
    }

    /**
     * 根据名称获取选择区中的 MessagePanel
     *
     * @param name 鉴权对象名称
     * @return 对应的 MessagePanel，不存在返回 null
     */
    public MessagePanel getSourcePanel(String name) {
        return sourcePanels.get(name);
    }

    /**
     * 根据名称获取被选择区中的 MessagePanel
     *
     * @param name 鉴权对象名称
     * @return 对应的 MessagePanel，不存在返回 null
     */
    public MessagePanel getTargetPanel(String name) {
        return targetPanels.get(name);
    }

    /**
     * 获取选择区所有 MessagePanel 映射
     *
     * @return 名称 -> MessagePanel 的映射（只读视图）
     */
    public Map<String, MessagePanel> getSourcePanels() {
        return Map.copyOf(sourcePanels);
    }

    /**
     * 获取被选择区所有 MessagePanel 映射
     *
     * @return 名称 -> MessagePanel 的映射（只读视图）
     */
    public Map<String, MessagePanel> getTargetPanels() {
        return Map.copyOf(targetPanels);
    }

    /**
     * 设置 Diff 展示内容
     *
     * @param diffText diff 结果文本
     */
    public void setDiffContent(String diffText) {
        textAreaDiff.setText(diffText);
        textAreaDiff.setCaretPosition(0);
    }

    /** 清空所有区域内容 */
    public void clearAll() {
        sourcePanels.values().forEach(MessagePanel::clearContent);
        targetPanels.values().forEach(MessagePanel::clearContent);
        textAreaDiff.setText("");
    }

    /**
     * 报文对比面板建造器
     * 通过 addAuthObject() 添加鉴权对象，会同时在选择区和被选择区创建对应的 Tab。
     */
    public static class Builder {

        private final JTabbedPane tabbedSource;
        private final JTabbedPane tabbedTarget;
        private final JTextArea textAreaDiff;
        private final Map<String, MessagePanel> sourcePanels;
        private final Map<String, MessagePanel> targetPanels;

        public Builder() {
            this.tabbedSource = new JTabbedPane();
            this.tabbedTarget = new JTabbedPane();
            this.textAreaDiff = new JTextArea();
            this.textAreaDiff.setFont(MONO_FONT);
            this.textAreaDiff.setEditable(false);
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
            MessagePanel sourcePanel = new MessagePanel.Builder().build();
            sourcePanels.put(name, sourcePanel);
            tabbedSource.addTab(name, sourcePanel);

            MessagePanel targetPanel = new MessagePanel.Builder().build();
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
