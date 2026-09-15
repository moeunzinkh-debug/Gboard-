package dev.jason.gboardpatches.patches.gboard.features.geminitranslation

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
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
    val target = GboardGeminiTranslationTargets.onStartInputView
    val helperReference = "${target.ownerDescriptor}->$ENTRY_HELPER_NAME($EDITOR_INFO_TYPE)V"
    val implementation = implementation
        ?: error("No instructions in $definingClass->$name")
    val instructions = implementation.instructions
    val existing = instructions.count { it.isMethodReference(helperReference) }
    if (existing > 0) {
        check(existing == 1 && instructions[0].isMethodReference(helperReference)) {
            "Malformed Gemini Translation entry delegate in $definingClass->$name"
        }
        return
    }
    // The delegate passes (this, EditorInfo), so the target must be the 18.0.3 instance
    // override with at least one register per parameter word. Reject anything else with a
    // message that names the mismatch instead of a bare "Collection is empty."
    val isStatic = AccessFlags.STATIC.isSet(accessFlags)
    val parameterWords = 1 + target.parameterTypes.sumOf { if (it == "J" || it == "D") 2 else 1 }
    val registerCount = implementation.registerCount
    check(!isStatic && parameterTypes.map { it.toString() } == target.parameterTypes) {
        "Cannot install the Gemini Translation entry delegate: expected the instance method " +
            "${target.reference} but found $definingClass->$name" +
            "(${parameterTypes.joinToString("")})$returnType" +
            (if (isStatic) " which is static" else "") + "."
    }
    check(registerCount >= parameterWords) {
        "Cannot install the Gemini Translation entry delegate into ${target.reference}: " +
            "registerCount=$registerCount is smaller than the $parameterWords parameter words. " +
            "The Gboard APK does not have the expected 18.0.3 shape — use the exact build " +
            "18.0.3.954559732-release-arm64-v8a with no other Gboard patch source enabled."
    }
    // Build the call programmatically instead of compiling smali text. InlineSmaliCompiler
    // mirrors this method into a synthetic template, and on some APK/patcher combinations
    // that compile silently yields zero methods, aborting the whole session with
    // "Collection is empty." The instruction below encodes exactly the same call.
    val thisRegister = registerCount - parameterWords
    val editorInfoRegister = thisRegister + 1
    val reference = ImmutableMethodReference(
        target.ownerDescriptor,
        ENTRY_HELPER_NAME,
        listOf(EDITOR_INFO_TYPE),
        "V",
    )
    val call: BuilderInstruction = if (editorInfoRegister <= MAX_35C_REGISTER) {
        BuilderInstruction35c(
            Opcode.INVOKE_DIRECT,
            ENTRY_DELEGATE_REGISTER_COUNT,
            thisRegister,
            editorInfoRegister,
            0,
            0,
            0,
            reference,
        )
    } else {
        BuilderInstruction3rc(
            Opcode.INVOKE_DIRECT_RANGE,
            thisRegister,
            ENTRY_DELEGATE_REGISTER_COUNT,
            reference,
        )
    }
    addInstructions(0, listOf(call))
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
private const val ENTRY_DELEGATE_REGISTER_COUNT = 2
private const val MAX_35C_REGISTER = 15
