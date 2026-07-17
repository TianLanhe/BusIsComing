package com.golink.busiscoming.service

import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.BusMonitorStatus
import com.golink.busiscoming.ui.common.LocalizedText
import kotlin.math.abs

object BusMonitorSpeechFormatter {
    fun phrase(firstEtaMinutes: Int, status: BusMonitorStatus, text: LocalizedText): String {
        val resource = when (status) {
            BusMonitorStatus.PREPARE -> R.string.tts_monitor_prepare
            BusMonitorStatus.LEAVE_NOW -> R.string.tts_monitor_leave_now
            BusMonitorStatus.LATE -> R.string.tts_monitor_late
        }
        return text.get(resource, arrayOf(firstEtaMinutes.coerceAtLeast(0)))
    }
}

object BusMonitorNotificationFormatter {
    fun title(routeName: String, status: BusMonitorStatus?, text: LocalizedText): String {
        return listOf(firstLegRoute(routeName, text), statusText(status, text)).joinToString(" · ")
    }

    fun successText(
        firstEtaMinutes: Int,
        nextEtaMinutes: Int?,
        walkingMinutes: Int,
        updatedAtText: String,
        text: LocalizedText
    ): String = bodyText(firstEtaMinutes, nextEtaMinutes, walkingMinutes, updatedAtText, text)

    fun bodyText(
        firstEtaMinutes: Int,
        nextEtaMinutes: Int?,
        walkingMinutes: Int,
        updatedAtText: String,
        text: LocalizedText
    ): String {
        val margin = firstEtaMinutes - walkingMinutes
        return listOfNotNull(
            text.get(
                if (margin >= 0) R.string.notification_margin_remaining else R.string.notification_margin_overdue,
                arrayOf(abs(margin))
            ),
            text.get(R.string.notification_bus_arrival, arrayOf(firstEtaMinutes.coerceAtLeast(0))),
            text.get(R.string.notification_walking_time, arrayOf(walkingMinutes.coerceAtLeast(0))),
            nextEtaMinutes?.let {
                text.get(R.string.notification_next_arrival, arrayOf(it.coerceAtLeast(0)))
            },
            text.get(R.string.notification_updated_at, arrayOf(updatedAtText))
        ).joinToString(" · ")
    }

    fun failureText(
        lastSuccessfulNotificationText: String?,
        failureCount: Int,
        text: LocalizedText
    ): String = lastSuccessfulNotificationText?.let {
        text.get(R.string.notification_failure_with_last, arrayOf<Any>(it, failureCount))
    } ?: text.get(R.string.notification_failure_no_eta, emptyArray())

    fun firstLegRoute(routeName: String, text: LocalizedText): String = routeName
        .split("→", "->")
        .firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: routeName.ifBlank { text.get(R.string.bus_fallback_name, emptyArray()) }

    private fun statusText(status: BusMonitorStatus?, text: LocalizedText): String = text.get(
        when (status) {
            BusMonitorStatus.PREPARE -> R.string.monitor_status_prepare
            BusMonitorStatus.LEAVE_NOW -> R.string.monitor_status_leave_now
            BusMonitorStatus.LATE -> R.string.monitor_status_late
            null -> R.string.monitor_status_active
        },
        emptyArray()
    )
}
