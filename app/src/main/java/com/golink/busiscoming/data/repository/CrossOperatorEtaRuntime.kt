package com.golink.busiscoming.data.repository

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.golink.busiscoming.data.local.CrossOperatorRouteDatabase
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.WaitTimeState
import java.util.concurrent.Executors

object CrossOperatorEtaRuntime {
    private val lock = Any()
    @Volatile private var service: CrossOperatorFirstLegEtaService? = null
    @Volatile private var coordinator: RouteDatabaseUpdateCoordinator? = null

    fun initialize(context: Context) {
        if (service != null && coordinator != null) return
        synchronized(lock) {
            if (service != null && coordinator != null) return
            val appContext = context.applicationContext
            val database = CrossOperatorRouteDatabase(appContext)
            val staticFetcher = RetryingGlobalStaticDataFetcher(
                KmbPacedGlobalStaticDataFetcher(HttpGlobalStaticDataFetcher())
            )
            val updater = CrossOperatorGlobalUpdater(database, staticFetcher)
            coordinator = RouteDatabaseUpdateCoordinator(
                activeSnapshot = database::activeSnapshot,
                update = updater::update
            )
            val stopMapResolver = CitybusP2pStopMapResolver()
            val citybusService = CitybusFirstLegEtaService(stopMapResolver = stopMapResolver)
            val sliceLoader = CtbRouteSliceLoader(CitybusStaticDataHttpSource(), database)
            val mappingRepository = CrossOperatorMappingRepository(
                snapshotStore = database,
                sliceStore = database,
                matchStore = database,
                routeLoader = sliceLoader::loadRoute,
                backgroundExecutor = Executors.newFixedThreadPool(2)
            )
            val kmbSource = KmbFirstLegEtaSource()
            service = CrossOperatorFirstLegEtaService(
                citybusResolver = citybusService::resolveWaitTime,
                stopIdentityResolver = P2pFirstLegStopIdentityResolver(stopMapResolver)::resolve,
                mappingResolver = mappingRepository::resolve,
                partnerSource = kmbSource::query
            )
        }
    }

    fun resolveWaitTime(query: FirstLegEtaQuery): WaitTimeState {
        return service?.resolveWaitTime(query) ?: CitybusFirstLegEtaService().resolveWaitTime(query)
    }

    fun resolveWaitTimeProgressively(
        query: FirstLegEtaQuery,
        onUpdate: (WaitTimeState) -> Unit
    ): WaitTimeState {
        val current = service
        return if (current == null) {
            CitybusFirstLegEtaService().resolveWaitTime(query).also(onUpdate)
        } else {
            current.resolveWaitTimeProgressively(query, onUpdate)
        }
    }

    fun updateCoordinator(): RouteDatabaseUpdateCoordinator? = coordinator

    internal fun replaceUpdateCoordinatorForTesting(
        replacement: RouteDatabaseUpdateCoordinator
    ): AutoCloseable {
        val previous = coordinator
        coordinator = replacement
        return AutoCloseable { coordinator = previous }
    }
}

class RouteDatabaseForegroundTrigger(
    private val onForeground: () -> Unit
) : Application.ActivityLifecycleCallbacks {
    private var startedActivities = 0

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities++ == 0) onForeground()
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
