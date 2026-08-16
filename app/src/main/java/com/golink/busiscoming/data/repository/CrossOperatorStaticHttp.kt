package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.local.RouteDatabaseSnapshot
import com.golink.busiscoming.data.model.CachedStaticSource
import com.golink.busiscoming.data.model.GlobalStaticSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

fun interface HttpStaticTransport {
    fun execute(url: URL, headers: Map<String, String>): GlobalFetchResponse
}

class HttpGlobalStaticDataFetcher(
    private val transport: HttpStaticTransport = HttpStaticTransport(::executeHttp)
) : GlobalStaticDataFetcher {
    override fun fetch(source: GlobalStaticSource, cached: CachedStaticSource?): GlobalFetchResponse {
        val headers = buildMap {
            cached?.etag?.let { put("If-None-Match", it) }
            cached?.lastModified?.let { put("If-Modified-Since", it) }
        }
        val response = transport.execute(URL(URLS.getValue(source)), headers)
        if (source != GlobalStaticSource.GTFS_ROUTES || response.statusCode == 304) return response
        val zipBody = response.body ?: return response
        return response.copy(body = extractRoutesText(zipBody))
    }

    private fun extractRoutesText(body: ByteArray): ByteArray {
        ZipInputStream(body.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.substringAfterLast('/') == "routes.txt") {
                    return readLimited(zip, MAX_GTFS_ROUTES_BYTES)
                }
                zip.closeEntry()
            }
        }
        throw StaticDataValidationException("GTFS zip has no routes.txt")
    }

    companion object {
        private const val MAX_GTFS_ROUTES_BYTES = 8 * 1024 * 1024
        private val URLS = mapOf(
            GlobalStaticSource.GTFS_ROUTES to
                "https://static.data.gov.hk/td/pt-headway-tc/gtfs.zip",
            GlobalStaticSource.KMB_ROUTES to
                "https://data.etabus.gov.hk/v1/transport/kmb/route/",
            GlobalStaticSource.KMB_ROUTE_STOPS to
                "https://data.etabus.gov.hk/v1/transport/kmb/route-stop",
            GlobalStaticSource.KMB_STOPS to
                "https://data.etabus.gov.hk/v1/transport/kmb/stop",
            GlobalStaticSource.CTB_ROUTES to
                "https://rt.data.gov.hk/v2/transport/citybus/route/CTB"
        )
    }
}

class RetryingGlobalStaticDataFetcher(
    private val delegate: GlobalStaticDataFetcher,
    private val maxAttempts: Int = 3,
    private val firstBackoffMillis: Long = 500L,
    private val sleeper: (Long) -> Unit = Thread::sleep
) : GlobalStaticDataFetcher {
    init {
        require(maxAttempts > 0)
        require(firstBackoffMillis >= 0)
    }

    override fun fetch(source: GlobalStaticSource, cached: CachedStaticSource?): GlobalFetchResponse {
        var backoff = firstBackoffMillis
        var lastFailure: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                val response = delegate.fetch(source, cached)
                if (!response.isTransientFailure() || attempt == maxAttempts - 1) return response
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            } catch (failure: java.io.IOException) {
                lastFailure = failure
                if (attempt == maxAttempts - 1) throw failure
            }
            sleeper(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
        }
        throw lastFailure ?: IOException("Static source retry exhausted")
    }

    private fun GlobalFetchResponse.isTransientFailure(): Boolean {
        return statusCode == 429 || statusCode in 500..599
    }

    companion object {
        private const val MAX_BACKOFF_MILLIS = 8_000L
    }
}

class KmbPacedGlobalStaticDataFetcher(
    private val delegate: GlobalStaticDataFetcher,
    private val minimumIntervalMillis: Long = DEFAULT_MINIMUM_INTERVAL_MILLIS,
    private val monotonicClock: () -> Long = android.os.SystemClock::elapsedRealtime,
    private val sleeper: (Long) -> Unit = Thread::sleep
) : GlobalStaticDataFetcher {
    private var lastKmbRequestCompletedMillis: Long? = null

    init {
        require(minimumIntervalMillis >= 0L)
    }

    @Synchronized
    override fun fetch(source: GlobalStaticSource, cached: CachedStaticSource?): GlobalFetchResponse {
        if (source !in KMB_GLOBAL_SOURCES) return delegate.fetch(source, cached)
        lastKmbRequestCompletedMillis?.let { previous ->
                val remaining = minimumIntervalMillis - (monotonicClock() - previous)
                if (remaining > 0L) sleeper(remaining)
        }
        return try {
            delegate.fetch(source, cached)
        } finally {
            lastKmbRequestCompletedMillis = monotonicClock()
        }
    }

    companion object {
        private const val DEFAULT_MINIMUM_INTERVAL_MILLIS = 2_000L
        private val KMB_GLOBAL_SOURCES = setOf(
            GlobalStaticSource.KMB_ROUTES,
            GlobalStaticSource.KMB_ROUTE_STOPS,
            GlobalStaticSource.KMB_STOPS
        )
    }
}

enum class RouteDatabaseUpdateTrigger {
    APP_FOREGROUND,
    MANUAL
}

sealed interface RouteDatabaseUpdateState {
    val lastSuccessMillis: Long?

    data class Idle(override val lastSuccessMillis: Long?) : RouteDatabaseUpdateState
    data class Checking(override val lastSuccessMillis: Long?) : RouteDatabaseUpdateState
    data class Success(
        override val lastSuccessMillis: Long,
        val changed: Boolean
    ) : RouteDatabaseUpdateState
    data class Failure(
        override val lastSuccessMillis: Long?,
        val reason: String
    ) : RouteDatabaseUpdateState
}

class RouteDatabaseUpdateCoordinator(
    private val activeSnapshot: () -> RouteDatabaseSnapshot?,
    private val update: (String) -> GlobalUpdateResult,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val executor: Executor = Executors.newSingleThreadExecutor()
) {
    private val lock = Any()
    private val observers = linkedSetOf<(RouteDatabaseUpdateState) -> Unit>()
    // Do not open and decode the large static database from Application.onCreate. The first
    // foreground check reads it on this coordinator's background executor.
    private var state: RouteDatabaseUpdateState = RouteDatabaseUpdateState.Idle(null)
    private var lastKnownDataDay: String? = null

    fun currentState(): RouteDatabaseUpdateState = synchronized(lock) { state }

    fun observe(observer: (RouteDatabaseUpdateState) -> Unit): AutoCloseable {
        val current = synchronized(lock) {
            observers += observer
            state
        }
        observer(current)
        return AutoCloseable { synchronized(lock) { observers -= observer } }
    }

    fun check(trigger: RouteDatabaseUpdateTrigger): Boolean {
        val dataDay = HongKongDataDay.forInstant(clock())
        val nextState = synchronized(lock) {
            if (state is RouteDatabaseUpdateState.Checking) return false
            if (trigger == RouteDatabaseUpdateTrigger.APP_FOREGROUND && lastKnownDataDay == dataDay) {
                return false
            }
            RouteDatabaseUpdateState.Checking(state.lastSuccessMillis).also { state = it }
        }
        notifyObservers(nextState)
        executor.execute {
            val currentSnapshot = runCatching(activeSnapshot).getOrNull()
            if (trigger == RouteDatabaseUpdateTrigger.APP_FOREGROUND && currentSnapshot?.dataDay == dataDay) {
                val idle = synchronized(lock) {
                    lastKnownDataDay = dataDay
                    RouteDatabaseUpdateState.Idle(currentSnapshot.completedAtMillis).also { state = it }
                }
                notifyObservers(idle)
                return@execute
            }
            val result = update(dataDay)
            val completedState = synchronized(lock) {
                when (result) {
                    is GlobalUpdateResult.Success -> {
                        lastKnownDataDay = result.snapshot.dataDay
                        RouteDatabaseUpdateState.Success(
                            result.snapshot.completedAtMillis,
                            result.changed
                        )
                    }
                    is GlobalUpdateResult.Failure -> {
                        if (trigger == RouteDatabaseUpdateTrigger.APP_FOREGROUND) {
                            // One automatic attempt per process/data day; manual checks may still
                            // retry explicitly without causing an Activity foreground retry loop.
                            lastKnownDataDay = dataDay
                        }
                        RouteDatabaseUpdateState.Failure(
                            runCatching(activeSnapshot).getOrNull()?.completedAtMillis,
                            result.reason
                        )
                    }
                }.also { state = it }
            }
            notifyObservers(completedState)
        }
        return true
    }

    private fun notifyObservers(value: RouteDatabaseUpdateState) {
        val snapshot = synchronized(lock) { observers.toList() }
        snapshot.forEach { observer -> runCatching { observer(value) } }
    }
}

private fun executeHttp(url: URL, headers: Map<String, String>): GlobalFetchResponse {
    val connection = url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = HTTP_TIMEOUT_MILLIS
        connection.readTimeout = HTTP_TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "application/json, text/csv, application/zip")
        connection.setRequestProperty("User-Agent", "BusIsComing-Android")
        connection.setRequestProperty("Connection", "close")
        headers.forEach(connection::setRequestProperty)
        val status = connection.responseCode
        val body = when {
            status == HttpURLConnection.HTTP_NOT_MODIFIED -> null
            status in 200..299 -> readLimited(connection.inputStream, MAX_HTTP_BODY_BYTES)
            else -> connection.errorStream?.let { readLimited(it, MAX_ERROR_BODY_BYTES) }
        }
        GlobalFetchResponse(
            statusCode = status,
            body = body,
            etag = connection.getHeaderField("ETag"),
            lastModified = connection.getHeaderField("Last-Modified")
        )
    } finally {
        connection.disconnect()
    }
}

internal fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
    return input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("Response exceeds configured size limit")
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}

private const val HTTP_TIMEOUT_MILLIS = 20_000
private const val MAX_HTTP_BODY_BYTES = 48 * 1024 * 1024
private const val MAX_ERROR_BODY_BYTES = 64 * 1024
