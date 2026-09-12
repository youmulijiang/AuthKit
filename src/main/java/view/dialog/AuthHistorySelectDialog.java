package view.dialog;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import utils.I18n;
import view.AuthContextMenuProvider;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 从历史选择鉴权字段弹窗。
 * <p>
 * 顶部支持范围过滤（同一 Host / 全部 Host / 同一 Referer / 自定义 Host）
 * 与鉴权字段关键字筛选；下方展示请求/响应及认证头列表。
 */
public class AuthHistorySelectDialog extends JDialog {

    private enum ScopeMode {
        SAME_HOST,
        ALL_HOSTS,
        SAME_REFERER,
        CUSTOM
    }

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    private final List<ProxyHttpRequestResponse> allItems;
    private final String originalHost;
    private final String originalReferer;
    private final Consumer<ProxyHttpRequestResponse> applyHandler;

    /** 当前过滤并去重后的展示列表，与表格行一一对应。 */
    private List<ProxyHttpRequestResponse> displayedItems = new ArrayList<>();

    private JComboBox<String> scopeCombo;
    private JTextField customHostField;
    private JTextField filterField;
    private JTextArea authHeadersArea;
    private ScopeMode[] scopeModes;

    /**
     * 显示对话框（模态，阻塞直到关闭）。
     *
     * @param parent          父组件（用于居中定位）
     * @param api             Montoya API 实例
     * @param items           含鉴权字段的全量代理历史
     * @param originalHost    触发请求的 Host（用于默认「同一 Host」过滤）
     * @param originalReferer 触发请求的 Referer（可为 null）
     * @param applyHandler    用户点击「应用」时的回调
     */
    public static void show(Component parent, MontoyaApi api,
                            List<ProxyHttpRequestResponse> items,
                            String originalHost,
                            String originalReferer,
                            Consumer<ProxyHttpRequestResponse> applyHandler) {
        AuthHistorySelectDialog dialog = new AuthHistorySelectDialog(
                parent, api, items, originalHost, originalReferer, applyHandler);
        dialog.setVisible(true);
    }

    private AuthHistorySelectDialog(Component parent, MontoyaApi api,
                                    List<ProxyHttpRequestResponse> items,
                                    String originalHost,
                                    String originalReferer,
                                    Consumer<ProxyHttpRequestResponse> applyHandler) {
        super(SwingUtilities.getWindowAncestor(parent),
                I18n.getInstance().text("auth_context_menu", "dialog.authHistory.title"),
                ModalityType.APPLICATION_MODAL);
        this.allItems = items != null ? items : List.of();
        this.originalHost = originalHost != null ? originalHost : "";
        this.originalReferer = originalReferer;
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
        initLayout(parent);
        applyFilters();
    }

    private void initLayout(Component parent) {
        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        add(buildFilterBar(), BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(2).setMaxWidth(80);
        table.getColumnModel().getColumn(6).setMaxWidth(70);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    if (modelRow >= 0 && modelRow < displayedItems.size()) {
                        showMessage(displayedItems.get(modelRow));
                    }
                } else {
                    clearMessage();
                }
            }
        });

        JSplitPane messageSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapEditor(I18n.getInstance().text("message", "tab.request"), requestEditor.uiComponent()),
                wrapEditor(I18n.getInstance().text("message", "tab.response"), responseEditor.uiComponent()));
        messageSplit.setResizeWeight(0.5);

        JPanel messageContainer = new JPanel(new BorderLayout(0, 4));
        messageContainer.add(buildAuthHeadersPanel(), BorderLayout.NORTH);
        messageContainer.add(messageSplit, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), messageContainer);
        mainSplit.setResizeWeight(0.4);

        I18n i18n = I18n.getInstance();
        JButton applyBtn = new JButton(i18n.text("auth_context_menu", "dialog.authHistory.apply"));
        JButton cancelBtn = new JButton(i18n.text("auth_context_menu", "dialog.authHistory.cancel"));

        applyBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow >= 0 && modelRow < displayedItems.size()) {
                    applyHandler.accept(displayedItems.get(modelRow));
                    dispose();
                }
            }
        });
        cancelBtn.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        buttonPanel.add(applyBtn);
        buttonPanel.add(cancelBtn);

        add(mainSplit, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setSize(1100, 720);
        setLocationRelativeTo(parent);
    }

    private JPanel buildFilterBar() {
        I18n i18n = I18n.getInstance();
        JPanel bar = new JPanel(new GridBagLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 4, 0, 4);
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        String sameHost = i18n.text("auth_context_menu", "dialog.authHistory.scope.sameHost");
        String allHosts = i18n.text("auth_context_menu", "dialog.authHistory.scope.allHosts");
        String sameReferer = i18n.text("auth_context_menu", "dialog.authHistory.scope.sameReferer");
        String custom = i18n.text("auth_context_menu", "dialog.authHistory.scope.custom");
        scopeModes = new ScopeMode[]{
                ScopeMode.SAME_HOST,
                ScopeMode.ALL_HOSTS,
                ScopeMode.SAME_REFERER,
                ScopeMode.CUSTOM
        };
        scopeCombo = new JComboBox<>(new String[]{sameHost, allHosts, sameReferer, custom});
        scopeCombo.setSelectedIndex(0);

        customHostField = new JTextField(originalHost, 16);
        customHostField.setVisible(false);
        customHostField.setToolTipText(custom);

        filterField = new JTextField(18);
        filterField.putClientProperty("JTextField.placeholderText",
                i18n.text("auth_context_menu", "dialog.authHistory.filter.placeholder"));

        gbc.gridx = 0;
        gbc.weightx = 0;
        bar.add(new JLabel(i18n.text("auth_context_menu", "dialog.authHistory.scope.label")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        bar.add(scopeCombo, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.25;
        bar.add(customHostField, gbc);

        gbc.gridx = 3;
        gbc.weightx = 0;
        bar.add(new JLabel(i18n.text("auth_context_menu", "dialog.authHistory.filter.label")), gbc);

        gbc.gridx = 4;
        gbc.weightx = 1.0;
        bar.add(filterField, gbc);

        scopeCombo.addActionListener(e -> {
            boolean customSelected = getSelectedScope() == ScopeMode.CUSTOM;
            customHostField.setVisible(customSelected);
            bar.revalidate();
            bar.repaint();
            applyFilters();
        });

        DocumentListener filterListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        };
        filterField.getDocument().addDocumentListener(filterListener);
        customHostField.getDocument().addDocumentListener(filterListener);

        return bar;
    }

    private JPanel buildAuthHeadersPanel() {
        I18n i18n = I18n.getInstance();
        authHeadersArea = new JTextArea(4, 40);
        authHeadersArea.setEditable(false);
        authHeadersArea.setLineWrap(true);
        authHeadersArea.setWrapStyleWord(true);
        authHeadersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        authHeadersArea.setBackground(UIManager.getColor("TextArea.background"));

        JScrollPane scroll = new JScrollPane(authHeadersArea);
        scroll.setPreferredSize(new Dimension(0, 90));
        scroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel(i18n.text("auth_context_menu", "dialog.authHistory.authHeaders.title"));
        title.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 0, 2, 0),
                BorderFactory.createTitledBorder("")));
        panel.add(title, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private ScopeMode getSelectedScope() {
        int idx = scopeCombo.getSelectedIndex();
        if (idx < 0 || idx >= scopeModes.length) {
            return ScopeMode.SAME_HOST;
        }
        return scopeModes[idx];
    }

    /** 按当前范围 + 关键字过滤，鉴权字段组合去重后刷新表格。 */
    private void applyFilters() {
        ScopeMode scope = getSelectedScope();
        String keyword = filterField != null ? filterField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String customHost = customHostField != null
                ? customHostField.getText().trim().toLowerCase(Locale.ROOT) : "";

        LinkedHashMap<String, ProxyHttpRequestResponse> deduped = new LinkedHashMap<>();
        for (ProxyHttpRequestResponse item : allItems) {
            if (!matchesScope(item, scope, customHost)) continue;
            String authSummary = buildAuthSummary(item.request().headers());
            if (!keyword.isEmpty() && !authSummary.toLowerCase(Locale.ROOT).contains(keyword)) {
                continue;
            }
            String key = buildAuthDeduplicationKey(item.request());
            if (!key.isEmpty()) {
                deduped.put(key, item);
            }
        }

        displayedItems = new ArrayList<>(deduped.values());
        refreshTable();
    }

    private boolean matchesScope(ProxyHttpRequestResponse item, ScopeMode scope, String customHost) {
        return switch (scope) {
            case SAME_HOST -> originalHost.equalsIgnoreCase(nullToEmpty(item.host()));
            case ALL_HOSTS -> true;
            case SAME_REFERER -> matchesSameReferer(item);
            case CUSTOM -> {
                if (customHost.isEmpty()) {
                    yield true;
                }
                yield nullToEmpty(item.host()).toLowerCase(Locale.ROOT).contains(customHost);
            }
        };
    }

    /**
     * 同一 Referer 来源：请求自身 Referer 与原始请求 Referer 相同，
     * 或请求 URL 与原始 Referer 指向同一来源路径（跳转链）。
     */
    private boolean matchesSameReferer(ProxyHttpRequestResponse item) {
        if (originalReferer == null || originalReferer.isBlank()) {
            // 无 Referer 时退化为同一 Host
            return originalHost.equalsIgnoreCase(nullToEmpty(item.host()));
        }
        String itemReferer = item.request() != null ? item.request().headerValue("Referer") : null;
        if (originalReferer.equals(itemReferer)) {
            return true;
        }
        // 历史请求本身就是 Referer 指向的页面（作为跳转来源）
        String itemUrl = buildRequestUrl(item);
        return !itemUrl.isEmpty() && (originalReferer.equals(itemUrl)
                || originalReferer.startsWith(itemUrl)
                || itemUrl.startsWith(stripQuery(originalReferer)));
    }

    private static String buildRequestUrl(ProxyHttpRequestResponse item) {
        if (item == null || item.request() == null) return "";
        try {
            return item.request().url();
        } catch (Exception ignored) {
            String host = nullToEmpty(item.host());
            String path = nullToEmpty(item.path());
            if (host.isEmpty()) return "";
            return host + path;
        }
    }

    private static String stripQuery(String url) {
        if (url == null) return "";
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        for (int i = 0; i < displayedItems.size(); i++) {
            ProxyHttpRequestResponse item = displayedItems.get(i);
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
        if (tableModel.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        } else {
            clearMessage();
        }
    }

    private void showMessage(ProxyHttpRequestResponse item) {
        if (item.request() != null) {
            requestEditor.setRequest(item.request());
            authHeadersArea.setText(buildAuthHeadersDetail(item.request().headers()));
            authHeadersArea.setCaretPosition(0);
        } else {
            authHeadersArea.setText("");
        }
        if (item.hasResponse() && item.response() != null) {
            responseEditor.setResponse(item.response());
        }
    }

    private void clearMessage() {
        authHeadersArea.setText("");
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

    /** 认证头详情（完整 name: value，每行一个）。 */
    private static String buildAuthHeadersDetail(List<HttpHeader> headers) {
        StringBuilder sb = new StringBuilder();
        for (HttpHeader header : headers) {
            if (AuthContextMenuProvider.isAuthHeader(header.name())) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(header.name()).append(": ").append(header.value());
            }
        }
        return sb.toString();
    }

    /** 构建代理历史鉴权字段的去重键。 */
    private static String buildAuthDeduplicationKey(HttpRequest request) {
        List<String> parts = new ArrayList<>();
        for (HttpHeader header : request.headers()) {
            if (AuthContextMenuProvider.isAuthHeader(header.name())) {
                parts.add(header.name().toLowerCase(Locale.ROOT) + "=" + header.value());
            }
        }
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }
}
