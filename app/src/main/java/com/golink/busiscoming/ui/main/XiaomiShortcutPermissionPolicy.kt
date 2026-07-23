package com.golink.busiscoming.ui.main

import android.content.Context
import android.os.Build

enum class XiaomiShortcutPermissionState {
    GRANTED,
    DENIED,
    UNKNOWN
}

enum class XiaomiShortcutPermissionAction {
    OPEN_SETTINGS,
    REQUEST_PIN
}

class XiaomiShortcutPermissionPolicy(
    manufacturer: String = Build.MANUFACTURER.orEmpty(),
    brand: String = Build.BRAND.orEmpty(),
    private val trustedStateReader: () -> XiaomiShortcutPermissionState = {
        XiaomiShortcutPermissionState.UNKNOWN
    }
) {
    val isXiaomiFamily: Boolean = listOf(manufacturer, brand)
        .map(::normalizeIdentity)
        .any { it in XIAOMI_IDENTITIES }

    fun action(
        gatePassed: Boolean,
        bypassPermissionGate: Boolean
    ): XiaomiShortcutPermissionAction {
        if (!isXiaomiFamily || gatePassed || bypassPermissionGate) {
            return XiaomiShortcutPermissionAction.REQUEST_PIN
        }
        return if (trustedStateReader() == XiaomiShortcutPermissionState.GRANTED) {
            XiaomiShortcutPermissionAction.REQUEST_PIN
        } else {
            XiaomiShortcutPermissionAction.OPEN_SETTINGS
        }
    }

    private fun normalizeIdentity(value: String): String = value.trim().lowercase()

    private companion object {
        val XIAOMI_IDENTITIES = setOf("xiaomi", "redmi", "poco")
    }
}

class XiaomiShortcutPermissionStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isGatePassed(): Boolean = preferences.getBoolean(KEY_GATE_PASSED, false)

    fun markGatePassed() {
        preferences.edit()
            .putBoolean(KEY_GATE_PASSED, true)
            .remove(KEY_PIN_REQUEST_PENDING)
            .apply()
    }

    fun clearGate() {
        preferences.edit().remove(KEY_GATE_PASSED).apply()
    }

    fun markPinRequestPending() {
        preferences.edit().putBoolean(KEY_PIN_REQUEST_PENDING, true).apply()
    }

    fun consumePinRequestPending(): Boolean {
        val pending = preferences.getBoolean(KEY_PIN_REQUEST_PENDING, false)
        if (pending) {
            preferences.edit().remove(KEY_PIN_REQUEST_PENDING).apply()
        }
        return pending
    }

    private companion object {
        const val PREFERENCES_NAME = "transit_code_shortcut_permission"
        const val KEY_GATE_PASSED = "xiaomi_gate_passed"
        const val KEY_PIN_REQUEST_PENDING = "pin_request_pending"
    }
}
