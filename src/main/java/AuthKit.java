import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import controller.AuthController;
import core.DiffService;
import core.HttpRequestHandler;
import core.RequestReplayService;
import core.TextDiffService;
import core.processor.HeaderReplaceProcessor;
import core.processor.ParamReplaceProcessor;
import core.processor.ProcessorChain;
import core.processor.RequestProcessor;
import model.AuthUserModel;
import model.CompareSampleModel;
import model.ConfigModel;
import model.MessageDataModel;
import utils.ApiUtils;
import utils.LogUtils;
import view.MainPanel;
import view.component.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthKit implements BurpExtension {

    private ExecutorService executor;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // 初始化全局 API 访问点
        ApiUtils.INSTANCE.init(montoyaApi);
        montoyaApi.extension().setName("AuthKit");

        // 创建线程池
        executor = Executors.newFixedThreadPool(3);

        // 创建 UI（传入 MontoyaApi 以创建 Burp 原生编辑器）
        MainPanel mainPanel = new MainPanel(montoyaApi);
        montoyaApi.userInterface().registerSuiteTab("AuthKit", mainPanel);

        // 创建配置模型
        ConfigModel configModel = new ConfigModel();

        // 创建处理器链
        List<RequestProcessor> processors = List.of(
                new HeaderReplaceProcessor(),
                new ParamReplaceProcessor()
        );
        ProcessorChain processorChain = new ProcessorChain(processors);

        // 创建核心服务
        RequestReplayService replayService = new RequestReplayService(
                montoyaApi.http(), processorChain);
        TextDiffService diffService = new TextDiffService();

        // 创建控制器
        AuthController controller = new AuthController(configModel, replayService, diffService);

        // 绑定 UI → ConfigModel 同步
        bindConfigSync(mainPanel.getPanelConfiguration(), configModel);

        // 绑定 Clear 按钮
        mainPanel.getPanelConfiguration().getBtnClearTable().addActionListener(e -> {
            controller.clearAll();
            mainPanel.getPanelDataTable().clearAll();
            mainPanel.getPanelMetadataTable().clearAll();
            mainPanel.getPanelCompare().clearAll();
        });

        // 绑定展示指标下拉框切换 → 刷新 DataTable 中鉴权对象列的数据
        mainPanel.getPanelConfiguration().getComboBoxDisplayMetric().addActionListener(e -> {
            String metric = mainPanel.getPanelConfiguration().getSelectedDisplayMetric();
            DataTablePanel dataTable = mainPanel.getPanelDataTable();
            List<String> authColumns = dataTable.getAuthColumns();
            int rowCount = dataTable.getTableModel().getRowCount();
            for (int row = 0; row < rowCount; row++) {
                CompareSampleModel sample = controller.getSample(row);
                if (sample != null) {
                    for (int col = 0; col < authColumns.size(); col++) {
                        dataTable.getTableModel().setValueAt(
                                sample.getValueByAuthName(authColumns.get(col), metric),
                                row, 3 + col);
                    }
                }
            }
        });

        // 绑定筛选功能
        bindFilter(mainPanel, controller);

        // 绑定 DataTable 行选中事件 → 更新 MetadataTable + ComparePanel
        bindTableSelection(mainPanel, controller);

        // 绑定 Diff 按钮事件
        bindDiffButton(mainPanel.getPanelCompare(), diffService);

        // 创建并注册 HttpRequestHandler
        HttpRequestHandler httpHandler = new HttpRequestHandler(configModel, (request, response) -> {
            // 去重检查：相同 method + url 的请求只处理一次
            if (!controller.isNewRequest(request.method(), request.url())) {
                return;
            }
            executor.submit(() -> {
                try {
                    // 构建原始报文数据（含 Montoya 原始对象引用）
                    MessageDataModel originalData = new MessageDataModel(
                            request.toString(), response.toString(),
                            response.statusCode(),
                            response.bodyToString().length(),
                            core.HashService.hash(response.bodyToString()),
                            request, response
                    );

                    // 从 UserPanel 收集启用的用户配置
                    List<AuthUserModel> users = collectUsers(mainPanel.getPanelUser());

                    // 处理请求
                    CompareSampleModel sample = controller.processRequest(
                            request, response, originalData, users);

                    // 更新 UI（在 EDT 线程）
                    SwingUtilities.invokeLater(() -> updateUI(mainPanel, sample));
                } catch (Exception ex) {
                    LogUtils.INSTANCE.error("Error processing request", ex);
                }
            });
        });
        montoyaApi.http().registerHttpHandler(httpHandler);

        // 注册插件卸载时清理线程池
        montoyaApi.extension().registerUnloadingHandler(() -> {
            executor.shutdownNow();
            LogUtils.INSTANCE.info("AuthKit 插件已卸载");
        });

        LogUtils.INSTANCE.info("AuthKit 插件加载成功");
    }

    /**
     * 绑定筛选功能：下拉框切换和输入框实时输入触发筛选
     */
    private void bindFilter(MainPanel mainPanel, AuthController controller) {
        ToolbarPanel toolbar = mainPanel.getPanelToolbar();
        DataTablePanel dataTable = mainPanel.getPanelDataTable();

        // 设置数据提供器：根据筛选类型返回对应的可搜索文本
        dataTable.setDataProvider(modelRow -> {
            CompareSampleModel sample = controller.getSample(modelRow);
            if (sample == null) {
                return "";
            }
            String filterType = toolbar.getSelectedFilterType();
            StringBuilder sb = new StringBuilder();
            for (String authName : sample.getAuthNames()) {
                MessageDataModel data = sample.getMessageData(authName);
                if (data == null) continue;
                switch (filterType) {
                    case "Request Content":
                        if (data.getRequest() != null) sb.append(data.getRequest()).append(" ");
                        break;
                    case "Response Content":
                        if (data.getResponse() != null) sb.append(data.getResponse()).append(" ");
                        break;
                    default:
                        // All: 拼接所有内容
                        if (data.getRequest() != null) sb.append(data.getRequest()).append(" ");
                        if (data.getResponse() != null) sb.append(data.getResponse()).append(" ");
                        break;
                }
            }
            return sb.toString();
        });

        // 触发筛选的公共方法
        Runnable doFilter = () -> {
            String keyword = toolbar.getFilterText();
            String filterType = toolbar.getSelectedFilterType();
            dataTable.applyFilter(filterType, keyword);
        };

        // 输入框实时筛选（DocumentListener）
        toolbar.getTextFieldFilter().getDocument().addDocumentListener(
                new javax.swing.event.DocumentListener() {
                    @Override
                    public void insertUpdate(javax.swing.event.DocumentEvent e) {
                        if (!toolbar.isShowingPlaceholder()) doFilter.run();
                    }
                    @Override
                    public void removeUpdate(javax.swing.event.DocumentEvent e) {
                        if (!toolbar.isShowingPlaceholder()) doFilter.run();
                    }
                    @Override
                    public void changedUpdate(javax.swing.event.DocumentEvent e) {
                        if (!toolbar.isShowingPlaceholder()) doFilter.run();
                    }
                });

        // 下拉框切换时重新筛选
        toolbar.getComboBoxFilterType().addActionListener(e -> doFilter.run());
    }

    /**
     * 绑定 ConfigurationPanel UI 控件变化 → ConfigModel 同步
     */
    private void bindConfigSync(ConfigurationPanel panel, ConfigModel model) {
        panel.getCheckBoxEnabled().addActionListener(e ->
                model.setEnabled(panel.getCheckBoxEnabled().isSelected()));
        panel.getCheckBoxDomainFilter().addActionListener(e ->
                model.setDomainFilterEnabled(panel.getCheckBoxDomainFilter().isSelected()));
        panel.getCheckBoxMethodFilter().addActionListener(e ->
                model.setMethodFilterEnabled(panel.getCheckBoxMethodFilter().isSelected()));
        panel.getCheckBoxPathFilter().addActionListener(e ->
                model.setPathFilterEnabled(panel.getCheckBoxPathFilter().isSelected()));
        panel.getCheckBoxStatusCodeFilter().addActionListener(e ->
                model.setStatusCodeFilterEnabled(panel.getCheckBoxStatusCodeFilter().isSelected()));
        panel.getCheckBoxExtensionFilter().addActionListener(e ->
                model.setExtensionFilterEnabled(panel.getCheckBoxExtensionFilter().isSelected()));

        // 文本区域使用 FocusListener 在失焦时同步
        panel.getTextAreaDomain().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawDomains(panel.getTextAreaDomain().getText());
            }
        });
        panel.getTextFieldMethod().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawFilterMethods(panel.getTextFieldMethod().getText());
            }
        });
        panel.getTextAreaPath().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawFilterPaths(panel.getTextAreaPath().getText());
            }
        });
        panel.getTextFieldStatusCode().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawFilterStatusCodes(panel.getTextFieldStatusCode().getText());
            }
        });
        panel.getTextAreaAuthHeaders().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawAuthHeaders(panel.getTextAreaAuthHeaders().getText());
            }
        });
        panel.getTextFieldExtensionBlacklist().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                model.setRawExtensionBlacklist(panel.getTextFieldExtensionBlacklist().getText());
            }
        });

        // 初始同步默认值
        model.setEnabled(panel.getCheckBoxEnabled().isSelected());
        model.setDomainFilterEnabled(panel.getCheckBoxDomainFilter().isSelected());
        model.setMethodFilterEnabled(panel.getCheckBoxMethodFilter().isSelected());
        model.setPathFilterEnabled(panel.getCheckBoxPathFilter().isSelected());
        model.setStatusCodeFilterEnabled(panel.getCheckBoxStatusCodeFilter().isSelected());
        model.setExtensionFilterEnabled(panel.getCheckBoxExtensionFilter().isSelected());
        model.setRawFilterMethods(panel.getTextFieldMethod().getText());
        model.setRawFilterStatusCodes(panel.getTextFieldStatusCode().getText());
        model.setRawAuthHeaders(panel.getTextAreaAuthHeaders().getText());
        model.setRawExtensionBlacklist(panel.getTextFieldExtensionBlacklist().getText());
    }

    /**
     * 绑定 DataTable 行选中事件
     */
    private void bindTableSelection(MainPanel mainPanel, AuthController controller) {
        mainPanel.getPanelDataTable().getTableData().getSelectionModel()
                .addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) {
                        return;
                    }
                    int selectedRow = mainPanel.getPanelDataTable().getSelectedRow();
                    if (selectedRow < 0) {
                        return;
                    }
                    CompareSampleModel sample = controller.getSample(selectedRow);
                    if (sample == null) {
                        return;
                    }
                    updateMetadataTable(mainPanel.getPanelMetadataTable(), sample);
                    updateComparePanel(mainPanel.getPanelCompare(), sample);
                });
    }

    /**
     * 从 UserPanel 收集所有用户配置
     */
    private List<AuthUserModel> collectUsers(UserPanel userPanel) {
        List<AuthUserModel> users = new ArrayList<>();
        Map<String, AuthUserConfigPanel> panels = userPanel.getUserPanels();
        for (Map.Entry<String, AuthUserConfigPanel> entry : panels.entrySet()) {
            AuthUserConfigPanel configPanel = entry.getValue();
            AuthUserModel user = new AuthUserModel(configPanel.getUserName());
            user.setEnabled(configPanel.isUserEnabled());
            user.setRawHeaders(configPanel.getTextAreaAuthHeaders().getText());
            user.setRawParams(configPanel.getTextAreaParamReplacement().getText());
            users.add(user);
        }
        return users;
    }

    /**
     * 更新 UI：添加新行到 DataTable
     */
    private void updateUI(MainPanel mainPanel, CompareSampleModel sample) {
        DataTablePanel dataTable = mainPanel.getPanelDataTable();
        List<String> authColumns = dataTable.getAuthColumns();
        String metric = mainPanel.getPanelConfiguration().getSelectedDisplayMetric();

        // 构建行数据: # / Method / URL / 各鉴权对象的指标值
        int totalColumns = 3 + authColumns.size();
        Object[] row = new Object[totalColumns];
        row[0] = sample.getId();
        row[1] = sample.getMethod();
        row[2] = sample.getUrl();
        for (int i = 0; i < authColumns.size(); i++) {
            row[3 + i] = sample.getValueByAuthName(authColumns.get(i), metric);
        }
        dataTable.addRow(row);
    }

    /**
     * 更新 MetadataTable
     */
    private void updateMetadataTable(MetadataTablePanel metadataTable, CompareSampleModel sample) {
        for (String authName : metadataTable.getAuthRows()) {
            MessageDataModel data = sample.getMessageData(authName);
            if (data != null) {
                metadataTable.updateRow(authName, data.getStatusCode(),
                        data.getLength(), data.getHash(), 0);
            }
        }
    }

    /**
     * 更新 ComparePanel（使用 Montoya 原始对象设置编辑器内容）
     */
    private void updateComparePanel(ComparePanel comparePanel, CompareSampleModel sample) {
        Map<String, MessagePanel> sourcePanels = comparePanel.getSourcePanels();
        Map<String, MessagePanel> targetPanels = comparePanel.getTargetPanels();

        for (Map.Entry<String, MessagePanel> entry : sourcePanels.entrySet()) {
            MessageDataModel data = sample.getMessageData(entry.getKey());
            if (data != null) {
                entry.getValue().setContent(data.getHttpRequest(), data.getHttpResponse());
            } else {
                entry.getValue().clearContent();
            }
        }
        for (Map.Entry<String, MessagePanel> entry : targetPanels.entrySet()) {
            MessageDataModel data = sample.getMessageData(entry.getKey());
            if (data != null) {
                entry.getValue().setContent(data.getHttpRequest(), data.getHttpResponse());
            } else {
                entry.getValue().clearContent();
            }
        }
    }

    /**
     * 绑定 Diff 按钮事件
     * 根据 Source/Target 当前选中的 Tab 类型（Request/Response）自动对比对应内容
     */
    private void bindDiffButton(ComparePanel comparePanel, DiffService diffService) {
        comparePanel.getBtnDiff().addActionListener(e -> {
            MessagePanel sourcePanel = comparePanel.getSelectedSourcePanel();
            MessagePanel targetPanel = comparePanel.getSelectedTargetPanel();
            if (sourcePanel == null || targetPanel == null) {
                comparePanel.setDiffContent("<html><body><p>请先选择 Source 和 Target 对象</p></body></html>");
                return;
            }

            String sourceName = comparePanel.getSelectedSourceName();
            String targetName = comparePanel.getSelectedTargetName();
            int tabIndex = sourcePanel.getSelectedTabIndex();
            String tabType = tabIndex == 0 ? "Request" : "Response";

            String sourceText;
            String targetText;

            if (tabIndex == 0) {
                // Request Tab
                sourceText = sourcePanel.getRequestEditor().getRequest() != null
                        ? sourcePanel.getRequestEditor().getRequest().toString() : "";
                targetText = targetPanel.getRequestEditor().getRequest() != null
                        ? targetPanel.getRequestEditor().getRequest().toString() : "";
            } else {
                // Response Tab
                sourceText = sourcePanel.getResponseEditor().getResponse() != null
                        ? sourcePanel.getResponseEditor().getResponse().toString() : "";
                targetText = targetPanel.getResponseEditor().getResponse() != null
                        ? targetPanel.getResponseEditor().getResponse().toString() : "";
            }

            String diffBody = diffService.diff(sourceText, targetText);

            // 构建完整 HTML（含标题）
            StringBuilder html = new StringBuilder();
            html.append("<html><body style='font-family:Courier New;font-size:10pt;'>");
            html.append("<b>Diff: ").append(sourceName).append(" (").append(tabType)
                    .append(") &rarr; ").append(targetName).append(" (").append(tabType).append(")</b><br>");
            if (diffBody.isEmpty()) {
                html.append("<br><span style='color:green;'>无差异</span>");
            } else {
                html.append(diffBody);
            }
            html.append("</body></html>");

            comparePanel.setDiffContent(html.toString());
        });
    }
}
