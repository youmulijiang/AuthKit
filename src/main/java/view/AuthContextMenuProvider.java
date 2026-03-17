package view;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import utils.I18n;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /** Send 处理回调：(选中的请求响应列表) */
    private final Consumer<List<HttpRequestResponse>> sendHandler;

    /** Extract 处理回调：(提取到的认证头文本, 目标用户名称，null 表示新建用户) */
    private final BiConsumer<String, String> extractHandler;

    /** 新建用户回调：接收提取到的认证头文本，返回新建用户的名称（null 表示取消） */
    private final Function<String, String> createUserHandler;

    public AuthContextMenuProvider(Supplier<List<String>> userNamesSupplier,
                                    Supplier<Boolean> enabledSupplier,
                                    Consumer<List<HttpRequestResponse>> sendHandler,
                                    BiConsumer<String, String> extractHandler,
                                    Function<String, String> createUserHandler) {
        this.userNamesSupplier = userNamesSupplier;
        this.enabledSupplier = enabledSupplier;
        this.sendHandler = sendHandler;
        this.extractHandler = extractHandler;
        this.createUserHandler = createUserHandler;
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
                JOptionPane.showMessageDialog(null,
                        I18n.getInstance().text("auth_context_menu", "dialog.pluginDisabled.message"),
                        I18n.getInstance().text("auth_context_menu", "dialog.pluginDisabled.title"),
                        JOptionPane.WARNING_MESSAGE);
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
    static boolean isAuthHeader(String headerName) {
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

