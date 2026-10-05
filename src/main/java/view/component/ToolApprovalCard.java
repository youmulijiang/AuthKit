package view.component;

import utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * 发包批准卡片（对话方式参考 burp-ai-agent 的 ToolApprovalCard）：
 * 模型请求发送 HTTP 数据包时在对话流中内联展示请求原文，提供
 * 拒绝 / 仅本次允许 / 会话内允许 三个决定按钮；点击后按钮行原位替换为
 * 决定回执（✔/✖ + 时间），允许并执行后响应内容追加到卡片底部，
 * 卡片作为完整记录留在对话中（拒绝在前，规避误点）。
 */
class ToolApprovalCard extends JPanel {

    /** 用户对发包请求的决定 */
    enum Decision { DENY, APPROVE_ONCE, APPROVE_SESSION }

    private static final Color CARD_BG = new Color(0xFF, 0xF8, 0xE1);
    private static final Color TITLE_COLOR = new Color(0xB8, 0x86, 0x0B);
    private static final Color OK_COLOR = new Color(0x2E, 0x7D, 0x32);
    private static final Color DENY_COLOR = new Color(0xC6, 0x28, 0x28);
    private static final Color GRAY = new Color(0x99, 0x99, 0x99);
    /** 响应展示截断长度（与工具气泡一致） */
    private static final int RESPONSE_MAX = 1500;

    private final Consumer<Decision> onDecision;
    /** 需要按视口宽度重测的正文窗格（请求 + 追加的响应） */
    private final List<JEditorPane> measuredPanes = new ArrayList<>();
    /** 决定按钮行（null 表示已作出决定） */
    private JComponent decisionRow;

    ToolApprovalCard(String requestText, Consumer<Decision> onDecision) {
        this.onDecision = onDecision;
        I18n i18n = I18n.getInstance();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(CARD_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BG.darker(), 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        // 头部：标题 + 时间戳
        JLabel titleLabel = new JLabel(i18n.text("ai", "chat.toolAsk"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setForeground(TITLE_COLOR);
        JLabel timeLabel = new JLabel(new SimpleDateFormat("HH:mm").format(new Date()));
        timeLabel.setFont(timeLabel.getFont().deriveFont(10f));
        timeLabel.setForeground(GRAY);
        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerLeft.setOpaque(false);
        headerLeft.add(titleLabel);
        headerLeft.add(timeLabel);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(headerLeft, BorderLayout.WEST);
        add(header);

        // 请求原文（等宽字体展示）
        String requestHtml = "<b>" + escape(i18n.text("ai", "chat.toolRequest")) + ":</b><br>"
                + "<font face='monospaced'>" + escape(requestText) + "</font>";
        add(createPane(requestHtml));

        // 决定按钮行
        decisionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        decisionRow.setOpaque(false);
        decisionRow.add(decisionButton(i18n.text("ai", "chat.toolDeny"), Decision.DENY));
        decisionRow.add(decisionButton(i18n.text("ai", "chat.toolApproveOnce"), Decision.APPROVE_ONCE));
        decisionRow.add(decisionButton(i18n.text("ai", "chat.toolApproveSession"), Decision.APPROVE_SESSION));
        add(decisionRow);
    }

    /** 将按钮行原位替换为决定回执（幂等：重复调用无效果） */
    void resolve(Decision decision) {
        if (decisionRow == null) {
            return;
        }
        int index = Math.max(getComponentZOrder(decisionRow), 0);
        remove(decisionRow);
        decisionRow = null;
        add(buildReceiptRow(decision), Math.min(index, getComponentCount()));
        revalidate();
        repaint();
    }

    /** 追加执行响应到卡片底部（允许并执行后由 EDT 调用） */
    void appendResponse(String result) {
        I18n i18n = I18n.getInstance();
        String html = "<b>" + escape(i18n.text("ai", "chat.toolResponse")) + ":</b><br>"
                + "<font face='monospaced' color='#555555'>"
                + escape(truncate(result, RESPONSE_MAX)) + "</font>";
        add(createPane(html));
        revalidate();
        repaint();
    }

    /** 视口宽度变化时重测所有正文窗格（HTML 排版高度依赖宽度） */
    void relayout(int viewportWidth) {
        // 扣除卡片内边距（左右各 10）与对齐面板边距，避免窗格超宽出现横滚条
        int width = viewportWidth > 0 ? viewportWidth - 36 : 600;
        for (JEditorPane pane : measuredPanes) {
            AiChatPanel.applyPaneSize(pane, width);
        }
        revalidate();
        repaint();
    }

    /** 构建决定回执行：glyph 承载颜色语义（✔ 允许 / ✖ 拒绝） */
    private JComponent buildReceiptRow(Decision decision) {
        I18n i18n = I18n.getInstance();
        boolean approved = decision != Decision.DENY;
        String verb = approved
                ? i18n.text("ai", "chat.toolApproved")
                        + (decision == Decision.APPROVE_SESSION
                                ? " (" + i18n.text("ai", "chat.toolApproveSession") + ")" : "")
                : i18n.text("ai", "chat.toolDenied");
        JLabel receipt = new JLabel((approved ? "✔ " : "✖ ") + verb
                + " · " + new SimpleDateFormat("HH:mm").format(new Date()));
        receipt.setFont(receipt.getFont().deriveFont(Font.PLAIN, 11f));
        receipt.setForeground(approved ? OK_COLOR : DENY_COLOR);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        row.setOpaque(false);
        row.add(receipt);
        return row;
    }

    private JButton decisionButton(String label, Decision decision) {
        JButton button = new JButton(label);
        button.setFont(button.getFont().deriveFont(11f));
        button.setFocusPainted(false);
        // 点击即解析卡片自身（按钮行原位变回执），再通知外部决定回调
        button.addActionListener(e -> {
            resolve(decision);
            onDecision.accept(decision);
        });
        return button;
    }

    /** 创建正文窗格（HTML、只读、与卡片同底色），登记待重测列表 */
    private JEditorPane createPane(String html) {
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setBackground(CARD_BG);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setBorder(BorderFactory.createEmptyBorder());
        measuredPanes.add(pane);
        return pane;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n... (truncated)";
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
    }
}
