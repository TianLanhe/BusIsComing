package com.golink.busiscoming.data.repository

import android.util.Log
import com.golink.busiscoming.BuildConfig
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.localization.LanguageSnapshot
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class CitybusBusRouteRepository(
    private val parser: CitybusRouteParser = CitybusRouteParser,
    private val languageSnapshotProvider: () -> LanguageSnapshot = AppLanguageRuntime::snapshot,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val routeFetcher: (URL, Map<String, String>) -> String = ::fetchRouteHtml,
    private val requestLogger: (String) -> Unit = ::logRouteCurl,
    private val stopMapResolver: CitybusP2pStopMapResolver = CitybusP2pStopMapResolver(clock = clock),
    private val etaService: CitybusFirstLegEtaService = CitybusFirstLegEtaService(
        clock = clock,
        stopMapResolver = stopMapResolver
    ),
    private val waitTimeResolver: (FirstLegEtaQuery) -> WaitTimeState = etaService::resolveWaitTime,
    private val etaWorkerCount: Int = DEFAULT_ETA_WORKER_COUNT,
    private val stopPreviewResolver: RouteCardStopPreviewResolver = RouteCardStopPreviewResolver(
        stopMapResolver = stopMapResolver
    ),
    private val stopPreviewWorkerCount: Int = DEFAULT_STOP_PREVIEW_WORKER_COUNT
) : BusRouteRepository {
    private val etaScopeLock = Any()
    private var etaGeneration = 0
    private var activeEtaExecutor: ExecutorService? = null
    private val stopPreviewScopeLock = Any()
    private var stopPreviewGeneration = 0
    private var activeStopPreviewExecutor: ExecutorService? = null

    override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> {
        val languageSnapshot = languageSnapshotProvider()
        val queryTime = formatQueryTime(clock())
        val headers = requestHeaders()
        val executor = Executors.newFixedThreadPool(SEARCH_MODES.size)
        return try {
            val futures = executor.invokeAll(
                SEARCH_MODES.map { mode ->
                    Callable {
                        val url = buildRouteUrl(
                            origin,
                            destination,
                            queryTime,
                            mode,
                            languageSnapshot.citybusLanguage
                        )
                        requestLogger(buildRouteQueryLogSummary(url, headers))
                        parser.parse(routeFetcher(url, headers), languageSnapshot.citybusLanguage)
                    }
                }
            )

            val failures = mutableListOf<Throwable>()
            val successfulResults = futures.mapNotNull { future ->
                try {
                    future.get()
                } catch (exception: ExecutionException) {
                    failures += exception.cause ?: exception
                    null
                }
            }

            if (successfulResults.isEmpty()) {
                throw IOException("Citybus route query failed for all m1 modes", failures.firstOrNull())
            }

            aggregateResults(successfulResults.flatten())
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Citybus route query was interrupted", exception)
        } finally {
            executor.shutdownNow()
        }
    }

    override fun cancelProgressiveQueries() {
        cancelActiveEtaQueries()
        cancelActiveStopPreviewQueries()
    }

    override fun searchRoutesProgressively(
        origin: Place,
        destination: Place,
        callback: BusRouteQueryCallback
    ) {
        val routes = try {
            searchRoutes(origin, destination)
        } catch (exception: Throwable) {
            callback.onFailure(exception)
            return
        }

        if (routes.isEmpty()) {
            callback.onInitialRoutes(emptyList())
            return
        }

        callback.onInitialRoutes(routes)
        startStopPreviewCompletion(routes, callback)
        startEtaCompletion(routes, callback)
    }

    fun buildRouteUrl(
        origin: Place,
        destination: Place,
        queryTime: String,
        searchMode: String = DEFAULT_SEARCH_MODE,
        language: String = languageSnapshotProvider().citybusLanguage
    ): URL {
        return URL(
            "$BASE_URL" +
                "?slat=${origin.latitude}" +
                "&slon=${origin.longitude}" +
                "&elat=${destination.latitude}" +
                "&elon=${destination.longitude}" +
                "&t=${encodeQueryValue(queryTime)}" +
                "&ws=$WALKING_SEARCH_RATIO" +
                "&leg=2" +
                "&m1=$searchMode" +
                "&l=$language"
        )
    }

    fun formatQueryTime(timestampMillis: Long): String {
        return SimpleDateFormat(QUERY_TIME_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone(HONG_KONG_TIME_ZONE)
        }.format(Date(timestampMillis))
    }

    fun requestHeaders(): Map<String, String> = emptyMap()

    fun buildRouteQueryLogSummary(url: URL, headers: Map<String, String>): String {
        val queryParams = url.query.orEmpty()
            .split("&")
            .mapNotNull { part ->
                val name = part.substringBefore("=", missingDelimiterValue = "").takeIf { it.isNotBlank() }
                val value = part.substringAfter("=", missingDelimiterValue = "")
                name?.let { it to value }
            }
            .toMap()
        return listOf(
            "endpoint=${url.path.substringAfterLast("/")}",
            "searchMode=${queryParams["m1"].orEmpty().ifBlank { "unknown" }}",
            "origin=${if (queryParams.containsKey("slat") && queryParams.containsKey("slon")) "present" else "missing"}",
            "destination=${if (queryParams.containsKey("elat") && queryParams.containsKey("elon")) "present" else "missing"}",
            "headers=${headers.size}"
        ).joinToString(", ")
    }

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun aggregateResults(routes: List<BusRouteOption>): List<BusRouteOption> {
        return routes
            .groupBy {
                RouteDedupKey(
                    routeSegments = it.routeSegments,
                    priceHkd = it.priceHkd,
                    durationMinutes = it.durationMinutes,
                    walkingDistanceMeters = it.walkingDistanceMeters,
                    rawInfo = it.routeDetailQuery?.rawInfo
                )
            }
            .map { (_, duplicateRoutes) ->
                duplicateRoutes.firstOrNull { it.firstLegEtaQuery != null } ?: duplicateRoutes.first()
            }
            .sortedBy { it.durationMinutes }
    }

    private fun startEtaCompletion(
        routes: List<BusRouteOption>,
        callback: BusRouteQueryCallback
    ) {
        val groups = etaRequestGroups(routes, routes.indices.toList())
        if (groups.isEmpty()) return

        val etaExecutor = Executors.newFixedThreadPool(etaWorkerCount.coerceAtLeast(1))
        val generation = registerEtaExecutor(etaExecutor)
        val remainingGroups = AtomicInteger(groups.size)

        groups.forEach { group ->
            etaExecutor.execute {
                try {
                    if (!isEtaGenerationActive(generation)) return@execute

                    val result = resolveEtaRequestGroup(group)
                    if (!isEtaGenerationActive(generation)) return@execute

                    result.routeIds.forEach { routeId ->
                        callback.onRouteWaitTimeUpdated(routeId, result.waitTimeState)
                    }
                } finally {
                    if (remainingGroups.decrementAndGet() == 0) {
                        finishEtaGeneration(generation)
                    }
                }
            }
        }
        etaExecutor.shutdown()
    }

    private fun startStopPreviewCompletion(
        routes: List<BusRouteOption>,
        callback: BusRouteQueryCallback
    ) {
        val groups = stopPreviewRequestGroups(routes)
        if (groups.isEmpty()) return

        val previewExecutor = Executors.newFixedThreadPool(stopPreviewWorkerCount.coerceAtLeast(1))
        val generation = registerStopPreviewExecutor(previewExecutor)
        val remainingGroups = AtomicInteger(groups.size)

        groups.forEach { group ->
            previewExecutor.execute {
                try {
                    if (!isStopPreviewGenerationActive(generation)) return@execute

                    val preview = runCatching { stopPreviewResolver.resolvePreview(group.route) }.getOrNull()
                    if (preview == null || !isStopPreviewGenerationActive(generation)) return@execute

                    group.routeIds.forEach { routeId ->
                        callback.onRouteStopPreviewUpdated(routeId, preview)
                    }
                } finally {
                    if (remainingGroups.decrementAndGet() == 0) {
                        finishStopPreviewGeneration(generation)
                    }
                }
            }
        }
        previewExecutor.shutdown()
    }

    private fun registerEtaExecutor(etaExecutor: ExecutorService): Int {
        synchronized(etaScopeLock) {
            cancelActiveEtaQueriesLocked()
            etaGeneration += 1
            activeEtaExecutor = etaExecutor
            return etaGeneration
        }
    }

    private fun cancelActiveEtaQueries() {
        synchronized(etaScopeLock) {
            etaGeneration += 1
            cancelActiveEtaQueriesLocked()
        }
    }

    private fun cancelActiveEtaQueriesLocked() {
        activeEtaExecutor?.shutdownNow()
        activeEtaExecutor = null
    }

    private fun isEtaGenerationActive(generation: Int): Boolean {
        synchronized(etaScopeLock) {
            return etaGeneration == generation && activeEtaExecutor != null
        }
    }

    private fun finishEtaGeneration(generation: Int) {
        synchronized(etaScopeLock) {
            if (etaGeneration == generation) {
                activeEtaExecutor = null
            }
        }
    }

    private fun registerStopPreviewExecutor(previewExecutor: ExecutorService): Int {
        synchronized(stopPreviewScopeLock) {
            cancelActiveStopPreviewQueriesLocked()
            stopPreviewGeneration += 1
            activeStopPreviewExecutor = previewExecutor
            return stopPreviewGeneration
        }
    }

    private fun cancelActiveStopPreviewQueries() {
        synchronized(stopPreviewScopeLock) {
            stopPreviewGeneration += 1
            cancelActiveStopPreviewQueriesLocked()
        }
    }

    private fun cancelActiveStopPreviewQueriesLocked() {
        activeStopPreviewExecutor?.shutdownNow()
        activeStopPreviewExecutor = null
    }

    private fun isStopPreviewGenerationActive(generation: Int): Boolean {
        synchronized(stopPreviewScopeLock) {
            return stopPreviewGeneration == generation && activeStopPreviewExecutor != null
        }
    }

    private fun finishStopPreviewGeneration(generation: Int) {
        synchronized(stopPreviewScopeLock) {
            if (stopPreviewGeneration == generation) {
                activeStopPreviewExecutor = null
            }
        }
    }

    private fun etaRequestGroups(
        routes: List<BusRouteOption>,
        indexes: List<Int>
    ): List<EtaRequestGroup> {
        return indexes
            .mapNotNull { index ->
                val query = routes[index].firstLegEtaQuery ?: return@mapNotNull null
                IndexedEtaRoute(routes[index].resultId, query)
            }
            .groupBy { it.query.requestKey() }
            .values
            .map { routesWithSameFirstLeg ->
                EtaRequestGroup(
                    query = routesWithSameFirstLeg.first().query,
                    routeIds = routesWithSameFirstLeg.map { it.routeId }
                )
            }
    }

    private fun resolveEtaRequestGroup(group: EtaRequestGroup): EtaRequestResult {
        val waitTimeState = runCatching { waitTimeResolver(group.query) }
            .getOrDefault(WaitTimeState.Unavailable)
        return EtaRequestResult(
            routeIds = group.routeIds,
            waitTimeState = waitTimeState
        )
    }

    private fun stopPreviewRequestGroups(routes: List<BusRouteOption>): List<StopPreviewRequestGroup> {
        return routes
            .filter { it.routeDetailQuery != null }
            .groupBy { it.routeDetailQuery!!.cacheKey() }
            .values
            .map { routesWithSamePreview ->
                StopPreviewRequestGroup(
                    route = routesWithSamePreview.first(),
                    routeIds = routesWithSamePreview.map { it.resultId }
                )
            }
    }

    private data class RouteDedupKey(
        val routeSegments: List<String>,
        val priceHkd: Double,
        val durationMinutes: Int,
        val walkingDistanceMeters: Int,
        val rawInfo: String?
    )

    private data class IndexedEtaRoute(
        val routeId: String,
        val query: FirstLegEtaQuery
    )

    private data class EtaRequestGroup(
        val query: FirstLegEtaQuery,
        val routeIds: List<String>
    )

    private data class EtaRequestResult(
        val routeIds: List<String>,
        val waitTimeState: WaitTimeState
    )

    private data class StopPreviewRequestGroup(
        val route: BusRouteOption,
        val routeIds: List<String>
    )

    companion object {
        private const val BASE_URL = "https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php"
        private const val DEFAULT_SEARCH_MODE = "T"
        private const val WALKING_SEARCH_RATIO = "1.3"
        private const val QUERY_TIME_PATTERN = "yyyy-MM-dd HH:mm"
        private const val HONG_KONG_TIME_ZONE = "Asia/Hong_Kong"
        private const val DEFAULT_ETA_WORKER_COUNT = 3
        private const val DEFAULT_STOP_PREVIEW_WORKER_COUNT = 2
        private val SEARCH_MODES = listOf("T", "F", "W")
    }
}

private const val TIMEOUT_MS = 20_000
private const val LOG_TAG = "CitybusRouteQuery"

private fun logRouteCurl(routeQuerySummary: String) {
    if (BuildConfig.DEBUG) {
        Log.d(LOG_TAG, "Citybus route query: $routeQuerySummary")
    }
}

private fun fetchRouteHtml(url: URL, headers: Map<String, String>): String {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        headers.forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }

        val statusCode = connection.responseCode
        val responseBody = if (statusCode in 200..299) {
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }

        if (statusCode !in 200..299) {
            throw IOException("Citybus route query failed with HTTP $statusCode")
        }

        responseBody
    } finally {
        connection.disconnect()
    }
}
