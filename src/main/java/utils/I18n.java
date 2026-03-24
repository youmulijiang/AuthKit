package utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class I18n {

    public static final String AUTH_OBJECT_ORIGINAL = "Original";
    public static final String AUTH_OBJECT_UNAUTHORIZED = "Unauthorized";
    private static final List<String> RESOURCE_AREAS = List.of(
            "common", "main", "toolbar", "configuration",
            "data_table", "metadata_table", "compare", "message",
            "user", "auth_context_menu", "jwt");
    private static final Set<String> CHINESE_TIME_ZONES = Set.of(
            "asia/shanghai", "asia/chongqing", "asia/harbin", "asia/urumqi",
            "asia/hong_kong", "asia/hongkong", "asia/macau", "asia/taipei");
    private static final I18n INSTANCE = new I18n();

    private final Map<Language, Map<String, Properties>> bundles;
    private final CopyOnWriteArrayList<Runnable> listeners;
    private volatile Language currentLanguage;

    private I18n() {
        this.bundles = new EnumMap<>(Language.class);
        this.listeners = new CopyOnWriteArrayList<>();
        loadBundles();
        this.currentLanguage = detectDefaultLanguage();
    }

    public static I18n getInstance() {
        return INSTANCE;
    }

    public Language getCurrentLanguage() {
        return currentLanguage;
    }

    public void setLanguage(Language language) {
        if (language == null || language == currentLanguage) {
            return;
        }
        currentLanguage = language;
        listeners.forEach(Runnable::run);
    }

    public void addLanguageChangeListener(Runnable listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeLanguageChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public String text(String area, String key) {
        String value = findText(currentLanguage, area, key);
        if (value != null) {
            return value;
        }
        value = findText(Language.ENGLISH, area, key);
        return value != null ? value : key;
    }

    public String format(String area, String key, Object... args) {
        return MessageFormat.format(text(area, key), args);
    }

    public String translateAuthObjectName(String authObjectName) {
        if (Objects.equals(AUTH_OBJECT_ORIGINAL, authObjectName)) {
            return text("common", "auth.original");
        }
        if (Objects.equals(AUTH_OBJECT_UNAUTHORIZED, authObjectName)) {
            return text("common", "auth.unauthorized");
        }
        return authObjectName;
    }

    private void loadBundles() {
        for (Language language : Language.values()) {
            Map<String, Properties> areaMap = new java.util.LinkedHashMap<>();
            for (String area : RESOURCE_AREAS) {
                areaMap.put(area, loadProperties(language, area));
            }
            bundles.put(language, areaMap);
        }
    }

    private Properties loadProperties(Language language, String area) {
        String resourcePath = String.format("i18n/%s/%s.properties", language.getCode(), area);
        try (InputStream input = I18n.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing i18n resource: " + resourcePath);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load i18n resource: " + resourcePath, ex);
        }
    }

    private Language detectDefaultLanguage() {
        String zoneId = ZoneId.systemDefault().getId().toLowerCase(Locale.ROOT);
        if (CHINESE_TIME_ZONES.contains(zoneId)) {
            return Language.CHINESE;
        }
        Locale locale = Locale.getDefault();
        if ("zh".equalsIgnoreCase(locale.getLanguage())) {
            return Language.CHINESE;
        }
        return Language.ENGLISH;
    }

    private String findText(Language language, String area, String key) {
        Map<String, Properties> areaMap = bundles.get(language);
        if (areaMap == null) {
            return null;
        }
        Properties properties = areaMap.get(area);
        return properties != null ? properties.getProperty(key) : null;
    }

    public enum Language {
        ENGLISH("en", "English"),
        CHINESE("zh", "中文");

        private final String code;
        private final String displayName;

        Language(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}