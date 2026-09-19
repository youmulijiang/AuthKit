package view.component;

import burp.api.montoya.http.message.HttpRequestResponse;
import core.AiChatService;
import model.AiConfigModel;
import utils.I18n;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 对话面板
 * <p>
 * 位于主界面顶层 AI 选项卡中，用户与 LLM 对话分析数据包越权。
 * 聊天记录以聊天软件样式展示：用户消息靠右、LLM 回复靠左（含思考过程），
 * 面板只读不可编辑。AI 连接配置（API Key / Base URL / 请求格式等）位于
 * Configuration 选项卡，通过共享的 {@link AiConfigModel} 读取。
 * 支持右键菜单直接将选中数据包发送到本面板进行 AI 分析。
 */
public class AiChatPanel extends JPanel {

    /** 系统提示词：要求模型以越权测试专家身份分析数据包 */
    private static final String SYSTEM_PROMPT =
            "You are an expert web security analyst specializing in authorization testing "
                    + "(BOLA / IDOR / privilege escalation). Analyze the provided HTTP packets, "
                    + "identify authentication/authorization fields, compare object identifiers "
                    + "between users, and report potential broken access control findings with "
                    + "actionable verification steps. Answer in the same language as the user.";

    private final AiConfigModel configModel;
    private final AiChatService chatService;

    // ===== 对话区（HTML 聊天样式，只读） =====
    private final JEditorPane editorChat;
    private final JTextArea textAreaInput;
    private final JButton btnSend;
    private final JButton btnClear;

    // ===== i18n =====
    private TitledBorder borderChat;
    /** thinking 占位元素自增序号，用于生成唯一元素 id */
    private int thinkingElementSeq = 0;
    /** 是否正在等待模型回复 */
    private volatile boolean awaitingReply = false;

    /** 对话历史（含 system 提示词，按时间顺序） */
    private final List<AiChatService.ChatMessage> history = new ArrayList<>();

    public AiChatPanel(AiConfigModel configModel) {
        this.configModel = configModel;
        this.chatService = new AiChatService(configModel);

        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

        this.editorChat = new JEditorPane("text/html", "");
        this.editorChat.setEditable(false);
        this.editorChat.setFont(monoFont);
        this.editorChat.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        this.textAreaInput = new JTextArea(4, 40);
        this.textAreaInput.setFont(monoFont);
        this.textAreaInput.setLineWrap(true);
        this.textAreaInput.setWrapStyleWord(true);

        this.btnSend = new JButton();
        this.btnSend.setBackground(new Color(0x4C, 0xAF, 0x50));
        this.btnSend.setForeground(Color.WHITE);
        this.btnSend.setFocusPainted(false);
        this.btnClear = new JButton();

        initLayout();
        bindEvents();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
    }

    /** 初始化布局：上方聊天记录 + 下方输入区 */
    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // 上方：聊天记录（只读 HTML，用户靠右 / LLM 靠左）
        borderChat = new TitledBorder("");
        JScrollPane scrollChat = new JScrollPane(editorChat);
        scrollChat.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollChat.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1), ""));
        borderChat = (TitledBorder) scrollChat.getBorder();
        add(scrollChat, BorderLayout.CENTER);

        // 下方：输入区
        JPanel panelInput = new JPanel(new BorderLayout(5, 5));

        JScrollPane scrollInput = new JScrollPane(textAreaInput);
        panelInput.add(scrollInput, BorderLayout.CENTER);

        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelButtons.add(btnClear);
        panelButtons.add(btnSend);
        panelButtons.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        panelInput.add(panelButtons, BorderLayout.SOUTH);

        panelInput.setBorder(BorderFactory.createEmptyBorder(5, 2, 2, 2));
        add(panelInput, BorderLayout.SOUTH);
    }

    /** 绑定按钮事件 */
    private void bindEvents() {
        btnSend.addActionListener(e -> doSend());
        btnClear.addActionListener(e -> doClear());
    }

    /** 发送用户输入 */
    private void doSend() {
        String input = textAreaInput.getText().trim();
        if (input.isEmpty() || awaitingReply) {
            return;
        }
        textAreaInput.setText("");
        ensureSystemPrompt();
        history.add(AiChatService.ChatMessage.user(input));
        appendUserBubble(input);
        dispatchChatRequest();
    }

    /** 保证 system 提示词存在且位于首位 */
    private void ensureSystemPrompt() {
        if (history.isEmpty() || !"system".equals(history.get(0).role())) {
            history.add(0, AiChatService.ChatMessage.system(SYSTEM_PROMPT));
        }
    }

    /** 后台线程调用 LLM，先插入 thinking 占位，回复到达后替换为气泡 */
    private void dispatchChatRequest() {
        setAwaiting(true);
        String thinkingId = insertThinkingIndicator();
        List<AiChatService.ChatMessage> snapshot = new ArrayList<>(history);
        Thread thread = new Thread(() -> {
            try {
                AiChatService.Reply reply = chatService.chatDetailed(snapshot);
                SwingUtilities.invokeLater(() -> {
                    history.add(AiChatService.ChatMessage.assistant(reply.content()));
                    removeThinkingIndicator(thinkingId);
                    appendAssistantBubble(reply.reasoning(), reply.content());
                    setAwaiting(false);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    removeThinkingIndicator(thinkingId);
                    appendErrorBubble(ex.getMessage());
                    setAwaiting(false);
                });
            }
        }, "authkit-ai-chat");
        thread.setDaemon(true);
        thread.start();
    }

    /** 清空对话 */
    private void doClear() {
        if (awaitingReply) {
            return;
        }
        history.clear();
        chatBubbles.clear();
        renderChat();
    }

    /**
     * 右键菜单发送：将选中数据包构建为分析提示词并自动发送给模型
     *
     * @param packets 选中的请求响应列表
     */
    public void sendPacketsAuto(List<HttpRequestResponse> packets) {
        if (packets == null || packets.isEmpty() || awaitingReply) {
            return;
        }
        String packetText = buildPacketPrompt(packets);
        ensureSystemPrompt();
        history.add(AiChatService.ChatMessage.user(packetText));
        // 聊天窗口中用户侧显示摘要，避免整包刷屏
        appendUserBubble(summarizePackets(packets));
        dispatchChatRequest();
    }

    /** 生成数据包摘要（方法 + URL + 状态码），用于用户气泡展示 */
    private String summarizePackets(List<HttpRequestResponse> packets) {
        StringBuilder sb = new StringBuilder(
                I18n.getInstance().text("ai", "chat.packetSummary"));
        int count = 0;
        for (HttpRequestResponse item : packets) {
            if (item == null || item.request() == null) {
                continue;
            }
            count++;
            sb.append(count == 1 ? " " : ", ")
                    .append(item.request().method())
                    .append(' ')
                    .append(item.request().url());
            if (item.response() != null) {
                sb.append(" → ").append(item.response().statusCode());
            }
            if (count >= 5) {
                sb.append(I18n.getInstance().text("ai", "chat.morePackets"));
                break;
            }
        }
        return sb.toString();
    }

    /** 构建数据包分析提示词（限制数量与响应长度） */
    private String buildPacketPrompt(List<HttpRequestResponse> packets) {
        int max = configModel.getMaxPackets();
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.getInstance().text("ai", "prompt.packetHeader")).append('\n');
        int count = 0;
        for (HttpRequestResponse item : packets) {
            if (item == null || item.request() == null) {
                continue;
            }
            if (count >= max) {
                sb.append(I18n.getInstance().format("ai", "prompt.packetTruncated", max))
                        .append('\n');
                break;
            }
            count++;
            sb.append("\n===== Packet ").append(count).append(" =====\n");
            sb.append("### Request:\n").append(item.request().toString()).append('\n');
            if (item.response() != null) {
                sb.append("### Response:\n")
                        .append(truncatePacket(item.response().toString(), 8000)).append('\n');
            }
        }
        return sb.toString();
    }

    // ===== HTML 聊天渲染 =====
    // 采用“气泡片段列表 + 每次重建整份 HTML”的渲染方式：
    // JEditorPane 的 HTMLDocument 增量插入（insertAfterEnd）行为不可靠，
    // 会导致气泡内容丢失，因此统一累积片段后整体 setText。

    /** 已渲染的聊天气泡 HTML 片段（按时间顺序） */
    private final List<String> chatBubbles = new ArrayList<>();

    /** 追加用户消息气泡（靠右，蓝色） */
    private void appendUserBubble(String text) {
        String bubble = "<table width='100%' cellspacing='0' cellpadding='0'><tr>"
                + "<td align='right'>"
                + "<table cellspacing='0' cellpadding='6' bgcolor='#cfe6ff'><tr>"
                + "<td><b>" + escapeHtml(I18n.getInstance().text("ai", "label.userPrefix"))
                + "</b> " + escapeHtml(text)
                + "</td></tr></table></td></tr></table><br>";
        addChatBubble(bubble);
    }

    /** 追加 LLM 回复气泡（靠左，浅灰；reasoning 非空时先展示思考块） */
    private void appendAssistantBubble(String reasoning, String content) {
        StringBuilder bubble = new StringBuilder();
        bubble.append("<table width='100%' cellspacing='0' cellpadding='0'><tr>")
                .append("<td align='left'>")
                .append("<table cellspacing='0' cellpadding='6' bgcolor='#e8f5e9'><tr>")
                .append("<td><b>").append(escapeHtml(
                        I18n.getInstance().text("ai", "label.assistantPrefix"))).append("</b> ");
        if (reasoning != null && !reasoning.isBlank()) {
            bubble.append("<p><i><font color='#888888'><b>").append(escapeHtml(
                            I18n.getInstance().text("ai", "label.thinking")))
                    .append("</b>:<br>").append(escapeHtml(reasoning))
                    .append("</font></i></p>");
        }
        bubble.append(escapeHtml(content));
        bubble.append("</td></tr></table></td></tr></table><br>");
        addChatBubble(bubble.toString());
    }

    /** 追加错误气泡（靠左，浅红） */
    private void appendErrorBubble(String message) {
        String bubble = "<table width='100%' cellspacing='0' cellpadding='0'><tr>"
                + "<td align='left'>"
                + "<table cellspacing='0' cellpadding='6' bgcolor='#fdecea'><tr>"
                + "<td>" + escapeHtml(I18n.getInstance().format(
                        "ai", "message.requestFailed", message == null ? "" : message))
                + "</td></tr></table></td></tr></table><br>";
        addChatBubble(bubble);
    }

    /** 插入 thinking 占位（灰色斜体），返回占位元素 id */
    private String insertThinkingIndicator() {
        String id = "authkit-thinking-" + (++thinkingElementSeq);
        String html = "<p id='" + id + "'><i><font color='#888888'>"
                + escapeHtml(I18n.getInstance().text("ai", "chat.thinking"))
                + " ...</font></i></p>";
        addChatBubble(html);
        return id;
    }

    /** 移除 thinking 占位元素（重建时按 id 过滤） */
    private void removeThinkingIndicator(String id) {
        chatBubbles.removeIf(fragment -> fragment.contains("id='" + id + "'"));
        renderChat();
    }

    /** 追加气泡片段并重渲染聊天区（须在 EDT 调用） */
    private void addChatBubble(String html) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> addChatBubble(html));
            return;
        }
        chatBubbles.add(html);
        renderChat();
    }

    /** 用当前气泡片段重建整份 HTML 文档并滚动到底部 */
    private void renderChat() {
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='width:100%'>");
        for (String fragment : chatBubbles) {
            html.append(fragment);
        }
        html.append("</body></html>");
        editorChat.setText(html.toString());
        editorChat.setCaretPosition(editorChat.getDocument().getLength());
    }

    private static String truncatePacket(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n... (truncated)";
    }

    /** HTML 转义 */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
    }

    /** 切换等待状态，禁用发送按钮 */
    private void setAwaiting(boolean awaiting) {
        awaitingReply = awaiting;
        btnSend.setEnabled(!awaiting);
        btnSend.setText(awaiting
                ? I18n.getInstance().text("ai", "button.thinking")
                : I18n.getInstance().text("ai", "button.send"));
    }

    /** 刷新国际化文本 */
    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        if (borderChat != null) {
            borderChat.setTitle(i18n.text("ai", "section.chat"));
        }
        btnSend.setText(awaitingReply
                ? i18n.text("ai", "button.thinking")
                : i18n.text("ai", "button.send"));
        btnClear.setText(i18n.text("ai", "button.clear"));
        editorChat.setToolTipText(i18n.text("ai", "tooltip.transcript"));
        revalidate();
        repaint();
    }
}
