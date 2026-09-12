package core.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.InvocationType;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import utils.HttpHeaderUtils;
import utils.I18n;
import view.AuthContextMenuProvider;
import view.dialog.AuthHistorySelectDialog;

import javax.swing.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 鉴权历史操作服务（M层领域服务）
 * <p>
 * 基于代理历史执行"更新为最新鉴权"、"从历史选择鉴权"、"删除鉴权字段"三类操作。
 * 输出目标（编辑器或 Repeater）由事件上下文决定。
 */
public class AuthHistoryService {

    private final MontoyaApi montoyaApi;

    public AuthHistoryService(MontoyaApi montoyaApi) {
        this.montoyaApi = montoyaApi;
    }

    /**
     * 从代理历史中查找同 host 下最新的含不同鉴权字段的请求，替换原始请求的鉴权字段后输出。
     * 仅可写 Request 编辑器会直接回写；代理历史等只读视图改为发送到 Repeater。
     */
    public void updateToLatestAuth(ContextMenuEvent event, List<HttpRequestResponse> items) {
        if (items == null || items.isEmpty()) return;
        HttpRequest originalRequest = items.get(0).request();
        if (originalRequest == null) return;
        String host = originalRequest.httpService().host();

        List<ProxyHttpRequestResponse> history = montoyaApi.proxy().history();
        ProxyHttpRequestResponse latest = null;
        ZonedDateTime latestTime = null;

        for (ProxyHttpRequestResponse item : history) {
            if (!host.equals(item.host())) continue;
            boolean hasAuth = false;
            boolean hasDiff = false;
            for (HttpHeader header : item.request().headers()) {
                if (!AuthContextMenuProvider.isAuthHeader(header.name())) continue;
                hasAuth = true;
                String origValue = originalRequest.headerValue(header.name());
                if (!header.value().equals(origValue)) {
                    hasDiff = true;
                }
            }
            if (hasAuth && hasDiff) {
                ZonedDateTime t = item.time();
                if (latestTime == null || (t != null && t.isAfter(latestTime))) {
                    latest = item;
                    latestTime = t;
                }
            }
        }

        if (latest == null) {
            JOptionPane.showMessageDialog(null,
                    I18n.getInstance().text("auth_context_menu", "dialog.authHistory.noLatest"),
                    I18n.getInstance().text("auth_context_menu", "menu.updateAuth"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        applyAuthHeaders(event, originalRequest, latest.request());
    }

    /**
     * 弹出历史选择对话框，展示全量代理历史（含鉴权字段的请求）。
     * Dialog 内部负责 host/referer/自定义 范围过滤与去重；用户选中后将所选请求的
     * 鉴权字段替换到原始请求。仅可写 Request 编辑器会直接回写，否则发送到 Repeater。
     */
    public void selectFromHistory(JComponent parent, ContextMenuEvent event,
                                  List<HttpRequestResponse> items) {
        if (items == null || items.isEmpty()) return;
        HttpRequest originalRequest = items.get(0).request();
        if (originalRequest == null) return;
        String originalHost = originalRequest.httpService().host();
        String originalReferer = originalRequest.headerValue("Referer");

        // 拉取全量代理历史，仅保留含鉴权字段的条目，传给 Dialog 自行过滤
        List<ProxyHttpRequestResponse> history = montoyaApi.proxy().history();
        List<ProxyHttpRequestResponse> withAuth = new ArrayList<>();
        for (ProxyHttpRequestResponse item : history) {
            if (hasAuthHeaders(item.request())) {
                withAuth.add(item);
            }
        }

        if (withAuth.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    I18n.getInstance().text("auth_context_menu", "dialog.authHistory.noHistory"),
                    I18n.getInstance().text("auth_context_menu", "menu.updateAuth"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        AuthHistorySelectDialog.show(parent, montoyaApi, withAuth, originalHost, originalReferer,
                selected -> applyAuthHeaders(event, originalRequest, selected.request()));
    }

    /**
     * 删除选中请求中的鉴权字段。配置非空时只按配置删除，配置为空时使用默认规则。
     * 仅可写 Request 编辑器会直接回写；代理历史等只读视图以及表格选中项改为发送到 Repeater。
     */
    public void deleteAuthFields(ContextMenuEvent event, List<HttpRequestResponse> items,
                                 List<String> authHeaderNameKeywords) {
        if (items == null || items.isEmpty()) return;
        if (canWriteToRequestEditor(event)) {
            HttpRequest request =
                    event.messageEditorRequestResponse().get().requestResponse().request();
            if (request == null) return;
            outputUpdatedRequest(event, HttpHeaderUtils.removeAuthHeaders(request, authHeaderNameKeywords),
                    "AuthKit - Removed Auth");
            return;
        }
        for (HttpRequestResponse item : items) {
            HttpRequest request = item.request();
            if (request == null) continue;
            montoyaApi.repeater().sendToRepeater(
                    HttpHeaderUtils.removeAuthHeaders(request, authHeaderNameKeywords),
                    "AuthKit - Removed Auth");
        }
    }

    /**
     * 将 source 的鉴权头应用到 original，并按事件上下文输出。
     */
    void applyAuthHeaders(ContextMenuEvent event, HttpRequest originalRequest, HttpRequest sourceRequest) {
        if (originalRequest == null || sourceRequest == null) return;
        outputUpdatedRequest(event,
                AuthContextMenuProvider.replaceAuthHeaders(originalRequest, sourceRequest),
                "AuthKit - Updated Auth");
    }

    /**
     * 仅在可写的 Request 编辑器（如 Repeater）中回写；代理历史等只读视图中 setRequest 会被静默忽略，
     * 此时以及存在表格选中项时改为发送到 Repeater。
     */
    void outputUpdatedRequest(ContextMenuEvent event, HttpRequest updated, String repeaterTabName) {
        if (canWriteToRequestEditor(event)) {
            event.messageEditorRequestResponse().get().setRequest(updated);
            return;
        }
        montoyaApi.repeater().sendToRepeater(updated, repeaterTabName);
    }

    /**
     * 判断当前右键上下文是否允许直接改写请求编辑器。
     * 代理历史同时带有只读消息查看器，不能仅凭 editor 存在就调用 setRequest。
     */
    static boolean canWriteToRequestEditor(ContextMenuEvent event) {
        if (event == null) return false;
        List<HttpRequestResponse> explicitSelectedItems = event.selectedRequestResponses();
        boolean hasExplicitSelectedItems = explicitSelectedItems != null && !explicitSelectedItems.isEmpty();
        Optional<MessageEditorHttpRequestResponse> editorCtx = event.messageEditorRequestResponse();
        return !hasExplicitSelectedItems
                && editorCtx.isPresent()
                && event.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST);
    }

    private static boolean hasAuthHeaders(HttpRequest request) {
        for (HttpHeader header : request.headers()) {
            if (AuthContextMenuProvider.isAuthHeader(header.name())) return true;
        }
        return false;
    }
}
