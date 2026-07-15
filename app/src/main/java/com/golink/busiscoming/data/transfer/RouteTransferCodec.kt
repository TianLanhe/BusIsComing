package com.golink.busiscoming.data.transfer

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object RouteTransferCodec {
    const val FORMAT = "com.golink.busiscoming.routes"
    const val VERSION = 1
    const val MAX_ROUTES = 500

    private val rootKeys = setOf("format", "version", "exportedAt", "routes")
    private val routeKeys = setOf("name", "origin", "destination")
    private val placeKeys = setOf("name", "latitude", "longitude")
    private val timestampPattern = Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")

    fun encode(routes: List<RouteConfig>, exportedAtUtc: String): ByteArray {
        if (routes.isEmpty()) throw RouteTransferException(RouteTransferError.EMPTY_ROUTES)
        if (routes.size > MAX_ROUTES) throw RouteTransferException(RouteTransferError.TOO_MANY_ROUTES)
        if (!isValidUtcTimestamp(exportedAtUtc)) throw RouteTransferException(RouteTransferError.INVALID_SCHEMA)

        val routeArray = JSONArray()
        routes.forEach { config ->
            val route = TransferRoute(
                name = config.name.trim(),
                origin = normalizePlace(config.origin),
                destination = normalizePlace(config.destination)
            )
            validateRoute(route)
            routeArray.put(
                JSONObject()
                    .put("name", route.name)
                    .put("origin", encodePlace(route.origin))
                    .put("destination", encodePlace(route.destination))
            )
        }

        val bytes = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", exportedAtUtc)
            .put("routes", routeArray)
            .toString()
            .toByteArray(Charsets.UTF_8)
        if (bytes.size > RouteTransferFileReader.MAX_FILE_BYTES) {
            throw RouteTransferException(RouteTransferError.FILE_TOO_LARGE)
        }
        return bytes
    }

    fun decode(bytes: ByteArray): DecodedRouteTransfer {
        if (bytes.size > RouteTransferFileReader.MAX_FILE_BYTES) {
            throw RouteTransferException(RouteTransferError.FILE_TOO_LARGE)
        }
        val root = try {
            JSONObject(bytes.toString(Charsets.UTF_8))
        } catch (error: JSONException) {
            throw RouteTransferException(RouteTransferError.MALFORMED_JSON, error)
        }

        requireExactKeys(root, rootKeys)
        val format = requireString(root, "format")
        if (format != FORMAT) throw RouteTransferException(RouteTransferError.INVALID_FORMAT)

        val rawVersion = root.getOrSchemaError("version")
        if (rawVersion !is Int && rawVersion !is Long) schemaError()
        if ((rawVersion as Number).toLong() != VERSION.toLong()) {
            throw RouteTransferException(RouteTransferError.UNSUPPORTED_VERSION)
        }

        val exportedAt = requireString(root, "exportedAt")
        if (!isValidUtcTimestamp(exportedAt)) schemaError()
        val routeArray = root.getOrSchemaError("routes") as? JSONArray ?: schemaError()
        if (routeArray.length() == 0) throw RouteTransferException(RouteTransferError.EMPTY_ROUTES)
        if (routeArray.length() > MAX_ROUTES) throw RouteTransferException(RouteTransferError.TOO_MANY_ROUTES)

        val uniqueRoutes = ArrayList<TransferRoute>(routeArray.length())
        val identities = LinkedHashSet<RouteIdentity>()
        var duplicateCount = 0
        for (index in 0 until routeArray.length()) {
            val routeJson = routeArray.opt(index) as? JSONObject ?: schemaError()
            val route = decodeRoute(routeJson)
            if (identities.add(route.identity())) {
                uniqueRoutes += route
            } else {
                duplicateCount += 1
            }
        }
        return DecodedRouteTransfer(exportedAt, uniqueRoutes, duplicateCount)
    }

    private fun decodeRoute(json: JSONObject): TransferRoute {
        requireExactKeys(json, routeKeys)
        val route = TransferRoute(
            name = requireString(json, "name").trim(),
            origin = decodePlace(json.getOrSchemaError("origin")),
            destination = decodePlace(json.getOrSchemaError("destination"))
        )
        validateRoute(route)
        return route
    }

    private fun decodePlace(raw: Any): Place {
        val json = raw as? JSONObject ?: schemaError()
        requireExactKeys(json, placeKeys)
        val latitude = requireNumber(json, "latitude")
        val longitude = requireNumber(json, "longitude")
        return Place(requireString(json, "name").trim(), latitude, longitude)
    }

    private fun validateRoute(route: TransferRoute) {
        if (route.name.isBlank() || route.origin.name.isBlank() || route.destination.name.isBlank()) {
            throw RouteTransferException(RouteTransferError.INVALID_ROUTE)
        }
        if (!route.origin.latitude.isFinite() || route.origin.latitude !in -90.0..90.0 ||
            !route.destination.latitude.isFinite() || route.destination.latitude !in -90.0..90.0 ||
            !route.origin.longitude.isFinite() || route.origin.longitude !in -180.0..180.0 ||
            !route.destination.longitude.isFinite() || route.destination.longitude !in -180.0..180.0 ||
            route.origin == route.destination
        ) {
            throw RouteTransferException(RouteTransferError.INVALID_ROUTE)
        }
    }

    private fun normalizePlace(place: Place) = place.copy(name = place.name.trim())

    private fun encodePlace(place: Place) = JSONObject()
        .put("name", place.name)
        .put("latitude", place.latitude)
        .put("longitude", place.longitude)

    private fun requireExactKeys(json: JSONObject, expected: Set<String>) {
        if (json.keys().asSequence().toSet() != expected) schemaError()
    }

    private fun requireString(json: JSONObject, key: String): String =
        json.getOrSchemaError(key) as? String ?: schemaError()

    private fun requireNumber(json: JSONObject, key: String): Double {
        val raw = json.getOrSchemaError(key)
        if (raw !is Number) schemaError()
        return raw.toDouble()
    }

    private fun JSONObject.getOrSchemaError(key: String): Any = try {
        get(key)
    } catch (error: JSONException) {
        throw RouteTransferException(RouteTransferError.INVALID_SCHEMA, error)
    }

    private fun schemaError(): Nothing = throw RouteTransferException(RouteTransferError.INVALID_SCHEMA)

    private fun isValidUtcTimestamp(value: String): Boolean {
        if (!timestampPattern.matches(value)) return false
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.parse(value)?.let { formatter.format(it) == value } == true
    }
}

internal data class RouteIdentity(
    val name: String,
    val origin: Place,
    val destination: Place
)

internal fun TransferRoute.identity() = RouteIdentity(name.trim(), origin, destination)
