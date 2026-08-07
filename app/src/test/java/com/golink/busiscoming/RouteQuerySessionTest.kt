package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.WalkingDistanceDisplayState
import com.golink.busiscoming.data.repository.BusRouteQueryCallback
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.PedestrianRequestTrigger
import com.golink.busiscoming.ui.main.RouteQuerySession
import com.golink.busiscoming.ui.main.RouteQuerySessionPhase
import com.golink.busiscoming.ui.main.RouteQueryTrigger
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteQuerySessionTest {
    private val origin = Place("Origin", 22.30, 114.10)
    private val destination = Place("Destination", 22.31, 114.11)

    @Test
    fun `observer replacement replays raw snapshot without restarting the query or walking flight`() {
        val repository = CapturingRepository()
        val session = session(repository)
        val oldObserverSnapshots = mutableListOf<List<BusRouteOption>>()
        val newObserverSnapshots = mutableListOf<List<BusRouteOption>>()
        val oldObserver: (com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot) -> Unit = {
            oldObserverSnapshots += it.routes
        }
        val newObserver: (com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot) -> Unit = {
            newObserverSnapshots += it.routes
        }

        session.observe(oldObserver)
        val queryId = session.start(origin, destination, RouteQueryTrigger.INITIAL)
        assertEquals(1, queryId)
        repository.callbacks.single().onInitialRoutes(listOf(route("route")))
        assertEquals(listOf(WalkingDistanceDisplayState.Loading), oldObserverSnapshots.last().map { it.walkingDistanceDisplayState })

        session.clearObserver(oldObserver)
        session.observe(newObserver)
        assertEquals(oldObserverSnapshots.last(), newObserverSnapshots.single())

        repository.callbacks.single().onRouteWalkingDistanceUpdated(
            "route",
            WalkingDistanceDisplayState.CsdiSuccess(123)
        )

        assertEquals(2, newObserverSnapshots.size)
        assertEquals(
            WalkingDistanceDisplayState.CsdiSuccess(123),
            newObserverSnapshots.last().single().walkingDistanceDisplayState
        )
        assertEquals(2, oldObserverSnapshots.size)
        assertEquals(1, repository.progressiveSearchCount)
        assertEquals(1, repository.cancelCount)
    }

    @Test
    fun `base result completes automatic cycle while progressive domains keep updating same snapshot`() {
        val repository = CapturingRepository()
        val session = session(repository)
        val snapshots = mutableListOf<com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot>()
        session.observe(snapshots::add)

        session.start(origin, destination, RouteQueryTrigger.INITIAL)
        repository.callbacks.single().onInitialRoutes(listOf(route("baseline")))
        val queryId = session.start(origin, destination, RouteQueryTrigger.AUTOMATIC)
        assertEquals(2, queryId)
        assertEquals(PedestrianRequestTrigger.AUTOMATIC, repository.walkingTriggers.last())
        repository.callbacks.last().onInitialRoutes(listOf(route("route")))

        assertEquals(RouteQuerySessionPhase.BASE_AVAILABLE, snapshots.last().phase)
        assertFalse(snapshots.last().networkCycleInProgress)

        repository.callbacks.last().onRouteWaitTimeUpdated("route", WaitTimeState.Available(4))
        repository.callbacks.last().onRouteStopPreviewUpdated(
            "route",
            RouteCardStopPreview("Board", "Alight")
        )
        repository.callbacks.last().onRouteWalkingDistanceUpdated(
            "route",
            WalkingDistanceDisplayState.CsdiSuccess(88)
        )

        val completed = snapshots.last()
        assertEquals(RouteQuerySessionPhase.BASE_AVAILABLE, completed.phase)
        assertEquals(WaitTimeState.Available(4), completed.routes.single().waitTimeState)
        assertEquals("Board", completed.routes.single().stopPreview?.boardingStopName)
        assertEquals(WalkingDistanceDisplayState.CsdiSuccess(88), completed.routes.single().walkingDistanceDisplayState)
    }

    @Test
    fun `owner semantics reject overlap and only successful base including empty enables automatic refresh`() {
        val repository = CapturingRepository()
        val session = session(repository)

        assertEquals(1, session.start(origin, destination, RouteQueryTrigger.INITIAL))
        assertNull(session.start(origin, destination, RouteQueryTrigger.AUTOMATIC))
        repository.callbacks.single().onInitialRoutes(emptyList())
        assertTrue(session.latestSnapshot!!.automaticBaselineAvailable)
        assertTrue(session.latestSnapshot!!.routes.isEmpty())

        assertEquals(2, session.start(origin, destination, RouteQueryTrigger.MANUAL))
        assertNull(session.start(origin, destination, RouteQueryTrigger.AUTOMATIC))
        repository.callbacks[1].onFailure(IllegalStateException("manual failed"))
        assertTrue(session.latestSnapshot!!.automaticBaselineAvailable)

        val freshRepository = CapturingRepository()
        val freshSession = session(freshRepository)
        assertEquals(1, freshSession.start(origin, destination, RouteQueryTrigger.INITIAL))
        freshRepository.callbacks.single().onFailure(IllegalStateException("initial failed"))
        assertFalse(freshSession.latestSnapshot!!.automaticBaselineAvailable)
        assertNull(freshSession.start(origin, destination, RouteQueryTrigger.AUTOMATIC))
    }

    @Test
    fun `new query cancels old progressive consumer and stale callbacks cannot replace new generation`() {
        val repository = CapturingRepository()
        val session = session(repository)
        val snapshots = mutableListOf<com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot>()
        session.observe(snapshots::add)

        session.start(origin, destination, RouteQueryTrigger.INITIAL)
        repository.callbacks[0].onInitialRoutes(listOf(route("old")))
        session.start(origin, destination, RouteQueryTrigger.MANUAL)
        repository.callbacks[0].onRouteWalkingDistanceUpdated(
            "old",
            WalkingDistanceDisplayState.CsdiSuccess(999)
        )
        repository.callbacks[1].onInitialRoutes(listOf(route("new")))

        assertEquals(2, repository.cancelCount)
        assertEquals(listOf("new"), snapshots.last().routes.map { it.resultId })
    }

    @Test
    fun `language recreation replays raw walking snapshot and still accepts language neutral CSDI completion`() {
        val repository = CapturingRepository()
        var languageVersion = 1L
        val session = RouteQuerySession(
            repository = repository,
            executor = Executor(Runnable::run),
            dispatch = { it.run() },
            languageVersion = { languageVersion }
        )
        val firstObserver = mutableListOf<com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot>()
        val replacementObserver = mutableListOf<com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot>()
        val first: (com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot) -> Unit = firstObserver::add
        val replacement: (com.golink.busiscoming.ui.main.RouteQuerySessionSnapshot) -> Unit = replacementObserver::add
        session.observe(first)
        session.start(origin, destination, RouteQueryTrigger.INITIAL)
        repository.callbacks.single().onInitialRoutes(listOf(route("route")))

        session.clearObserver(first)
        languageVersion = 2L
        session.observe(replacement)
        repository.callbacks.single().onRouteWalkingDistanceUpdated(
            "route",
            WalkingDistanceDisplayState.CsdiSuccess(77)
        )
        repository.callbacks.single().onRouteStopPreviewUpdated(
            "route",
            RouteCardStopPreview("old language", "old language")
        )

        assertEquals(2, replacementObserver.size)
        assertEquals(
            WalkingDistanceDisplayState.CsdiSuccess(77),
            replacementObserver.last().routes.single().walkingDistanceDisplayState
        )
        assertNull(replacementObserver.last().routes.single().stopPreview)
        assertEquals(1, repository.progressiveSearchCount)
    }

    @Test
    fun `language recreation restarts only a base query that has not produced walking state`() {
        val repository = CapturingRepository()
        var languageVersion = 1L
        val session = RouteQuerySession(
            repository = repository,
            executor = Executor(Runnable::run),
            dispatch = { it.run() },
            languageVersion = { languageVersion }
        )
        session.start(origin, destination, RouteQueryTrigger.INITIAL)

        languageVersion = 2L
        assertEquals(2, session.reconcileCurrentLanguage())
        repository.callbacks[0].onInitialRoutes(listOf(route("old-language")))
        repository.callbacks[1].onInitialRoutes(listOf(route("current-language")))

        assertEquals(2, repository.progressiveSearchCount)
        assertEquals(2, repository.cancelCount)
        assertEquals(
            listOf("current-language"),
            session.latestSnapshot!!.routes.map { it.resultId }
        )
    }

    private fun session(repository: CapturingRepository) = RouteQuerySession(
        repository = repository,
        executor = Executor(Runnable::run),
        dispatch = { it.run() },
        languageVersion = { 1L }
    )

    private fun route(id: String) = BusRouteOption(
        routeName = id,
        routeSegments = listOf(id),
        priceHkd = 1.0,
        durationMinutes = 10,
        arrivalMinutes = 10,
        transferCount = 0,
        walkingDistanceMeters = 100,
        walkingDistanceDisplayState = WalkingDistanceDisplayState.Loading,
        resultId = id
    )

    private class CapturingRepository : BusRouteRepository {
        val callbacks = mutableListOf<BusRouteQueryCallback>()
        val walkingTriggers = mutableListOf<PedestrianRequestTrigger>()
        var progressiveSearchCount = 0
        var cancelCount = 0

        override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> = emptyList()

        override fun searchRoutesProgressively(
            origin: Place,
            destination: Place,
            walkingTrigger: PedestrianRequestTrigger,
            callback: BusRouteQueryCallback
        ) {
            progressiveSearchCount += 1
            walkingTriggers += walkingTrigger
            callbacks += callback
        }

        override fun cancelProgressiveQueries() {
            cancelCount += 1
        }
    }
}
