package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorSpeechControllerContractTest {
    private val controllerKt = File("src/main/java/com/golink/busiscoming/service/BusMonitorSpeechController.kt").readText()
    private val bottomSheetKt = File("src/main/java/com/golink/busiscoming/ui/main/MonitorSettingsBottomSheet.kt").readText()
    private val bottomSheetLayout =
        File("src/main/res/layout/bottom_sheet_monitor_settings.xml").readText()

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
        assertTrue(bottomSheetLayout.contains("@string/monitor_voice"))
        assertTrue(bottomSheetLayout.contains("android:id=\"@+id/monitor_voice_switch\""))
        assertTrue(bottomSheetLayout.contains("android:checked=\"true\""))
        assertFalse(bottomSheetKt.contains("preview"))
        assertFalse(bottomSheetKt.contains("ACTION_TTS_SETTINGS"))
        assertFalse(bottomSheetKt.contains("ACTION_INSTALL_TTS_DATA"))
    }
}
