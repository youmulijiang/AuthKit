import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import controller.AuthController;
import controller.ContextMenuController;
import controller.DataTableController;
import core.AuthResultExportService;
import core.DiffService;
import core.FakeIpIntruderHttpHandler;
import core.FakeIpPayloadGeneratorProvider;
import core.FakeIpService;
import core.HttpRequestHandler;
import core.RequestReplayService;
import core.TextDiffService;
import core.service.ConfigRequestFilter;
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
import view.MainPanel;
import view.binding.ConfigBinder;
import view.binding.UserPanelBinder;
import view.component.*;

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
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        DataTableController dataTableController = new DataTableController(
                mainPanel, controller, exportService, executor);

        // 绑定 UI → ConfigModel 同步
        ConfigBinder.bind(mainPanel.getPanelConfiguration(), configModel);

        // 绑定 DataTable 相关事件（筛选/选中/导出/Clear/指标/越权勾选）
        dataTableController.bindAll();

        // 绑定自动 Diff 事件（懒加载，tab 切换时自动触发）
        bindAutoDiff(mainPanel.getPanelCompare(), diffService);

        // 注册右键菜单
        ContextMenuController contextMenuController = new ContextMenuController(
                montoyaApi, mainPanel, controller, replayService, fakeIpService, executor,
                dataTableController::refresh);
        contextMenuController.register();

        // 注册 Intruder 随机 IP payload 生成器（fakeIpPayloads）
        montoyaApi.intruder().registerPayloadGeneratorProvider(
                new FakeIpPayloadGeneratorProvider(fakeIpService));
        // 注册 Intruder 请求发送前处理器：每个爆破数据包自动注入随机伪造 IP 头
        montoyaApi.http().registerHttpHandler(new FakeIpIntruderHttpHandler(fakeIpService));

        // 注册 JWT 编辑器 Provider（在 Burp 请求编辑器中添加 JWT 选项卡）
        montoyaApi.userInterface().registerHttpRequestEditorProvider(new JwtRequestEditorProvider(montoyaApi));

        // 创建并注册 HttpRequestHandler（回调委托给 DataTableController）
        ConfigRequestFilter requestFilter = new ConfigRequestFilter(configModel);
        HttpRequestHandler httpHandler = new HttpRequestHandler(requestFilter,
                dataTableController::handleCapturedRequest);
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

}
