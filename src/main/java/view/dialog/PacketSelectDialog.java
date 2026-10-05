package view.dialog;

import burp.api.montoya.http.message.HttpRequestResponse;
import core.PacketSourceService;
import utils.I18n;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 数据包选择弹窗：AI 请求读取 Proxy 历史 / 站点地图时，由用户选择要提供给 AI 的报文。
 * <p>
 * 设计约束（参考 portswigger/mcp-server 的数据访问门控思路）：**不默认全量读取**，
 * 每次都由用户勾选；候选按越权测试价值评分排序，并把高价值项（含对象标识符）
 * 预先选中作为建议，用户可增删后再发送。选中条数有上限，避免上下文膨胀。
 * <p>
 * 读取在后台线程完成（大历史下 Proxy 历史可能有上万条），界面先展示"读取中"占位，
 * 避免弹窗卡死或看起来空白；候选可按**域名（Host）**二次筛选。
 */
public class PacketSelectDialog extends JDialog {

    /** 单次最多提供给 AI 的数据包条数 */
    public static final int MAX_SEND = 20;
    /** 建议预选的最低评分（含对象标识符等特征） */
    private static final int SUGGEST_SCORE = 40;

    private static final String[] COLUMN_KEYS = {
            "column.method", "column.host", "column.path", "column.status",
            "column.length", "column.score"
    };

    private final PacketSourceService service;
    private final PacketSourceService.Source source;
    private final String keywordHint;
    /** AI 给出的读取理由，用于弹窗中说明"为什么请求读取" */
    private final String requestReason;
    private final JTextField textKeyword = new JTextField(14);
    private final JComboBox<String> comboHost = new JComboBox<>();
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel labelSummary = new JLabel();
    private final JLabel labelNote = new JLabel();
    private final JButton btnSend = new JButton();
    private final JButton btnSendAll = new JButton();
    private final JButton btnCancel = new JButton();
    private final JButton btnFilter = new JButton();
    private final JButton btnReset = new JButton();
    private final JButton btnSuggestObjectId = new JButton();
    private final JButton btnSuggestParams = new JButton();
    private final JButton btnSuggestOk = new JButton();
    private final JLabel labelHint = new JLabel();
    private final JLabel labelSource = new JLabel();
    private final JLabel labelKeyword = new JLabel();
    private final JLabel labelHost = new JLabel();
    private final JLabel labelSuggest = new JLabel();
    /** 当前来源读取到的全部候选 */
    private List<PacketSourceService.Candidate> candidates = new ArrayList<>();
    /** 按域名筛选后实际展示的候选（与表格行一一对应） */
    private List<PacketSourceService.Candidate> visible = new ArrayList<>();
    /** 关键词未匹配时的提示（空表示无提示） */
    private String unmatchedKeyword = "";
    /** 用户确认发送的报文；取消时为 null */
    private List<HttpRequestResponse> selectedPackets;
    /** 正在重建域名下拉框：避免触发一次多余的重新筛选 */
    private boolean rebuildingHosts;
    /** 后台读取计数：仅最后一次读取结果生效 */
    private int loadToken;
    /** 弹窗已关闭：丢弃迟到的后台读取结果 */
    private boolean closed;

    public PacketSelectDialog(Component parent, PacketSourceService service,
                              PacketSourceService.Source source, String keywordHint,
                              String requestReason) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), "",
                ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.source = source;
        this.keywordHint = keywordHint == null ? "" : keywordHint;
        this.tableModel = new DefaultTableModel(0, COLUMN_KEYS.length) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.table = new JTable(tableModel);
        this.requestReason = requestReason;

        buildLayout();
        bindEvents();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
        textKeyword.setText(this.keywordHint);
        reload();
        pack();
        setSize(Math.max(getWidth(), 820), Math.max(getHeight(), 480));
        setLocationRelativeTo(parent);
    }

    private void buildLayout() {
        setLayout(new BorderLayout(6, 6));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(8, 10, 0, 10));
        labelHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        labelSource.setAlignmentX(Component.LEFT_ALIGNMENT);
        labelNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        // 加粗走字体属性，不用 <html><b>：Burp 的 LookAndFeel 不渲染 JLabel 的 HTML，
        // 那样会把 <html><b> 标签原样显示出来
        Font hintFont = labelHint.getFont();
        if (hintFont != null) {
            labelHint.setFont(hintFont.deriveFont(Font.BOLD));
        }
        labelNote.setForeground(new Color(0xB86E00));
        top.add(labelHint);
        top.add(Box.createVerticalStrut(4));
        top.add(labelSource);
        top.add(Box.createVerticalStrut(4));
        top.add(labelNote);
        top.add(Box.createVerticalStrut(6));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterRow.add(labelKeyword);
        filterRow.add(textKeyword);
        filterRow.add(btnFilter);
        filterRow.add(btnReset);
        filterRow.add(Box.createHorizontalStrut(10));
        filterRow.add(labelHost);
        comboHost.setPreferredSize(new Dimension(190, comboHost.getPreferredSize().height));
        filterRow.add(comboHost);
        top.add(filterRow);
        top.add(Box.createVerticalStrut(4));

        JPanel suggestRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        suggestRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        suggestRow.add(labelSuggest);
        suggestRow.add(btnSuggestObjectId);
        suggestRow.add(btnSuggestParams);
        suggestRow.add(btnSuggestOk);
        top.add(suggestRow);
        add(top, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(60);
        table.getColumnModel().getColumn(1).setPreferredWidth(160);
        table.getColumnModel().getColumn(2).setPreferredWidth(320);
        table.getColumnModel().getColumn(3).setPreferredWidth(60);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(5).setPreferredWidth(50);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        bottom.add(labelSummary, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(btnSendAll);
        buttons.add(btnSend);
        buttons.add(btnCancel);
        bottom.add(buttons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
    }

    private void bindEvents() {
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                closed = true;
            }
        });
        btnFilter.addActionListener(e -> reload());
        textKeyword.addActionListener(e -> reload());
        btnReset.addActionListener(e -> {
            textKeyword.setText("");
            reload();
        });
        comboHost.addActionListener(e -> {
            if (!rebuildingHosts) {
                refillTable();
            }
        });
        btnSuggestObjectId.addActionListener(e -> selectWhere(c ->
                PacketSourceService.hasObjectIdentifier(c.url())));
        btnSuggestParams.addActionListener(e -> selectWhere(c -> c.url().contains("?")));
        btnSuggestOk.addActionListener(e -> selectWhere(c ->
                c.statusCode() >= 200 && c.statusCode() < 300));
        btnSend.addActionListener(e -> confirmSelection());
        btnSendAll.addActionListener(e -> confirmAll());
        btnCancel.addActionListener(e -> {
            selectedPackets = null;
            dispose();
        });
        table.getSelectionModel().addListSelectionListener(e -> updateSummary());
        // ESC 关闭
        getRootPane().registerKeyboardAction(e -> {
            selectedPackets = null;
            dispose();
        }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * 重新读取候选：读取放到后台线程，EDT 上先显示"读取中"，
     * 避免上万条历史时弹窗长时间空白（看起来像"没有域名/没有数据"）。
     */
    private void reload() {
        String keyword = textKeyword.getText() == null ? "" : textKeyword.getText().trim();
        I18n i18n = I18n.getInstance();
        labelNote.setText("");
        labelSummary.setText(i18n.format("ai", "dialog.packets.loading", sourceName()));
        btnSend.setEnabled(false);
        btnSendAll.setEnabled(false);
        btnFilter.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        int token = ++loadToken;
        Thread loader = new Thread(() -> {
            LoadResult result = loadCandidates(keyword);
            SwingUtilities.invokeLater(() -> {
                if (token != loadToken || closed) {
                    return;
                }
                applyLoaded(result);
            });
        }, "authkit-packet-load");
        loader.setDaemon(true);
        loader.start();
    }

    /** 后台读取：关键词未命中时退化为更宽松的关键词，仍无候选则不过滤展示全部 */
    private LoadResult loadCandidates(String keyword) {
        List<PacketSourceService.Candidate> loaded = service.read(source, keyword);
        if (loaded.isEmpty() && !keyword.isEmpty()) {
            String token = PacketSourceService.sanitizeKeyword(keyword);
            if (!token.isEmpty() && !token.equalsIgnoreCase(keyword)) {
                loaded = service.read(source, token);
            }
            if (loaded.isEmpty()) {
                // 关键词过滤把候选全滤掉了：改为展示全部候选，避免弹窗空白
                return new LoadResult(service.read(source, ""), keyword);
            }
        }
        return new LoadResult(loaded, "");
    }

    private void applyLoaded(LoadResult result) {
        candidates = result.candidates();
        unmatchedKeyword = result.unmatchedKeyword();
        setCursor(Cursor.getDefaultCursor());
        btnFilter.setEnabled(true);
        btnSendAll.setEnabled(!candidates.isEmpty());
        rebuildHostCombo();
        refillTable();
        I18n i18n = I18n.getInstance();
        if (!unmatchedKeyword.isEmpty()) {
            labelNote.setText(i18n.format("ai", "dialog.packets.keywordUnmatched", unmatchedKeyword));
        } else if (candidates.isEmpty()) {
            labelNote.setText(i18n.format("ai", "dialog.packets.empty", sourceName()));
        }
    }

    /** 用候选中的域名（Host）重建下拉框，尽量保留用户当前选择 */
    private void rebuildHostCombo() {
        String previous = (String) comboHost.getSelectedItem();
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (PacketSourceService.Candidate candidate : candidates) {
            String host = hostOf(candidate.url());
            if (!host.isEmpty()) {
                hosts.add(host);
            }
        }
        rebuildingHosts = true;
        comboHost.removeAllItems();
        comboHost.addItem(allHostsLabel());
        for (String host : hosts) {
            comboHost.addItem(host);
        }
        if (previous != null && hosts.contains(previous)) {
            comboHost.setSelectedItem(previous);
        } else {
            comboHost.setSelectedIndex(0);
        }
        rebuildingHosts = false;
    }

    /** 按当前域名选择刷新表格（不重新读取数据源） */
    private void refillTable() {
        String selectedHost = (String) comboHost.getSelectedItem();
        boolean allHosts = selectedHost == null || selectedHost.equals(allHostsLabel());
        visible = new ArrayList<>();
        for (PacketSourceService.Candidate candidate : candidates) {
            if (allHosts || selectedHost.equals(hostOf(candidate.url()))) {
                visible.add(candidate);
            }
        }
        tableModel.setRowCount(0);
        for (PacketSourceService.Candidate candidate : visible) {
            tableModel.addRow(new Object[]{
                    candidate.method(), hostOf(candidate.url()), pathOf(candidate.url()),
                    candidate.statusCode() == 0 ? "-" : candidate.statusCode(),
                    candidate.length(), candidate.score()});
        }
        preselectSuggested();
        updateSummary();
    }

    /** 建议预选：评分达到阈值的候选（上限 MAX_SEND 条） */
    private void preselectSuggested() {
        table.clearSelection();
        int count = 0;
        for (int row = 0; row < visible.size() && count < MAX_SEND; row++) {
            if (visible.get(row).score() >= SUGGEST_SCORE) {
                table.addRowSelectionInterval(row, row);
                count++;
            }
        }
        if (count > 0 && table.getSelectedRow() >= 0) {
            table.scrollRectToVisible(table.getCellRect(table.getSelectedRow(), 0, true));
        }
    }

    /** 按条件追加选中（保留已有选择） */
    private void selectWhere(java.util.function.Predicate<PacketSourceService.Candidate> predicate) {
        for (int row = 0; row < visible.size(); row++) {
            if (predicate.test(visible.get(row))) {
                table.addRowSelectionInterval(row, row);
            }
        }
        updateSummary();
    }

    /** 确认发送当前选中的报文 */
    private void confirmSelection() {
        List<HttpRequestResponse> packets = collectSelected();
        if (packets.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().text("ai", "dialog.packets.noneSelected"),
                    I18n.getInstance().text("ai", "dialog.packets.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (packets.size() > MAX_SEND) {
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("ai", "dialog.packets.capWarning", MAX_SEND),
                    I18n.getInstance().text("ai", "dialog.packets.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        selectedPackets = packets;
        dispose();
    }

    /** 全部发送：需用户二次确认（"除非用户要求"才全量） */
    private void confirmAll() {
        I18n i18n = I18n.getInstance();
        int total = visible.size();
        int confirm = JOptionPane.showConfirmDialog(this,
                i18n.format("ai", "dialog.packets.confirmAll.message",
                        Math.min(total, MAX_SEND), total),
                i18n.text("ai", "dialog.packets.confirmAll.title"),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        table.clearSelection();
        int limit = Math.min(total, MAX_SEND);
        for (int row = 0; row < limit; row++) {
            table.addRowSelectionInterval(row, row);
        }
        confirmSelection();
    }

    /** 收集选中行对应的报文 */
    private List<HttpRequestResponse> collectSelected() {
        List<HttpRequestResponse> packets = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < visible.size()) {
                packets.add(visible.get(modelRow).requestResponse());
            }
        }
        return packets;
    }

    private void updateSummary() {
        I18n i18n = I18n.getInstance();
        labelSummary.setText(i18n.format("ai", "dialog.packets.summary",
                collectSelected().size(), visible.size(), MAX_SEND));
        btnSend.setEnabled(!table.getSelectionModel().isSelectionEmpty());
    }

    private String sourceName() {
        return I18n.getInstance().text("ai", source == PacketSourceService.Source.PROXY_HISTORY
                ? "dialog.packets.source.proxy" : "dialog.packets.source.sitemap");
    }

    private String allHostsLabel() {
        return I18n.getInstance().text("ai", "dialog.packets.host.all");
    }

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        String sourceName = sourceName();
        setTitle(i18n.text("ai", "dialog.packets.title"));
        labelHint.setText(i18n.format("ai", "dialog.packets.hint", sourceName));
        labelSource.setText(requestReason == null || requestReason.isBlank()
                ? i18n.format("ai", "dialog.packets.reason.none", sourceName)
                : i18n.format("ai", "dialog.packets.reason", sourceName, requestReason));
        labelKeyword.setText(i18n.text("ai", "dialog.packets.keyword"));
        labelHost.setText(i18n.text("ai", "dialog.packets.host"));
        labelSuggest.setText(i18n.text("ai", "dialog.packets.suggest"));
        btnFilter.setText(i18n.text("ai", "dialog.packets.filter"));
        btnReset.setText(i18n.text("ai", "dialog.packets.reset"));
        btnSuggestObjectId.setText(i18n.text("ai", "dialog.packets.suggest.objectId"));
        btnSuggestParams.setText(i18n.text("ai", "dialog.packets.suggest.params"));
        btnSuggestOk.setText(i18n.text("ai", "dialog.packets.suggest.ok"));
        btnSend.setText(i18n.text("ai", "dialog.packets.send"));
        btnSendAll.setText(i18n.text("ai", "dialog.packets.sendAll"));
        btnCancel.setText(i18n.text("ai", "dialog.packets.cancel"));
        for (int i = 0; i < COLUMN_KEYS.length; i++) {
            table.getColumnModel().getColumn(i).setHeaderValue(i18n.text("ai", COLUMN_KEYS[i]));
        }
        table.getTableHeader().repaint();
        rebuildHostCombo();
        refillTable();
    }

    /** 模态展示，返回用户确认发送的报文；取消返回 null */
    public List<HttpRequestResponse> showDialog() {
        setVisible(true);
        return selectedPackets;
    }

    /** 从 URL 中取 host（URI 解析失败时退化为手工解析，保证域名列不为空） */
    static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url).getHost();
            if (host != null && !host.isEmpty()) {
                return host;
            }
        } catch (Exception ignored) {
            // URL 含非法字符时 URI 解析会失败，走下面的手工解析
        }
        return authorityOf(url);
    }

    /** 从 URL 中取 path?query（解析失败时退化为手工解析） */
    static String pathOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getRawPath();
            if (path != null) {
                return uri.getRawQuery() != null ? path + "?" + uri.getRawQuery() : path;
            }
        } catch (Exception ignored) {
            // 同上：退化为手工解析
        }
        String rest = stripScheme(url);
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(slash) : "/";
    }

    /** 手工解析 URL 的 authority 部分（host[:port]） */
    private static String authorityOf(String url) {
        String rest = stripScheme(url);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String authority = rest.substring(0, end);
        int at = authority.lastIndexOf('@');
        return at >= 0 ? authority.substring(at + 1) : authority;
    }

    private static String stripScheme(String url) {
        int schemeEnd = url.indexOf("://");
        return schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
    }

    /** 一次读取的结果：候选 + 未命中的关键词（用于提示） */
    private record LoadResult(List<PacketSourceService.Candidate> candidates,
                              String unmatchedKeyword) {
    }
}