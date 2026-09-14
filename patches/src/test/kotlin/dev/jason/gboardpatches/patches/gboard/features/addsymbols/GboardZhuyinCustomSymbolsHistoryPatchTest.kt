package dev.jason.gboardpatches.patches.gboard.features.addsymbols

import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardZhuyinCustomSymbolsHistoryPatchTest {
    @Test
    fun `stock click consumer is classified for the history delegate`() {
        assertEquals(
            VerifiedTransformationState.STOCK,
            classifyEmoticonHistoryWriteShape(
                EmoticonHistoryWriteShape(
                    ownGuardPresent = false,
                    historyFieldReadDistance = 1,
                    foreignInvokeBeforeWrite = false,
                ),
            ),
        )
    }

    @Test
    fun `history write displaced by inserted instructions is still patched`() {
        // Gboard 18.0.3 reads EmoticonKeyboardM2.b immediately before the Lgjl;->b write, but an
        // earlier patch can push instructions between the two. The retired adjacency test reported
        // "Expected one EmoticonKeyboardM2 history write, found 0" here and aborted the session.
        assertEquals(
            VerifiedTransformationState.STOCK,
            classifyEmoticonHistoryWriteShape(
                EmoticonHistoryWriteShape(
                    ownGuardPresent = false,
                    historyFieldReadDistance = 5,
                    foreignInvokeBeforeWrite = false,
                ),
            ),
        )
    }

    @Test
    fun `already installed history guard is left untouched`() {
        // A second enabled source shipping the same Custom Symbols feature installs the very same
        // interceptHistoryWrite guard. Adding ours would duplicate the jasondev_history_handled
        // label and clobber registers the first guard still needs.
        assertEquals(
            VerifiedTransformationState.PATCHED,
            classifyEmoticonHistoryWriteShape(
                EmoticonHistoryWriteShape(
                    ownGuardPresent = true,
                    historyFieldReadDistance = null,
                    foreignInvokeBeforeWrite = false,
                ),
            ),
        )
    }

    @Test
    fun `foreign guard between the field read and the write is left untouched`() {
        assertEquals(
            VerifiedTransformationState.PATCHED,
            classifyEmoticonHistoryWriteShape(
                EmoticonHistoryWriteShape(
                    ownGuardPresent = false,
                    historyFieldReadDistance = 5,
                    foreignInvokeBeforeWrite = true,
                ),
            ),
        )
    }

    @Test
    fun `click consumer without the history field read is malformed`() {
        assertEquals(
            VerifiedTransformationState.MALFORMED,
            classifyEmoticonHistoryWriteShape(
                EmoticonHistoryWriteShape(
                    ownGuardPresent = false,
                    historyFieldReadDistance = null,
                    foreignInvokeBeforeWrite = false,
                ),
            ),
        )
    }

    @Test
    fun `history write is identified by its EmoticonKeyboardM2 b field read not by call count`() {
        // Gboard 18.0.3's Liyd.accept(Object) calls Lgjl;->b(String)V from several switch cases
        // (emoji, sticker and symbol clicks all record history), so counting method-reference
        // matches reports three "history writes" and aborts with "Expected one
        // EmoticonKeyboardM2 history write, found 3". The write this delegate targets is the
        // call whose receiver register is loaded from EmoticonKeyboardM2.b.
        val history = readPatch("GboardZhuyinCustomSymbolsHistoryPatch.kt")

        assertTrue(history.contains("val writeIndices = indices.filter"))
        assertTrue(history.contains("singleEmoticonHistoryWriteSite"))
        assertTrue(history.contains("emoticonHistoryFieldReadIndex(writeIndex)"))
        assertTrue(history.contains("check(writeIndices.isNotEmpty())"))
        assertTrue(history.contains("check(writeSites.isNotEmpty())"))
        assertTrue(history.contains("minByOrNull"))
    }

    @Test
    fun `history patch verifies its transformation and keeps its 1803 descriptors`() {
        val history = readPatch("GboardZhuyinCustomSymbolsHistoryPatch.kt")

        assertTrue(history.contains("applyVerified("))
        assertTrue(history.contains("VerifiedTransformationPlan("))
        assertTrue(history.contains("classifyEmoticonHistoryWriteShape"))
        assertTrue(history.contains("HISTORY_FIELD_READ_SCAN_WINDOW"))
        assertTrue(history.contains("RuntimeAbiCatalog.abi(HISTORY_WRITE_RUNTIME_CALL).reference"))
        // The adjacency lookup that made a second patch source abort the whole session.
        assertFalse(history.contains("instructions[index - 1]"))
    }

    private fun readPatch(fileName: String): String = Files.readString(
        Path.of("src/main/kotlin/dev/jason/gboardpatches/patches/gboard/features/addsymbols/$fileName")
    )
}
