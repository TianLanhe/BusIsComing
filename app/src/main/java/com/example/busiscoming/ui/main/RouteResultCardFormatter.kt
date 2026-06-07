package com.example.busiscoming.ui.main

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.WaitTimeState
import java.util.Locale

object RouteResultCardFormatter {
    fun price(priceHkd: Double): String {
        return if (priceHkd == 0.0) {
            "免費"
        } else {
            String.format(Locale.US, "HK$ %.1f", priceHkd)
        }
    }

    fun waitStatus(waitTimeState: WaitTimeState): String {
        return when (waitTimeState) {
            is WaitTimeState.Available -> "等候 ${waitTimeState.minutes} 分鐘"
            WaitTimeState.Loading -> "候車查詢中"
            WaitTimeState.Unavailable -> "暫無車輛"
        }
    }

    fun info(route: BusRouteOption): String {
        return "${price(route.priceHkd)} · 耗時 ${route.durationMinutes} 分鐘 · 步行 ${route.walkingDistanceMeters} 米"
    }

    fun resultSummary(routes: List<BusRouteOption>): String {
        return "共 ${routes.size} 條路線，${routes.count { it.transferCount == 0 }} 條直達"
    }
}
