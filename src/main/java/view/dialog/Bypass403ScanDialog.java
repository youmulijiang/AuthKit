package view.dialog;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import core.Bypass403ScanResult;
import utils.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** 403 bypass 扫描结果弹窗：顶部配置区，中部结果表格，下方请求响应查看器。 */
public class Bypass403ScanDialog extends JDialog {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JSpinner threadSpinner;
    private final JCheckBox followRedirectsCheckBox;
    private final JButton btnStart;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final List<Bypass403ScanResult> results = new ArrayList<>();

    private Runnable closeHandler = () -> {};
    private BiConsumer<Integer, Boolean> startHandler = (threads, followRedirects) -> {};
    private int total;
    private boolean finished;
    private boolean closeNotified;

    public Bypass403ScanDialog(MontoyaApi api, Component parent) {
        super(SwingUtilities.getWindowAncestor(parent),
                I18n.getInstance().text("auth_context_menu", "dialog.bypass403.title"),
                ModalityType.MODELESS);
        this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.tableModel = new DefaultTableModel(columns(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.table = new JTable(tableModel);
        this.statusLabel = new JLabel(I18n.getInstance().text("auth_context_menu", "dialog.bypass403.ready"));
        this.progressBar = new JProgressBar(0, 1);
        this.progressBar.setStringPainted(true);
        this.progressBar.setString("");
        this.threadSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        this.followRedirectsCheckBox = new JCheckBox(
                I18n.getInstance().text("auth_context_menu", "dialog.bypass403.followRedirects"), false);
        this.btnStart = new JButton(I18n.getInstance().text("auth_context_menu", "dialog.bypass403.start"));
        initLayout(parent);
    }

    public void showDialog() {
        setVisible(true);
    }

    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler != null ? closeHandler : () -> {};
    }

    /** 设置开始扫描回调：参数为 (线程数, 是否跟随重定向)。 */
    public void setStartHandler(BiConsumer<Integer, Boolean> handler) {
        this.startHandler = handler != null ? handler : (t, r) -> {};
    }

    public void setTotal(int total) {
        this.total = Math.max(total, 0);
        progressBar.setMaximum(Math.max(total, 1));
        progressBar.setValue(0);
        refreshStatus(false);
    }

    public void addResult(Bypass403ScanResult result) {
        results.add(result);
        tableModel.addRow(new Object[]{
                result.index(), result.time(), "AuthKit", result.technique(), result.method(), result.host(),
                result.path(), result.query(), result.paramCount(), result.statusCode(),
                result.length(), result.durationMs(), result.comment(), result.error()
        });
        if (results.size() == 1) {
            table.setRowSelectionInterval(0, 0);
            showMessage(result);
        }
        progressBar.setValue(results.size());
        refreshStatus(false);
    }

    public void finish() {
        finished = true;
        btnStart.setEnabled(true);
        progressBar.setValue(total);
        refreshStatus(true);
    }

    private void initLayout(Component parent) {
        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                notifyCloseRequested();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                notifyCloseRequested();
            }
        });

        add(buildConfigPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setSize(1100, 750);
        setLocationRelativeTo(parent);
    }

    private JPanel buildConfigPanel() {
        I18n i18n = I18n.getInstance();
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.setBorder(BorderFactory.createTitledBorder(i18n.text("auth_context_menu", "dialog.bypass403.config")));
        panel.add(new JLabel(i18n.text("auth_context_menu", "dialog.bypass403.threads")));
        panel.add(threadSpinner);
        panel.add(followRedirectsCheckBox);
        panel.add(btnStart);

        btnStart.addActionListener(e -> {
            btnStart.setEnabled(false);
            results.clear();
            tableModel.setRowCount(0);
            progressBar.setValue(0);
            int threads = (int) threadSpinner.getValue();
            boolean followRedirects = followRedirectsCheckBox.isSelected();
            startHandler.accept(threads, followRedirects);
        });
        return panel;
    }

    private JSplitPane buildMainSplit() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    showMessage(results.get(table.convertRowIndexToModel(row)));
                }
            }
        });

        JSplitPane messageSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapEditor(I18n.getInstance().text("message", "tab.request"), requestEditor.uiComponent()),
                wrapEditor(I18n.getInstance().text("message", "tab.response"), responseEditor.uiComponent()));
        messageSplit.setResizeWeight(0.5);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), messageSplit);
        mainSplit.setResizeWeight(0.45);
        return mainSplit;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(progressBar, BorderLayout.CENTER);
        return bar;
    }

    private void notifyCloseRequested() {
        if (finished || closeNotified) {
            return;
        }
        closeNotified = true;
        closeHandler.run();
    }

    private JPanel wrapEditor(String title, Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private void showMessage(Bypass403ScanResult result) {
        HttpRequestResponse requestResponse = result.requestResponse();
        if (requestResponse == null) {
            return;
        }
        if (requestResponse.request() != null) {
            requestEditor.setRequest(requestResponse.request());
        }
        if (requestResponse.response() != null) {
            responseEditor.setResponse(requestResponse.response());
        }
    }

    private void refreshStatus(boolean fin) {
        I18n i18n = I18n.getInstance();
        String key = fin ? "dialog.bypass403.finished" : "dialog.bypass403.progress";
        if (total > 0 || fin) {
            statusLabel.setText(i18n.format("auth_context_menu", key, results.size(), total));
            progressBar.setString(results.size() + "/" + total);
        }
    }

    private Object[] columns() {
        I18n i18n = I18n.getInstance();
        return new Object[]{"#", i18n.text("auth_context_menu", "dialog.bypass403.column.time"),
                "Tool", i18n.text("auth_context_menu", "dialog.bypass403.column.technique"), "Method", "Host",
                "Path", "Query", "Param count", "Status code", "Length",
                i18n.text("auth_context_menu", "dialog.bypass403.column.duration"), "Comment", "Error"};
    }
}
