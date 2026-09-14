package dev.jason.gboardpatches.extension.geminitranslation;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
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
 * <p>Tapping the toolbar icon opens a small translation box whose source field receives the typed
 * text; the Translate button asks Gemini on a background thread and the result can be inserted
 * back into the original text field. The legacy in-place translation (translate the selected
 * text and replace it) is kept as a fallback when the box cannot be shown. Every step fails
 * closed so Gboard's input path is never interrupted.
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

    private static final long INSERT_RETRY_INITIAL_DELAY_MS = 250L;
    private static final long INSERT_RETRY_INTERVAL_MS = 200L;
    private static final int INSERT_RETRY_ATTEMPTS = 12;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private static volatile Selection pendingInsertSelection;
    private static volatile String pendingInsertText;
    private static volatile WeakReference<Context> pendingInsertContext = new WeakReference<>(null);

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

    /** Legacy in-place translation, used as a fallback when the box cannot be opened. */
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

    /**
     * Opens the small translation box for the toolbar icon.
     *
     * <p>The box mirrors Google Translate's typing UI: a source field at the top receives the
     * typed text, a Translate button asks Gemini for the result, and the translation can be
     * inserted back into the original text field. The pre-existing in-place translation is kept
     * as a fallback for the rare case the box cannot be shown.
     */
    public static void openTranslationBox(Context context) {
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
            pendingInsertSelection = connection == null ? null : readSelection(connection);
            String initialText = pendingInsertSelection == null
                    ? ""
                    : pendingInsertSelection.text;
            if (launchTranslationBoxActivity(context, initialText)) {
                return;
            }
            translateCurrentInput(context);
        } catch (Throwable failure) {
            logFailure("Opening the Gemini translation box failed", failure);
            translateCurrentInput(context);
        }
    }

    private static boolean launchTranslationBoxActivity(Context context, String initialText) {
        try {
            Intent intent = new Intent(context, GboardGeminiTranslationActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.putExtra(GboardGeminiTranslationActivity.EXTRA_INITIAL_TEXT, initialText);
            context.startActivity(intent);
            return true;
        } catch (Throwable failure) {
            logFailure("Unable to launch the Gemini translation box", failure);
            return false;
        }
    }

    /**
     * Commits the translation back into the original text field.
     *
     * <p>The box Activity hands control here before finishing, so the first attempt is delayed to
     * let the Activity finish and Gboard reconnect to the original field; the write is retried for
     * a short window and falls back to the clipboard when the field cannot be reached anymore.
     */
    public static void insertTranslation(Context context, String translated) {
        try {
            if (context == null || translated == null || translated.trim().isEmpty()) {
                return;
            }
            Selection selection = pendingInsertSelection;
            if (selection == null) {
                copyToClipboard(context, translated);
                showToast(context, "找不到原始輸入框，已複製到剪貼簿",
                        "Original text field not found; copied to clipboard");
                return;
            }
            pendingInsertContext = new WeakReference<>(
                    context.getApplicationContext() != null
                            ? context.getApplicationContext()
                            : context);
            pendingInsertText = translated;
            scheduleInsertionAttempt(0);
        } catch (Throwable failure) {
            logFailure("Scheduling the translation insertion failed", failure);
        }
    }

    private static void scheduleInsertionAttempt(int attempt) {
        long delay = attempt == 0 ? INSERT_RETRY_INITIAL_DELAY_MS : INSERT_RETRY_INTERVAL_MS;
        MAIN_HANDLER.postDelayed(() -> attemptInsertion(attempt), delay);
    }

    private static void attemptInsertion(int attempt) {
        try {
            Selection selection = pendingInsertSelection;
            String text = pendingInsertText;
            if (selection == null || text == null) {
                return;
            }
            InputMethodService service = activeInputMethodService.get();
            InputConnection connection = service == null
                    ? null
                    : service.getCurrentInputConnection();
            if (connection != null && commitReplacement(connection, selection, text)) {
                pendingInsertSelection = null;
                pendingInsertText = null;
                pendingInsertContext = new WeakReference<>(null);
                return;
            }
            if (attempt + 1 >= INSERT_RETRY_ATTEMPTS) {
                pendingInsertSelection = null;
                String fallback = pendingInsertText;
                pendingInsertText = null;
                Context context = pendingInsertContext.get();
                pendingInsertContext = new WeakReference<>(null);
                if (context != null && fallback != null) {
                    copyToClipboard(context, fallback);
                    showToast(context, "無法自動插入，已複製到剪貼簿",
                            "Could not insert; copied to clipboard");
                }
                return;
            }
            scheduleInsertionAttempt(attempt + 1);
        } catch (Throwable failure) {
            logFailure("Translation insertion attempt failed", failure);
            if (attempt + 1 < INSERT_RETRY_ATTEMPTS) {
                scheduleInsertionAttempt(attempt + 1);
            }
        }
    }

    static void copyToClipboard(Context context, String text) {
        try {
            if (context == null || text == null) {
                return;
            }
            ClipboardManager manager =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("translation", text));
            }
        } catch (Throwable ignored) {
            // Clipboard fallback cannot affect the insertion path.
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
