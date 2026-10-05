package view.dialog;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import core.PacketSourceService;
import utils.I18n;
import view.component.AiChatPanel;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * AI 越权扫描弹窗（右键菜单入口，布局与其他专项扫描弹窗一致）。
 * <p>
 * 顶部配置区：**内置提示词（可编辑）** + 待分析数据包（可多个，可从 Proxy 历史 / 站点地图挑选）；
 * 中部：AI 实际发送的数据包表格；下方：请求 / 响应查看器。
 * <p>
 * 与其它扫描弹窗的差异：扫描由 AI 对话驱动，发包仍需用户在对话流中逐次批准，
 * 因此本弹窗为**非模态**，用户可在扫描过程中回到 AI 选项卡操作。
 */
public class AiAuthScanDialog extends JDialog implements AiChatPanel.ToolSendListener {

    private final PacketSourceService packetSourceService;
    /** 待分析的数据包（用户确认后交给 AI） */
    private final List<HttpRequestResponse> packets = new ArrayList<>();
    /** AI 实际发送的数据包（按发送顺序） */
    private final List<HttpRequestResponse> sentPackets = new ArrayList<>();

    /** 待分析数据包表格（配置区） */
    private final DefaultTableModel packetsModel;
    private final JTable packetsTable;
    /** AI 发送的数据包表格（结果区） */
    private final DefaultTableModel sentModel;
    private final JTable sentTable;
    private final JTextArea textPrompt = new JTextArea(5, 60);
    private final JComboBox<String> comboSource = new JComboBox<>();
    private final JButton btnPick = new JButton();
    private final JButton btnClear = new JButton();
    private final JButton btnStart = new JButton();
    private final JButton btnClose = new JButton();
    private final JLabel labelSource = new JLabel();
    private final JLabel labelPrompt = new JLabel();
    private final JLabel labelPromptHint = new JLabel();
    private final JLabel labelCount = new JLabel();
    private final JLabel labelSent = new JLabel();
    private final JLabel labelStatus = new JLabel();
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;

    /** 开始扫描回调：(待分析数据包, 用户编辑后的提示词) */
    private BiConsumer<List<HttpRequestResponse>, String> startHandler = (items, prompt) -> {};

    /** 关闭回调：用于注销发包监听 */
    private Runnable closeHandler = () -> {};

    private boolean startNotified;

    public AiAuthScanDialog(MontoyaApi api, Component parent,
                            PacketSourceService packetSourceService,
                            List<HttpRequestResponse> initialPackets) {
        super(parent == null ? null : SwingUtilities.getWindowAncestor(parent), "",
                ModalityType.MODELESS);
        this.packetSourceService = packetSourceService;
        if (initialPackets != null) {
            for (HttpRequestResponse item : initialPackets) {
                if (item != null && item.request() != null) {
                    packets.add(item);
                }
            }
        }
        this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.packetsModel = readOnlyModel(4);
        this.packetsTable = new JTable(packetsModel);
        this.sentModel = readOnlyModel(4);
        this.sentTable = new JTable(sentModel);

        initLayout(parent);
        bindEvents();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
        refreshPackets();
    }

    /** 设置表头并刷新（国际化切换时重建） */
    private static void applyHeaders(JTable target, String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            target.getColumnModel().getColumn(i).setHeaderValue(headers[i]);
        }
        target.getTableHeader().repaint();
    }

    /** 创建只读表格模型 */
    private static DefaultTableModel readOnlyModel(int columns) {
        return new DefaultTableModel(0, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void initLayout(Component parent) {
        setLayout(new BorderLayout(8, 8));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeHandler.run();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                closeHandler.run();
            }
        });

        add(buildConfigPanel(), BorderLayout.NORTH);
        add(buildMainSplit(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        setSize(1100, 760);
        setLocationRelativeTo(parent);
    }

    /** 配置区：数据包选择 + 可编辑提示词 */
    private JPanel buildConfigPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
                I18n.getInstance().text("auth_context_menu", "menu.aiScan")));

        // 数据包选择行
        JPanel pickRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        pickRow.add(labelSource);
        pickRow.add(comboSource);
        pickRow.add(btnPick);
        pickRow.add(btnClear);
        pickRow.add(labelCount);
        pickRow.add(Box.createHorizontalStrut(16));
        pickRow.add(btnStart);
        pickRow.add(btnClose);
        pickRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(pickRow);

        // 待分析数据包表格
        packetsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        packetsTable.setAutoCreateRowSorter(true);
        JScrollPane scrollPackets = new JScrollPane(packetsTable);
        scrollPackets.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPackets.setPreferredSize(new Dimension(0, 110));
        scrollPackets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        panel.add(scrollPackets);

        // 可编辑提示词
        panel.add(Box.createVerticalStrut(4));
        // 加粗/灰色走字体与前景色属性，不用 <html><b>/<font>：
        // Burp 的 LookAndFeel 不渲染 JLabel 的 HTML，那样会把标签原样显示出来
        Font promptFont = labelPrompt.getFont();
        if (promptFont != null) {
            labelPrompt.setFont(promptFont.deriveFont(Font.BOLD));
        }
        labelPromptHint.setForeground(new Color(0x88, 0x88, 0x88));
        JPanel promptTitleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        promptTitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        promptTitleRow.add(labelPrompt);
        promptTitleRow.add(labelPromptHint);
        panel.add(promptTitleRow);
        textPrompt.setLineWrap(true);
        textPrompt.setWrapStyleWord(true);
        textPrompt.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scrollPrompt = new JScrollPane(textPrompt);
        scrollPrompt.setAlignmentX(Component.LEFT_ALIGNMENT);
        scrollPrompt.setPreferredSize(new Dimension(0, 90));
        scrollPrompt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        panel.add(scrollPrompt);
        return panel;
    }

    /** 中部：AI 发送的数据包表格 + 请求/响应查看器 */
    private JSplitPane buildMainSplit() {
        sentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sentTable.setAutoCreateRowSorter(true);
        sentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = sentTable.getSelectedRow();
                if (row >= 0) {
                    int modelRow = sentTable.convertRowIndexToModel(row);
                    if (modelRow >= 0 && modelRow < sentPackets.size()) {
                        showMessage(sentPackets.get(modelRow));
                    }
                }
            }
        });

        JSplitPane messageSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapEditor(I18n.getInstance().text("message", "tab.request"),
                        requestEditor.uiComponent()),
                wrapEditor(I18n.getInstance().text("message", "tab.response"),
                        responseEditor.uiComponent()));
        messageSplit.setResizeWeight(0.5);

        JPanel sentPanel = new JPanel(new BorderLayout());
        labelSent.setBorder(BorderFactory.createEmptyBorder(4, 6, 2, 6));
        Font sentFont = labelSent.getFont();
        if (sentFont != null) {
            labelSent.setFont(sentFont.deriveFont(Font.BOLD));
        }
        sentPanel.add(labelSent, BorderLayout.NORTH);
        sentPanel.add(new JScrollPane(sentTable), BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sentPanel, messageSplit);
        mainSplit.setResizeWeight(0.4);
        return mainSplit;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        bar.add(labelStatus, BorderLayout.CENTER);
        return bar;
    }

    private void bindEvents() {
        btnPick.addActionListener(e -> pickPackets());
        btnClear.addActionListener(e -> {
            packets.clear();
            refreshPackets();
        });
        btnStart.addActionListener(e -> {
            if (packets.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        I18n.getInstance().text("ai", "dialog.aiScan.noPackets"),
                        I18n.getInstance().text("ai", "dialog.aiScan.title"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            startNotified = true;
            startHandler.accept(new ArrayList<>(packets), textPrompt.getText());
        });
        btnClose.addActionListener(e -> dispose());
    }

    /** 从所选来源挑选数据包（经用户勾选弹窗，不默认全量） */
    private void pickPackets() {
        PacketSourceService.Source source = comboSource.getSelectedIndex() == 0
                ? PacketSourceService.Source.PROXY_HISTORY
                : PacketSourceService.Source.SITEMAP;
        PacketSelectDialog dialog = new PacketSelectDialog(this, packetSourceService, source, "",
                I18n.getInstance().text("ai", "dialog.aiScan.pickReason"));
        List<HttpRequestResponse> picked = dialog.showDialog();
        if (picked == null || picked.isEmpty()) {
            return;
        }
        packets.clear();
        packets.addAll(picked);
        refreshPackets();
    }

    /** 刷新待分析数据包表格与计数 */
    private void refreshPackets() {
        I18n i18n = I18n.getInstance();
        packetsModel.setRowCount(0);
        for (HttpRequestResponse item : packets) {
            String url = item.request().url();
            packetsModel.addRow(new Object[]{
                    item.request().method(),
                    PacketSelectDialog.hostOf(url),
                    PacketSelectDialog.pathOf(url),
                    item.response() != null ? item.response().statusCode() : "-"});
        }
        labelCount.setText(i18n.format("ai", "dialog.aiScan.count", packets.size()));
        btnStart.setEnabled(!packets.isEmpty());
    }

    /**
     * 开始扫描回调（由装配根注入）：切换选项卡并启动 AI 对话，
     * 同时把本弹窗注册为发包监听。
     *
     * @param handler (待分析数据包, 提示词) -> 启动扫描
     */
    public void setStartHandler(BiConsumer<List<HttpRequestResponse>, String> handler) {
        this.startHandler = handler != null ? handler : (items, prompt) -> {};
    }

    /** 关闭回调（由装配根注入，用于注销发包监听） */
    public void setCloseHandler(Runnable handler) {
        this.closeHandler = handler != null ? handler : () -> {};
    }

    /** 展示弹窗（非模态，立即返回，扫描结果异步回填） */
    public void showDialog() {
        setVisible(true);
    }

    @Override
    public void onRequestSent(HttpRequestResponse requestResponse, int round) {
        if (requestResponse == null) {
            return;
        }
        Runnable update = () -> {
            sentPackets.add(requestResponse);
            String url = requestResponse.request() != null
                    ? requestResponse.request().url() : "";
            sentModel.addRow(new Object[]{
                    round,
                    requestResponse.request() != null ? requestResponse.request().method() : "",
                    url,
                    requestResponse.response() != null
                            ? requestResponse.response().statusCode() : "-"});
            int last = sentModel.getRowCount() - 1;
            if (last >= 0) {
                sentTable.setRowSelectionInterval(last, last);
            }
            labelStatus.setText(I18n.getInstance().format("ai", "dialog.aiScan.status.sent",
                    sentPackets.size()));
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void showMessage(HttpRequestResponse reqResp) {
        if (reqResp == null) {
            return;
        }
        if (reqResp.request() != null) {
            requestEditor.setRequest(reqResp.request());
        }
        if (reqResp.response() != null) {
            responseEditor.setResponse(reqResp.response());
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

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        setTitle(i18n.text("ai", "dialog.aiScan.title"));
        labelSent.setText(i18n.text("ai", "dialog.aiScan.sent"));
        labelSource.setText(i18n.text("ai", "dialog.aiScan.source"));
        labelPrompt.setText(i18n.text("ai", "dialog.aiScan.prompt"));
        labelPromptHint.setText(i18n.text("ai", "dialog.aiScan.promptHint"));
        btnPick.setText(i18n.text("ai", "dialog.aiScan.pick"));
        btnClear.setText(i18n.text("ai", "dialog.aiScan.clear"));
        btnStart.setText(i18n.text("ai", "dialog.aiScan.start"));
        btnClose.setText(i18n.text("ai", "dialog.aiScan.close"));
        if (textPrompt.getText().isBlank()) {
            textPrompt.setText(i18n.text("ai", "prompt.authScan"));
        }
        if (!startNotified) {
            labelStatus.setText(i18n.text("ai", "dialog.aiScan.status.waiting"));
        }
        String[] sentHeaders = {
                i18n.text("ai", "dialog.aiScan.column.round"),
                i18n.text("ai", "column.method"),
                i18n.text("ai", "column.url"),
                i18n.text("ai", "column.status")};
        String[] packetHeaders = {
                i18n.text("ai", "column.method"),
                i18n.text("ai", "column.host"),
                i18n.text("ai", "column.path"),
                i18n.text("ai", "column.status")};
        applyHeaders(sentTable, sentHeaders);
        applyHeaders(packetsTable, packetHeaders);
        comboSource.removeAllItems();
        comboSource.addItem(i18n.text("ai", "dialog.packets.source.proxy"));
        comboSource.addItem(i18n.text("ai", "dialog.packets.source.sitemap"));
        refreshPackets();
    }
}
