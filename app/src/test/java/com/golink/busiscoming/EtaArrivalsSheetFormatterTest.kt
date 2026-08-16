package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.ui.main.EtaArrivalsSheetFormatter
import com.golink.busiscoming.ui.common.LocalizedText
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class EtaArrivalsSheetFormatterTest {
    @Test
    fun formatsTitleSubtitleRowsAndUpdateTime() {
        val route = route().copy(
            stopPreview = RouteCardStopPreview(
                boardingStopName = "樂軒臺",
                alightingStopName = "健康村"
            )
        )
        val arrival = EtaArrival(
            sequence = 1,
            minutes = 4,
            arrivalTimeText = "12:04",
            destination = "筲箕灣",
            remark = "原定班次",
            dataTimestampMillis = millis("2026-06-04T12:01:00+08:00")
        )

        assertEquals("首程 8X 候車時間", EtaArrivalsSheetFormatter.title(route, text))
        assertEquals("樂軒臺 往 筲箕灣", EtaArrivalsSheetFormatter.subtitle(route, arrival, text))
        assertEquals("4 分鐘", EtaArrivalsSheetFormatter.minuteText(4, text))
        assertEquals("即將到站", EtaArrivalsSheetFormatter.minuteText(0, text))
        assertEquals("更新 12:01", EtaArrivalsSheetFormatter.updateTimeText(listOf(arrival), text))
    }

    @Test
    fun updateTimeUsesOldestTimestampAcrossAllOperators() {
        val arrivals = listOf(
            EtaArrival(1, 3, dataTimestampMillis = millis("2026-06-04T12:03:00+08:00")),
            EtaArrival(
                2,
                5,
                dataTimestampMillis = millis("2026-06-04T12:01:00+08:00"),
                operator = BusOperator.KMB
            ),
            EtaArrival(3, 8, dataTimestampMillis = null, operator = BusOperator.CTB)
        )

        assertEquals("更新 12:01", EtaArrivalsSheetFormatter.updateTimeText(arrivals, text))
    }

    @Test
    fun fallsBackToStopPreviewWhenDestinationIsMissing() {
        val route = route().copy(
            stopPreview = RouteCardStopPreview(
                boardingStopName = "樂軒臺",
                alightingStopName = "健康村"
            )
        )

        assertEquals(
            "樂軒臺  →  健康村",
            EtaArrivalsSheetFormatter.subtitle(
                route,
                EtaArrival(sequence = 1, minutes = 4),
                text
            )
        )
    }

    private fun route(): BusRouteOption {
        return BusRouteOption(
            routeName = "8X",
            routeSegments = listOf("8X"),
            priceHkd = 8.1,
            durationMinutes = 30,
            arrivalMinutes = 4,
            transferCount = 0,
            walkingDistanceMeters = 100
        )
    }

    private fun millis(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)!!.time
    }

    private val text = LocalizedText { resourceId, args ->
        when (resourceId) {
            R.string.eta_sheet_title -> "首程 ${args[0]} 候車時間"
            R.string.direction_from_to -> "${args[0]} 往 ${args[1]}"
            R.string.direction_to -> "往 ${args[0]}"
            R.string.eta_due -> "即將到站"
            R.string.minutes_count -> "${args[0]} 分鐘"
            R.string.eta_updated -> "更新 ${args[0]}"
            else -> error("Unexpected resource $resourceId")
        }
    }
}
