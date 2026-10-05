package view;

import burp.api.montoya.MontoyaApi;
import model.AiConfigModel;
import model.CompareSampleModel;
import model.MessageDataModel;
import utils.I18n;
import view.binding.AiConfigBinder;
import view.component.AiChatPanel;
import view.component.ComparePanel;
import view.component.ConfigurationPanel;
import view.component.DataTablePanel;
import view.component.JwtPanel;
import view.component.MetadataTablePanel;
import view.component.ToolbarPanel;
import view.component.UserPanel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件主面板
 * 作为 Burp Suite Tab 的根面板，使用 JSplitPane 将界面分为左右两部分：
 * 左侧: 工具栏 + 数据表 + 元数据透视表
 * 右侧: 选项卡（View / Configuration / User / JWT / AI）
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
    private final AiChatPanel panelAi;
    /** AI 配置模型（Configuration 选项卡编辑，AI 对话选项卡读取） */
    private final AiConfigModel aiConfigModel;
    /** 数据表选中样本提供者（AI 自动附带上下文使用），由装配根注入 */
    private java.util.function.Supplier<CompareSampleModel> selectedSampleProvider = () -> null;

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
        // AI 配置模型：从本地持久化恢复，配置面板绑定编辑，AI 对话面板共享读取
        this.aiConfigModel = new AiConfigModel();
        aiConfigModel.load();
        AiConfigBinder.bind(panelConfiguration, aiConfigModel);
        this.panelAi = new AiChatPanel(aiConfigModel);
        // AI 数据包来源工具：模型可请求读取 Proxy 历史 / 站点地图，实际报文由用户在弹窗中挑选
        panelAi.setPacketSourceService(new core.PacketSourceService(api));
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

        // 右侧面板: JTabbedPane（View / Configuration / User / JWT / AI）
        tabbedRight.addTab("", panelCompare);
        tabbedRight.addTab("", panelConfiguration);
        tabbedRight.addTab("", panelUser);
        tabbedRight.addTab("", panelJwt);
        tabbedRight.addTab("", panelAi);
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

        // AI 对话自动附带上下文：数据表选中记录 → 合成 Original/Unauthorized/各用户数据包
        panelAi.setContextPacketsSupplier(this::collectAiContextPackets);
    }

    /**
     * 从数据表当前选中记录合成 AI 分析数据包列表（Original / Unauthorized / 各用户）。
     * 依赖 MessageDataModel 中保留的 Montoya 原始对象重建 HttpRequestResponse。
     *
     * @return 数据包列表，无选中记录或无有效报文数据时返回空列表
     */
    private java.util.List<burp.api.montoya.http.message.HttpRequestResponse> collectAiContextPackets() {
        return toPackets(selectedSampleProvider.get());
    }

    /**
     * 把一条比较样本转换为 AI 可分析的数据包列表（Original / Unauthorized / 各用户）。
     * 供数据包工具取数与数据表右键"发送给 AI 分析"复用。
     *
     * @param sample 比较样本，可为 null
     * @return 数据包列表，样本为空或无有效报文时返回空列表
     */
    public static java.util.List<burp.api.montoya.http.message.HttpRequestResponse> toPackets(
            CompareSampleModel sample) {
        List<burp.api.montoya.http.message.HttpRequestResponse> packets = new ArrayList<>();
        if (sample == null) {
            return packets;
        }
        for (String authName : sample.getAuthNamesOrdered()) {
            MessageDataModel data = sample.getMessageData(authName);
            if (data == null || data.getHttpRequest() == null) {
                continue;
            }
            burp.api.montoya.http.message.responses.HttpResponse response =
                    data.getHttpResponse() != null
                            ? data.getHttpResponse()
                            : burp.api.montoya.http.message.responses.HttpResponse.httpResponse("");
            packets.add(burp.api.montoya.http.message.HttpRequestResponse.httpRequestResponse(
                    data.getHttpRequest(), response));
        }
        return packets;
    }

    /**
     * 注入数据表选中样本提供者（由 AuthKit 装配根在创建 DataTableController 后调用）
     *
     * @param provider 返回数据表当前选中样本，无选中返回 null
     */
    public void setSelectedSampleProvider(java.util.function.Supplier<CompareSampleModel> provider) {
        this.selectedSampleProvider = provider;
    }

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        tabbedRight.setTitleAt(0, i18n.text("main", "tab.view"));
        tabbedRight.setTitleAt(1, i18n.text("main", "tab.configuration"));
        tabbedRight.setTitleAt(2, i18n.text("main", "tab.user"));
        tabbedRight.setTitleAt(3, i18n.text("main", "tab.jwt"));
        tabbedRight.setTitleAt(4, i18n.text("main", "tab.ai"));
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

    /** 获取AI对话面板（AI 选项卡） */
    public AiChatPanel getPanelAi() {
        return panelAi;
    }

    /** 获取共享的 AI 配置模型 */
    public AiConfigModel getAiConfigModel() {
        return aiConfigModel;
    }
}
