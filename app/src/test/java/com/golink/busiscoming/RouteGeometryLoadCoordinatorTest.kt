package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometryPoint
import com.golink.busiscoming.data.model.RouteGeometrySegment
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
    fun candidateWaitsForEndpointsThenPublishesWithoutAnotherLoad() {
        val coordinator = RouteGeometryLoadCoordinator(listOf(first))
        val segment = segment(first)
        coordinator.beginGeneration(first, 1)

        val early = coordinator.onCandidate(first, 1, segment, endpointsAvailable = false)
        assertEquals(null, early)
        assertEquals(RouteGeometryLoadState.CANDIDATE, coordinator.state(first))
        assertEquals(segment, coordinator.candidate(first, 1))

        val published = coordinator.onValidated(first, 1)
        assertEquals(segment, published)
        assertEquals(RouteGeometryLoadState.LOADED, coordinator.state(first))
        assertTrue(coordinator.failedKeys().isEmpty())
    }

    @Test
    fun lateEndpointFailureNeverPublishesCandidateAndConsumersStayIndependent() {
        val firstConsumer = RouteGeometryLoadCoordinator(listOf(first))
        val secondConsumer = RouteGeometryLoadCoordinator(listOf(first))
        val segment = segment(first)
        firstConsumer.beginGeneration(first, 1)
        secondConsumer.beginGeneration(first, 1)
        firstConsumer.onCandidate(first, 1, segment, endpointsAvailable = false)
        secondConsumer.onCandidate(first, 1, segment, endpointsAvailable = false)

        assertEquals(segment, firstConsumer.onValidated(first, 1))
        assertEquals(
            RouteGeometryRetryDecision.FAILED,
            secondConsumer.onFailure(first, 1, IllegalArgumentException("wrong endpoints"), allowAutoRetry = false)
        )

        assertEquals(RouteGeometryLoadState.LOADED, firstConsumer.state(first))
        assertEquals(RouteGeometryLoadState.FAILED, secondConsumer.state(first))
        assertEquals(null, secondConsumer.candidate(first, 1))
    }

    @Test
    fun oldFailureCannotDemoteNewGenerationSuccess() {
        val coordinator = RouteGeometryLoadCoordinator(listOf(first))
        val segment = segment(first)
        coordinator.beginGeneration(first, 1)
        coordinator.onCandidate(first, 1, segment, endpointsAvailable = false)
        coordinator.beginGeneration(first, 2)
        coordinator.onCandidate(first, 2, segment, endpointsAvailable = true)

        coordinator.onFailure(first, 1, IOException("late"), allowAutoRetry = false)

        assertEquals(RouteGeometryLoadState.LOADED, coordinator.state(first))
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

    private fun segment(key: RouteGeometryKey) = RouteGeometrySegment(
        key,
        listOf(
            RouteGeometryPoint("a", 22.3, 114.1),
            RouteGeometryPoint("b", 22.4, 114.2)
        )
    )

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
