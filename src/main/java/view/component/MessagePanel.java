package view.component;

import javax.swing.*;
import java.awt.*;

/**
 * 报文面板组件（可复用）
 * 内含一个 JTabbedPane，包含 Request 和 Response 两个选项卡，
 * 每个选项卡内是一个只读等宽字体的 JTextArea。
 * 每个鉴权对象（Original / Unauthorized / User1 ...）对应一个 MessagePanel 实例。
 */
public class MessagePanel extends JPanel {

    private static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    private final JTabbedPane tabbedMessage;
    private final JTextArea textAreaRequest;
    private final JTextArea textAreaResponse;

    private MessagePanel(Builder builder) {
        this.tabbedMessage = builder.tabbedMessage;
        this.textAreaRequest = builder.textAreaRequest;
        this.textAreaResponse = builder.textAreaResponse;
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

    /** 获取请求报文文本区 */
    public JTextArea getTextAreaRequest() {
        return textAreaRequest;
    }

    /** 获取响应报文文本区 */
    public JTextArea getTextAreaResponse() {
        return textAreaResponse;
    }

    /**
     * 设置报文内容
     *
     * @param request  请求报文
     * @param response 响应报文
     */
    public void setContent(String request, String response) {
        textAreaRequest.setText(request);
        textAreaResponse.setText(response);
        textAreaRequest.setCaretPosition(0);
        textAreaResponse.setCaretPosition(0);
    }

    /** 清空报文内容 */
    public void clearContent() {
        textAreaRequest.setText("");
        textAreaResponse.setText("");
    }

    /**
     * 报文面板建造器
     */
    public static class Builder {

        private JTabbedPane tabbedMessage;
        private JTextArea textAreaRequest;
        private JTextArea textAreaResponse;

        public Builder() {
            this.textAreaRequest = createReadOnlyTextArea();
            this.textAreaResponse = createReadOnlyTextArea();
            this.tabbedMessage = new JTabbedPane();
            this.tabbedMessage.addTab("Request", new JScrollPane(textAreaRequest));
            this.tabbedMessage.addTab("Response", new JScrollPane(textAreaResponse));
        }

        /** 创建只读等宽字体文本区 */
        private JTextArea createReadOnlyTextArea() {
            JTextArea textArea = new JTextArea();
            textArea.setFont(MONO_FONT);
            textArea.setEditable(false);
            return textArea;
        }

        /** 构建报文面板 */
        public MessagePanel build() {
            return new MessagePanel(this);
        }
    }
}

