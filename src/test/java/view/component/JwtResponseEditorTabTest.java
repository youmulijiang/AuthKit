package view.component;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import utils.I18n;

import javax.swing.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtResponseEditorTabTest {

    private static final String HEADER_B64 = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    private static final String PAYLOAD_B64 = b64("{\"sub\":\"admin\",\"exp\":9999999999}");
    private static final String JWT_TOKEN = HEADER_B64 + "." + PAYLOAD_B64 + ".c2ln";

    @Test
    @DisplayName("响应体包含 JWT 时应启用选项卡并解码展示 Payload 与位置")
    void bodyJwt_shouldEnableAndDecode() throws Exception {
        HttpResponse response = mock(HttpResponse.class);
        when(response.headers()).thenReturn(List.of());
        when(response.bodyToString()).thenReturn("{\"token\":\"" + JWT_TOKEN + "\"}");
        HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
        when(requestResponse.response()).thenReturn(response);

        JwtResponseEditorTab tab = new JwtResponseEditorTab();
        assertTrue(tab.isEnabledFor(requestResponse));
        SwingUtilities.invokeAndWait(() -> tab.setRequestResponse(requestResponse));

        JTextArea payload = getField(tab, "textAreaPayload", JTextArea.class);
        JTextArea signature = getField(tab, "textAreaSignature", JTextArea.class);
        JLabel location = getField(tab, "labelLocationValue", JLabel.class);
        JLabel algorithm = getField(tab, "labelAlgorithmValue", JLabel.class);
        JLabel expiresAt = getField(tab, "labelExpiresAtValue", JLabel.class);

        assertTrue(payload.getText().contains("\"sub\": \"admin\""));
        assertEquals("c2ln", signature.getText());
        assertEquals(I18n.getInstance().text("jwt", "label.body"), location.getText());
        assertEquals("HS256", algorithm.getText());
        assertTrue(expiresAt.getText().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "过期时间应格式化显示，实际: " + expiresAt.getText());
    }

    @Test
    @DisplayName("响应头包含 JWT 时位置应显示响应头名称，且支持头/体多 JWT 切换")
    void headerAndBodyJwt_shouldShowLocationAndSwitch() throws Exception {
        HttpHeader setCookie = mock(HttpHeader.class);
        when(setCookie.name()).thenReturn("Set-Cookie");
        when(setCookie.value()).thenReturn("session=" + JWT_TOKEN);

        HttpResponse response = mock(HttpResponse.class);
        when(response.headers()).thenReturn(List.of(setCookie));
        when(response.bodyToString()).thenReturn("{\"token\":\"" + JWT_TOKEN + "\"}");
        HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
        when(requestResponse.response()).thenReturn(response);

        JwtResponseEditorTab tab = new JwtResponseEditorTab();
        SwingUtilities.invokeAndWait(() -> tab.setRequestResponse(requestResponse));

        JComboBox<String> selector = getField(tab, "comboJwtSelector", JComboBox.class);
        JLabel location = getField(tab, "labelLocationValue", JLabel.class);
        assertEquals(2, selector.getItemCount());
        assertEquals("Set-Cookie", location.getText());

        // 切换到响应体中的 JWT → 位置更新为 Body
        SwingUtilities.invokeAndWait(() -> selector.setSelectedIndex(1));
        assertEquals(I18n.getInstance().text("jwt", "label.body"), location.getText());
    }

    @Test
    @DisplayName("响应中无 JWT 或响应读取异常时应安全返回 false")
    void noJwtOrBrokenResponse_shouldReturnFalse() {
        HttpResponse emptyResponse = mock(HttpResponse.class);
        when(emptyResponse.headers()).thenReturn(List.of());
        when(emptyResponse.bodyToString()).thenReturn("{\"ok\":true}");
        HttpRequestResponse noJwt = mock(HttpRequestResponse.class);
        when(noJwt.response()).thenReturn(emptyResponse);
        assertFalse(new JwtResponseEditorTab().isEnabledFor(noJwt));

        HttpResponse brokenResponse = mock(HttpResponse.class);
        when(brokenResponse.headers()).thenThrow(new RuntimeException("broken response"));
        HttpRequestResponse broken = mock(HttpRequestResponse.class);
        when(broken.response()).thenReturn(brokenResponse);
        assertFalse(new JwtResponseEditorTab().isEnabledFor(broken));

        HttpRequestResponse noResponse = mock(HttpRequestResponse.class);
        when(noResponse.response()).thenReturn(null);
        assertFalse(new JwtResponseEditorTab().isEnabledFor(noResponse));
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
