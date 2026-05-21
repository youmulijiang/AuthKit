package view.component;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.EditorMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtEditorTabTest {

    private static final String HEADER_B64 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";
    private static final String PAYLOAD_ADMIN_B64 = "eyJzdWIiOiJhZG1pbiJ9";
    private static final String ORIGINAL_JWT = HEADER_B64 + "." + PAYLOAD_ADMIN_B64 + ".sig";

    @Test
    @DisplayName("修改请求头 JWT 时应通过 withUpdatedHeader 覆盖原 header，且修改后仍启用 JWT Tab")
    void editHeaderJwt_shouldUpdateHeaderAndKeepTabEnabled() throws Exception {
        String headerValue = "Bearer " + ORIGINAL_JWT;
        HttpHeader authHeader = mock(HttpHeader.class);
        when(authHeader.name()).thenReturn("Authorization");
        when(authHeader.value()).thenReturn(headerValue);

        HttpRequest request = mock(HttpRequest.class);
        HttpRequest updatedRequest = mock(HttpRequest.class);
        when(request.headers()).thenReturn(List.of(authHeader));
        when(request.withUpdatedHeader(eq("Authorization"), anyString())).thenReturn(updatedRequest);
        HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
        when(requestResponse.request()).thenReturn(request);

        JwtEditorTab tab = new JwtEditorTab();
        SwingUtilities.invokeAndWait(() -> tab.setRequestResponse(requestResponse));

        JTextArea payload = getField(tab, "textAreaPayload", JTextArea.class);
        JTextArea jwtText = getField(tab, "textAreaJwt", JTextArea.class);

        SwingUtilities.invokeAndWait(() -> payload.setText("{\"sub\":\"guest\"}"));

        String editedJwt = jwtText.getText();
        String rebuiltHeaderValue = invokeStringMethod(tab, "rebuildHeaderValue", 0);

        assertNotEquals(ORIGINAL_JWT, editedJwt);
        assertEquals("Bearer " + editedJwt, rebuiltHeaderValue);
        assertSame(updatedRequest, tab.getRequest());
        verify(request).withUpdatedHeader("Authorization", rebuiltHeaderValue);

        HttpHeader updatedAuthHeader = mock(HttpHeader.class);
        when(updatedAuthHeader.name()).thenReturn("Authorization");
        when(updatedAuthHeader.value()).thenReturn(rebuiltHeaderValue);
        HttpRequest requestAfterEdit = mock(HttpRequest.class);
        when(requestAfterEdit.headers()).thenReturn(List.of(updatedAuthHeader));
        HttpRequestResponse responseAfterEdit = mock(HttpRequestResponse.class);
        when(responseAfterEdit.request()).thenReturn(requestAfterEdit);
        assertTrue(tab.isEnabledFor(responseAfterEdit));
    }

    @Test
    @DisplayName("只读模式下 JwtEditorTab 不应标记修改或回写请求")
    void readOnlyMode_shouldNotModifyRequest() throws Exception {
        EditorCreationContext context = mock(EditorCreationContext.class);
        when(context.editorMode()).thenReturn(EditorMode.READ_ONLY);
        HttpHeader authHeader = mockJwtHeader("Authorization", "Bearer " + ORIGINAL_JWT);
        HttpRequest request = mock(HttpRequest.class);
        when(request.headers()).thenReturn(List.of(authHeader));
        HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
        when(requestResponse.request()).thenReturn(request);

        JwtEditorTab tab = new JwtEditorTab(null, context);
        SwingUtilities.invokeAndWait(() -> tab.setRequestResponse(requestResponse));

        JTextArea payload = getField(tab, "textAreaPayload", JTextArea.class);
        SwingUtilities.invokeAndWait(() -> payload.setText("{\"sub\":\"guest\"}"));

        assertFalse(tab.isModified());
        assertSame(request, tab.getRequest());
    }

    @Test
    @DisplayName("isEnabledFor 遇到异常请求头时应安全返回 false")
    void isEnabledFor_headerException_shouldReturnFalse() {
        HttpRequest request = mock(HttpRequest.class);
        when(request.headers()).thenThrow(new RuntimeException("broken request"));
        HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
        when(requestResponse.request()).thenReturn(request);

        assertFalse(new JwtEditorTab().isEnabledFor(requestResponse));
    }

    private static HttpHeader mockJwtHeader(String name, String value) {
        HttpHeader header = mock(HttpHeader.class);
        when(header.name()).thenReturn(name);
        when(header.value()).thenReturn(value);
        return header;
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
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

    private static String invokeStringMethod(Object target, String methodName, int arg) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, int.class);
            method.setAccessible(true);
            return (String) method.invoke(target, arg);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
