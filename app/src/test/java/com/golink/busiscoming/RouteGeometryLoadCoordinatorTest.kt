package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.ui.main.RouteGeometryLoadCoordinator
import com.golink.busiscoming.ui.main.RouteGeometryLoadState
import com.golink.busiscoming.ui.main.RouteGeometryRetryDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RouteGeometryLoadCoordinatorTest {
    private val first = RouteGeometryKey("FIRST", 1, 2)
    private val second = RouteGeometryKey("SECOND", 3, 4)

    @Test
    fun candidateCanBeShownBeforeDetailThenValidatedWithoutAnotherLoad() {
        val coordinator = RouteGeometryLoadCoordinator(listOf(first))

        coordinator.onCandidate(first, endpointsAvailable = false)
        assertEquals(RouteGeometryLoadState.CANDIDATE, coordinator.state(first))

        coordinator.onValidated(first)
        assertEquals(RouteGeometryLoadState.LOADED, coordinator.state(first))
        assertTrue(coordinator.failedKeys().isEmpty())
    }

    @Test
    fun recoverableFailureRetriesOnceAndPermanentFailureStaysLocal() {
        val coordinator = RouteGeometryLoadCoordinator(listOf(first, second))

        assertEquals(
            RouteGeometryRetryDecision.AUTO_RETRY,
            coordinator.onFailure(first, IOException("temporary"))
        )
        coordinator.onCandidate(first, endpointsAvailable = true)
        assertEquals(RouteGeometryLoadState.LOADED, coordinator.state(first))

        assertEquals(
            RouteGeometryRetryDecision.AUTO_RETRY,
            coordinator.onFailure(second, IOException("temporary"))
        )
        assertEquals(
            RouteGeometryRetryDecision.FAILED,
            coordinator.onFailure(second, IOException("still unavailable"))
        )
        assertEquals(setOf(second), coordinator.failedKeys())

        coordinator.beginManualRetry(setOf(second))
        assertEquals(RouteGeometryLoadState.LOADING, coordinator.state(second))
        assertEquals(RouteGeometryLoadState.LOADED, coordinator.state(first))
    }

    @Test
    fun backgroundFailureDoesNotStartAutomaticRetry() {
        val coordinator = RouteGeometryLoadCoordinator(listOf(first))

        assertEquals(
            RouteGeometryRetryDecision.FAILED,
            coordinator.onFailure(first, IOException("temporary"), allowAutoRetry = false)
        )
        assertEquals(RouteGeometryLoadState.FAILED, coordinator.state(first))
        assertEquals(setOf(first), coordinator.failedKeys())
    }
}
