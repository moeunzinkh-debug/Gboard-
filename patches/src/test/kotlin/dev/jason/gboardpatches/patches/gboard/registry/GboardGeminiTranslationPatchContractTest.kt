package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.geminitranslation.gboardGeminiTranslationFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.geminitranslation.gboardGeminiTranslationLifecyclePatch
import dev.jason.gboardpatches.patches.gboard.features.geminitranslation.gboardGeminiTranslationManifestPatch
import dev.jason.gboardpatches.patches.gboard.shared.accesspoint.gboardAccessPointContributions1803Patch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardGeminiTranslationPatchContractTest {
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
    fun manifestContractDeclaresInternetPermissionAndTranslationActivity() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/geminitranslation/GboardGeminiTranslationManifestPatch.kt",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(source.contains("android.permission.INTERNET"))
        assertTrue(source.contains("ensureManifestComponent"))
        assertTrue(source.contains("GboardGeminiTranslationActivity"))
        assertTrue(source.contains("\"exported\", \"false\""))
        // The translation box is an Activity, never a background service.
        assertFalse(source.contains("<service"))
    }

    @Test
    fun translationBoxWiresSourceFieldTranslateAndInsertBack() {
        val activity = Files.readString(
            repositoryRoot().resolve(
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                    "geminitranslation/GboardGeminiTranslationActivity.java",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(activity.contains("class GboardGeminiTranslationActivity"))
        assertTrue(activity.contains("new EditText(this)"))
        assertTrue(activity.contains("GboardGeminiTranslationClient.translate("))
        assertTrue(activity.contains("GboardGeminiTranslationRuntime.insertTranslation(this"))
        assertTrue(activity.contains("insertResult()"))

        val runtime = Files.readString(
            repositoryRoot().resolve(
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                    "geminitranslation/GboardGeminiTranslationRuntime.java",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(runtime.contains("openTranslationBox(Context"))
        assertTrue(runtime.contains("insertTranslation(Context"))
        assertTrue(runtime.contains("launchTranslationBoxActivity"))
        assertTrue(runtime.contains("translateCurrentInput(context)"))
    }

    @Test
    fun runtimeNeverSendsTheApiKeyInTheQueryString() {
        val client = Files.readString(
            repositoryRoot().resolve(
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                    "geminitranslation/GboardGeminiTranslationClient.java",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(client.contains("\"x-goog-api-key\""))
        assertFalse(client.contains("?key="))
        assertTrue(client.contains("https://generativelanguage.googleapis.com/v1beta/models/"))
    }

    @Test
    fun settingsScreenOffersAGetApiKeyLink() {
        val feature = Files.readString(
            repositoryRoot().resolve(
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                    "geminitranslation/GboardGeminiTranslationSettingsFeature.java",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(feature.contains("\"https://aistudio.google.com/apikey\""))
        assertTrue(feature.contains("openExternalUrl(host, GEMINI_API_KEY_URL)"))
        assertTrue(feature.contains(
            "R.string.gboard_patches_gemini_translation_get_api_key_title"))

        val settingsText = Files.readString(
            repositoryRoot().resolve(
                "extensions/extension/src/main/settings-text/gboard_settings_text.xml",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(settingsText.contains("gboard_patches_gemini_translation_get_api_key_title"))
        assertTrue(settingsText.contains("gboard_patches_gemini_translation_get_api_key_summary"))
    }

    @Test
    fun apiKeyStoreIsNotPartOfBackupAndRestore() {
        val backupManager = Files.readString(
            repositoryRoot().resolve(
                "extensions/extension/src/main/java/dev/jason/gboardpatches/extension/" +
                    "backuprestore/GboardPatchesBackupManager.java",
            ),
            StandardCharsets.UTF_8,
        )
        assertFalse(backupManager.contains("gboard_gemini_translation_settings"))
        assertFalse(backupManager.contains("GboardGeminiTranslationSettings"))
    }

    private fun repositoryRoot(): Path {
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        return generateSequence(workingDirectory) { it.parent }
            .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
    }
}
