package com.golink.busiscoming.data.location

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
    private val nowElapsedMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val capabilityProbe: GoogleServiceCapabilityProbe = AndroidGoogleServiceCapabilityProbe(context)
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private var lastCapability: GoogleServiceCapability? = null
    private val systemRequests = SystemLocationRequestBroker(
        source = { callback -> SystemForegroundLocationSource(appContext).request(callback) },
        schedule = { delay, action ->
            val runnable = Runnable(action)
            mainHandler.postDelayed(runnable, delay)
            AutoCloseable { mainHandler.removeCallbacks(runnable) }
        },
        now = nowElapsedMillis
    )
    private var cachedSnapshot: CurrentLocationSnapshot? = null
    private var pendingCallbacks: MutableList<(CurrentLocationResult) -> Unit>? = null
    private var timeoutRunnable: Runnable? = null

    fun getCurrentLocation(callback: (CurrentLocationResult) -> Unit): AutoCloseable {
        if (!LocationPermissionUtils.hasForegroundLocationPermission(appContext)) {
            callback(CurrentLocationResult.NoPermission)
            return AutoCloseable {}
        }

        val capability = capabilityProbe.detect()
        if (lastCapability != null && lastCapability != capability) {
            close()
            cachedSnapshot = null
            systemRequests.invalidate()
        }
        lastCapability = capability
        if (capability == GoogleServiceCapability.UNAVAILABLE) return systemRequests.request(callback)
        if (capability == GoogleServiceCapability.UNKNOWN) {
            callback(CurrentLocationResult.Unavailable)
            return AutoCloseable {}
        }

        cachedSnapshot?.takeIf { isFresh(it) }?.let {
            callback(CurrentLocationResult.Success(it))
            return AutoCloseable {}
        }

        val existingCallbacks = pendingCallbacks
        if (existingCallbacks != null) {
            existingCallbacks += callback
            return AutoCloseable { existingCallbacks.remove(callback) }
        }

        val request = mutableListOf(callback)
        pendingCallbacks = request
        try { requestLastLocation(request) } catch (_: RuntimeException) {
            finish(CurrentLocationResult.Unavailable, request)
        }
        return AutoCloseable { request.remove(callback) }
    }

    @SuppressLint("MissingPermission")
    private fun requestLastLocation(request: MutableList<(CurrentLocationResult) -> Unit>) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (pendingCallbacks !== request) return@addOnSuccessListener
                val snapshot = location?.toFreshSnapshot()
                if (snapshot != null) {
                    finish(CurrentLocationResult.Success(snapshot), request)
                } else {
                    requestFreshLocation(request)
                }
            }
            .addOnFailureListener {
                requestFreshLocation(request)
            }
    }

    fun updateSnapshotForTests(snapshot: CurrentLocationSnapshot?) {
        cachedSnapshot = snapshot
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation(request: MutableList<(CurrentLocationResult) -> Unit>) {
        if (pendingCallbacks !== request) return
        val timeout = Runnable {
            finish(CurrentLocationResult.Timeout, request)
        }
        timeoutRunnable = timeout
        mainHandler.postDelayed(timeout, LOCATION_TIMEOUT_MS)

        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (pendingCallbacks !== request) return@addOnSuccessListener
                    val snapshot = location?.toSnapshot()
                    finish(
                        if (snapshot == null) {
                            CurrentLocationResult.Unavailable
                        } else {
                            CurrentLocationResult.Success(snapshot)
                        }, request
                    )
                }
                .addOnFailureListener {
                    finish(CurrentLocationResult.Unavailable, request)
                }
        } catch (_: RuntimeException) {
            finish(CurrentLocationResult.Unavailable, request)
        }
    }

    private fun finish(result: CurrentLocationResult, request: MutableList<(CurrentLocationResult) -> Unit>) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { finish(result, request) }
            return
        }
        if (pendingCallbacks !== request) return
        val callbacks = request.toList()
        pendingCallbacks = null
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        if (result is CurrentLocationResult.Success) {
            cachedSnapshot = result.snapshot
        }
        callbacks.forEach { it(result) }
    }

    override fun close() {
        pendingCallbacks = null
        timeoutRunnable?.let(mainHandler::removeCallbacks)
        timeoutRunnable = null
        systemRequests.close()
    }

    fun onBackground() {
        // 只收束新增的系統後備；正常 Google 請求的既有生命週期不變。
        systemRequests.onBackground()
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
