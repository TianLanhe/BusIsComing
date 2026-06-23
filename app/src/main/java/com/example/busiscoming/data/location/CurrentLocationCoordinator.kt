package com.example.busiscoming.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class CurrentLocationCoordinator(
    context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val nowElapsedMillis: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private val appContext = context.applicationContext
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(appContext)
    private var cachedSnapshot: CurrentLocationSnapshot? = null
    private var pendingCallbacks: MutableList<(CurrentLocationResult) -> Unit>? = null
    private var timeoutRunnable: Runnable? = null

    fun getCurrentLocation(callback: (CurrentLocationResult) -> Unit) {
        if (!LocationPermissionUtils.hasForegroundLocationPermission(appContext)) {
            callback(CurrentLocationResult.NoPermission)
            return
        }

        cachedSnapshot?.takeIf { isFresh(it) }?.let {
            callback(CurrentLocationResult.Success(it))
            return
        }

        val existingCallbacks = pendingCallbacks
        if (existingCallbacks != null) {
            existingCallbacks += callback
            return
        }

        pendingCallbacks = mutableListOf(callback)
        requestLastLocation()
    }

    @SuppressLint("MissingPermission")
    private fun requestLastLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                val snapshot = location?.toFreshSnapshot()
                if (snapshot != null) {
                    finish(CurrentLocationResult.Success(snapshot))
                } else {
                    requestFreshLocation()
                }
            }
            .addOnFailureListener {
                requestFreshLocation()
            }
    }

    fun updateSnapshotForTests(snapshot: CurrentLocationSnapshot?) {
        cachedSnapshot = snapshot
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        if (pendingCallbacks == null) return
        val timeout = Runnable {
            finish(CurrentLocationResult.Timeout)
        }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, LOCATION_TIMEOUT_MS)

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                val snapshot = location?.toSnapshot()
                finish(
                    if (snapshot == null) {
                        CurrentLocationResult.Unavailable
                    } else {
                        CurrentLocationResult.Success(snapshot)
                    }
                )
            }
            .addOnFailureListener {
                finish(CurrentLocationResult.Unavailable)
            }
    }

    private fun finish(result: CurrentLocationResult) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { finish(result) }
            return
        }
        val callbacks = pendingCallbacks ?: return
        pendingCallbacks = null
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        if (result is CurrentLocationResult.Success) {
            cachedSnapshot = result.snapshot
        }
        callbacks.forEach { it(result) }
    }

    private fun Location.toFreshSnapshot(): CurrentLocationSnapshot? {
        val snapshot = toSnapshot()
        return snapshot.takeIf { isFresh(it) }
    }

    private fun Location.toSnapshot(): CurrentLocationSnapshot {
        return CurrentLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            elapsedRealtimeMillis = elapsedRealtimeNanos / NANOS_PER_MILLI
        )
    }

    private fun isFresh(snapshot: CurrentLocationSnapshot): Boolean {
        return nowElapsedMillis() - snapshot.elapsedRealtimeMillis <= SNAPSHOT_MAX_AGE_MS
    }

    companion object {
        const val SNAPSHOT_MAX_AGE_MS = 30_000L
        const val LOCATION_TIMEOUT_MS = 3_000L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
