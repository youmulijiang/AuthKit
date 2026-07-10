package view;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import core.FakeIpService;
import utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 右键菜单提供者
 * 提供两个功能：
 * 1. Send to AuthKit — 将选中的请求发送到 AuthKit 进行主动鉴权测试
 * 2. Extract Auth to User — 从选中请求中提取认证头，填入指定鉴权用户配置
 */
public class AuthContextMenuProvider implements ContextMenuItemsProvider {

    /**
     * 常见认证头关键字（小写），用于模糊匹配。
     * 只要请求头名称中包含以下任一关键字，就视为认证头。
     */
    static final List<String> AUTH_HEADER_KEYWORDS = List.of(
            "cookie", "token", "authorization", "auth",
            "session", "jwt", "bearer", "api-key", "apikey",
            "x-csrf", "x-xsrf", "access-key", "accesskey",
            "secret"
    );

    /** 获取当前已配置的鉴权用户名称列表 */
    private final Supplier<List<String>> userNamesSupplier;

    /** 获取插件是否启用 */
    private final Supplier<Boolean> enabledSupplier;

    /** 开启插件的回调（用于弹窗中勾选后直接启用） */
    private final Runnable enablePluginHandler;

    /** Send 处理回调：(选中的请求响应列表) */
    private final Consumer<List<HttpRequestResponse>> sendHandler;

    /** Extract 处理回调：(提取到的认证头文本, 目标用户名称，null 表示新建用户) */
    private final BiConsumer<String, String> extractHandler;

    /** 新建用户回调：接收提取到的认证头文本，返回新建用户的名称（null 表示取消） */
    private final Function<String, String> createUserHandler;

    /** 伪造 IP 核心服务 */
    private final FakeIpService fakeIpService;

    /** 无法直接回写编辑器时，将修改后的请求发送到 Repeater */
    private final Consumer<HttpRequest> sendToRepeaterHandler;

    /** 随机 IP 爆破：将带伪造 IP 请求头的请求发送到 Intruder */
    private final Consumer<HttpRequest> sendToIntruderHandler;

    /** 403 bypass 扫描回调 */
    private final Consumer<List<HttpRequestResponse>> bypass403ScanHandler;

    /** IDOR 扫描回调 */
    private Consumer<List<HttpRequestResponse>> idorScanHandler = items -> {};

    /** 更新为最新鉴权字段回调：(event, selectedItems) */
    private final BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> updateToLatestAuthHandler;

    /** 从历史选择鉴权字段回调：(event, selectedItems) */
    private final BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> selectFromHistoryHandler;

    /** 删除所有鉴权字段回调：(event, selectedItems) */
    private final BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> deleteAuthHandler;

    public AuthContextMenuProvider(Supplier<List<String>> userNamesSupplier,
                                    Supplier<Boolean> enabledSupplier,
                                    Runnable enablePluginHandler,
                                    Consumer<List<HttpRequestResponse>> sendHandler,
                                    BiConsumer<String, String> extractHandler,
                                    Function<String, String> createUserHandler) {
        this(userNamesSupplier, enabledSupplier, enablePluginHandler, sendHandler, extractHandler,
                createUserHandler, new FakeIpService(), request -> {}, request -> {}, items -> {});
    }

    public AuthContextMenuProvider(Supplier<List<String>> userNamesSupplier,
                                    Supplier<Boolean> enabledSupplier,
                                    Runnable enablePluginHandler,
                                    Consumer<List<HttpRequestResponse>> sendHandler,
                                    BiConsumer<String, String> extractHandler,
                                    Function<String, String> createUserHandler,
                                    FakeIpService fakeIpService,
                                    Consumer<HttpRequest> sendToRepeaterHandler,
                                    Consumer<HttpRequest> sendToIntruderHandler) {
        this(userNamesSupplier, enabledSupplier, enablePluginHandler, sendHandler, extractHandler,
                createUserHandler, fakeIpService, sendToRepeaterHandler, sendToIntruderHandler, items -> {});
    }

    public AuthContextMenuProvider(Supplier<List<String>> userNamesSupplier,
                                    Supplier<Boolean> enabledSupplier,
                                    Runnable enablePluginHandler,
                                    Consumer<List<HttpRequestResponse>> sendHandler,
                                    BiConsumer<String, String> extractHandler,
                                    Function<String, String> createUserHandler,
                                    FakeIpService fakeIpService,
                                    Consumer<HttpRequest> sendToRepeaterHandler,
                                    Consumer<HttpRequest> sendToIntruderHandler,
                                    Consumer<List<HttpRequestResponse>> bypass403ScanHandler) {
        this(userNamesSupplier, enabledSupplier, enablePluginHandler, sendHandler, extractHandler,
                createUserHandler, fakeIpService, sendToRepeaterHandler, sendToIntruderHandler,
                bypass403ScanHandler, (event, items) -> {}, (event, items) -> {});
    }

    public AuthContextMenuProvider(Supplier<List<String>> userNamesSupplier,
                                    Supplier<Boolean> enabledSupplier,
                                    Runnable enablePluginHandler,
                                    Consumer<List<HttpRequestResponse>> sendHandler,
                                    BiConsumer<String, String> extractHandler,
                                    Function<String, String> createUserHandler,
                                    FakeIpService fakeIpService,
                                    Consumer<HttpRequest> sendToRepeaterHandler,
                                    Consumer<HttpRequest> sendToIntruderHandler,
                                    Consumer<List<HttpRequestResponse>> bypass403ScanHandler,
                                    BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> updateToLatestAuthHandler,
                                    BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> selectFromHistoryHandler) {
        this(userNamesSupplier, enabledSupplier, enablePluginHandler, sendHandler, extractHandler,
                createUserHandler, fakeIpService, sendToRepeaterHandler, sendToIntruderHandler,
                bypass403ScanHandler, updateToLatestAuthHandler, selectFromHistoryHandler, (event, items) -> {});
    }

    public AuthContextMenuProvider(Supplier<List<String>> userNamesSupplier,
                                    Supplier<Boolean> enabledSupplier,
                                    Runnable enablePluginHandler,
                                    Consumer<List<HttpRequestResponse>> sendHandler,
                                    BiConsumer<String, String> extractHandler,
                                    Function<String, String> createUserHandler,
                                    FakeIpService fakeIpService,
                                    Consumer<HttpRequest> sendToRepeaterHandler,
                                    Consumer<HttpRequest> sendToIntruderHandler,
                                    Consumer<List<HttpRequestResponse>> bypass403ScanHandler,
                                    BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> updateToLatestAuthHandler,
                                    BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> selectFromHistoryHandler,
                                    BiConsumer<ContextMenuEvent, List<HttpRequestResponse>> deleteAuthHandler) {
        this.userNamesSupplier = userNamesSupplier;
        this.enabledSupplier = enabledSupplier;
        this.enablePluginHandler = enablePluginHandler;
        this.sendHandler = sendHandler;
        this.extractHandler = extractHandler;
        this.createUserHandler = createUserHandler;
        this.fakeIpService = fakeIpService;
        this.sendToRepeaterHandler = sendToRepeaterHandler;
        this.sendToIntruderHandler = sendToIntruderHandler;
        this.bypass403ScanHandler = bypass403ScanHandler;
        this.updateToLatestAuthHandler = updateToLatestAuthHandler;
        this.selectFromHistoryHandler = selectFromHistoryHandler;
        this.deleteAuthHandler = deleteAuthHandler;
    }

    public void setIdorScanHandler(Consumer<List<HttpRequestResponse>> handler) {
        this.idorScanHandler = handler != null ? handler : items -> {};
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        List<HttpRequestResponse> selectedItems = resolveSelectedItems(event);
        if (selectedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<Component> menuItems = new ArrayList<>();
        List<String> userNames = userNamesSupplier.get();
        final List<HttpRequestResponse> finalSelectedItems = selectedItems;

        // === Menu 1: Send to AuthKit ===
        menuItems.add(buildSendMenu(finalSelectedItems));

        // === Menu 2: Extract Auth to User ===
        menuItems.add(buildExtractMenu(userNames, finalSelectedItems));

        // === Menu 3: Fake IP ===
        menuItems.add(buildFakeIpMenu(event, finalSelectedItems));

        // === Menu 4: 403 Bypass Scan ===
        menuItems.add(buildBypass403ScanMenu(finalSelectedItems));

        // === Menu 5: IDOR Scan ===
        menuItems.add(buildIdorScanMenu(finalSelectedItems));

        // === Menu 6: 更新鉴权字段 ===
        menuItems.add(buildUpdateAuthMenu(event, finalSelectedItems));

        return menuItems;
    }

    /**
     * 从事件中解析选中的请求响应列表
     */
    private List<HttpRequestResponse> resolveSelectedItems(ContextMenuEvent event) {
        List<HttpRequestResponse> items = event.selectedRequestResponses();
        if (items.isEmpty() && event.messageEditorRequestResponse().isPresent()) {
            return List.of(event.messageEditorRequestResponse().get().requestResponse());
        }
        return items;
    }

    /**
     * 构建 "Send to AuthKit" 菜单项（单个按钮，主动发包并展示到 DataTable）
     */
    private Component buildSendMenu(List<HttpRequestResponse> selectedItems) {
        JMenuItem item = new JMenuItem(I18n.getInstance().text("auth_context_menu", "menu.send"));
        item.addActionListener(e -> {
            if (!enabledSupplier.get()) {
                I18n i18n = I18n.getInstance();
                // 构建带勾选框的自定义面板
                JLabel msgLabel = new JLabel(i18n.text("auth_context_menu", "dialog.pluginDisabled.message"));
                JCheckBox enableCheckBox = new JCheckBox(
                        i18n.text("auth_context_menu", "dialog.pluginDisabled.checkbox"));
                JPanel panel = new JPanel(new BorderLayout(0, 8));
                panel.add(msgLabel, BorderLayout.NORTH);
                panel.add(enableCheckBox, BorderLayout.CENTER);

                int result = JOptionPane.showConfirmDialog(
                        null, panel,
                        i18n.text("auth_context_menu", "dialog.pluginDisabled.title"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (result == JOptionPane.OK_OPTION && enableCheckBox.isSelected()) {
                    enablePluginHandler.run();
                    sendHandler.accept(selectedItems);
                }
                return;
            }
            sendHandler.accept(selectedItems);
        });
        return item;
    }

    /**
     * 构建 "Extract Auth to User" 菜单
     */
    private Component buildExtractMenu(List<String> userNames,
                                        List<HttpRequestResponse> selectedItems) {
        JMenu menu = new JMenu(I18n.getInstance().text("auth_context_menu", "menu.extract"));

        // 已有的鉴权用户
        for (String name : userNames) {
            JMenuItem userItem = new JMenuItem(name);
            userItem.addActionListener(e -> {
                String authText = extractAuthHeaders(selectedItems);
                extractHandler.accept(authText, name);
            });
            menu.add(userItem);
        }

        if (!userNames.isEmpty()) {
            menu.addSeparator();
        }

        // "+ New User" 选项
        JMenuItem newUserItem = new JMenuItem(I18n.getInstance().text("auth_context_menu", "menu.newUser"));
        newUserItem.addActionListener(e -> {
            String authText = extractAuthHeaders(selectedItems);
            if (authText.isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        I18n.getInstance().text("auth_context_menu", "dialog.noMessage.message"),
                        I18n.getInstance().text("auth_context_menu", "dialog.noMessage.title"),
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            // createUserHandler 内部会弹出新建用户对话框，传入认证头文本
            createUserHandler.apply(authText);
        });
        menu.add(newUserItem);

        return menu;
    }

    /** 构建 fakeIp 菜单。 */
    private Component buildFakeIpMenu(ContextMenuEvent event, List<HttpRequestResponse> selectedItems) {
        I18n i18n = I18n.getInstance();
        JMenu menu = new JMenu(i18n.text("auth_context_menu", "menu.fakeIp"));

        JMenuItem customIp = new JMenuItem(i18n.text("auth_context_menu", "menu.fakeIp.custom"));
        customIp.addActionListener(e -> handleCustomFakeIp(event, selectedItems));
        menu.add(customIp);

        JMenuItem localIp = new JMenuItem(i18n.text("auth_context_menu", "menu.fakeIp.local"));
        localIp.addActionListener(e -> applyFakeIp(event, selectedItems, fakeIpService::addLocalIpHeaders, false));
        menu.add(localIp);

        JMenuItem randomIp = new JMenuItem(i18n.text("auth_context_menu", "menu.fakeIp.random"));
        randomIp.addActionListener(e -> applyFakeIp(event, selectedItems, fakeIpService::addRandomIpHeaders, false));
        menu.add(randomIp);

        JMenuItem bruteForce = new JMenuItem(i18n.text("auth_context_menu", "menu.fakeIp.bruteforce"));
        bruteForce.addActionListener(e -> applyFakeIp(event, selectedItems, fakeIpService::addBruteForceHeaders, true));
        menu.add(bruteForce);

        return menu;
    }

    private Component buildBypass403ScanMenu(List<HttpRequestResponse> selectedItems) {
        JMenuItem item = new JMenuItem(I18n.getInstance().text("auth_context_menu", "menu.bypass403.scan"));
        item.addActionListener(e -> bypass403ScanHandler.accept(selectedItems));
        return item;
    }

    private Component buildIdorScanMenu(List<HttpRequestResponse> selectedItems) {
        JMenuItem item = new JMenuItem(I18n.getInstance().text("auth_context_menu", "menu.idor.scan"));
        item.addActionListener(e -> idorScanHandler.accept(selectedItems));
        return item;
    }

    /** 构建"更新鉴权字段"父菜单（含三个子菜单）。 */
    private Component buildUpdateAuthMenu(ContextMenuEvent event, List<HttpRequestResponse> selectedItems) {
        I18n i18n = I18n.getInstance();
        JMenu menu = new JMenu(i18n.text("auth_context_menu", "menu.updateAuth"));

        JMenuItem latestItem = new JMenuItem(i18n.text("auth_context_menu", "menu.updateAuth.latest"));
        latestItem.addActionListener(e -> updateToLatestAuthHandler.accept(event, selectedItems));
        menu.add(latestItem);

        JMenuItem historyItem = new JMenuItem(i18n.text("auth_context_menu", "menu.updateAuth.history"));
        historyItem.addActionListener(e -> selectFromHistoryHandler.accept(event, selectedItems));
        menu.add(historyItem);

        menu.addSeparator();

        JMenuItem clearAllItem = new JMenuItem(i18n.text("auth_context_menu", "menu.updateAuth.clearAll"));
        clearAllItem.addActionListener(e -> deleteAuthHandler.accept(event, selectedItems));
        menu.add(clearAllItem);

        return menu;
    }

    /**
     * 将 source 请求中的所有鉴权字段应用到 original 请求上。
     * 若 original 中已存在同名请求头则替换其值，否则追加。
     * 非鉴权字段保持不变。
     *
     * @param original 原始请求（仅替换其鉴权字段）
     * @param source   鉴权字段来源请求
     * @return 替换后的新 HttpRequest 对象
     */
    public static HttpRequest replaceAuthHeaders(HttpRequest original, HttpRequest source) {
        HttpRequest updated = original;
        for (HttpHeader header : source.headers()) {
            if (isAuthHeader(header.name())) {
                updated = updated.withHeader(header.name(), header.value());
            }
        }
        return updated;
    }

    /**
     * 删除请求中的所有鉴权字段。
     *
     * @param original 原始请求
     * @return 移除所有鉴权头后的新 HttpRequest 对象
     */
    public static HttpRequest removeAuthHeaders(HttpRequest original) {
        List<HttpHeader> toRemove = new ArrayList<>();
        for (HttpHeader header : original.headers()) {
            if (isAuthHeader(header.name())) {
                toRemove.add(header);
            }
        }
        return toRemove.isEmpty() ? original : original.withRemovedHeaders(toRemove);
    }

    private void handleCustomFakeIp(ContextMenuEvent event, List<HttpRequestResponse> selectedItems) {
        I18n i18n = I18n.getInstance();
        String ip = JOptionPane.showInputDialog(null,
                i18n.text("auth_context_menu", "dialog.fakeIp.custom.message"),
                i18n.text("auth_context_menu", "dialog.fakeIp.custom.title"),
                JOptionPane.PLAIN_MESSAGE);
        if (ip == null) {
            return;
        }
        String trimmedIp = ip.trim();
        if (!fakeIpService.isValidIpv4(trimmedIp)) {
            JOptionPane.showMessageDialog(null,
                    i18n.text("auth_context_menu", "dialog.fakeIp.invalid.message"),
                    i18n.text("auth_context_menu", "dialog.fakeIp.invalid.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        applyFakeIp(event, selectedItems,
                request -> fakeIpService.addFakeIpHeaders(request, trimmedIp), false);
    }

    private void applyFakeIp(ContextMenuEvent event, List<HttpRequestResponse> selectedItems,
                             Function<HttpRequest, HttpRequest> requestTransformer,
                             boolean sendToIntruder) {
        Optional<MessageEditorHttpRequestResponse> editorContext = event.messageEditorRequestResponse();
        if (editorContext.isPresent()) {
            HttpRequest request = editorContext.get().requestResponse().request();
            if (request == null) {
                return;
            }
            HttpRequest updatedRequest = requestTransformer.apply(request);
            if (sendToIntruder) {
                sendToIntruderHandler.accept(updatedRequest);
            } else {
                editorContext.get().setRequest(updatedRequest);
            }
            return;
        }

        for (HttpRequestResponse item : selectedItems) {
            HttpRequest request = item.request();
            if (request == null) {
                continue;
            }
            HttpRequest updatedRequest = requestTransformer.apply(request);
            if (sendToIntruder) {
                sendToIntruderHandler.accept(updatedRequest);
            } else {
                sendToRepeaterHandler.accept(updatedRequest);
            }
        }
    }

    /**
     * 从选中的请求中提取认证头。
     * 使用模糊匹配：请求头名称（小写）包含 AUTH_HEADER_KEYWORDS 中任一关键字即视为认证头。
     *
     * @param selectedItems 选中的请求响应列表
     * @return 提取到的认证头文本（格式: HeaderName: HeaderValue，每行一条）
     */
    static String extractAuthHeaders(List<HttpRequestResponse> selectedItems) {
        StringBuilder sb = new StringBuilder();
        for (HttpRequestResponse reqResp : selectedItems) {
            HttpRequest request = reqResp.request();
            if (request == null) {
                continue;
            }
            for (HttpHeader header : request.headers()) {
                if (isAuthHeader(header.name())) {
                    sb.append(header.name()).append(": ").append(header.value()).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * 判断请求头名称是否为认证头（模糊匹配）
     *
     * @param headerName 请求头名称
     * @return 如果包含任一认证关键字则返回 true
     */
    public static boolean isAuthHeader(String headerName) {
        if (headerName == null || headerName.isEmpty()) {
            return false;
        }
        String lowerName = headerName.toLowerCase();
        for (String keyword : AUTH_HEADER_KEYWORDS) {
            if (lowerName.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

