package com.golink.busiscoming.data.local

import android.content.Context
import java.util.concurrent.CopyOnWriteArraySet

enum class RouteAutoRefreshInterval(val millis: Long?) {
    OFF(null),
    MINUTES_1(60_000L),
    MINUTES_2(120_000L),
    MINUTES_5(300_000L),
    MINUTES_10(600_000L)
}

interface AutoRefreshKeyValueStore {
    fun getString(key: String): String?
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
}

class RouteAutoRefreshSettingsStore private constructor(
    private val values: AutoRefreshKeyValueStore,
    private val notifyChanges: Boolean
) {
    constructor(context: Context) : this(SharedPreferencesAutoRefreshStore(context), true)
    constructor(values: AutoRefreshKeyValueStore) : this(values, false)

    fun getInterval(): RouteAutoRefreshInterval = values.getString(KEY_INTERVAL)
        ?.let { stored -> RouteAutoRefreshInterval.entries.firstOrNull { it.name == stored } }
        ?: RouteAutoRefreshInterval.MINUTES_1

    fun setInterval(interval: RouteAutoRefreshInterval) {
        values.putString(KEY_INTERVAL, interval.name)
        values.putBoolean(KEY_EXPLICIT_SELECTION, true)
        if (notifyChanges) RouteAutoRefreshSettingsEvents.publish(interval)
    }

    fun hasExplicitSelection(): Boolean = values.getBoolean(KEY_EXPLICIT_SELECTION, false)

    companion object {
        const val PREFERENCE_FILE = "bus_is_coming_auto_refresh"
        const val KEY_INTERVAL = "route_auto_refresh_interval"
        const val KEY_EXPLICIT_SELECTION = "route_auto_refresh_explicit_selection"
    }
}

class AutoRefreshNoticeStore(private val values: AutoRefreshKeyValueStore) {
    constructor(context: Context) : this(SharedPreferencesAutoRefreshStore(context))

    fun isComplete(): Boolean = values.getBoolean(KEY_NOTICE_COMPLETE, false)

    fun shouldShow(hasExplicitSettingSelection: Boolean): Boolean =
        !isComplete() && !hasExplicitSettingSelection

    fun complete() {
        values.putBoolean(KEY_NOTICE_COMPLETE, true)
    }

    private companion object {
        const val KEY_NOTICE_COMPLETE = "route_auto_refresh_notice_complete"
    }
}

object RouteAutoRefreshSettingsEvents {
    private val listeners = CopyOnWriteArraySet<(RouteAutoRefreshInterval) -> Unit>()

    fun observe(listener: (RouteAutoRefreshInterval) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    internal fun publish(interval: RouteAutoRefreshInterval) {
        listeners.forEach { listener -> runCatching { listener(interval) } }
    }
}

private class SharedPreferencesAutoRefreshStore(context: Context) : AutoRefreshKeyValueStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        RouteAutoRefreshSettingsStore.PREFERENCE_FILE,
        Context.MODE_PRIVATE
    )

    override fun getString(key: String): String? = preferences.getString(key, null)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
}
