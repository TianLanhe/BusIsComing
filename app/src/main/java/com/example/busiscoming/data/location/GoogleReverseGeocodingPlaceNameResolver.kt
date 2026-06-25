package com.example.busiscoming.data.location

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.busiscoming.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

interface PlaceNameResolver {
    fun resolve(
        snapshot: CurrentLocationSnapshot,
        callback: (PlaceNameResolutionResult) -> Unit
    )

    fun prefetch(snapshot: CurrentLocationSnapshot)
}

sealed class PlaceNameResolutionResult {
    data class Success(
        val addressName: String,
        val attribution: PlaceAttribution = PlaceAttribution.GOOGLE_MAPS
    ) : PlaceNameResolutionResult()

    data object Failure : PlaceNameResolutionResult()
}

enum class PlaceAttribution {
    GOOGLE_MAPS
}

class GoogleReverseGeocodingPlaceNameResolver(
    context: Context? = null,
    private val apiKeyProvider: () -> String = { BuildConfig.GOOGLE_GEOCODING_API_KEY },
    private val languageCodeProvider: () -> String = { DEFAULT_LANGUAGE_CODE },
    private val identityProvider: () -> AndroidRequestIdentity? = {
        context?.applicationContext?.let(AndroidRequestIdentity::from)
    },
    private val fetcher: (GoogleReverseGeocodingRequest) -> GoogleReverseGeocodingHttpResponse =
        ::fetchGoogleReverseGeocoding,
    private val nowElapsedMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val callbackExecutor: (Runnable) -> Unit = MAIN_THREAD_CALLBACK_EXECUTOR,
    private val requestExecutor: java.util.concurrent.Executor = SHARED_REQUEST_EXECUTOR,
    private val timeoutExecutor: ScheduledExecutorService = SHARED_TIMEOUT_EXECUTOR,
    private val cache: GoogleReverseGeocodingCache = SHARED_CACHE,
    private val failureLogger: (String) -> Unit = DEFAULT_FAILURE_LOGGER
) : PlaceNameResolver {
    override fun resolve(
        snapshot: CurrentLocationSnapshot,
        callback: (PlaceNameResolutionResult) -> Unit
    ) {
        resolveInternal(snapshot, callback)
    }

    override fun prefetch(snapshot: CurrentLocationSnapshot) {
        resolveInternal(snapshot, callback = null)
    }

    private fun resolveInternal(
        snapshot: CurrentLocationSnapshot,
        callback: ((PlaceNameResolutionResult) -> Unit)?
    ) {
        val requestContext = requestContext() ?: run {
            callback?.postResult(PlaceNameResolutionResult.Failure)
            return
        }
        val key = GoogleReverseGeocodingCacheKey.from(snapshot, requestContext.languageCode)
        cache.get(key, nowElapsedMillis())?.let { cached ->
            callback?.postResult(
                PlaceNameResolutionResult.Success(cached.addressName)
            )
            return
        }

        val requestId = cache.addInFlightCallback(key, callback) ?: return

        val timeout = timeoutExecutor.schedule(
            {
                val callbacks = cache.completeInFlight(key, requestId)
                callbacks.forEach { it.postResult(PlaceNameResolutionResult.Failure) }
                logFailure("timeout")
            },
            NAME_RESOLUTION_TIMEOUT_MS,
            TimeUnit.MILLISECONDS
        )

        requestExecutor.execute {
            val result = runGoogleRequest(snapshot, requestContext)
            timeout.cancel(false)
            if (result is PlaceNameResolutionResult.Success) {
                cache.put(
                    key = key,
                    entry = GoogleReverseGeocodingCacheEntry(
                        addressName = result.addressName,
                        cachedAtMillis = nowElapsedMillis()
                    )
                )
            }
            val callbacks = cache.completeInFlight(key, requestId)
            callbacks.forEach { it.postResult(result) }
        }
    }

    private fun requestContext(): GoogleReverseGeocodingRequestContext? {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            logFailure("key_missing")
            return null
        }
        val identity = identityProvider()
        if (identity == null) {
            logFailure("android_identity_missing")
            return null
        }
        return GoogleReverseGeocodingRequestContext(
            apiKey = apiKey,
            languageCode = languageCodeProvider().ifBlank { DEFAULT_LANGUAGE_CODE },
            identity = identity
        )
    }

    private fun runGoogleRequest(
        snapshot: CurrentLocationSnapshot,
        requestContext: GoogleReverseGeocodingRequestContext
    ): PlaceNameResolutionResult {
        val request = buildRequest(snapshot, requestContext)
        val response = try {
            fetcher(request)
        } catch (_: java.net.SocketTimeoutException) {
            logFailure("timeout")
            return PlaceNameResolutionResult.Failure
        } catch (_: IOException) {
            logFailure("network_error")
            return PlaceNameResolutionResult.Failure
        } catch (_: RuntimeException) {
            logFailure("network_error")
            return PlaceNameResolutionResult.Failure
        }
        if (response.statusCode !in 200..299) {
            logFailure("http_${response.statusCode}")
            return PlaceNameResolutionResult.Failure
        }
        return when (val parsed = GoogleReverseGeocodingParser.parse(response.body)) {
            is GoogleReverseGeocodingParseResult.Success -> {
                PlaceNameResolutionResult.Success(parsed.addressName)
            }
            GoogleReverseGeocodingParseResult.ApiError -> {
                logFailure("api_error")
                PlaceNameResolutionResult.Failure
            }
            GoogleReverseGeocodingParseResult.EmptyResults -> {
                logFailure("empty_results")
                PlaceNameResolutionResult.Failure
            }
            GoogleReverseGeocodingParseResult.MalformedJson -> {
                logFailure("malformed_json")
                PlaceNameResolutionResult.Failure
            }
        }
    }

    fun buildRequest(
        snapshot: CurrentLocationSnapshot,
        requestContext: GoogleReverseGeocodingRequestContext = requestContext()
            ?: GoogleReverseGeocodingRequestContext(
                apiKey = "",
                languageCode = DEFAULT_LANGUAGE_CODE,
                identity = AndroidRequestIdentity("", "")
            )
    ): GoogleReverseGeocodingRequest {
        val lat = coordinate(snapshot.latitude)
        val lng = coordinate(snapshot.longitude)
        val languageCode = encode(requestContext.languageCode)
        val regionCode = encode(REGION_CODE)
        val url = URL("$BASE_URL/$lat,$lng?languageCode=$languageCode&regionCode=$regionCode")
        return GoogleReverseGeocodingRequest(
            url = url,
            headers = mapOf(
                "Content-Type" to "application/json",
                "X-Goog-Api-Key" to requestContext.apiKey,
                "X-Goog-FieldMask" to FIELD_MASK,
                "X-Android-Package" to requestContext.identity.packageName,
                "X-Android-Cert" to requestContext.identity.certificateSha1
            )
        )
    }

    private fun ((PlaceNameResolutionResult) -> Unit).postResult(result: PlaceNameResolutionResult) {
        callbackExecutor(Runnable { this(result) })
    }

    private fun logFailure(category: String) {
        failureLogger(category)
    }

    companion object {
        const val DEFAULT_LANGUAGE_CODE = "zh-Hant"
        const val REGION_CODE = "HK"
        const val FIELD_MASK =
            "results.formattedAddress,results.types,results.addressComponents.longText,results.addressComponents.types"
        const val NAME_RESOLUTION_TIMEOUT_MS = 3_000L
        private const val BASE_URL = "https://geocode.googleapis.com/v4/geocode/location"
        private const val TAG = "GoogleReverseGeocoder"

        private val SHARED_REQUEST_EXECUTOR = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "GoogleReverseGeocoder").apply { isDaemon = true }
        }
        private val SHARED_TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "GoogleReverseGeocoderTimeout").apply { isDaemon = true }
        }
        private val SHARED_CACHE = GoogleReverseGeocodingCache()
        private val MAIN_THREAD_CALLBACK_EXECUTOR: (Runnable) -> Unit = { runnable ->
            android.os.Handler(android.os.Looper.getMainLooper()).post(runnable)
        }
        private val DEFAULT_FAILURE_LOGGER: (String) -> Unit = { category ->
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Google reverse geocoding failed: $category")
            }
        }

        fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

        private fun encode(value: String): String {
            return URLEncoder.encode(value, Charsets.UTF_8.name())
        }
    }
}

data class GoogleReverseGeocodingRequestContext(
    val apiKey: String,
    val languageCode: String,
    val identity: AndroidRequestIdentity
)

data class GoogleReverseGeocodingRequest(
    val url: URL,
    val headers: Map<String, String>
)

data class GoogleReverseGeocodingHttpResponse(
    val statusCode: Int,
    val body: String
)

data class AndroidRequestIdentity(
    val packageName: String,
    val certificateSha1: String
) {
    companion object {
        fun from(context: Context): AndroidRequestIdentity? {
            val packageName = context.packageName.takeIf { it.isNotBlank() } ?: return null
            val signature = firstSignatureBytes(context, packageName) ?: return null
            return AndroidRequestIdentity(
                packageName = packageName,
                certificateSha1 = signature.sha1Hex()
            )
        }

        @Suppress("DEPRECATION")
        private fun firstSignatureBytes(context: Context, packageName: String): ByteArray? {
            val packageManager = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = packageInfo.signingInfo ?: return null
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signatures?.firstOrNull()?.toByteArray()
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures
                    ?.firstOrNull()
                    ?.toByteArray()
            }
        }

        private fun ByteArray.sha1Hex(): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(this)
            return digest.joinToString(separator = "") { byte -> "%02X".format(byte) }
        }
    }
}

class GoogleReverseGeocodingCache {
    private val lock = Any()
    private val entries = mutableMapOf<GoogleReverseGeocodingCacheKey, GoogleReverseGeocodingCacheEntry>()
    private val inFlight = mutableMapOf<GoogleReverseGeocodingCacheKey, InFlightCallbacks>()
    private var nextRequestId: Long = 0L

    fun get(key: GoogleReverseGeocodingCacheKey, nowElapsedMillis: Long): GoogleReverseGeocodingCacheEntry? {
        return synchronized(lock) {
            val entry = entries[key] ?: return@synchronized null
            if (nowElapsedMillis - entry.cachedAtMillis > CACHE_TTL_MS) {
                entries.remove(key)
                null
            } else {
                entry
            }
        }
    }

    fun put(key: GoogleReverseGeocodingCacheKey, entry: GoogleReverseGeocodingCacheEntry) {
        synchronized(lock) {
            entries[key] = entry
        }
    }

    fun addInFlightCallback(
        key: GoogleReverseGeocodingCacheKey,
        callback: ((PlaceNameResolutionResult) -> Unit)?
    ): Long? {
        return synchronized(lock) {
            val callbacks = inFlight[key]
            if (callbacks == null) {
                val requestId = ++nextRequestId
                inFlight[key] = InFlightCallbacks(
                    requestId = requestId,
                    callbacks = mutableListOf(callback)
                )
                requestId
            } else {
                callbacks.callbacks += callback
                null
            }
        }
    }

    fun completeInFlight(
        key: GoogleReverseGeocodingCacheKey,
        requestId: Long
    ): List<(PlaceNameResolutionResult) -> Unit> {
        return synchronized(lock) {
            val callbacks = inFlight[key] ?: return@synchronized emptyList()
            if (callbacks.requestId != requestId) return@synchronized emptyList()
            inFlight.remove(key)
                ?.callbacks
                ?.filterNotNull()
                .orEmpty()
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            inFlight.clear()
        }
    }

    companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1000L
    }

    private data class InFlightCallbacks(
        val requestId: Long,
        val callbacks: MutableList<((PlaceNameResolutionResult) -> Unit)?>
    )
}

data class GoogleReverseGeocodingCacheKey(
    val latitudeE4: Int,
    val longitudeE4: Int,
    val languageCode: String
) {
    companion object {
        fun from(snapshot: CurrentLocationSnapshot, languageCode: String): GoogleReverseGeocodingCacheKey {
            return GoogleReverseGeocodingCacheKey(
                latitudeE4 = (snapshot.latitude * 10_000).roundToInt(),
                longitudeE4 = (snapshot.longitude * 10_000).roundToInt(),
                languageCode = languageCode
            )
        }
    }
}

data class GoogleReverseGeocodingCacheEntry(
    val addressName: String,
    val cachedAtMillis: Long
)

sealed class GoogleReverseGeocodingParseResult {
    data class Success(val addressName: String) : GoogleReverseGeocodingParseResult()
    data object ApiError : GoogleReverseGeocodingParseResult()
    data object EmptyResults : GoogleReverseGeocodingParseResult()
    data object MalformedJson : GoogleReverseGeocodingParseResult()
}

object GoogleReverseGeocodingParser {
    private val coarseTypes = setOf(
        "country",
        "administrative_area_level_1",
        "administrative_area_level_2",
        "locality",
        "political"
    )
    private val plusCodePattern = Regex(
        pattern = """^[23456789CFGHJMPQRVWX]{2,8}\+[23456789CFGHJMPQRVWX]{2,}.*$""",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(response: String): GoogleReverseGeocodingParseResult {
        val root = try {
            JSONObject(response)
        } catch (_: JSONException) {
            return GoogleReverseGeocodingParseResult.MalformedJson
        }
        if (root.has("error")) return GoogleReverseGeocodingParseResult.ApiError
        val results = root.optJSONArray("results") ?: return GoogleReverseGeocodingParseResult.EmptyResults
        if (results.length() == 0) return GoogleReverseGeocodingParseResult.EmptyResults

        val selected = selectStreetAddress(results)
            ?: selectNonCoarseAddress(results)
            ?: selectFirstAddress(results)
            ?: selectAddressComponents(results)
            ?: return GoogleReverseGeocodingParseResult.EmptyResults

        return GoogleReverseGeocodingParseResult.Success(selected)
    }

    private fun selectStreetAddress(results: JSONArray): String? {
        return resultSequence(results)
            .firstNotNullOfOrNull { result ->
                val address = result.optFormattedAddress()
                if (address != null && result.types().contains("street_address")) address else null
            }
    }

    private fun selectNonCoarseAddress(results: JSONArray): String? {
        return resultSequence(results)
            .firstNotNullOfOrNull { result ->
                val address = result.optFormattedAddress() ?: return@firstNotNullOfOrNull null
                val types = result.types()
                if (types.isNotEmpty() && !types.all { it in coarseTypes }) address else null
            }
    }

    private fun selectFirstAddress(results: JSONArray): String? {
        return resultSequence(results).firstNotNullOfOrNull { it.optFormattedAddress() }
    }

    private fun selectAddressComponents(results: JSONArray): String? {
        return resultSequence(results)
            .firstNotNullOfOrNull { result ->
                val components = result.optJSONArray("addressComponents") ?: return@firstNotNullOfOrNull null
                val seen = linkedSetOf<String>()
                for (index in 0 until components.length()) {
                    val value = components.optJSONObject(index)
                        ?.optString("longText")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                    if (value != null && !isPlusCode(value)) {
                        seen += value
                    }
                    if (seen.size >= MAX_COMPONENTS) break
                }
                seen.takeIf { it.isNotEmpty() }?.joinToString("，")
            }
    }

    private fun JSONObject.optFormattedAddress(): String? {
        return optString("formattedAddress")
            .trim()
            .takeIf { it.isNotBlank() && !isPlusCode(it) }
    }

    private fun JSONObject.types(): Set<String> {
        val array = optJSONArray("types") ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim().takeIf { it.isNotBlank() }
                if (value != null) add(value)
            }
        }
    }

    private fun resultSequence(results: JSONArray): Sequence<JSONObject> {
        return sequence {
            for (index in 0 until results.length()) {
                results.optJSONObject(index)?.let { yield(it) }
            }
        }
    }

    fun isPlusCode(value: String): Boolean {
        val candidate = value
            .trim()
            .substringBefore(",")
            .replace(" ", "")
        return candidate.contains("+") && plusCodePattern.matches(candidate)
    }

    private const val MAX_COMPONENTS = 4
}

private fun fetchGoogleReverseGeocoding(
    request: GoogleReverseGeocodingRequest
): GoogleReverseGeocodingHttpResponse {
    val connection = request.url.openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = GoogleReverseGeocodingPlaceNameResolver.NAME_RESOLUTION_TIMEOUT_MS.toInt()
        connection.readTimeout = GoogleReverseGeocodingPlaceNameResolver.NAME_RESOLUTION_TIMEOUT_MS.toInt()
        request.headers.forEach { (name, value) ->
            connection.setRequestProperty(name, value)
        }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        GoogleReverseGeocodingHttpResponse(
            statusCode = statusCode,
            body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        )
    } finally {
        connection.disconnect()
    }
}
