package dev.jason.gboardpatches.extension.geminitranslation;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardGeminiTranslationSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String CUSTOM_VALUE = "__custom__";
    private static final String GEMINI_API_KEY_URL = "https://aistudio.google.com/apikey";

    private final Context textContext;

    public GboardGeminiTranslationSettingsFeature(Context context) {
        textContext = context;
    }

    @Override
    public String getEntryTitle() {
        return text(R.string.gboard_patches_gemini_translation_title);
    }

    @Override
    public String getEntrySummary() {
        return text(R.string.gboard_patches_gemini_translation_summary);
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_GEMINI_TRANSLATION);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            SharedPreferences preferences =
                    GboardGeminiTranslationSettings.preferences(host.getContext());
            GboardGeminiTranslationSettings.ensureDefaults(preferences);
            GboardGeminiTranslationSettings.Snapshot settings =
                    GboardGeminiTranslationSettings.read(preferences);
            return buildScreen(host, settings);
        } catch (Throwable failure) {
            logFailure("Failed to render Gemini Translation settings", failure);
            return errorScreen();
        }
    }

    private GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host,
            GboardGeminiTranslationSettings.Snapshot settings) {
        Context context = host.getContext();
        boolean enabled = settings.isEnabled();

        List<GboardPatchesSettingsContract.Row> featureRows = new ArrayList<>();
        featureRows.add(new GboardPatchesSettingsContract.ToggleRow(
                text(R.string.gboard_patches_gemini_translation_enable_title),
                text(R.string.gboard_patches_gemini_translation_enable_summary),
                true,
                enabled,
                value -> safeWrite(() ->
                        GboardGeminiTranslationSettings.writeEnabled(context, value))));

        List<GboardPatchesSettingsContract.Row> apiRows = new ArrayList<>();
        apiRows.add(new GboardPatchesSettingsContract.SelectorRow(
                text(R.string.gboard_patches_gemini_translation_api_key_title),
                text(R.string.gboard_patches_gemini_translation_api_key_summary),
                settings.hasApiKey()
                        ? GboardGeminiTranslationSettings.maskApiKey(settings.getApiKey())
                        : text(R.string.gboard_patches_gemini_translation_api_key_unset),
                enabled,
                () -> showApiKeyDialog(host)));
        apiRows.add(new GboardPatchesSettingsContract.CommandRow(
                text(R.string.gboard_patches_gemini_translation_get_api_key_title),
                text(R.string.gboard_patches_gemini_translation_get_api_key_summary),
                true,
                () -> GboardPatchesSettingsContract.openExternalUrl(host, GEMINI_API_KEY_URL)));
        apiRows.add(new GboardPatchesSettingsContract.SelectorRow(
                text(R.string.gboard_patches_gemini_translation_model_title),
                text(R.string.gboard_patches_gemini_translation_model_summary),
                settings.getModel(),
                enabled,
                () -> showModelDialog(host, settings.getModel())));
        if (settings.hasApiKey()) {
            apiRows.add(new GboardPatchesSettingsContract.CommandRow(
                    text(R.string.gboard_patches_gemini_translation_clear_key_title),
                    text(R.string.gboard_patches_gemini_translation_clear_key_summary),
                    enabled,
                    () -> {
                        safeWrite(() -> GboardGeminiTranslationSettings.writeApiKey(context, ""));
                        GboardPatchesSettingsContract.refresh(host);
                    }));
        }

        List<GboardPatchesSettingsContract.Row> languageRows = new ArrayList<>();
        languageRows.add(new GboardPatchesSettingsContract.SelectorRow(
                text(R.string.gboard_patches_gemini_translation_target_title),
                text(R.string.gboard_patches_gemini_translation_target_summary),
                GboardGeminiTranslationSettings.languageDisplayName(settings.getTargetLanguage()),
                enabled,
                () -> showLanguageDialog(host,
                        text(R.string.gboard_patches_gemini_translation_target_title),
                        settings.getTargetLanguage(), false)));
        languageRows.add(new GboardPatchesSettingsContract.SelectorRow(
                text(R.string.gboard_patches_gemini_translation_alternate_title),
                text(R.string.gboard_patches_gemini_translation_alternate_summary),
                settings.hasAlternateLanguage()
                        ? GboardGeminiTranslationSettings.languageDisplayName(
                                settings.getAlternateLanguage())
                        : text(R.string.gboard_patches_gemini_translation_alternate_none),
                enabled,
                () -> showLanguageDialog(host,
                        text(R.string.gboard_patches_gemini_translation_alternate_title),
                        settings.getAlternateLanguage(), true)));
        languageRows.add(new GboardPatchesSettingsContract.CommandRow(
                text(R.string.gboard_patches_gemini_translation_test_title),
                text(R.string.gboard_patches_gemini_translation_test_summary),
                enabled && settings.hasApiKey(),
                () -> runConnectionTest(context)));

        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(),
                text(R.string.gboard_patches_header_badge),
                getEntryTitle(),
                "",
                Collections.emptyList(),
                List.of(
                        new GboardPatchesSettingsContract.Section(
                                text(R.string.gboard_patches_gemini_translation_section_feature),
                                featureRows),
                        new GboardPatchesSettingsContract.Section(
                                text(R.string.gboard_patches_gemini_translation_section_api),
                                apiRows),
                        new GboardPatchesSettingsContract.Section(
                                text(R.string.gboard_patches_gemini_translation_section_language),
                                languageRows)),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private void showApiKeyDialog(GboardPatchesSettingsContract.FeatureHost host) {
        try {
            GboardPatchesSettingsContract.showTextInputDialog(
                    host,
                    text(R.string.gboard_patches_gemini_translation_api_key_title),
                    "AIza…",
                    "",
                    value -> {
                        String sanitized = GboardGeminiTranslationSettings.sanitizeApiKey(value);
                        if (sanitized.isEmpty()) {
                            throw new IllegalArgumentException(text(
                                    R.string.gboard_patches_gemini_translation_api_key_error));
                        }
                        if (!GboardGeminiTranslationSettings.writeApiKey(host.getContext(),
                                sanitized)) {
                            throw new IllegalStateException("Unable to save Gemini API key");
                        }
                    });
        } catch (Throwable failure) {
            logFailure("Unable to show API key editor", failure);
        }
    }

    private void showModelDialog(GboardPatchesSettingsContract.FeatureHost host,
            String currentModel) {
        try {
            String[] known = GboardGeminiTranslationSettings.KNOWN_MODELS;
            List<String> labels = new ArrayList<>(Arrays.asList(known));
            List<String> values = new ArrayList<>(Arrays.asList(known));
            labels.add(text(R.string.gboard_patches_gemini_translation_model_custom));
            values.add(CUSTOM_VALUE);
            String selected = values.contains(currentModel) ? currentModel : CUSTOM_VALUE;
            GboardPatchesSettingsContract.showChoiceDialog(
                    host,
                    text(R.string.gboard_patches_gemini_translation_model_title),
                    labels.toArray(new String[0]),
                    values.toArray(new String[0]),
                    selected,
                    CUSTOM_VALUE,
                    () -> showCustomModelDialog(host, currentModel),
                    value -> safeWrite(() ->
                            GboardGeminiTranslationSettings.writeModel(host.getContext(), value)));
        } catch (Throwable failure) {
            logFailure("Unable to show model choices", failure);
        }
    }

    private void showCustomModelDialog(GboardPatchesSettingsContract.FeatureHost host,
            String currentModel) {
        try {
            GboardPatchesSettingsContract.showTextInputDialog(
                    host,
                    text(R.string.gboard_patches_gemini_translation_model_custom),
                    GboardGeminiTranslationSettings.DEFAULT_MODEL,
                    currentModel,
                    value -> {
                        if (GboardGeminiTranslationSettings.sanitizeModel(value).isEmpty()) {
                            throw new IllegalArgumentException(text(
                                    R.string.gboard_patches_gemini_translation_model_error));
                        }
                        if (!GboardGeminiTranslationSettings.writeModel(host.getContext(),
                                value)) {
                            throw new IllegalStateException("Unable to save Gemini model");
                        }
                    });
        } catch (Throwable failure) {
            logFailure("Unable to show custom model editor", failure);
        }
    }

    private void showLanguageDialog(GboardPatchesSettingsContract.FeatureHost host,
            String title, String currentLanguage, boolean allowNone) {
        try {
            List<String> labels = new ArrayList<>();
            List<String> values = new ArrayList<>();
            if (allowNone) {
                labels.add(text(R.string.gboard_patches_gemini_translation_alternate_none));
                values.add("");
            }
            labels.addAll(Arrays.asList(GboardGeminiTranslationSettings.knownLanguageNames()));
            values.addAll(Arrays.asList(GboardGeminiTranslationSettings.knownLanguageCodes()));
            labels.add(text(R.string.gboard_patches_gemini_translation_language_custom));
            values.add(CUSTOM_VALUE);
            String selected = values.contains(currentLanguage) ? currentLanguage : CUSTOM_VALUE;
            GboardPatchesSettingsContract.showChoiceDialog(
                    host,
                    title,
                    labels.toArray(new String[0]),
                    values.toArray(new String[0]),
                    selected,
                    CUSTOM_VALUE,
                    () -> showCustomLanguageDialog(host, title, currentLanguage, allowNone),
                    value -> safeWrite(() -> allowNone
                            ? GboardGeminiTranslationSettings.writeAlternateLanguage(
                                    host.getContext(), value)
                            : GboardGeminiTranslationSettings.writeTargetLanguage(
                                    host.getContext(), value)));
        } catch (Throwable failure) {
            logFailure("Unable to show language choices", failure);
        }
    }

    private void showCustomLanguageDialog(GboardPatchesSettingsContract.FeatureHost host,
            String title, String currentLanguage, boolean allowNone) {
        try {
            GboardPatchesSettingsContract.showTextInputDialog(
                    host,
                    title,
                    "Khmer",
                    currentLanguage,
                    value -> {
                        boolean saved = allowNone
                                ? GboardGeminiTranslationSettings.writeAlternateLanguage(
                                        host.getContext(), value)
                                : GboardGeminiTranslationSettings.writeTargetLanguage(
                                        host.getContext(), value);
                        if (!saved) {
                            throw new IllegalArgumentException(text(
                                    R.string.gboard_patches_gemini_translation_language_error));
                        }
                    });
        } catch (Throwable failure) {
            logFailure("Unable to show custom language editor", failure);
        }
    }

    private void runConnectionTest(Context context) {
        try {
            GboardGeminiTranslationSettings.Snapshot settings =
                    GboardGeminiTranslationSettings.read(context);
            Context application = context.getApplicationContext();
            Context safeContext = application != null ? application : context;
            toast(safeContext, text(R.string.gboard_patches_gemini_translation_test_running));
            Executors.newSingleThreadExecutor().execute(() -> {
                GboardGeminiTranslationClient.Result result =
                        GboardGeminiTranslationClient.translate(
                                new GboardGeminiTranslationClient.Request(settings.getApiKey(),
                                        settings.getModel(), "Hello, world!",
                                        settings.getTargetLanguage(),
                                        settings.getAlternateLanguage()));
                new Handler(Looper.getMainLooper()).post(() -> toast(safeContext,
                        result.isSuccess()
                                ? text(R.string.gboard_patches_gemini_translation_test_success)
                                        + " " + result.getText()
                                : text(R.string.gboard_patches_gemini_translation_test_failure)
                                        + " " + result.getErrorMessage()));
            });
        } catch (Throwable failure) {
            logFailure("Gemini connection test failed", failure);
        }
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(),
                text(R.string.gboard_patches_header_badge),
                getEntryTitle(),
                "",
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_gemini_translation_error_title),
                        text(R.string.gboard_patches_gemini_translation_error_summary),
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private String text(int resourceId) {
        return GboardSettingsText.get(textContext, resourceId);
    }

    private static void toast(Context context, String message) {
        try {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            // Feedback is best effort.
        }
    }

    private static void safeWrite(BooleanOperation operation) {
        try {
            operation.run();
        } catch (Throwable failure) {
            logFailure("Gemini Translation setting callback failed", failure);
        }
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            Log.w(TAG, message, failure);
        } catch (Throwable ignored) {
            // Settings diagnostics cannot affect the host activity.
        }
    }

    private interface BooleanOperation {
        boolean run();
    }
}
