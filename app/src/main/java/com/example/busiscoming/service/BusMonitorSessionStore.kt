package com.example.busiscoming.service

import android.content.Context
import com.example.busiscoming.data.model.BusMonitorSessionSnapshot
import com.example.busiscoming.data.model.BusMonitorSessionSnapshotCodec

class BusMonitorSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun save(snapshot: BusMonitorSessionSnapshot) {
        val encoded = BusMonitorSessionSnapshotCodec.encode(snapshot)
        preferences.edit().clear().apply {
            encoded.forEach { (key, value) -> putString(key, value) }
        }.apply()
    }

    fun load(): BusMonitorSessionSnapshot? {
        val values = preferences.all.mapNotNull { (key, value) ->
            (value as? String)?.let { key to it }
        }.toMap()
        return BusMonitorSessionSnapshotCodec.decode(values)
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "bus_monitor_session"
    }
}
