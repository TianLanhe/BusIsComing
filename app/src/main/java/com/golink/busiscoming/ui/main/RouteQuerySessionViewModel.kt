package com.golink.busiscoming.ui.main

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.BusRouteRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteQuerySessionViewModel : ViewModel() {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "route-query-session").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var session: RouteQuerySession? = null

    val latestSnapshot: RouteQuerySessionSnapshot?
        get() = session?.latestSnapshot

    fun initialize(repositoryFactory: () -> BusRouteRepository) {
        if (session != null) return
        session = RouteQuerySession(
            repository = repositoryFactory(),
            executor = executor,
            dispatch = mainHandler::post,
            languageVersion = { AppLanguageRuntime.snapshot().version }
        )
    }

    fun observe(observer: (RouteQuerySessionSnapshot) -> Unit) {
        requireNotNull(session) { "Route query ViewModel must be initialized before observing" }
            .also { it.reconcileCurrentLanguage() }
            .observe(observer)
    }

    fun clearObserver(observer: (RouteQuerySessionSnapshot) -> Unit) {
        session?.clearObserver(observer)
    }

    fun start(
        origin: Place,
        destination: Place,
        trigger: RouteQueryTrigger
    ): Int? = requireNotNull(session) {
        "Route query ViewModel must be initialized before querying"
    }.start(origin, destination, trigger)

    fun invalidate(clearSnapshot: Boolean = true) {
        session?.invalidate(clearSnapshot)
    }

    override fun onCleared() {
        session?.close()
        session = null
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }
}
