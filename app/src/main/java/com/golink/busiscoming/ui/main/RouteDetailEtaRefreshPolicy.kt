package com.golink.busiscoming.ui.main

object RouteDetailEtaRefreshPolicy {
    const val REFRESH_INTERVAL_MILLIS = 60_000L

    fun shouldRefreshOnForeground(nowMillis: Long, lastSuccessMillis: Long?): Boolean {
        return lastSuccessMillis == null || nowMillis - lastSuccessMillis >= REFRESH_INTERVAL_MILLIS
    }
}
