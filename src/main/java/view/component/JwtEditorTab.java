package view.component;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import utils.JwtTextUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;

/**
 * JWT 编辑器选项卡
 * 实现 ExtensionProvidedHttpRequestEditor，当 HTTP 请求中包含 JWT 时，
 * 在 Burp 的请求编辑器中显示一个 "JWT" 选项卡，允许用户查看和编辑 JWT。
 *
 * 支持多 JWT：通过下拉列表选择不同的 JWT 进行编辑，
 * 编辑 Header/Payload 时动态重建 JWT 并实时替换请求中对应的 token。
 */
public class JwtEditorTab implements ExtensionProvidedHttpRequestEditor {

    private static final String[] ALGORITHMS = {
            "HS256", "HS384", "HS512",
            "RS256", "RS384", "RS512",
            "ES256", "ES384", "ES512"
    };

    private final JPanel mainPanel;
    private final JComboBox<String> comboJwtSelector;
    private final JTextArea textAreaJwt;
    private final JTextArea textAreaHeader;
    private final JTextArea textAreaPayload;
    private final JTextArea textAreaSignature;
    private final JTextField textFieldSecret;
    private final JComboBox<String> comboAlgorithm;
    private final boolean editorEditable;

    private HttpRequestResponse currentRequestResponse;
    private boolean modified = false;

    /** 请求中提取到的所有原始 JWT（按出现顺序，允许重复） */
    private final List<String> originalJwts = new ArrayList<>();
    /** 每个 JWT 当前位置对应的当前值 */
    private final List<String> currentJwts = new ArrayList<>();
    /** JWT 在请求头中的精确位置 */
    private final List<HeaderJwtOccurrence> headerJwtOccurrences = new ArrayList<>();

    /** 防止编辑事件递归触发 */
    private boolean updatingUI = false;

    public JwtEditorTab() {
        this(null, null);
    }

    public JwtEditorTab(MontoyaApi api, EditorCreationContext creationContext) {
        this.editorEditable = creationContext == null || creationContext.editorMode() != EditorMode.READ_ONLY;
        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

        comboJwtSelector = new JComboBox<>();
        comboJwtSelector.setFont(monoFont);

        textAreaJwt = new JTextArea(3, 40);
        textAreaJwt.setFont(monoFont);
        textAreaJwt.setLineWrap(true);
        textAreaJwt.setWrapStyleWord(true);
        textAreaJwt.setEditable(false);

        textAreaHeader = new JTextArea(4, 30);
        textAreaHeader.setFont(monoFont);
        textAreaHeader.setLineWrap(true);
        textAreaHeader.setEditable(editorEditable);

        textAreaPayload = new JTextArea(6, 30);
        textAreaPayload.setFont(monoFont);
        textAreaPayload.setLineWrap(true);
        textAreaPayload.setEditable(editorEditable);

        textAreaSignature = new JTextArea(2, 30);
        textAreaSignature.setFont(monoFont);
        textAreaSignature.setLineWrap(true);
        textAreaSignature.setEditable(false);

        textFieldSecret = new JTextField("MyJwtSecret");
        textFieldSecret.setFont(monoFont);
        textFieldSecret.setEnabled(editorEditable);

        comboAlgorithm = new JComboBox<>(ALGORITHMS);
        comboAlgorithm.setEnabled(editorEditable);

        mainPanel = buildUI();
        bindEvents();
    }

    private JPanel buildUI() {
        // 顶部操作栏：两行响应式布局
        JPanel actionBar = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        comboJwtSelector.setPrototypeDisplayValue(
                "JWT #1: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9................................");
        comboAlgorithm.setPrototypeDisplayValue("HS384");

        // 第一行：JWT 选择器
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        actionBar.add(new JLabel("JWT:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.gridwidth = 5;
        actionBar.add(comboJwtSelector, gbc);

        // 第二行：Algorithm + Secret
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        gbc.weightx = 0;
        actionBar.add(new JLabel("Algorithm:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.2;
        actionBar.add(comboAlgorithm, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        actionBar.add(new JLabel("Secret:"), gbc);

        gbc.gridx = 3;
        gbc.weightx = 0.8;
        gbc.gridwidth = 3;
        actionBar.add(textFieldSecret, gbc);

        JPanel tokenPanel = new JPanel(new BorderLayout());
        tokenPanel.setBorder(BorderFactory.createTitledBorder("JWT Token"));
        tokenPanel.add(new JScrollPane(textAreaJwt), BorderLayout.CENTER);

        // 底部: Header / Payload / Signature 三栏
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createTitledBorder("Header"));
        headerPanel.add(new JScrollPane(textAreaHeader), BorderLayout.CENTER);

        JPanel payloadPanel = new JPanel(new BorderLayout());
        payloadPanel.setBorder(BorderFactory.createTitledBorder("Payload"));
        payloadPanel.add(new JScrollPane(textAreaPayload), BorderLayout.CENTER);

        JPanel signaturePanel = new JPanel(new BorderLayout());
        signaturePanel.setBorder(BorderFactory.createTitledBorder("Signature"));
        signaturePanel.add(new JScrollPane(textAreaSignature), BorderLayout.CENTER);

        JPanel editPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        editPanel.add(headerPanel);
        editPanel.add(payloadPanel);
        editPanel.add(signaturePanel);

        JPanel wrapper = new JPanel(new BorderLayout(0, 3));
        wrapper.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        wrapper.add(actionBar, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tokenPanel, editPanel);
        splitPane.setResizeWeight(0.24);
        splitPane.setBorder(null);

        wrapper.add(splitPane, BorderLayout.CENTER);

        return wrapper;
    }

    private void bindEvents() {
        // JWT 选择切换 → 解码显示
        comboJwtSelector.addActionListener(e -> {
            if (updatingUI) return;
            onJwtSelected();
        });

        if (!editorEditable) {
            return;
        }

        comboAlgorithm.addActionListener(e -> {
            if (updatingUI) return;
            onEditChanged();
        });

        // Header / Payload 编辑 → 动态重建当前 JWT
        DocumentListener dynamicEncoder = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onEditChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onEditChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onEditChanged(); }
        };
        textAreaHeader.getDocument().addDocumentListener(dynamicEncoder);
        textAreaPayload.getDocument().addDocumentListener(dynamicEncoder);
        textFieldSecret.getDocument().addDocumentListener(dynamicEncoder);
    }

    /** 用户从下拉列表中选择了某个 JWT → 解码展示 */
    private void onJwtSelected() {
        int index = comboJwtSelector.getSelectedIndex();
        if (index < 0 || index >= originalJwts.size()) return;

        displayJwtWithoutTriggeringEdit(currentJwts.get(index));
    }

    private void displayJwtWithoutTriggeringEdit(String jwt) {
        updatingUI = true;
        try {
            decodeAndDisplay(jwt);
        } finally {
            updatingUI = false;
        }
    }

    /** 解码 JWT 并填充 Header / Payload / Signature，自动选择算法 */
    private void decodeAndDisplay(String jwt) {
        try {
            DecodedJWT decoded = JWT.decode(jwt);
            textAreaJwt.setText(jwt);
            String header = new String(
                    Base64.getUrlDecoder().decode(decoded.getHeader()), StandardCharsets.UTF_8);
            String payload = new String(
                    Base64.getUrlDecoder().decode(decoded.getPayload()), StandardCharsets.UTF_8);
            textAreaHeader.setText(JwtTextUtils.formatJson(header));
            textAreaPayload.setText(JwtTextUtils.formatJson(payload));
            textAreaSignature.setText(decoded.getSignature());
            autoSelectAlgorithm(header);
        } catch (Exception ex) {
            textAreaHeader.setText("Error: " + ex.getMessage());
            textAreaPayload.setText("");
            textAreaSignature.setText("");
        }
    }

    /** Header 或 Payload 被编辑 → 动态重建当前选中的 JWT */
    private void onEditChanged() {
        if (updatingUI || !editorEditable) return;
        int index = comboJwtSelector.getSelectedIndex();
        if (index < 0 || index >= originalJwts.size()) return;

        String headerJson = textAreaHeader.getText().trim();
        String payloadJson = textAreaPayload.getText().trim();
        if (headerJson.isEmpty() || payloadJson.isEmpty()) return;

        try {
            String headerB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String unsignedToken = headerB64 + "." + payloadB64;

            String selectedAlg = (String) comboAlgorithm.getSelectedItem();
            String secret = textFieldSecret.getText().trim();
            String newJwt;

            if (!secret.isEmpty() && isHmacAlgorithm(selectedAlg)) {
                Algorithm algorithm = resolveHmacAlgorithm(selectedAlg, secret);
                byte[] sigBytes = algorithm.sign(unsignedToken.getBytes(StandardCharsets.UTF_8));
                String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
                newJwt = unsignedToken + "." + signature;
                textAreaSignature.setText(signature);
            } else {
                // 无密钥或非 HMAC → 保留原始签名或空签名
                String currentJwt = currentJwts.get(index);
                String[] parts = currentJwt.split("\\.", -1);
                String origSig = parts.length >= 3 ? parts[2] : "";
                newJwt = unsignedToken + "." + origSig;
                textAreaSignature.setText(origSig);
            }

            currentJwts.set(index, newJwt);
            modified = hasModifiedTokens();
            textAreaJwt.setText(newJwt);

            // 更新下拉列表中的显示文本（截断展示）
            updatingUI = true;
            try {
                comboJwtSelector.removeItemAt(index);
                comboJwtSelector.insertItemAt(truncateForDisplay(newJwt, index), index);
                comboJwtSelector.setSelectedIndex(index);
            } finally {
                updatingUI = false;
            }
        } catch (Exception ignored) {
            // 编辑过程中可能出现不完整 JSON，忽略
        }
    }

    /** 只从请求头中提取 JWT，避免编辑 body 后 Burp 主报文面板同步异常。 */
    private List<HeaderJwtOccurrence> extractHeaderJwtOccurrences(HttpRequest request) {
        List<HeaderJwtOccurrence> occurrences = new ArrayList<>();
        if (request == null) return occurrences;

        try {
            List<HttpHeader> headers = request.headers();
            if (headers == null) return occurrences;

            for (HttpHeader header : headers) {
                String value = header.value();
                if (value == null || value.isEmpty()) continue;

                Matcher matcher = JwtTextUtils.JWT_PATTERN.matcher(value);
                while (matcher.find()) {
                    String jwt = matcher.group(1);
                    if (JwtTextUtils.isValidJwt(jwt)) {
                        occurrences.add(new HeaderJwtOccurrence(
                                header.name(), value, matcher.start(1), matcher.end(1)));
                    }
                }
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return occurrences;
    }

    private boolean hasModifiedTokens() {
        if (originalJwts.size() != currentJwts.size()) {
            return false;
        }
        for (int i = 0; i < originalJwts.size(); i++) {
            if (!originalJwts.get(i).equals(currentJwts.get(i))) {
                return true;
            }
        }
        return false;
    }

    private String rebuildHeaderValue(int index) {
        HeaderJwtOccurrence occurrence = headerJwtOccurrences.get(index);
        StringBuilder builder = new StringBuilder(occurrence.originalHeaderValue());
        builder.replace(occurrence.start(), occurrence.end(), currentJwts.get(index));
        return builder.toString();
    }

    /** 截断 JWT 用于下拉列表显示 */
    private String truncateForDisplay(String jwt, int index) {
        String label = "JWT #" + (index + 1) + ": ";
        int maxLen = 80;
        if (jwt.length() > maxLen) {
            return label + jwt.substring(0, maxLen) + "...";
        }
        return label + jwt;
    }

    // ===== ExtensionProvidedHttpRequestEditor 接口实现 =====

    @Override
    public HttpRequest getRequest() {
        if (currentRequestResponse == null) return null;
        if (!editorEditable || !modified) {
            return currentRequestResponse.request();
        }
        HttpRequest updatedRequest = currentRequestResponse.request();
        boolean[] processed = new boolean[headerJwtOccurrences.size()];
        for (int i = 0; i < headerJwtOccurrences.size(); i++) {
            if (processed[i]) {
                continue;
            }
            HeaderJwtOccurrence occurrence = headerJwtOccurrences.get(i);
            String updatedHeaderValue = rebuildHeaderValueForSameHeader(i, processed);
            if (!occurrence.originalHeaderValue().equals(updatedHeaderValue)) {
                updatedRequest = updatedRequest.withUpdatedHeader(occurrence.headerName(), updatedHeaderValue);
            }
        }
        return updatedRequest;
    }

    private String rebuildHeaderValueForSameHeader(int baseIndex, boolean[] processed) {
        HeaderJwtOccurrence base = headerJwtOccurrences.get(baseIndex);
        StringBuilder builder = new StringBuilder(base.originalHeaderValue());
        for (int i = headerJwtOccurrences.size() - 1; i >= 0; i--) {
            HeaderJwtOccurrence current = headerJwtOccurrences.get(i);
            if (current.headerName().equals(base.headerName())
                    && current.originalHeaderValue().equals(base.originalHeaderValue())) {
                processed[i] = true;
                if (!originalJwts.get(i).equals(currentJwts.get(i))) {
                    builder.replace(current.start(), current.end(), currentJwts.get(i));
                }
            }
        }
        return builder.toString();
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        this.currentRequestResponse = requestResponse;
        this.modified = false;
        originalJwts.clear();
        currentJwts.clear();
        headerJwtOccurrences.clear();

        updatingUI = true;
        try {
            comboJwtSelector.removeAllItems();
            if (requestResponse == null || requestResponse.request() == null) {
                clearFields();
                return;
            }

            List<HeaderJwtOccurrence> occurrences = extractHeaderJwtOccurrences(requestResponse.request());
            if (occurrences.isEmpty()) {
                clearFields();
                return;
            }

            headerJwtOccurrences.addAll(occurrences);
            for (int i = 0; i < occurrences.size(); i++) {
                HeaderJwtOccurrence occurrence = occurrences.get(i);
                String jwt = occurrence.originalHeaderValue()
                        .substring(occurrence.start(), occurrence.end());
                originalJwts.add(jwt);
                currentJwts.add(jwt);
                comboJwtSelector.addItem(truncateForDisplay(jwt, i));
            }
            comboJwtSelector.setSelectedIndex(0);
        } finally {
            updatingUI = false;
        }

        // 解码第一个 JWT（避免初始化时触发动态编辑）
        if (!originalJwts.isEmpty()) {
            displayJwtWithoutTriggeringEdit(originalJwts.get(0));
        }
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        try {
            if (requestResponse == null || requestResponse.request() == null) return false;
            return !extractHeaderJwtOccurrences(requestResponse.request()).isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public String caption() { return "JWT"; }

    @Override
    public Component uiComponent() { return mainPanel; }

    @Override
    public Selection selectedData() { return null; }

    @Override
    public boolean isModified() { return editorEditable && modified; }

    private void clearFields() {
        textAreaJwt.setText("");
        textAreaHeader.setText("");
        textAreaPayload.setText("");
        textAreaSignature.setText("");
    }

    private record HeaderJwtOccurrence(String headerName, String originalHeaderValue, int start, int end) {
    }

    /** 自动根据 header JSON 中的 alg 字段选择下拉框 */
    private void autoSelectAlgorithm(String headerJson) {
        for (String alg : ALGORITHMS) {
            if (headerJson.contains("\"" + alg + "\"")
                    || headerJson.contains("\"" + alg.toLowerCase() + "\"")) {
                comboAlgorithm.setSelectedItem(alg);
                return;
            }
        }
    }

    private boolean isHmacAlgorithm(String name) {
        return name != null && name.startsWith("HS");
    }

    private Algorithm resolveHmacAlgorithm(String name, String secret) {
        return switch (name) {
            case "HS384" -> Algorithm.HMAC384(secret);
            case "HS512" -> Algorithm.HMAC512(secret);
            default -> Algorithm.HMAC256(secret);
        };
    }
}