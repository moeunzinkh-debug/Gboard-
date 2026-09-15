package dev.jason.gboardpatches.extension.geminitranslation;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
 * The Gemini translation bar, docked inside Gboard's own input view.
 *
 * <p>Tapping the Gemini Translate Access Point opens a Google Translate style panel right above
 * the keyboard rows: a header with the source language, ⇄ and the target language, a source card
 * that mirrors whatever the focused field holds, and a result card with the translation plus
 * ✓ insert and copy actions. The bar docks straight into Gboard's input view — a FrameLayout
 * subclass — floating above the keyboard rows with elevation, exactly like the calculator
 * suggestion strip. That placement needs no window resizing: the previous approach asked the
 * framework's inputArea frame for extra padding, and when the IME window refused to grow the bar
 * stayed invisible and the tap appeared to do nothing.
 *
 * <p>The bar opens even before a Gemini API key is configured; the result card then carries an
 * inline hint instead of only a toast. When the latched keyboard view is stale or was never
 * handed over, it is recovered from the IME window's own inputArea. Nothing of Gboard's is
 * reparented, every path fails closed, and a rebuilt keyboard simply detaches the bar.
 */
public final class GboardGeminiTranslationPanel {
    /** Tag used to recognise the bar again on the keyboard's own view tree. */
    static final String PANEL_TAG = "gboard-patches-gemini-translation-panel";

    private static final int STATE_SOURCE = 0;
    private static final int STATE_TRANSLATING = 1;
    private static final int STATE_RESULT = 2;

    /** Poll interval for mirroring the focused field into the bar while it is open. */
    private static final long MIRROR_INTERVAL_MS = 400L;
    private static final int CONTROL_HEIGHT_DP = 36;
    private static final int TRANSLATE_BUTTON_DP = 40;
    private static final int BAR_MARGIN_DP = 6;
    private static final int CARD_MARGIN_DP = 6;
    private static final int CARD_PADDING_DP = 12;
    private static final int CARD_RADIUS_DP = 16;
    private static final int BAR_RADIUS_DP = 24;
    private static final int BAR_ELEVATION_DP = 24;
    private static final int SOURCE_MAX_LINES = 3;
    private static final int RESULT_MAX_LINES = 4;

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
    private final TextView sourceTextView;
    private final TextView translateButton;
    private final TextView resultTextView;
    private final TextView insertButton;
    private final TextView copyButton;
    private final LinearLayout languageRow;
    private final HorizontalScrollView languagePicker;
    private final LinearLayout languagePickerRow;

    private final Runnable mirrorTick = this::mirrorEditorText;

    private GboardGeminiTranslationRuntime.Selection selection;
    private String sourceValue = "";
    private String translatedText;
    private String statusMessage;
    private int state = STATE_SOURCE;
    private boolean pickerForTarget;
    private String editorPackage;

    private GboardGeminiTranslationPanel(InputMethodService service, FrameLayout host) {
        this.service = service;
        this.host = host;
        this.palette = Palette.forKeyboard(host.getContext(), keyboardBackgroundColour(host));

        Context context = host.getContext();
        bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setTag(PANEL_TAG);
        // The bar is a touch barrier: presses on its padding must never reach the keys below.
        bar.setClickable(true);
        bar.setPadding(dp(BAR_MARGIN_DP), dp(BAR_MARGIN_DP), dp(BAR_MARGIN_DP),
                dp(BAR_MARGIN_DP));
        bar.setBackground(rounded(palette.surface, dp(BAR_RADIUS_DP), Color.TRANSPARENT));
        bar.setElevation(dp(BAR_ELEVATION_DP));

        languageRow = new LinearLayout(context);
        languageRow.setOrientation(LinearLayout.HORIZONTAL);
        languageRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView close = control("←", label("បិទរបារបកប្រែ", "關閉翻譯列",
                "Close the translation bar"));
        close.setOnClickListener(view -> detach());
        languageRow.addView(close, controlParams());

        sourceChip = chip(label("ភាសាដើម", "來源語言", "Source language"));
        sourceChip.setOnClickListener(view -> openLanguagePicker(false));
        languageRow.addView(sourceChip, chipParams(dp(BAR_MARGIN_DP), 1f));

        TextView swap = control("⇄", label("ប្ដូរភាសា", "交換語言", "Swap languages"));
        swap.setOnClickListener(view -> swapLanguages());
        languageRow.addView(swap, controlParams());

        targetChip = chip(label("ភាសាគោលដៅ", "目標語言", "Target language"));
        targetChip.setOnClickListener(view -> openLanguagePicker(true));
        languageRow.addView(targetChip, chipParams(dp(BAR_MARGIN_DP), 1f));
        bar.addView(languageRow, rowParams());

        LinearLayout sourceCard = new LinearLayout(context);
        sourceCard.setOrientation(LinearLayout.HORIZONTAL);
        sourceCard.setGravity(Gravity.CENTER_VERTICAL);
        sourceCard.setPadding(dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP),
                dp(CARD_PADDING_DP));
        sourceCard.setBackground(rounded(palette.field, dp(CARD_RADIUS_DP), palette.fieldStroke));

        sourceTextView = new TextView(context);
        sourceTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        sourceTextView.setTextColor(palette.primaryText);
        sourceTextView.setMaxLines(SOURCE_MAX_LINES);
        sourceTextView.setEllipsize(TextUtils.TruncateAt.END);
        sourceTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        sourceTextView.setLineSpacing(dp(2), 1f);
        sourceCard.addView(sourceTextView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        translateButton = new TextView(context);
        translateButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        translateButton.setTextColor(palette.onAccent);
        translateButton.setGravity(Gravity.CENTER);
        translateButton.setContentDescription(label("បកប្រែ", "翻譯", "Translate"));
        translateButton.setBackground(circle(palette.accent));
        translateButton.setClickable(true);
        translateButton.setOnClickListener(view -> translate());
        LinearLayout.LayoutParams translateParams = new LinearLayout.LayoutParams(
                dp(TRANSLATE_BUTTON_DP), dp(TRANSLATE_BUTTON_DP));
        translateParams.leftMargin = dp(CARD_MARGIN_DP);
        sourceCard.addView(translateButton, translateParams);
        LinearLayout.LayoutParams sourceCardParams = rowParams();
        sourceCardParams.topMargin = dp(CARD_MARGIN_DP);
        bar.addView(sourceCard, sourceCardParams);

        LinearLayout resultCard = new LinearLayout(context);
        resultCard.setOrientation(LinearLayout.VERTICAL);
        resultCard.setPadding(dp(CARD_PADDING_DP), dp(CARD_PADDING_DP), dp(CARD_PADDING_DP),
                dp(8));
        resultCard.setBackground(rounded(palette.resultField, dp(CARD_RADIUS_DP),
                Color.TRANSPARENT));

        resultTextView = new TextView(context);
        resultTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        resultTextView.setTextColor(palette.secondaryText);
        resultTextView.setMaxLines(RESULT_MAX_LINES);
        resultTextView.setEllipsize(TextUtils.TruncateAt.END);
        resultTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        resultTextView.setLineSpacing(dp(2), 1f);
        resultTextView.setOnClickListener(view -> copyTranslation());
        resultCard.addView(resultTextView, rowParams());

        LinearLayout resultActions = new LinearLayout(context);
        resultActions.setOrientation(LinearLayout.HORIZONTAL);
        resultActions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        insertButton = smallAction("✓ " + label("បញ្ចូល", "插入", "Insert"),
                label("បញ្ចូលការបកប្រែ", "插入翻譯結果", "Insert the translation"));
        insertButton.setOnClickListener(view -> insertTranslation());
        resultActions.addView(insertButton);

        copyButton = smallAction(label("ចម្លង", "複製", "Copy"),
                label("ចម្លងលទ្ធផលបកប្រែ", "複製翻譯結果", "Copy the translation"));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyParams.leftMargin = dp(CARD_MARGIN_DP);
        copyButton.setOnClickListener(view -> copyTranslation());
        resultActions.addView(copyButton, copyParams);

        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = dp(4);
        actionsParams.gravity = Gravity.END;
        resultCard.addView(resultActions, actionsParams);
        LinearLayout.LayoutParams resultCardParams = rowParams();
        resultCardParams.topMargin = dp(CARD_MARGIN_DP);
        bar.addView(resultCard, resultCardParams);

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
        bar.addView(languagePicker, rowParams());
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
                    || inputView != current.host
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
        if (!settings.isEnabled()) {
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
     * Gboard's own input view, which the lifecycle delegate latched. The view is a FrameLayout
     * subclass, so the bar docks straight into it above the keyboard rows — the same placement
     * the calculator suggestion strip uses. When the latch is empty or stale (a rebuilt keyboard,
     * or a delegate that never fired) the view is recovered from the IME window's inputArea.
     */
    private static FrameLayout resolveHost(InputMethodService service) {
        try {
            if (service == null) {
                return null;
            }
            View inputView = GboardGeminiTranslationRuntime.activeInputView();
            if (!(inputView instanceof FrameLayout host)
                    || !host.isAttachedToWindow()
                    || host.getWidth() <= 0) {
                inputView = resolveInputViewFromWindow(service);
                if (!(inputView instanceof FrameLayout fresh)) {
                    return null;
                }
                GboardGeminiTranslationRuntime.rememberInputView(fresh);
                inputView = fresh;
            }
            return (FrameLayout) inputView;
        } catch (Throwable failure) {
            return null;
        }
    }

    /** The current input view, found through the IME's own window when the latch missed it. */
    private static View resolveInputViewFromWindow(InputMethodService service) {
        try {
            Dialog dialog = service.getWindow();
            android.view.Window window = dialog == null ? null : dialog.getWindow();
            if (window == null) {
                return null;
            }
            int inputAreaId =
                    service.getResources().getIdentifier("inputArea", "id", "android");
            if (inputAreaId == 0) {
                return null;
            }
            View inputArea = window.findViewById(inputAreaId);
            if (!(inputArea instanceof ViewGroup group)) {
                return null;
            }
            for (int index = group.getChildCount() - 1; index >= 0; index--) {
                View child = group.getChildAt(index);
                if (child != null && child.getVisibility() == View.VISIBLE
                        && child.isAttachedToWindow() && !PANEL_TAG.equals(child.getTag())) {
                    return child;
                }
            }
        } catch (Throwable ignored) {
            // The window fallback is best effort only.
        }
        return null;
    }

    private boolean attach() {
        int topMargin = resolveTopMargin();
        if (topMargin < 0) {
            return false;
        }
        removeStaleBars(host);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.leftMargin = dp(BAR_MARGIN_DP);
        params.rightMargin = dp(BAR_MARGIN_DP);
        params.topMargin = topMargin;
        host.addView(bar, params);
        bar.bringToFront();
        EditorInfo editorInfo = safeCurrentEditorInfo();
        editorPackage = editorInfo == null ? null : editorInfo.packageName;
        render();
        refreshFromEditor();
        scheduleMirror();
        return true;
    }

    private void detach() {
        GboardGeminiTranslationPanel current = active;
        try {
            MAIN_HANDLER.removeCallbacks(mirrorTick);
            if (bar.getParent() == host) {
                host.removeView(bar);
            }
        } catch (Throwable ignored) {
            // Cleaning up after the bar must never reach Gboard.
        } finally {
            if (current == this) {
                active = null;
            }
        }
    }

    /** Drops any orphaned bar left in this host by a panel that lost track of itself. */
    private static void removeStaleBars(FrameLayout host) {
        try {
            for (int index = host.getChildCount() - 1; index >= 0; index--) {
                View child = host.getChildAt(index);
                if (child != null && PANEL_TAG.equals(child.getTag())) {
                    host.removeViewAt(index);
                }
            }
        } catch (Throwable ignored) {
            // Stale bars are cosmetic; attaching still works.
        }
    }

    /**
     * Where the bar docks: just above Gboard's keyboard rows, located the same way the calculator
     * suggestion strip finds them, so the bar never needs the IME window to grow.
     */
    private int resolveTopMargin() {
        try {
            if (host.getWidth() <= 0) {
                return -1;
            }
            int[] hostLocation = new int[2];
            host.getLocationOnScreen(hostLocation);
            int keyboardTop = findKeyboardPanelTop(
                    host, host, hostLocation[1], Integer.MAX_VALUE, 0);
            if (keyboardTop == Integer.MAX_VALUE) {
                return -1;
            }
            return Math.max(0, keyboardTop - hostLocation[1] + dp(4));
        } catch (Throwable failure) {
            return -1;
        }
    }

    /**
     * The screen top of the keyboard rows: the first visible descendant that is nearly as wide as
     * the input view and at least 160dp tall, anchored to the bottom of the input view.
     */
    private static int findKeyboardPanelTop(FrameLayout host, View current,
            int hostScreenTop, int bestScreenTop, int depth) {
        if (current == null || depth > 16 || current.getVisibility() != View.VISIBLE
                || !current.isAttachedToWindow()) {
            return bestScreenTop;
        }
        if (current != host
                && current.getWidth() >= Math.round(host.getWidth() * 0.85f)
                && current.getHeight() >= dp(host.getContext(), 160)) {
            int[] location = new int[2];
            current.getLocationOnScreen(location);
            int hostBottom = hostScreenTop + host.getHeight();
            int minimumTop = hostScreenTop + host.getHeight() / 3;
            if (location[1] >= minimumTop
                    && location[1] + current.getHeight()
                    >= hostBottom - dp(host.getContext(), 48)) {
                bestScreenTop = Math.min(bestScreenTop, location[1]);
            }
        }
        if (current instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                bestScreenTop = findKeyboardPanelTop(
                        host, group.getChildAt(index), hostScreenTop,
                        bestScreenTop, depth + 1);
            }
        }
        return bestScreenTop;
    }

    /** The solid colour the keyboard paints with, when it uses one at all. */
    private static Integer keyboardBackgroundColour(FrameLayout host) {
        try {
            for (int index = host.getChildCount() - 1; index >= 0; index--) {
                View child = host.getChildAt(index);
                if (child == null || PANEL_TAG.equals(child.getTag())) {
                    continue;
                }
                if (child.getBackground() instanceof ColorDrawable background) {
                    return background.getColor();
                }
            }
        } catch (Throwable ignored) {
            // Fall back to the theme palette below.
        }
        return null;
    }

    private EditorInfo safeCurrentEditorInfo() {
        try {
            return service.getCurrentInputEditorInfo();
        } catch (Throwable failure) {
            return null;
        }
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
            renderContents(settings);
            boolean translating = state == STATE_TRANSLATING;
            translateButton.setText(translating ? "…" : "↻");
            translateButton.setEnabled(!translating);
            translateButton.setContentDescription(translating
                    ? label("កំពុងបកប្រែ", "翻譯中", "Translating")
                    : label("បកប្រែ", "翻譯", "Translate"));
            boolean hasResult = state == STATE_RESULT
                    && translatedText != null && !translatedText.isEmpty();
            insertButton.setEnabled(hasResult);
            insertButton.setAlpha(hasResult ? 1f : 0.45f);
            boolean canCopy = (translatedText != null && !translatedText.isEmpty())
                    || !sourceValue.isEmpty();
            copyButton.setEnabled(canCopy);
            copyButton.setAlpha(canCopy ? 1f : 0.45f);
        } catch (Throwable ignored) {
            // A rendering failure simply leaves the previous frame on screen.
        }
    }

    /** The source card mirrors the field; the result card carries status or the translation. */
    private void renderContents(GboardGeminiTranslationSettings.Snapshot settings) {
        String source = sourceValue == null ? "" : sourceValue;
        sourceTextView.setText(source.isEmpty()
                ? label("រាយបញ្ចូលនៅទីនេះ ដើម្បីបកប្រែ", "輸入要翻譯的文字…",
                        "Type text to translate…")
                : source);
        sourceTextView.setTextColor(source.isEmpty()
                ? palette.secondaryText : palette.primaryText);

        String result;
        int resultColour = palette.primaryText;
        if (state == STATE_TRANSLATING) {
            result = label("កំពុងបកប្រែ…", "翻譯中…", "Translating…");
            resultColour = palette.secondaryText;
        } else if (statusMessage != null && !statusMessage.isEmpty()) {
            result = statusMessage;
            resultColour = palette.secondaryText;
        } else if (!settings.hasApiKey()) {
            result = label("សូមដាក់គ្រាប់សម្ងាត់ Gemini API ក្នុងការកំណត់ Patches ជាមុនសិន",
                    "請先在 Patches 設定輸入 Gemini API 金鑰",
                    "Set your Gemini API key in Patches settings first");
            resultColour = palette.secondaryText;
        } else if (state == STATE_RESULT
                && translatedText != null && !translatedText.isEmpty()) {
            result = translatedText;
        } else {
            result = label("លទ្ធផលបកប្រែនឹងបង្ហាញនៅទីនេះ", "翻譯結果會顯示在這裡",
                    "Translation will appear here");
            resultColour = palette.secondaryText;
        }
        resultTextView.setText(result);
        resultTextView.setTextColor(resultColour);
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
            if (state == STATE_RESULT && translatedText != null && !translatedText.isEmpty()) {
                // Like Google Translate: the translation becomes the new source.
                String previousSource = sourceValue;
                sourceValue = translatedText;
                translatedText = previousSource;
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
        languageRow.setVisibility(View.GONE);
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
        languagePickerRow.addView(chip, pickerChipParams(dp(4)));
    }

    private void closeLanguagePicker() {
        languagePicker.setVisibility(View.GONE);
        languageRow.setVisibility(View.VISIBLE);
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
     * Picks up whatever the focused field holds right now, so typing on the keyboard feeds the
     * source card without leaving it. A finished translation is left alone until the next
     * translate press, which is what keeps ⇄ swap meaningful.
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
                if (selection != null && selection.wasSelection
                        && read.text.equals(selection.text)) {
                    return;
                }
                selection = read;
                sourceValue = read.text;
                translatedText = null;
                statusMessage = null;
                state = STATE_SOURCE;
                render();
                return;
            }
            selection = read;
            if (state == STATE_SOURCE && !read.text.equals(sourceValue)) {
                sourceValue = read.text;
                statusMessage = null;
                render();
            }
        } catch (Throwable ignored) {
            // The bar keeps the last text it saw.
        }
    }

    private void translate() {
        final String text = sourceValue == null ? "" : sourceValue.trim();
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
            render();
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
            statusMessage = null;
            state = STATE_RESULT;
        } else {
            translatedText = null;
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
        String translation = translatedText != null && !translatedText.isEmpty()
                ? translatedText
                : sourceValue;
        GboardGeminiTranslationRuntime.copyToClipboard(host.getContext(), translation);
        GboardGeminiTranslationRuntime.showToast(host.getContext(), "បានចម្លងលទ្ធផលបកប្រែ",
                "已複製翻譯", "Translation copied");
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
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(6), dp(14), dp(6));
        chip.setBackground(rounded(palette.field, dp(18), Color.TRANSPARENT));
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
        control.setBackground(rounded(palette.field, dp(18), Color.TRANSPARENT));
        control.setClickable(true);
        return control;
    }

    private TextView smallAction(String text, String description) {
        TextView action = new TextView(host.getContext());
        action.setText(text);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        action.setTextColor(palette.accent);
        action.setGravity(Gravity.CENTER);
        action.setContentDescription(description);
        action.setPadding(dp(12), dp(6), dp(12), dp(6));
        action.setMinHeight(dp(30));
        action.setBackground(rounded(palette.field, dp(15), palette.fieldStroke));
        action.setClickable(true);
        return action;
    }

    private LinearLayout.LayoutParams controlParams() {
        return new LinearLayout.LayoutParams(dp(CONTROL_HEIGHT_DP), dp(CONTROL_HEIGHT_DP));
    }

    private LinearLayout.LayoutParams chipParams(int margin, float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, dp(CONTROL_HEIGHT_DP), weight);
        params.leftMargin = margin;
        params.rightMargin = margin;
        return params;
    }

    private LinearLayout.LayoutParams pickerChipParams(int margin) {
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

    private GradientDrawable circle(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        return background;
    }

    private int dp(int value) {
        return Math.max(1, Math.round(value * host.getResources().getDisplayMetrics().density));
    }

    private static int dp(Context context, int value) {
        float density = context != null
                ? context.getResources().getDisplayMetrics().density : 1f;
        return Math.max(1, Math.round(value * density));
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

    /** Bar colours; the keyboard's own background wins so themes keep matching. */
    private static final class Palette {
        private static final int ACCENT_LIGHT = 0xff1a73e8;
        private static final int ACCENT_DARK = 0xff8ab4f8;

        private final int surface;
        private final int field;
        private final int resultField;
        private final int fieldStroke;
        private final int primaryText;
        private final int secondaryText;
        private final int accent;
        private final int onAccent;

        private Palette(int surface, int field, int resultField, int fieldStroke,
                int primaryText, int secondaryText, int accent, int onAccent) {
            this.surface = surface;
            this.field = field;
            this.resultField = resultField;
            this.fieldStroke = fieldStroke;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.accent = accent;
            this.onAccent = onAccent;
        }

        static Palette forKeyboard(Context context, Integer keyboardColour) {
            if (keyboardColour != null) {
                if (isDark(keyboardColour)) {
                    return new Palette(keyboardColour,
                            tint(keyboardColour, 0.16f),
                            blend(keyboardColour, ACCENT_DARK, 0.20f),
                            tint(keyboardColour, 0.28f),
                            Color.WHITE,
                            0xff9aa0a6,
                            ACCENT_DARK,
                            0xff0b1d33);
                }
                return new Palette(keyboardColour,
                        tint(keyboardColour, -0.03f),
                        blend(keyboardColour, ACCENT_LIGHT, 0.10f),
                        tint(keyboardColour, -0.10f),
                        0xff202124,
                        0xff5f6368,
                        ACCENT_LIGHT,
                        Color.WHITE);
            }
            boolean dark = context != null
                    && (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return dark
                    ? new Palette(0xff303134, 0xff3c4043,
                            blend(0xff303134, ACCENT_DARK, 0.20f),
                            0xff4a4d51, Color.WHITE, 0xff9aa0a6, ACCENT_DARK, 0xff0b1d33)
                    : new Palette(0xffffffff, 0xfff8f9fa,
                            blend(0xffffffff, ACCENT_LIGHT, 0.10f),
                            0xffdadce0, 0xff202124, 0xff5f6368, ACCENT_LIGHT, Color.WHITE);
        }

        /** Lightens a colour when {@code amount} is positive, darkens it when negative. */
        private static int tint(int colour, float amount) {
            float factor = 1f + amount;
            int red = clampChannel(Math.round(Color.red(colour) * factor));
            int green = clampChannel(Math.round(Color.green(colour) * factor));
            int blue = clampChannel(Math.round(Color.blue(colour) * factor));
            return Color.argb(Color.alpha(colour), red, green, blue);
        }

        /** Mixes {@code overlay} into {@code base}; the tint Google gives translated text. */
        private static int blend(int base, int overlay, float ratio) {
            float inverse = 1f - ratio;
            int red = clampChannel(
                    Math.round(Color.red(base) * inverse + Color.red(overlay) * ratio));
            int green = clampChannel(
                    Math.round(Color.green(base) * inverse + Color.green(overlay) * ratio));
            int blue = clampChannel(
                    Math.round(Color.blue(base) * inverse + Color.blue(overlay) * ratio));
            return Color.argb(0xff, red, green, blue);
        }

        private static int clampChannel(int value) {
            return value < 0 ? 0 : Math.min(255, value);
        }

        private static boolean isDark(int colour) {
            return 0.299f * Color.red(colour) + 0.587f * Color.green(colour)
                    + 0.114f * Color.blue(colour) < 140f;
        }
    }
}
