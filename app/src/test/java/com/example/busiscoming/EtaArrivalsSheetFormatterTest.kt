package com.example.busiscoming

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.EtaArrival
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.ui.main.EtaArrivalsSheetFormatter
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

        assertEquals("首程 8X 候車時間", EtaArrivalsSheetFormatter.title(route))
        assertEquals("樂軒臺 往 筲箕灣", EtaArrivalsSheetFormatter.subtitle(route, arrival))
        assertEquals("4 分鐘", EtaArrivalsSheetFormatter.minuteText(4))
        assertEquals("即將到站", EtaArrivalsSheetFormatter.minuteText(0))
        assertEquals("更新 12:01", EtaArrivalsSheetFormatter.updateTimeText(arrival))
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
            EtaArrivalsSheetFormatter.subtitle(route, EtaArrival(sequence = 1, minutes = 4))
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
}
