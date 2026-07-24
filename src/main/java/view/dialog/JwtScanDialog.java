package view.dialog;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import core.JwtScanResult;
import utils.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** JWT 主动扫描弹窗：显示每个实际发送的数据包、响应和基线相似度。 */
public class JwtScanDialog extends JDialog {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JSpinner threadSpinner;
    private final JCheckBox followRedirectsCheckBox;
    private final JButton startButton;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final List<JwtScanResult> results = new ArrayList<>();
    private Runnable closeHandler = () -> {};
    private BiConsumer<Integer, Boolean> startHandler = (threads, redirects) -> {};
    private int total;
    private boolean finished;
    private boolean closeNotified;

    public JwtScanDialog(MontoyaApi api, Component parent) {
        super(SwingUtilities.getWindowAncestor(parent),
                I18n.getInstance().text("auth_context_menu", "dialog.jwtScan.title"),
                ModalityType.MODELESS);
        requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        tableModel = new DefaultTableModel(columns(), 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        statusLabel = new JLabel(I18n.getInstance().text("auth_context_menu", "dialog.jwtScan.ready"));
        progressBar = new JProgressBar(0, 1);
        progressBar.setStringPainted(true);
        threadSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        followRedirectsCheckBox = new JCheckBox(
                I18n.getInstance().text("auth_context_menu", "dialog.jwtScan.followRedirects"), false);
        startButton = new JButton(I18n.getInstance().text("auth_context_menu", "dialog.jwtScan.start"));
        initLayout(parent);
    }

    public void showDialog() { setVisible(true); }
    public void setCloseHandler(Runnable handler) { closeHandler = handler != null ? handler : () -> {}; }
    public void setStartHandler(BiConsumer<Integer, Boolean> handler) {
        startHandler = handler != null ? handler : (threads, redirects) -> {};
    }

    public void setTotal(int total) {
        this.total = Math.max(total, 0);
        progressBar.setMaximum(Math.max(1, total));
        progressBar.setValue(0);
        refreshStatus(false);
    }

    public void addResult(JwtScanResult result) {
        results.add(result);
        tableModel.addRow(new Object[]{result.index(), result.time(), result.technique(),
                result.tokenLocation(), result.method(), result.host(), result.path(), result.statusCode(),
                result.length(), result.similarity() + "%", result.possibleVulnerability(),
                result.durationMs(), result.comment(), result.error()});
        if (results.size() == 1) {
            table.setRowSelectionInterval(0, 0);
            showMessage(result);
        }
        progressBar.setValue(results.size());
        refreshStatus(false);
    }

    public void finish() {
        finished = true;
        startButton.setEnabled(true);
        progressBar.setValue(total);
        refreshStatus(true);
    }

    private void initLayout(Component parent) {
        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { notifyCloseRequested(); }
            @Override public void windowClosed(WindowEvent e) { notifyCloseRequested(); }
        });
        add(buildConfigPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setSize(1200, 780);
        setLocationRelativeTo(parent);
    }

    private JPanel buildConfigPanel() {
        I18n i18n = I18n.getInstance();
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.setBorder(BorderFactory.createTitledBorder(
                i18n.text("auth_context_menu", "dialog.jwtScan.config")));
        panel.add(new JLabel(i18n.text("auth_context_menu", "dialog.jwtScan.threads")));
        panel.add(threadSpinner);
        panel.add(followRedirectsCheckBox);
        panel.add(startButton);
        panel.add(new JLabel(i18n.text("auth_context_menu", "dialog.jwtScan.legend")));
        startButton.addActionListener(e -> {
            startButton.setEnabled(false);
            results.clear();
            tableModel.setRowCount(0);
            progressBar.setValue(0);
            startHandler.accept((int) threadSpinner.getValue(), followRedirectsCheckBox.isSelected());
        });
        return panel;
    }

    private JSplitPane buildMainSplit() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                Component component = super.getTableCellRendererComponent(
                        table, value, selected, focused, row, column);
                int modelRow = table.convertRowIndexToModel(row);
                if (!selected && modelRow < results.size() && results.get(modelRow).possibleVulnerability()) {
                    component.setBackground(new Color(255, 210, 210));
                } else if (!selected) {
                    component.setBackground(table.getBackground());
                }
                return component;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && table.getSelectedRow() >= 0) {
                showMessage(results.get(table.convertRowIndexToModel(table.getSelectedRow())));
            }
        });

        JSplitPane messages = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapEditor(I18n.getInstance().text("message", "tab.request"), requestEditor.uiComponent()),
                wrapEditor(I18n.getInstance().text("message", "tab.response"), responseEditor.uiComponent()));
        messages.setResizeWeight(0.5);
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), messages);
        main.setResizeWeight(0.45);
        return main;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 6, 4, 6));
        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(progressBar, BorderLayout.CENTER);
        return panel;
    }

    private JPanel wrapEditor(String title, Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private void showMessage(JwtScanResult result) {
        HttpRequestResponse message = result.requestResponse();
        if (message == null) return;
        if (message.request() != null) requestEditor.setRequest(message.request());
        if (message.response() != null) responseEditor.setResponse(message.response());
    }

    private void notifyCloseRequested() {
        if (finished || closeNotified) return;
        closeNotified = true;
        closeHandler.run();
    }

    private void refreshStatus(boolean done) {
        if (total <= 0 && !done) return;
        String key = done ? "dialog.jwtScan.finished" : "dialog.jwtScan.progress";
        statusLabel.setText(I18n.getInstance().format("auth_context_menu", key, results.size(), total));
        progressBar.setString(results.size() + "/" + total);
    }

    private Object[] columns() {
        I18n i18n = I18n.getInstance();
        return new Object[]{"#", i18n.text("auth_context_menu", "dialog.jwtScan.column.time"),
                i18n.text("auth_context_menu", "dialog.jwtScan.column.technique"),
                i18n.text("auth_context_menu", "dialog.jwtScan.column.location"),
                "Method", "Host", "Path", "Status", "Length",
                i18n.text("auth_context_menu", "dialog.jwtScan.column.similarity"),
                i18n.text("auth_context_menu", "dialog.jwtScan.column.possible"),
                i18n.text("auth_context_menu", "dialog.jwtScan.column.duration"), "Comment", "Error"};
    }
}
