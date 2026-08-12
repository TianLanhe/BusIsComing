package com.golink.busiscoming.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

fun interface ForegroundLocationSubscription : AutoCloseable {
    override fun close()
}

fun interface ForegroundLocationSource {
    fun start(onLocation: (JourneyLocationFix) -> Unit): ForegroundLocationSubscription
}

fun interface RouteDetailLocationScheduler {
    fun schedule(delayMillis: Long, block: () -> Unit): ForegroundLocationSubscription
}

class HandlerRouteDetailLocationScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : RouteDetailLocationScheduler {
    override fun schedule(
        delayMillis: Long,
        block: () -> Unit
    ): ForegroundLocationSubscription {
        val runnable = Runnable(block)
        handler.postDelayed(runnable, delayMillis)
        return ForegroundLocationSubscription { handler.removeCallbacks(runnable) }
    }
}

class FusedForegroundLocationSource(context: Context) : ForegroundLocationSource {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    @SuppressLint("MissingPermission")
    override fun start(onLocation: (JourneyLocationFix) -> Unit): ForegroundLocationSubscription {
        var active = true
        val currentCancellation = CancellationTokenSource()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!active) return
                result.lastLocation?.let { onLocation(it.toJourneyLocationFix()) }
            }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            UPDATE_INTERVAL_MILLIS
        ).setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()

        client.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            currentCancellation.token
        ).addOnSuccessListener { location ->
            if (active && location != null) onLocation(location.toJourneyLocationFix())
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        return ForegroundLocationSubscription {
            if (active) {
                active = false
                currentCancellation.cancel()
                client.removeLocationUpdates(callback)
            }
        }
    }

    private fun Location.toJourneyLocationFix(): JourneyLocationFix = JourneyLocationFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        elapsedRealtimeMillis = elapsedRealtimeNanos / NANOS_PER_MILLI
    )

    companion object {
        const val UPDATE_INTERVAL_MILLIS = 10_000L
        const val MIN_UPDATE_DISTANCE_METERS = 20f
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
