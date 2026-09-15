package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.geminitranslation.gboardGeminiTranslationFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.geminitranslation.gboardGeminiTranslationLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.geminitranslation.gboardGeminiTranslationManifestPatch
import dev.jason.gboardpatches.patches.gboard.shared.accesspoint.gboardAccessPointContributions1803Patch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardGeminiTranslationPatchContractTest {
    private val featureRoot = "extensions/extension/src/main/java/dev/jason/gboardpatches/" +
        "extension/geminitranslation/"
    private val patchRoot = "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
        "features/geminitranslation/"

    @Test
    fun publicPatchIsIndependentDefaultOnAndOwnsTheRequiredClosure() {
        val patch = GboardPublishedPatchCatalog.morpheRegistrations.single {
            it.name == "Gemini Translation"
        }
        assertSame(gboardGeminiTranslationPatch, patch)
        assertTrue(patch.use)
        assertTrue(patch.description!!.contains("Gemini"))
        assertTrue(patch.dependencies.any { it === gboardPatchesSettingsPatch })
        assertTrue(patch.dependencies.any { it === gboardGeminiTranslationFeatureMarkerPatch })
        assertTrue(patch.dependencies.any { it === gboardGeminiTranslationManifestPatch })
        assertTrue(patch.dependencies.any { it === gboardGeminiTranslationLifecyclePatch })
        assertTrue(patch.dependencies.any { it === gboardAccessPointContributions1803Patch })
        val publicNames = GboardPublishedPatchCatalog.morpheRegistrations
            .mapNotNull { it.name }
            .toSet()
        assertTrue(patch.dependencies.mapNotNull { it.name }.none { it in publicNames })
    }

    @Test
    fun manifestOnlyGrantsTheNetworkBecauseTheBarNeverOpensAWindow() {
        val source = read(patchRoot + "GboardGeminiTranslationManifestPatch.kt")
        assertTrue(source.contains("android.permission.INTERNET"))
        assertTrue(source.contains("ensureManifestUsesPermission"))
        // The bar is part of the keyboard: no Activity, no dialog window, no background service.
        assertFalse(source.contains("ensureManifestComponent"))
        assertFalse(source.contains("<service"))
        assertFalse(Files.exists(
            repositoryRoot().resolve(featureRoot + "GboardGeminiTranslationActivity.java"),
        ))
    }

    @Test
    fun lifecycleDelegateHandsTheKeyboardViewToTheBar() {
        val source = read(patchRoot + "GboardGeminiTranslationLifecyclePatch.kt")
        assertTrue(source.contains("GEMINI_TRANSLATION_RUNTIME_ON_INPUT_VIEW_STARTING"))
        assertTrue(source.contains("Lcom/google/android/libraries/inputmethod/inputview/InputView;"))
        assertTrue(source.contains("iget-object"))
        assertTrue(source.contains("addHelperMethodIfMissing"))
        assertTrue(source.contains("Landroid/widget/FrameLayout;"))
        assertEquals(
            listOf(
                "Ljava/lang/Object;",
                "Ljava/lang/Object;",
                "Landroid/view/inputmethod/EditorInfo;",
            ),
            RuntimeAbiCatalog.abi(
                RuntimeCallId.GEMINI_TRANSLATION_RUNTIME_ON_INPUT_VIEW_STARTING,
            ).parameters,
        )
    }

    @Test
    fun lifecycleEntryDelegateAvoidsInlineSmaliCompilation() {
        val source = read(patchRoot + "GboardGeminiTranslationLifecyclePatch.kt")
        // The one-line entry call must be built programmatically: compiling its smali text
        // against some APK/patcher combinations silently yields zero methods and aborts the
        // whole session with "Collection is empty."
        assertTrue(source.contains("BuilderInstruction35c"))
        assertTrue(source.contains("BuilderInstruction3rc"))
        assertTrue(source.contains("ImmutableMethodReference"))
        assertTrue(source.contains("registerCount >= parameterWords"))
        assertFalse(source.contains("invoke-direct {p0, p1}"))
    }

    @Test
    fun translationBarDocksInsideTheKeyboardAboveTheRows() {
        val panel = read(featureRoot + "GboardGeminiTranslationPanel.java")
        assertTrue(panel.contains("class GboardGeminiTranslationPanel"))
        assertTrue(panel.contains("import android.widget.FrameLayout;"))
        assertTrue(panel.contains("host.addView(bar, params)"))
        // The bar docks into Gboard's own input view (a FrameLayout), floating above the
        // keyboard rows with elevation — the placement the calculator strip already uses,
        // because asking the inputArea frame for extra padding left the bar invisible when
        // the IME window refused to grow.
        assertTrue(panel.contains("findKeyboardPanelTop"))
        assertTrue(panel.contains("params.topMargin = topMargin"))
        assertTrue(panel.contains("setElevation"))
        // A stale or missing latched input view must not make the bar unreachable.
        assertTrue(panel.contains("resolveInputViewFromWindow"))
        assertTrue(panel.contains("GboardGeminiTranslationClient.translate("))
        assertTrue(panel.contains("GboardGeminiTranslationRuntime.commitReplacement("))
        // The Google Translate layout: language chips with swap, a source card, a result card.
        assertTrue(panel.contains("swapLanguages()"))
        assertTrue(panel.contains("sourceCard"))
        assertTrue(panel.contains("resultCard"))

        // Docking inside the input view must never rebuild the keyboard or spawn a window.
        assertFalse(panel.contains("setInputView("))
        assertFalse(panel.contains("startActivity("))
        assertFalse(panel.contains("TYPE_APPLICATION_OVERLAY"))
    }

    @Test
    fun toolbarTapOpensTheBarAndOnlyFallsBackToInPlaceTranslation() {
        val runtime = read(featureRoot + "GboardGeminiTranslationRuntime.java")
        assertTrue(runtime.contains("openTranslationPanel(Context"))
        assertTrue(runtime.contains("GboardGeminiTranslationPanel.toggle(context)"))
        assertTrue(runtime.contains("translateCurrentInput(context)"))
        assertTrue(runtime.contains(
            "onInputViewStarting(Object inputMethodService, Object inputView",
        ))
        assertTrue(runtime.contains("rememberInputView(inputView)"))

        // The bar must open before anything else can veto it: a tap without an API key still
        // shows the Google Translate panel, which explains inline what is missing.
        val openMethod = runtime.substringAfter("public static boolean openTranslationPanel")
        val toggleIndex = openMethod.indexOf("GboardGeminiTranslationPanel.toggle(context)")
        val fallbackIndex = openMethod.indexOf("translateCurrentInput(context)")
        assertTrue(toggleIndex >= 0 && fallbackIndex > toggleIndex)
        assertFalse(openMethod.substring(0, toggleIndex).contains("hasApiKey()"))

        // No dialog Activity is launched, and no translation result is parked for a handoff.
        assertFalse(runtime.contains("GboardGeminiTranslationActivity"))
        assertFalse(runtime.contains("startActivity("))
        assertFalse(runtime.contains("insertTranslation("))
    }

    @Test
    fun runtimeNeverSendsTheApiKeyInTheQueryString() {
        val client = read(featureRoot + "GboardGeminiTranslationClient.java")
        assertTrue(client.contains("\"x-goog-api-key\""))
        assertFalse(client.contains("?key="))
        assertTrue(client.contains("https://generativelanguage.googleapis.com/v1beta/models/"))
    }

    @Test
    fun settingsScreenOffersAGetApiKeyLink() {
        val feature = read(featureRoot + "GboardGeminiTranslationSettingsFeature.java")
        assertTrue(feature.contains("\"https://aistudio.google.com/apikey\""))
        assertTrue(feature.contains("openExternalUrl(host, GEMINI_API_KEY_URL)"))
        assertTrue(feature.contains(
            "R.string.gboard_patches_gemini_translation_get_api_key_title"))

        val settingsText = read(
            "extensions/extension/src/main/settings-text/gboard_settings_text.xml",
        )
        assertTrue(settingsText.contains("gboard_patches_gemini_translation_get_api_key_title"))
        assertTrue(settingsText.contains("gboard_patches_gemini_translation_get_api_key_summary"))
    }

    @Test
    fun apiKeyStoreIsNotPartOfBackupAndRestore() {
        val backupManager = read(
            "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                "backuprestore/GboardPatchesBackupManager.java",
        )
        assertFalse(backupManager.contains("gboard_gemini_translation_settings"))
        assertFalse(backupManager.contains("GboardGeminiTranslationSettings"))
    }

    private fun read(relativePath: String): String = Files.readString(
        repositoryRoot().resolve(relativePath),
        StandardCharsets.UTF_8,
    )

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
    }
}
