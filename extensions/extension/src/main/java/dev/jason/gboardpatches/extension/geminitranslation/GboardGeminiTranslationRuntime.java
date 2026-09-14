package dev.jason.gboardpatches.extension.geminitranslation;

import android.content.Context;
import android.content.ContextWrapper;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime entry points for the Gemini Translation Access Point.
 *
 * <p>Tapping the toolbar icon reads the selected text (or the whole field when nothing is
 * selected), asks Gemini for a translation on a background thread, and replaces the source text
 * with the result. Every step fails closed so Gboard's input path is never interrupted.
 */
public final class GboardGeminiTranslationRuntime {
    private static final String TAG = "GboardPatches";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GboardPatches-GeminiTranslate");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static volatile WeakReference<InputMethodService> activeInputMethodService =
            new WeakReference<>(null);

    private GboardGeminiTranslationRuntime() {
    }

    public static boolean isEnabled(Context context) {
        try {
            return GboardGeminiTranslationSettings.read(context).isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Lifecycle delegate injected at the top of Gboard's {@code onStartInputView}. */
    public static void onInputViewStarting(Object inputMethodService, EditorInfo editorInfo) {
        rememberInputMethodService(inputMethodService);
    }

    /** Remembers the live IME so toolbar taps can reach the current {@link InputConnection}. */
    public static void rememberInputMethodService(Object candidate) {
        try {
            InputMethodService service = unwrapInputMethodService(candidate);
            if (service != null) {
                activeInputMethodService = new WeakReference<>(service);
            }
        } catch (Throwable ignored) {
            // Latching the IME is best effort only.
        }
    }

    /** Invoked from the Access Point runnable. */
    public static void translateCurrentInput(Context context) {
        try {
            rememberInputMethodService(context);
            GboardGeminiTranslationSettings.Snapshot settings =
                    GboardGeminiTranslationSettings.read(context);
            if (!settings.isEnabled()) {
                return;
            }
            if (!settings.hasApiKey()) {
                showToast(context, "請先在 Patches 設定輸入 Gemini API 金鑰",
                        "Set your Gemini API key in Patches settings first");
                return;
            }
            InputMethodService service = activeInputMethodService.get();
            InputConnection connection = service == null
                    ? null
                    : service.getCurrentInputConnection();
            if (connection == null) {
                showToast(context, "目前沒有可翻譯的輸入框", "No text field to translate");
                return;
            }
            Selection selection = readSelection(connection);
            if (selection == null || selection.text.trim().isEmpty()) {
                showToast(context, "沒有可翻譯的文字", "Nothing to translate");
                return;
            }
            if (!IN_FLIGHT.compareAndSet(false, true)) {
                showToast(context, "翻譯進行中…", "Translation already in progress…");
                return;
            }
            showToast(context, "Gemini 翻譯中…", "Translating with Gemini…");
            Context application = context.getApplicationContext();
            Context safeContext = application != null ? application : context;
            WeakReference<InputMethodService> serviceReference = new WeakReference<>(service);
            EXECUTOR.execute(() -> runTranslation(safeContext, serviceReference, settings,
                    selection));
        } catch (Throwable failure) {
            IN_FLIGHT.set(false);
            logFailure("Gemini translation launch failed", failure);
            showToast(context, "無法啟動 Gemini 翻譯", "Unable to start Gemini translation");
        }
    }

    private static void runTranslation(Context context,
            WeakReference<InputMethodService> serviceReference,
            GboardGeminiTranslationSettings.Snapshot settings, Selection selection) {
        GboardGeminiTranslationClient.Result result;
        try {
            result = GboardGeminiTranslationClient.translate(
                    new GboardGeminiTranslationClient.Request(settings.getApiKey(),
                            settings.getModel(), selection.text, settings.getTargetLanguage(),
                            settings.getAlternateLanguage()));
        } catch (Throwable failure) {
            logFailure("Gemini translation request failed", failure);
            result = GboardGeminiTranslationClient.Result.failure("Translation failed");
        }
        final GboardGeminiTranslationClient.Result finalResult = result;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (!finalResult.isSuccess()) {
                    showToast(context, "Gemini 翻譯失敗：" + finalResult.getErrorMessage(),
                            "Gemini translation failed: " + finalResult.getErrorMessage());
                    return;
                }
                InputMethodService service = serviceReference.get();
                InputConnection connection = service == null
                        ? null
                        : service.getCurrentInputConnection();
                if (connection == null || !commitReplacement(connection, selection,
                        finalResult.getText())) {
                    showToast(context, "無法寫入翻譯結果", "Unable to insert translation");
                }
            } catch (Throwable failure) {
                logFailure("Gemini translation commit failed", failure);
            } finally {
                IN_FLIGHT.set(false);
            }
        });
    }

    static Selection readSelection(InputConnection connection) {
        try {
            CharSequence selected = connection.getSelectedText(0);
            if (selected != null && selected.length() > 0) {
                return new Selection(selected.toString(), true, -1, -1);
            }
            ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
            if (extracted != null && extracted.text != null && extracted.text.length() > 0) {
                return new Selection(extracted.text.toString(), false,
                        extracted.startOffset, extracted.text.length());
            }
            CharSequence before = connection.getTextBeforeCursor(
                    GboardGeminiTranslationClient.MAX_INPUT_CHARACTERS, 0);
            CharSequence after = connection.getTextAfterCursor(
                    GboardGeminiTranslationClient.MAX_INPUT_CHARACTERS, 0);
            String beforeText = before == null ? "" : before.toString();
            String afterText = after == null ? "" : after.toString();
            if (beforeText.isEmpty() && afterText.isEmpty()) {
                return null;
            }
            return new Selection(beforeText + afterText, false, -2,
                    beforeText.length() + afterText.length());
        } catch (Throwable failure) {
            logFailure("Reading the input selection failed", failure);
            return null;
        }
    }

    static boolean commitReplacement(InputConnection connection, Selection selection,
            String translated) {
        try {
            connection.beginBatchEdit();
            try {
                if (selection.wasSelection) {
                    return connection.commitText(translated, 1);
                }
                if (selection.startOffset >= 0) {
                    connection.setSelection(selection.startOffset,
                            selection.startOffset + selection.length);
                    return connection.commitText(translated, 1);
                }
                connection.deleteSurroundingText(
                        GboardGeminiTranslationClient.MAX_INPUT_CHARACTERS,
                        GboardGeminiTranslationClient.MAX_INPUT_CHARACTERS);
                return connection.commitText(translated, 1);
            } finally {
                connection.endBatchEdit();
            }
        } catch (Throwable failure) {
            logFailure("Committing the translation failed", failure);
            return false;
        }
    }

    static InputMethodService unwrapInputMethodService(Object candidate) {
        Object current = candidate;
        int depth = 0;
        while (current != null && depth < 12) {
            if (current instanceof InputMethodService inputMethodService) {
                return inputMethodService;
            }
            if (!(current instanceof ContextWrapper wrapper)) {
                return null;
            }
            Context baseContext = wrapper.getBaseContext();
            if (baseContext == current) {
                return null;
            }
            current = baseContext;
            depth++;
        }
        return null;
    }

    static void showToast(Context context, String chinese, String english) {
        try {
            if (context == null) {
                return;
            }
            String language = Locale.getDefault().getLanguage();
            String message = "zh".equalsIgnoreCase(language) ? chinese : english;
            Runnable show = () -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            if (Looper.myLooper() == Looper.getMainLooper()) {
                show.run();
            } else {
                new Handler(Looper.getMainLooper()).post(show);
            }
        } catch (Throwable ignored) {
            // User feedback cannot affect the Gboard click path.
        }
    }

    private static void logFailure(String message, Throwable failure) {
        try {
            Log.w(TAG, message, failure);
        } catch (Throwable ignored) {
            // Diagnostics cannot affect the host.
        }
    }

    static final class Selection {
        final String text;
        final boolean wasSelection;
        final int startOffset;
        final int length;

        Selection(String text, boolean wasSelection, int startOffset, int length) {
            this.text = text;
            this.wasSelection = wasSelection;
            this.startOffset = startOffset;
            this.length = length;
        }
    }
}
