package dev.jason.gboardpatches.patches.gboard.features.addsymbols

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.findMutableMethodOrThrow
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId

private const val EMOTICON_ITEM_CLICK_CONSUMER_CLASS = "Liyd;"
private const val EMOTICON_HISTORY_MANAGER_CLASS = "Lgjl;"
private const val EMOTICON_KEYBOARD_CLASS =
    "Lcom/google/android/apps/inputmethod/libs/search/emoticon/EmoticonKeyboardM2;"
private const val EMOTICON_HISTORY_FIELD = "b"
private const val EMOTICON_HISTORY_WRITE_METHOD = "b"
private const val EMOTICON_HISTORY_WRITE_REFERENCE =
    "$EMOTICON_HISTORY_MANAGER_CLASS->$EMOTICON_HISTORY_WRITE_METHOD(Ljava/lang/String;)V"
private const val EMOTICON_HISTORY_FIELD_REFERENCE =
    "$EMOTICON_KEYBOARD_CLASS->$EMOTICON_HISTORY_FIELD:$EMOTICON_HISTORY_MANAGER_CLASS"
private const val TARGET_REFERENCE =
    "$EMOTICON_ITEM_CLICK_CONSUMER_CLASS->accept(Ljava/lang/Object;)V"

/**
 * How far above the history write the `EmoticonKeyboardM2.b` field read may sit.
 *
 * Gboard 18.0.3 reads the field immediately before the call, so the distance is 1 for a stock
 * target. A larger distance means another patch already inserted instructions between the two.
 */
private const val HISTORY_FIELD_READ_SCAN_WINDOW = 8

private val HISTORY_WRITE_RUNTIME_CALL = RuntimeCallId.ADD_SYMBOLS_RUNTIME_INTERCEPT_HISTORY_WRITE
private val HISTORY_WRITE_RUNTIME_REFERENCE =
    RuntimeAbiCatalog.abi(HISTORY_WRITE_RUNTIME_CALL).reference

/**
 * What [TARGET_REFERENCE] looks like when the history delegate is classified.
 *
 * @property ownGuardPresent an `interceptHistoryWrite` call is already wired into the method,
 *   whether by this source or by another enabled source shipping the same feature.
 * @property historyFieldReadDistance number of instructions between the `EmoticonKeyboardM2.b`
 *   read and the history write, or null when no such read sits within
 *   [HISTORY_FIELD_READ_SCAN_WINDOW] above the write.
 * @property foreignInvokeBeforeWrite an invoke sits between that field read and the write, so
 *   something else already guards the write even though its runtime call is not ours.
 */
internal data class EmoticonHistoryWriteShape(
    val ownGuardPresent: Boolean,
    val historyFieldReadDistance: Int?,
    val foreignInvokeBeforeWrite: Boolean,
)

/**
 * Decides whether the history delegate still has to be installed.
 *
 * A target that is already guarded - by this source on a re-run, or by a second enabled patch
 * source that ships the same `Custom Symbols` feature - is reported as PATCHED so the
 * transformation is skipped. Installing a second delegate there would duplicate the
 * `jasondev_history_handled` label and clobber the registers the first guard still needs.
 *
 * The retired implementation required the `EmoticonKeyboardM2.b` read to sit immediately before
 * the write, so a delegate inserted by another source made it report "Expected one
 * EmoticonKeyboardM2 history write, found 0" and abort the whole patching session.
 */
internal fun classifyEmoticonHistoryWriteShape(
    shape: EmoticonHistoryWriteShape,
): VerifiedTransformationState = when {
    shape.ownGuardPresent -> VerifiedTransformationState.PATCHED
    shape.historyFieldReadDistance == null -> VerifiedTransformationState.MALFORMED
    shape.foreignInvokeBeforeWrite -> VerifiedTransformationState.PATCHED
    else -> VerifiedTransformationState.STOCK
}

internal val gboardZhuyinCustomSymbolsHistoryPatch = bytecodePatch(
    description = "移植 add-symbols 的 recent/history namespace 隔離。",
) {
    dependsOn(gboardZhuyinCustomSymbolsExtensionPatch)

    execute {
        val clickMethod = findMutableMethodOrThrow(
            classType = EMOTICON_ITEM_CLICK_CONSUMER_CLASS,
            name = "accept",
            returnType = "V",
            parameterTypes = listOf("Ljava/lang/Object;"),
        )
        clickMethod.applyZhuyinCustomSymbolsHistoryDelegate()
    }
}

internal fun MutableMethod.applyZhuyinCustomSymbolsHistoryDelegate(): MutableMethod = applyVerified(
    VerifiedTransformationPlan(
        targetName = TARGET_REFERENCE,
        classify = MutableMethod::classifyEmoticonHistoryDelegate,
        mutate = { method ->
            val implementation = checkNotNull(method.implementation) {
                "No instructions available in $TARGET_REFERENCE"
            }
            val instructions = implementation.instructions
            val historyWriteIndex = instructions.singleEmoticonHistoryWriteIndex()
            val historyWrite = instructions[historyWriteIndex] as? FiveRegisterInstruction
                ?: error("$EMOTICON_HISTORY_WRITE_REFERENCE is not a five-register invoke")
            val historyManagerRegister = smaliRegisterName(
                historyWrite.registerC,
                implementation.registerCount,
                parameterRegisterCount = 2,
            )
            val symbolRegister = smaliRegisterName(
                historyWrite.registerD,
                implementation.registerCount,
                parameterRegisterCount = 2,
            )
            method.addInstructionsWithLabels(
                historyWriteIndex,
                HISTORY_WRITE_DELEGATE.format(historyManagerRegister, symbolRegister),
                ExternalLabel("jasondev_history_handled", instructions[historyWriteIndex + 1]),
            )
            method
        },
    ),
)

private fun MutableMethod.classifyEmoticonHistoryDelegate(): VerifiedTransformationState {
    val instructions = implementation?.instructions
        ?: error("No instructions available in $TARGET_REFERENCE")
    return classifyEmoticonHistoryWriteShape(instructions.emoticonHistoryWriteShape())
}

private fun List<Instruction>.emoticonHistoryWriteShape(): EmoticonHistoryWriteShape {
    if (any { instruction -> instruction.isMethodReference(HISTORY_WRITE_RUNTIME_REFERENCE) }) {
        return EmoticonHistoryWriteShape(
            ownGuardPresent = true,
            historyFieldReadDistance = null,
            foreignInvokeBeforeWrite = false,
        )
    }

    val (historyWriteIndex, fieldReadIndex) = singleEmoticonHistoryWriteSite()
    return EmoticonHistoryWriteShape(
        ownGuardPresent = false,
        historyFieldReadDistance = historyWriteIndex - fieldReadIndex,
        foreignInvokeBeforeWrite = hasInvokeBetween(fieldReadIndex, historyWriteIndex),
    )
}

/**
 * Index of the single history write in `Liyd.accept(Object)`.
 *
 * `Liyd.accept(Object)` invokes `gjl.b(String)V` from several switch cases (emoji, sticker and
 * symbol clicks all record history), so matching the method reference alone finds more than one
 * call and made the retired implementation abort with "found 3" on stock Gboard 18.0.3. The
 * history write this delegate intercepts is the call whose receiver register is loaded from the
 * `EmoticonKeyboardM2.b` field.
 */
private fun List<Instruction>.singleEmoticonHistoryWriteIndex(): Int =
    singleEmoticonHistoryWriteSite().first

/**
 * The history write and the `iget-object` that loads its receiver.
 *
 * A candidate is a `gjl.b(String)V` call whose receiver register is loaded from
 * [EMOTICON_HISTORY_FIELD_REFERENCE] within [HISTORY_FIELD_READ_SCAN_WINDOW] above it. Stock
 * Gboard 18.0.3 reads that field immediately before the write (distance 1); when several calls
 * read the field into their receiver, the call whose read sits closest is the history write.
 */
private fun List<Instruction>.singleEmoticonHistoryWriteSite(): Pair<Int, Int> {
    val writeIndices = indices.filter { index ->
        this[index].isMethodReference(EMOTICON_HISTORY_WRITE_REFERENCE)
    }
    check(writeIndices.isNotEmpty()) {
        "$TARGET_REFERENCE does not call $EMOTICON_HISTORY_WRITE_REFERENCE"
    }
    val writeSites = writeIndices.mapNotNull { writeIndex ->
        emoticonHistoryFieldReadIndex(writeIndex)?.let { fieldReadIndex ->
            writeIndex to fieldReadIndex
        }
    }
    check(writeSites.isNotEmpty()) {
        "No EmoticonKeyboardM2 history write found in $TARGET_REFERENCE: none of the " +
            "$EMOTICON_HISTORY_WRITE_REFERENCE calls reads $EMOTICON_HISTORY_FIELD_REFERENCE " +
            "into its receiver."
    }
    return writeSites.minByOrNull { (writeIndex, fieldReadIndex) ->
        writeIndex - fieldReadIndex
    } ?: error("Unreachable: $TARGET_REFERENCE has no history write")
}

/**
 * Index of the `iget-object` that loads the history manager consumed by the write at
 * [historyWriteIndex], or null when no such read sits within [HISTORY_FIELD_READ_SCAN_WINDOW]
 * above it. Matching on the receiver register keeps the lookup precise while tolerating
 * instructions another patch inserted between the read and the write.
 */
private fun List<Instruction>.emoticonHistoryFieldReadIndex(historyWriteIndex: Int): Int? {
    val receiverRegister = (this[historyWriteIndex] as? FiveRegisterInstruction)?.registerC
        ?: return null
    val firstCandidate = (historyWriteIndex - HISTORY_FIELD_READ_SCAN_WINDOW).coerceAtLeast(0)
    return (historyWriteIndex - 1 downTo firstCandidate).firstOrNull { index ->
        val instruction = this[index]
        instruction.isOpcode("IGET_OBJECT") &&
            instruction.isFieldReference(EMOTICON_HISTORY_FIELD_REFERENCE) &&
            (instruction as? OneRegisterInstruction)?.registerA == receiverRegister
    }
}

private fun List<Instruction>.hasInvokeBetween(fieldReadIndex: Int, historyWriteIndex: Int): Boolean =
    (fieldReadIndex + 1 until historyWriteIndex).any { index ->
        this[index].opcode.name.uppercase().startsWith("INVOKE_")
    }

private val HISTORY_WRITE_DELEGATE = """
    move-object/from16 v5, p0

    ${RuntimeCallEmitter.invoke(
        RuntimeCallId.ADD_SYMBOLS_RUNTIME_INTERCEPT_HISTORY_WRITE,
        "v5, %s, %s",
    )}

    move-result p1

    if-nez p1, :jasondev_history_handled
""".trimIndent()

private fun smaliRegisterName(
    register: Int,
    registerCount: Int,
    parameterRegisterCount: Int,
): String {
    val firstParameterRegister = registerCount - parameterRegisterCount
    return if (register < firstParameterRegister) {
        "v$register"
    } else {
        "p${register - firstParameterRegister}"
    }
}
