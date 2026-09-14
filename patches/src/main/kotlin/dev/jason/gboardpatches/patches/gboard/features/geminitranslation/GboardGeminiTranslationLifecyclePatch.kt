package dev.jason.gboardpatches.patches.gboard.features.geminitranslation

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardGeminiTranslationLifecyclePatch = bytecodePatch(
    description = "在 18.0.3 onStartInputView 加入薄 delegate，讓 Gemini 翻譯取得目前的 InputConnection。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        findMutableMethodOrThrow(GboardGeminiTranslationTargets.onStartInputView)
            .applyGeminiTranslationEntryDelegate(
                RuntimeCallId.GEMINI_TRANSLATION_RUNTIME_ON_INPUT_VIEW_STARTING,
            )
    }
}

internal object GboardGeminiTranslationTargets {
    val onStartInputView = GboardMethodTarget(
        classType = "Loup;",
        name = "onStartInputView",
        parameterTypes = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returnType = "V",
    )
}

internal fun MutableMethod.applyGeminiTranslationEntryDelegate(call: RuntimeCallId) {
    val abi = RuntimeAbiCatalog.abi(call)
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(abi.reference) }
    if (existing > 0) {
        check(existing == 1) {
            "Malformed Gemini Translation entry delegate in $definingClass->$name"
        }
        return
    }
    addInstructions(0, RuntimeCallEmitter.invoke(call, "p0 .. p1"))
}
