package view.dialog;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import core.scan.idor.IdorScanResult;
import utils.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** IDOR 扫描弹窗：上方配置区（线程数 + 开始扫描），中部结果表格，下方请求响应查看器。 */
public class IdorScanDialog extends JDialog {

    /** 哈希变化行背景色（浅红），表示可能存在 IDOR 越权 */
    private static final Color HASH_CHANGED_COLOR = new Color(255, 200, 200);

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;
    private final JSpinner threadSpinner;
    private final JButton btnStart;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final List<IdorScanResult> results = new ArrayList<>();

    private Runnable closeHandler = () -> {};
    private Consumer<Integer> startHandler = threads -> {};
    private int total;
    private boolean finished;
    private boolean closeNotified;

    public IdorScanDialog(MontoyaApi api, Component parent) {
        super(SwingUtilities.getWindowAncestor(parent),
                I18n.getInstance().text("auth_context_menu", "dialog.idor.title"),
                ModalityType.MODELESS);
        this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.tableModel = new DefaultTableModel(buildColumns(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.table = new JTable(tableModel);
        this.statusLabel = new JLabel(I18n.getInstance().text("auth_context_menu", "dialog.idor.ready"));
        this.threadSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        this.btnStart = new JButton(I18n.getInstance().text("auth_context_menu", "dialog.idor.start"));
        initLayout(parent);
    }

    public void showDialog() {
        setVisible(true);
    }

    public void setCloseHandler(Runnable handler) {
        this.closeHandler = handler != null ? handler : () -> {};
    }

    public void setStartHandler(Consumer<Integer> handler) {
        this.startHandler = handler != null ? handler : threads -> {};
    }

    public void setTotal(int total) {
        this.total = Math.max(total, 0);
        refreshStatus(false);
    }

    public void addResult(IdorScanResult result) {
        results.add(result);
        tableModel.addRow(new Object[]{
                result.index(), result.time(), result.technique(),
                result.paramName() != null ? result.paramName() : "",
                result.oldValue() != null ? result.oldValue() : "",
                result.newValue() != null ? result.newValue() : "",
                result.method(), result.host(), result.path(), result.query(),
                result.statusCode(), result.length(),
                result.hashChanged() ? "YES" : "-",
                result.durationMs(), result.comment(), result.error()
        });
        if (results.size() == 1) {
            table.setRowSelectionInterval(0, 0);
            showMessage(result);
        }
        refreshStatus(false);
    }

    public void finish() {
        finished = true;
        btnStart.setEnabled(true);
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

        setSize(1200, 750);
        setLocationRelativeTo(parent);
    }

    private JPanel buildConfigPanel() {
        I18n i18n = I18n.getInstance();
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        panel.setBorder(BorderFactory.createTitledBorder(i18n.text("auth_context_menu", "dialog.idor.config")));
        panel.add(new JLabel(i18n.text("auth_context_menu", "dialog.idor.threads")));
        panel.add(threadSpinner);
        panel.add(btnStart);

        JLabel legend = new JLabel("  " + i18n.text("auth_context_menu", "dialog.idor.legend"));
        legend.setForeground(new Color(180, 0, 0));
        panel.add(legend);

        btnStart.addActionListener(e -> {
            btnStart.setEnabled(false);
            int threads = (int) threadSpinner.getValue();
            startHandler.accept(threads);
        });
        return panel;
    }

    private JSplitPane buildMainSplit() {
        initTable();

        JSplitPane messageSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapEditor(I18n.getInstance().text("message", "tab.request"), requestEditor.uiComponent()),
                wrapEditor(I18n.getInstance().text("message", "tab.response"), responseEditor.uiComponent()));
        messageSplit.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), messageSplit);
        mainSplit.setResizeWeight(0.45);
        return mainSplit;
    }

    private void initTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);

        // 哈希变化行：整行染浅红色
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                            boolean hasFocus, int row, int column) {
                Component comp = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    int modelRow = t.convertRowIndexToModel(row);
                    if (modelRow >= 0 && modelRow < results.size() && results.get(modelRow).hashChanged()) {
                        comp.setBackground(HASH_CHANGED_COLOR);
                    } else {
                        comp.setBackground(t.getBackground());
                    }
                }
                return comp;
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    showMessage(results.get(table.convertRowIndexToModel(row)));
                }
            }
        });
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    private void notifyCloseRequested() {
        if (finished || closeNotified) return;
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

    private void showMessage(IdorScanResult result) {
        HttpRequestResponse reqResp = result.requestResponse();
        if (reqResp == null) return;
        if (reqResp.request() != null) requestEditor.setRequest(reqResp.request());
        if (reqResp.response() != null) responseEditor.setResponse(reqResp.response());
    }

    private void refreshStatus(boolean fin) {
        I18n i18n = I18n.getInstance();
        if (fin) {
            statusLabel.setText(i18n.format("auth_context_menu", "dialog.idor.finished", results.size(), total));
        } else if (total > 0) {
            statusLabel.setText(i18n.format("auth_context_menu", "dialog.idor.progress", results.size(), total));
        }
    }

    private Object[] buildColumns() {
        I18n i18n = I18n.getInstance();
        return new Object[]{
                "#",
                i18n.text("auth_context_menu", "dialog.idor.column.time"),
                i18n.text("auth_context_menu", "dialog.idor.column.technique"),
                i18n.text("auth_context_menu", "dialog.idor.column.param"),
                i18n.text("auth_context_menu", "dialog.idor.column.oldValue"),
                i18n.text("auth_context_menu", "dialog.idor.column.newValue"),
                "Method", "Host", "Path", "Query", "Status", "Length",
                i18n.text("auth_context_menu", "dialog.idor.column.hashChanged"),
                i18n.text("auth_context_menu", "dialog.bypass403.column.duration"),
                "Comment", "Error"
        };
    }
}
