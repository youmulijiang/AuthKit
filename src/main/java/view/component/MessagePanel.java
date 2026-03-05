package view.component;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import javax.swing.*;
import java.awt.*;

/**
 * 报文面板组件（可复用）
 * 内含一个 JTabbedPane，包含 Request 和 Response 两个选项卡，
 * 使用 Montoya API 的 HttpRequestEditor 和 HttpResponseEditor 展示报文。
 * 每个鉴权对象（Original / Unauthorized / User1 ...）对应一个 MessagePanel 实例。
 */
public class MessagePanel extends JPanel {

    private final JTabbedPane tabbedMessage;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;

    /**
     * 构造报文面板
     *
     * @param api Montoya API 实例
     */
    public MessagePanel(MontoyaApi api) {
        this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.tabbedMessage = new JTabbedPane();
        this.tabbedMessage.addTab("Request", requestEditor.uiComponent());
        this.tabbedMessage.addTab("Response", responseEditor.uiComponent());
        initLayout();
    }

    /** 初始化布局 */
    private void initLayout() {
        setLayout(new BorderLayout());
        add(tabbedMessage, BorderLayout.CENTER);
    }

    /** 获取报文 TabbedPane */
    public JTabbedPane getTabbedMessage() {
        return tabbedMessage;
    }

    /** 获取 Montoya 请求编辑器 */
    public HttpRequestEditor getRequestEditor() {
        return requestEditor;
    }

    /** 获取 Montoya 响应编辑器 */
    public HttpResponseEditor getResponseEditor() {
        return responseEditor;
    }

    /**
     * 设置报文内容（使用 Montoya 原始对象）
     *
     * @param request  Montoya HttpRequest 对象
     * @param response Montoya HttpResponse 对象
     */
    public void setContent(HttpRequest request, HttpResponse response) {
        if (request != null) {
            requestEditor.setRequest(request);
        }
        if (response != null) {
            responseEditor.setResponse(response);
        }
    }

    /** 清空报文内容 */
    public void clearContent() {
        requestEditor.setRequest(HttpRequest.httpRequest(""));
        responseEditor.setResponse(HttpResponse.httpResponse(""));
    }

    /**
     * 获取当前选中的 Tab 索引
     * 0 = Request, 1 = Response
     */
    public int getSelectedTabIndex() {
        return tabbedMessage.getSelectedIndex();
    }
}
