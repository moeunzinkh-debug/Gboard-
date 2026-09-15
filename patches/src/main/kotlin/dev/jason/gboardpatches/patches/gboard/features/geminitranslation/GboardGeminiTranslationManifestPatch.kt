package dev.jason.gboardpatches.patches.gboard.features.geminitranslation

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.gboard.shared.ensureManifestUsesPermission
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/**
 * Gemini Translation only needs the network permission.
 *
 * The translation bar is drawn inside Gboard's own input window, so no Activity, dialog or service
 * is declared here — the whole feature lives in the IME process that is already running.
 */
internal val gboardGeminiTranslationManifestPatch = resourcePatch(
    description = "宣告 Gemini Translation 呼叫 generativelanguage.googleapis.com 所需的 INTERNET 權限。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    finalize {
        document("AndroidManifest.xml").use { document ->
            ensureManifestUsesPermission(document, document.documentElement, INTERNET_PERMISSION)
        }
    }
}

private const val INTERNET_PERMISSION = "android.permission.INTERNET"
