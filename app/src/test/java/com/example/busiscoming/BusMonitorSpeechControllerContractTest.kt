package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorSpeechControllerContractTest {
    private val controllerKt = File("src/main/java/com/example/busiscoming/service/BusMonitorSpeechController.kt").readText()
    private val bottomSheetKt = File("src/main/java/com/example/busiscoming/ui/main/MonitorSettingsBottomSheet.kt").readText()

    @Test
    fun speechControllerUsesUtteranceCallbacksAndDiagnosticFailures() {
        assertTrue(controllerKt.contains("UtteranceProgressListener"))
        assertTrue(controllerKt.contains("onStart"))
        assertTrue(controllerKt.contains("onDone"))
        assertTrue(controllerKt.contains("PLAYBACK_TIMEOUT"))
        assertTrue(controllerKt.contains("LANGUAGE_MISSING_DATA"))
        assertTrue(controllerKt.contains("LANGUAGE_NOT_SUPPORTED"))
    }

    @Test
    fun previewAndMonitorUseSeparateAudioStrategies() {
        assertTrue(controllerKt.contains("BusMonitorSpeechAudioMode.PREVIEW -> AudioAttributes.USAGE_MEDIA"))
        assertTrue(controllerKt.contains("BusMonitorSpeechAudioMode.MONITOR -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE"))
        assertTrue(controllerKt.contains("requestAudioFocus(mode)"))
        assertTrue(controllerKt.contains("AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"))
        assertTrue(controllerKt.contains("abandonAudioFocusRequest"))
        assertTrue(controllerKt.contains("AUDIO_FOCUS_DENIED"))
    }

    @Test
    fun monitorSettingsKeepsVoiceSwitchButRemovesPreviewEntry() {
        assertTrue(bottomSheetKt.contains("text = \"語音播報\""))
        assertTrue(bottomSheetKt.contains("isChecked = true"))
        assertFalse(bottomSheetKt.contains("試聽語音"))
        assertFalse(bottomSheetKt.contains("設定系統語音"))
        assertFalse(bottomSheetKt.contains("ACTION_INSTALL_TTS_DATA"))
    }
}
