package dev.jason.gboardpatches.extension.geminitranslation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Preference authority for the Gemini Translation feature.
 *
 * <p>The API key is stored in a dedicated preference file so that it never travels with the
 * core Patches settings store and is not included in Backup &amp; Restore exports.
 */
@SuppressLint("ApplySharedPref")
public final class GboardGeminiTranslationSettings {
    public static final String PREF_FILE = "gboard_gemini_translation_settings";
    public static final String PREF_KEY_ENABLED = "pref_gemini_translation_enabled";
    public static final String PREF_KEY_API_KEY = "pref_gemini_translation_api_key";
    public static final String PREF_KEY_MODEL = "pref_gemini_translation_model";
    public static final String PREF_KEY_TARGET_LANGUAGE = "pref_gemini_translation_target_language";
    public static final String PREF_KEY_ALTERNATE_LANGUAGE =
            "pref_gemini_translation_alternate_language";

    public static final String MODEL_GEMINI_3_6_FLASH = "gemini-3.6-flash";
    public static final String MODEL_GEMINI_3_5_FLASH_LITE = "gemini-3.5-flash-lite";
    public static final String MODEL_GEMINI_2_5_FLASH = "gemini-2.5-flash";
    public static final String[] KNOWN_MODELS = {
            MODEL_GEMINI_3_6_FLASH,
            MODEL_GEMINI_3_5_FLASH_LITE,
            MODEL_GEMINI_2_5_FLASH,
    };

    public static final boolean DEFAULT_ENABLED = true;
    public static final String DEFAULT_MODEL = MODEL_GEMINI_3_6_FLASH;
    public static final String DEFAULT_TARGET_LANGUAGE = "en";
    public static final String DEFAULT_ALTERNATE_LANGUAGE = "";

    public static final int MAX_MODEL_LENGTH = 64;
    public static final int MAX_API_KEY_LENGTH = 256;
    public static final int MAX_LANGUAGE_LENGTH = 48;

    /** Language code to English display name, used both for the picker and the prompt. */
    private static final Map<String, String> LANGUAGE_NAMES = new LinkedHashMap<>();

    static {
        LANGUAGE_NAMES.put("en", "English");
        LANGUAGE_NAMES.put("zh-TW", "Traditional Chinese");
        LANGUAGE_NAMES.put("zh-CN", "Simplified Chinese");
        LANGUAGE_NAMES.put("ja", "Japanese");
        LANGUAGE_NAMES.put("ko", "Korean");
        LANGUAGE_NAMES.put("km", "Khmer");
        LANGUAGE_NAMES.put("th", "Thai");
        LANGUAGE_NAMES.put("vi", "Vietnamese");
        LANGUAGE_NAMES.put("id", "Indonesian");
        LANGUAGE_NAMES.put("ms", "Malay");
        LANGUAGE_NAMES.put("hi", "Hindi");
        LANGUAGE_NAMES.put("ar", "Arabic");
        LANGUAGE_NAMES.put("es", "Spanish");
        LANGUAGE_NAMES.put("fr", "French");
        LANGUAGE_NAMES.put("de", "German");
        LANGUAGE_NAMES.put("it", "Italian");
        LANGUAGE_NAMES.put("pt", "Portuguese");
        LANGUAGE_NAMES.put("ru", "Russian");
    }

    private GboardGeminiTranslationSettings() {
    }

    public static SharedPreferences preferences(Context context) {
        Context applicationContext = context == null ? null : context.getApplicationContext();
        Context lookupContext = applicationContext != null ? applicationContext : context;
        return lookupContext == null
                ? null
                : lookupContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(SharedPreferences preferences) {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        boolean dirty = false;
        if (!preferences.contains(PREF_KEY_ENABLED)) {
            editor.putBoolean(PREF_KEY_ENABLED, DEFAULT_ENABLED);
            dirty = true;
        }
        if (!preferences.contains(PREF_KEY_MODEL)) {
            editor.putString(PREF_KEY_MODEL, DEFAULT_MODEL);
            dirty = true;
        }
        if (!preferences.contains(PREF_KEY_TARGET_LANGUAGE)) {
            editor.putString(PREF_KEY_TARGET_LANGUAGE, DEFAULT_TARGET_LANGUAGE);
            dirty = true;
        }
        if (!preferences.contains(PREF_KEY_ALTERNATE_LANGUAGE)) {
            editor.putString(PREF_KEY_ALTERNATE_LANGUAGE, DEFAULT_ALTERNATE_LANGUAGE);
            dirty = true;
        }
        if (dirty) {
            editor.commit();
        }
    }

    public static Snapshot read(SharedPreferences preferences) {
        if (preferences == null) {
            return Snapshot.defaults();
        }
        return new Snapshot(
                readBoolean(preferences, PREF_KEY_ENABLED, DEFAULT_ENABLED),
                sanitizeApiKey(readString(preferences, PREF_KEY_API_KEY, "")),
                sanitizeModel(readString(preferences, PREF_KEY_MODEL, DEFAULT_MODEL)),
                sanitizeLanguage(readString(preferences, PREF_KEY_TARGET_LANGUAGE,
                        DEFAULT_TARGET_LANGUAGE), DEFAULT_TARGET_LANGUAGE),
                sanitizeLanguage(readString(preferences, PREF_KEY_ALTERNATE_LANGUAGE,
                        DEFAULT_ALTERNATE_LANGUAGE), DEFAULT_ALTERNATE_LANGUAGE));
    }

    public static Snapshot read(Context context) {
        return read(preferences(context));
    }

    public static boolean writeEnabled(Context context, boolean enabled) {
        return write(context, editor -> editor.putBoolean(PREF_KEY_ENABLED, enabled));
    }

    public static boolean writeApiKey(Context context, String rawApiKey) {
        String apiKey = sanitizeApiKey(rawApiKey);
        return write(context, editor -> editor.putString(PREF_KEY_API_KEY, apiKey));
    }

    public static boolean writeModel(Context context, String rawModel) {
        String model = sanitizeModel(rawModel);
        if (model.isEmpty()) {
            return false;
        }
        return write(context, editor -> editor.putString(PREF_KEY_MODEL, model));
    }

    public static boolean writeTargetLanguage(Context context, String rawLanguage) {
        String language = sanitizeLanguage(rawLanguage, "");
        if (language.isEmpty()) {
            return false;
        }
        return write(context, editor -> editor.putString(PREF_KEY_TARGET_LANGUAGE, language));
    }

    public static boolean writeAlternateLanguage(Context context, String rawLanguage) {
        String language = sanitizeLanguage(rawLanguage, "");
        return write(context, editor -> editor.putString(PREF_KEY_ALTERNATE_LANGUAGE, language));
    }

    public static String[] knownLanguageCodes() {
        return LANGUAGE_NAMES.keySet().toArray(new String[0]);
    }

    public static String[] knownLanguageNames() {
        return LANGUAGE_NAMES.values().toArray(new String[0]);
    }

    public static boolean isKnownLanguage(String code) {
        return code != null && LANGUAGE_NAMES.containsKey(code);
    }

    /** Human readable language name suitable for both UI display and the Gemini prompt. */
    public static String languageDisplayName(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        String known = LANGUAGE_NAMES.get(code);
        if (known != null) {
            return known;
        }
        try {
            Locale locale = Locale.forLanguageTag(code);
            String display = locale.getDisplayName(Locale.ENGLISH);
            if (display != null && !display.isEmpty() && !display.equals(code)) {
                return display;
            }
        } catch (Throwable ignored) {
            // Fall through to the raw value.
        }
        return code;
    }

    public static String sanitizeApiKey(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_API_KEY_LENGTH) {
            trimmed = trimmed.substring(0, MAX_API_KEY_LENGTH);
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (character > 0x20 && character < 0x7F) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    public static String sanitizeModel(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("models/")) {
            trimmed = trimmed.substring("models/".length());
        }
        if (trimmed.length() > MAX_MODEL_LENGTH) {
            trimmed = trimmed.substring(0, MAX_MODEL_LENGTH);
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '-' || character == '.' || character == '_';
            if (allowed) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    public static String sanitizeLanguage(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim().replace('\n', ' ').replace('\r', ' ');
        if (trimmed.length() > MAX_LANGUAGE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LANGUAGE_LENGTH);
        }
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 4) {
            return "••••";
        }
        return "••••" + apiKey.substring(apiKey.length() - 4);
    }

    private static boolean readBoolean(SharedPreferences preferences, String key,
            boolean fallback) {
        try {
            Object raw = preferences.getAll().get(key);
            if (raw instanceof Boolean value) {
                return value.booleanValue();
            }
            if (raw instanceof String value) {
                if ("true".equalsIgnoreCase(value)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(value)) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
            // Corrupted stores fall back to the default.
        }
        return fallback;
    }

    private static String readString(SharedPreferences preferences, String key,
            String fallback) {
        try {
            Object raw = preferences.getAll().get(key);
            if (raw instanceof String value) {
                return value;
            }
        } catch (Throwable ignored) {
            // Corrupted stores fall back to the default.
        }
        return fallback;
    }

    private static boolean write(Context context, EditorMutation mutation) {
        try {
            SharedPreferences preferences = preferences(context);
            if (preferences == null) {
                return false;
            }
            SharedPreferences.Editor editor = preferences.edit();
            mutation.apply(editor);
            return editor.commit();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private interface EditorMutation {
        void apply(SharedPreferences.Editor editor);
    }

    public static final class Snapshot {
        private final boolean enabled;
        private final String apiKey;
        private final String model;
        private final String targetLanguage;
        private final String alternateLanguage;

        Snapshot(boolean enabled, String apiKey, String model, String targetLanguage,
                String alternateLanguage) {
            this.enabled = enabled;
            this.apiKey = apiKey == null ? "" : apiKey;
            this.model = model == null || model.isEmpty() ? DEFAULT_MODEL : model;
            this.targetLanguage = targetLanguage == null || targetLanguage.isEmpty()
                    ? DEFAULT_TARGET_LANGUAGE : targetLanguage;
            this.alternateLanguage = alternateLanguage == null ? "" : alternateLanguage;
        }

        static Snapshot defaults() {
            return new Snapshot(DEFAULT_ENABLED, "", DEFAULT_MODEL, DEFAULT_TARGET_LANGUAGE,
                    DEFAULT_ALTERNATE_LANGUAGE);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public boolean hasApiKey() {
            return !apiKey.isEmpty();
        }

        public String getModel() {
            return model;
        }

        public String getTargetLanguage() {
            return targetLanguage;
        }

        public String getAlternateLanguage() {
            return alternateLanguage;
        }

        public boolean hasAlternateLanguage() {
            return !alternateLanguage.isEmpty();
        }
    }
}
