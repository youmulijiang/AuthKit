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
 * AI 询问卡片（对话流内联，黄色气泡，交互模仿 Claude 的选择建议）。
 * <p>
 * AI 在不清楚用户意图时发出问题，并给出最多三个建议选项；用户可：
 * <ul>
 *   <li>点某个选项（按钮带序号，点击即作为回答）</li>
 *   <li>在自定义输入框里写自己的回答后发送</li>
 *   <li>取消（不回答，交由 AI 自行判断）</li>
 * </ul>
 * 作出选择后交互区原位替换为回执，卡片作为记录留在对话中。
 */
class AskUserCard extends JPanel {

    /** 用户回答：类型 + 文本 */
    record Answer(Kind kind, String text) {

        enum Kind { OPTION, CUSTOM, CANCEL }

        static Answer option(String text) {
            return new Answer(Kind.OPTION, text);
        }

        static Answer custom(String text) {
            return new Answer(Kind.CUSTOM, text);
        }

        static Answer cancel() {
            return new Answer(Kind.CANCEL, "");
        }
    }

    private static final Color CARD_BG = new Color(0xFF, 0xF3, 0xC4);
    private static final Color TITLE_COLOR = new Color(0xB8, 0x6E, 0x00);
    private static final Color GRAY = new Color(0x99, 0x99, 0x99);
    /** 序号符号（最多三个建议） */
    private static final String[] INDEX_MARKS = {"①", "②", "③"};
    /** 建议选项上限 */
    static final int MAX_OPTIONS = 3;

    private final Consumer<Answer> onAnswer;
    /** 需要按视口宽度重测的正文窗格 */
    private final List<JEditorPane> measuredPanes = new ArrayList<>();
    /** 交互区（选项 + 自定义输入 + 取消），作出选择后整体替换为回执 */
    private JComponent interactive;
    private final JTextField textCustom = new JTextField(18);

    AskUserCard(String question, List<String> options, Consumer<Answer> onAnswer) {
        this.onAnswer = onAnswer;
        I18n i18n = I18n.getInstance();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(CARD_BG);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BG.darker(), 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        add(buildHeader());
        add(createPane(escape(question)));

        interactive = new JPanel();
        interactive.setLayout(new BoxLayout(interactive, BoxLayout.Y_AXIS));
        interactive.setOpaque(false);
        interactive.add(Box.createVerticalStrut(4));
        interactive.add(buildOptions(options));
        interactive.add(Box.createVerticalStrut(2));
        interactive.add(buildCustomRow(i18n));
        interactive.add(Box.createVerticalStrut(2));
        interactive.add(buildCancelRow(i18n));
        add(interactive);
    }

    /** 头部：标题 + 时间戳 */
    private JComponent buildHeader() {
        I18n i18n = I18n.getInstance();
        JLabel titleLabel = new JLabel(i18n.text("ai", "chat.ask.title"));
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
        return header;
    }

    /** 建议选项：带序号的左对齐按钮，点击即回答 */
    private JComponent buildOptions(List<String> options) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        int count = 0;
        for (String option : options) {
            if (option == null || option.isBlank() || count >= MAX_OPTIONS) {
                continue;
            }
            JButton button = new JButton(INDEX_MARKS[count] + " " + option);
            button.setFont(button.getFont().deriveFont(11f));
            button.setFocusPainted(false);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(e -> choose(Answer.option(option)));
            panel.add(button);
            panel.add(Box.createVerticalStrut(2));
            count++;
        }
        return panel;
    }

    /** 自定义回答行：输入框 + 发送 */
    private JComponent buildCustomRow(I18n i18n) {
        textCustom.setFont(textCustom.getFont().deriveFont(11f));
        textCustom.setToolTipText(i18n.text("ai", "chat.ask.customPlaceholder"));
        JButton btnSend = new JButton(i18n.text("ai", "chat.ask.customSend"));
        btnSend.setFont(btnSend.getFont().deriveFont(11f));
        btnSend.setFocusPainted(false);
        btnSend.addActionListener(e -> submitCustom());
        textCustom.addActionListener(e -> submitCustom());
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new JLabel(i18n.text("ai", "chat.ask.customLabel")), BorderLayout.WEST);
        row.add(textCustom, BorderLayout.CENTER);
        row.add(btnSend, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** 取消行 */
    private JComponent buildCancelRow(I18n i18n) {
        JButton btnCancel = new JButton(i18n.text("ai", "chat.ask.cancel"));
        btnCancel.setFont(btnCancel.getFont().deriveFont(11f));
        btnCancel.setFocusPainted(false);
        btnCancel.addActionListener(e -> choose(Answer.cancel()));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(btnCancel);
        return row;
    }

    private void submitCustom() {
        String text = textCustom.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        choose(Answer.custom(text));
    }

    /** 记录回答：交互区原位替换为回执，再通知外部回调 */
    private void choose(Answer answer) {
        if (interactive == null) {
            return;
        }
        int index = Math.max(getComponentZOrder(interactive), 0);
        remove(interactive);
        interactive = null;
        add(buildReceipt(answer), Math.min(index, getComponentCount()));
        revalidate();
        repaint();
        onAnswer.accept(answer);
    }

    /** 回执行：选项/自定义/取消三种回执文案 */
    private JComponent buildReceipt(Answer answer) {
        I18n i18n = I18n.getInstance();
        String text;
        Color color;
        switch (answer.kind()) {
            case OPTION -> {
                text = "✔ " + i18n.format("ai", "chat.ask.receipt.option", answer.text());
                color = TITLE_COLOR;
            }
            case CUSTOM -> {
                text = "✔ " + i18n.format("ai", "chat.ask.receipt.custom", answer.text());
                color = TITLE_COLOR;
            }
            default -> {
                text = "✖ " + i18n.text("ai", "chat.ask.receipt.cancel");
                color = GRAY;
            }
        }
        JLabel receipt = new JLabel(text + " · " + new SimpleDateFormat("HH:mm").format(new Date()));
        receipt.setFont(receipt.getFont().deriveFont(Font.PLAIN, 11f));
        receipt.setForeground(color);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        row.setOpaque(false);
        row.add(receipt);
        return row;
    }

    /** 视口宽度变化时重测正文窗格（HTML 排版高度依赖宽度） */
    void relayout(int viewportWidth) {
        int width = viewportWidth > 0 ? viewportWidth - 36 : 600;
        for (JEditorPane pane : measuredPanes) {
            AiChatPanel.applyPaneSize(pane, width);
        }
        revalidate();
        repaint();
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
