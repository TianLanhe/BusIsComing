package com.golink.busiscoming

import com.golink.busiscoming.data.local.AutoRefreshKeyValueStore
import com.golink.busiscoming.data.local.AutoRefreshNoticeStore
import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import com.golink.busiscoming.data.local.RouteAutoRefreshSettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAutoRefreshSettingsStoreTest {
    @Test
    fun missingAndUnknownValuesDefaultToOneMinute() {
        val values = FakeValues()
        val store = RouteAutoRefreshSettingsStore(values)

        assertEquals(RouteAutoRefreshInterval.MINUTES_1, store.getInterval())
        values.putString("route_auto_refresh_interval", "future")
        assertEquals(RouteAutoRefreshInterval.MINUTES_1, store.getInterval())
    }

    @Test
    fun everySupportedValuePersistsAndExplicitReselectionIsRecorded() {
        val values = FakeValues()
        val store = RouteAutoRefreshSettingsStore(values)

        RouteAutoRefreshInterval.entries.forEach { interval ->
            store.setInterval(interval)
            assertEquals(interval, RouteAutoRefreshSettingsStore(values).getInterval())
            assertTrue(store.hasExplicitSelection())
        }
    }

    @Test
    fun noticeCompletesOnlyAfterAnExplicitCompletionEvent() {
        val values = FakeValues()
        val notice = AutoRefreshNoticeStore(values)

        assertFalse(notice.isComplete())
        assertTrue(notice.shouldShow(hasExplicitSettingSelection = false))
        assertFalse(notice.shouldShow(hasExplicitSettingSelection = true))

        notice.complete()

        assertTrue(AutoRefreshNoticeStore(values).isComplete())
        assertFalse(notice.shouldShow(hasExplicitSettingSelection = false))
    }

    private class FakeValues : AutoRefreshKeyValueStore {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String): String? = values[key] as? String
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key] as? Boolean ?: defaultValue

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
