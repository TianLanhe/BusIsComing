package com.golink.busiscoming

import com.golink.busiscoming.data.location.ForegroundLocationSource
import com.golink.busiscoming.data.location.ForegroundLocationSubscription
import com.golink.busiscoming.data.location.JourneyLocationFix
import com.golink.busiscoming.data.location.RouteDetailLocationController
import com.golink.busiscoming.data.location.RouteDetailLocationEffect
import com.golink.busiscoming.data.location.RouteDetailLocationPermission
import com.golink.busiscoming.data.location.RouteDetailLocationScheduler
import com.golink.busiscoming.data.location.RouteDetailLocationSessionState
import com.golink.busiscoming.data.location.RouteDetailLocationUiState
import com.golink.busiscoming.data.location.RouteJourneyAxisBuilder
import com.golink.busiscoming.data.model.JourneyPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailLocationControllerTest {
    @Test
    fun grantedForegroundStartSubscribesImmediatelyAndStopsOnBackground() {
        val fixture = controllerFixture(permission = RouteDetailLocationPermission.GRANTED)

        fixture.controller.startForeground()
        fixture.controller.stopForeground()

        assertEquals(1, fixture.source.startCount)
        assertTrue(fixture.source.subscriptions.single().closed)
        assertSame(RouteDetailLocationUiState.Inactive, fixture.states.last())
    }

    @Test
    fun ungrantedStartShowsOneSnackbarWithoutOpeningSystemPrompt() {
        val fixture = controllerFixture(permission = RouteDetailLocationPermission.REQUESTABLE)

        fixture.controller.startForeground()
        fixture.controller.startForeground()

        assertEquals(listOf(RouteDetailLocationEffect.ShowPermissionSnackbar), fixture.effects)
        assertEquals(0, fixture.source.startCount)
        assertSame(RouteDetailLocationUiState.WaitingPermission, fixture.states.last())
    }

    @Test
    fun permissionSnackbarActionRequestsPermissionOrOpensSettingsWhenPermanent() {
        var permission = RouteDetailLocationPermission.REQUESTABLE
        val fixture = controllerFixture(permissionProvider = { permission })
        fixture.controller.startForeground()

        fixture.controller.onPermissionAction()
        permission = RouteDetailLocationPermission.PERMANENTLY_DENIED
        fixture.controller.onPermissionAction()

        assertEquals(
            listOf(
                RouteDetailLocationEffect.ShowPermissionSnackbar,
                RouteDetailLocationEffect.RequestPermission,
                RouteDetailLocationEffect.OpenAppSettings
            ),
            fixture.effects
        )
    }

    @Test
    fun systemLocationOffShowsOneRecoveryAndDoesNotStartSource() {
        val fixture = controllerFixture(
            permission = RouteDetailLocationPermission.GRANTED,
            systemLocationEnabled = false
        )

        fixture.controller.startForeground()
        fixture.controller.onReturnedFromSettings()

        assertEquals(listOf(RouteDetailLocationEffect.ShowSystemLocationSnackbar), fixture.effects)
        assertEquals(0, fixture.source.startCount)
        assertSame(RouteDetailLocationUiState.Hidden, fixture.states.last())
    }

    @Test
    fun firstFixTimeoutNotifiesOnceButKeepsSubscriptionAlive() {
        val fixture = controllerFixture(permission = RouteDetailLocationPermission.GRANTED)
        fixture.controller.startForeground()

        fixture.scheduler.runAll()
        fixture.scheduler.runAll()

        assertEquals(listOf(RouteDetailLocationEffect.ShowFirstFixTimeout), fixture.effects)
        assertFalse(fixture.source.subscriptions.single().closed)
        assertSame(RouteDetailLocationUiState.WaitingFix, fixture.states.last())
    }

    @Test
    fun permissionRaceDuringSourceStartFallsBackToFirstFixTimeout() {
        val source = FakeLocationSource(startFailure = SecurityException("permission revoked"))
        val fixture = controllerFixture(source = source)

        fixture.controller.startForeground()
        fixture.scheduler.runAll()

        assertEquals(1, source.startCount)
        assertTrue(fixture.effects.contains(RouteDetailLocationEffect.ShowFirstFixTimeout))
        assertSame(RouteDetailLocationUiState.WaitingFix, fixture.states.last())
    }

    @Test
    fun lateFixFromClosedSubscriptionCannotUpdateNewForegroundGeneration() {
        val fixture = controllerFixture(permission = RouteDetailLocationPermission.GRANTED)
        val axis = RouteJourneyAxisBuilder().build(RouteJourneyFixtures.input())
        fixture.controller.updateAxis(axis)
        fixture.controller.startForeground()
        val oldSubscription = fixture.source.subscriptions.single()
        fixture.controller.stopForeground()
        fixture.controller.startForeground()

        oldSubscription.emit(fix(0.0020))

        assertTrue(fixture.states.none { it is RouteDetailLocationUiState.Visible })
    }

    @Test
    fun latestFixWaitsForAxisThenPublishesOneVisibleState() {
        val fixture = controllerFixture(permission = RouteDetailLocationPermission.GRANTED)
        fixture.controller.startForeground()
        fixture.source.subscriptions.single().emit(fix(0.0020))
        assertSame(RouteDetailLocationUiState.Hidden, fixture.states.last())

        fixture.controller.updateAxis(RouteJourneyAxisBuilder().build(RouteJourneyFixtures.input()))
        fixture.source.subscriptions.single().emit(fix(0.0020))

        val visible = fixture.states.filterIsInstance<RouteDetailLocationUiState.Visible>()
        assertEquals(1, visible.size)
        assertTrue(visible.single().position is JourneyPosition.AtNode)
    }

    @Test
    fun unreliableFixHidesImmediatelyAndSameVisibleResultIsDeduplicated() {
        val fixture = controllerFixture(permission = RouteDetailLocationPermission.GRANTED)
        fixture.controller.updateAxis(RouteJourneyAxisBuilder().build(RouteJourneyFixtures.input()))
        fixture.controller.startForeground()
        val subscription = fixture.source.subscriptions.single()

        subscription.emit(fix(0.0020))
        subscription.emit(fix(0.0020))
        subscription.emit(fix(0.0020, accuracy = 100f))

        assertEquals(1, fixture.states.filterIsInstance<RouteDetailLocationUiState.Visible>().size)
        assertSame(RouteDetailLocationUiState.Hidden, fixture.states.last())
    }

    @Test
    fun visiblePositionHidesWhenLatestFixExpiresWithoutAnotherCallback() {
        val fixture = controllerFixture(permission = RouteDetailLocationPermission.GRANTED)
        fixture.controller.updateAxis(RouteJourneyAxisBuilder().build(RouteJourneyFixtures.input()))
        fixture.controller.startForeground()
        fixture.source.subscriptions.single().emit(fix(0.0020))
        assertTrue(fixture.states.last() is RouteDetailLocationUiState.Visible)

        fixture.scheduler.runAll()

        assertSame(RouteDetailLocationUiState.Hidden, fixture.states.last())
    }

    @Test
    fun restoredConfigurationSessionDoesNotRepeatPermissionSnackbar() {
        val first = controllerFixture(permission = RouteDetailLocationPermission.REQUESTABLE)
        first.controller.startForeground()

        val recreated = controllerFixture(
            permission = RouteDetailLocationPermission.REQUESTABLE,
            restoredSessionState = first.controller.sessionState()
        )
        recreated.controller.startForeground()

        assertTrue(recreated.effects.isEmpty())
        assertSame(RouteDetailLocationUiState.WaitingPermission, recreated.states.last())
    }

    @Test
    fun availabilityLossClearsOldFixBeforeAxisUpdatesAndFreshRestartTimeout() {
        var systemLocationEnabled = true
        val fixture = controllerFixture(
            systemLocationProvider = { systemLocationEnabled }
        )
        fixture.controller.updateAxis(RouteJourneyAxisBuilder().build(RouteJourneyFixtures.input()))
        fixture.controller.startForeground()
        fixture.source.subscriptions.single().emit(fix(0.0020))
        assertTrue(fixture.states.last() is RouteDetailLocationUiState.Visible)

        systemLocationEnabled = false
        fixture.controller.onReturnedFromSettings()
        fixture.controller.updateAxis(
            RouteJourneyAxisBuilder().build(
                RouteJourneyFixtures.input(
                    walkingSegments = mapOf(
                        "origin" to RouteJourneyFixtures.walkingRoute(0.0 to 0.0008),
                        "destination" to RouteJourneyFixtures.walkingRoute(0.0030 to 0.0040)
                    )
                )
            )
        )
        assertSame(RouteDetailLocationUiState.Hidden, fixture.states.last())

        systemLocationEnabled = true
        fixture.controller.onReturnedFromSettings()
        fixture.scheduler.runAll()

        assertEquals(2, fixture.source.startCount)
        assertTrue(fixture.effects.contains(RouteDetailLocationEffect.ShowFirstFixTimeout))
        assertSame(RouteDetailLocationUiState.WaitingFix, fixture.states.last())
    }

    private fun controllerFixture(
        permission: RouteDetailLocationPermission = RouteDetailLocationPermission.GRANTED,
        permissionProvider: () -> RouteDetailLocationPermission = { permission },
        systemLocationEnabled: Boolean = true,
        systemLocationProvider: () -> Boolean = { systemLocationEnabled },
        source: FakeLocationSource = FakeLocationSource(),
        restoredSessionState: RouteDetailLocationSessionState? = null
    ): ControllerFixture {
        val scheduler = FakeScheduler()
        val states = mutableListOf<RouteDetailLocationUiState>()
        val effects = mutableListOf<RouteDetailLocationEffect>()
        val controller = RouteDetailLocationController(
            pageGeneration = 7L,
            source = source,
            permission = permissionProvider,
            systemLocationEnabled = systemLocationProvider,
            scheduler = scheduler,
            nowElapsedMillis = { 100_000L },
            onState = states::add,
            onEffect = effects::add,
            restoredSessionState = restoredSessionState
        )
        return ControllerFixture(controller, source, scheduler, states, effects)
    }

    private fun fix(longitudeOffset: Double, accuracy: Float = 8f): JourneyLocationFix =
        JourneyLocationFix(
            latitude = RouteJourneyFixtures.BASE_LATITUDE,
            longitude = RouteJourneyFixtures.BASE_LONGITUDE + longitudeOffset,
            accuracyMeters = accuracy,
            elapsedRealtimeMillis = 100_000L
        )

    private data class ControllerFixture(
        val controller: RouteDetailLocationController,
        val source: FakeLocationSource,
        val scheduler: FakeScheduler,
        val states: MutableList<RouteDetailLocationUiState>,
        val effects: MutableList<RouteDetailLocationEffect>
    )

    private class FakeLocationSource(
        private val startFailure: SecurityException? = null
    ) : ForegroundLocationSource {
        var startCount = 0
        val subscriptions = mutableListOf<FakeSubscription>()

        override fun start(onLocation: (JourneyLocationFix) -> Unit): ForegroundLocationSubscription {
            startCount += 1
            startFailure?.let { throw it }
            return FakeSubscription(onLocation).also(subscriptions::add)
        }
    }

    private class FakeSubscription(
        private val callback: (JourneyLocationFix) -> Unit
    ) : ForegroundLocationSubscription {
        var closed = false

        fun emit(fix: JourneyLocationFix) {
            callback(fix)
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeScheduler : RouteDetailLocationScheduler {
        private val pending = mutableListOf<Scheduled>()

        override fun schedule(delayMillis: Long, block: () -> Unit): ForegroundLocationSubscription {
            return Scheduled(block).also(pending::add)
        }

        fun runAll() {
            val ready = pending.toList()
            pending.clear()
            ready.filterNot(Scheduled::closed).forEach { it.block() }
        }

        private class Scheduled(val block: () -> Unit) : ForegroundLocationSubscription {
            var closed = false
            override fun close() {
                closed = true
            }
        }
    }
}
