package view.component;

import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;

/**
 * JWT 响应分析提供者
 * 注册到 Burp Suite 后，当响应中包含 JWT 时，
 * 会在响应编辑器中自动添加一个 "JWT" 分析选项卡。
 */
public class JwtResponseEditorProvider implements HttpResponseEditorProvider {

    @Override
    public ExtensionProvidedHttpResponseEditor provideHttpResponseEditor(EditorCreationContext creationContext) {
        return new JwtResponseEditorTab();
    }
}
