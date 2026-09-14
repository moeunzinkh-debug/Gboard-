package dev.jason.gboardpatches.patches.gboard.registry

import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.BLUETOOTH_MICROPHONE_FEATURE_MARKER
import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.gboardBluetoothMicrophoneFeatureMarkerPatch
import dev.jason.gboardpatches.patches.gboard.features.bluetoothmicrophone.gboardBluetoothMicrophoneFlagValuePatch
import dev.jason.gboardpatches.patches.gboard.shared.gboardPatchesSettingsPatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardBluetoothMicrophonePatchContractTest {
    @Test
    fun publicPatchIsIndependentAndExact1803Only() {
        val patch = gboardBluetoothMicrophonePatch
        assertEquals("Use Bluetooth Microphone", patch.name)
        assertEquals(BLUETOOTH_MICROPHONE_DESCRIPTION, patch.description)
        assertTrue(patch.default)
        assertEquals(
            listOf(
                gboardPatchesSettingsPatch,
                gboardBluetoothMicrophoneFeatureMarkerPatch,
                gboardBluetoothMicrophoneFlagValuePatch,
            ),
            patch.dependencies.toList(),
        )
        assertFalse(patch.dependencies.any { dependency ->
            dependency.toString().contains("AdvancedVoice", ignoreCase = true) ||
                dependency.toString().contains("LongPressQuickActions", ignoreCase = true)
        })
        assertEquals(1, gboardBluetoothMicrophoneFlagValuePatch.dependencies.size)
        assertEquals(
            "18.0.3.954559732-release-arm64-v8a",
            patch.compatibility!!.single().targets.single().version,
        )
        assertEquals(
            "dev.jason.gboardpatches.feature.bluetooth_microphone",
            BLUETOOTH_MICROPHONE_FEATURE_MARKER,
        )
    }

    @Test
    fun generatedInventoryContainsExactlyOneBluetoothMicrophoneRowWithoutOptions() {
        val rows = generatedPublishedPatches()
            .filter { it.get("name").asString == "Use Bluetooth Microphone" }

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(BLUETOOTH_MICROPHONE_DESCRIPTION, row.get("description").asString)
        assertTrue(row.get("use").asBoolean)
        assertEquals(0, row.getAsJsonArray("options").size())
        assertEquals(
            listOf("18.0.3.954559732-release-arm64-v8a"),
            row.getAsJsonObject("compatiblePackages")
                .getAsJsonArray("com.google.android.inputmethod.latin")
                .map { it.asString },
        )
    }

    @Test
    fun readmeUsesConciseBluetoothMicrophoneDescription() {
        val patch = gboardBluetoothMicrophonePatch

        // The README documents every patch as one table row carrying the concise headline line of
        // its bilingual description, not the English prose of the retired <details> layout.
        assertEquals("Use Bluetooth Microphone", patch.name)
        assertEquals(BLUETOOTH_MICROPHONE_DESCRIPTION, patch.description)
        assertReadmeListsPatch(patch.name, BLUETOOTH_MICROPHONE_DESCRIPTION)
    }

    private companion object {
        const val BLUETOOTH_MICROPHONE_DESCRIPTION =
            "啟用 語音輸入 -> 使用藍芽麥克風\n" +
                "Enable Voice typing -> Use Bluetooth microphone."
    }
}
