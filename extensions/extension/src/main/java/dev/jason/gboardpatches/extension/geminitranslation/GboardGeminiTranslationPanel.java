package dev.jason.gboardpatches.extension.geminitranslation;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Gemini translation bar, docked inside Gboard's own input window.
 *
 * <p>Tapping the Gemini Translate Access Point no longer leaves the keyboard. The bar is added to
 * the frame that hosts Gboard's input view — the framework's {@code android.R.id.inputArea}
 * {@link FrameLayout} — with a gravity of {@code TOP}, a {@code topMargin} that pulls it back
 * into the extra {@code paddingTop} the bar asks for, and that same padding pushing the keyboard
 * rows down. The result is the Google Translate strip the screenshot shows: a back button, the
 * source language, a swap button, the target language and one rounded field carrying the text to
 * translate, with the keyboard still fully usable below it.
 *
 * <p>Nothing of Gboard's is reparented, so a {@code setInputView} call from Gboard itself merely
 * detaches the bar and the lifecycle delegate drops its state. Every path fails closed and falls
 * back to the invisible in-place translation, so a layout surprise can never break typing.
 */
public final class GboardGeminiTranslationPanel {
    /** Tag used to recognise the bar again on the keyboard's own view tree. */
    static final String PANEL_TAG = "gboard-patches-gemini-translation-panel";

    private static final int STATE_SOURCE = 0;
    private static final int STATE_TRANSLATING = 1;
    private static final int STATE_RESULT = 2;

    /** Poll interval for mirroring the focused field into the bar while it is open. */
    private static final long MIRROR_INTERVAL_MS = 400L;
    private static final int BAR_HEIGHT_FALLBACK_DP = 96;
    private static final int CONTROL_HEIGHT_DP = 40;
    private static final int BAR_MARGIN_DP = 6;
    private static final int BAR_PADDING_DP = 8;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GboardPatches-GeminiTranslateBar");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean IN_FLIGHT = new AtomicBoolean(false);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    /** Khmer language names, because {@code languageDisplayName} only speaks English. */
    private static final Map<String, String> KHMER_LANGUAGE_NAMES = new LinkedHashMap<>();

    static {
        KHMER_LANGUAGE_NAMES.put("en", "អង់គ្លេស");
        KHMER_LANGUAGE_NAMES.put("zh-TW", "ចិនប្រពៃណី");
        KHMER_LANGUAGE_NAMES.put("zh-CN", "ចិនសម្រួល");
        KHMER_LANGUAGE_NAMES.put("ja", "ជប៉ុន");
        KHMER_LANGUAGE_NAMES.put("ko", "កូរ៉េ");
        KHMER_LANGUAGE_NAMES.put("km", "ភាសាខ្មែរ");
        KHMER_LANGUAGE_NAMES.put("th", "ថៃ");
        KHMER_LANGUAGE_NAMES.put("vi", "វៀតណាម");
        KHMER_LANGUAGE_NAMES.put("id", "ឥណឌូណេស៊ី");
        KHMER_LANGUAGE_NAMES.put("ms", "ម៉ាឡេ");
        KHMER_LANGUAGE_NAMES.put("hi", "ហិនឌី");
        KHMER_LANGUAGE_NAMES.put("ar", "អារ៉ាប់");
        KHMER_LANGUAGE_NAMES.put("es", "អេស្ប៉ាញ");
        KHMER_LANGUAGE_NAMES.put("fr", "បារាំង");
        KHMER_LANGUAGE_NAMES.put("de", "អាល្លឺម៉ង់");
        KHMER_LANGUAGE_NAMES.put("it", "អ៊ីតាលី");
        KHMER_LANGUAGE_NAMES.put("pt", "ព័រទុយហ្គាល់");
        KHMER_LANGUAGE_NAMES.put("ru", "រុស្ស៊ី");
    }

    private static volatile GboardGeminiTranslationPanel active;

    private final InputMethodService service;
    private final FrameLayout host;
    private final Palette palette;
    private final LinearLayout bar;
    private final TextView sourceChip;
    private final TextView targetChip;
    private final TextView pillText;
    private final TextView copyButton;
    private final TextView actionButton;
    private final LinearLayout languagePickerRow;
    private final HorizontalScrollView languagePicker;

    private final int savedPaddingLeft;
    private final int savedPaddingTop;
    private final int savedPaddingRight;
    private final int savedPaddingBottom;

    private final Runnable mirrorTick = this::mirrorEditorText;
    private final View.OnLayoutChangeListener heightWatcher =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    syncBarInset(bottom - top);

    private GboardGeminiTranslationRuntime.Selection selection;
    private String sourceText = "";
    private String translatedText;
    private String statusMessage;
    private int state = STATE_SOURCE;
    private boolean showingTranslation;
    private boolean pickerForTarget;
    private int barInset;
    private String editorPackage;

    private GboardGeminiTranslationPanel(InputMethodService service, FrameLayout host) {
        this.service = service;
        this.host = host;
        this.palette = Palette.from(host.getContext());
        this.savedPaddingLeft = host.getPaddingLeft();
        this.savedPaddingTop = host.getPaddingTop();
        this.savedPaddingRight = host.getPaddingRight();
        this.savedPaddingBottom = host.getPaddingBottom();

        Context context = host.getContext();
        this.bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setTag(PANEL_TAG);
        bar.setPadding(dp(BAR_PADDING_DP), dp(BAR_PADDING_DP), dp(BAR_PADDING_DP),
                dp(BAR_PADDING_DP));
        bar.setBackground(rounded(palette.surface, dp(18), Color.TRANSPARENT));

        LinearLayout languages = new LinearLayout(context);
        languages.setOrientation(LinearLayout.HORIZONTAL);
        languages.setGravity(Gravity.CENTER_VERTICAL);

        TextView close = control("←", label("បិទរបារបកប្រែ", "關閉翻譯列",
                "Close the translation bar"));
        close.setOnClickListener(view -> detach());
        languages.addView(close, controlParams());

        sourceChip = chip(label("ភាសាដើម", "來源語言", "Source language"));
        sourceChip.setOnClickListener(view -> openLanguagePicker(false));
        languages.addView(sourceChip, chipParams(dp(BAR_MARGIN_DP)));

        TextView swap = control("⇄", label("ប្ដូរភាសា", "交換語言", "Swap languages"));
        swap.setOnClickListener(view -> swapLanguages());
        languages.addView(swap, controlParams());

        targetChip = chip(label("ភាសាគោលដៅ", "目標語言", "Target language"));
        targetChip.setOnClickListener(view -> openLanguagePicker(true));
        languages.addView(targetChip, chipParams(dp(BAR_MARGIN_DP)));
        bar.addView(languages, rowParams());

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);

        pillText = new TextView(context);
        pillText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        pillText.setTextColor(palette.primaryText);
        pillText.setMaxLines(2);
        pillText.setEllipsize(TextUtils.TruncateAt.END);
        pillText.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        pillText.setPadding(dp(14), dp(6), dp(14), dp(6));
        pillText.setMinHeight(dp(CONTROL_HEIGHT_DP));
        pillText.setBackground(rounded(palette.field, dp(20), palette.accent));
        pillText.setOnClickListener(view -> onPillTapped());
        pillText.setOnLongClickListener(view -> {
            copyVisibleText();
            return true;
        });
        content.addView(pillText, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        copyButton = control(label("ចម្លង", "複製", "Copy"),
                label("ចម្លងលទ្ធផលបកប្រែ", "複製翻譯結果", "Copy the translation"));
        copyButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        copyButton.setVisibility(View.GONE);
        copyButton.setOnClickListener(view -> copyTranslation());
        LinearLayout.LayoutParams copyParams = controlParams();
        copyParams.leftMargin = dp(BAR_MARGIN_DP);
        content.addView(copyButton, copyParams);

        actionButton = control("↻", label("បកប្រែ", "翻譯", "Translate"));
        actionButton.setOnClickListener(view -> onActionTapped());
        LinearLayout.LayoutParams actionParams = controlParams();
        actionParams.leftMargin = dp(BAR_MARGIN_DP);
        content.addView(actionButton, actionParams);
        bar.addView(content, rowParams());

        languagePickerRow = new LinearLayout(context);
        languagePickerRow.setOrientation(LinearLayout.HORIZONTAL);
        languagePickerRow.setGravity(Gravity.CENTER_VERTICAL);
        languagePicker = new HorizontalScrollView(context);
        languagePicker.setFillViewport(true);
        languagePicker.setHorizontalScrollBarEnabled(false);
        languagePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        languagePicker.addView(languagePickerRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        languagePicker.setVisibility(View.GONE);
        LinearLayout.LayoutParams pickerParams = rowParams();
        pickerParams.topMargin = dp(BAR_MARGIN_DP);
        bar.addView(languagePicker, pickerParams);
    }

    // ---------------------------------------------------------------------------------------
    // Entry points used by the toolbar action and by the input view lifecycle delegate.
    // ---------------------------------------------------------------------------------------

    /** Opens the bar, or closes it again when it is already on screen. */
    public static boolean toggle(Context context) {
        try {
            GboardGeminiTranslationPanel current = active;
            if (current != null) {
                current.detach();
                return true;
            }
            return open(context);
        } catch (Throwable failure) {
            hide();
            return false;
        }
    }

    /** Removes the bar from the keyboard, if it is showing. */
    public static void hide() {
        GboardGeminiTranslationPanel current = active;
        if (current != null) {
            current.detach();
        }
    }

    public static boolean isVisible() {
        GboardGeminiTranslationPanel current = active;
        return current != null && current.bar.getParent() == current.host;
    }

    /**
     * Lifecycle hook fed by the {@code onStartInputView} delegate. It re-reads the chips for the
     * current session and drops the bar when Gboard rebuilt its input view or when focus moved to
     * another app.
     */
    public static void onInputViewStarted(Object inputMethodService, View inputView,
            EditorInfo editorInfo) {
        try {
            GboardGeminiTranslationPanel current = active;
            if (current == null) {
                return;
            }
            InputMethodService service =
                    GboardGeminiTranslationRuntime.unwrapInputMethodService(inputMethodService);
            if (service == null || service != current.service || inputView == null
                    || inputView.getParent() != current.host
                    || current.bar.getParent() != current.host
                    || !current.host.isAttachedToWindow()) {
                current.detach();
                return;
            }
            String packageName = editorInfo == null ? null : editorInfo.packageName;
            if (packageName != null && current.editorPackage != null
                    && !packageName.equals(current.editorPackage)) {
                current.detach();
                return;
            }
            current.render();
            current.refreshFromEditor();
            current.scheduleMirror();
        } catch (Throwable ignored) {
            // The bar is an extra; the keyboard must keep working regardless.
        }
    }

    private static boolean open(Context context) {
        if (context == null) {
            return false;
        }
        GboardGeminiTranslationSettings.Snapshot settings =
                GboardGeminiTranslationSettings.read(context);
        if (!settings.isEnabled() || !settings.hasApiKey()) {
            return false;
        }
        InputMethodService service = GboardGeminiTranslationRuntime.activeInputMethodService();
        FrameLayout host = resolveHost(service);
        if (service == null || host == null) {
            return false;
        }
        GboardGeminiTranslationPanel panel = new GboardGeminiTranslationPanel(service, host);
        if (!panel.attach()) {
            return false;
        }
        active = panel;
        return true;
    }

    /**
     * The frame that holds Gboard's input view. Gboard never touches it, so its padding and an
     * extra child are ours to use, and a rebuilt keyboard detaches the bar by itself.
     */
    private static FrameLayout resolveHost(InputMethodService service) {
        try {
            if (service == null) {
                return null;
            }
            View inputView = GboardGeminiTranslationRuntime.activeInputView();
            if (inputView == null) {
                return null;
            }
            return inputView.getParent() instanceof FrameLayout host ? host : null;
        } catch (Throwable failure) {
            return null;
        }
    }

    private boolean attach() {
        int height = measureBarHeight();
        if (height <= 0) {
            return false;
        }
        barInset = height;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        // The bar lives inside the extra top padding it asked the host for, and the keyboard rows
        // start right below it.
        params.topMargin = -height;
        host.setPadding(savedPaddingLeft, savedPaddingTop + height, savedPaddingRight,
                savedPaddingBottom);
        host.addView(bar, params);
        bar.addOnLayoutChangeListener(heightWatcher);
        EditorInfo editorInfo = safeCurrentEditorInfo();
        editorPackage = editorInfo == null ? null : editorInfo.packageName;
        render();
        refreshFromEditor();
        scheduleMirror();
        host.requestLayout();
        return true;
    }

    private void detach() {
        GboardGeminiTranslationPanel current = active;
        try {
            MAIN_HANDLER.removeCallbacks(mirrorTick);
            bar.removeOnLayoutChangeListener(heightWatcher);
            if (bar.getParent() == host) {
                host.removeView(bar);
            }
            host.setPadding(savedPaddingLeft, savedPaddingTop, savedPaddingRight,
                    savedPaddingBottom);
            host.requestLayout();
        } catch (Throwable ignored) {
            // Cleaning up after the bar must never reach Gboard.
        } finally {
            if (current == this) {
                active = null;
            }
        }
    }

    private EditorInfo safeCurrentEditorInfo() {
        try {
            return service.getCurrentInputEditorInfo();
        } catch (Throwable failure) {
            return null;
        }
    }

    private int measureBarHeight() {
        try {
            int width = host.getWidth() > 0
                    ? host.getWidth()
                    : host.getResources().getDisplayMetrics().widthPixels;
            bar.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int measured = bar.getMeasuredHeight();
            return measured > 0 ? measured : dp(BAR_HEIGHT_FALLBACK_DP);
        } catch (Throwable failure) {
            return dp(BAR_HEIGHT_FALLBACK_DP);
        }
    }

    /** Keeps the keyboard pushed down by exactly the height the bar needs. */
    private void syncBarInset(int height) {
        if (height <= 0 || height == barInset || bar.getParent() != host) {
            return;
        }
        barInset = height;
        if (bar.getLayoutParams() instanceof FrameLayout.LayoutParams params) {
            params.topMargin = -height;
            params.gravity = Gravity.TOP;
            bar.setLayoutParams(params);
        }
        host.setPadding(savedPaddingLeft, savedPaddingTop + height, savedPaddingRight,
                savedPaddingBottom);
        host.requestLayout();
    }

    // ---------------------------------------------------------------------------------------
    // Rendering.
    // ---------------------------------------------------------------------------------------

    private void render() {
        try {
            GboardGeminiTranslationSettings.Snapshot settings =
                    GboardGeminiTranslationSettings.read(host.getContext());
            sourceChip.setText(languageLabel(settings.getAlternateLanguage()));
            targetChip.setText(languageLabel(settings.getTargetLanguage()));
            pillText.setText(pillContent());
            switch (state) {
                case STATE_TRANSLATING:
                    actionButton.setText("…");
                    actionButton.setEnabled(false);
                    actionButton.setContentDescription(label("កំពុងបកប្រែ", "翻譯中",
                            "Translating"));
                    copyButton.setVisibility(View.GONE);
                    break;
                case STATE_RESULT:
                    actionButton.setText("✓");
                    actionButton.setEnabled(true);
                    actionButton.setContentDescription(label("បញ្ចូលការបកប្រែ", "插入翻譯結果",
                            "Insert the translation"));
                    copyButton.setVisibility(View.VISIBLE);
                    break;
                default:
                    actionButton.setText("↻");
                    actionButton.setEnabled(true);
                    actionButton.setContentDescription(label("បកប្រែ", "翻譯", "Translate"));
                    copyButton.setVisibility(View.GONE);
                    break;
            }
        } catch (Throwable ignored) {
            // A rendering failure simply leaves the previous frame on screen.
        }
    }

    private String pillContent() {
        if (state == STATE_TRANSLATING) {
            return label("កំពុងបកប្រែ…", "翻譯中…", "Translating…");
        }
        if (state == STATE_RESULT && showingTranslation) {
            return translatedText == null || translatedText.isEmpty()
                    ? label("លទ្ធផលបកប្រែនឹងបង្ហាញនៅទីនេះ", "翻譯結果會顯示在這裡",
                            "Translation will appear here")
                    : translatedText;
        }
        if (statusMessage != null && !statusMessage.isEmpty()) {
            return statusMessage;
        }
        return sourceText.isEmpty()
                ? label("រាយបញ្ចូលនៅទីនេះ ដើម្បីបកប្រែ", "輸入要翻譯的文字…",
                        "Type text to translate…")
                : sourceText;
    }

    private String languageLabel(String code) {
        if (code == null || code.isEmpty()) {
            return label("ស្វ័យបរវត្តិ", "自動偵測", "Auto detect");
        }
        if (isKhmerLocale()) {
            String khmer = KHMER_LANGUAGE_NAMES.get(code);
            if (khmer != null) {
                return khmer;
            }
        }
        return GboardGeminiTranslationSettings.languageDisplayName(code);
    }

    // ---------------------------------------------------------------------------------------
    // Interaction.
    // ---------------------------------------------------------------------------------------

    private void onPillTapped() {
        if (state == STATE_SOURCE) {
            translate();
            return;
        }
        if (state == STATE_RESULT) {
            showingTranslation = !showingTranslation;
            render();
        }
    }

    private void onActionTapped() {
        if (state == STATE_RESULT) {
            insertTranslation();
            return;
        }
        translate();
    }

    private void swapLanguages() {
        try {
            Context context = host.getContext();
            GboardGeminiTranslationSettings.Snapshot settings =
                    GboardGeminiTranslationSettings.read(context);
            String target = settings.getTargetLanguage();
            String alternate = settings.getAlternateLanguage();
            if (alternate.isEmpty()) {
                GboardGeminiTranslationRuntime.showToast(context,
                        "សូមជ្រើសរើសភាសាដើមជាមុនសិន", "請先選擇來源語言",
                        "Pick a source language first");
                return;
            }
            GboardGeminiTranslationSettings.writeTargetLanguage(context, alternate);
            GboardGeminiTranslationSettings.writeAlternateLanguage(context, target);
            if (state == STATE_RESULT && translatedText != null) {
                String previousSource = sourceText;
                sourceText = translatedText;
                translatedText = previousSource;
                showingTranslation = false;
            }
            render();
        } catch (Throwable ignored) {
            // Swapping is a convenience; the stored pair stays untouched on failure.
        }
    }

    private void openLanguagePicker(boolean forTarget) {
        if (languagePicker.getVisibility() == View.VISIBLE && pickerForTarget == forTarget) {
            closeLanguagePicker();
            return;
        }
        pickerForTarget = forTarget;
        languagePickerRow.removeAllViews();
        if (!forTarget) {
            addLanguageChip("", label("ស្វ័យបរវត្តិ", "自動偵測", "Auto detect"));
        }
        for (String code : GboardGeminiTranslationSettings.knownLanguageCodes()) {
            addLanguageChip(code, languageLabel(code));
        }
        languagePicker.setVisibility(View.VISIBLE);
    }

    private void addLanguageChip(final String code, String name) {
        TextView chip = chip(name);
        chip.setOnClickListener(view -> {
            try {
                Context context = host.getContext();
                if (pickerForTarget) {
                    GboardGeminiTranslationSettings.writeTargetLanguage(context, code);
                } else {
                    GboardGeminiTranslationSettings.writeAlternateLanguage(context, code);
                }
            } catch (Throwable ignored) {
                // The re-render below just shows whatever is still stored.
            }
            closeLanguagePicker();
            render();
        });
        languagePickerRow.addView(chip, chipParams(dp(4)));
    }

    private void closeLanguagePicker() {
        languagePicker.setVisibility(View.GONE);
        languagePickerRow.removeAllViews();
    }

    // ---------------------------------------------------------------------------------------
    // Editor mirroring, translation and insertion.
    // ---------------------------------------------------------------------------------------

    private void scheduleMirror() {
        MAIN_HANDLER.removeCallbacks(mirrorTick);
        MAIN_HANDLER.postDelayed(mirrorTick, MIRROR_INTERVAL_MS);
    }

    private void mirrorEditorText() {
        if (bar.getParent() != host) {
            return;
        }
        try {
            refreshFromEditor();
        } finally {
            scheduleMirror();
        }
    }

    /**
     * Picks up whatever the focused field holds right now, so typing on the keyboard feeds the bar
     * without leaving it.
     */
    private void refreshFromEditor() {
        try {
            InputConnection connection = service.getCurrentInputConnection();
            if (connection == null) {
                return;
            }
            GboardGeminiTranslationRuntime.Selection read =
                    GboardGeminiTranslationRuntime.readSelection(connection);
            if (read == null || read.text == null) {
                return;
            }
            if (read.wasSelection) {
                // A real selection is a deliberate range, so it wins over the live field text.
                if (selection != null && read.text.equals(selection.text)) {
                    return;
                }
                selection = read;
                sourceText = read.text;
                state = STATE_SOURCE;
                showingTranslation = false;
                statusMessage = null;
                render();
                return;
            }
            selection = read;
            if (state == STATE_SOURCE && !read.text.equals(sourceText)) {
                sourceText = read.text;
                statusMessage = null;
                render();
            }
        } catch (Throwable ignored) {
            // The bar keeps the last text it saw.
        }
    }

    private void translate() {
        final String text = sourceText == null ? "" : sourceText.trim();
        if (text.isEmpty()) {
            GboardGeminiTranslationRuntime.showToast(host.getContext(),
                    "សូមរាយបញ្ចូលអត្ថបទជាមុនសិន", "請先輸入文字", "Enter some text first");
            return;
        }
        if (!IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        final GboardGeminiTranslationSettings.Snapshot settings;
        try {
            settings = GboardGeminiTranslationSettings.read(host.getContext());
        } catch (Throwable failure) {
            IN_FLIGHT.set(false);
            return;
        }
        if (!settings.hasApiKey()) {
            IN_FLIGHT.set(false);
            GboardGeminiTranslationRuntime.showToast(host.getContext(),
                    "សូមដាក់ Gemini API key ក្នុងការកំណត់ Patches ជាមុនសិន",
                    "請先在 Patches 設定輸入 Gemini API 金鑰",
                    "Set your Gemini API key in Patches settings first");
            return;
        }
        statusMessage = null;
        state = STATE_TRANSLATING;
        render();
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
            final GboardGeminiTranslationClient.Result finalResult = result;
            MAIN_HANDLER.post(() -> respond(finalResult));
        });
    }

    private void respond(GboardGeminiTranslationClient.Result result) {
        IN_FLIGHT.set(false);
        if (bar.getParent() != host) {
            return;
        }
        if (result.isSuccess()) {
            translatedText = result.getText();
            showingTranslation = true;
            statusMessage = null;
            state = STATE_RESULT;
        } else {
            translatedText = null;
            showingTranslation = false;
            statusMessage = label("ការបកប្រែបានបរាជ័យ៖ ", "翻譯失敗：", "Translation failed: ")
                    + result.getErrorMessage();
            state = STATE_SOURCE;
        }
        render();
    }

    private void insertTranslation() {
        String translation = translatedText;
        if (translation == null || translation.trim().isEmpty()) {
            return;
        }
        Context context = host.getContext();
        boolean written = false;
        try {
            InputConnection connection = service.getCurrentInputConnection();
            if (connection == null) {
                GboardGeminiTranslationRuntime.showToast(context, "រកមិនឃើញប្រអប់បញ្ចូលអត្ថបទ",
                        "找不到輸入框", "No text field to write into");
                return;
            }
            GboardGeminiTranslationRuntime.Selection fresh =
                    GboardGeminiTranslationRuntime.readSelection(connection);
            GboardGeminiTranslationRuntime.Selection target = fresh == null ? selection : fresh;
            written = target != null
                    && GboardGeminiTranslationRuntime.commitReplacement(connection, target,
                            translation);
        } catch (Throwable failure) {
            written = false;
        }
        if (written) {
            GboardGeminiTranslationRuntime.showToast(context, "បានបញ្ចូលលទ្ធលបកប្រែ",
                    "已插入翻譯", "Translation inserted");
            detach();
            return;
        }
        // The field could not be written to: keep the bar open and hand the text to the clipboard
        // instead, so the translation is never lost.
        GboardGeminiTranslationRuntime.copyToClipboard(context, translation);
        GboardGeminiTranslationRuntime.showToast(context,
                "មិនអាចបញ្ចូលបានទេ បានចម្លងទៅក្ដារតម្បៀតខ្ទាត់វិញ", "無法插入，已複製到剪貼簿",
                "Could not insert; copied to clipboard");
    }

    private void copyTranslation() {
        String translation = translatedText == null ? sourceText : translatedText;
        GboardGeminiTranslationRuntime.copyToClipboard(host.getContext(), translation);
        GboardGeminiTranslationRuntime.showToast(host.getContext(), "បានចម្លងលទ្ធផលបកប្រែ",
                "已複製翻譯", "Translation copied");
    }

    private void copyVisibleText() {
        String text = state == STATE_RESULT && showingTranslation && translatedText != null
                ? translatedText
                : sourceText;
        GboardGeminiTranslationRuntime.copyToClipboard(host.getContext(), text);
        GboardGeminiTranslationRuntime.showToast(host.getContext(), "បានចម្លងអត្ថបទ", "複製成功",
                "Copied");
    }

    // ---------------------------------------------------------------------------------------
    // View helpers.
    // ---------------------------------------------------------------------------------------

    private TextView chip(String text) {
        TextView chip = new TextView(host.getContext());
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        chip.setTextColor(palette.primaryText);
        chip.setSingleLine(true);
        chip.setMaxEms(9);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        chip.setBackground(rounded(palette.field, dp(20), Color.TRANSPARENT));
        chip.setClickable(true);
        return chip;
    }

    private TextView control(String text, String description) {
        TextView control = new TextView(host.getContext());
        control.setText(text);
        control.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        control.setTextColor(palette.primaryText);
        control.setGravity(Gravity.CENTER);
        control.setContentDescription(description);
        control.setBackground(rounded(palette.field, dp(20), Color.TRANSPARENT));
        control.setClickable(true);
        return control;
    }

    private LinearLayout.LayoutParams controlParams() {
        return new LinearLayout.LayoutParams(dp(CONTROL_HEIGHT_DP), dp(CONTROL_HEIGHT_DP));
    }

    private LinearLayout.LayoutParams chipParams(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(CONTROL_HEIGHT_DP));
        params.leftMargin = margin;
        params.rightMargin = margin;
        return params;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private GradientDrawable rounded(int color, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) {
            background.setStroke(dp(1), stroke);
        }
        return background;
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * host.getResources().getDisplayMetrics().density));
    }

    private static boolean isKhmerLocale() {
        return "km".equalsIgnoreCase(Locale.getDefault().getLanguage());
    }

    /** Khmer, Chinese and English labels; the keyboard UI language decides. */
    private static String label(String khmer, String chinese, String english) {
        String language = Locale.getDefault().getLanguage();
        if ("km".equalsIgnoreCase(language)) {
            return khmer;
        }
        return "zh".equalsIgnoreCase(language) ? chinese : english;
    }

    private static final class Palette {
        private final int surface;
        private final int field;
        private final int primaryText;
        private final int accent;

        private Palette(int surface, int field, int primaryText, int accent) {
            this.surface = surface;
            this.field = field;
            this.primaryText = primaryText;
            this.accent = accent;
        }

        static Palette from(Context context) {
            boolean dark = context != null
                    && (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return dark
                    ? new Palette(0xff202124, 0xff3c4043, Color.WHITE, 0xff8ab4f8)
                    : new Palette(0xfff8f9fa, 0xffe8eaed, 0xff202124, 0xff1a73e8);
        }
    }
}
