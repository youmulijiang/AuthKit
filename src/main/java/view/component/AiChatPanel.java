package view.component;

import burp.api.montoya.http.message.HttpRequestResponse;
import core.AiChatService;
import core.AiSessionService;
import core.AiToolService;
import model.AiConfigModel;
import model.CompareSampleModel;
import model.MessageDataModel;
import utils.I18n;
import utils.ApiUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 对话面板
 * <p>
 * 位于主界面顶层 AI 选项卡中，用户与 LLM 对话分析数据包越权。
 * 聊天记录以聊天软件样式展示：用户消息靠右、LLM 回复靠左（含思考过程），
 * 面板只读不可编辑。AI 连接配置（API Key / Base URL / 请求格式等）位于
 * Configuration 选项卡，通过共享的 {@link AiConfigModel} 读取。
 * 支持右键菜单直接将选中数据包发送到本面板进行 AI 分析。
 * <p>
 * 附加能力：
 * <ul>
 *   <li>会话保存/加载：导出与恢复对话历史（JSON 文件）</li>
 *   <li>消息卡片：每条消息带彩色角色标签 + 时间戳头部，正文 Markdown 渲染，
 *       复制按钮悬停气泡时显示（对话方式参考 burp-ai-agent 项目）；
 *       思考中占位渲染为左侧灰色气泡，思考过程默认折叠、点击 ▶/▼ 切换展开</li>
 *   <li>发包批准卡片：AI 请求发送数据包时，对话流内联展示请求原文并提供
 *       拒绝 / 仅本次允许 / 会话内允许 三个决定按钮，点击后卡片原位变为决定回执；
 *       拒绝时向模型回传中性"未授权"反馈，会话内允许后后续发包自动执行</li>
 *   <li>按需拉取数据包：数据包不随提问自动附带，模型通过 {@code <<<GET_PACKETS>>>}
 *       工具主动拉取数据表当前选中记录的 Original / Unauthorized / 各用户报文，
 *       是否拉取由模型按用户需求决定</li>
 * </ul>
 * <p>
 * 聊天渲染采用组件化气泡：每条消息一个独立 JEditorPane（HTML 内容）加入
 * 垂直 BoxLayout 容器，追加/移除即增删组件；气泡宽度随视口自适应重排。
 */
public class AiChatPanel extends JPanel {

    /** 系统提示词：要求模型以越权测试专家身份分析数据包 */
    private static final String SYSTEM_PROMPT =
            "You are an expert web security analyst specializing in authorization testing "
                    + "(BOLA / IDOR / privilege escalation). Analyze the HTTP packets the user "
                    + "provides or that you fetch with your tools, identify authentication/"
                    + "authorization fields, compare object identifiers between users, and report "
                    + "potential broken access control findings with actionable verification steps. "
                    + "Answer in the same language as the user.";

    /**
     * 数据包工具说明（注入数据表上下文提供者时附加）：
     * 数据包不再自动附带，模型需按需通过标记块拉取。
     */
    private static final String PACKETS_TOOL_PROMPT =
            "\n\nYou have a built-in tool to fetch the HTTP packets currently selected in the "
                    + "data table. The packets are NOT sent to you automatically; to get them, "
                    + "output a block exactly like:\n"
                    + "<<<GET_PACKETS>>>\n"
                    + "<<<END>>>\n"
                    + "The plugin replies with the selected Original / Unauthorized / per-user "
                    + "packets. Request them when the packet contents are actually needed for "
                    + "your analysis; if the user's question can be answered without them, "
                    + "answer directly instead of calling the tool.";

    /**
     * 内置发包工具说明（仅在启用发包功能时附加）：
     * 告知模型可通过标记块输出原始 HTTP 请求，由插件实际发送并回填响应。
     */
    private static final String SEND_TOOL_PROMPT =
            "\n\nYou have a built-in tool to send HTTP requests through Burp Suite. "
                    + "To send a request, output a block exactly like:\n"
                    + "<<<SEND_HTTP_REQUEST>>>\n"
                    + "<raw HTTP request text, e.g.>\n"
                    + "GET /api/users/2 HTTP/1.1\n"
                    + "Host: example.com\n"
                    + "<<<END>>>\n"
                    + "Rules: send only one request per block; the plugin will ask the user to "
                    + "approve it, execute it, and feed the response back to you; then continue "
                    + "your analysis and either send another request or give your final "
                    + "conclusion. Never ask the user for permission before sending (do not "
                    + "use ASK_USER for that): the approval card already handles consent, so "
                    + "output the SEND block directly. Use this to actively verify authorization bypass: replay "
                    + "requests with tampered auth fields or object identifiers, compare "
                    + "responses, and confirm whether access control is enforced.";

    /**
     * 数据包来源工具说明（注入数据包来源服务时附加）：
     * Proxy 历史与站点地图数据包由用户在弹窗中挑选，模型不能直接全量读取。
     */
    private static final String PACKET_SOURCE_TOOL_PROMPT =
            "\n\nYou can also request packet data captured by Burp Suite:\n"
                    + "<<<GET_PROXY_HISTORY>>>optional keyword<<<END>>>\n"
                    + "<<<GET_SITEMAP>>>optional keyword<<<END>>>\n"
                    + "Always include a keyword (host, path fragment or parameter name) that "
                    + "describes which packets you need. If you leave the block empty the user "
                    + "will be asked to either pick the packets manually or re-describe the "
                    + "scope, so prefer stating a keyword. The plugin shows the matching "
                    + "candidates to the user, who chooses which packets to share; you may "
                    + "receive a subset or nothing. Never assume packets were provided, and if "
                    + "the user declines, continue with the information you already have "
                    + "instead of retrying.";

    /**
     * 询问工具说明（始终附加）：AI 不清楚用户意图时可发问并给出建议选项，
     * 用户可选项、可自定义回答、也可取消。
     */
    private static final String ASK_TOOL_PROMPT =
            "\n\nYou can ask the user a question when their intent is unclear, by outputting a "
                    + "block exactly like:\n"
                    + "<<<ASK_USER>>>\n"
                    + "question: <what you need to know>\n"
                    + "- <suggested option 1>\n"
                    + "- <suggested option 2>\n"
                    + "- <suggested option 3>\n"
                    + "<<<END>>>\n"
                    + "Rules: at most 3 options, each a short concrete choice (the user may also "
                    + "type their own answer or cancel). Use this only when you genuinely cannot "
                    + "proceed without the answer; ask once, then continue with the reply you get "
                    + "(the user may cancel, in which case state your assumptions and proceed).";

    private final AiConfigModel configModel;
    private final AiChatService chatService;
    private final AiSessionService sessionService;
    /** 内置发包工具服务（Montoya API 不可用时为 null，功能禁用） */
    private final AiToolService toolService;

    // ===== 对话区（气泡容器，每条消息一个 JEditorPane 组件） =====
    /** 气泡滚动容器：内部面板垂直堆叠所有气泡组件 */
    private final JPanel panelBubbleContainer;
    /** 气泡滚动容器外壳 */
    private final JScrollPane scrollChat;
    private final JTextArea textAreaInput;
    private final JButton btnSend;
    private final JButton btnClear;
    private final JButton btnSaveSession;
    private final JButton btnLoadSession;
    /** AI 主动发包测试开关（启用后系统提示词附加工具说明，模型可请求发送数据包） */
    private final JCheckBox checkEnableTool;
    /** 单次用户提问允许的最大发包轮数，防止模型循环发包失控 */
    private static final int MAX_TOOL_ROUNDS = 10;
    /** 拒绝发包时回传给模型的中性反馈（不给模型任何可利用的措辞） */
    private static final String TOOL_DENIAL_FEEDBACK =
            "[tool] The request was not authorised by the user. Do not retry it; "
                    + "continue your analysis with the information you already have.";
    /** 数据包工具回填消息前缀（会话重建时据此识别为数据包摘要气泡） */
    private static final String PACKETS_FEEDBACK_PREFIX = "[tool] HTTP packets";
    /** 用户未提供数据包时的中性反馈（不给模型任何可重试的措辞） */
    private static final String PACKET_ACCESS_DENIED_FEEDBACK =
            "[tool] The user did not provide any packet data. Do not retry this request; "
                    + "continue your analysis with the information you already have.";
    /** 用户选择"重新描述"时的反馈：本轮不得再次调用该工具 */
    private static final String PACKET_REDESCRIBE_FEEDBACK =
            "[tool] The user will re-describe which packets are needed. Do not call this tool "
                    + "again in this turn; wait for their next message.";
    /** 用户取消询问时的反馈：本轮不得重复提问 */
    private static final String ASK_CANCEL_FEEDBACK =
            "[tool] The user cancelled the question. Do not ask again in this turn; continue "
                    + "with your best judgement and state any assumptions you make.";

    /**
     * 数据包选择结果
     *
     * @param packets    用户确认的报文，未选择时为 null
     * @param redescribe 用户是否要求重新描述需求
     */
    private record PacketSelection(List<HttpRequestResponse> packets, boolean redescribe) {

        static PacketSelection none() {
            return new PacketSelection(null, false);
        }

        static PacketSelection askRedescribe() {
            return new PacketSelection(null, true);
        }

        static PacketSelection of(List<HttpRequestResponse> packets) {
            return new PacketSelection(packets, false);
        }
    }

    /**
     * 发包结果监听：AI 每成功发出一个数据包后回调（EDT），
     * 供 AI 越权扫描弹窗展示"AI 发送的数据包"。
     */
    public interface ToolSendListener {

        /**
         * @param requestResponse AI 实际发送的请求与响应
         * @param round           本轮对话中的第几次发包
         */
        void onRequestSent(HttpRequestResponse requestResponse, int round);
    }

    // ===== i18n =====
    private TitledBorder borderChat;
    /** 是否正在等待模型回复 */
    private volatile boolean awaitingReply = false;
    /** 当前对话循环线程（暂停时打断其阻塞） */
    private volatile Thread chatThread;
    /** 用户是否已请求暂停本轮生成 */
    private volatile boolean cancelRequested = false;
    /** 当前显示的 thinking 占位组件（非 null 表示正在思考） */
    private JComponent thinkingIndicator;
    /** 滚动到底部动画计时器 */
    private Timer scrollAnimator;

    /** 对话历史（含 system 提示词，按时间顺序） */
    private final List<AiChatService.ChatMessage> history = new ArrayList<>();
    /** 会话内已允许发包（"会话内允许"决定后置 true，后续发包免批准） */
    private boolean toolSessionApproved = false;
    /** 当前待用户决定的批准卡片（非 null 表示对话暂停等待用户点击） */
    private ToolApprovalCard pendingApprovalCard;
    /** 批准卡片决定后的续跑动作（由后台发包循环挂起，用户点击后恢复） */
    private volatile Consumer<Boolean> pendingApprovalContinuation;

    /**
     * 当前对话附带的数据包上下文提供者：返回 null 或空表示无数据包。
     * 由 MainPanel 装配时注入，用于数据包工具拉取数据表选中记录。
     */
    private java.util.function.Supplier<List<HttpRequestResponse>> contextPacketsSupplier;

    /**
     * Proxy 历史 / 站点地图数据包来源服务：由 MainPanel 装配时注入。
     * 为 null 时模型不能请求这两类来源（标记块被忽略）。
     */
    private core.PacketSourceService packetSourceService;

    /** 发包结果监听（AI 越权扫描弹窗用它回填"AI 发送的数据包"） */
    private volatile ToolSendListener toolSendListener;

    public AiChatPanel(AiConfigModel configModel) {
        this.configModel = configModel;
        this.chatService = new AiChatService(configModel);
        this.sessionService = new AiSessionService();
        AiToolService tool = null;
        try {
            tool = new AiToolService(ApiUtils.INSTANCE.api());
        } catch (IllegalStateException | ExceptionInInitializerError ex) {
            // Montoya API 尚未初始化（如单测环境），发包功能禁用
            utils.LogUtils.INSTANCE.error("AI send-request tool disabled: Montoya API unavailable", ex);
        }
        this.toolService = tool;

        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

        this.panelBubbleContainer = new JPanel();
        this.panelBubbleContainer.setLayout(new BoxLayout(panelBubbleContainer, BoxLayout.Y_AXIS));
        this.panelBubbleContainer.setBackground(Color.WHITE);
        this.scrollChat = new JScrollPane(panelBubbleContainer);
        this.scrollChat.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        // 内部容器非 Scrollable 视图，滚轮默认每格仅滚 1px，提升到 20px/格
        this.scrollChat.getVerticalScrollBar().setUnitIncrement(20);
        this.scrollChat.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                relayoutBubbles();
            }
        });

        this.textAreaInput = new JTextArea(4, 40);
        this.textAreaInput.setFont(monoFont);
        this.textAreaInput.setLineWrap(true);
        this.textAreaInput.setWrapStyleWord(true);

        this.btnSend = new JButton();
        this.btnSend.setBackground(SEND_BUTTON_COLOR);
        this.btnSend.setForeground(Color.WHITE);
        this.btnSend.setFocusPainted(false);
        this.btnClear = new JButton();
        this.btnSaveSession = new JButton();
        this.btnLoadSession = new JButton();
        this.checkEnableTool = new JCheckBox();
        // 默认允许 AI 发包测试（工具可用时勾选；发包仍逐次经批准卡片确认）
        this.checkEnableTool.setSelected(toolService != null);

        initLayout();
        bindEvents();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
    }

    /** 初始化布局：上方聊天记录 + 下方输入区 */
    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // 上方：聊天记录（气泡容器，用户靠右 / LLM 靠左）
        borderChat = new TitledBorder("");
        scrollChat.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1), ""));
        borderChat = (TitledBorder) scrollChat.getBorder();
        add(scrollChat, BorderLayout.CENTER);

        // 下方：输入区
        JPanel panelInput = new JPanel(new BorderLayout(5, 5));

        JScrollPane scrollInput = new JScrollPane(textAreaInput);
        panelInput.add(scrollInput, BorderLayout.CENTER);

        JPanel panelButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelButtons.add(checkEnableTool);
        panelButtons.add(btnLoadSession);
        panelButtons.add(btnSaveSession);
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
        btnSaveSession.addActionListener(e -> doSaveSession());
        btnLoadSession.addActionListener(e -> doLoadSession());
    }
    /**
     * 注入数据包上下文提供者：自动附带模式下由该提供者返回数据表选中记录的报文
     *
     * @param supplier 数据包提供者，返回 null 或空表示无可用数据包
     */
    public void setContextPacketsSupplier(
            java.util.function.Supplier<List<HttpRequestResponse>> supplier) {
        this.contextPacketsSupplier = supplier;
    }

    /**
     * 注入 Proxy 历史 / 站点地图数据包来源服务（由装配根在创建后调用）。
     * 注入后模型可通过 {@code <<<GET_PROXY_HISTORY>>>} / {@code <<<GET_SITEMAP>>>}
     * 请求读取，实际报文由用户在弹窗中勾选。
     *
     * @param service 数据包来源服务，传 null 表示禁用这两类工具
     */
    public void setPacketSourceService(core.PacketSourceService service) {
        this.packetSourceService = service;
    }

    /**
     * 注入发包结果监听（AI 越权扫描弹窗在开始扫描时注册，关闭时传 null 注销）。
     *
     * @param listener 监听器，null 表示不再通知
     */
    public void setToolSendListener(ToolSendListener listener) {
        this.toolSendListener = listener;
    }

    /**
     * 发送用户输入；生成中再次点击则暂停本轮。
     * 数据包不自动附带：模型需要时通过 {@code <<<GET_PACKETS>>>} 工具主动拉取数据表选中记录。
     */
    private void doSend() {
        if (awaitingReply) {
            // 生成中按钮已变为"暂停"，点击即中止本轮
            doPause();
            return;
        }
        String input = textAreaInput.getText().trim();
        if (input.isEmpty()) {
            return;
        }
        textAreaInput.setText("");
        ensureSystemPrompt();
        history.add(AiChatService.ChatMessage.user(input));
        appendUserBubble(input, input);
        dispatchChatRequest();
    }

    /**
     * 读取数据表当前选中的数据包（数据包工具的取数入口）。
     * 提供者会访问 Swing 表格状态，非 EDT 调用时切到 EDT 执行。
     *
     * @return 选中的数据包列表，无选中或读取失败返回空列表
     */
    private List<HttpRequestResponse> fetchSelectedPackets() {
        if (contextPacketsSupplier == null) {
            return List.of();
        }
        java.util.concurrent.atomic.AtomicReference<List<HttpRequestResponse>> holder =
                new java.util.concurrent.atomic.AtomicReference<>(List.of());
        Runnable read = () -> {
            try {
                List<HttpRequestResponse> packets = contextPacketsSupplier.get();
                if (packets != null) {
                    holder.set(packets);
                }
            } catch (Exception ex) {
                utils.LogUtils.INSTANCE.error("Failed to read AI context packets", ex);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            read.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(read);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (java.lang.reflect.InvocationTargetException ex) {
                utils.LogUtils.INSTANCE.error("Failed to read AI context packets", ex);
            }
        }
        return holder.get();
    }

    /** 保证 system 提示词存在且位于首位（按可用工具附加使用说明） */
    private void ensureSystemPrompt() {
        if (history.isEmpty() || !"system".equals(history.get(0).role())) {
            StringBuilder prompt = new StringBuilder(SYSTEM_PROMPT);
            prompt.append(ASK_TOOL_PROMPT);
            if (contextPacketsSupplier != null) {
                prompt.append(PACKETS_TOOL_PROMPT);
            }
            if (packetSourceService != null) {
                prompt.append(PACKET_SOURCE_TOOL_PROMPT);
            }
            if (isSendToolEnabled()) {
                prompt.append(SEND_TOOL_PROMPT);
            }
            history.add(0, AiChatService.ChatMessage.system(prompt.toString()));
        }
    }

    /** 发包工具是否可用且已启用 */
    private boolean isSendToolEnabled() {
        return toolService != null && checkEnableTool.isSelected();
    }

    /** 后台线程调用 LLM，先插入 thinking 占位，回复到达后替换为气泡 */
    private void dispatchChatRequest() {
        cancelRequested = false;
        setAwaiting(true);
        insertThinkingIndicator();
        List<AiChatService.ChatMessage> snapshot = new ArrayList<>(history);
        Thread thread = new Thread(() -> runChatLoop(snapshot), "authkit-ai-chat");
        thread.setDaemon(true);
        chatThread = thread;
        thread.start();
    }

    /**
     * 暂停当前生成：置取消标志并打断后台线程的阻塞（HTTP 请求或等待用户操作的锁），
     * 界面立即恢复为可发送状态并记录一条暂停提示。后台循环检测到标志后自行退出。
     */
    private void doPause() {
        if (!awaitingReply) {
            return;
        }
        cancelRequested = true;
        Thread thread = chatThread;
        if (thread != null) {
            thread.interrupt();
        }
        finishCancelled();
    }

    /** 本轮已暂停：恢复界面并（首次调用时）记录暂停提示气泡 */
    private void finishCancelled() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::finishCancelled);
            return;
        }
        boolean wasAwaiting = awaitingReply;
        removeThinkingIndicator();
        setAwaiting(false);
        if (wasAwaiting) {
            addBubble(createBubblePane(
                    escapeHtml(I18n.getInstance().text("ai", "chat.paused")),
                    new Color(0xF5, 0xF5, 0xF5)), false);
        }
    }

    /**
     * 对话主循环（后台线程）：请求模型 → 若回复含工具调用标记，按工具类型处理
     * （拉取数据包直接回填；发包经批准卡片等待用户决定）后继续请求，直至模型给出
     * 最终回答、出错或达到轮数上限。批准等待期间循环在锁上挂起，
     * 用户点击决定后由 EDT 唤醒。
     *
     * @param snapshot 发送时的历史快照（循环中通过 history 引用追加）
     */
    private void runChatLoop(List<AiChatService.ChatMessage> snapshot) {
        int toolRounds = 0;
        List<AiChatService.ChatMessage> current = snapshot;
        while (true) {
            if (cancelRequested) {
                // 用户已暂停：不再请求模型，也不渲染最终回答
                finishCancelled();
                return;
            }
            AiChatService.Reply reply;
            try {
                reply = chatService.chatDetailed(current);
            } catch (Exception ex) {
                if (cancelRequested) {
                    // 暂停打断了 HTTP 请求，按暂停处理而非报错
                    finishCancelled();
                    return;
                }
                utils.LogUtils.INSTANCE.error("AI request failed", ex);
                SwingUtilities.invokeLater(() -> {
                    removeThinkingIndicator();
                    appendErrorBubble(ex.getMessage());
                    setAwaiting(false);
                });
                return;
            }
            if (cancelRequested) {
                finishCancelled();
                return;
            }
            AiToolService.ToolCall call = resolveToolCall(reply.content());
            boolean overLimit = toolRounds >= MAX_TOOL_ROUNDS;
            if (call.type() != AiToolService.ToolType.NONE && overLimit) {
                // 达到轮数上限仍想调用工具：不执行，直接要求模型给结论
                toolRounds++;
                history.add(AiChatService.ChatMessage.assistant(reply.content()));
                history.add(AiChatService.ChatMessage.user(
                        "[tool] Max tool rounds (" + MAX_TOOL_ROUNDS
                                + ") reached. Stop calling tools and give your final conclusion now."));
                final String label = toolCallLabel(call);
                SwingUtilities.invokeLater(() ->
                        appendToolHistoryBubble(label, null));
                current = new ArrayList<>(history);
                continue;
            }
            if (call.type() == AiToolService.ToolType.NONE) {
                // 最终回答：落历史、替换 thinking 为正式气泡
                String reasoning = reply.reasoning();
                String content = reply.content();
                history.add(AiChatService.ChatMessage.assistant(content));
                SwingUtilities.invokeLater(() -> {
                    removeThinkingIndicator();
                    appendAssistantBubble(reasoning, content);
                    setAwaiting(false);
                });
                return;
            }
            toolRounds++;
            history.add(AiChatService.ChatMessage.assistant(reply.content()));
            if (call.type() == AiToolService.ToolType.GET_PACKETS) {
                // 数据包工具：只读数据表选中记录，不发送流量，无需用户批准
                List<HttpRequestResponse> packets = fetchSelectedPackets();
                final String feedback = buildPacketsFeedback(packets);
                history.add(AiChatService.ChatMessage.user(feedback));
                if (!packets.isEmpty()) {
                    SwingUtilities.invokeLater(() ->
                            appendUserBubble(summarizePackets(packets), feedback));
                }
                current = new ArrayList<>(history);
                continue;
            }
            if (call.type() == AiToolService.ToolType.PROXY_HISTORY
                    || call.type() == AiToolService.ToolType.SITEMAP) {
                // 数据包来源工具：先给建议由用户选择（未指定范围时）或直接弹窗挑选
                boolean fromProxy = call.type() == AiToolService.ToolType.PROXY_HISTORY;
                core.PacketSourceService.Source source = fromProxy
                        ? core.PacketSourceService.Source.PROXY_HISTORY
                        : core.PacketSourceService.Source.SITEMAP;
                PacketSelection selection = selectPacketsOnEdt(source, call.payload());
                if (selection.redescribe()) {
                    history.add(AiChatService.ChatMessage.user(PACKET_REDESCRIBE_FEEDBACK));
                    SwingUtilities.invokeLater(this::focusInput);
                } else if (selection.packets() == null || selection.packets().isEmpty()) {
                    history.add(AiChatService.ChatMessage.user(PACKET_ACCESS_DENIED_FEEDBACK));
                } else {
                    final String feedback = buildPacketSourceFeedback(source, selection.packets());
                    history.add(AiChatService.ChatMessage.user(feedback));
                    SwingUtilities.invokeLater(() ->
                            appendUserBubble(summarizePackets(selection.packets()), feedback));
                }
                current = new ArrayList<>(history);
                continue;
            }
            if (call.type() == AiToolService.ToolType.ASK_USER) {
                // 询问工具：AI 不清楚意图时发问，用户在卡片里选选项 / 自定义回答 / 取消
                AiToolService.AskRequest ask = AiToolService.parseAskRequest(call.payload());
                AskUserCard.Answer answer = awaitUserAnswerOnEdt(ask.question(), ask.options());
                history.add(AiChatService.ChatMessage.user(buildAskFeedback(answer)));
                current = new ArrayList<>(history);
                continue;
            }
            // 发包工具：经批准卡片等待用户决定（会话内允许后免批准）
            final String requestText = call.payload();
            boolean approved = toolSessionApproved || awaitApprovalOnEdt(requestText);
            String toolResult;
            if (approved) {
                HttpRequestResponse sentPacket = null;
                try {
                    sentPacket = toolService.executeToolCallWithResponse(requestText);
                    toolResult = AiToolService.describeResponse(sentPacket);
                } catch (Exception ex) {
                    utils.LogUtils.INSTANCE.error("AI tool request failed", ex);
                    toolResult = "[tool] Request failed: " + ex.getMessage();
                }
                history.add(AiChatService.ChatMessage.user(
                        "[tool] HTTP Response for the request you sent:\n" + toolResult));
                final String result = toolResult;
                SwingUtilities.invokeLater(() -> {
                    if (pendingApprovalCard != null) {
                        pendingApprovalCard.appendResponse(result);
                    }
                });
                notifyRequestSent(sentPacket, toolRounds);
            } else {
                // 拒绝：中性反馈，不给模型任何可继续尝试的措辞
                history.add(AiChatService.ChatMessage.user(TOOL_DENIAL_FEEDBACK));
            }
            current = new ArrayList<>(history);
        }
    }

    /**
     * 解析回复中的工具调用，并按当前可用性过滤：未启用发包工具时不认发包标记，
     * 没有数据包提供者时不认数据包标记（此类回复按最终回答处理）。
     *
     * @param replyContent 模型回复文本
     * @return 可执行的工具调用，无可执行工具返回 {@link AiToolService.ToolCall#NONE}
     */
    private AiToolService.ToolCall resolveToolCall(String replyContent) {
        AiToolService.ToolCall call = AiToolService.parseToolCall(replyContent);
        return switch (call.type()) {
            case SEND_REQUEST -> isSendToolEnabled() ? call : AiToolService.ToolCall.NONE;
            case GET_PACKETS -> contextPacketsSupplier != null ? call : AiToolService.ToolCall.NONE;
            case PROXY_HISTORY, SITEMAP ->
                    packetSourceService != null ? call : AiToolService.ToolCall.NONE;
            case ASK_USER -> call;
            case NONE -> AiToolService.ToolCall.NONE;
        };
    }

    /** 工具调用在对话记录气泡中的展示标签 */
    private String toolCallLabel(AiToolService.ToolCall call) {
        I18n i18n = I18n.getInstance();
        return switch (call.type()) {
            case GET_PACKETS -> i18n.text("ai", "chat.packetsTool");
            case PROXY_HISTORY -> i18n.text("ai", "chat.proxyHistoryTool");
            case SITEMAP -> i18n.text("ai", "chat.sitemapTool");
            case ASK_USER -> i18n.text("ai", "chat.askTool");
            default -> call.payload();
        };
    }

    /**
     * 在 EDT 上完成"数据包读取"交互并阻塞等待用户操作（后台线程调用）。
     * <p>
     * 模型**未指定 URL / 域名 / 关键词**时，先插入询问卡片让用户决定怎么继续：
     * 选择数据包（打开选择弹窗）、重新描述（放弃本次读取）、自定义回答（作为关键词预筛）
     * 或取消（不提供数据）；指定了范围则直接打开选择弹窗（关键词预填）。
     * 两种等待都用锁挂起对话循环，用户操作后由 EDT 唤醒。
     *
     * @param source 数据包来源
     * @param hint   AI 给出的范围提示（关键词），空表示未指定
     * @return 用户的选择结果（确认的报文 / 重新描述 / 取消）
     */
    private PacketSelection selectPacketsOnEdt(core.PacketSourceService.Source source, String hint) {
        if (hint == null || hint.isBlank()) {
            I18n i18n = I18n.getInstance();
            String sourceName = i18n.text("ai",
                    source == core.PacketSourceService.Source.PROXY_HISTORY
                            ? "dialog.packets.source.proxy" : "dialog.packets.source.sitemap");
            String question = i18n.format("ai", "chat.packetSuggest.message", sourceName)
                    + "\n" + i18n.text("ai", "chat.packetSuggest.hint");
            AskUserCard.Answer answer = awaitUserAnswerOnEdt(question, List.of(
                    i18n.text("ai", "chat.packetSuggest.select"),
                    i18n.text("ai", "chat.packetSuggest.redescribe")));
            switch (answer.kind()) {
                case CANCEL -> {
                    // 取消：不提供任何数据
                    return PacketSelection.none();
                }
                case OPTION -> {
                    if (answer.text().equals(i18n.text("ai", "chat.packetSuggest.redescribe"))) {
                        return PacketSelection.askRedescribe();
                    }
                }
                case CUSTOM -> hint = answer.text();
            }
        }
        final Object lock = new Object();
        final java.util.concurrent.atomic.AtomicReference<PacketSelection> holder =
                new java.util.concurrent.atomic.AtomicReference<>(PacketSelection.none());
        final String keyword = hint;
        SwingUtilities.invokeLater(() -> {
            removeThinkingIndicator();
            view.dialog.PacketSelectDialog dialog = new view.dialog.PacketSelectDialog(
                    this, packetSourceService, source, keyword, keyword);
            List<HttpRequestResponse> picked = dialog.showDialog();
            holder.set(picked == null || picked.isEmpty()
                    ? PacketSelection.none() : PacketSelection.of(picked));
            synchronized (lock) {
                lock.notifyAll();
            }
            insertThinkingIndicator();
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return PacketSelection.none();
            }
        }
        return holder.get();
    }

    /**
     * 在对话流插入询问卡片并阻塞等待用户回答（后台线程调用）。
     * 用户可选建议选项、写自定义回答或取消，三种结果都由调用方决定如何处理。
     *
     * @param question 问题文本
     * @param options  建议选项（最多三个，可为空）
     * @return 用户的回答
     */
    private AskUserCard.Answer awaitUserAnswerOnEdt(String question, List<String> options) {
        final Object lock = new Object();
        final java.util.concurrent.atomic.AtomicReference<AskUserCard.Answer> holder =
                new java.util.concurrent.atomic.AtomicReference<>(AskUserCard.Answer.cancel());
        SwingUtilities.invokeLater(() -> {
            removeThinkingIndicator();
            AskUserCard card = new AskUserCard(question, options, answer -> {
                synchronized (lock) {
                    holder.set(answer);
                    lock.notifyAll();
                }
                insertThinkingIndicator();
            });
            insertAskCard(card);
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        return holder.get();
    }

    /** 将询问卡片加入对话流（左侧对齐，随视口重排） */
    private void insertAskCard(AskUserCard card) {
        JPanel aligner = createAligner(FlowLayout.LEFT);
        aligner.putClientProperty(KEY_ASK_CARD, card);
        // 同时登记为测量目标，视口变化时 relayoutBubbles 按类型分派重排
        aligner.putClientProperty(KEY_MEASURE_TARGET, card);
        aligner.add(card);
        card.relayout(Math.max(scrollChat.getViewport().getWidth(), 600));
        panelBubbleContainer.add(aligner);
        panelBubbleContainer.revalidate();
        panelBubbleContainer.repaint();
        scrollToBottom();
    }

    /** 构建询问工具回填给模型的反馈文本 */
    private String buildAskFeedback(AskUserCard.Answer answer) {
        return switch (answer.kind()) {
            case OPTION -> "[tool] The user chose: " + answer.text();
            case CUSTOM -> "[tool] The user replied: " + answer.text();
            case CANCEL -> ASK_CANCEL_FEEDBACK;
        };
    }

    /** 把焦点交回输入框（用户选择"重新描述"后调用） */
    private void focusInput() {
        textAreaInput.requestFocusInWindow();
    }

    /** 通知发包结果监听（EDT 回调，监听器为空则忽略） */
    private void notifyRequestSent(HttpRequestResponse sentPacket, int round) {
        ToolSendListener listener = toolSendListener;
        if (sentPacket == null || listener == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> listener.onRequestSent(sentPacket, round));
    }

    /** 构建数据包来源工具回填给模型的反馈文本 */
    private String buildPacketSourceFeedback(core.PacketSourceService.Source source,
                                             List<HttpRequestResponse> packets) {
        String label = source == core.PacketSourceService.Source.PROXY_HISTORY
                ? "the Proxy history" : "the site map";
        return PACKETS_FEEDBACK_PREFIX + " the user selected from " + label + ":\n"
                + buildPacketPrompt(packets);
    }

    /** 构建数据包工具回填给模型的反馈文本 */
    private String buildPacketsFeedback(List<HttpRequestResponse> packets) {
        if (packets.isEmpty()) {
            return "[tool] No HTTP packet is currently selected in the data table. "
                    + "Ask the user to select a record, or continue your analysis without packets.";
        }
        return PACKETS_FEEDBACK_PREFIX + " currently selected in the data table:\n"
                + buildPacketPrompt(packets);
    }

    /**
     * 在对话流中插入批准卡片并阻塞等待用户决定（后台线程调用）。
     * thinking 指示器先移除，避免与卡片同时显示。
     *
     * @param requestText AI 请求发送的原始数据包
     * @return true=允许执行，false=拒绝
     */
    private boolean awaitApprovalOnEdt(String requestText) {
        final Object lock = new Object();
        final boolean[] decision = {false};
        SwingUtilities.invokeLater(() -> {
            removeThinkingIndicator();
            pendingApprovalCard = insertApprovalCard(requestText, dec -> {
                synchronized (lock) {
                    decision[0] = dec != ToolApprovalCard.Decision.DENY;
                    toolSessionApproved = dec == ToolApprovalCard.Decision.APPROVE_SESSION;
                    pendingApprovalCard = null;
                    pendingApprovalContinuation = null;
                    lock.notifyAll();
                }
                // 允许时重新显示 thinking（响应回填后继续循环）
                if (decision[0]) {
                    insertThinkingIndicator();
                }
            });
        });
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return decision[0];
    }

    /** 清空对话 */
    private void doClear() {
        if (awaitingReply) {
            return;
        }
        history.clear();
        assistantAnswers.clear();
        toolSessionApproved = false;
        removeApprovalCard();
        clearBubbles();
    }

    /** 保存会话到用户选择的 JSON 文件 */
    private void doSaveSession() {
        if (history.stream().noneMatch(msg -> !"system".equals(msg.role()))) {
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().text("ai", "message.sessionEmpty"),
                    I18n.getInstance().text("ai", "message.sessionSaved"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.getInstance().text("ai", "dialog.saveSession.title"));
        chooser.setSelectedFile(new File("authkit-ai-session.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = ensureJsonExtension(chooser.getSelectedFile());
        try {
            sessionService.save(history, path);
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("ai", "message.sessionSaved", path),
                    I18n.getInstance().text("ai", "dialog.saveSession.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            utils.LogUtils.INSTANCE.error("Failed to save AI session", ex);
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("ai", "message.sessionSaveFailed", ex.getMessage()),
                    I18n.getInstance().text("ai", "dialog.saveSession.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 从用户选择的 JSON 文件恢复会话 */
    private void doLoadSession() {
        if (awaitingReply) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.getInstance().text("ai", "dialog.loadSession.title"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            List<AiChatService.ChatMessage> messages = sessionService.load(path);
            history.clear();
            assistantAnswers.clear();
            toolSessionApproved = false;
            removeApprovalCard();
            ensureSystemPrompt();
            history.addAll(messages);
            rebuildBubblesFromHistory();
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("ai", "message.sessionLoaded",
                            messages.size()),
                    I18n.getInstance().text("ai", "dialog.loadSession.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            utils.LogUtils.INSTANCE.error("Failed to load AI session", ex);
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("ai", "message.sessionLoadFailed", ex.getMessage()),
                    I18n.getInstance().text("ai", "dialog.loadSession.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 确保文件名以 .json 结尾 */
    private static Path ensureJsonExtension(File selectedFile) {
        String path = selectedFile.getPath();
        if (!path.toLowerCase().endsWith(".json")) {
            path = path + ".json";
        }
        return Path.of(path);
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
        // 聊天窗口中用户侧显示摘要（复制按钮复制完整上下文），避免整包刷屏
        appendUserBubble(summarizePackets(packets), packetText);
        dispatchChatRequest();
    }

    /**
     * 右键菜单「AI 越权扫描」入口：把用户确认的数据包与扫描提示词注入对话并发起请求。
     * 扫描过程中 AI 的发包仍逐次经批准卡片确认，发包结果通过
     * {@link ToolSendListener} 回填到扫描弹窗。
     *
     * @param packets 用户在扫描弹窗中确认的数据包
     * @param prompt  扫描提示词（弹窗内置默认值，可编辑）
     */
    public void startAuthScan(List<HttpRequestResponse> packets, String prompt) {
        if (packets == null || packets.isEmpty() || awaitingReply) {
            return;
        }
        String scanPrompt = prompt == null || prompt.isBlank()
                ? I18n.getInstance().text("ai", "prompt.authScan") : prompt.trim();
        String packetText = buildPacketPrompt(packets);
        ensureSystemPrompt();
        history.add(AiChatService.ChatMessage.user(packetText + "\n\n" + scanPrompt));
        appendUserBubble(summarizePackets(packets) + "\n" + scanPrompt,
                packetText + "\n\n" + scanPrompt);
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

    /** 附带数据包超过该数量时，需弹窗经用户确认后才全部附带 */
    private static final int PACKET_PROMPT_CONFIRM_THRESHOLD = 20;

    /** 构建数据包分析提示词（按用户选中的数据包全部附带；超过阈值时先经用户确认） */
    private String buildPacketPrompt(List<HttpRequestResponse> packets) {
        int validCount = 0;
        for (HttpRequestResponse item : packets) {
            if (item != null && item.request() != null) {
                validCount++;
            }
        }
        // 超过阈值未经确认时仅附带前 20 个，防止提示词过长
        int max = validCount > PACKET_PROMPT_CONFIRM_THRESHOLD
                ? confirmLargePacketPrompt(validCount) : validCount;
        StringBuilder sb = new StringBuilder();
        sb.append(I18n.getInstance().text("ai", "prompt.packetHeader")).append('\n');
        int count = 0;
        for (HttpRequestResponse item : packets) {
            if (item == null || item.request() == null) {
                continue;
            }
            if (count >= max) {
                if (max < validCount) {
                    sb.append(I18n.getInstance().format("ai", "prompt.packetTruncated", max))
                            .append('\n');
                }
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

    /**
     * 附带数据包超过阈值时弹窗确认（可在后台线程调用，内部切换 EDT）。
     *
     * @param validCount 有效数据包总数
     * @return 用户同意时返回总数，否则返回阈值
     */
    private int confirmLargePacketPrompt(int validCount) {
        final int[] limit = {PACKET_PROMPT_CONFIRM_THRESHOLD};
        Runnable dialog = () -> {
            String message = I18n.getInstance().format("ai", "chat.manyPacketsConfirm", validCount);
            int choice = JOptionPane.showConfirmDialog(this, message,
                    I18n.getInstance().text("ai", "chat.manyPacketsTitle"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                limit[0] = validCount;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            dialog.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(dialog);
            } catch (Exception ex) {
                // EDT 不可用（窗口关闭中）时保守截断
            }
        }
        return limit[0];
    }

    // ===== 组件化气泡渲染 =====
    // 每条消息一个独立 JEditorPane 气泡加入 panelBubbleContainer（Y 轴 BoxLayout），
    // 追加/移除即增删组件，无需重建整份 HTML 文档。
    // 气泡宽度跟随视口：componentResized 时对每个气泡 setSize 重取首选高度（HTML 排版
    // 高度依赖宽度，必须显式两段式测量）。
    // 对话方式参考 burp-ai-agent：消息卡片带角色+时间戳头部，AI 正文 Markdown 渲染，
    // 复制按钮悬停显示。

    /** 气泡内容与视口边缘留白 */
    private static final int BUBBLE_MARGIN = 8;
    /** AI 气泡最大宽度占比（相对视口宽） */
    private static final double BUBBLE_MAX_WIDTH_RATIO = 0.85;
    /** 用户气泡最大宽度占比（更窄，参考 burp-ai-agent 用户 60% / AI 75%） */
    private static final double USER_BUBBLE_MAX_WIDTH_RATIO = 0.60;
    /** 气泡上标记最大宽度占比的 client property key（重排时按角色取回） */
    private static final String KEY_WIDTH_RATIO = "authkit.widthRatio";
    /** aligner 面板上标记尺寸测量目标（JEditorPane）的 client property key */
    private static final String KEY_MEASURE_TARGET = "authkit.measureTarget";
    /** aligner 面板上标记批准卡片的 client property key */
    private static final String KEY_APPROVAL_CARD = "authkit.approvalCard";
    /** aligner 面板上标记 AI 询问卡片的 client property key */
    private static final String KEY_ASK_CARD = "authkit.askCard";
    /** aligner 面板上标记气泡内部滚动宿主的 client property key */
    private static final String KEY_SCROLL_HOST = "authkit.scrollHost";

    /** 用户/助手消息角色标签颜色 */
    private static final Color USER_ROLE_COLOR = new Color(0x15, 0x65, 0xC0);
    private static final Color AI_ROLE_COLOR = new Color(0x2E, 0x7D, 0x32);
    /** 头部次要信息（时间戳）颜色 */
    private static final Color META_COLOR = new Color(0x99, 0x99, 0x99);
    /** thinking 占位气泡背景（白灰） */
    private static final Color THINKING_BUBBLE_BG = new Color(0xF5, 0xF5, 0xF5);
    /** 发送按钮配色（空闲绿 / 生成中橙，用于"暂停"） */
    private static final Color SEND_BUTTON_COLOR = new Color(0x4C, 0xAF, 0x50);
    private static final Color PAUSE_BUTTON_COLOR = new Color(0xE6, 0x51, 0x00);
    /** 用户气泡内部滚动上限：超出后气泡内出现滚动条 */
    private static final int MAX_USER_BUBBLE_HEIGHT = 240;

    /** Markdown 代码块：```lang ... ``` */
    private static final Pattern P_CODE_BLOCK =
            Pattern.compile("```[a-zA-Z0-9]*\\r?\\n?([\\s\\S]*?)```");
    /** Markdown 行内代码：`...` */
    private static final Pattern P_INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    /** Markdown 斜体 *...*（先于斜体处理加粗，避免误配） */
    private static final Pattern P_ITALIC = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    /** Markdown 标题（# ~ ######，行首锚定） */
    private static final Pattern P_HEADING = Pattern.compile("(?m)^(#{1,6}) (.+)$");
    /** Markdown 无序列表（- / * 开头行） */
    private static final Pattern P_UL = Pattern.compile("(?m)^[\\-*] (.+)$");
    /** Markdown 有序列表（数字. 开头行） */
    private static final Pattern P_OL = Pattern.compile("(?m)^(\\d+)\\. (.+)$");
    /** Markdown 加粗 **...** */
    private static final Pattern P_BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    /** Markdown 链接 [text](url) */
    private static final Pattern P_LINK = Pattern.compile("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)");

    /**
     * 将模型回复的 Markdown 转换为 HTML（简化版，参考 burp-ai-agent MarkdownRenderer）：
     * 支持代码块 / 行内代码 / 标题 / 列表 / 加粗 / 链接。管线顺序关键：
     * 先在保留换行的转义文本上跑行首锚定规则（标题/列表），最后才把换行转为 &lt;br&gt;，
     * 否则 (?m)^ 锚定失效。
     */
    private static String markdownToHtml(String markdown) {
        if (markdown == null) {
            return "";
        }
        // 仅转义 HTML 特殊字符，换行保持 \n 供行锚定规则使用
        String html = escapeHtmlKeepNewlines(markdown);
        // 代码块：占位符保护，内部换行即时转 <br>，避免被后续规则改写
        List<String> codeBlocks = new ArrayList<>();
        Matcher m = P_CODE_BLOCK.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String code = m.group(1).replaceAll("\\n\\s*$", "").replace("\n", "<br>");
            codeBlocks.add("<font face='monospaced'><span style='background:#EEEEEE;'>"
                    + code + "</span></font>");
            m.appendReplacement(sb, Matcher.quoteReplacement(" CB" + (codeBlocks.size() - 1) + " "));
        }
        m.appendTail(sb);
        html = sb.toString();
        // 行内代码
        html = P_INLINE_CODE.matcher(html).replaceAll(
                "<font face='monospaced'><span style='background:#EEEEEE;'>$1</span></font>");
        // 标题（行首锚定，须在换行转 <br> 之前）。字号用 CSS font-size 显式给绝对磅值：
        // Swing 对 <font size> 的映射很粗（正文 12pt 时 size 4 = 12pt、size 3 = 10pt、size 2 = 8pt），
        // 按相对字号算会让 ## / ### 标题反而比正文小，故不再使用 <font size>。
        html = P_HEADING.matcher(html).replaceAll(mr -> {
            int pt = switch (mr.group(1).length()) {
                case 1 -> 18;
                case 2 -> 16;
                case 3 -> 14;
                default -> 13;
            };
            return "<b><span style='font-size:" + pt + "pt;'>" + mr.group(2) + "</span></b>";
        });
        // 列表
        html = P_UL.matcher(html).replaceAll("&#8226; $1");
        html = P_OL.matcher(html).replaceAll("$1. $2");
        // 加粗（先于斜体，避免 ** 被斜体规则截胡）
        html = P_BOLD.matcher(html).replaceAll("<b>$1</b>");
        html = P_ITALIC.matcher(html).replaceAll("<i>$1</i>");
        // 链接
        html = P_LINK.matcher(html).replaceAll("<a href='$2'>$1</a>");
        // 换行转 <br>（最后执行）
        html = html.replace("\r\n", "\n");
        html = html.replace("\n\n", "<br><br>").replace("\n", "<br>");
        // 还原代码块占位符
        for (int i = 0; i < codeBlocks.size(); i++) {
            html = html.replace(" CB" + i + " ", codeBlocks.get(i));
        }
        return html;
    }

    /** HTML 转义但保留换行（供 Markdown 源输入，行锚定规则依赖 \n） */
    private static String escapeHtmlKeepNewlines(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\r\n", "\n");
    }

    /** 助手回答原文（按气泡顺序），供复制按钮使用 */
    private final List<String> assistantAnswers = new ArrayList<>();

    /** 创建消息气泡面板：内嵌只读 HTML JEditorPane，浅色圆角风格背景 */
    private JEditorPane createBubblePane(String html, Color background) {
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setBackground(background);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.darker(), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        return pane;
    }

    /**
     * 创建气泡对齐面板：FlowLayout 负责左右锚定，并把最大高度压到首选高度。
     * 外层容器是 BoxLayout.Y_AXIS，默认最大尺寸会让容器多余的垂直空间被平摊到
     * 各气泡之间（消息少时气泡间距被撑得很大），压到首选高度即保持紧凑间距。
     *
     * @param flowAlignment FlowLayout.LEFT / FlowLayout.RIGHT
     */
    private static JPanel createAligner(int flowAlignment) {
        JPanel aligner = new JPanel(new FlowLayout(flowAlignment, 0, 2)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension max = super.getMaximumSize();
                return new Dimension(max.width, getPreferredSize().height);
            }
        };
        aligner.setOpaque(false);
        return aligner;
    }

    /**
     * 将气泡加入容器：用外层对齐面板控制左右锚定，
     * 气泡本身按视口宽度计算首选尺寸（最大 85% 宽）。
     */
    private void addBubble(JEditorPane bubble, boolean alignRight) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> addBubble(bubble, alignRight));
            return;
        }
        applyBubbleSize(bubble, BUBBLE_MAX_WIDTH_RATIO);
        JPanel aligner = createAligner(alignRight ? FlowLayout.RIGHT : FlowLayout.LEFT);
        aligner.putClientProperty(KEY_MEASURE_TARGET, bubble);
        aligner.putClientProperty(KEY_WIDTH_RATIO, BUBBLE_MAX_WIDTH_RATIO);
        aligner.add(bubble);
        panelBubbleContainer.add(aligner);
        panelBubbleContainer.revalidate();
        panelBubbleContainer.repaint();
        scrollToBottom();
    }

    /**
     * 按当前视口宽度测量并设置气泡首选尺寸，宽度收缩贴字（对话方式参考
     * burp-ai-agent ChatMessagePanel.getPreferredSize）：先以最大宽排版，
     * 取 preferred 宽为不换行自然宽度，短文本气泡收缩到自然宽、长文本截到上限；
     * 再以最终宽排版取真实高度（HTML 排版需先定宽再测高）。
     *
     * @param bubble 目标气泡
     * @param widthRatio 最大宽度占比（相对视口宽）
     */
    private void applyBubbleSize(JEditorPane bubble, double widthRatio) {
        int viewportWidth = scrollChat.getViewport().getWidth();
        if (viewportWidth <= 0) {
            // 首次布局前视口尚无宽度，先给默认值，componentResized 时会重排
            viewportWidth = 600;
        }
        int maxWidth = (int) (viewportWidth * widthRatio) - BUBBLE_MARGIN * 2;
        maxWidth = Math.max(maxWidth, 120);
        applyShrinkToFitSize(bubble, maxWidth);
    }

    /**
     * 两段式收缩测量：显式 preferredSize 会短路后续 getPreferredSize（缓存旧值），
     * 必须先置 null 再排版测量，否则视口变化重排时尺寸失真。
     */
    static void applyShrinkToFitSize(JEditorPane pane, int maxWidth) {
        pane.setPreferredSize(null);
        pane.setSize(maxWidth, Short.MAX_VALUE);
        int naturalWidth = pane.getPreferredSize().width;
        int width = Math.min(naturalWidth, maxWidth);
        pane.setSize(width, Short.MAX_VALUE);
        int preferredHeight = pane.getPreferredSize().height;
        pane.setPreferredSize(new Dimension(width, preferredHeight));
    }

    /** 供 ToolApprovalCard 复用的窗格尺寸测量（包内可见静态方法） */
    static void applyPaneSize(JEditorPane pane, int viewportWidth) {
        int width = viewportWidth > 0 ? viewportWidth : 600;
        applyShrinkToFitSize(pane, width);
    }

    /** 视口尺寸变化时对所有气泡重新测量排版 */
    private void relayoutBubbles() {
        int viewportWidth = scrollChat.getViewport().getWidth();
        for (Component comp : panelBubbleContainer.getComponents()) {
            if (comp instanceof JPanel aligner) {
                Object target = aligner.getClientProperty(KEY_MEASURE_TARGET);
                if (target instanceof JEditorPane bubble) {
                    Object ratio = aligner.getClientProperty(KEY_WIDTH_RATIO);
                    applyBubbleSize(bubble,
                            ratio instanceof Double d ? d : BUBBLE_MAX_WIDTH_RATIO);
                    // 用户气泡：同步滚动宿主尺寸（含高度上限）
                    if (aligner.getClientProperty(KEY_SCROLL_HOST) instanceof JScrollPane host) {
                        applyScrollHostSize(bubble, host);
                    }
                } else if (target instanceof ToolApprovalCard card) {
                    card.relayout(viewportWidth);
                } else if (target instanceof AskUserCard card) {
                    card.relayout(viewportWidth);
                } else if (target instanceof JPanel collapsible) {
                    // 折叠气泡：按视口宽重测内部可见窗格
                    relayoutCollapsibleBubble(collapsible, viewportWidth);
                }
            }
        }
        panelBubbleContainer.revalidate();
        panelBubbleContainer.repaint();
    }

    /** 重测折叠气泡内部窗格：正文窗格重排，思考窗格仅在展开（可见）时重排 */
    private void relayoutCollapsibleBubble(Component target, int viewportWidth) {
        if (!(target instanceof JPanel bubble)) {
            return;
        }
        int vw = viewportWidth > 0 ? viewportWidth : 600;
        int maxWidth = Math.max((int) (vw * BUBBLE_MAX_WIDTH_RATIO) - BUBBLE_MARGIN * 2 - 16, 120);
        for (Component child : bubble.getComponents()) {
            if (child instanceof JEditorPane pane && pane.isVisible()) {
                applyShrinkToFitSize(pane, maxWidth);
            }
        }
        bubble.revalidate();
        bubble.repaint();
    }

    /** 滚动到聊天区底部 */
    private void scrollToBottom() {
        animateScrollToBottom();
    }

    /** 平滑滚动到底部：每 30ms 向目标位置靠近 30%，接近后一步到位 */
    private void animateScrollToBottom() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::animateScrollToBottom);
            return;
        }
        if (scrollAnimator != null && scrollAnimator.isRunning()) {
            // 已有滚动动画进行中，由其自行收敛到最新最大值
            return;
        }
        scrollAnimator = new Timer(30, null);
        scrollAnimator.addActionListener(e -> {
            javax.swing.JScrollBar bar = scrollChat.getVerticalScrollBar();
            int target = bar.getMaximum() - scrollChat.getViewport().getExtentSize().height;
            int current = bar.getValue();
            int step = (int) Math.ceil((target - current) * 0.3);
            if (step <= 1 || Math.abs(target - current) < 4) {
                bar.setValue(target);
                scrollAnimator.stop();
                scrollAnimator = null;
            } else {
                bar.setValue(current + step);
            }
        });
        scrollAnimator.start();
    }

    /** 构建消息头部：彩色角色标签 + 时间戳 */
    private JPanel buildMessageHeader(String roleText, Color roleColor) {
        JLabel roleLabel = new JLabel(roleText);
        roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD, 11f));
        roleLabel.setForeground(roleColor);
        JLabel timeLabel = new JLabel(new SimpleDateFormat("HH:mm").format(new Date()));
        timeLabel.setFont(timeLabel.getFont().deriveFont(10f));
        timeLabel.setForeground(META_COLOR);
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.setOpaque(false);
        header.add(roleLabel);
        header.add(timeLabel);
        return header;
    }

    /** 构建悬停显示的复制按钮（点击后短暂变为"已复制"反馈） */
    private JButton buildHoverCopyButton(String content) {
        I18n i18n = I18n.getInstance();
        JButton btnCopy = new JButton(i18n.text("ai", "chat.copy"));
        btnCopy.setFont(btnCopy.getFont().deriveFont(10f));
        btnCopy.setFocusPainted(false);
        btnCopy.setContentAreaFilled(false);
        btnCopy.setBorder(BorderFactory.createEmptyBorder());
        btnCopy.setForeground(META_COLOR);
        btnCopy.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCopy.setVisible(false);
        btnCopy.addActionListener(e -> {
            copyToClipboard(content);
            btnCopy.setText(i18n.text("ai", "chat.copied"));
            Timer reset = new Timer(1500, ev -> btnCopy.setText(i18n.text("ai", "chat.copy")));
            reset.setRepeats(false);
            reset.start();
        });
        return btnCopy;
    }

    /** 给气泡整体挂悬停监听：进入显示复制按钮，离开隐藏 */
    private void attachHoverCopy(JComponent hoverTarget, JButton btnCopy) {
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btnCopy.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                java.awt.Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), hoverTarget);
                if (!new Rectangle(0, 0, hoverTarget.getWidth(), hoverTarget.getHeight()).contains(p)) {
                    btnCopy.setVisible(false);
                }
            }
        };
        for (Component comp : new Component[]{hoverTarget, btnCopy}) {
            comp.addMouseListener(hover);
        }
        // JEditorPane 内部子组件（HTML 视图）不冒泡 mouseExited 到气泡，遍历挂接；
        // 气泡若嵌在内部滚动宿主里，视口视图也要挂，否则悬停长文本时不显示复制按钮
        for (Component child : hoverTarget.getComponents()) {
            child.addMouseListener(hover);
            if (child instanceof JScrollPane scroll && scroll.getViewport().getView() != null) {
                scroll.getViewport().getView().addMouseListener(hover);
            }
        }
    }

    /**
     * 追加用户消息卡片（靠右，蓝色气泡；头部角色标签+时间戳，悬停显示复制）。
     *
     * @param text       用户输入原文
     * @param copyTarget 复制到剪贴板的完整内容（自动附带数据包时与展示文本不同）
     */
    private void appendUserBubble(String text, String copyTarget) {
        I18n i18n = I18n.getInstance();
        String html = escapeHtml(text);
        JEditorPane bubble = createBubblePane(html, new Color(0xCF, 0xE6, 0xFF));

        JPanel header = buildMessageHeader(
                i18n.text("ai", "label.userPrefix"), USER_ROLE_COLOR);
        JButton btnCopy = buildHoverCopyButton(copyTarget);
        header.add(btnCopy);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        // 内容过长时气泡内部滚动（上限 MAX_USER_BUBBLE_HEIGHT），避免长文本撑高整个对话区
        JScrollPane scrollHost = buildBubbleScrollHost(bubble);
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(scrollHost, BorderLayout.CENTER);
        attachHoverCopy(wrapper, btnCopy);

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() ->
                    addAlignedComponent(wrapper, bubble, true, scrollHost));
        } else {
            addAlignedComponent(wrapper, bubble, true, scrollHost);
        }
    }

    /** 构建气泡的内部滚动宿主（无边框、与气泡同底色，按需出现纵向滚动条） */
    private static JScrollPane buildBubbleScrollHost(JEditorPane bubble) {
        JScrollPane scroll = new JScrollPane(bubble);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** 按气泡首选高度设置滚动宿主尺寸：超出上限则固定为上限高度，气泡内部滚动 */
    private static void applyScrollHostSize(JEditorPane bubble, JScrollPane host) {
        Dimension pref = bubble.getPreferredSize();
        Dimension size = new Dimension(pref.width, Math.min(pref.height, MAX_USER_BUBBLE_HEIGHT));
        host.setPreferredSize(size);
        host.setMinimumSize(size);
    }

    /** 追加 LLM 回复卡片（靠左，浅灰绿；reasoning 非空时展示可折叠思考块，正文 Markdown 渲染） */
    private void appendAssistantBubble(String reasoning, String content) {
        I18n i18n = I18n.getInstance();
        assistantAnswers.add(content);
        String bodyHtml = markdownToHtml(content);

        JComponent bubble;
        if (reasoning != null && !reasoning.isBlank()) {
            // 思考过程：默认折叠区块，▶/▼ 切换展开（浅灰斜体，与正文同气泡）
            String thinkingHtml = "<i><font color='#888888'>"
                    + escapeHtml(reasoning) + "</font></i>";
            JEditorPane thinkingPane = createPlainPane(thinkingHtml);
            JEditorPane bodyPane = createPlainPane(bodyHtml);
            bubble = buildCollapsibleBubble(i18n.text("ai", "label.thinking"), thinkingPane, bodyPane);
        } else {
            bubble = createBubblePane(bodyHtml, new Color(0xE8, 0xF5, 0xE9));
        }

        JPanel header = buildMessageHeader(
                i18n.text("ai", "label.assistantPrefix"), AI_ROLE_COLOR);
        JButton btnCopy = buildHoverCopyButton(content);
        header.add(btnCopy);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(bubble, BorderLayout.CENTER);
        attachHoverCopy(wrapper, btnCopy);

        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> addAlignedComponent(wrapper, bubble, false));
        } else {
            addAlignedComponent(wrapper, bubble, false);
        }
    }

    /**
     * 构建折叠气泡：头部是切换按钮（▶ 折叠 / ▼ 展开），思考窗格默认隐藏。
     * 展开后重新测量思考窗格宽度（HTML 高度依赖宽度，隐藏时测不准）。
     *
     * @param title        切换按钮标题（如"思考过程"）
     * @param thinkingPane 思考内容窗格（默认隐藏）
     * @param bodyPane     正文窗格（始终可见）
     * @return 组合后的气泡组件
     */
    private JComponent buildCollapsibleBubble(String title,
                                              JEditorPane thinkingPane, JEditorPane bodyPane) {
        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBackground(new Color(0xE8, 0xF5, 0xE9));
        bubble.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE8, 0xF5, 0xE9).darker(), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        JButton toggle = new JButton("▶ " + title);
        toggle.setFont(toggle.getFont().deriveFont(Font.PLAIN, 11f));
        toggle.setForeground(new Color(0x88, 0x88, 0x88));
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setFocusPainted(false);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.addActionListener(e -> {
            boolean expanded = thinkingPane.isVisible();
            thinkingPane.setVisible(!expanded);
            toggle.setText((expanded ? "▶ " : "▼ ") + title);
            if (!expanded) {
                // 展开时按当前气泡宽重测思考窗格高度
                int width = Math.max(bodyPane.getPreferredSize().width, 100);
                applyShrinkToFitSize(thinkingPane, width);
            }
            bubble.revalidate();
            bubble.repaint();
        });

        thinkingPane.setVisible(false);
        thinkingPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        bodyPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(toggle);
        bubble.add(thinkingPane);
        bubble.add(bodyPane);
        return bubble;
    }

    /** 创建无气泡边框的正文窗格（用于嵌在折叠气泡内部，不重复画边框） */
    private JEditorPane createPlainPane(String html) {
        JEditorPane pane = new JEditorPane("text/html", html);
        pane.setEditable(false);
        pane.setBackground(new Color(0xE8, 0xF5, 0xE9));
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setBorder(BorderFactory.createEmptyBorder());
        return pane;
    }

    /**
     * 将消息 wrapper（头部+气泡）加入容器：用外层对齐面板控制左右锚定，
     * 尺寸测量目标挂在 KEY_MEASURE_TARGET 上（普通气泡是 JEditorPane，
     * 折叠气泡是 JPanel 组合体，重排时按类型分别处理）。
     */
    private void addAlignedComponent(JPanel wrapper, JComponent bubble, boolean alignRight) {
        addAlignedComponent(wrapper, bubble, alignRight, null);
    }

    /**
     * 将消息 wrapper（头部+气泡）加入容器。
     *
     * @param scrollHost 气泡的内部滚动宿主（用户气泡使用，可为 null）；
     *                   传入时按气泡高度做上限截断并在重排时同步
     */
    private void addAlignedComponent(JPanel wrapper, JComponent bubble, boolean alignRight,
                                     JScrollPane scrollHost) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() ->
                    addAlignedComponent(wrapper, bubble, alignRight, scrollHost));
            return;
        }
        double widthRatio = alignRight
                ? USER_BUBBLE_MAX_WIDTH_RATIO : BUBBLE_MAX_WIDTH_RATIO;
        // 折叠气泡（JPanel 组合体）内部窗格自行测量，只按内容定宽；普通气泡两段式收缩测量
        if (bubble instanceof JEditorPane pane) {
            applyBubbleSize(pane, widthRatio);
            if (scrollHost != null) {
                applyScrollHostSize(pane, scrollHost);
            }
        } else {
            relayoutCollapsibleBubble(bubble, scrollChat.getViewport().getWidth());
        }
        JPanel aligner = createAligner(alignRight ? FlowLayout.RIGHT : FlowLayout.LEFT);
        aligner.putClientProperty(KEY_MEASURE_TARGET, bubble);
        aligner.putClientProperty(KEY_WIDTH_RATIO, widthRatio);
        if (scrollHost != null) {
            aligner.putClientProperty(KEY_SCROLL_HOST, scrollHost);
        }
        aligner.add(wrapper);
        panelBubbleContainer.add(aligner);
        panelBubbleContainer.revalidate();
        panelBubbleContainer.repaint();
        if (scrollHost != null) {
            // 布局完成后把内部滚动位置复位到顶部（否则可能停在末尾）
            SwingUtilities.invokeLater(() ->
                    scrollHost.getViewport().setViewPosition(new Point(0, 0)));
        }
        scrollToBottom();
    }

    /**
     * 尺寸测量目标是否为折叠气泡（JPanel 组合体而非单个 JEditorPane）：
     * 折叠气泡内部窗格已按需测量，整宽重排时跳过避免破坏展开状态。
     */
    private static boolean isCollapsibleBubble(Component comp) {
        return comp instanceof JPanel && !(comp instanceof JEditorPane);
    }

    /** 追加错误气泡（靠左，浅红） */
    private void appendErrorBubble(String message) {
        String html = escapeHtml(I18n.getInstance().format(
                "ai", "message.requestFailed", message == null ? "" : message));
        addBubble(createBubblePane(html, new Color(0xFD, 0xEC, 0xEA)), false);
    }

    /**
     * 追加历史工具调用记录气泡（靠左，浅黄）：加载会话时还原（无批准交互）。
     *
     * @param requestText AI 发出的原始请求
     * @param result      执行反馈（含响应）；失败时为错误说明，null 表示仅记录请求
     */
    private void appendToolHistoryBubble(String requestText, String result) {
        I18n i18n = I18n.getInstance();
        StringBuilder html = new StringBuilder();
        html.append("<b>").append(escapeHtml(i18n.text("ai", "chat.toolHistory"))).append("</b>")
                .append("<br><b>").append(escapeHtml(i18n.text("ai", "chat.toolRequest")))
                .append(":</b><br>")
                .append("<font face='monospaced'>").append(escapeHtml(requestText)).append("</font>");
        if (result != null) {
            html.append("<br><b>").append(escapeHtml(i18n.text("ai", "chat.toolResponse")))
                    .append(":</b><br>")
                    .append("<font face='monospaced' color='#555555'>")
                    .append(escapeHtml(truncatePacket(result, 1500))).append("</font>");
        }
        addBubble(createBubblePane(html.toString(), new Color(0xFF, 0xF8, 0xE1)), false);
    }

    /**
     * 在对话流中插入发包批准卡片并挂起后台循环，等待用户决定（对话方式
     * 参考 burp-ai-agent 的 ToolApprovalCard：内联卡片，非弹窗）。
     *
     * @param requestText   AI 请求发送的原始数据包
     * @param onDecided     决定回调（EDT 调用），携带用户点击的具体决定
     * @return 插入的卡片（EDT 外调用时经 invokeLater 排队，返回值仍有效）
     */
    private ToolApprovalCard insertApprovalCard(String requestText,
                                                Consumer<ToolApprovalCard.Decision> onDecided) {
        ToolApprovalCard card = new ToolApprovalCard(requestText, onDecided);
        JPanel aligner = createAligner(FlowLayout.LEFT);
        aligner.putClientProperty(KEY_APPROVAL_CARD, card);
        // 同时登记为测量目标，视口变化时 relayoutBubbles 按类型分派重排
        aligner.putClientProperty(KEY_MEASURE_TARGET, card);
        aligner.add(card);
        // 先按视口宽测量卡片内窗格（HTML 高度依赖宽度），避免首帧出现横滚条
        card.relayout(Math.max(scrollChat.getViewport().getWidth(), 600));
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> {
                panelBubbleContainer.add(aligner);
                panelBubbleContainer.revalidate();
                panelBubbleContainer.repaint();
                scrollToBottom();
            });
        } else {
            panelBubbleContainer.add(aligner);
            panelBubbleContainer.revalidate();
            panelBubbleContainer.repaint();
            scrollToBottom();
        }
        return card;
    }

    /** 从容器中移除批准卡片（清空对话/加载会话时） */
    private void removeApprovalCard() {
        pendingApprovalCard = null;
        pendingApprovalContinuation = null;
    }

    /** 插入 thinking 占位：左侧灰色气泡（样式与消息气泡一致），持有引用便于移除 */
    private void insertThinkingIndicator() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::insertThinkingIndicator);
            return;
        }
        JEditorPane bubble = createBubblePane(buildThinkingHtml(), THINKING_BUBBLE_BG);
        applyBubbleSize(bubble, BUBBLE_MAX_WIDTH_RATIO);
        JPanel aligner = createAligner(FlowLayout.LEFT);
        aligner.putClientProperty(KEY_MEASURE_TARGET, bubble);
        aligner.putClientProperty(KEY_WIDTH_RATIO, BUBBLE_MAX_WIDTH_RATIO);
        aligner.add(bubble);
        thinkingIndicator = aligner;
        panelBubbleContainer.add(aligner);
        panelBubbleContainer.revalidate();
        panelBubbleContainer.repaint();
        animateScrollToBottom();
    }

    /** 构建 thinking 占位 HTML：白灰斜体静态文本 */
    private static String buildThinkingHtml() {
        return "<html><body style='margin:0'><i><font color='#AAAAAA'>"
                + escapeHtml(I18n.getInstance().text("ai", "chat.thinking"))
                + "...</font></i></body></html>";
    }

    /** 移除 thinking 占位组件 */
    private void removeThinkingIndicator() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::removeThinkingIndicator);
            return;
        }
        if (thinkingIndicator != null) {
            panelBubbleContainer.remove(thinkingIndicator);
            thinkingIndicator = null;
            panelBubbleContainer.revalidate();
            panelBubbleContainer.repaint();
        }
    }

    /** 移除全部气泡（清空对话/加载会话） */
    private void clearBubbles() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clearBubbles);
            return;
        }
        panelBubbleContainer.removeAll();
        thinkingIndicator = null;
        panelBubbleContainer.revalidate();
        panelBubbleContainer.repaint();
    }

    /** 将文本复制到系统剪贴板 */
    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    /**
     * 从会话历史重建聊天气泡（加载会话后调用）。
     * 历史中的数据包消息以摘要形式展示，assistant 消息重建复制链接索引。
     */
    private void rebuildBubblesFromHistory() {
        clearBubbles();
        String packetHeader = I18n.getInstance().text("ai", "prompt.packetHeader");
        for (AiChatService.ChatMessage msg : history) {
            switch (msg.role()) {
                case "user" -> {
                    if (msg.content().startsWith(packetHeader)) {
                        // 数据包消息：仅显示摘要占位
                        appendUserBubble(I18n.getInstance().text("ai", "chat.packetSummary"),
                                msg.content());
                    } else if (msg.content().startsWith(PACKETS_FEEDBACK_PREFIX)) {
                        // 数据包工具回填：还原为数据包摘要气泡
                        appendUserBubble(I18n.getInstance().text("ai", "chat.packetSummary"),
                                msg.content());
                    } else if (msg.content().startsWith("[tool]")) {
                        // 工具响应回填消息：以工具记录气泡样式还原（不重复展示请求原文）
                        appendToolHistoryBubble(
                                I18n.getInstance().text("ai", "chat.toolHistory"),
                                stripToolPrefix(msg.content()));
                    } else {
                        appendUserBubble(msg.content(), msg.content());
                    }
                }
                case "assistant" -> appendAssistantBubble(null, msg.content());
                default -> {
                    // system 等其他角色不渲染
                }
            }
        }
    }

    /** 去掉工具回填消息的 "[tool] ..." 前缀行，仅保留响应内容 */
    private static String stripToolPrefix(String content) {
        int idx = content.indexOf('\n');
        return idx >= 0 ? content.substring(idx + 1) : content;
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

    /**
     * 切换生成状态：生成中按钮变为"暂停"（可中止本轮），空闲时为"发送"。
     */
    private void setAwaiting(boolean awaiting) {
        awaitingReply = awaiting;
        I18n i18n = I18n.getInstance();
        btnSend.setEnabled(true);
        btnSend.setText(i18n.text("ai", awaiting ? "button.pause" : "button.send"));
        btnSend.setToolTipText(i18n.text("ai", awaiting ? "tooltip.pause" : "tooltip.send"));
        btnSend.setBackground(awaiting ? PAUSE_BUTTON_COLOR : SEND_BUTTON_COLOR);
    }

    /** 刷新国际化文本 */
    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        if (borderChat != null) {
            borderChat.setTitle(i18n.text("ai", "section.chat"));
        }
        btnSend.setText(i18n.text("ai", awaitingReply ? "button.pause" : "button.send"));
        btnSend.setToolTipText(i18n.text("ai", awaitingReply ? "tooltip.pause" : "tooltip.send"));
        btnSend.setBackground(awaitingReply ? PAUSE_BUTTON_COLOR : SEND_BUTTON_COLOR);
        btnClear.setText(i18n.text("ai", "button.clear"));
        btnSaveSession.setText(i18n.text("ai", "button.saveSession"));
        btnLoadSession.setText(i18n.text("ai", "button.loadSession"));
        checkEnableTool.setText(i18n.text("ai", "checkbox.enableTool"));
        checkEnableTool.setEnabled(toolService != null);
        checkEnableTool.setToolTipText(toolService != null
                ? i18n.text("ai", "tooltip.enableTool")
                : i18n.text("ai", "tooltip.enableToolUnavailable"));
        scrollChat.setToolTipText(i18n.text("ai", "tooltip.transcript"));
        relayoutBubbles();
        revalidate();
        repaint();
    }
}
