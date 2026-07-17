package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.BusRouteQueryCallback
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.ui.navigation.RouteQueryGeneration
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import java.util.concurrent.Executor

class RouteQueryCoordinator(
    private val repository: BusRouteRepository,
    private val executor: Executor,
    private val postToOwner: (Runnable) -> Unit,
    private val isOwnerActive: () -> Boolean,
    private val languageVersion: () -> Long = { AppLanguageRuntime.snapshot().version }
) {
    private val generation = RouteQueryGeneration()

    fun query(origin: Place, destination: Place, callback: Callback): Int {
        val queryId = generation.begin()
        val queryLanguageVersion = languageVersion()
        repository.cancelProgressiveQueries()
        executor.execute {
            repository.searchRoutesProgressively(
                origin,
                destination,
                object : BusRouteQueryCallback {
                    override fun onInitialRoutes(routes: List<BusRouteOption>) {
                        dispatch(queryId, queryLanguageVersion) {
                            callback.onInitialRoutes(queryId, routes)
                        }
                    }

                    override fun onRouteWaitTimeUpdated(
                        routeId: String,
                        waitTimeState: WaitTimeState
                    ) {
                        dispatch(queryId, queryLanguageVersion) {
                            callback.onRouteWaitTimeUpdated(queryId, routeId, waitTimeState)
                        }
                    }

                    override fun onRouteStopPreviewUpdated(
                        routeId: String,
                        preview: RouteCardStopPreview
                    ) {
                        dispatch(queryId, queryLanguageVersion) {
                            callback.onRouteStopPreviewUpdated(queryId, routeId, preview)
                        }
                    }

                    override fun onFailure(error: Throwable) {
                        dispatch(queryId, queryLanguageVersion) {
                            callback.onFailure(queryId, error)
                        }
                    }
                }
            )
        }
        return queryId
    }

    fun invalidate() {
        generation.invalidate()
        repository.cancelProgressiveQueries()
    }

    private fun dispatch(queryId: Int, queryLanguageVersion: Long, action: () -> Unit) {
        postToOwner(Runnable {
            if (
                generation.isCurrent(queryId) &&
                languageVersion() == queryLanguageVersion &&
                isOwnerActive()
            ) {
                action()
            }
        })
    }

    interface Callback {
        fun onInitialRoutes(queryId: Int, routes: List<BusRouteOption>)

        fun onRouteWaitTimeUpdated(
            queryId: Int,
            routeId: String,
            waitTimeState: WaitTimeState
        )

        fun onRouteStopPreviewUpdated(
            queryId: Int,
            routeId: String,
            preview: RouteCardStopPreview
        )

        fun onFailure(queryId: Int, error: Throwable)
    }
}
