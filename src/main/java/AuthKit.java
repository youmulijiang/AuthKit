import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import controller.AuthController;
import core.AuthResultExportService;
import core.Bypass403PayloadService;
import core.Bypass403RequestVariant;
import core.Bypass403ScanResult;
import core.IdorPayloadService;
import core.IdorScanResult;
import core.IdorScanVariant;
import core.DiffService;
import core.FakeIpIntruderHttpHandler;
import core.FakeIpPayloadGeneratorProvider;
import core.FakeIpService;
import core.HttpRequestHandler;
import core.RequestReplayService;
import core.TextDiffService;
import core.service.AuthHistoryService;
import core.service.Bypass403ScanService;
import core.service.ConfigRequestFilter;
import core.service.IdorScanService;
import core.processor.HeaderReplaceProcessor;
import core.processor.ParamReplaceProcessor;
import core.processor.ProcessorChain;
import core.processor.RequestProcessor;
import model.AuthUserModel;
import model.CompareSampleModel;
import model.ConfigModel;
import model.MessageDataModel;
import utils.ApiUtils;
import utils.HttpTextUtils;
import utils.I18n;
import utils.LogUtils;
import view.AuthContextMenuProvider;
import view.MainPanel;
import view.binding.ConfigBinder;
import view.binding.UserPanelBinder;
import view.component.*;
import view.dialog.AuthHistorySelectDialog;
import view.dialog.Bypass403ScanDialog;
import view.dialog.IdorScanDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class AuthKit implements BurpExtension {

    private static final String AuthKit_Version = "1.9.0";
    private static final int AUTO_DIFF_DEBOUNCE_MS = 180;
    private static final int DATA_TABLE_FIXED_COLUMN_COUNT = 3;
    private static final AtomicBoolean WELCOME_BANNER_PRINTED = new AtomicBoolean(false);

    private ExecutorService executor;
    private ExecutorService diffExecutor;

    /** Montoya API 引用，供右键菜单扫描等流程使用 */
    private MontoyaApi montoyaApi;

    /** 缓存当前已展示在 ComparePanel 中的 sample ID，避免重复点击同一行时重复加载 */
    private int lastCompareSampleId = -1;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // 初始化全局 API 访问点
        ApiUtils.INSTANCE.init(montoyaApi);
        this.montoyaApi = montoyaApi;
        montoyaApi.extension().setName("AuthKit");

        printWelcomeBanner();

        // 创建线程池
        executor = Executors.newFixedThreadPool(3);
        diffExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "authkit-diff-worker");
            thread.setDaemon(true);
            return thread;
        });

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
        AuthResultExportService exportService = new AuthResultExportService();
        FakeIpService fakeIpService = new FakeIpService();

        // 创建控制器
        AuthController controller = new AuthController(configModel, replayService, diffService);

        // 绑定 UI → ConfigModel 同步
        ConfigBinder.bind(mainPanel.getPanelConfiguration(), configModel);

        // 绑定 Clear 按钮
        mainPanel.getPanelConfiguration().getBtnClearTable().addActionListener(e -> {
            controller.clearAll();
            mainPanel.getPanelDataTable().clearAll();
            mainPanel.getPanelMetadataTable().clearAll();
            mainPanel.getPanelCompare().clearAll();
            lastCompareSampleId = -1;
        });

        // 绑定展示指标下拉框切换 → 刷新 DataTable 中鉴权对象列的数据
        mainPanel.getPanelConfiguration().getComboBoxDisplayMetric().addActionListener(
                e -> refreshDataTable(mainPanel, controller));

        // 绑定仅显示越权行勾选框
        mainPanel.getPanelConfiguration().getCheckBoxUnauthorizedOnly().addActionListener(e ->
                mainPanel.getPanelDataTable().setUnauthorizedOnly(
                        mainPanel.getPanelConfiguration().getCheckBoxUnauthorizedOnly().isSelected()));

        // 绑定筛选功能
        bindFilter(mainPanel, controller);

        // 绑定 DataTable 行选中事件 → 更新 MetadataTable + ComparePanel
        bindTableSelection(mainPanel, controller);

        // 绑定 DataTable 多选右键动作 → 导出 / 复制 URL
        bindDataTableSelectionActions(mainPanel, controller, exportService);

        // 绑定自动 Diff 事件（懒加载，tab 切换时自动触发）
        bindAutoDiff(mainPanel.getPanelCompare(), diffService);

        // 注册右键菜单
        registerContextMenu(montoyaApi, mainPanel, controller, configModel, replayService, fakeIpService);

        // 注册 Intruder 随机 IP payload 生成器（fakeIpPayloads）
        montoyaApi.intruder().registerPayloadGeneratorProvider(
                new FakeIpPayloadGeneratorProvider(fakeIpService));
        // 注册 Intruder 请求发送前处理器：每个爆破数据包自动注入随机伪造 IP 头
        montoyaApi.http().registerHttpHandler(new FakeIpIntruderHttpHandler(fakeIpService));

        // 注册 JWT 编辑器 Provider（在 Burp 请求编辑器中添加 JWT 选项卡）
        montoyaApi.userInterface().registerHttpRequestEditorProvider(new JwtRequestEditorProvider(montoyaApi));

        // 创建并注册 HttpRequestHandler
        ConfigRequestFilter requestFilter = new ConfigRequestFilter(configModel);
        HttpRequestHandler httpHandler = new HttpRequestHandler(requestFilter, (request, response) -> {
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
                    originalData.setContentType(response.headerValue("Content-Type") != null
                            ? response.headerValue("Content-Type") : "");
                    // 从拦截响应的 annotations 读取 Burp 备注
                    if (response.annotations() != null && response.annotations().hasNotes()) {
                        originalData.setNote(response.annotations().notes());
                    }

                    // 从 UserPanel 收集启用的用户配置
                    List<AuthUserModel> users = UserPanelBinder.collectUsers(mainPanel.getPanelUser());

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
            if (diffExecutor != null) {
                diffExecutor.shutdownNow();
            }
            LogUtils.INSTANCE.info("AuthKit 插件已卸载");
        });

        LogUtils.INSTANCE.info("AuthKit 插件加载成功");
    }

    private void printWelcomeBanner() {
        if (!WELCOME_BANNER_PRINTED.compareAndSet(false, true)) {
            return;
        }
        ApiUtils.INSTANCE.api().logging().logToOutput(String.format(
                "[   Pwn The Planet, One HTTP at a Time  ]\n" +
                        "[#] Author: youmulijiang\n" +
                        "[#] Github: https://github.com/youmulijiang\n" +
                        "[#] Version: %s\n",AuthKit_Version
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
                    case ToolbarPanel.FILTER_HOST:
                        if (data.getRequest() != null) {
                            String host = HttpTextUtils.extractHost(data.getRequest());
                            if (host != null) sb.append(host).append(" ");
                        }
                        break;
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

    /** 绑定 DataTable 多选右键动作。 */
    private void bindDataTableSelectionActions(MainPanel mainPanel, AuthController controller,
                                               AuthResultExportService exportService) {
        DataTablePanel dataTable = mainPanel.getPanelDataTable();
        dataTable.setSelectionActionHandler(new DataTablePanel.SelectionActionHandler() {
            @Override
            public void exportCsv(List<Integer> modelRows) {
                exportSelectedRows(mainPanel, controller, exportService, modelRows, ExportFormat.CSV);
            }

            @Override
            public void exportHtml(List<Integer> modelRows) {
                exportSelectedRows(mainPanel, controller, exportService, modelRows, ExportFormat.HTML);
            }

            @Override
            public void copyUrls(List<Integer> modelRows) {
                copySelectedUrls(mainPanel, controller, modelRows);
            }
        });
    }

    private void exportSelectedRows(MainPanel mainPanel, AuthController controller,
                                    AuthResultExportService exportService,
                                    List<Integer> modelRows, ExportFormat format) {
        List<CompareSampleModel> samples = collectSelectedSamples(controller, modelRows);
        if (samples.isEmpty()) {
            showDataTableMessage(mainPanel, "message.noSelection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        I18n i18n = I18n.getInstance();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(i18n.text("data_table", format.dialogTitleKey));
        chooser.setSelectedFile(new File("authkit-export." + format.extension));
        if (chooser.showSaveDialog(mainPanel) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path path = ensureExtension(chooser.getSelectedFile(), format.extension);
        String content = format == ExportFormat.CSV
                ? exportService.toCsv(samples, mainPanel.getPanelDataTable().getAuthColumns())
                : exportService.toHtml(samples, mainPanel.getPanelDataTable().getAuthColumns());
        try {
            exportService.write(path, content);
            JOptionPane.showMessageDialog(mainPanel,
                    i18n.format("data_table", "message.exportSuccess", path),
                    i18n.text("data_table", "title.export"), JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            LogUtils.INSTANCE.error("Failed to export selected AuthKit results", ex);
            JOptionPane.showMessageDialog(mainPanel,
                    i18n.format("data_table", "message.exportFailed", ex.getMessage()),
                    i18n.text("data_table", "title.export"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void copySelectedUrls(MainPanel mainPanel, AuthController controller, List<Integer> modelRows) {
        List<CompareSampleModel> samples = collectSelectedSamples(controller, modelRows);
        if (samples.isEmpty()) {
            showDataTableMessage(mainPanel, "message.noSelection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        StringBuilder urls = new StringBuilder();
        for (CompareSampleModel sample : samples) {
            if (sample.getUrl() != null && !sample.getUrl().isBlank()) {
                if (!urls.isEmpty()) {
                    urls.append(System.lineSeparator());
                }
                urls.append(sample.getUrl());
            }
        }
        if (urls.isEmpty()) {
            showDataTableMessage(mainPanel, "message.noUrl", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(urls.toString()), null);
        showDataTableMessage(mainPanel, "message.copySuccess", JOptionPane.INFORMATION_MESSAGE);
    }

    private List<CompareSampleModel> collectSelectedSamples(AuthController controller, List<Integer> modelRows) {
        List<CompareSampleModel> samples = new ArrayList<>();
        for (Integer modelRow : modelRows) {
            CompareSampleModel sample = modelRow != null ? controller.getSample(modelRow) : null;
            if (sample != null) {
                samples.add(sample);
            }
        }
        return samples;
    }

    private Path ensureExtension(File selectedFile, String extension) {
        String path = selectedFile.getPath();
        if (!path.toLowerCase().endsWith("." + extension)) {
            path = path + "." + extension;
        }
        return Path.of(path);
    }

    private void showDataTableMessage(MainPanel mainPanel, String key, int messageType) {
        JOptionPane.showMessageDialog(mainPanel,
                I18n.getInstance().text("data_table", key),
                I18n.getInstance().text("data_table", "title.selection"), messageType);
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

        // 数据更新后重新应用越权过滤，确保基于最新指标值生效
        boolean unauthorizedOnly = mainPanel.getPanelConfiguration().getCheckBoxUnauthorizedOnly().isSelected();
        dataTable.setUnauthorizedOnly(unauthorizedOnly);
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
            row[3 + i] = sample.getDisplayValueByAuthName(authColumns.get(i), metric);
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
                        data.getRank(), data.getContentType());
            }
        }
    }

    /**
     * 更新 ComparePanel（使用 Montoya 原始对象设置编辑器内容）
     */
    private void updateComparePanel(ComparePanel comparePanel, CompareSampleModel sample) {
        // 缓存命中：同一条记录重复点击时跳过，避免重复设置编辑器内容和触发 diff
        if (sample.getId() == lastCompareSampleId) {
            return;
        }
        lastCompareSampleId = sample.getId();

        Map<String, MessagePanel> sourcePanels = comparePanel.getSourcePanels();
        Map<String, MessagePanel> targetPanels = comparePanel.getTargetPanels();

        for (Map.Entry<String, MessagePanel> entry : sourcePanels.entrySet()) {
            MessageDataModel data = sample.getMessageData(entry.getKey());
            if (data != null) {
                entry.getValue().setContent(data.getHttpRequest(), data.getHttpResponse(),
                        data.getRequest(), data.getResponse());
            } else {
                entry.getValue().clearContent();
            }
        }
        for (Map.Entry<String, MessagePanel> entry : targetPanels.entrySet()) {
            MessageDataModel data = sample.getMessageData(entry.getKey());
            if (data != null) {
                entry.getValue().setContent(data.getHttpRequest(), data.getHttpResponse(),
                        data.getRequest(), data.getResponse());
            } else {
                entry.getValue().clearContent();
            }
        }

        comparePanel.requestDiff();
    }

    /**
     * 绑定自动 Diff 事件（懒加载）
     * 当 Source/Target 选项卡或内部 Request/Response 页签切换时，自动在后台线程执行 Diff，
     * 比较过程中显示进度条，完成后更新 Diff 展示区。
     */
    private void bindAutoDiff(ComparePanel comparePanel, DiffService diffService) {
        AtomicInteger requestVersion = new AtomicInteger();
        AtomicReference<DiffContext> pendingContextRef = new AtomicReference<>();
        AtomicBoolean diffRunning = new AtomicBoolean(false);

        Runnable scheduleLatestDiff = new Runnable() {
            @Override
            public void run() {
                if (!diffRunning.compareAndSet(false, true)) {
                    return;
                }

                DiffContext context = pendingContextRef.getAndSet(null);
                if (context == null) {
                    diffRunning.set(false);
                    comparePanel.hideProgress();
                    return;
                }

                comparePanel.showProgress();
                boolean sideBySide = comparePanel.isSideBySideMode();
                diffExecutor.submit(() -> {
                    String diffBody = "";
                    DiffService.SideBySideDiffResult sideBySideResult = null;
                    Exception error = null;
                    try {
                        if (sideBySide) {
                            sideBySideResult = diffService.diffSideBySide(
                                    context.sourceText(), context.targetText());
                        } else {
                            diffBody = diffService.diff(context.sourceText(), context.targetText());
                        }
                    } catch (Exception ex) {
                        error = ex;
                    }

                    String finalDiffBody = diffBody;
                    DiffService.SideBySideDiffResult finalSideBySideResult = sideBySideResult;
                    Exception finalError = error;
                    SwingUtilities.invokeLater(() -> {
                        try {
                            if (context.version() == requestVersion.get()) {
                                if (finalError != null) {
                                    comparePanel.setDiffContent("<html><body><p>Diff error: "
                                            + finalError.getMessage() + "</p></body></html>");
                                } else if (sideBySide && finalSideBySideResult != null) {
                                    String wrapL = buildSideBySideHtml(finalSideBySideResult.leftHtml());
                                    String wrapR = buildSideBySideHtml(finalSideBySideResult.rightHtml());
                                    comparePanel.setDiffContentSideBySide(wrapL, wrapR);
                                } else {
                                    comparePanel.setDiffContent(buildDiffHtml(
                                            context.sourceName(), context.targetName(),
                                            context.tabType(), finalDiffBody));
                                }
                            }
                        } finally {
                            diffRunning.set(false);
                            if (pendingContextRef.get() != null) {
                                this.run();
                            } else {
                                comparePanel.hideProgress();
                            }
                        }
                    });
                });
            }
        };

        Timer debounceTimer = new Timer(AUTO_DIFF_DEBOUNCE_MS, e -> {
            DiffContext context = pendingContextRef.get();
            if (context == null) {
                return;
            }

            comparePanel.showProgress();
            scheduleLatestDiff.run();
        });
        debounceTimer.setRepeats(false);

        comparePanel.setDiffCallback(panel -> {
            I18n i18n = I18n.getInstance();
            MessagePanel sourcePanel = panel.getSelectedSourcePanel();
            MessagePanel targetPanel = panel.getSelectedTargetPanel();
            if (sourcePanel == null || targetPanel == null) {
                debounceTimer.stop();
                requestVersion.incrementAndGet();
                pendingContextRef.set(null);
                panel.hideProgress();
                panel.setDiffContent("<html><body><p>"
                        + i18n.text("compare", "message.selectSourceTarget")
                        + "</p></body></html>");
                return;
            }

            String sourceName = panel.getSelectedSourceName();
            String targetName = panel.getSelectedTargetName();
            int tabIndex = sourcePanel.getSelectedTabIndex();
            String tabType = tabIndex == MessagePanel.REQUEST_TAB_INDEX
                    ? i18n.text("message", "tab.request")
                    : i18n.text("message", "tab.response");

            String sourceText = tabIndex == MessagePanel.REQUEST_TAB_INDEX
                    ? sourcePanel.getRequestText()
                    : sourcePanel.getResponseText();
            String targetText = tabIndex == MessagePanel.REQUEST_TAB_INDEX
                    ? targetPanel.getRequestText()
                    : targetPanel.getResponseText();

            pendingContextRef.set(new DiffContext(
                    requestVersion.incrementAndGet(),
                    sourceName,
                    targetName,
                    tabType,
                    sourceText,
                    targetText));
            debounceTimer.restart();
        });
    }

    private String buildDiffHtml(String sourceName, String targetName, String tabType, String diffBody) {
        I18n i18n = I18n.getInstance();
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
        return html.toString();
    }

    private String buildSideBySideHtml(String bodyHtml) {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family:Courier New;font-size:10pt;'>");
        html.append(bodyHtml);
        html.append("</body></html>");
        return html.toString();
    }

    private record DiffContext(int version, String sourceName, String targetName,
                               String tabType, String sourceText, String targetText) {
    }

    private enum ExportFormat {
        CSV("csv", "dialog.exportCsv.title"),
        HTML("html", "dialog.exportHtml.title");

        private final String extension;
        private final String dialogTitleKey;

        ExportFormat(String extension, String dialogTitleKey) {
            this.extension = extension;
            this.dialogTitleKey = dialogTitleKey;
        }
    }

    /**
     * 注册右键菜单，支持用户在 Proxy/Repeater 等模块中右键发送请求到 AuthKit 进行主动鉴权测试
     */
    private void registerContextMenu(MontoyaApi montoyaApi, MainPanel mainPanel,
                                      AuthController controller, ConfigModel configModel,
                                      RequestReplayService replayService,
                                      FakeIpService fakeIpService) {
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

        // 新建用户回调：弹出新建用户对话框，用户确认后创建用户并返回名称
        java.util.function.Function<String, String> createUserHandler = (authText) -> {
            final String[] newName = {null};
            try {
                Runnable showDialog = () -> {
                    // 生成默认名称
                    UserPanel userPanel = mainPanel.getPanelUser();
                    String defaultName = "User" + (userPanel.getUserCount() + 1);

                    // 弹出新建用户对话框
                    NewUserDialog.UserConfig config =
                            NewUserDialog.show(mainPanel, defaultName, authText);
                    if (config == null) {
                        return; // 用户取消
                    }

                    // 创建用户并应用对话框中的配置
                    AuthUserConfigPanel panel = userPanel.addUser();
                    // addUser() 使用自动生成的名称（如 "User1"），需要获取该名称
                    String autoName = panel.getUserName();
                    panel.getCheckBoxEnabled().setSelected(config.enabled());
                    panel.getTextAreaAuthHeaders().setText(config.authHeaders());
                    panel.getTextAreaParamReplacement().setText(config.paramReplacement());
                    // 如果用户在对话框中输入了不同的名称，执行重命名以同步到所有面板
                    if (!autoName.equals(config.name())) {
                        userPanel.renameUser(autoName, config.name());
                    }
                    newName[0] = config.name();
                    LogUtils.INSTANCE.info("Created new user via dialog: " + config.name());
                };

                if (SwingUtilities.isEventDispatchThread()) {
                    showDialog.run();
                } else {
                    SwingUtilities.invokeAndWait(showDialog);
                }
            } catch (Exception ex) {
                LogUtils.INSTANCE.error("Error creating new user via dialog", ex);
            }
            return newName[0];
        };

        // 插件启用状态提供者
        java.util.function.Supplier<Boolean> enabledSupplier =
                () -> mainPanel.getPanelConfiguration().getCheckBoxEnabled().isSelected();

        // 开启插件的回调：点击勾选框触发 doClick，等效于用户手动勾选 Enable Plugin
        Runnable enablePluginHandler = () -> {
            javax.swing.JCheckBox cb = mainPanel.getPanelConfiguration().getCheckBoxEnabled();
            if (!cb.isSelected()) {
                cb.doClick();
            }
        };

        java.util.function.Consumer<burp.api.montoya.http.message.requests.HttpRequest>
                sendToRepeaterHandler = request -> montoyaApi.repeater().sendToRepeater(request, "AuthKit Fake IP");
        java.util.function.Consumer<burp.api.montoya.http.message.requests.HttpRequest>
                sendToIntruderHandler = request -> montoyaApi.intruder().sendToIntruder(request, "AuthKit Fake IP");
        Bypass403PayloadService bypass403PayloadService = new Bypass403PayloadService();
        Bypass403ScanService bypass403ScanService =
                new Bypass403ScanService(montoyaApi, bypass403PayloadService);
        java.util.function.Consumer<List<burp.api.montoya.http.message.HttpRequestResponse>>
                bypass403ScanHandler = selectedItems -> runBypass403Scan(
                mainPanel, selectedItems, bypass403ScanService);

        IdorPayloadService idorPayloadService = new IdorPayloadService();
        IdorScanService idorScanService = new IdorScanService(montoyaApi, idorPayloadService);
        java.util.function.Consumer<List<burp.api.montoya.http.message.HttpRequestResponse>>
                idorScanHandler = selectedItems -> runIdorScan(
                mainPanel, selectedItems, idorScanService);

        AuthHistoryService authHistoryService = new AuthHistoryService(montoyaApi);

        // 更新为最新鉴权字段回调
        java.util.function.BiConsumer<burp.api.montoya.ui.contextmenu.ContextMenuEvent,
                List<burp.api.montoya.http.message.HttpRequestResponse>> updateToLatestAuthHandler =
                (event, items) -> authHistoryService.updateToLatestAuth(event, items);

        // 从历史选择鉴权字段回调
        java.util.function.BiConsumer<burp.api.montoya.ui.contextmenu.ContextMenuEvent,
                List<burp.api.montoya.http.message.HttpRequestResponse>> selectFromHistoryHandler =
                (event, items) -> authHistoryService.selectFromHistory(mainPanel, event, items);

        // 删除所有鉴权字段回调
        java.util.function.BiConsumer<burp.api.montoya.ui.contextmenu.ContextMenuEvent,
                List<burp.api.montoya.http.message.HttpRequestResponse>> deleteAuthHandler =
                (event, items) -> authHistoryService.deleteAuthFields(event, items);

        AuthContextMenuProvider contextMenuProvider =
                new AuthContextMenuProvider(userNamesSupplier, enabledSupplier, enablePluginHandler,
                        sendHandler, extractHandler, createUserHandler, fakeIpService,
                        sendToRepeaterHandler, sendToIntruderHandler, bypass403ScanHandler,
                        updateToLatestAuthHandler, selectFromHistoryHandler, deleteAuthHandler);
        contextMenuProvider.setIdorScanHandler(idorScanHandler);
        montoyaApi.userInterface().registerContextMenuItemsProvider(contextMenuProvider);
    }

    private void runBypass403Scan(JComponent parent,
                                  List<burp.api.montoya.http.message.HttpRequestResponse> selectedItems,
                                  Bypass403ScanService scanService) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }
        Bypass403ScanDialog dialog = new Bypass403ScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        dialog.setStartHandler((threadCount, followRedirects) -> executor.submit(() ->
                scanService.executeScan(selectedItems, dialog, threadCount, followRedirects,
                        scanPoolRef, stopRequested)));

        dialog.showDialog();
    }

    /**
     * IDOR 扫描：通过三种策略（删鉴权、数字参数变形、历史参数不同值）检测越权漏洞。
     * 弹窗上方配置线程数，点击"开始扫描"后并发发包；哈希变化行染浅红。
     */
    private void runIdorScan(JComponent parent,
                              List<burp.api.montoya.http.message.HttpRequestResponse> selectedItems,
                              IdorScanService scanService) {
        if (selectedItems == null || selectedItems.isEmpty()) return;

        IdorScanDialog dialog = new IdorScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        dialog.setStartHandler(threadCount -> executor.submit(() ->
                scanService.executeScan(selectedItems, dialog, threadCount, scanPoolRef, stopRequested)));

        dialog.showDialog();
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
        originalData.setContentType(response.headerValue("Content-Type") != null
                ? response.headerValue("Content-Type") : "");
        // 从右键菜单选中条目的 annotations 读取 Burp 备注
        if (reqResp.annotations() != null && reqResp.annotations().hasNotes()) {
            originalData.setNote(reqResp.annotations().notes());
        }

        // 收集所有鉴权用户
        List<AuthUserModel> users = UserPanelBinder.collectUsers(mainPanel.getPanelUser());

        // 处理请求
        final burp.api.montoya.http.message.responses.HttpResponse finalResponse = response;
        CompareSampleModel sample = controller.processRequest(
                request, finalResponse, originalData, users);

        // 更新 UI
        SwingUtilities.invokeLater(() -> refreshDataTable(mainPanel, controller));
    }
}
