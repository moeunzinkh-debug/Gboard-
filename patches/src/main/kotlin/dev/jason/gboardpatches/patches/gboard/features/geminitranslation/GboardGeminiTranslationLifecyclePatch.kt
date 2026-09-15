package dev.jason.gboardpatches.patches.gboard.features.geminitranslation

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import dev.jason.gboardpatches.patches.gboard.shared.GboardFieldTarget
import dev.jason.gboardpatches.patches.gboard.shared.GboardMethodTarget
import dev.jason.gboardpatches.patches.gboard.shared.addHelperMethodIfMissing
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesExtensionCarrierPatch
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

internal val gboardGeminiTranslationLifecyclePatch = bytecodePatch(
    description = "在 18.0.3 onStartInputView 加入薄 delegate，" +
        "把目前的 InputConnection 與鍵盤 View 交給 Gemini 翻譯列。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)
    dependsOn(gboardPatchesExtensionCarrierPatch)

    execute {
        // The bar docks into the frame that holds Gboard's input view, so the delegate hands the
        // runtime that view directly instead of reflecting into framework internals.
        val inputView = GboardGeminiTranslationTargets.inputView.resolve(this)
        check(isGeminiTranslationHostCompatible(inputView.type) { type ->
            runCatching { mutableClass(type).superclass }.getOrNull()
        }) {
            "Gemini translation host ${inputView.type} must inherit from $FRAME_LAYOUT_TYPE"
        }

        val onStartInputView =
            findMutableMethodOrThrow(GboardGeminiTranslationTargets.onStartInputView)
        addHelperMethodIfMissing(
            classType = GboardGeminiTranslationTargets.onStartInputView.ownerDescriptor,
            name = ENTRY_HELPER_NAME,
            parameterTypes = listOf(EDITOR_INFO_TYPE),
            returnType = "V",
            accessFlags = AccessFlags.PRIVATE.value or AccessFlags.FINAL.value,
            registerCount = ENTRY_HELPER_REGISTER_COUNT,
            body = entryHelperBody(),
        )
        onStartInputView.applyGeminiTranslationEntryDelegate()
    }
}

internal object GboardGeminiTranslationTargets {
    val inputView = GboardFieldTarget(
        classType = "Loup;",
        name = "j",
        type = "Lcom/google/android/libraries/inputmethod/inputview/InputView;",
    )
    val onStartInputView = GboardMethodTarget(
        classType = "Loup;",
        name = "onStartInputView",
        parameterTypes = listOf("Landroid/view/inputmethod/EditorInfo;", "Z"),
        returnType = "V",
    )
}

internal fun isGeminiTranslationHostCompatible(
    startType: String,
    superclassOf: (String) -> String?,
): Boolean {
    var current: String? = startType
    val visited = mutableSetOf<String>()
    while (current != null && visited.add(current)) {
        if (current == FRAME_LAYOUT_TYPE) {
            return true
        }
        current = superclassOf(current)
    }
    return false
}

/** Installs (or reuses) the one-line `onStartInputView` entry delegate, idempotently. */
private fun MutableMethod.applyGeminiTranslationEntryDelegate() {
    val helperReference =
        "${GboardGeminiTranslationTargets.onStartInputView.ownerDescriptor}" +
            "->$ENTRY_HELPER_NAME($EDITOR_INFO_TYPE)V"
    val instructions = implementation?.instructions
        ?: error("No instructions in $definingClass->$name")
    val existing = instructions.count { it.isMethodReference(helperReference) }
    if (existing > 0) {
        check(existing == 1 && instructions[0].isMethodReference(helperReference)) {
            "Malformed Gemini Translation entry delegate in $definingClass->$name"
        }
        return
    }
    addInstructions(0, "invoke-direct {p0, p1}, $helperReference")
}

private fun entryHelperBody(): String = """
    iget-object v0, p0, ${GboardGeminiTranslationTargets.inputView.reference}

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.GEMINI_TRANSLATION_RUNTIME_ON_INPUT_VIEW_STARTING,
        "p0, v0, p1",
    )}

    return-void
""".trimIndent()

private const val FRAME_LAYOUT_TYPE = "Landroid/widget/FrameLayout;"
private const val EDITOR_INFO_TYPE = "Landroid/view/inputmethod/EditorInfo;"
private const val ENTRY_HELPER_NAME = "gboardPatchesGeminiTranslationEntry"
private const val ENTRY_HELPER_REGISTER_COUNT = 3
