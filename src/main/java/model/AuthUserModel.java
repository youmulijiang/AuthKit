package model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 鉴权用户配置数据模型
 * 封装单个鉴权角色的配置信息，包括认证头和参数替换规则。
 * 从 AuthUserConfigPanel 的 UI 控件中提取原始文本，解析为结构化数据。
 */
public class AuthUserModel {

    private String name;
    private boolean enabled;
    private boolean headerReplaceEnabled;
    private boolean paramReplaceEnabled;

    /** 原始认证头文本（多行，格式: HeaderName: HeaderValue） */
    private String rawHeaders;
    /** 原始参数替换文本（多行，格式: paramName=newValue） */
    private String rawParams;

    public AuthUserModel(String name) {
        this.name = name;
        this.enabled = true;
        this.headerReplaceEnabled = true;
        this.paramReplaceEnabled = true;
        this.rawHeaders = "";
        this.rawParams = "";
    }

    /** 获取用户名称 */
    public String getName() {
        return name;
    }

    /** 设置用户名称 */
    public void setName(String name) {
        this.name = name;
    }

    /** 是否启用该鉴权角色 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置启用状态 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 是否启用认证头替换 */
    public boolean isHeaderReplaceEnabled() {
        return headerReplaceEnabled;
    }

    /** 设置认证头替换开关 */
    public void setHeaderReplaceEnabled(boolean headerReplaceEnabled) {
        this.headerReplaceEnabled = headerReplaceEnabled;
    }

    /** 是否启用参数替换 */
    public boolean isParamReplaceEnabled() {
        return paramReplaceEnabled;
    }

    /** 设置参数替换开关 */
    public void setParamReplaceEnabled(boolean paramReplaceEnabled) {
        this.paramReplaceEnabled = paramReplaceEnabled;
    }

    /** 设置原始认证头文本 */
    public void setRawHeaders(String rawHeaders) {
        this.rawHeaders = rawHeaders != null ? rawHeaders : "";
    }

    /** 获取原始认证头文本 */
    public String getRawHeaders() {
        return rawHeaders;
    }

    /** 设置原始参数替换文本 */
    public void setRawParams(String rawParams) {
        this.rawParams = rawParams != null ? rawParams : "";
    }

    /** 获取原始参数替换文本 */
    public String getRawParams() {
        return rawParams;
    }

    /**
     * 解析认证头文本为 Map
     * 格式: HeaderName: HeaderValue（每行一条）
     *
     * @return 认证头名称 -> 值的映射
     */
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rawHeaders == null || rawHeaders.isBlank()) {
            return headers;
        }
        for (String line : rawHeaders.split("\n")) {
            String trimmed = line.trim();
            int colonIndex = trimmed.indexOf(":");
            if (colonIndex <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colonIndex).trim();
            String value = trimmed.substring(colonIndex + 1).trim();
            if (!key.isEmpty()) {
                headers.put(key, value);
            }
        }
        return headers;
    }

    /**
     * 解析参数替换文本为 Map
     * 格式: paramName=newValue（每行一条）
     *
     * @return 参数名 -> 新值的映射
     */
    public Map<String, String> getParams() {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawParams == null || rawParams.isBlank()) {
            return params;
        }
        for (String line : rawParams.split("\n")) {
            String trimmed = line.trim();
            int eqIndex = trimmed.indexOf("=");
            if (eqIndex <= 0) {
                continue;
            }
            String key = trimmed.substring(0, eqIndex).trim();
            String value = trimmed.substring(eqIndex + 1).trim();
            if (!key.isEmpty()) {
                params.put(key, value);
            }
        }
        return params;
    }
}

