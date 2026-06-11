package com.example.busiscoming

import com.example.busiscoming.data.model.BusMonitorSpeechFormatter
import com.example.busiscoming.data.model.BusMonitorSpeechPolicy
import com.example.busiscoming.data.model.BusMonitorNotificationFormatter
import com.example.busiscoming.data.model.BusMonitorRefreshPolicy
import com.example.busiscoming.data.model.BusMonitorSessionPolicy
import com.example.busiscoming.data.model.BusMonitorSessionSnapshotCodec
import com.example.busiscoming.data.model.BusMonitorStateEvaluator
import com.example.busiscoming.data.model.BusMonitorStatus
import com.example.busiscoming.data.model.BusMonitorStopPolicy
import com.example.busiscoming.data.model.BusMonitorStopTargetSource
import com.example.busiscoming.data.model.BusMonitorTtsLanguagePolicy
import com.example.busiscoming.data.model.BusMonitorTtsLanguageSelection
import com.example.busiscoming.data.model.BusMonitorTtsLanguageUnavailableReason
import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.WalkingScenarioModifier
import com.example.busiscoming.data.model.WalkingSpeedPreset
import com.example.busiscoming.data.model.WalkingTimeCalculator
import com.example.busiscoming.service.BusMonitorSpeechFailureReason
import com.example.busiscoming.service.BusMonitorSpeechResult
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusMonitorModelsTest {
    @Test
    fun walkingEstimateUsesMaxDistanceEstimateAndUserAdjustmentThenAddsModifiers() {
        val estimate = WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = 420,
            straightLineDistanceMeters = 350,
            userAdjustedMinutes = 7,
            speedPreset = WalkingSpeedPreset.NORMAL,
            modifiers = setOf(WalkingScenarioModifier.ELEVATOR, WalkingScenarioModifier.CROSSING)
        )

        assertEquals(6, estimate.interfaceDistanceMinutes)
        assertEquals(5, estimate.straightLineMinutes)
        assertEquals(11, estimate.finalMinutes)
    }

    @Test
    fun rainReducesEffectiveWalkingSpeed() {
        val normal = WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = 400,
            straightLineDistanceMeters = null,
            userAdjustedMinutes = 1,
            speedPreset = WalkingSpeedPreset.NORMAL,
            modifiers = emptySet()
        )
        val rain = WalkingTimeCalculator.estimate(
            interfaceDistanceMeters = 400,
            straightLineDistanceMeters = null,
            userAdjustedMinutes = 1,
            speedPreset = WalkingSpeedPreset.NORMAL,
            modifiers = setOf(WalkingScenarioModifier.RAIN)
        )

        assertEquals(5, normal.finalMinutes)
        assertEquals(6, rain.finalMinutes)
    }

    @Test
    fun evaluatesMonitorStateThresholds() {
        assertEquals(BusMonitorStatus.PREPARE, BusMonitorStateEvaluator.evaluate(12, 8))
        assertEquals(BusMonitorStatus.LEAVE_NOW, BusMonitorStateEvaluator.evaluate(10, 8))
        assertEquals(BusMonitorStatus.LATE, BusMonitorStateEvaluator.evaluate(8, 8))
    }

    @Test
    fun formatsSpeechForState() {
        assertEquals(
            "當前汽車到站剩餘 10 分鐘，請立即出門",
            BusMonitorSpeechFormatter.phrase(10, BusMonitorStatus.LEAVE_NOW)
        )
        assertEquals(
            "當前汽車到站剩餘 7 分鐘，請立即出門",
            BusMonitorSpeechFormatter.previewPhrase()
        )
    }

    @Test
    fun speaksOnlyWhenMonitorStateChanges() {
        assertTrue(BusMonitorSpeechPolicy.shouldSpeak(null, BusMonitorStatus.PREPARE))
        assertFalse(BusMonitorSpeechPolicy.shouldSpeak(BusMonitorStatus.PREPARE, BusMonitorStatus.PREPARE))
        assertTrue(BusMonitorSpeechPolicy.shouldSpeak(BusMonitorStatus.PREPARE, BusMonitorStatus.LEAVE_NOW))
    }

    @Test
    fun stopsAfterSecondEtaOrFirstEtaFallbackWindow() {
        val now = 10_000L

        assertFalse(
            BusMonitorStopPolicy.shouldAutoStop(
                nowMillis = now,
                stopAtMillis = 10_001L
            )
        )
        assertTrue(
            BusMonitorStopPolicy.shouldAutoStop(
                nowMillis = now,
                stopAtMillis = 10_000L
            )
        )
        assertFalse(BusMonitorStopPolicy.shouldAutoStop(nowMillis = now, stopAtMillis = null))
    }

    @Test
    fun manualStopRequestAlwaysStopsSession() {
        assertTrue(BusMonitorStopPolicy.shouldStopManually(manualStopRequested = true))
        assertFalse(BusMonitorStopPolicy.shouldStopManually(manualStopRequested = false))
    }

    @Test
    fun formatsMonitorNotificationSuccessAndFailureStates() {
        assertEquals(
            "82X · 準備出門",
            BusMonitorNotificationFormatter.title("82X → 102", BusMonitorStatus.PREPARE)
        )
        assertEquals(
            "82X · 立即出門",
            BusMonitorNotificationFormatter.title("82X", BusMonitorStatus.LEAVE_NOW)
        )
        assertEquals(
            "82X · 快遲到了",
            BusMonitorNotificationFormatter.title("82X", BusMonitorStatus.LATE)
        )
        assertEquals(
            "剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新",
            BusMonitorNotificationFormatter.bodyText(
                firstEtaMinutes = 5,
                nextEtaMinutes = 25,
                walkingMinutes = 3,
                updatedAtText = "12:45"
            )
        )
        assertEquals(
            "已過 1 分鐘 · 車 2 分鐘到 · 步行 3 分鐘 · 12:45 更新",
            BusMonitorNotificationFormatter.bodyText(
                firstEtaMinutes = 2,
                nextEtaMinutes = null,
                walkingMinutes = 3,
                updatedAtText = "12:45"
            )
        )
        assertEquals(
            BusMonitorNotificationFormatter.bodyText(
                firstEtaMinutes = 5,
                nextEtaMinutes = 25,
                walkingMinutes = 3,
                updatedAtText = "12:45"
            ),
            BusMonitorNotificationFormatter.successText(
                firstEtaMinutes = 5,
                nextEtaMinutes = 25,
                walkingMinutes = 3,
                updatedAtText = "12:45"
            )
        )
        assertEquals(
            "資料延遲 · 剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新 · 更新失敗 2 次",
            BusMonitorNotificationFormatter.failureText(
                lastSuccessfulNotificationText = "剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新",
                failureCount = 2
            )
        )
        assertEquals(
            "資料延遲 · 暫無 ETA，1 分鐘後重試",
            BusMonitorNotificationFormatter.failureText(
                lastSuccessfulNotificationText = null,
                failureCount = 1
            )
        )
    }

    @Test
    fun sessionSnapshotCodecRoundTripsStableMonitorFields() {
        val session = BusMonitorSessionPolicy.newSession(
            nowMillis = 1_000L,
            routeName = "A12",
            walkingMinutes = 8,
            voiceEnabled = true,
            query = etaQuery()
        )
        val refreshed = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = session,
            nowMillis = 2_000L,
            status = BusMonitorStatus.LEAVE_NOW,
            lastSpokenStatus = BusMonitorStatus.LEAVE_NOW,
            notificationText = "立即出門 · 剩餘 10 分鐘",
            firstEtaMillis = 11_000L,
            secondEtaMillis = 21_000L
        )

        val decoded = BusMonitorSessionSnapshotCodec.decode(
            BusMonitorSessionSnapshotCodec.encode(refreshed)
        )

        assertEquals(refreshed, decoded)
    }

    @Test
    fun firstSuccessfulRefreshFixesSecondEtaStopTarget() {
        val session = BusMonitorSessionPolicy.newSession(
            nowMillis = 1_000L,
            routeName = "A12",
            walkingMinutes = 8,
            voiceEnabled = true,
            query = etaQuery()
        )

        val refreshed = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = session,
            nowMillis = 2_000L,
            status = BusMonitorStatus.LEAVE_NOW,
            lastSpokenStatus = BusMonitorStatus.LEAVE_NOW,
            notificationText = "立即出門",
            firstEtaMillis = 11_000L,
            secondEtaMillis = 21_000L
        )

        assertEquals(21_000L, refreshed.stopAtMillis)
        assertEquals(BusMonitorStopTargetSource.SECOND_ETA, refreshed.stopTargetSource)
    }

    @Test
    fun firstSuccessfulRefreshUsesFirstEtaFallbackStopTargetWhenSecondEtaMissing() {
        val session = BusMonitorSessionPolicy.newSession(
            nowMillis = 1_000L,
            routeName = "A12",
            walkingMinutes = 8,
            voiceEnabled = true,
            query = etaQuery()
        )

        val refreshed = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = session,
            nowMillis = 2_000L,
            status = BusMonitorStatus.LEAVE_NOW,
            lastSpokenStatus = BusMonitorStatus.LEAVE_NOW,
            notificationText = "立即出門",
            firstEtaMillis = 11_000L,
            secondEtaMillis = null
        )

        assertEquals(131_000L, refreshed.stopAtMillis)
        assertEquals(BusMonitorStopTargetSource.FIRST_ETA_FALLBACK, refreshed.stopTargetSource)
    }

    @Test
    fun laterSuccessfulRefreshDoesNotExtendFixedStopTarget() {
        val session = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = BusMonitorSessionPolicy.newSession(
                nowMillis = 1_000L,
                routeName = "A12",
                walkingMinutes = 8,
                voiceEnabled = true,
                query = etaQuery()
            ),
            nowMillis = 2_000L,
            status = BusMonitorStatus.LEAVE_NOW,
            lastSpokenStatus = BusMonitorStatus.LEAVE_NOW,
            notificationText = "立即出門",
            firstEtaMillis = 11_000L,
            secondEtaMillis = 21_000L
        )

        val refreshedAgain = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = session,
            nowMillis = 3_000L,
            status = BusMonitorStatus.PREPARE,
            lastSpokenStatus = BusMonitorStatus.PREPARE,
            notificationText = "準備出門",
            firstEtaMillis = 30_000L,
            secondEtaMillis = 60_000L
        )

        assertEquals(21_000L, refreshedAgain.stopAtMillis)
        assertEquals(BusMonitorStopTargetSource.SECOND_ETA, refreshedAgain.stopTargetSource)
    }

    @Test
    fun sessionRestoreClearsExpiredInterruptedOrPassedStopDeadline() {
        val session = BusMonitorSessionPolicy.newSession(
            nowMillis = 1_000L,
            routeName = "A12",
            walkingMinutes = 8,
            voiceEnabled = true,
            query = etaQuery()
        )
        val refreshed = BusMonitorSessionPolicy.recordSuccessfulRefresh(
            snapshot = session,
            nowMillis = 2_000L,
            status = BusMonitorStatus.PREPARE,
            lastSpokenStatus = BusMonitorStatus.PREPARE,
            notificationText = "準備出門 · 剩餘 20 分鐘",
            firstEtaMillis = 60_000L,
            secondEtaMillis = 120_000L
        )

        assertFalse(BusMonitorSessionPolicy.shouldClearOnRestore(119_999L, refreshed))
        assertTrue(BusMonitorSessionPolicy.shouldClearOnRestore(120_000L, refreshed))
        assertTrue(
            BusMonitorSessionPolicy.shouldClearOnRestore(
                session.expiresAtMillis,
                session
            )
        )
        assertTrue(
            BusMonitorSessionPolicy.shouldClearOnRestore(
                2_000L,
                BusMonitorSessionPolicy.markInterrupted(session)
            )
        )
    }

    @Test
    fun refreshPolicyUsesSixtySecondIdleAwareAttemptsAndFailureGuard() {
        assertEquals(61_000L, BusMonitorRefreshPolicy.nextTriggerElapsedRealtime(1_000L))
        assertEquals(2_000L, BusMonitorRefreshPolicy.nextTriggerElapsedRealtime(1_000L, delayMillis = 1L))
        assertFalse(BusMonitorRefreshPolicy.shouldUseIdleAwareAlarm(22))
        assertTrue(BusMonitorRefreshPolicy.shouldUseIdleAwareAlarm(23))
        assertFalse(
            BusMonitorRefreshPolicy.shouldStopAfterFailureCount(
                BusMonitorRefreshPolicy.MAX_CONSECUTIVE_FAILURES - 1
            )
        )
        assertTrue(
            BusMonitorRefreshPolicy.shouldStopAfterFailureCount(
                BusMonitorRefreshPolicy.MAX_CONSECUTIVE_FAILURES
            )
        )
    }

    @Test
    fun ttsLanguagePolicyFallsBackAcrossChineseLocales() {
        val chosen = BusMonitorTtsLanguagePolicy.chooseSupportedLocale { locale ->
            if (locale == Locale.forLanguageTag("zh-HK")) 1 else -2
        }

        assertEquals(Locale.forLanguageTag("zh-HK"), chosen)
        assertEquals(Locale.TRADITIONAL_CHINESE, BusMonitorTtsLanguagePolicy.fallbackLocales.first())
        assertTrue(BusMonitorTtsLanguagePolicy.isLanguageUsable(0))
        assertFalse(BusMonitorTtsLanguagePolicy.isLanguageUsable(-1))
    }

    @Test
    fun ttsLanguagePolicyKeepsDiagnosticsForMissingDataAndUnsupportedLanguages() {
        val missingData = BusMonitorTtsLanguagePolicy.selectSupportedLocale { locale ->
            if (locale == Locale.TRADITIONAL_CHINESE) {
                BusMonitorTtsLanguagePolicy.LANGUAGE_MISSING_DATA
            } else {
                BusMonitorTtsLanguagePolicy.LANGUAGE_NOT_SUPPORTED
            }
        }
        val unsupported = BusMonitorTtsLanguagePolicy.selectSupportedLocale {
            BusMonitorTtsLanguagePolicy.LANGUAGE_NOT_SUPPORTED
        }

        assertTrue(missingData is BusMonitorTtsLanguageSelection.Unavailable)
        assertEquals(
            BusMonitorTtsLanguageUnavailableReason.MISSING_DATA,
            (missingData as BusMonitorTtsLanguageSelection.Unavailable).reason
        )
        assertTrue(unsupported is BusMonitorTtsLanguageSelection.Unavailable)
        assertEquals(
            BusMonitorTtsLanguageUnavailableReason.NOT_SUPPORTED,
            (unsupported as BusMonitorTtsLanguageSelection.Unavailable).reason
        )
        assertTrue(BusMonitorTtsLanguagePolicy.fallbackLocales.contains(Locale.SIMPLIFIED_CHINESE))
        assertTrue(BusMonitorTtsLanguagePolicy.fallbackLocales.contains(Locale.forLanguageTag("yue-HK")))
    }

    @Test
    fun speechResultTracksSpokenAndDiagnosticFailureStates() {
        assertTrue(BusMonitorSpeechResult.Started.countsAsSpoken)
        assertTrue(BusMonitorSpeechResult.Completed.countsAsSpoken)
        assertFalse(BusMonitorSpeechResult.Accepted.countsAsSpoken)
        assertFalse(
            BusMonitorSpeechResult.Failure(
                BusMonitorSpeechFailureReason.LANGUAGE_MISSING_DATA
            ).countsAsSpoken
        )
        assertFalse(
            BusMonitorSpeechResult.Failure(
                BusMonitorSpeechFailureReason.AUDIO_FOCUS_DENIED
            ).countsAsSpoken
        )
    }

    private fun etaQuery(): FirstLegEtaQuery {
        return FirstLegEtaQuery(
            company = "CTB",
            routeVariant = "A12-THR-1",
            route = "A12",
            boardingSeq = 3,
            alightingSeq = 18,
            bound = "O",
            directionPath = "outbound",
            rawInfo = "1|*|CTB||A12-THR-1||3||18||O|*|",
            lang = "0"
        )
    }
}
