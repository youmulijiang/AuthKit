package view;

import burp.api.montoya.MontoyaApi;
import utils.I18n;
import view.component.ComparePanel;
import view.component.ConfigurationPanel;
import view.component.DataTablePanel;
import view.component.JwtPanel;
import view.component.MetadataTablePanel;
import view.component.ToolbarPanel;
import view.component.UserPanel;

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
    private final ConfigurationPanel panelConfiguration;
    private final UserPanel panelUser;
    private final JwtPanel panelJwt;

    /**
     * 构造主面板
     *
     * @param api Montoya API 实例，用于创建 Burp 原生编辑器
     */
    public MainPanel(MontoyaApi api) {
        this.panelToolbar = new ToolbarPanel.Builder().build();
        this.panelDataTable = new DataTablePanel.Builder().build();
        this.panelMetadataTable = new MetadataTablePanel.Builder().build();
        this.panelCompare = new ComparePanel.Builder(api).build();
        this.panelConfiguration = new ConfigurationPanel.Builder().build();
        this.panelUser = new UserPanel.Builder().build();
        this.panelJwt = new JwtPanel();
        this.tabbedRight = new JTabbedPane();
        initLayout();
        bindEvents();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
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
        tabbedRight.addTab("", panelCompare);
        tabbedRight.addTab("", panelConfiguration);
        tabbedRight.addTab("", panelUser);
        tabbedRight.addTab("", panelJwt);
        tabbedRight.setSelectedComponent(panelConfiguration);

        // 左右水平分割
        JSplitPane splitMain = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                panelLeft,
                tabbedRight
        );
        splitMain.setResizeWeight(0.45);

        add(splitMain, BorderLayout.CENTER);
    }

    /** 绑定事件：UserPanel 添加/删除用户时联动 DataTablePanel 和 MetadataTablePanel */
    private void bindEvents() {
        panelUser.onUserAdded(name -> {
            panelDataTable.addAuthColumn(name);
            panelMetadataTable.addAuthRow(name);
            panelCompare.addAuthObject(name);
        });

        panelUser.onUserRemoved(name -> {
            panelDataTable.removeAuthColumn(name);
            panelMetadataTable.removeAuthRow(name);
            panelCompare.removeAuthObject(name);
        });

        panelUser.onUserRenamed((oldName, newName) -> {
            panelDataTable.renameAuthColumn(oldName, newName);
            panelMetadataTable.renameAuthRow(oldName, newName);
            panelCompare.renameAuthObject(oldName, newName);
        });
    }

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        tabbedRight.setTitleAt(0, i18n.text("main", "tab.view"));
        tabbedRight.setTitleAt(1, i18n.text("main", "tab.configuration"));
        tabbedRight.setTitleAt(2, i18n.text("main", "tab.user"));
        tabbedRight.setTitleAt(3, i18n.text("main", "tab.jwt"));
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
    public ConfigurationPanel getPanelConfiguration() {
        return panelConfiguration;
    }

    /** 获取用户面板（User 选项卡） */
    public UserPanel getPanelUser() {
        return panelUser;
    }

    /** 获取JWT面板（JWT 选项卡） */
    public JwtPanel getPanelJwt() {
        return panelJwt;
    }
}
