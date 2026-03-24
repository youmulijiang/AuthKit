package view.component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import utils.I18n;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT 工具面板
 * 位于右侧 TabbedPane 的 JWT 选项卡中，提供 JWT 解码、编码、校验和爆破功能。
 */
public class JwtPanel extends JPanel {

    private static final String[] ALGORITHMS = {
            "HS256", "HS384", "HS512",
            "RS256", "RS384", "RS512",
            "ES256", "ES384", "ES512"
    };
    private static final String[] ENCODING_TYPES = {"None", "Base64", "UTF-8"};

    // ===== 左侧操作区 =====
    private final JComboBox<String> comboAlgorithm;
    private final JButton btnDecode;
    private final JButton btnEncode;
    private final JButton btnVerify;

    // ===== 右侧解析区 =====
    private final JTextArea textAreaJwt;
    private final JTextArea textAreaHeader;
    private final JTextArea textAreaPayload;
    private final JTextField textFieldVerify;
    private final JTextField textFieldSecret;

    // ===== 爆破设置区 =====
    private final JComboBox<String> comboEncodingType;
    private final JTextField textFieldDictPath;
    private final JButton btnSelectDict;
    private final JButton btnStartBrute;
    private final JButton btnStopBrute;
    private final JProgressBar progressBar;

    // ===== i18n borders & labels =====
    private TitledBorder borderOperation;
    private TitledBorder borderParse;
    private TitledBorder borderBruteForce;
    private JLabel labelAlgorithm;
    private JLabel labelHeader;
    private JLabel labelPayload;
    private JLabel labelVerify;
    private JLabel labelSecret;
    private JLabel labelEncodingType;
    private JLabel labelDictPath;

    // ===== 爆破线程控制 =====
    private volatile boolean bruteForceRunning = false;
    private Thread bruteForceThread;

    public JwtPanel() {
        Font monoFont = new Font("Monospaced", Font.PLAIN, 12);

        this.comboAlgorithm = new JComboBox<>(ALGORITHMS);
        this.btnDecode = new JButton();
        this.btnEncode = new JButton();
        this.btnVerify = new JButton();
        this.btnVerify.setBackground(new Color(0xE8, 0x6B, 0x30));
        this.btnVerify.setForeground(Color.WHITE);
        this.btnVerify.setFocusPainted(false);

        this.textAreaJwt = new JTextArea(3, 40);
        this.textAreaJwt.setFont(monoFont);
        this.textAreaJwt.setLineWrap(true);
        this.textAreaJwt.setWrapStyleWord(true);

        this.textAreaHeader = new JTextArea(4, 30);
        this.textAreaHeader.setFont(monoFont);
        this.textAreaHeader.setLineWrap(true);

        this.textAreaPayload = new JTextArea(5, 30);
        this.textAreaPayload.setFont(monoFont);
        this.textAreaPayload.setLineWrap(true);

        this.textFieldVerify = new JTextField();
        this.textFieldVerify.setFont(monoFont);
        this.textFieldSecret = new JTextField("MyJwtSecret");
        this.textFieldSecret.setFont(monoFont);

        this.comboEncodingType = new JComboBox<>(ENCODING_TYPES);
        this.textFieldDictPath = new JTextField();
        this.textFieldDictPath.setFont(monoFont);
        this.btnSelectDict = new JButton();
        this.btnStartBrute = new JButton();
        this.btnStartBrute.setBackground(new Color(0x4C, 0xAF, 0x50));
        this.btnStartBrute.setForeground(Color.WHITE);
        this.btnStartBrute.setFocusPainted(false);
        this.btnStopBrute = new JButton();
        this.progressBar = new JProgressBar(0, 100);
        this.progressBar.setStringPainted(true);

        initLayout();
        bindEvents();
        I18n.getInstance().addLanguageChangeListener(this::refreshTexts);
        refreshTexts();
    }

    /** 初始化布局：左(JWT输入) | 中(操作按钮) | 右(解析结果) */
    private void initLayout() {
        setLayout(new BorderLayout(5, 5));

        // 左侧: JWT 输入文本框
        JPanel panelLeft = buildJwtInputPanel();
        // 中间: 操作按钮
        JPanel panelCenter = buildOperationPanel();
        // 右侧: 解析结果
        JPanel panelRight = buildParsePanel();

        // 左 | 中+右
        JSplitPane splitRight = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelCenter, panelRight);
        splitRight.setResizeWeight(0.0);
        splitRight.setDividerLocation(150);

        JSplitPane splitMain = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelLeft, splitRight);
        splitMain.setResizeWeight(0.25);
        splitMain.setDividerLocation(200);

        add(splitMain, BorderLayout.CENTER);
    }

    /** 构建左侧JWT输入面板 */
    private JPanel buildJwtInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JScrollPane scrollJwt = new JScrollPane(textAreaJwt);
        scrollJwt.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        panel.add(scrollJwt, BorderLayout.CENTER);
        return panel;
    }

    /** 构建中间操作面板 */
    private JPanel buildOperationPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        borderOperation = new TitledBorder("");
        panel.setBorder(borderOperation);

        labelAlgorithm = new JLabel();
        labelAlgorithm.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(labelAlgorithm);
        panel.add(Box.createVerticalStrut(5));

        comboAlgorithm.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        comboAlgorithm.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(comboAlgorithm);
        panel.add(Box.createVerticalStrut(10));

        btnDecode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnDecode.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(btnDecode);
        panel.add(Box.createVerticalStrut(5));

        btnEncode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btnEncode.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(btnEncode);
        panel.add(Box.createVerticalStrut(10));

        btnVerify.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        btnVerify.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(btnVerify);

        panel.add(Box.createVerticalGlue());
        return panel;
    }

    /** 构建右侧解析面板（Header、Payload、Verify、Secret、爆破设置） */
    private JPanel buildParsePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        borderParse = new TitledBorder("");
        panel.setBorder(borderParse);

        // Header
        labelHeader = new JLabel();
        labelHeader.setFont(new Font("SansSerif", Font.BOLD, 12));
        labelHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(labelHeader);
        panel.add(Box.createVerticalStrut(3));
        JScrollPane scrollHeader = new JScrollPane(textAreaHeader);
        scrollHeader.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        scrollHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(scrollHeader);
        panel.add(Box.createVerticalStrut(8));

        // Payload
        labelPayload = new JLabel();
        labelPayload.setFont(new Font("SansSerif", Font.BOLD, 12));
        labelPayload.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(labelPayload);
        panel.add(Box.createVerticalStrut(3));
        JScrollPane scrollPayload = new JScrollPane(textAreaPayload);
        scrollPayload.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
        scrollPayload.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(scrollPayload);
        panel.add(Box.createVerticalStrut(8));

        // Verify
        labelVerify = new JLabel();
        labelVerify.setFont(new Font("SansSerif", Font.BOLD, 12));
        labelVerify.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(labelVerify);
        panel.add(Box.createVerticalStrut(3));
        textFieldVerify.setAlignmentX(Component.LEFT_ALIGNMENT);
        textFieldVerify.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.add(textFieldVerify);
        panel.add(Box.createVerticalStrut(8));

        // Secret
        labelSecret = new JLabel();
        labelSecret.setFont(new Font("SansSerif", Font.BOLD, 12));
        labelSecret.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(labelSecret);
        panel.add(Box.createVerticalStrut(3));
        textFieldSecret.setAlignmentX(Component.LEFT_ALIGNMENT);
        textFieldSecret.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.add(textFieldSecret);
        panel.add(Box.createVerticalStrut(12));

        // 爆破设置区
        panel.add(buildBruteForcePanel());

        JScrollPane outerScroll = new JScrollPane(panel);
        outerScroll.setBorder(null);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(outerScroll, BorderLayout.CENTER);
        return wrapper;
    }

    /** 构建爆破设置面板 */
    private JPanel buildBruteForcePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        borderBruteForce = new TitledBorder("");
        panel.setBorder(borderBruteForce);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // 编码类型行
        JPanel rowEncoding = new JPanel(new BorderLayout(5, 0));
        labelEncodingType = new JLabel();
        rowEncoding.add(labelEncodingType, BorderLayout.WEST);
        comboEncodingType.setPreferredSize(new Dimension(200, 28));
        JPanel comboWrapper = new JPanel(new BorderLayout());
        comboWrapper.add(comboEncodingType, BorderLayout.CENTER);
        JPanel bruteButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        bruteButtons.add(btnStartBrute);
        bruteButtons.add(btnStopBrute);
        comboWrapper.add(bruteButtons, BorderLayout.EAST);
        rowEncoding.add(comboWrapper, BorderLayout.CENTER);
        rowEncoding.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        rowEncoding.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(rowEncoding);
        panel.add(Box.createVerticalStrut(5));

        // 字典路径行
        JPanel rowDict = new JPanel(new BorderLayout(5, 0));
        labelDictPath = new JLabel();
        rowDict.add(labelDictPath, BorderLayout.WEST);
        rowDict.add(textFieldDictPath, BorderLayout.CENTER);
        rowDict.add(btnSelectDict, BorderLayout.EAST);
        rowDict.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        rowDict.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(rowDict);
        panel.add(Box.createVerticalStrut(5));

        // 进度条
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
        panel.add(progressBar);

        return panel;
    }

    /** 绑定按钮事件 */
    private void bindEvents() {
        btnDecode.addActionListener(e -> doDecode());
        btnEncode.addActionListener(e -> doEncode());
        btnVerify.addActionListener(e -> doVerify());
        btnSelectDict.addActionListener(e -> doSelectDict());
        btnStartBrute.addActionListener(e -> doStartBrute());
        btnStopBrute.addActionListener(e -> doStopBrute());
    }

    /** 解码JWT */
    private void doDecode() {
        String jwt = textAreaJwt.getText().trim();
        if (jwt.isEmpty()) {
            return;
        }
        try {
            DecodedJWT decoded = JWT.decode(jwt);
            String header = new String(Base64.getUrlDecoder().decode(decoded.getHeader()), StandardCharsets.UTF_8);
            String payload = new String(Base64.getUrlDecoder().decode(decoded.getPayload()), StandardCharsets.UTF_8);
            textAreaHeader.setText(formatJson(header));
            textAreaPayload.setText(formatJson(payload));
            textFieldVerify.setText(decoded.getSignature());
        } catch (JWTDecodeException ex) {
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("jwt", "message.decodeFailed", ex.getMessage()),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 编码JWT（使用Header/Payload/Secret重新签名生成JWT） */
    private void doEncode() {
        try {
            String secret = textFieldSecret.getText().trim();
            String headerJson = textAreaHeader.getText().trim();
            String payloadJson = textAreaPayload.getText().trim();
            if (secret.isEmpty() || payloadJson.isEmpty()) {
                return;
            }
            // 手动构建JWT: base64url(header).base64url(payload).signature
            String headerB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String unsignedToken = headerB64 + "." + payloadB64;

            Algorithm algorithm = getSelectedAlgorithm(secret);
            byte[] signatureBytes = algorithm.sign(unsignedToken.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            String token = unsignedToken + "." + signature;
            textAreaJwt.setText(token);
            textFieldVerify.setText(signature);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("jwt", "message.encodeFailed", ex.getMessage()),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 校验JWT签名 */
    private void doVerify() {
        String jwt = textAreaJwt.getText().trim();
        String secret = textFieldSecret.getText().trim();
        if (jwt.isEmpty() || secret.isEmpty()) {
            return;
        }
        try {
            Algorithm algorithm = getSelectedAlgorithm(secret);
            JWTVerifier verifier = JWT.require(algorithm).build();
            verifier.verify(jwt);
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().text("jwt", "message.verifySuccess"),
                    "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    I18n.getInstance().format("jwt", "message.verifyFailed", ex.getMessage()),
                    "Failed", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** 选择字典文件 */
    private void doSelectDict() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            textFieldDictPath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    /** 开始爆破 */
    private void doStartBrute() {
        String selectedAlgName = (String) comboAlgorithm.getSelectedItem();
        if (!isHmacAlgorithm(selectedAlgName)) {
            JOptionPane.showMessageDialog(this,
                    "Brute force is only supported for HMAC algorithms (HS256/HS384/HS512)",
                    "Unsupported", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String jwt = textAreaJwt.getText().trim();
        String dictPath = textFieldDictPath.getText().trim();
        if (jwt.isEmpty() || dictPath.isEmpty()) {
            return;
        }
        File dictFile = new File(dictPath);
        if (!dictFile.exists() || !dictFile.isFile()) {
            JOptionPane.showMessageDialog(this, "Dictionary file not found", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        bruteForceRunning = true;
        btnStartBrute.setEnabled(false);
        progressBar.setValue(0);

        bruteForceThread = new Thread(() -> {
            try {
                // 先统计总行数
                long totalLines = 0;
                try (BufferedReader counter = new BufferedReader(new FileReader(dictFile, StandardCharsets.UTF_8))) {
                    while (counter.readLine() != null) {
                        totalLines++;
                    }
                }
                if (totalLines == 0) {
                    return;
                }

                long current = 0;
                String selectedAlg = (String) comboAlgorithm.getSelectedItem();
                String encodingType = (String) comboEncodingType.getSelectedItem();

                try (BufferedReader reader = new BufferedReader(new FileReader(dictFile, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && bruteForceRunning) {
                        current++;
                        String candidateSecret = processSecretEncoding(line.trim(), encodingType);
                        if (candidateSecret.isEmpty()) {
                            continue;
                        }
                        try {
                            Algorithm alg = resolveAlgorithm(selectedAlg, candidateSecret);
                            JWT.require(alg).build().verify(jwt);
                            // 找到密钥
                            final String found = candidateSecret;
                            SwingUtilities.invokeLater(() -> {
                                textFieldSecret.setText(found);
                                progressBar.setValue(100);
                                JOptionPane.showMessageDialog(this,
                                        I18n.getInstance().format("jwt", "message.bruteFound", found),
                                        "Found", JOptionPane.INFORMATION_MESSAGE);
                            });
                            return;
                        } catch (Exception ignored) {
                            // 签名不匹配，继续
                        }
                        // 更新进度
                        final int pct = (int) (current * 100 / totalLines);
                        SwingUtilities.invokeLater(() -> progressBar.setValue(pct));
                    }
                }
                if (bruteForceRunning) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            I18n.getInstance().text("jwt", "message.bruteNotFound"),
                            "Result", JOptionPane.INFORMATION_MESSAGE));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
            } finally {
                bruteForceRunning = false;
                SwingUtilities.invokeLater(() -> btnStartBrute.setEnabled(true));
            }
        }, "jwt-brute-force");
        bruteForceThread.setDaemon(true);
        bruteForceThread.start();
    }

    /** 停止爆破 */
    private void doStopBrute() {
        bruteForceRunning = false;
        if (bruteForceThread != null) {
            bruteForceThread.interrupt();
        }
        btnStartBrute.setEnabled(true);
    }

    /** 根据当前选中的算法和密钥获取Algorithm */
    private Algorithm getSelectedAlgorithm(String secret) throws Exception {
        String alg = (String) comboAlgorithm.getSelectedItem();
        return resolveAlgorithm(alg, secret);
    }

    /** 根据算法名称和密钥/PEM创建Algorithm */
    private Algorithm resolveAlgorithm(String algorithmName, String secret) throws Exception {
        return switch (algorithmName) {
            case "HS384" -> Algorithm.HMAC384(secret);
            case "HS512" -> Algorithm.HMAC512(secret);
            case "RS256" -> Algorithm.RSA256(parseRSAPublicKey(secret), parseRSAPrivateKey(secret));
            case "RS384" -> Algorithm.RSA384(parseRSAPublicKey(secret), parseRSAPrivateKey(secret));
            case "RS512" -> Algorithm.RSA512(parseRSAPublicKey(secret), parseRSAPrivateKey(secret));
            case "ES256" -> Algorithm.ECDSA256(parseECPublicKey(secret), parseECPrivateKey(secret));
            case "ES384" -> Algorithm.ECDSA384(parseECPublicKey(secret), parseECPrivateKey(secret));
            case "ES512" -> Algorithm.ECDSA512(parseECPublicKey(secret), parseECPrivateKey(secret));
            default -> Algorithm.HMAC256(secret);
        };
    }

    /** 判断算法是否为HMAC系列 */
    private boolean isHmacAlgorithm(String algorithmName) {
        return algorithmName != null && algorithmName.startsWith("HS");
    }

    /** 从PEM文本解析RSA公钥（可能返回null） */
    private RSAPublicKey parseRSAPublicKey(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从PEM文本解析RSA私钥（可能返回null） */
    private RSAPrivateKey parseRSAPrivateKey(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从PEM文本解析EC公钥（可能返回null） */
    private ECPublicKey parseECPublicKey(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从PEM文本解析EC私钥（可能返回null） */
    private ECPrivateKey parseECPrivateKey(String pem) {
        try {
            String cleaned = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN EC PRIVATE KEY-----", "")
                    .replace("-----END EC PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(cleaned);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(spec);
        } catch (Exception e) {
            return null;
        }
    }

    /** 处理密钥编码 */
    private String processSecretEncoding(String secret, String encodingType) {
        if (secret.isEmpty()) {
            return "";
        }
        return switch (encodingType) {
            case "Base64" -> {
                try {
                    yield new String(Base64.getDecoder().decode(secret), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    yield secret;
                }
            }
            default -> secret;
        };
    }

    /** 简单的JSON格式化（缩进美化） */
    private String formatJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
            } else if (!inString) {
                switch (c) {
                    case '{', '[' -> {
                        sb.append(c);
                        sb.append('\n');
                        indent++;
                        sb.append("    ".repeat(indent));
                    }
                    case '}', ']' -> {
                        sb.append('\n');
                        indent--;
                        sb.append("    ".repeat(Math.max(0, indent)));
                        sb.append(c);
                    }
                    case ',' -> {
                        sb.append(c);
                        sb.append('\n');
                        sb.append("    ".repeat(indent));
                    }
                    case ':' -> sb.append(": ");
                    case ' ', '\t', '\n', '\r' -> { /* skip whitespace */ }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 刷新国际化文本 */
    private void refreshTexts() {
        I18n i18n = I18n.getInstance();
        borderOperation.setTitle(i18n.text("jwt", "section.operation"));
        borderParse.setTitle(i18n.text("jwt", "section.parse"));
        borderBruteForce.setTitle(i18n.text("jwt", "section.bruteForce"));
        labelAlgorithm.setText(i18n.text("jwt", "label.signAlgorithm"));
        labelHeader.setText(i18n.text("jwt", "label.header"));
        labelPayload.setText(i18n.text("jwt", "label.payload"));
        labelVerify.setText(i18n.text("jwt", "label.verify"));
        labelSecret.setText(i18n.text("jwt", "label.secret"));
        labelEncodingType.setText(i18n.text("jwt", "label.encodingType"));
        labelDictPath.setText(i18n.text("jwt", "label.dictPath"));
        btnDecode.setText(i18n.text("jwt", "button.decode"));
        btnEncode.setText(i18n.text("jwt", "button.encode"));
        btnVerify.setText(i18n.text("jwt", "button.verify"));
        btnStartBrute.setText(i18n.text("jwt", "button.startBrute"));
        btnStopBrute.setText(i18n.text("jwt", "button.stopBrute"));
        btnSelectDict.setText(i18n.text("jwt", "button.selectDict"));
        revalidate();
        repaint();
    }
}