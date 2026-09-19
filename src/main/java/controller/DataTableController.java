package controller;

import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.requests.HttpRequest;
import core.AuthResultExportService;
import core.HashService;
import core.RankService;
import model.AuthUserModel;
import model.CompareSampleModel;
import model.MessageDataModel;
import utils.HttpTextUtils;
import utils.I18n;
import utils.LogUtils;
import view.MainPanel;
import view.binding.UserPanelBinder;
import view.component.ComparePanel;
import view.component.DataTablePanel;
import view.component.MessagePanel;
import view.component.MetadataTablePanel;
import view.component.ToolbarPanel;

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

/**
 * 数据表控制器（C层）
 * <p>
 * 编排 DataTable 相关的全部 UI 事件：表格刷新、行/单元格选中联动、
 * 导出 CSV/HTML、复制 URL、被动捕获请求处理、Clear 清空。
 * 选中联动会更新右侧 MetadataTable 与 ComparePanel。
 */
public class DataTableController {

    private static final int DATA_TABLE_FIXED_COLUMN_COUNT = 3;

    private final MainPanel mainPanel;
    private final AuthController controller;
    private final AuthResultExportService exportService;
    private final ExecutorService executor;

    /** 缓存当前已展示在 ComparePanel 中的 sample ID，避免重复点击同一行时重复加载 */
    private int lastCompareSampleId = -1;

    public DataTableController(MainPanel mainPanel, AuthController controller,
                               AuthResultExportService exportService, ExecutorService executor) {
        this.mainPanel = mainPanel;
        this.controller = controller;
        this.exportService = exportService;
        this.executor = executor;
    }

    /**
     * 绑定全部表格相关事件：筛选、选中联动、多选右键动作、
     * Clear 按钮、展示指标下拉框、仅显示越权行勾选框。
     */
    public void bindAll() {
        bindFilter();
        bindTableSelection();
        bindDataTableSelectionActions();

        // 绑定 Clear 按钮
        mainPanel.getPanelConfiguration().getBtnClearTable().addActionListener(e -> clearAll());

        // 绑定展示指标下拉框切换 → 刷新 DataTable 中鉴权对象列的数据
        mainPanel.getPanelConfiguration().getComboBoxDisplayMetric().addActionListener(e -> refresh());

        // 绑定仅显示越权行勾选框
        mainPanel.getPanelConfiguration().getCheckBoxUnauthorizedOnly().addActionListener(e ->
                mainPanel.getPanelDataTable().setUnauthorizedOnly(
                        mainPanel.getPanelConfiguration().getCheckBoxUnauthorizedOnly().isSelected()));

        // 注入越权判定器：基于 RankService 的归一化评分与阈值判定，与表格展示指标无关
        mainPanel.getPanelDataTable().setUnauthorizedChecker(modelRow -> {
            CompareSampleModel sample = controller.getSample(modelRow);
            return sample != null && sample.hasUnauthorizedByRank(RankService.UNAUTHORIZED_THRESHOLD);
        });
    }

    /**
     * 清空全部数据：控制器记录 + 各 UI 面板 + ComparePanel 缓存
     */
    public void clearAll() {
        controller.clearAll();
        mainPanel.getPanelDataTable().clearAll();
        mainPanel.getPanelMetadataTable().clearAll();
        mainPanel.getPanelCompare().clearAll();
        lastCompareSampleId = -1;
    }

    /**
     * 处理 HttpHandler 捕获的请求：去重、构建原始数据、收集用户重放、刷新表格
     */
    public void handleCapturedRequest(HttpRequest request, HttpResponseReceived response) {
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
                        HashService.hash(response.bodyToString()),
                        request, response
                );
                originalData.setBody(response.bodyToString());
                originalData.setContentType(response.headerValue("Content-Type") != null
                        ? response.headerValue("Content-Type") : "");
                // 从拦截响应的 annotations 读取 Burp 备注
                if (response.annotations() != null && response.annotations().hasNotes()) {
                    originalData.setNote(response.annotations().notes());
                }

                // 从 UserPanel 收集启用的用户配置
                List<AuthUserModel> users = UserPanelBinder.collectUsers(mainPanel.getPanelUser());

                // 处理请求
                controller.processRequest(request, response, originalData, users);

                // 更新 UI（在 EDT 线程）
                SwingUtilities.invokeLater(this::refresh);
            } catch (Exception ex) {
                LogUtils.INSTANCE.error("Error processing request", ex);
            }
        });
    }

    /**
     * 刷新 DataTable：始终以当前 samples 快照和当前选中 metric 为准
     */
    public void refresh() {
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
     * 绑定筛选功能：下拉框切换和输入框实时输入触发筛选
     */
    private void bindFilter() {
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
    private void bindTableSelection() {
        JTable table = mainPanel.getPanelDataTable().getTableData();

        table.getSelectionModel()
                .addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) {
                        return;
                    }
                    updateSelectedSample(mainPanel.getPanelDataTable().getSelectedRow());
                });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleTableCellClick(
                        table.rowAtPoint(e.getPoint()),
                        table.columnAtPoint(e.getPoint()));
            }
        });
    }

    /** 绑定 DataTable 多选右键动作。 */
    private void bindDataTableSelectionActions() {
        DataTablePanel dataTable = mainPanel.getPanelDataTable();
        dataTable.setSelectionActionHandler(new DataTablePanel.SelectionActionHandler() {
            @Override
            public void exportCsv(List<Integer> modelRows) {
                exportSelectedRows(modelRows, ExportFormat.CSV);
            }

            @Override
            public void exportHtml(List<Integer> modelRows) {
                exportSelectedRows(modelRows, ExportFormat.HTML);
            }

            @Override
            public void copyUrls(List<Integer> modelRows) {
                copySelectedUrls(modelRows);
            }
        });
    }

    private void exportSelectedRows(List<Integer> modelRows, ExportFormat format) {
        List<CompareSampleModel> samples = collectSelectedSamples(modelRows);
        if (samples.isEmpty()) {
            showDataTableMessage("message.noSelection", JOptionPane.WARNING_MESSAGE);
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

    private void copySelectedUrls(List<Integer> modelRows) {
        List<CompareSampleModel> samples = collectSelectedSamples(modelRows);
        if (samples.isEmpty()) {
            showDataTableMessage("message.noSelection", JOptionPane.WARNING_MESSAGE);
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
            showDataTableMessage("message.noUrl", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(urls.toString()), null);
        showDataTableMessage("message.copySuccess", JOptionPane.INFORMATION_MESSAGE);
    }

    private List<CompareSampleModel> collectSelectedSamples(List<Integer> modelRows) {
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

    private void showDataTableMessage(String key, int messageType) {
        JOptionPane.showMessageDialog(mainPanel,
                I18n.getInstance().text("data_table", key),
                I18n.getInstance().text("data_table", "title.selection"), messageType);
    }

    /** 更新当前选中样本对应的 Metadata 和 Compare 区域 */
    private void updateSelectedSample(int modelRow) {
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
    private void handleTableCellClick(int viewRow, int viewColumn) {
        if (viewRow < 0 || viewColumn < 0) {
            return;
        }

        JTable table = mainPanel.getPanelDataTable().getTableData();
        int modelRow = table.convertRowIndexToModel(viewRow);
        updateSelectedSample(modelRow);

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
