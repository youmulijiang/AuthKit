package view.component;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import utils.I18n;

import javax.swing.*;
import java.awt.*;

/**
 * 报文面板组件（可复用）
 * 内含一个 JTabbedPane，包含 Request、Response 和 Request&lt;-&gt;Response 三个选项卡。
 * 前两个分别展示请求/响应；第三个为水平分割面板，同时展示请求与响应。
 * 使用 Montoya API 的 HttpRequestEditor 和 HttpResponseEditor 展示报文。
 * 每个鉴权对象（Original / Unauthorized / User1 ...）对应一个 MessagePanel 实例。
 */
public class MessagePanel extends JPanel {

    public static final int REQUEST_TAB_INDEX = 0;
    public static final int RESPONSE_TAB_INDEX = 1;
    public static final int COMBINED_TAB_INDEX = 2;

    /** 当前选中的报文视图类型 */
    public enum MessageView {
        REQUEST,
        RESPONSE,
        COMBINED
    }

    private final JTabbedPane tabbedMessage;
    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;
    /** 组合视图中的请求编辑器（与单独 Request Tab 分离，避免同一组件挂载两处） */
    private final HttpRequestEditor combinedRequestEditor;
    /** 组合视图中的响应编辑器 */
    private final HttpResponseEditor combinedResponseEditor;
    private final JSplitPane splitCombined;
    private String requestText = "";
    private String responseText = "";

    /**
     * 构造报文面板
     *
     * @param api Montoya API 实例
     */
    public MessagePanel(MontoyaApi api) {
        this.requestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.responseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.combinedRequestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        this.combinedResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.splitCombined = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                combinedRequestEditor.uiComponent(),
                combinedResponseEditor.uiComponent());
        this.splitCombined.setResizeWeight(0.5);
        this.splitCombined.setContinuousLayout(true);

        this.tabbedMessage = new JTabbedPane();
        this.tabbedMessage.addTab("", requestEditor.uiComponent());
        this.tabbedMessage.addTab("", responseEditor.uiComponent());
        this.tabbedMessage.addTab("", splitCombined);
        initLayout();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
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

    /** 获取组合视图的水平分割面板 */
    public JSplitPane getSplitCombined() {
        return splitCombined;
    }

    /** 获取 Montoya 请求编辑器（Request 选项卡） */
    public HttpRequestEditor getRequestEditor() {
        return requestEditor;
    }

    /** 获取 Montoya 响应编辑器（Response 选项卡） */
    public HttpResponseEditor getResponseEditor() {
        return responseEditor;
    }

    /** 获取缓存的请求报文文本 */
    public String getRequestText() {
        return requestText;
    }

    /** 获取缓存的响应报文文本 */
    public String getResponseText() {
        return responseText;
    }

    /**
     * 设置报文内容（使用 Montoya 原始对象）
     *
     * @param request  Montoya HttpRequest 对象
     * @param response Montoya HttpResponse 对象
     */
    public void setContent(HttpRequest request, HttpResponse response) {
        setContent(request, response,
                request != null ? request.toString() : "",
                response != null ? response.toString() : "");
    }

    /**
     * 设置报文内容（同时写入编辑器和缓存文本）
     * 响应缓存仅保留响应体（bodyToString），供 Diff 比较使用，排除响应头。
     */
    public void setContent(HttpRequest request, HttpResponse response, String requestText, String responseText) {
        this.requestText = requestText != null ? requestText : "";
        this.responseText = response != null ? response.bodyToString()
                : (responseText != null ? responseText : "");
        if (request != null) {
            requestEditor.setRequest(request);
            combinedRequestEditor.setRequest(request);
        }
        if (response != null) {
            responseEditor.setResponse(response);
            combinedResponseEditor.setResponse(response);
        }
    }

    /** 清空报文内容 */
    public void clearContent() {
        requestText = "";
        responseText = "";
        HttpRequest emptyRequest = HttpRequest.httpRequest("");
        HttpResponse emptyResponse = HttpResponse.httpResponse("");
        requestEditor.setRequest(emptyRequest);
        responseEditor.setResponse(emptyResponse);
        combinedRequestEditor.setRequest(emptyRequest);
        combinedResponseEditor.setResponse(emptyResponse);
    }

    /**
     * 获取当前选中的 Tab 索引
     * 0 = Request, 1 = Response, 2 = Request&lt;-&gt;Response
     */
    public int getSelectedTabIndex() {
        return tabbedMessage.getSelectedIndex();
    }

    /** 设置当前选中的报文 Tab */
    public void setSelectedTabIndex(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= tabbedMessage.getTabCount()) {
            return;
        }
        tabbedMessage.setSelectedIndex(tabIndex);
    }

    /** 获取当前选中的报文视图类型 */
    public MessageView getSelectedView() {
        return switch (tabbedMessage.getSelectedIndex()) {
            case REQUEST_TAB_INDEX -> MessageView.REQUEST;
            case RESPONSE_TAB_INDEX -> MessageView.RESPONSE;
            default -> MessageView.COMBINED;
        };
    }

    /**
     * 获取当前视图对应的报文文本，供 Diff 使用。
     * Response 视图仅返回响应体（排除响应头）；组合视图将请求与响应体拼接后返回。
     */
    public String getSelectedText() {
        return switch (getSelectedView()) {
            case REQUEST -> requestText;
            case RESPONSE -> responseText;
            case COMBINED -> {
                if (requestText.isEmpty()) {
                    yield responseText;
                }
                if (responseText.isEmpty()) {
                    yield requestText;
                }
                yield requestText + "\n\n" + responseText;
            }
        };
    }

    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        tabbedMessage.setTitleAt(REQUEST_TAB_INDEX, i18n.text("message", "tab.request"));
        tabbedMessage.setTitleAt(RESPONSE_TAB_INDEX, i18n.text("message", "tab.response"));
        tabbedMessage.setTitleAt(COMBINED_TAB_INDEX, i18n.text("message", "tab.combined"));
    }
}
