package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.JointOperatorRoute
import com.golink.busiscoming.data.model.StaticRouteRecord
import com.golink.busiscoming.data.model.StaticRouteStop
import com.golink.busiscoming.data.model.StaticRouteStopRecord
import com.golink.busiscoming.data.model.StaticRouteVariant
import com.golink.busiscoming.data.model.StaticStopRecord
import org.json.JSONArray
import org.json.JSONObject

class StaticDataValidationException(message: String) : IllegalArgumentException(message)

object CrossOperatorStaticParsers {
    fun parseJointGtfsRoutes(csv: String): List<JointOperatorRoute> {
        val rows = csv.lineSequence().filter { it.isNotBlank() }.map(::parseCsvRow).toList()
        if (rows.isEmpty()) throw StaticDataValidationException("GTFS routes.txt is empty")
        val header = rows.first().mapIndexed { index, name -> name.trim() to index }.toMap()
        val agencyIndex = header["agency_id"]
            ?: throw StaticDataValidationException("GTFS routes.txt misses agency_id")
        val shortNameIndex = header["route_short_name"]
            ?: throw StaticDataValidationException("GTFS routes.txt misses route_short_name")
        return rows.drop(1).mapNotNull { row ->
            val partner = when (row.getOrNull(agencyIndex)?.trim()) {
                "KMB+CTB" -> BusOperator.KMB
                "LWB+CTB" -> BusOperator.LWB
                else -> null
            } ?: return@mapNotNull null
            val route = row.getOrNull(shortNameIndex)?.trim().orEmpty()
            if (route.isBlank()) throw StaticDataValidationException("Joint GTFS route is blank")
            JointOperatorRoute(route, partner)
        }.distinct()
    }

    fun parseKmbRoutes(
        json: String,
        operatorByJointRoute: Map<String, BusOperator> = emptyMap()
    ): List<StaticRouteRecord> {
        return dataArray(json).mapObjects { record ->
            val route = record.required("route")
            val operator = resolveKmbStaticOperator(record, route, operatorByJointRoute)
                ?: return@mapObjects null
            StaticRouteRecord(
                operator = operator,
                route = route,
                direction = record.required("bound"),
                serviceType = record.required("service_type")
            )
        }
    }

    fun parseKmbRouteStops(
        json: String,
        operatorByJointRoute: Map<String, BusOperator> = emptyMap()
    ): List<StaticRouteStopRecord> {
        return dataArray(json).mapObjects { record ->
            val route = record.required("route")
            val operator = resolveKmbStaticOperator(record, route, operatorByJointRoute)
                ?: return@mapObjects null
            StaticRouteStopRecord(
                operator = operator,
                route = route,
                direction = record.required("bound"),
                serviceType = record.required("service_type"),
                sequence = record.positiveSequence(),
                stopId = record.required("stop")
            )
        }
    }

    fun parseKmbStops(json: String): Map<String, StaticStopRecord> {
        return dataArray(json).mapObjects { record -> parseStop(record) }.associateBy { it.id }
    }

    fun parseCtbRoutes(json: String): List<StaticRouteRecord> {
        return dataArray(json).mapObjects { record ->
            val operator = BusOperator.fromCode(record.optString("co", "CTB"))
                ?.takeIf { it == BusOperator.CTB }
                ?: throw StaticDataValidationException("Unknown CTB route operator")
            StaticRouteRecord(
                operator = operator,
                route = record.required("route"),
                direction = record.optString("bound").trim(),
                serviceType = ""
            )
        }
    }

    fun parseCtbRouteStops(json: String, direction: String): List<StaticRouteStopRecord> {
        if (direction.isBlank()) throw StaticDataValidationException("CTB direction is blank")
        return dataArray(json).mapObjects { record ->
            StaticRouteStopRecord(
                operator = BusOperator.CTB,
                route = record.required("route"),
                direction = direction,
                serviceType = "",
                sequence = record.positiveSequence(),
                stopId = record.required("stop")
            )
        }
    }

    fun parseCtbStop(json: String): StaticStopRecord {
        val root = JSONObject(json)
        val data = root.opt("data")
        val record = when (data) {
            is JSONObject -> data
            is JSONArray -> data.optJSONObject(0)
            else -> null
        } ?: throw StaticDataValidationException("CTB stop response has no data")
        return parseStop(record)
    }

    fun buildVariants(
        routeStops: List<StaticRouteStopRecord>,
        stops: Map<String, StaticStopRecord>
    ): List<StaticRouteVariant> {
        return routeStops.groupBy { record ->
            listOf(record.operator.code, record.route, record.direction, record.serviceType)
                .joinToString("|")
        }.values.map { records ->
            val first = records.first()
            val sequences = records.map { it.sequence }
            if (sequences.toSet().size != sequences.size) {
                throw StaticDataValidationException("Duplicate route-stop sequence")
            }
            StaticRouteVariant(
                operator = first.operator,
                route = first.route,
                direction = first.direction,
                serviceType = first.serviceType,
                stops = records.sortedBy { it.sequence }.map { routeStop ->
                    val stop = stops[routeStop.stopId]
                        ?: throw StaticDataValidationException("Missing stop ${routeStop.stopId}")
                    StaticRouteStop(
                        id = stop.id,
                        sequence = routeStop.sequence,
                        latitude = stop.latitude,
                        longitude = stop.longitude,
                        name = stop.nameTraditionalChinese
                    )
                }
            )
        }
    }

    private fun dataArray(json: String): JSONArray {
        val root = try {
            JSONObject(json)
        } catch (exception: Exception) {
            throw StaticDataValidationException("Static JSON is invalid: ${exception.message}")
        }
        return root.optJSONArray("data")
            ?: throw StaticDataValidationException("Static JSON has no data array")
    }

    private fun parseStop(record: JSONObject): StaticStopRecord {
        val latitude = record.required("lat").toDoubleOrNull()
            ?: throw StaticDataValidationException("Stop latitude is invalid")
        val longitude = record.required("long").toDoubleOrNull()
            ?: throw StaticDataValidationException("Stop longitude is invalid")
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw StaticDataValidationException("Stop coordinate is outside WGS84")
        }
        return StaticStopRecord(
            id = record.required("stop"),
            latitude = latitude,
            longitude = longitude,
            nameTraditionalChinese = record.optString("name_tc").trim(),
            nameSimplifiedChinese = record.optString("name_sc").trim(),
            nameEnglish = record.optString("name_en").trim()
        )
    }

    private fun JSONObject.required(name: String): String {
        return optString(name).trim().takeIf { it.isNotBlank() }
            ?: throw StaticDataValidationException("Required field $name is blank")
    }

    private fun JSONObject.positiveSequence(): Int {
        return required("seq").toIntOrNull()?.takeIf { it > 0 }
            ?: throw StaticDataValidationException("Route-stop sequence is invalid")
    }

    private fun resolveKmbStaticOperator(
        record: JSONObject,
        route: String,
        operatorByJointRoute: Map<String, BusOperator>
    ): BusOperator? {
        val explicitCode = record.optString("co").trim()
        if (explicitCode.isBlank()) return operatorByJointRoute[route] ?: BusOperator.KMB
        return BusOperator.fromCode(explicitCode)
            ?.takeIf { it == BusOperator.KMB || it == BusOperator.LWB }
    }

    private fun <T : Any> JSONArray.mapObjects(transform: (JSONObject) -> T?): List<T> {
        return buildList {
            for (index in 0 until length()) {
                val record = optJSONObject(index)
                    ?: throw StaticDataValidationException("Data record is not an object")
                transform(record)?.let(::add)
            }
        }
    }

    private fun parseCsvRow(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index += 1
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    values += current.toString()
                    current.setLength(0)
                }
                else -> current.append(character)
            }
            index += 1
        }
        if (quoted) throw StaticDataValidationException("CSV row has an unclosed quote")
        values += current.toString()
        return values
    }
}
