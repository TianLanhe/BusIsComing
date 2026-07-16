package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.BusRouteQueryCallback
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.ui.main.RouteQueryCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class RouteQueryCoordinatorTest {
    private val origin = Place("Origin", 22.3, 114.1)
    private val destination = Place("Destination", 22.4, 114.2)

    @Test
    fun `new query rejects callbacks from the previous generation`() {
        val repository = CapturingRepository()
        val received = mutableListOf<String>()
        val coordinator = coordinator(repository)

        coordinator.query(origin, destination, callback(received))
        coordinator.query(origin, destination, callback(received))
        repository.callbacks[0].onInitialRoutes(listOf(route("stale")))
        repository.callbacks[1].onInitialRoutes(listOf(route("current")))

        assertEquals(listOf("current"), received)
        assertEquals(2, repository.cancelCount)
    }

    @Test
    fun `invalidated owner rejects incremental and failure callbacks`() {
        val repository = CapturingRepository()
        val received = mutableListOf<String>()
        val coordinator = coordinator(repository)

        coordinator.query(origin, destination, callback(received))
        coordinator.invalidate()
        repository.callbacks.single().onRouteWaitTimeUpdated("route", WaitTimeState.Available(2))
        repository.callbacks.single().onFailure(IllegalStateException("late"))

        assertTrue(received.isEmpty())
        assertEquals(2, repository.cancelCount)
    }

    @Test
    fun `inactive owner rejects a current callback`() {
        val repository = CapturingRepository()
        val received = mutableListOf<String>()
        var active = true
        val coordinator = coordinator(repository) { active }

        coordinator.query(origin, destination, callback(received))
        active = false
        repository.callbacks.single().onInitialRoutes(listOf(route("hidden")))

        assertTrue(received.isEmpty())
    }

    private fun coordinator(
        repository: CapturingRepository,
        isOwnerActive: () -> Boolean = { true }
    ) = RouteQueryCoordinator(
        repository = repository,
        executor = Executor { runnable -> runnable.run() },
        postToOwner = { runnable -> runnable.run() },
        isOwnerActive = isOwnerActive
    )

    private fun callback(received: MutableList<String>) = object : RouteQueryCoordinator.Callback {
        override fun onInitialRoutes(queryId: Int, routes: List<BusRouteOption>) {
            received += routes.single().routeName
        }

        override fun onRouteWaitTimeUpdated(
            queryId: Int,
            routeId: String,
            waitTimeState: WaitTimeState
        ) {
            received += "eta"
        }

        override fun onRouteStopPreviewUpdated(
            queryId: Int,
            routeId: String,
            preview: RouteCardStopPreview
        ) {
            received += "preview"
        }

        override fun onFailure(queryId: Int, error: Throwable) {
            received += "failure"
        }
    }

    private fun route(id: String): BusRouteOption =
        BusRouteOption(id, listOf(id), 1.0, 10, 10, 0, 100, resultId = id)

    private class CapturingRepository : BusRouteRepository {
        val callbacks = mutableListOf<BusRouteQueryCallback>()
        var cancelCount = 0

        override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> = emptyList()

        override fun searchRoutesProgressively(
            origin: Place,
            destination: Place,
            callback: BusRouteQueryCallback
        ) {
            callbacks += callback
        }

        override fun cancelProgressiveQueries() {
            cancelCount += 1
        }
    }
}
