package view.dialog;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import utils.I18n;
import view.AuthContextMenuProvider;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/** 从历史选择鉴权字段弹窗：展示同 host 下含鉴权字段的去重代理历史请求。 */
public class AuthHistorySelectDialog extends JDialog {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final List<ProxyHttpRequestResponse> items;
    private final Consumer<ProxyHttpRequestResponse> applyHandler;

    /**
     * 显示对话框（模态，阻塞直到关闭）。
     *
     * @param parent       父组件（用于居中定位）
     * @param api          Montoya API 实例
     * @param items        去重后的代理历史请求列表
     * @param applyHandler 用户点击"应用"时的回调，接收选中的历史请求
     */
    public static void show(Component parent, MontoyaApi api,
                             List<ProxyHttpRequestResponse> items,
                             Consumer<ProxyHttpRequestResponse> applyHandler) {
        AuthHistorySelectDialog dialog = new AuthHistorySelectDialog(parent, api, items, applyHandler);
        dialog.setVisible(true);
    }

    private AuthHistorySelectDialog(Component parent, MontoyaApi api,
                                     List<ProxyHttpRequestResponse> items,
                                     Consumer<ProxyHttpRequestResponse> applyHandler) {
        super(SwingUtilities.getWindowAncestor(parent),
                I18n.getInstance().text("auth_context_menu", "dialog.authHistory.title"),
                ModalityType.APPLICATION_MODAL);
        this.items = items;
        this.applyHandler = applyHandler;
        this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.tableModel = new DefaultTableModel(buildColumns(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.table = new JTable(tableModel);
        populateTable();
        initLayout(parent);
    }

    private void populateTable() {
        for (int i = 0; i < items.size(); i++) {
            ProxyHttpRequestResponse item = items.get(i);
            String method = item.method();
            String host = item.host();
            String path = item.path();
            String authSummary = buildAuthSummary(item.request().headers());
            String time = item.time() != null ? item.time().toLocalDateTime().toString() : "";
            int status = item.hasResponse() && item.response() != null ? item.response().statusCode() : 0;
            tableModel.addRow(new Object[]{
                    i + 1, time, method, host, path, authSummary,
                    status > 0 ? status : "-"
            });
        }
    }

    private void initLayout(Component parent) {
        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(2).setMaxWidth(80);
        table.getColumnModel().getColumn(6).setMaxWidth(70);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    showMessage(items.get(table.convertRowIndexToModel(row)));
                }
            }
        });

        JSplitPane messageSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapEditor(I18n.getInstance().text("message", "tab.request"), requestEditor.uiComponent()),
                wrapEditor(I18n.getInstance().text("message", "tab.response"), responseEditor.uiComponent()));
        messageSplit.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), messageSplit);
        mainSplit.setResizeWeight(0.4);

        I18n i18n = I18n.getInstance();
        JButton applyBtn = new JButton(i18n.text("auth_context_menu", "dialog.authHistory.apply"));
        JButton cancelBtn = new JButton(i18n.text("auth_context_menu", "dialog.authHistory.cancel"));

        applyBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                applyHandler.accept(items.get(table.convertRowIndexToModel(row)));
                dispose();
            }
        });
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttonPanel.add(applyBtn);
        buttonPanel.add(cancelBtn);

        add(mainSplit, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(1050, 660);
        setLocationRelativeTo(parent);

        if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void showMessage(ProxyHttpRequestResponse item) {
        if (item.request() != null) {
            requestEditor.setRequest(item.request());
        }
        if (item.hasResponse() && item.response() != null) {
            responseEditor.setResponse(item.response());
        }
    }

    private JPanel wrapEditor(String title, Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title);
        label.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private Object[] buildColumns() {
        I18n i18n = I18n.getInstance();
        return new Object[]{
                "#",
                i18n.text("auth_context_menu", "dialog.authHistory.column.time"),
                "Method", "Host", "Path",
                i18n.text("auth_context_menu", "dialog.authHistory.column.authFields"),
                "Status"
        };
    }

    /** 构建鉴权字段摘要字符串，值超过 40 字符时截断。 */
    private static String buildAuthSummary(List<HttpHeader> headers) {
        StringBuilder sb = new StringBuilder();
        for (HttpHeader header : headers) {
            if (AuthContextMenuProvider.isAuthHeader(header.name())) {
                if (!sb.isEmpty()) sb.append("; ");
                String value = header.value();
                if (value.length() > 40) {
                    value = value.substring(0, 37) + "...";
                }
                sb.append(header.name()).append(": ").append(value);
            }
        }
        return sb.toString();
    }
}
