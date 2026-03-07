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
import utils.I18n;
import utils.LogUtils;
import view.AuthContextMenuProvider;
import view.MainPanel;
import view.component.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class AuthKit implements BurpExtension {

    private static final int DATA_TABLE_FIXED_COLUMN_COUNT = 3;

    private ExecutorService executor;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // 初始化全局 API 访问点
        ApiUtils.INSTANCE.init(montoyaApi);
        montoyaApi.extension().setName("AuthKit");

        printWelcomeBanner();

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
        mainPanel.getPanelConfiguration().getComboBoxDisplayMetric().addActionListener(
                e -> refreshDataTable(mainPanel, controller));

        // 绑定筛选功能
        bindFilter(mainPanel, controller);

        // 绑定 DataTable 行选中事件 → 更新 MetadataTable + ComparePanel
        bindTableSelection(mainPanel, controller);

        // 绑定 Diff 按钮事件
        bindDiffButton(mainPanel.getPanelCompare(), diffService);

        // 注册右键菜单
        registerContextMenu(montoyaApi, mainPanel, controller, configModel, replayService);

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
                    SwingUtilities.invokeLater(() -> refreshDataTable(mainPanel, controller));
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

    private void printWelcomeBanner() {
        ApiUtils.INSTANCE.api().logging().logToOutput(String.format(
                "[   Pwn The Planet, One HTTP at a Time  ]\n" +
                        "[#] Author: youmulijiang\n" +
                        "[#] Github: https://github.com/youmulijiang\n" +
                        "[#] Version: 1.1.0\n"
        ));
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
                    case ToolbarPanel.FILTER_REQUEST_CONTENT:
                        if (data.getRequest() != null) sb.append(data.getRequest()).append(" ");
                        break;
                    case ToolbarPanel.FILTER_RESPONSE_CONTENT:
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
        panel.getCheckBoxScopeProxy().addActionListener(e ->
                model.setProxyScopeEnabled(panel.getCheckBoxScopeProxy().isSelected()));
        panel.getCheckBoxScopeRepeater().addActionListener(e ->
                model.setRepeaterScopeEnabled(panel.getCheckBoxScopeRepeater().isSelected()));
        panel.getCheckBoxScopeIntruder().addActionListener(e ->
                model.setIntruderScopeEnabled(panel.getCheckBoxScopeIntruder().isSelected()));
        panel.getCheckBoxScopeExtensions().addActionListener(e ->
                model.setExtensionsScopeEnabled(panel.getCheckBoxScopeExtensions().isSelected()));
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
        model.setProxyScopeEnabled(panel.getCheckBoxScopeProxy().isSelected());
        model.setRepeaterScopeEnabled(panel.getCheckBoxScopeRepeater().isSelected());
        model.setIntruderScopeEnabled(panel.getCheckBoxScopeIntruder().isSelected());
        model.setExtensionsScopeEnabled(panel.getCheckBoxScopeExtensions().isSelected());
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
        JTable table = mainPanel.getPanelDataTable().getTableData();

        table.getSelectionModel()
                .addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) {
                        return;
                    }
                    updateSelectedSample(mainPanel, controller,
                            mainPanel.getPanelDataTable().getSelectedRow());
                });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleTableCellClick(mainPanel, controller,
                        table.rowAtPoint(e.getPoint()),
                        table.columnAtPoint(e.getPoint()));
            }
        });
    }

    /** 更新当前选中样本对应的 Metadata 和 Compare 区域 */
    private void updateSelectedSample(MainPanel mainPanel, AuthController controller, int modelRow) {
        if (modelRow < 0) {
            return;
        }

        CompareSampleModel sample = controller.getSample(modelRow);
        if (sample == null) {
            return;
        }

        updateMetadataTable(mainPanel.getPanelMetadataTable(), sample);
        updateComparePanel(mainPanel.getPanelCompare(), sample);
    }

    /** 处理 DataTable 单元格点击联动 */
    private void handleTableCellClick(MainPanel mainPanel, AuthController controller,
                                      int viewRow, int viewColumn) {
        if (viewRow < 0 || viewColumn < 0) {
            return;
        }

        JTable table = mainPanel.getPanelDataTable().getTableData();
        int modelRow = table.convertRowIndexToModel(viewRow);
        updateSelectedSample(mainPanel, controller, modelRow);

        int modelColumn = table.convertColumnIndexToModel(viewColumn);
        if (modelColumn < DATA_TABLE_FIXED_COLUMN_COUNT) {
            return;
        }

        String authName = mainPanel.getPanelDataTable().getAuthColumnKeyAtModelIndex(modelColumn);
        if (authName == null) {
            return;
        }
        ComparePanel comparePanel = mainPanel.getPanelCompare();
        if (comparePanel.selectTargetTab(authName)) {
            comparePanel.selectTargetMessageTab(MessagePanel.RESPONSE_TAB_INDEX);
        }
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
     * 刷新 DataTable：始终以当前 samples 快照和当前选中 metric 为准
     */
    private void refreshDataTable(MainPanel mainPanel, AuthController controller) {
        DataTablePanel dataTable = mainPanel.getPanelDataTable();
        DefaultTableModel tableModel = dataTable.getTableModel();
        List<String> authColumns = dataTable.getAuthColumns();
        String metric = mainPanel.getPanelConfiguration().getSelectedDisplayMetric();
        List<CompareSampleModel> samples = controller.getSamples();

        tableModel.setRowCount(samples.size());
        for (int row = 0; row < samples.size(); row++) {
            Object[] rowData = buildDataTableRow(samples.get(row), authColumns, metric);
            for (int col = 0; col < rowData.length; col++) {
                if (!Objects.equals(tableModel.getValueAt(row, col), rowData[col])) {
                    tableModel.setValueAt(rowData[col], row, col);
                }
            }
        }
    }

    /**
     * 构建 DataTable 单行数据
     */
    private Object[] buildDataTableRow(CompareSampleModel sample, List<String> authColumns,
                                       String metric) {
        int totalColumns = 3 + authColumns.size();
        Object[] row = new Object[totalColumns];
        row[0] = sample.getId();
        row[1] = sample.getMethod();
        row[2] = sample.getUrl();
        for (int i = 0; i < authColumns.size(); i++) {
            row[3 + i] = sample.getValueByAuthName(authColumns.get(i), metric);
        }
        return row;
    }

    /**
     * 更新 MetadataTable
     */
    private void updateMetadataTable(MetadataTablePanel metadataTable, CompareSampleModel sample) {
        for (String authName : metadataTable.getAuthRows()) {
            MessageDataModel data = sample.getMessageData(authName);
            if (data != null) {
                metadataTable.updateRow(authName, data.getStatusCode(),
                        data.getLength(), data.getHash(),
                        data.getAttributeCount(), data.getNote(),
                        data.getRank());
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
            I18n i18n = I18n.getInstance();
            MessagePanel sourcePanel = comparePanel.getSelectedSourcePanel();
            MessagePanel targetPanel = comparePanel.getSelectedTargetPanel();
            if (sourcePanel == null || targetPanel == null) {
                comparePanel.setDiffContent("<html><body><p>"
                        + i18n.text("compare", "message.selectSourceTarget")
                        + "</p></body></html>");
                return;
            }

            String sourceName = comparePanel.getSelectedSourceName();
            String targetName = comparePanel.getSelectedTargetName();
            int tabIndex = sourcePanel.getSelectedTabIndex();
            String tabType = tabIndex == MessagePanel.REQUEST_TAB_INDEX
                    ? i18n.text("message", "tab.request")
                    : i18n.text("message", "tab.response");

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
            html.append("<b>")
                    .append(i18n.format("compare", "title.diff",
                            i18n.translateAuthObjectName(sourceName), tabType,
                            i18n.translateAuthObjectName(targetName), tabType))
                    .append("</b><br>");
            if (diffBody.isEmpty()) {
                html.append("<br><span style='color:green;'>")
                        .append(i18n.text("compare", "message.noDiff"))
                        .append("</span>");
            } else {
                html.append(diffBody);
            }
            html.append("</body></html>");

            comparePanel.setDiffContent(html.toString());
        });
    }

    /**
     * 注册右键菜单，支持用户在 Proxy/Repeater 等模块中右键发送请求到 AuthKit 进行主动鉴权测试
     */
    private void registerContextMenu(MontoyaApi montoyaApi, MainPanel mainPanel,
                                      AuthController controller, ConfigModel configModel,
                                      RequestReplayService replayService) {
        // 用户名称提供者：从 UserPanel 获取当前已配置的用户名称
        java.util.function.Supplier<List<String>> userNamesSupplier = () ->
                new ArrayList<>(mainPanel.getPanelUser().getUserPanels().keySet());

        // Send 处理回调：主动发包，由插件内部处理所有鉴权用户
        java.util.function.Consumer<List<burp.api.montoya.http.message.HttpRequestResponse>>
                sendHandler = (selectedItems) -> {
            for (burp.api.montoya.http.message.HttpRequestResponse reqResp : selectedItems) {
                if (reqResp.request() == null) {
                    continue;
                }
                executor.submit(() -> {
                    try {
                        processContextMenuRequest(reqResp,
                                mainPanel, controller, replayService);
                    } catch (Exception ex) {
                        LogUtils.INSTANCE.error("Error processing context menu request", ex);
                    }
                });
            }
        };

        // Extract 处理回调：将提取到的认证头文本填入指定用户的配置
        java.util.function.BiConsumer<String, String> extractHandler = (authText, userName) -> {
            SwingUtilities.invokeLater(() -> {
                UserPanel userPanel = mainPanel.getPanelUser();
                AuthUserConfigPanel configPanel = userPanel.getUserPanel(userName);
                if (configPanel != null) {
                    configPanel.getTextAreaAuthHeaders().setText(authText);
                    LogUtils.INSTANCE.info("Extracted auth headers to user: " + userName);
                }
            });
        };

        // 新建用户回调：通过 UserPanel 添加用户并返回名称
        java.util.function.Supplier<String> createUserHandler = () -> {
            // 需要在 EDT 线程中执行 UI 操作，但 Supplier 需要同步返回结果
            // 使用 invokeAndWait 确保在 EDT 中创建用户
            final String[] newName = {null};
            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    AuthUserConfigPanel panel = mainPanel.getPanelUser().addUser();
                    newName[0] = panel.getUserName();
                } else {
                    SwingUtilities.invokeAndWait(() -> {
                        AuthUserConfigPanel panel = mainPanel.getPanelUser().addUser();
                        newName[0] = panel.getUserName();
                    });
                }
            } catch (Exception ex) {
                LogUtils.INSTANCE.error("Error creating new user", ex);
            }
            return newName[0];
        };

        // 插件启用状态提供者
        java.util.function.Supplier<Boolean> enabledSupplier =
                () -> mainPanel.getPanelConfiguration().getCheckBoxEnabled().isSelected();

        AuthContextMenuProvider contextMenuProvider =
                new AuthContextMenuProvider(userNamesSupplier, enabledSupplier,
                        sendHandler, extractHandler, createUserHandler);
        montoyaApi.userInterface().registerContextMenuItemsProvider(contextMenuProvider);
    }

    /**
     * 处理右键菜单发送的请求：构建原始数据、收集所有鉴权用户重放请求、更新 UI
     */
    private void processContextMenuRequest(
            burp.api.montoya.http.message.HttpRequestResponse reqResp,
            MainPanel mainPanel, AuthController controller,
            RequestReplayService replayService) {

        burp.api.montoya.http.message.requests.HttpRequest request = reqResp.request();
        burp.api.montoya.http.message.responses.HttpResponse response = reqResp.response();

        // 如果没有响应，先发送请求获取响应
        if (response == null) {
            response = replayService.sendRaw(request);
        }

        // 去重检查
        if (!controller.isNewRequest(request.method(), request.url())) {
            return;
        }

        // 构建原始报文数据
        MessageDataModel originalData = new MessageDataModel(
                request.toString(), response.toString(),
                response.statusCode(),
                response.bodyToString().length(),
                core.HashService.hash(response.bodyToString()),
                request, response
        );

        // 收集所有鉴权用户
        List<AuthUserModel> users = collectUsers(mainPanel.getPanelUser());

        // 处理请求
        final burp.api.montoya.http.message.responses.HttpResponse finalResponse = response;
        CompareSampleModel sample = controller.processRequest(
                request, finalResponse, originalData, users);

        // 更新 UI
        SwingUtilities.invokeLater(() -> refreshDataTable(mainPanel, controller));
    }
}
