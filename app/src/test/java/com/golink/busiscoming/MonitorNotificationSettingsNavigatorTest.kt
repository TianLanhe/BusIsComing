package com.golink.busiscoming

import com.golink.busiscoming.ui.main.MonitorNotificationSettingsKind
import com.golink.busiscoming.ui.main.MonitorNotificationSettingsNavigationResult
import com.golink.busiscoming.ui.main.MonitorNotificationSettingsNavigator
import com.golink.busiscoming.ui.main.MonitorNotificationSettingsRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonitorNotificationSettingsNavigatorTest {
    @Test
    fun opensSpecificChannelWithPackageAndChannelIdFirst() {
        val started = mutableListOf<MonitorNotificationSettingsRequest>()
        val navigator = navigator(
            canOpen = { true },
            openRequest = { started += it }
        )

        val result = navigator.open("bus_monitor_status_v2")

        assertEquals(MonitorNotificationSettingsNavigationResult.CHANNEL, result)
        assertEquals(MonitorNotificationSettingsKind.CHANNEL, started.single().kind)
        assertEquals("com.golink.busiscoming", started.single().packageName)
        assertEquals("bus_monitor_status_v2", started.single().channelId)
    }

    @Test
    fun fallsBackFromChannelToAppNotifications() {
        val resolved = mutableListOf<MonitorNotificationSettingsKind>()
        val started = mutableListOf<MonitorNotificationSettingsRequest>()
        val navigator = navigator(
            canOpen = {
                resolved += it.kind
                it.kind == MonitorNotificationSettingsKind.APP_NOTIFICATIONS
            },
            openRequest = { started += it }
        )

        val result = navigator.open("bus_monitor_status_v2")

        assertEquals(MonitorNotificationSettingsNavigationResult.APP_NOTIFICATIONS, result)
        assertEquals(
            listOf(
                MonitorNotificationSettingsKind.CHANNEL,
                MonitorNotificationSettingsKind.APP_NOTIFICATIONS
            ),
            resolved
        )
        assertEquals(MonitorNotificationSettingsKind.APP_NOTIFICATIONS, started.single().kind)
    }

    @Test
    fun starterFailureContinuesToAppDetails() {
        val started = mutableListOf<MonitorNotificationSettingsKind>()
        val navigator = navigator(
            canOpen = { true },
            openRequest = {
                started += it.kind
                if (it.kind != MonitorNotificationSettingsKind.APP_DETAILS) {
                    throw SecurityException("blocked")
                }
            }
        )

        val result = navigator.open("bus_monitor_status_v2")

        assertEquals(MonitorNotificationSettingsNavigationResult.APP_DETAILS, result)
        assertEquals(
            listOf(
                MonitorNotificationSettingsKind.CHANNEL,
                MonitorNotificationSettingsKind.APP_NOTIFICATIONS,
                MonitorNotificationSettingsKind.APP_DETAILS
            ),
            started
        )
    }

    @Test
    fun resolverFailureAndUnavailableTargetsReturnManualGuidance() {
        val navigator = navigator(
            canOpen = { throw IllegalStateException("resolver failed") },
            openRequest = { error("must not start") }
        )

        assertEquals(
            MonitorNotificationSettingsNavigationResult.MANUAL_GUIDANCE,
            navigator.open("bus_monitor_alert_v2")
        )
    }

    @Test
    fun appWideProblemSkipsChannelAndDetailsUsesPackageUri() {
        val resolved = mutableListOf<MonitorNotificationSettingsRequest>()
        val started = mutableListOf<MonitorNotificationSettingsRequest>()
        val navigator = navigator(
            canOpen = {
                resolved += it
                it.kind == MonitorNotificationSettingsKind.APP_DETAILS
            },
            openRequest = { started += it }
        )

        val result = navigator.open(channelId = null)

        assertEquals(MonitorNotificationSettingsNavigationResult.APP_DETAILS, result)
        assertEquals(
            listOf(
                MonitorNotificationSettingsKind.APP_NOTIFICATIONS,
                MonitorNotificationSettingsKind.APP_DETAILS
            ),
            resolved.map { it.kind }
        )
        assertNull(started.single().channelId)
        assertEquals("package:com.golink.busiscoming", started.single().dataUri)
    }

    @Test
    fun legacyPlatformSkipsSpecificChannelTarget() {
        val resolved = mutableListOf<MonitorNotificationSettingsKind>()
        val navigator = MonitorNotificationSettingsNavigator(
            packageName = "com.golink.busiscoming",
            sdkInt = 25,
            canOpen = {
                resolved += it.kind
                true
            },
            openRequest = {}
        )

        assertEquals(
            MonitorNotificationSettingsNavigationResult.APP_NOTIFICATIONS,
            navigator.open("bus_monitor_status_v2")
        )
        assertEquals(listOf(MonitorNotificationSettingsKind.APP_NOTIFICATIONS), resolved)
    }

    private fun navigator(
        canOpen: (MonitorNotificationSettingsRequest) -> Boolean,
        openRequest: (MonitorNotificationSettingsRequest) -> Unit
    ) = MonitorNotificationSettingsNavigator(
        packageName = "com.golink.busiscoming",
        sdkInt = 36,
        canOpen = canOpen,
        openRequest = openRequest
    )
}
