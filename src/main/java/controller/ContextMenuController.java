package controller;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import core.FakeIpService;
import core.HashService;
import core.RequestReplayService;
import core.scan.bypass403.Bypass403PayloadService;
import core.scan.bypass403.Bypass403ScanService;
import core.scan.idor.IdorPayloadService;
import core.scan.idor.IdorScanService;
import core.scan.jwt.JwtPayloadService;
import core.scan.jwt.JwtScanService;
import core.service.AuthHistoryService;
import model.AuthUserModel;
import model.MessageDataModel;
import utils.LogUtils;
import view.AuthContextMenuProvider;
import view.MainPanel;
import view.component.AuthUserConfigPanel;
import view.component.NewUserDialog;
import view.component.UserPanel;
import view.dialog.AiAuthScanDialog;
import view.dialog.Bypass403ScanDialog;
import view.dialog.IdorScanDialog;
import view.dialog.JwtScanDialog;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 右键菜单事件控制器（C层）
 * <p>
 * 编排右键菜单触发的业务流程：送测、提取鉴权、新建用户、Fake IP、
 * 403 绕过扫描、IDOR 扫描、鉴权历史操作。扫描执行与历史操作委托至对应领域服务。
 */
public class ContextMenuController {

    private final MontoyaApi montoyaApi;
    private final MainPanel mainPanel;
    private final AuthController controller;
    private final RequestReplayService replayService;
    private final FakeIpService fakeIpService;
    private final ExecutorService executor;
    private final Runnable refreshDataTableCallback;

    public ContextMenuController(MontoyaApi montoyaApi, MainPanel mainPanel, AuthController controller,
                                 RequestReplayService replayService, FakeIpService fakeIpService,
                                 ExecutorService executor, Runnable refreshDataTableCallback) {
        this.montoyaApi = montoyaApi;
        this.mainPanel = mainPanel;
        this.controller = controller;
        this.replayService = replayService;
        this.fakeIpService = fakeIpService;
        this.executor = executor;
        this.refreshDataTableCallback = refreshDataTableCallback;
    }

    /**
     * 组装右键菜单 Provider 并注册到 Montoya UI（工厂方法）
     */
    public void register() {
        // 用户名称提供者：从 UserPanel 获取当前已配置的用户名称
        java.util.function.Supplier<List<String>> userNamesSupplier = () ->
                new ArrayList<>(mainPanel.getPanelUser().getUserPanels().keySet());

        // Send 处理回调：主动发包，由插件内部处理所有鉴权用户
        java.util.function.Consumer<List<HttpRequestResponse>> sendHandler = (selectedItems) -> {
            for (HttpRequestResponse reqResp : selectedItems) {
                if (reqResp.request() == null) {
                    continue;
                }
                executor.submit(() -> {
                    try {
                        processContextMenuRequest(reqResp);
                    } catch (Exception ex) {
                        LogUtils.INSTANCE.error("Error processing context menu request", ex);
                    }
                });
            }
        };

        // Extract 处理回调：将提取到的认证头文本填入指定用户的配置
        java.util.function.BiConsumer<String, String> extractHandler = (authText, userName) -> {
            SwingUtilities.invokeLater(() -> {
                UserPanel userPanel = mainPanel.getPanelUser();
                AuthUserConfigPanel configPanel = userPanel.getUserPanel(userName);
                if (configPanel != null) {
                    configPanel.getTextAreaAuthHeaders().setText(authText);
                    LogUtils.INSTANCE.info("Extracted auth headers to user: " + userName);
                }
            });
        };

        // 新建用户回调：弹出新建用户对话框，用户确认后创建用户并返回名称
        java.util.function.Function<String, String> createUserHandler = (authText) -> {
            final String[] newName = {null};
            try {
                Runnable showDialog = () -> {
                    // 生成默认名称
                    UserPanel userPanel = mainPanel.getPanelUser();
                    String defaultName = "User" + (userPanel.getUserCount() + 1);

                    // 弹出新建用户对话框
                    NewUserDialog.UserConfig config =
                            NewUserDialog.show(mainPanel, defaultName, authText);
                    if (config == null) {
                        return; // 用户取消
                    }

                    // 创建用户并应用对话框中的配置
                    AuthUserConfigPanel panel = userPanel.addUser();
                    // addUser() 使用自动生成的名称（如 "User1"），需要获取该名称
                    String autoName = panel.getUserName();
                    panel.getCheckBoxEnabled().setSelected(config.enabled());
                    panel.getTextAreaAuthHeaders().setText(config.authHeaders());
                    panel.getTextAreaParamReplacement().setText(config.paramReplacement());
                    // 如果用户在对话框中输入了不同的名称，执行重命名以同步到所有面板
                    if (!autoName.equals(config.name())) {
                        userPanel.renameUser(autoName, config.name());
                    }
                    newName[0] = config.name();
                    LogUtils.INSTANCE.info("Created new user via dialog: " + config.name());
                };

                if (SwingUtilities.isEventDispatchThread()) {
                    showDialog.run();
                } else {
                    SwingUtilities.invokeAndWait(showDialog);
                }
            } catch (Exception ex) {
                LogUtils.INSTANCE.error("Error creating new user via dialog", ex);
            }
            return newName[0];
        };

        // 插件启用状态提供者
        java.util.function.Supplier<Boolean> enabledSupplier =
                () -> mainPanel.getPanelConfiguration().getCheckBoxEnabled().isSelected();
        java.util.function.Supplier<List<String>> authHeaderKeywordsSupplier =
                () -> controller.getConfigModel().getAuthHeaders();

        // 开启插件的回调：点击勾选框触发 doClick，等效于用户手动勾选 Enable Plugin
        Runnable enablePluginHandler = () -> {
            JCheckBox cb = mainPanel.getPanelConfiguration().getCheckBoxEnabled();
            if (!cb.isSelected()) {
                cb.doClick();
            }
        };

        java.util.function.Consumer<HttpRequest> sendToRepeaterHandler =
                request -> montoyaApi.repeater().sendToRepeater(request, "AuthKit Fake IP");
        java.util.function.Consumer<HttpRequest> sendToIntruderHandler =
                request -> montoyaApi.intruder().sendToIntruder(request, "AuthKit Fake IP");

        Bypass403PayloadService bypass403PayloadService = new Bypass403PayloadService();
        Bypass403ScanService bypass403ScanService =
                new Bypass403ScanService(montoyaApi, bypass403PayloadService);

        IdorPayloadService idorPayloadService = new IdorPayloadService();
        IdorScanService idorScanService = new IdorScanService(montoyaApi, idorPayloadService);

        JwtPayloadService jwtPayloadService = new JwtPayloadService();
        JwtScanService jwtScanService = new JwtScanService(montoyaApi, jwtPayloadService);

        // Scan 一级菜单回调：全部扫描（按共享配置自动执行 403 / IDOR / JWT 三个扫描项）
        java.util.function.Consumer<List<HttpRequestResponse>> scanAllHandler =
                selectedItems -> runScanAll(mainPanel, selectedItems,
                        bypass403ScanService, idorScanService, jwtScanService);

        // AI 分析回调：切换到主界面 AI 选项卡，将数据包交给 AI 对话面板分析
        java.util.function.Consumer<List<HttpRequestResponse>> aiAnalysisHandler =
                selectedItems -> SwingUtilities.invokeLater(() -> {
                    mainPanel.getTabbedRight().setSelectedComponent(mainPanel.getPanelAi());
                    mainPanel.getPanelAi().sendPacketsAuto(selectedItems);
                });

        // AI 越权扫描回调：弹窗内可编辑提示词并查看 AI 发送的数据包，开始后交给 AI 对话面板执行
        core.PacketSourceService packetSourceService = new core.PacketSourceService(montoyaApi);
        java.util.function.Consumer<List<HttpRequestResponse>> aiScanHandler =
                selectedItems -> SwingUtilities.invokeLater(() -> {
                    AiAuthScanDialog dialog = new AiAuthScanDialog(
                            montoyaApi, mainPanel, packetSourceService, selectedItems);
                    dialog.setStartHandler((packets, prompt) -> {
                        mainPanel.getTabbedRight().setSelectedComponent(mainPanel.getPanelAi());
                        // 注册发包监听，AI 每发一个包就回填到扫描弹窗
                        mainPanel.getPanelAi().setToolSendListener(dialog);
                        mainPanel.getPanelAi().startAuthScan(packets, prompt);
                    });
                    dialog.setCloseHandler(() -> mainPanel.getPanelAi().setToolSendListener(null));
                    dialog.showDialog();
                });

        AuthHistoryService authHistoryService = new AuthHistoryService(montoyaApi);

        // 更新为最新鉴权字段回调
        java.util.function.BiConsumer<burp.api.montoya.ui.contextmenu.ContextMenuEvent,
                List<HttpRequestResponse>> updateToLatestAuthHandler =
                (event, items) -> authHistoryService.updateToLatestAuth(event, items);

        // 从历史选择鉴权字段回调
        java.util.function.BiConsumer<burp.api.montoya.ui.contextmenu.ContextMenuEvent,
                List<HttpRequestResponse>> selectFromHistoryHandler =
                (event, items) -> authHistoryService.selectFromHistory(mainPanel, event, items);

        // 删除所有鉴权字段回调
        java.util.function.BiConsumer<burp.api.montoya.ui.contextmenu.ContextMenuEvent,
                List<HttpRequestResponse>> deleteAuthHandler =
                (event, items) -> authHistoryService.deleteAuthFields(
                        event, items, authHeaderKeywordsSupplier.get());

        AuthContextMenuProvider contextMenuProvider =
                new AuthContextMenuProvider(userNamesSupplier, enabledSupplier, enablePluginHandler,
                        sendHandler, extractHandler, createUserHandler, authHeaderKeywordsSupplier, fakeIpService,
                        sendToRepeaterHandler, sendToIntruderHandler, scanAllHandler,
                        updateToLatestAuthHandler, selectFromHistoryHandler, deleteAuthHandler);
        contextMenuProvider.setBypass403ScanHandler(selectedItems ->
                runBypass403Scan(mainPanel, selectedItems, bypass403ScanService));
        contextMenuProvider.setIdorScanHandler(selectedItems ->
                runIdorScan(mainPanel, selectedItems, idorScanService));
        contextMenuProvider.setJwtScanHandler(selectedItems ->
                runJwtScan(mainPanel, selectedItems, jwtScanService));
        contextMenuProvider.setAiAnalysisHandler(aiAnalysisHandler);
        contextMenuProvider.setAiScanHandler(aiScanHandler);
        montoyaApi.userInterface().registerContextMenuItemsProvider(contextMenuProvider);
    }

    /**
     * 处理右键菜单发送的请求：构建原始数据、收集所有鉴权用户重放请求、更新 UI
     */
    private void processContextMenuRequest(HttpRequestResponse reqResp) {
        HttpRequest request = reqResp.request();
        HttpResponse response = reqResp.response();

        // 如果没有响应，先发送请求获取响应
        if (response == null) {
            response = replayService.sendRaw(request);
        }

        // 去重检查
        if (!controller.isNewRequest(request.method(), request.url())) {
            return;
        }

        // 构建原始报文数据
        MessageDataModel originalData = new MessageDataModel(
                request.toString(), response.toString(),
                response.statusCode(),
                response.bodyToString().length(),
                HashService.hash(response.bodyToString()),
                request, response
        );
        originalData.setContentType(response.headerValue("Content-Type") != null
                ? response.headerValue("Content-Type") : "");
        // 从右键菜单选中条目的 annotations 读取 Burp 备注
        if (reqResp.annotations() != null && reqResp.annotations().hasNotes()) {
            originalData.setNote(reqResp.annotations().notes());
        }

        // 收集所有鉴权用户
        List<AuthUserModel> users = view.binding.UserPanelBinder.collectUsers(mainPanel.getPanelUser());

        // 处理请求
        final HttpResponse finalResponse = response;
        controller.processRequest(request, finalResponse, originalData, users);

        // 更新 UI
        SwingUtilities.invokeLater(refreshDataTableCallback);
    }

    /**
     * 打开 403 绕过扫描弹窗，由用户配置后点击开始（Scan 二级菜单单独触发）
     */
    private void runBypass403Scan(JComponent parent, List<HttpRequestResponse> selectedItems,
                                  Bypass403ScanService scanService) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }
        Bypass403ScanDialog dialog = new Bypass403ScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        dialog.setStartHandler((threadCount, followRedirects) -> executor.submit(() ->
                scanService.executeScan(selectedItems, dialog, threadCount, followRedirects,
                        scanPoolRef, stopRequested)));

        dialog.showDialog();
    }

    /**
     * 打开 IDOR 扫描弹窗，由用户配置后点击开始（Scan 二级菜单单独触发）
     */
    private void runIdorScan(JComponent parent, List<HttpRequestResponse> selectedItems,
                             IdorScanService scanService) {
        if (selectedItems == null || selectedItems.isEmpty()) return;

        IdorScanDialog dialog = new IdorScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        dialog.setStartHandler(threadCount -> executor.submit(() ->
                scanService.executeScan(selectedItems, dialog, threadCount, scanPoolRef, stopRequested)));

        dialog.showDialog();
    }

    /**
     * 打开 JWT 扫描弹窗，由用户配置后点击开始（Scan 二级菜单单独触发）
     */
    private void runJwtScan(JComponent parent, List<HttpRequestResponse> selectedItems,
                            JwtScanService scanService) {
        if (selectedItems == null || selectedItems.isEmpty()) return;

        boolean containsJwt = selectedItems.stream().anyMatch(item -> item != null
                && item.request() != null
                && new JwtPayloadService().containsJwt(item.request()));
        if (!containsJwt) {
            JOptionPane.showMessageDialog(parent,
                    utils.I18n.getInstance().text("auth_context_menu", "dialog.jwtScan.noToken"),
                    utils.I18n.getInstance().text("auth_context_menu", "dialog.jwtScan.title"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        JwtScanDialog dialog = new JwtScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        dialog.setStartHandler((threadCount, followRedirects) -> executor.submit(() ->
                scanService.executeScan(selectedItems, dialog, threadCount, followRedirects,
                        scanPoolRef, stopRequested)));
        dialog.showDialog();
    }

    /**
     * "全部扫描"：自动执行 403 / IDOR / JWT 三个扫描项，各扫描结果在独立弹窗中
     * 并行展示。使用默认共享配置（线程数 5、不跟随重定向）。
     * AI 越权扫描为对话式流程，不参与一键全部扫描。
     */
    private void runScanAll(JComponent parent, List<HttpRequestResponse> selectedItems,
                            Bypass403ScanService bypass403ScanService,
                            IdorScanService idorScanService,
                            JwtScanService jwtScanService) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return;
        }
        boolean containsJwt = selectedItems.stream().anyMatch(item -> item != null
                && item.request() != null
                && new JwtPayloadService().containsJwt(item.request()));

        SwingUtilities.invokeLater(() -> {
            startBypass403Scan(parent, selectedItems, bypass403ScanService,
                    DEFAULT_SCAN_THREADS, false);
            startIdorScan(parent, selectedItems, idorScanService, DEFAULT_SCAN_THREADS);
            if (containsJwt) {
                startJwtScan(parent, selectedItems, jwtScanService,
                        DEFAULT_SCAN_THREADS, false);
            }
        });
    }

    /** 全部扫描使用的默认线程数 */
    private static final int DEFAULT_SCAN_THREADS = 5;

    /** 启动 403 绕过扫描（按共享配置自动开始，无需用户在弹窗内再点开始） */
    private void startBypass403Scan(JComponent parent, List<HttpRequestResponse> selectedItems,
                                    Bypass403ScanService scanService,
                                    int threadCount, boolean followRedirects) {
        Bypass403ScanDialog dialog = new Bypass403ScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        executor.submit(() -> scanService.executeScan(selectedItems, dialog,
                threadCount, followRedirects, scanPoolRef, stopRequested));
        dialog.showDialog();
    }

    /** 启动 IDOR 扫描（按共享配置自动开始） */
    private void startIdorScan(JComponent parent, List<HttpRequestResponse> selectedItems,
                               IdorScanService scanService, int threadCount) {
        IdorScanDialog dialog = new IdorScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        executor.submit(() -> scanService.executeScan(selectedItems, dialog,
                threadCount, scanPoolRef, stopRequested));
        dialog.showDialog();
    }

    /** 启动 JWT 扫描（按共享配置自动开始） */
    private void startJwtScan(JComponent parent, List<HttpRequestResponse> selectedItems,
                              JwtScanService scanService, int threadCount, boolean followRedirects) {
        JwtScanDialog dialog = new JwtScanDialog(montoyaApi, parent);
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicReference<ExecutorService> scanPoolRef = new AtomicReference<>();

        dialog.setCloseHandler(() -> {
            stopRequested.set(true);
            ExecutorService pool = scanPoolRef.get();
            if (pool != null) pool.shutdownNow();
        });

        executor.submit(() -> scanService.executeScan(selectedItems, dialog,
                threadCount, followRedirects, scanPoolRef, stopRequested));
        dialog.showDialog();
    }
}
