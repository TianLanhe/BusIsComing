package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.location.CurrentLocationCoordinator
import com.golink.busiscoming.data.location.CurrentLocationSnapshot

internal object RouteDetailLocationHeadingPolicy {
    fun shouldTrack(
        resumed: Boolean,
        hasPermission: Boolean,
        systemLocationEnabled: Boolean,
        mapUsable: Boolean
    ): Boolean = resumed && hasPermission && systemLocationEnabled && mapUsable

    fun isFresh(snapshot: CurrentLocationSnapshot, nowElapsedMillis: Long): Boolean {
        val ageMillis = nowElapsedMillis - snapshot.elapsedRealtimeMillis
        return ageMillis in 0L..CurrentLocationCoordinator.SNAPSHOT_MAX_AGE_MS
    }
}
