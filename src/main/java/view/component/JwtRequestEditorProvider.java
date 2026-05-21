package view.component;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;

/**
 * JWT 请求编辑器提供者
 * 注册到 Burp Suite 后，当请求中包含 JWT 时，
 * 会在请求编辑器中自动添加一个 "JWT" 选项卡。
 */
public class JwtRequestEditorProvider implements HttpRequestEditorProvider {

    private final MontoyaApi api;

    public JwtRequestEditorProvider(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext creationContext) {
        return new JwtEditorTab(api, creationContext);
    }
}
