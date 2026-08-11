package com.golink.busiscoming.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.FusedOrientationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class FusedForegroundLocationUpdatesSource(
    context: Context,
    private val client: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
) : ForegroundLocationUpdatesSource {
    private val appContext = context.applicationContext

    @SuppressLint("MissingPermission")
    override fun start(callback: ForegroundLocationUpdatesCallback): LocationHeadingSubscription {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    callback.onLocation(
                        CurrentLocationSnapshot(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                            elapsedRealtimeMillis = location.elapsedRealtimeNanos / NANOS_PER_MILLI
                        )
                    )
                }
            }
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_INTERVAL_MILLIS
        ).setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MILLIS)
            .setMaxUpdateDelayMillis(0L)
            .build()
        client.requestLocationUpdates(
            request,
            ContextCompat.getMainExecutor(appContext),
            locationCallback
        ).addOnFailureListener { callback.onFailure() }
        return LocationHeadingSubscription {
            client.removeLocationUpdates(locationCallback)
        }
    }

    private companion object {
        const val LOCATION_INTERVAL_MILLIS = 1_000L
        const val LOCATION_MIN_INTERVAL_MILLIS = 500L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

class FusedDeviceHeadingUpdatesSource(
    context: Context,
    private val client: FusedOrientationProviderClient =
        LocationServices.getFusedOrientationProviderClient(context.applicationContext)
) : DeviceHeadingUpdatesSource {
    private val appContext = context.applicationContext

    override fun start(callback: DeviceHeadingUpdatesCallback): LocationHeadingSubscription {
        val listener = DeviceOrientationListener { orientation ->
            callback.onHeading(
                DeviceHeadingSnapshot(
                    headingDegrees = orientation.headingDegrees,
                    headingErrorDegrees = orientation.headingErrorDegrees,
                    conservativeErrorDegrees = if (orientation.hasConservativeHeadingErrorDegrees()) {
                        orientation.conservativeHeadingErrorDegrees
                    } else {
                        null
                    },
                    elapsedRealtimeMillis = orientation.elapsedRealtimeNs / NANOS_PER_MILLI
                )
            )
        }
        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT)
            .build()
        client.requestOrientationUpdates(
            request,
            ContextCompat.getMainExecutor(appContext),
            listener
        ).addOnFailureListener { callback.onFailure() }
        return LocationHeadingSubscription {
            client.removeOrientationUpdates(listener)
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

class MainThreadHeadingLivenessScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : HeadingLivenessScheduler {
    override fun schedule(delayMillis: Long, block: () -> Unit): LocationHeadingSubscription {
        val runnable = Runnable(block)
        handler.postDelayed(runnable, delayMillis)
        return LocationHeadingSubscription { handler.removeCallbacks(runnable) }
    }
}

fun createForegroundLocationHeadingCoordinator(context: Context): ForegroundLocationHeadingTracker =
    ForegroundLocationHeadingCoordinator(
        locationSource = FusedForegroundLocationUpdatesSource(context),
        headingSource = FusedDeviceHeadingUpdatesSource(context),
        headingLivenessScheduler = MainThreadHeadingLivenessScheduler()
    )
