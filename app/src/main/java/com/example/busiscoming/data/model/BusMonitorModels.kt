package com.example.busiscoming.data.model

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class WalkingSpeedPreset(
    val label: String,
    val speedKmh: Double
) {
    SLOW("慢行", 3.2),
    CHILD("帶小孩", 3.5),
    NORMAL("一般", 5.0),
    FAST("快走", 6.0)
}

enum class WalkingScenarioModifier(
    val label: String,
    val speedMultiplier: Double = 1.0,
    val extraMinutes: Int = 0
) {
    RAIN("雨天", speedMultiplier = 0.8),
    ELEVATOR("等電梯", extraMinutes = 2),
    CROSSING("天橋/過馬路", extraMinutes = 2)
}

enum class BusMonitorStatus(
    val displayText: String,
    val speechSuffix: String
) {
    PREPARE("準備出門", "請做好出門準備"),
    LEAVE_NOW("立即出門", "請立即出門"),
    LATE("快遲到了", "你要遲到了")
}

data class WalkingTimeEstimate(
    val interfaceDistanceMinutes: Int?,
    val straightLineMinutes: Int?,
    val userAdjustedMinutes: Int,
    val finalMinutes: Int
)

object WalkingTimeCalculator {
    fun estimate(
        interfaceDistanceMeters: Int?,
        straightLineDistanceMeters: Int?,
        userAdjustedMinutes: Int,
        speedPreset: WalkingSpeedPreset,
        modifiers: Set<WalkingScenarioModifier>
    ): WalkingTimeEstimate {
        val effectiveSpeedKmh = modifiers.fold(speedPreset.speedKmh) { speed, modifier ->
            speed * modifier.speedMultiplier
        }
        val interfaceMinutes = interfaceDistanceMeters?.let { minutesForDistance(it, effectiveSpeedKmh) }
        val straightLineMinutes = straightLineDistanceMeters?.let { minutesForDistance(it, effectiveSpeedKmh) }
        val extraMinutes = modifiers.sumOf { it.extraMinutes }
        val baseMinutes = listOfNotNull(
            interfaceMinutes,
            straightLineMinutes,
            userAdjustedMinutes.coerceAtLeast(1)
        ).maxOrNull() ?: userAdjustedMinutes.coerceAtLeast(1)
        return WalkingTimeEstimate(
            interfaceDistanceMinutes = interfaceMinutes,
            straightLineMinutes = straightLineMinutes,
            userAdjustedMinutes = userAdjustedMinutes.coerceAtLeast(1),
            finalMinutes = baseMinutes + extraMinutes
        )
    }

    fun minutesForDistance(distanceMeters: Int, speedKmh: Double): Int {
        if (distanceMeters <= 0 || speedKmh <= 0.0) return 1
        val metersPerMinute = speedKmh * 1000.0 / 60.0
        return ceil(distanceMeters / metersPerMinute).toInt().coerceAtLeast(1)
    }

    fun straightLineDistanceMeters(from: Place, toLatitude: Double, toLongitude: Double): Int {
        val earthRadiusMeters = 6_371_000.0
        val startLat = Math.toRadians(from.latitude)
        val endLat = Math.toRadians(toLatitude)
        val deltaLat = Math.toRadians(toLatitude - from.latitude)
        val deltaLon = Math.toRadians(toLongitude - from.longitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(startLat) * cos(endLat) * sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusMeters * c).roundToInt().coerceAtLeast(0)
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

object BusMonitorSpeechFormatter {
    fun phrase(firstEtaMinutes: Int, status: BusMonitorStatus): String {
        return "當前汽車到站剩餘 ${firstEtaMinutes.coerceAtLeast(0)} 分鐘，${status.speechSuffix}"
    }
}
