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

/** 403 bypass 扫描结果弹窗。 */
public class Bypass403ScanDialog extends JDialog {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final List<Bypass403ScanResult> results = new ArrayList<>();
    private Runnable closeHandler = () -> {};
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
        this.statusLabel = new JLabel(I18n.getInstance().text("auth_context_menu", "dialog.bypass403.running"));
        initLayout(parent);
    }

    public void showDialog() {
        setVisible(true);
    }

    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler != null ? closeHandler : () -> {};
    }

    public void setTotal(int total) {
        this.total = Math.max(total, 0);
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
        refreshStatus(false);
    }

    public void finish() {
        finished = true;
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
        add(mainSplit, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
        setSize(1100, 700);
        setLocationRelativeTo(parent);
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

    private void refreshStatus(boolean finished) {
        I18n i18n = I18n.getInstance();
        String key = finished ? "dialog.bypass403.finished" : "dialog.bypass403.progress";
        statusLabel.setText(i18n.format("auth_context_menu", key, results.size(), total));
    }

    private Object[] columns() {
        I18n i18n = I18n.getInstance();
        return new Object[]{"#", i18n.text("auth_context_menu", "dialog.bypass403.column.time"),
                "Tool", i18n.text("auth_context_menu", "dialog.bypass403.column.technique"), "Method", "Host",
                "Path", "Query", "Param count", "Status code", "Length",
                i18n.text("auth_context_menu", "dialog.bypass403.column.duration"), "Comment", "Error"};
    }
}
