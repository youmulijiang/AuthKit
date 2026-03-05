package view;

import view.component.ComparePanel;
import view.component.DataTablePanel;
import view.component.MetadataTablePanel;
import view.component.ToolbarPanel;

import javax.swing.*;
import java.awt.*;

/**
 * 插件主面板
 * 作为 Burp Suite Tab 的根面板，使用 JSplitPane 将界面分为左右两部分：
 * 左侧: 工具栏 + 数据表 + 元数据透视表
 * 右侧: 报文对比区（原始报文 / 被比较报文 / Diff 展示）
 */
public class MainPanel extends JPanel {

    private final ToolbarPanel panelToolbar;
    private final DataTablePanel panelDataTable;
    private final MetadataTablePanel panelMetadataTable;
    private final JTabbedPane tabbedRight;
    private final ComparePanel panelCompare;
    private final JPanel panelConfiguration;
    private final JPanel panelUser;

    public MainPanel() {
        this.panelToolbar = new ToolbarPanel.Builder()
                .filterPlaceholder("输入 URL 关键字筛选")
                .build();
        this.panelDataTable = new DataTablePanel.Builder().build();
        this.panelMetadataTable = new MetadataTablePanel.Builder().build();
        this.panelCompare = new ComparePanel.Builder().build();
        this.panelConfiguration = new JPanel(new BorderLayout());
        this.panelUser = new JPanel(new BorderLayout());
        this.tabbedRight = new JTabbedPane();
        initLayout();
    }

    /** 初始化主面板布局 */
    private void initLayout() {
        setLayout(new BorderLayout());

        // 左侧面板: 工具栏 + 数据表 + 元数据透视表
        JPanel panelLeft = new JPanel(new BorderLayout());
        panelLeft.add(panelToolbar, BorderLayout.NORTH);

        JSplitPane splitLeftVertical = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                panelDataTable,
                panelMetadataTable
        );
        splitLeftVertical.setResizeWeight(0.7);
        panelLeft.add(splitLeftVertical, BorderLayout.CENTER);

        // 右侧面板: JTabbedPane（View / Configuration / User）
        tabbedRight.addTab("View", panelCompare);
        tabbedRight.addTab("Configuration", panelConfiguration);
        tabbedRight.addTab("User", panelUser);

        // 左右水平分割
        JSplitPane splitMain = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                panelLeft,
                tabbedRight
        );
        splitMain.setResizeWeight(0.45);

        add(splitMain, BorderLayout.CENTER);
    }

    /** 获取工具栏面板 */
    public ToolbarPanel getPanelToolbar() {
        return panelToolbar;
    }

    /** 获取数据表面板 */
    public DataTablePanel getPanelDataTable() {
        return panelDataTable;
    }

    /** 获取元数据透视表面板 */
    public MetadataTablePanel getPanelMetadataTable() {
        return panelMetadataTable;
    }

    /** 获取右侧 TabbedPane */
    public JTabbedPane getTabbedRight() {
        return tabbedRight;
    }

    /** 获取报文对比面板（View 选项卡） */
    public ComparePanel getPanelCompare() {
        return panelCompare;
    }

    /** 获取配置面板（Configuration 选项卡） */
    public JPanel getPanelConfiguration() {
        return panelConfiguration;
    }

    /** 获取用户面板（User 选项卡） */
    public JPanel getPanelUser() {
        return panelUser;
    }
}
