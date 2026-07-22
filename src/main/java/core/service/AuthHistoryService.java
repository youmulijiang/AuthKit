package core.service;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import utils.I18n;
import view.AuthContextMenuProvider;
import view.dialog.AuthHistorySelectDialog;

import javax.swing.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * 若触发来源是消息编辑器则直接修改编辑器内容，否则发送到 Repeater。
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

        HttpRequest updated =
                AuthContextMenuProvider.replaceAuthHeaders(originalRequest, latest.request());
        Optional<MessageEditorHttpRequestResponse> editorCtx =
                event.messageEditorRequestResponse();
        if (editorCtx.isPresent()) {
            editorCtx.get().setRequest(updated);
        } else {
            montoyaApi.repeater().sendToRepeater(updated, "AuthKit - Updated Auth");
        }
    }

    /**
     * 弹出历史选择对话框，展示同 host 下按鉴权字段去重的代理历史。
     * 用户选中后将所选请求的鉴权字段替换到原始请求，输出到编辑器或 Repeater。
     */
    public void selectFromHistory(JComponent parent, ContextMenuEvent event,
                                  List<HttpRequestResponse> items) {
        if (items == null || items.isEmpty()) return;
        HttpRequest originalRequest = items.get(0).request();
        if (originalRequest == null) return;
        String host = originalRequest.httpService().host();

        // 同 host、含鉴权字段的代理历史，按鉴权字段组合去重，同 key 保留最新
        List<ProxyHttpRequestResponse> history = montoyaApi.proxy().history();
        LinkedHashMap<String, ProxyHttpRequestResponse> deduped = new LinkedHashMap<>();
        for (ProxyHttpRequestResponse item : history) {
            if (!host.equals(item.host())) continue;
            String key = buildAuthDeduplicationKey(item.request());
            if (!key.isEmpty()) {
                deduped.put(key, item);
            }
        }

        if (deduped.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    I18n.getInstance().text("auth_context_menu", "dialog.authHistory.noHistory"),
                    I18n.getInstance().text("auth_context_menu", "menu.updateAuth"),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<ProxyHttpRequestResponse> dedupedList = new ArrayList<>(deduped.values());
        AuthHistorySelectDialog.show(parent, montoyaApi, dedupedList, selected -> {
            HttpRequest updated =
                    AuthContextMenuProvider.replaceAuthHeaders(originalRequest, selected.request());
            Optional<MessageEditorHttpRequestResponse> editorCtx =
                    event.messageEditorRequestResponse();
            if (editorCtx.isPresent()) {
                editorCtx.get().setRequest(updated);
            } else {
                montoyaApi.repeater().sendToRepeater(updated, "AuthKit - Updated Auth");
            }
        });
    }

    /**
     * 删除选中请求中的所有鉴权字段。
     * 若在编辑器上下文中，直接回写编辑器；否则发送到 Repeater。
     */
    public void deleteAuthFields(ContextMenuEvent event, List<HttpRequestResponse> items) {
        if (items == null || items.isEmpty()) return;
        Optional<MessageEditorHttpRequestResponse> editorCtx =
                event.messageEditorRequestResponse();
        if (editorCtx.isPresent()) {
            HttpRequest request =
                    editorCtx.get().requestResponse().request();
            if (request == null) return;
            editorCtx.get().setRequest(AuthContextMenuProvider.removeAuthHeaders(request));
        } else {
            for (HttpRequestResponse item : items) {
                HttpRequest request = item.request();
                if (request == null) continue;
                montoyaApi.repeater().sendToRepeater(
                        AuthContextMenuProvider.removeAuthHeaders(request), "AuthKit - Removed Auth");
            }
        }
    }

    /**
     * 构建代理历史鉴权字段的去重键。
     * 将请求中所有鉴权字段（名称小写:值）排序后拼接，相同组合视为同一鉴权上下文。
     */
    private static String buildAuthDeduplicationKey(HttpRequest request) {
        List<String> parts = new ArrayList<>();
        for (HttpHeader header : request.headers()) {
            if (AuthContextMenuProvider.isAuthHeader(header.name())) {
                parts.add(header.name().toLowerCase() + "=" + header.value());
            }
        }
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }
}
