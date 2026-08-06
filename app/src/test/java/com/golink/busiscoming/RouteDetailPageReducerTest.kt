package com.golink.busiscoming

import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometryPoint
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.RouteDetailWalkingState
import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.ui.main.ProgressiveValue
import com.golink.busiscoming.ui.main.RouteDetailPageEvent
import com.golink.busiscoming.ui.main.RouteDetailPageReducer
import com.golink.busiscoming.ui.main.RouteDetailPageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailPageReducerTest {
    private val keyA = RouteGeometryKey("A", 1, 2)
    private val keyB = RouteGeometryKey("B", 3, 4)

    @Test
    fun independentDomainsConvergeRegardlessOfCompletionOrder() {
        val events = listOf(
            RouteDetailPageEvent.MapReady(PAGE, 1),
            RouteDetailPageEvent.DetailCacheAvailable(PAGE, detail("cached")),
            RouteDetailPageEvent.DetailStarted(PAGE, 1),
            RouteDetailPageEvent.DetailSucceeded(PAGE, 1, detail("fresh")),
            RouteDetailPageEvent.EtaStarted(PAGE, 1),
            RouteDetailPageEvent.EtaSucceeded(PAGE, 1, WaitTimeState.Available(6)),
            RouteDetailPageEvent.GeometryStarted(PAGE, keyA, 1),
            RouteDetailPageEvent.GeometrySucceeded(PAGE, keyA, 1, geometry(keyA)),
            RouteDetailPageEvent.GeometryStarted(PAGE, keyB, 1),
            RouteDetailPageEvent.GeometrySucceeded(PAGE, keyB, 1, geometry(keyB))
        )
        val first = reduce(events)
        val second = reduce(
            listOf(events[6], events[8], events[4], events[0], events[1], events[2], events[9], events[5], events[7], events[3])
        )

        assertEquals(first, second)
        assertEquals("fresh", first.detail.valueOrNull()?.routeName)
        assertEquals(setOf(keyA, keyB), first.successfulGeometries.keys)
        assertEquals(6, (first.eta.valueOrNull() as WaitTimeState.Available).minutes)
    }

    @Test
    fun stalePageOrDomainEventsAndOldErrorsCannotDemoteSuccess() {
        var state = initial()
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailStarted(PAGE, 1))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailStarted(PAGE, 2))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailSucceeded(PAGE, 2, detail("new")))
        val success = state

        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailFailed(PAGE, 1, "old"))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailFailed(PAGE - 1, 2, "old page"))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailStarted(PAGE, 1))

        assertSame(success, state)
        assertEquals("new", state.detail.valueOrNull()?.routeName)
    }

    @Test
    fun cacheSurvivesNetworkFailureAndRetryKeepsOtherGeometrySuccess() {
        var state = initial()
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailCacheAvailable(PAGE, detail("cached")))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailStarted(PAGE, 1))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.DetailFailed(PAGE, 1, "network"))
        assertEquals("cached", state.detail.valueOrNull()?.routeName)
        assertTrue(state.detail is ProgressiveValue.Failure)

        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.GeometryStarted(PAGE, keyA, 1))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.GeometryFailed(PAGE, keyA, 1, "bad A"))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.GeometryStarted(PAGE, keyB, 1))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.GeometrySucceeded(PAGE, keyB, 1, geometry(keyB)))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.GeometryStarted(PAGE, keyA, 2))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.GeometrySucceeded(PAGE, keyA, 2, geometry(keyA)))
        state = RouteDetailPageReducer.reduce(state, RouteDetailPageEvent.GeometryFailed(PAGE, keyA, 1, "late old error"))

        assertEquals(setOf(keyA, keyB), state.successfulGeometries.keys)
        assertEquals(1, state.geometryGenerations.getValue(keyB))
        assertEquals(2, state.geometryGenerations.getValue(keyA))
    }

    @Test
    fun walkingSegmentsReplaceByStableIdAndRejectOldGenerationWithoutIncrementalAccumulation() {
        var state = initial()
        state = RouteDetailPageReducer.reduce(
            state,
            RouteDetailPageEvent.WalkingStarted(
                PAGE,
                1,
                mapOf(
                    "origin" to RouteDetailWalkingState.Loading,
                    "destination" to RouteDetailWalkingState.Loading
                )
            )
        )
        val success = RouteDetailWalkingState.CsdiSuccess(pedestrianRoute())
        state = RouteDetailPageReducer.reduce(
            state,
            RouteDetailPageEvent.WalkingSegmentChanged(PAGE, 1, "origin", success)
        )
        state = RouteDetailPageReducer.reduce(
            state,
            RouteDetailPageEvent.WalkingSegmentChanged(PAGE, 1, "origin", success)
        )
        state = RouteDetailPageReducer.reduce(
            state,
            RouteDetailPageEvent.WalkingStarted(
                PAGE,
                2,
                mapOf("origin" to RouteDetailWalkingState.Loading)
            )
        )
        state = RouteDetailPageReducer.reduce(
            state,
            RouteDetailPageEvent.WalkingSegmentChanged(PAGE, 1, "destination", success)
        )

        assertEquals(2, state.walkingGeneration)
        assertEquals(mapOf("origin" to RouteDetailWalkingState.Loading), state.walkingSegments)
    }

    private fun reduce(events: List<RouteDetailPageEvent>): RouteDetailPageState =
        events.fold(initial(), RouteDetailPageReducer::reduce)

    private fun initial() = RouteDetailPageState.initial(PAGE, setOf(keyA, keyB))

    private fun detail(name: String) = RouteDetail(
        routeName = name,
        priceHkd = 10.0,
        durationMinutes = 20,
        walkingDistanceMeters = 100,
        legs = emptyList()
    )

    private fun geometry(key: RouteGeometryKey) = RouteGeometrySegment(
        key,
        listOf(RouteGeometryPoint("p", 22.3, 114.2))
    )

    private fun pedestrianRoute() = PedestrianRoute(
        10.5,
        0.2,
        listOf(
            PedestrianRoutePath(
                listOf(PedestrianCoordinate(22.3, 114.1), PedestrianCoordinate(22.31, 114.11))
            )
        )
    )

    private companion object {
        const val PAGE = 42L
    }
}
