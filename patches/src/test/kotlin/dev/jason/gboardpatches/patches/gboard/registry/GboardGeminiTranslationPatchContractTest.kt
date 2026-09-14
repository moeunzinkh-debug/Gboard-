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
    fun manifestContractOnlyDeclaresTheInternetPermission() {
        val source = Files.readString(
            repositoryRoot().resolve(
                "patches/src/main/kotlin/dev/jason/gboardpatches/patches/gboard/" +
                    "features/geminitranslation/GboardGeminiTranslationManifestPatch.kt",
            ),
            StandardCharsets.UTF_8,
        )
        assertTrue(source.contains("android.permission.INTERNET"))
        assertFalse(source.contains("ensureManifestComponent"))
        assertFalse(source.contains("<service"))
        assertFalse(source.contains("<activity"))
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
