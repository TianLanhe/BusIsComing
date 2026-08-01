package com.golink.busiscoming.data.model

import com.golink.busiscoming.data.location.GeoDistanceCalculator
import java.util.Locale
import kotlin.math.ceil

enum class WalkingSpeedPreset(
    val speedKmh: Double
) {
    SLOW(3.2),
    CHILD(3.5),
    NORMAL(5.0),
    FAST(6.0)
}

enum class WalkingScenarioModifier(
    val speedMultiplier: Double = 1.0,
    val extraMinutes: Int = 0
) {
    RAIN(speedMultiplier = 0.8),
    ELEVATOR(extraMinutes = 2),
    CROSSING(extraMinutes = 2)
}

enum class BusMonitorStatus { PREPARE, LEAVE_NOW, LATE }

data class WalkingTimeEstimate(
    val interfaceDistanceMinutes: Int?,
    val straightLineMinutes: Int?,
    val manualAdjustmentMinutes: Int,
    val finalMinutes: Int
)

object WalkingTimeCalculator {
    fun estimate(
        interfaceDistanceMeters: Int?,
        straightLineDistanceMeters: Int?,
        manualAdjustmentMinutes: Int,
        speedPreset: WalkingSpeedPreset,
        modifiers: Set<WalkingScenarioModifier>
    ): WalkingTimeEstimate {
        val effectiveSpeedKmh = modifiers.fold(speedPreset.speedKmh) { speed, modifier ->
            speed * modifier.speedMultiplier
        }
        val interfaceMinutes = interfaceDistanceMeters?.let { minutesForDistance(it, effectiveSpeedKmh) }
        val straightLineMinutes = straightLineDistanceMeters?.let { minutesForDistance(it, effectiveSpeedKmh) }
        val extraMinutes = modifiers.sumOf { it.extraMinutes }
        val distanceBaseline = listOfNotNull(
            interfaceMinutes,
            straightLineMinutes,
            1
        ).maxOrNull() ?: 1
        return WalkingTimeEstimate(
            interfaceDistanceMinutes = interfaceMinutes,
            straightLineMinutes = straightLineMinutes,
            manualAdjustmentMinutes = manualAdjustmentMinutes,
            finalMinutes = (
                distanceBaseline + extraMinutes + manualAdjustmentMinutes
            ).coerceAtLeast(1)
        )
    }

    fun minutesForDistance(distanceMeters: Int, speedKmh: Double): Int {
        if (distanceMeters <= 0 || speedKmh <= 0.0) return 1
        val metersPerMinute = speedKmh * 1000.0 / 60.0
        return ceil(distanceMeters / metersPerMinute).toInt().coerceAtLeast(1)
    }

    fun straightLineDistanceMeters(from: Place, toLatitude: Double, toLongitude: Double): Int {
        return GeoDistanceCalculator.distanceMeters(
            fromLatitude = from.latitude,
            fromLongitude = from.longitude,
            toLatitude = toLatitude,
            toLongitude = toLongitude
        )
    }
}

object BusMonitorStateEvaluator {
    fun evaluate(firstEtaMinutes: Int, walkingMinutes: Int): BusMonitorStatus {
        val remainingWaitMinutes = firstEtaMinutes - walkingMinutes
        return when {
            remainingWaitMinutes > 2 -> BusMonitorStatus.PREPARE
            remainingWaitMinutes in 1..2 -> BusMonitorStatus.LEAVE_NOW
            else -> BusMonitorStatus.LATE
        }
    }
}

object BusMonitorSpeechPolicy {
    fun shouldSpeak(lastStatus: BusMonitorStatus?, nextStatus: BusMonitorStatus): Boolean {
        return lastStatus != nextStatus
    }
}

object BusMonitorTtsLanguagePolicy {
    const val LANGUAGE_MISSING_DATA = -1
    const val LANGUAGE_NOT_SUPPORTED = -2

    fun selectSupportedLocale(
        family: com.golink.busiscoming.data.localization.TtsLanguageFamily,
        availableVoiceLocales: Collection<Locale>,
        languageResult: (Locale) -> Int
    ): BusMonitorTtsLanguageSelection {
        val compatibleLocales = availableVoiceLocales
            .filter { isCompatible(family, it) }
            .distinctBy { it.toLanguageTag().lowercase(Locale.ROOT) }
            .sortedBy { preferenceRank(family, it) }
        if (compatibleLocales.isEmpty()) {
            return BusMonitorTtsLanguageSelection.Unavailable(
                reason = BusMonitorTtsLanguageUnavailableReason.NO_COMPATIBLE_VOICE,
                checks = emptyList()
            )
        }
        val checks = mutableListOf<BusMonitorTtsLanguageCheck>()
        compatibleLocales.forEach { locale ->
            val result = languageResult(locale)
            checks += BusMonitorTtsLanguageCheck(locale = locale, result = result)
            if (isLanguageUsable(result)) {
                return BusMonitorTtsLanguageSelection.Supported(
                    locale = locale,
                    checks = checks.toList()
                )
            }
        }
        val reason = if (checks.any { it.result == LANGUAGE_MISSING_DATA }) {
            BusMonitorTtsLanguageUnavailableReason.MISSING_DATA
        } else {
            BusMonitorTtsLanguageUnavailableReason.NOT_SUPPORTED
        }
        return BusMonitorTtsLanguageSelection.Unavailable(
            reason = reason,
            checks = checks.toList()
        )
    }

    fun isLanguageUsable(languageResult: Int): Boolean {
        return languageResult >= 0
    }

    fun isCompatible(
        family: com.golink.busiscoming.data.localization.TtsLanguageFamily,
        locale: Locale
    ): Boolean {
        val language = locale.language.lowercase(Locale.ROOT)
        val script = locale.script.lowercase(Locale.ROOT)
        val region = locale.country.uppercase(Locale.ROOT)
        return when (family) {
            com.golink.busiscoming.data.localization.TtsLanguageFamily.TRADITIONAL_CHINESE ->
                language == "yue" ||
                    (language == "zh" && (script == "hant" || region in setOf("HK", "MO", "TW")))
            com.golink.busiscoming.data.localization.TtsLanguageFamily.SIMPLIFIED_CHINESE ->
                (language == "cmn" && region !in setOf("HK", "MO", "TW")) ||
                    (language == "zh" && (script == "hans" || region in setOf("CN", "SG")))
            com.golink.busiscoming.data.localization.TtsLanguageFamily.ENGLISH -> language == "en"
        }
    }

    private fun preferenceRank(
        family: com.golink.busiscoming.data.localization.TtsLanguageFamily,
        locale: Locale
    ): Int {
        val tag = locale.toLanguageTag().lowercase(Locale.ROOT)
        return when (family) {
            com.golink.busiscoming.data.localization.TtsLanguageFamily.TRADITIONAL_CHINESE -> when {
                tag.startsWith("yue-hk") -> 0
                tag == "zh-hk" -> 1
                "hant" in tag && tag.endsWith("-hk") -> 2
                "hant" in tag -> 3
                else -> 4
            }
            com.golink.busiscoming.data.localization.TtsLanguageFamily.SIMPLIFIED_CHINESE -> when {
                tag.startsWith("cmn-cn") -> 0
                "hans" in tag && tag.endsWith("-cn") -> 1
                tag == "zh-cn" -> 2
                "hans" in tag -> 3
                else -> 4
            }
            com.golink.busiscoming.data.localization.TtsLanguageFamily.ENGLISH -> when {
                tag == "en-hk" -> 0
                tag == "en-gb" -> 1
                tag == "en-us" -> 2
                else -> 3
            }
        }
    }
}

data class BusMonitorTtsLanguageCheck(
    val locale: Locale,
    val result: Int
)

sealed class BusMonitorTtsLanguageSelection {
    data class Supported(
        val locale: Locale,
        val checks: List<BusMonitorTtsLanguageCheck>
    ) : BusMonitorTtsLanguageSelection()

    data class Unavailable(
        val reason: BusMonitorTtsLanguageUnavailableReason,
        val checks: List<BusMonitorTtsLanguageCheck>
    ) : BusMonitorTtsLanguageSelection()
}

enum class BusMonitorTtsLanguageUnavailableReason {
    MISSING_DATA,
    NOT_SUPPORTED,
    NO_COMPATIBLE_VOICE
}

enum class BusMonitorStopTargetSource {
    SECOND_ETA,
    FIRST_ETA_FALLBACK
}

object BusMonitorStopPolicy {
    const val FALLBACK_SECOND_ETA_DELAY_MILLIS = 120_000L

    fun shouldAutoStop(
        nowMillis: Long,
        stopAtMillis: Long?
    ): Boolean {
        return stopAtMillis != null && nowMillis >= stopAtMillis
    }

    fun shouldStopManually(manualStopRequested: Boolean): Boolean {
        return manualStopRequested
    }
}

object BusMonitorRefreshPolicy {
    const val REFRESH_INTERVAL_MILLIS = 60_000L
    const val MAX_CONSECUTIVE_FAILURES = 10

    fun nextTriggerElapsedRealtime(nowElapsedRealtime: Long, delayMillis: Long = REFRESH_INTERVAL_MILLIS): Long {
        return nowElapsedRealtime + delayMillis.coerceAtLeast(1_000L)
    }

    fun shouldUseIdleAwareAlarm(sdkInt: Int): Boolean {
        return sdkInt >= 23
    }

    fun shouldStopAfterFailureCount(failureCount: Int): Boolean {
        return failureCount >= MAX_CONSECUTIVE_FAILURES
    }
}

data class BusMonitorSessionSnapshot(
    val routeName: String,
    val walkingMinutes: Int,
    val voiceEnabled: Boolean,
    val query: FirstLegEtaQuery,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val lastStatus: BusMonitorStatus? = null,
    val lastSpokenStatus: BusMonitorStatus? = null,
    val lastSuccessfulNotificationText: String? = null,
    val lastSuccessfulUpdateMillis: Long? = null,
    val firstEtaMillis: Long? = null,
    val secondEtaMillis: Long? = null,
    val stopAtMillis: Long? = null,
    val stopTargetSource: BusMonitorStopTargetSource? = null,
    val consecutiveFailureCount: Int = 0,
    val interrupted: Boolean = false
)

object BusMonitorSessionPolicy {
    const val MAX_SESSION_DURATION_MILLIS = 2 * 60 * 60 * 1_000L

    fun newSession(
        nowMillis: Long,
        routeName: String,
        walkingMinutes: Int,
        voiceEnabled: Boolean,
        query: FirstLegEtaQuery
    ): BusMonitorSessionSnapshot {
        return BusMonitorSessionSnapshot(
            routeName = routeName,
            walkingMinutes = walkingMinutes.coerceAtLeast(1),
            voiceEnabled = voiceEnabled,
            query = query,
            startedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + MAX_SESSION_DURATION_MILLIS
        )
    }

    fun shouldClearOnRestore(nowMillis: Long, snapshot: BusMonitorSessionSnapshot): Boolean {
        return snapshot.interrupted ||
            nowMillis >= snapshot.expiresAtMillis ||
            BusMonitorStopPolicy.shouldAutoStop(
                nowMillis = nowMillis,
                stopAtMillis = snapshot.stopAtMillis
            )
    }

    fun recordSuccessfulRefresh(
        snapshot: BusMonitorSessionSnapshot,
        nowMillis: Long,
        status: BusMonitorStatus,
        lastSpokenStatus: BusMonitorStatus?,
        notificationText: String,
        firstEtaMillis: Long?,
        secondEtaMillis: Long?
    ): BusMonitorSessionSnapshot {
        val stopTarget = resolveStopTarget(
            currentStopAtMillis = snapshot.stopAtMillis,
            currentStopTargetSource = snapshot.stopTargetSource,
            firstEtaMillis = firstEtaMillis,
            secondEtaMillis = secondEtaMillis
        )
        return snapshot.copy(
            lastStatus = status,
            lastSpokenStatus = lastSpokenStatus,
            lastSuccessfulNotificationText = notificationText,
            lastSuccessfulUpdateMillis = nowMillis,
            firstEtaMillis = firstEtaMillis,
            secondEtaMillis = secondEtaMillis,
            stopAtMillis = stopTarget.stopAtMillis,
            stopTargetSource = stopTarget.source,
            consecutiveFailureCount = 0,
            interrupted = false
        )
    }

    private fun resolveStopTarget(
        currentStopAtMillis: Long?,
        currentStopTargetSource: BusMonitorStopTargetSource?,
        firstEtaMillis: Long?,
        secondEtaMillis: Long?
    ): StopTarget {
        if (currentStopAtMillis != null && currentStopTargetSource != null) {
            return StopTarget(currentStopAtMillis, currentStopTargetSource)
        }
        if (secondEtaMillis != null) {
            return StopTarget(secondEtaMillis, BusMonitorStopTargetSource.SECOND_ETA)
        }
        if (firstEtaMillis != null) {
            return StopTarget(
                firstEtaMillis + BusMonitorStopPolicy.FALLBACK_SECOND_ETA_DELAY_MILLIS,
                BusMonitorStopTargetSource.FIRST_ETA_FALLBACK
            )
        }
        return StopTarget(null, null)
    }

    fun recordFailure(snapshot: BusMonitorSessionSnapshot): BusMonitorSessionSnapshot {
        return snapshot.copy(consecutiveFailureCount = snapshot.consecutiveFailureCount + 1)
    }

    fun markInterrupted(snapshot: BusMonitorSessionSnapshot): BusMonitorSessionSnapshot {
        return snapshot.copy(interrupted = true)
    }

    private data class StopTarget(
        val stopAtMillis: Long?,
        val source: BusMonitorStopTargetSource?
    )
}

object BusMonitorSessionSnapshotCodec {
    fun encode(snapshot: BusMonitorSessionSnapshot): Map<String, String> {
        val values = mutableMapOf(
            KEY_ROUTE_NAME to snapshot.routeName,
            KEY_WALKING_MINUTES to snapshot.walkingMinutes.toString(),
            KEY_VOICE_ENABLED to snapshot.voiceEnabled.toString(),
            KEY_STARTED_AT to snapshot.startedAtMillis.toString(),
            KEY_EXPIRES_AT to snapshot.expiresAtMillis.toString(),
            KEY_COMPANY to snapshot.query.company,
            KEY_ROUTE_VARIANT to snapshot.query.routeVariant,
            KEY_ROUTE to snapshot.query.route,
            KEY_BOARDING_SEQ to snapshot.query.boardingSeq.toString(),
            KEY_ALIGHTING_SEQ to snapshot.query.alightingSeq.toString(),
            KEY_BOUND to snapshot.query.bound,
            KEY_DIRECTION_PATH to snapshot.query.directionPath,
            KEY_RAW_INFO to snapshot.query.rawInfo,
            KEY_LANG to snapshot.query.lang,
            KEY_CONSECUTIVE_FAILURES to snapshot.consecutiveFailureCount.toString(),
            KEY_INTERRUPTED to snapshot.interrupted.toString()
        )
        snapshot.lastStatus?.let { values[KEY_LAST_STATUS] = it.name }
        snapshot.lastSpokenStatus?.let { values[KEY_LAST_SPOKEN_STATUS] = it.name }
        snapshot.lastSuccessfulNotificationText?.let { values[KEY_LAST_NOTIFICATION_TEXT] = it }
        snapshot.lastSuccessfulUpdateMillis?.let { values[KEY_LAST_UPDATE_MILLIS] = it.toString() }
        snapshot.firstEtaMillis?.let { values[KEY_FIRST_ETA_MILLIS] = it.toString() }
        snapshot.secondEtaMillis?.let { values[KEY_SECOND_ETA_MILLIS] = it.toString() }
        snapshot.stopAtMillis?.let { values[KEY_STOP_AT_MILLIS] = it.toString() }
        snapshot.stopTargetSource?.let { values[KEY_STOP_TARGET_SOURCE] = it.name }
        return values
    }

    fun decode(values: Map<String, String>): BusMonitorSessionSnapshot? {
        val routeName = values[KEY_ROUTE_NAME]?.takeIf { it.isNotBlank() } ?: return null
        val query = FirstLegEtaQuery(
            company = values[KEY_COMPANY]?.takeIf { it.isNotBlank() } ?: return null,
            routeVariant = values[KEY_ROUTE_VARIANT]?.takeIf { it.isNotBlank() } ?: return null,
            route = values[KEY_ROUTE]?.takeIf { it.isNotBlank() } ?: return null,
            boardingSeq = values[KEY_BOARDING_SEQ]?.toIntOrNull() ?: return null,
            alightingSeq = values[KEY_ALIGHTING_SEQ]?.toIntOrNull() ?: return null,
            bound = values[KEY_BOUND]?.takeIf { it.isNotBlank() } ?: return null,
            directionPath = values[KEY_DIRECTION_PATH]?.takeIf { it.isNotBlank() } ?: return null,
            rawInfo = values[KEY_RAW_INFO].orEmpty(),
            lang = values[KEY_LANG].orEmpty().ifBlank { "0" }
        )
        return BusMonitorSessionSnapshot(
            routeName = routeName,
            walkingMinutes = values[KEY_WALKING_MINUTES]?.toIntOrNull()?.coerceAtLeast(1) ?: return null,
            voiceEnabled = values[KEY_VOICE_ENABLED]?.toBooleanStrictOrNull() ?: true,
            query = query,
            startedAtMillis = values[KEY_STARTED_AT]?.toLongOrNull() ?: return null,
            expiresAtMillis = values[KEY_EXPIRES_AT]?.toLongOrNull() ?: return null,
            lastStatus = values[KEY_LAST_STATUS].toBusMonitorStatusOrNull(),
            lastSpokenStatus = values[KEY_LAST_SPOKEN_STATUS].toBusMonitorStatusOrNull(),
            lastSuccessfulNotificationText = values[KEY_LAST_NOTIFICATION_TEXT],
            lastSuccessfulUpdateMillis = values[KEY_LAST_UPDATE_MILLIS]?.toLongOrNull(),
            firstEtaMillis = values[KEY_FIRST_ETA_MILLIS]?.toLongOrNull(),
            secondEtaMillis = values[KEY_SECOND_ETA_MILLIS]?.toLongOrNull(),
            stopAtMillis = values[KEY_STOP_AT_MILLIS]?.toLongOrNull(),
            stopTargetSource = values[KEY_STOP_TARGET_SOURCE].toStopTargetSourceOrNull(),
            consecutiveFailureCount = values[KEY_CONSECUTIVE_FAILURES]?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            interrupted = values[KEY_INTERRUPTED]?.toBooleanStrictOrNull() ?: false
        )
    }

    private fun String?.toBusMonitorStatusOrNull(): BusMonitorStatus? {
        return this?.let { value ->
            runCatching { BusMonitorStatus.valueOf(value) }.getOrNull()
        }
    }

    private fun String?.toStopTargetSourceOrNull(): BusMonitorStopTargetSource? {
        return this?.let { value ->
            runCatching { BusMonitorStopTargetSource.valueOf(value) }.getOrNull()
        }
    }

    private const val KEY_ROUTE_NAME = "route_name"
    private const val KEY_WALKING_MINUTES = "walking_minutes"
    private const val KEY_VOICE_ENABLED = "voice_enabled"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_LAST_SPOKEN_STATUS = "last_spoken_status"
    private const val KEY_LAST_NOTIFICATION_TEXT = "last_notification_text"
    private const val KEY_LAST_UPDATE_MILLIS = "last_update_millis"
    private const val KEY_FIRST_ETA_MILLIS = "first_eta_millis"
    private const val KEY_SECOND_ETA_MILLIS = "second_eta_millis"
    private const val KEY_STOP_AT_MILLIS = "stop_at_millis"
    private const val KEY_STOP_TARGET_SOURCE = "stop_target_source"
    private const val KEY_CONSECUTIVE_FAILURES = "consecutive_failures"
    private const val KEY_INTERRUPTED = "interrupted"
    private const val KEY_COMPANY = "company"
    private const val KEY_ROUTE_VARIANT = "route_variant"
    private const val KEY_ROUTE = "route"
    private const val KEY_BOARDING_SEQ = "boarding_seq"
    private const val KEY_ALIGHTING_SEQ = "alighting_seq"
    private const val KEY_BOUND = "bound"
    private const val KEY_DIRECTION_PATH = "direction_path"
    private const val KEY_RAW_INFO = "raw_info"
    private const val KEY_LANG = "lang"
}
