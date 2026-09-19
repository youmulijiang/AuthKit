package model;

import java.util.prefs.Preferences;

/**
 * AI 对话配置数据模型（纯数据 POJO）
 * <p>
 * 保存 LLM 调用所需的 API Key、Base URL、模型名称与请求格式，
 * 并通过 {@link java.util.prefs.Preferences} 提供本地持久化能力。
 */
public class AiConfigModel {

    /** 请求格式：OpenAI 兼容接口（chat/completions） */
    public static final String FORMAT_OPENAI = "OpenAI Compatible";
    /** 请求格式：Anthropic 接口（messages） */
    public static final String FORMAT_ANTHROPIC = "Anthropic";
    /** 请求格式：Ollama 本地接口（api/chat） */
    public static final String FORMAT_OLLAMA = "Ollama";

    public static final String[] REQUEST_FORMATS = {
            FORMAT_OPENAI, FORMAT_ANTHROPIC, FORMAT_OLLAMA
    };

    private static final String PREF_KEY_API_KEY = "ai.apiKey";
    private static final String PREF_KEY_BASE_URL = "ai.baseUrl";
    private static final String PREF_KEY_MODEL = "ai.model";
    private static final String PREF_KEY_FORMAT = "ai.format";
    private static final String PREF_KEY_MAX_PACKETS = "ai.maxPackets";

    private String apiKey;
    private String baseUrl;
    private String model;
    private String requestFormat;
    /** 单次分析附带的最大数据包数量，防止提示词过长 */
    private int maxPackets;

    public AiConfigModel() {
        this.apiKey = "";
        this.baseUrl = "https://api.openai.com/v1";
        this.model = "gpt-4o-mini";
        this.requestFormat = FORMAT_OPENAI;
        this.maxPackets = 3;
    }

    /** 从本地 Preferences 恢复配置 */
    public void load() {
        Preferences prefs = Preferences.userNodeForPackage(AiConfigModel.class);
        this.apiKey = prefs.get(PREF_KEY_API_KEY, this.apiKey);
        this.baseUrl = prefs.get(PREF_KEY_BASE_URL, this.baseUrl);
        this.model = prefs.get(PREF_KEY_MODEL, this.model);
        this.requestFormat = prefs.get(PREF_KEY_FORMAT, this.requestFormat);
        this.maxPackets = prefs.getInt(PREF_KEY_MAX_PACKETS, this.maxPackets);
    }

    /** 将配置持久化到本地 Preferences */
    public void save() {
        Preferences prefs = Preferences.userNodeForPackage(AiConfigModel.class);
        prefs.put(PREF_KEY_API_KEY, apiKey == null ? "" : apiKey);
        prefs.put(PREF_KEY_BASE_URL, baseUrl == null ? "" : baseUrl);
        prefs.put(PREF_KEY_MODEL, model == null ? "" : model);
        prefs.put(PREF_KEY_FORMAT, requestFormat == null ? FORMAT_OPENAI : requestFormat);
        prefs.putInt(PREF_KEY_MAX_PACKETS, maxPackets);
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getRequestFormat() {
        return requestFormat;
    }

    public void setRequestFormat(String requestFormat) {
        this.requestFormat = requestFormat;
    }

    public int getMaxPackets() {
        return maxPackets;
    }

    public void setMaxPackets(int maxPackets) {
        this.maxPackets = maxPackets;
    }
}
