package dev.jason.gboardpatches.patches.gboard.features.geminitranslation

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.childElements
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestComponent
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestUsesPermission
import dev.jason.gboardpatches.patches.gboard.shared.manifestAndroidAttribute
import dev.jason.gboardpatches.patches.gboard.shared.setManifestAndroidAttribute
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import org.w3c.dom.Document
import org.w3c.dom.Element

internal val gboardGeminiTranslationManifestPatch = resourcePatch(
    description = "宣告 Gemini Translation 呼叫 generativelanguage.googleapis.com 所需的 INTERNET 權限與翻譯視窗 Activity。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        document("AndroidManifest.xml").use { document ->
            val manifest = document.documentElement
            ensureManifestUsesPermission(document, manifest, INTERNET_PERMISSION)
            ensureGeminiTranslationActivity(document, manifest)
        }
    }
}

private fun ensureGeminiTranslationActivity(document: Document, manifest: Element) {
    val application = manifest.childElements("application").firstOrNull()
        ?: error("Could not find application element in AndroidManifest.xml")
    val activity = ensureManifestComponent(
        document,
        application,
        "activity",
        GEMINI_TRANSLATION_ACTIVITY_CLASS,
    )
    if (activity.manifestAndroidAttribute("exported").isNullOrBlank()) {
        activity.setManifestAndroidAttribute("exported", "false")
    }
}

internal const val GEMINI_TRANSLATION_ACTIVITY_CLASS =
    "dev.jason.gboardpatches.extension.geminitranslation.GboardGeminiTranslationActivity"

private const val INTERNET_PERMISSION = "android.permission.INTERNET"
