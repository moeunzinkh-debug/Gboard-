package dev.jason.gboardpatches.extension.geminitranslation;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small in-process translation box shown when the Gemini Translate toolbar icon is tapped.
 *
 * <p>Mirrors Google Translate's typing UI: a source field at the top receives the typed text, a
 * Translate button asks Gemini for the result, and the translation is shown below. The result can
 * be inserted back into the original text field or copied to the clipboard.
 */
public final class GboardGeminiTranslationActivity extends Activity {
    static final String EXTRA_INITIAL_TEXT = "gboard_gemini_initial_text";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GboardPatches-GeminiTranslateBox");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);

    private EditText sourceInput;
    private TextView resultView;
    private Button translateButton;
    private String pendingTranslation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        super.onCreate(savedInstanceState);
        try {
            configureWindow();
            setContentView(buildContentView());
            String initial = getIntent() == null
                    ? null
                    : getIntent().getStringExtra(EXTRA_INITIAL_TEXT);
            sourceInput.setText(initial == null ? "" : initial);
            sourceInput.setSelection(sourceInput.getText().length());
            sourceInput.requestFocus();
        } catch (Throwable failure) {
            // The translation box must never crash the Gboard process.
            finish();
        }
    }

    private void configureWindow() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        window.setGravity(Gravity.TOP);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    private View buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView title = new TextView(this);
        title.setText(label("Gemini 翻譯", "Gemini Translate"));
        title.setTextSize(16f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, matchWrap());

        sourceInput = new EditText(this);
        sourceInput.setHint(label("輸入要翻譯的文字…", "Type text to translate…"));
        sourceInput.setMinLines(2);
        sourceInput.setMaxLines(4);
        sourceInput.setGravity(Gravity.TOP);
        root.addView(sourceInput, matchWrap());

        translateButton = new Button(this);
        translateButton.setText(label("翻譯", "Translate"));
        translateButton.setOnClickListener(ignored -> translateCurrent());
        root.addView(translateButton, matchWrap());

        ScrollView resultScroller = new ScrollView(this);
        resultScroller.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        resultView = new TextView(this);
        resultView.setTextSize(15f);
        resultView.setPadding(0, dp(10), 0, dp(10));
        resultView.setText(label("翻譯結果會顯示在這裡", "Translation will appear here"));
        resultScroller.addView(resultView, matchWrap());
        root.addView(resultScroller);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button insertButton = new Button(this);
        insertButton.setText(label("插入", "Insert"));
        insertButton.setOnClickListener(ignored -> insertResult());
        Button copyButton = new Button(this);
        copyButton.setText(label("複製", "Copy"));
        copyButton.setOnClickListener(ignored -> copyResult());
        Button closeButton = new Button(this);
        closeButton.setText(label("關閉", "Close"));
        closeButton.setOnClickListener(ignored -> finish());
        actions.addView(insertButton, weighted());
        actions.addView(copyButton, weighted());
        actions.addView(closeButton, weighted());
        root.addView(actions, matchWrap());

        return root;
    }

    private void translateCurrent() {
        String text = sourceInput.getText() == null ? "" : sourceInput.getText().toString().trim();
        if (text.isEmpty()) {
            toast(label("請先輸入文字", "Enter some text first"));
            return;
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            toast(label("翻譯進行中…", "Translation already in progress…"));
            return;
        }
        GboardGeminiTranslationSettings.Snapshot settings =
                GboardGeminiTranslationSettings.read(this);
        if (!settings.hasApiKey()) {
            IN_FLIGHT.set(false);
            toast(label("請先在 Patches 設定輸入 Gemini API 金鑰",
                    "Set your Gemini API key in Patches settings first"));
            return;
        }
        setBusy(true);
        EXECUTOR.execute(() -> {
            GboardGeminiTranslationClient.Result result;
            try {
                result = GboardGeminiTranslationClient.translate(
                        new GboardGeminiTranslationClient.Request(settings.getApiKey(),
                                settings.getModel(), text, settings.getTargetLanguage(),
                                settings.getAlternateLanguage()));
            } catch (Throwable failure) {
                result = GboardGeminiTranslationClient.Result.failure("Translation failed");
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                IN_FLIGHT.set(false);
                setBusy(false);
                if (result.isSuccess()) {
                    pendingTranslation = result.getText();
                    resultView.setText(result.getText());
                } else {
                    pendingTranslation = null;
                    resultView.setText(label("翻譯失敗：", "Translation failed: ")
                            + result.getErrorMessage());
                }
            });
        });
    }

    private void insertResult() {
        String translation = pendingTranslation;
        if (translation == null || translation.trim().isEmpty()) {
            toast(label("請先翻譯", "Translate first"));
            return;
        }
        GboardGeminiTranslationRuntime.insertTranslation(this, translation);
        finish();
    }

    private void copyResult() {
        String translation = pendingTranslation;
        if (translation == null || translation.trim().isEmpty()) {
            toast(label("請先翻譯", "Translate first"));
            return;
        }
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) {
            manager.setPrimaryClip(ClipData.newPlainText("translation", translation));
        }
        toast(label("已複製到剪貼簿", "Copied to clipboard"));
    }

    private void setBusy(boolean busy) {
        if (translateButton == null) {
            return;
        }
        translateButton.setEnabled(!busy);
        translateButton.setText(busy
                ? label("翻譯中…", "Translating…")
                : label("翻譯", "Translate"));
    }

    private void toast(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
            // User feedback cannot crash the host.
        }
    }

    private String label(String chinese, String english) {
        return "zh".equalsIgnoreCase(Locale.getDefault().getLanguage()) ? chinese : english;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private ViewGroup.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}
