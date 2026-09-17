package view.component;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import utils.I18n;
import utils.JwtTextUtils;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * JWT 响应分析选项卡（只读）
 * 实现 ExtensionProvidedHttpResponseEditor，当 HTTP 响应中包含 JWT 时，
 * 在 Burp 的响应编辑器中显示一个 "JWT" 选项卡，展示解码后的
 * Header / Payload / Signature 以及签发时间、过期时间、生效时间与有效性状态。
 *
 * 支持多 JWT：从响应头和响应体中提取，通过下拉列表切换查看。
 */
public class JwtResponseEditorTab implements ExtensionProvidedHttpResponseEditor {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JPanel mainPanel;
    private final JComboBox<String> comboJwtSelector;
    private final JTextArea textAreaToken;
    private final JTextArea textAreaHeader;
    private final JTextArea textAreaPayload;
    private final JTextArea textAreaSignature;
    private final JLabel labelLocationValue;
    private final JLabel labelAlgorithmValue;
    private final JLabel labelIssuedAtValue;
    private final JLabel labelExpiresAtValue;
    private final JLabel labelNotBeforeValue;
    private final JLabel labelStatusValue;

    private HttpRequestResponse currentRequestResponse;

    /** 响应中提取到的所有 JWT 及其位置描述 */
    private final List<JwtOccurrence> occurrences = new ArrayList<>();

    /** 防止选择事件递归触发 */
    private boolean updatingUI = false;

    public JwtResponseEditorTab() {
        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);
        I18n i18n = I18n.getInstance();

        comboJwtSelector = new JComboBox<>();
        comboJwtSelector.setFont(monoFont);
        comboJwtSelector.setPrototypeDisplayValue(
                "JWT #1: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9................................");

        textAreaToken = createReadOnlyTextArea(3, 40, monoFont);
        textAreaHeader = createReadOnlyTextArea(4, 30, monoFont);
        textAreaPayload = createReadOnlyTextArea(6, 30, monoFont);
        textAreaSignature = createReadOnlyTextArea(2, 30, monoFont);

        labelLocationValue = new JLabel("-");
        labelAlgorithmValue = new JLabel("-");
        labelIssuedAtValue = new JLabel("-");
        labelExpiresAtValue = new JLabel("-");
        labelNotBeforeValue = new JLabel("-");
        labelStatusValue = new JLabel("-");

        mainPanel = buildUI(i18n);
        bindEvents();
    }

    private static JTextArea createReadOnlyTextArea(int rows, int cols, Font font) {
        JTextArea textArea = new JTextArea(rows, cols);
        textArea.setFont(font);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        return textArea;
    }

    private JPanel buildUI(I18n i18n) {
        // 顶部：JWT 选择器
        JPanel selectorBar = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        selectorBar.add(new JLabel(i18n.text("jwt", "label.jwt")), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        selectorBar.add(comboJwtSelector, gbc);

        // 分析信息栏：Location / Algorithm / Issued At / Expires At / Not Before / Status
        JPanel infoPanel = new JPanel(new GridLayout(2, 3, 8, 2));
        infoPanel.setBorder(BorderFactory.createTitledBorder(i18n.text("jwt", "section.analysis")));
        infoPanel.add(infoItem(i18n.text("jwt", "label.location"), labelLocationValue));
        infoPanel.add(infoItem(i18n.text("jwt", "label.signAlgorithm"), labelAlgorithmValue));
        infoPanel.add(infoItem(i18n.text("jwt", "label.status"), labelStatusValue));
        infoPanel.add(infoItem(i18n.text("jwt", "label.issuedAt"), labelIssuedAtValue));
        infoPanel.add(infoItem(i18n.text("jwt", "label.expiresAt"), labelExpiresAtValue));
        infoPanel.add(infoItem(i18n.text("jwt", "label.notBefore"), labelNotBeforeValue));

        JPanel tokenPanel = new JPanel(new BorderLayout());
        tokenPanel.setBorder(BorderFactory.createTitledBorder(i18n.text("jwt", "label.token")));
        tokenPanel.add(new JScrollPane(textAreaToken), BorderLayout.CENTER);

        // 底部: Header / Payload / Signature 三栏
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createTitledBorder("Header"));
        headerPanel.add(new JScrollPane(textAreaHeader), BorderLayout.CENTER);

        JPanel payloadPanel = new JPanel(new BorderLayout());
        payloadPanel.setBorder(BorderFactory.createTitledBorder("Payload"));
        payloadPanel.add(new JScrollPane(textAreaPayload), BorderLayout.CENTER);

        JPanel signaturePanel = new JPanel(new BorderLayout());
        signaturePanel.setBorder(BorderFactory.createTitledBorder(i18n.text("jwt", "label.signature")));
        signaturePanel.add(new JScrollPane(textAreaSignature), BorderLayout.CENTER);

        JPanel editPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        editPanel.add(headerPanel);
        editPanel.add(payloadPanel);
        editPanel.add(signaturePanel);

        JPanel wrapper = new JPanel(new BorderLayout(0, 3));
        wrapper.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        wrapper.add(selectorBar, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tokenPanel, editPanel);
        splitPane.setResizeWeight(0.3);
        splitPane.setBorder(null);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 3));
        centerPanel.add(infoPanel, BorderLayout.NORTH);
        centerPanel.add(splitPane, BorderLayout.CENTER);
        wrapper.add(centerPanel, BorderLayout.CENTER);

        return wrapper;
    }

    private static JPanel infoItem(String label, JLabel valueLabel) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        panel.add(new JLabel(label));
        panel.add(valueLabel);
        return panel;
    }

    private void bindEvents() {
        comboJwtSelector.addActionListener(e -> {
            if (updatingUI) return;
            onJwtSelected();
        });
    }

    /** 用户从下拉列表中选择了某个 JWT → 解码展示 */
    private void onJwtSelected() {
        int index = comboJwtSelector.getSelectedIndex();
        if (index < 0 || index >= occurrences.size()) return;
        displayJwt(occurrences.get(index));
    }

    /** 解码 JWT 并填充 Token / Header / Payload / Signature 与分析信息 */
    private void displayJwt(JwtOccurrence occurrence) {
        labelLocationValue.setText(occurrence.location());
        decodeAndDisplay(occurrence.token());
    }

    private void decodeAndDisplay(String jwt) {
        I18n i18n = I18n.getInstance();
        try {
            DecodedJWT decoded = JWT.decode(jwt);
            textAreaToken.setText(jwt);
            String header = new String(
                    Base64.getUrlDecoder().decode(decoded.getHeader()), StandardCharsets.UTF_8);
            String payload = new String(
                    Base64.getUrlDecoder().decode(decoded.getPayload()), StandardCharsets.UTF_8);
            textAreaHeader.setText(JwtTextUtils.formatJson(header));
            textAreaPayload.setText(JwtTextUtils.formatJson(payload));
            textAreaSignature.setText(decoded.getSignature());

            labelAlgorithmValue.setText(decoded.getAlgorithm() != null ? decoded.getAlgorithm() : "-");
            labelIssuedAtValue.setText(formatDate(decoded.getIssuedAt()));
            labelExpiresAtValue.setText(formatDate(decoded.getExpiresAt()));
            labelNotBeforeValue.setText(formatDate(decoded.getNotBefore()));
            labelStatusValue.setText(resolveStatus(decoded, i18n));
        } catch (Exception ex) {
            textAreaHeader.setText("Error: " + ex.getMessage());
            textAreaPayload.setText("");
            textAreaSignature.setText("");
        }
    }

    /** 计算 JWT 有效性状态：优先判断过期，其次判断尚未生效 */
    private String resolveStatus(DecodedJWT decoded, I18n i18n) {
        Date now = new Date();
        Date expiresAt = decoded.getExpiresAt();
        Date notBefore = decoded.getNotBefore();
        if (expiresAt != null && now.after(expiresAt)) {
            return i18n.text("jwt", "message.statusExpired");
        }
        if (notBefore != null && now.before(notBefore)) {
            return i18n.text("jwt", "message.statusNotYetValid");
        }
        if (expiresAt != null || notBefore != null) {
            return i18n.text("jwt", "message.statusValid");
        }
        return "-";
    }

    private static String formatDate(Date date) {
        if (date == null) {
            return "-";
        }
        LocalDateTime time = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        return TIME_FORMATTER.format(time);
    }

    /** 从响应头和响应体中提取所有合法 JWT */
    private List<JwtOccurrence> extractJwtOccurrences(HttpResponse response) {
        List<JwtOccurrence> result = new ArrayList<>();
        if (response == null) return result;

        try {
            List<HttpHeader> headers = response.headers();
            if (headers != null) {
                for (HttpHeader header : headers) {
                    String value = header.value();
                    if (value == null || value.isEmpty()) continue;
                    for (String jwt : JwtTextUtils.extractJwts(value)) {
                        result.add(new JwtOccurrence(header.name(), jwt));
                    }
                }
            }
            String bodyText = response.bodyToString();
            if (bodyText != null && !bodyText.isEmpty()) {
                for (String jwt : JwtTextUtils.extractJwts(bodyText)) {
                    result.add(new JwtOccurrence(I18n.getInstance().text("jwt", "label.body"), jwt));
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return result;
    }

    private String truncateForDisplay(String jwt, int index) {
        String label = "JWT #" + (index + 1) + ": ";
        int maxLen = 80;
        if (jwt.length() > maxLen) {
            return label + jwt.substring(0, maxLen) + "...";
        }
        return label + jwt;
    }

    // ===== ExtensionProvidedHttpResponseEditor 接口实现 =====

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.currentRequestResponse = requestResponse;
        occurrences.clear();

        updatingUI = true;
        try {
            comboJwtSelector.removeAllItems();
            clearFields();
            if (requestResponse == null || requestResponse.response() == null) {
                return;
            }

            occurrences.addAll(extractJwtOccurrences(requestResponse.response()));
            if (occurrences.isEmpty()) {
                return;
            }

            for (int i = 0; i < occurrences.size(); i++) {
                comboJwtSelector.addItem(truncateForDisplay(occurrences.get(i).token(), i));
            }
            comboJwtSelector.setSelectedIndex(0);
        } finally {
            updatingUI = false;
        }

        // 解码第一个 JWT
        if (!occurrences.isEmpty()) {
            displayJwt(occurrences.get(0));
        }
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        try {
            if (requestResponse == null || requestResponse.response() == null) return false;
            return !extractJwtOccurrences(requestResponse.response()).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public HttpResponse getResponse() {
        if (currentRequestResponse == null) return null;
        return currentRequestResponse.response();
    }

    @Override
    public String caption() {
        return I18n.getInstance().text("jwt", "caption.jwt");
    }

    @Override
    public Component uiComponent() {
        return mainPanel;
    }

    @Override
    public Selection selectedData() {
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    private void clearFields() {
        textAreaToken.setText("");
        textAreaHeader.setText("");
        textAreaPayload.setText("");
        textAreaSignature.setText("");
        labelLocationValue.setText("-");
        labelAlgorithmValue.setText("-");
        labelIssuedAtValue.setText("-");
        labelExpiresAtValue.setText("-");
        labelNotBeforeValue.setText("-");
        labelStatusValue.setText("-");
    }

    /** 响应中的一个 JWT 及其位置描述（响应头名称或 Body） */
    private record JwtOccurrence(String location, String token) {
    }
}
