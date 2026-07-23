package com.golink.busiscoming

import com.golink.busiscoming.ui.main.XiaomiShortcutPermissionAction
import com.golink.busiscoming.ui.main.XiaomiShortcutPermissionPolicy
import com.golink.busiscoming.ui.main.XiaomiShortcutPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaomiShortcutPermissionPolicyTest {
    @Test
    fun `xiaomi redmi and poco identities are recognized case insensitively`() {
        assertTrue(policy(" Xiaomi ", "unknown").isXiaomiFamily)
        assertTrue(policy("unknown", "REDMI").isXiaomiFamily)
        assertTrue(policy("unknown", "poco").isXiaomiFamily)
        assertFalse(policy("Google", "Pixel").isXiaomiFamily)
    }

    @Test
    fun `unknown or denied Xiaomi permission opens settings before pin request`() {
        assertEquals(
            XiaomiShortcutPermissionAction.OPEN_SETTINGS,
            policy("Xiaomi", "Xiaomi", XiaomiShortcutPermissionState.UNKNOWN)
                .action(gatePassed = false, bypassPermissionGate = false)
        )
        assertEquals(
            XiaomiShortcutPermissionAction.OPEN_SETTINGS,
            policy("Xiaomi", "Xiaomi", XiaomiShortcutPermissionState.DENIED)
                .action(gatePassed = false, bypassPermissionGate = false)
        )
    }

    @Test
    fun `trusted grant persisted gate or one-shot resume can request pin`() {
        assertEquals(
            XiaomiShortcutPermissionAction.REQUEST_PIN,
            policy("Xiaomi", "Xiaomi", XiaomiShortcutPermissionState.GRANTED)
                .action(gatePassed = false, bypassPermissionGate = false)
        )
        assertEquals(
            XiaomiShortcutPermissionAction.REQUEST_PIN,
            policy("Xiaomi", "Xiaomi", XiaomiShortcutPermissionState.UNKNOWN)
                .action(gatePassed = true, bypassPermissionGate = false)
        )
        assertEquals(
            XiaomiShortcutPermissionAction.REQUEST_PIN,
            policy("Xiaomi", "Xiaomi", XiaomiShortcutPermissionState.UNKNOWN)
                .action(gatePassed = false, bypassPermissionGate = true)
        )
    }

    @Test
    fun `non Xiaomi devices always use Android standard pin flow`() {
        assertEquals(
            XiaomiShortcutPermissionAction.REQUEST_PIN,
            policy("Google", "Pixel", XiaomiShortcutPermissionState.DENIED)
                .action(gatePassed = false, bypassPermissionGate = false)
        )
    }

    private fun policy(
        manufacturer: String,
        brand: String,
        state: XiaomiShortcutPermissionState = XiaomiShortcutPermissionState.UNKNOWN
    ): XiaomiShortcutPermissionPolicy {
        return XiaomiShortcutPermissionPolicy(manufacturer, brand) { state }
    }

}
