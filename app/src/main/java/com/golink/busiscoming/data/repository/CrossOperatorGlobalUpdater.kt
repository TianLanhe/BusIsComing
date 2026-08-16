package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.local.CrossOperatorSnapshotStore
import com.golink.busiscoming.data.local.RouteDatabaseSnapshot
import com.golink.busiscoming.data.model.CachedStaticSource
import com.golink.busiscoming.data.model.GlobalStaticSource
import com.golink.busiscoming.data.model.JointOperatorRoute
import com.golink.busiscoming.data.model.StaticRouteVariant
import java.util.UUID

data class GlobalFetchResponse(
    val statusCode: Int,
    val body: ByteArray?,
    val etag: String?,
    val lastModified: String?
)

fun interface GlobalStaticDataFetcher {
    fun fetch(source: GlobalStaticSource, cached: CachedStaticSource?): GlobalFetchResponse
}

sealed interface GlobalUpdateResult {
    data class Success(
        val changed: Boolean,
        val snapshot: RouteDatabaseSnapshot
    ) : GlobalUpdateResult

    data class Failure(val reason: String) : GlobalUpdateResult
}

class CrossOperatorGlobalUpdater(
    private val store: CrossOperatorSnapshotStore,
    private val fetcher: GlobalStaticDataFetcher,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val snapshotIdFactory: () -> String = { UUID.randomUUID().toString() }
) {
    fun update(dataDay: String): GlobalUpdateResult {
        val previous = runCatching(store::activeSnapshot).getOrNull()
        return try {
            val caches = FETCH_ORDER.associateWith { source ->
                resolveSource(source, previous?.sourceCaches?.get(source))
            }
            val snapshot = assembleSnapshot(dataDay, caches)
            val changedRoutes = changedRoutes(previous, snapshot)
            store.stageSnapshot(snapshot)
            store.activateSnapshot(snapshot.id)
            changedRoutes.forEach(store::invalidateMatchesForRoute)
            GlobalUpdateResult.Success(
                changed = previous == null || changedRoutes.isNotEmpty() ||
                    previous.jointRoutes.toSet() != snapshot.jointRoutes.toSet() ||
                    previous.ctbRoutes.toSet() != snapshot.ctbRoutes.toSet(),
                snapshot = snapshot
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            GlobalUpdateResult.Failure("InterruptedException")
        } catch (exception: Throwable) {
            GlobalUpdateResult.Failure(
                buildString {
                    append(exception::class.java.simpleName)
                    exception.message?.takeIf(String::isNotBlank)?.let { message ->
                        append(": ").append(message.take(MAX_FAILURE_MESSAGE_LENGTH))
                    }
                }
            )
        }
    }

    private fun resolveSource(
        source: GlobalStaticSource,
        cached: CachedStaticSource?
    ): CachedStaticSource {
        val response = fetcher.fetch(source, cached)
        return when (response.statusCode) {
            in 200..299 -> {
                val body = response.body?.takeIf { it.isNotEmpty() }
                    ?: throw StaticDataValidationException("${source.name} response is empty")
                CachedStaticSource(response.etag, response.lastModified, body)
            }
            304 -> cached?.copy(
                etag = response.etag ?: cached.etag,
                lastModified = response.lastModified ?: cached.lastModified
            ) ?: throw StaticDataValidationException("${source.name} returned 304 without cache")
            else -> {
                val responseHint = response.body
                    ?.toString(Charsets.UTF_8)
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.take(120)
                    ?.takeIf(String::isNotBlank)
                throw StaticDataValidationException(
                    buildString {
                        append(source.name).append(" returned HTTP ").append(response.statusCode)
                        responseHint?.let { append(": ").append(it) }
                    }
                )
            }
        }
    }

    private fun assembleSnapshot(
        dataDay: String,
        caches: Map<GlobalStaticSource, CachedStaticSource>
    ): RouteDatabaseSnapshot {
        fun body(source: GlobalStaticSource): String = caches.getValue(source).body.toString(Charsets.UTF_8)
        val jointRoutes = CrossOperatorStaticParsers.parseJointGtfsRoutes(
            body(GlobalStaticSource.GTFS_ROUTES)
        )
        val jointOperators = jointRoutes.groupBy(JointOperatorRoute::route).mapValues { (route, records) ->
            records.map(JointOperatorRoute::partner).distinct().singleOrNull()
                ?: throw StaticDataValidationException("Joint route $route has ambiguous operators")
        }
        val kmbRoutes = CrossOperatorStaticParsers.parseKmbRoutes(
            body(GlobalStaticSource.KMB_ROUTES),
            jointOperators
        )
        val routeStops = CrossOperatorStaticParsers.parseKmbRouteStops(
            body(GlobalStaticSource.KMB_ROUTE_STOPS),
            jointOperators
        )
        val stops = CrossOperatorStaticParsers.parseKmbStops(
            body(GlobalStaticSource.KMB_STOPS)
        )
        val jointRouteNames = jointRoutes.map(JointOperatorRoute::route).toSet()
        val relevantKmbRoutes = kmbRoutes.filter { it.route in jointRouteNames }
        val relevantRouteStops = routeStops.filter { it.route in jointRouteNames }
        val routeKeys = relevantKmbRoutes.map { route ->
            listOf(route.operator.code, route.route, route.direction, route.serviceType).joinToString("|")
        }.toSet()
        relevantRouteStops.forEach { routeStop ->
            val key = listOf(
                routeStop.operator.code,
                routeStop.route,
                routeStop.direction,
                routeStop.serviceType
            ).joinToString("|")
            if (key !in routeKeys) throw StaticDataValidationException("Route-stop references missing route")
        }
        val variants = CrossOperatorStaticParsers.buildVariants(relevantRouteStops, stops)
        val ctbRoutes = CrossOperatorStaticParsers.parseCtbRoutes(
            body(GlobalStaticSource.CTB_ROUTES)
        )
        if (jointRoutes.isEmpty() || variants.isEmpty() || ctbRoutes.isEmpty()) {
            throw StaticDataValidationException("Global static snapshot is semantically empty")
        }
        return RouteDatabaseSnapshot(
            id = snapshotIdFactory(),
            dataDay = dataDay,
            completedAtMillis = clock(),
            jointRoutes = jointRoutes,
            ctbRoutes = ctbRoutes,
            variants = variants,
            sourceCaches = caches
        )
    }

    private fun changedRoutes(
        previous: RouteDatabaseSnapshot?,
        current: RouteDatabaseSnapshot
    ): Set<String> {
        if (previous == null) return current.variants.map { it.route }.toSet()
        val routes = previous.variants.map { it.route }.toSet() + current.variants.map { it.route }.toSet()
        return routes.filterTo(mutableSetOf()) { route ->
            semanticRoute(previous.variants, route) != semanticRoute(current.variants, route)
        }
    }

    private fun semanticRoute(variants: List<StaticRouteVariant>, route: String): List<String> {
        return variants.filter { it.route == route }
            .map(RouteSemanticFingerprint::of)
            .sorted()
    }

    companion object {
        private const val MAX_FAILURE_MESSAGE_LENGTH = 240
        private val FETCH_ORDER = listOf(
            GlobalStaticSource.GTFS_ROUTES,
            GlobalStaticSource.KMB_ROUTES,
            GlobalStaticSource.KMB_ROUTE_STOPS,
            GlobalStaticSource.KMB_STOPS,
            GlobalStaticSource.CTB_ROUTES
        )
    }
}
