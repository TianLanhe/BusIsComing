package com.golink.busiscoming.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SystemForegroundLocationSource(context: Context) : ForegroundLocationSource {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    override fun start(onLocation: (JourneyLocationFix) -> Unit): ForegroundLocationSubscription =
        subscribe(10_000L, 20f, onLocation, {})

    fun request(callback: (CurrentLocationResult) -> Unit): AutoCloseable = subscribe(0L, 0f, { fix ->
        callback(CurrentLocationResult.Success(CurrentLocationSnapshot(
            fix.latitude, fix.longitude, fix.accuracyMeters, fix.elapsedRealtimeMillis
        )))
    }, { callback(CurrentLocationResult.Unavailable) })

    @SuppressLint("MissingPermission")
    private fun subscribe(
        interval: Long,
        distance: Float,
        onLocation: (JourneyLocationFix) -> Unit,
        unavailable: () -> Unit
    ): ForegroundLocationSubscription {
        val manager = context.getSystemService(LocationManager::class.java)
        val active = AtomicBoolean(true)
        val freshness = SystemLocationFixFilter(
            if (interval == 0L) CurrentLocationCoordinator.SNAPSHOT_MAX_AGE_MS
            else RouteJourneyPositionMatcher.MAX_FIX_AGE_MILLIS
        )
        fun deliver(location: Location) {
            val fix = location.toFix()
            if (active.get() && freshness.accept(fix.elapsedRealtimeMillis, SystemClock.elapsedRealtime())) {
                onLocation(fix)
            }
        }
        if (manager == null || !LocationPermissionUtils.hasForegroundLocationPermission(context)) {
            unavailable()
            return ForegroundLocationSubscription {}
        }
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter {
            (it != LocationManager.GPS_PROVIDER || precise) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
        }
        if (providers.isEmpty()) {
            unavailable()
            return ForegroundLocationSubscription {}
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                deliver(location)
            }
            override fun onProviderDisabled(provider: String) {
                if (active.get() && providers.none { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }) unavailable()
            }
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("API 25 相容 callback")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        var registered = false
        providers.forEach { provider ->
            try {
                manager.requestLocationUpdates(provider, interval, distance, listener, Looper.getMainLooper())
                registered = true
            } catch (_: RuntimeException) { /* 個別 provider 不可用，仍可使用其他真實來源。 */ }
        }
        if (!registered) {
            active.set(false)
            runCatching { manager.removeUpdates(listener) }
            unavailable()
        } else {
            lastFixExecutor.execute {
                val fix = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                    .maxByOrNull { it.elapsedRealtimeNanos }
                handler.post { if (fix != null) deliver(fix) }
            }
        }
        return ForegroundLocationSubscription {
            if (active.getAndSet(false)) runCatching { manager.removeUpdates(listener) }
        }
    }

    private fun Location.toFix() = JourneyLocationFix(
        latitude, longitude, if (hasAccuracy()) accuracy else null, elapsedRealtimeNanos / 1_000_000L
    )

    companion object {
        private val lastFixExecutor = Executors.newSingleThreadExecutor()
    }
}

/** 每次前台訂閱重判，正常 Fused 的參數與交付規則維持不變。 */
class CapabilityForegroundLocationSource(
    context: Context,
    private val probe: GoogleServiceCapabilityProbe = AndroidGoogleServiceCapabilityProbe(context),
    private val google: () -> ForegroundLocationSource = { FusedForegroundLocationSource(context) },
    private val system: () -> ForegroundLocationSource = { SystemForegroundLocationSource(context) }
) : ForegroundLocationSource {
    private val googleSource by lazy(google)
    private val systemSource by lazy(system)

    override fun start(onLocation: (JourneyLocationFix) -> Unit): ForegroundLocationSubscription = try {
        probe.detect().select(
            google = { googleSource.start(onLocation) },
            system = { systemSource.start(onLocation) },
            unknown = { ForegroundLocationSubscription {} }
        )
    } catch (_: RuntimeException) { ForegroundLocationSubscription {} }
}
