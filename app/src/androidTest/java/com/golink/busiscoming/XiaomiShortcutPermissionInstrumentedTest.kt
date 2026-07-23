package com.golink.busiscoming

import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.ui.main.XiaomiShortcutPermissionNavigationResult
import com.golink.busiscoming.ui.main.XiaomiShortcutPermissionNavigator
import com.golink.busiscoming.ui.main.XiaomiShortcutPermissionStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class XiaomiShortcutPermissionInstrumentedTest {
    @Test
    fun navigatorPrefersXiaomiPermissionPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = mutableListOf<Intent>()
        val navigator = XiaomiShortcutPermissionNavigator(
            resolver = { _, intent -> intent.action == "miui.intent.action.APP_PERM_EDITOR" }
        )

        val result = navigator.open(context) { intent -> started += intent }

        assertEquals(XiaomiShortcutPermissionNavigationResult.XIAOMI_SETTINGS, result)
        assertEquals(1, started.size)
        assertEquals("com.miui.securitycenter", started.single().`package`)
        assertEquals(context.packageName, started.single().getStringExtra("extra_pkgname"))
    }

    @Test
    fun navigatorFallsBackToAppDetailsWhenXiaomiPageCannotStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = mutableListOf<Intent>()
        val navigator = XiaomiShortcutPermissionNavigator(
            resolver = { _, _ -> true }
        )

        val result = navigator.open(context) { intent ->
            if (intent.action == "miui.intent.action.APP_PERM_EDITOR") {
                throw SecurityException("blocked")
            }
            started += intent
        }

        assertEquals(XiaomiShortcutPermissionNavigationResult.APP_DETAILS, result)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.single().action)
        assertEquals("package:${context.packageName}", started.single().data.toString())
    }

    @Test
    fun pendingPinRequestsAreConsumedOnlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = XiaomiShortcutPermissionStateStore(context)
        store.clearGate()
        store.consumePinRequestPending()

        store.markPinRequestPending()
        assertTrue(store.consumePinRequestPending())
        assertFalse(store.consumePinRequestPending())

        store.markGatePassed()
        assertTrue(store.isGatePassed())
        store.clearGate()
    }
}
