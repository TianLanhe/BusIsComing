package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.P2pRoutePlan
import com.example.busiscoming.data.model.RouteDetailDisplayFormatter
import com.example.busiscoming.data.model.RouteDetailLeg
import com.example.busiscoming.data.model.RouteDetailStop
import com.example.busiscoming.data.model.RouteDetailStopRole
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class CitybusRouteDetailParseException(message: String) : IllegalArgumentException(message)

object CitybusRouteDetailParser {
    fun parse(response: String, plan: P2pRoutePlan): List<RouteDetailLeg> {
        val document = Jsoup.parse(response)
        val stopRows = document.allElements
            .filter { it.tagName().equals("table", ignoreCase = true) }
            .filter { it.classNames().contains(STOP_ROW_CLASS) }
            .mapNotNull { parseStopRow(it) }
        if (stopRows.isEmpty()) {
            throw CitybusRouteDetailParseException("Citybus route detail response has no station rows")
        }

        val directionTexts = parseDirectionTexts(document.root())
        return plan.legs.mapIndexed { index, leg ->
            val legStops = stopRows
                .filter { it.routeVariant == leg.routeVariant }
                .filter { it.sequence in leg.boardingSeq..leg.alightingSeq }
                .sortedBy { it.sequence }

            val boarding = legStops.firstOrNull { it.sequence == leg.boardingSeq }
                ?: throw CitybusRouteDetailParseException("Missing boarding station for ${leg.routeVariant}")
            val alighting = legStops.firstOrNull { it.sequence == leg.alightingSeq }
                ?: throw CitybusRouteDetailParseException("Missing alighting station for ${leg.routeVariant}")
            val viaStops = legStops
                .filter { it.sequence != leg.boardingSeq && it.sequence != leg.alightingSeq }
                .map { it.toRouteDetailStop(RouteDetailStopRole.VIA) }

            RouteDetailLeg(
                route = leg.route,
                routeVariant = leg.routeVariant,
                directionText = directionTexts.getOrNull(index),
                boardingStop = boarding.toRouteDetailStop(RouteDetailStopRole.BOARDING),
                viaStops = viaStops,
                alightingStop = alighting.toRouteDetailStop(RouteDetailStopRole.ALIGHTING)
            )
        }
    }

    fun parseOriginWalkingDistanceMeters(response: String): Int? {
        val firstRouteTitleIndex = response.indexOf(ROUTE_TITLE_CLASS, ignoreCase = true)
        val searchScope = if (firstRouteTitleIndex > 0) {
            response.substring(0, firstRouteTitleIndex)
        } else {
            response
        }
        return WALKING_DISTANCE_PATTERN.find(searchScope)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toIntOrNull()
    }

    private fun parseStopRow(row: Element): ParsedStop? {
        val onclick = row.attr("onclick").ifBlank { row.attr("onkeypress") }
        val match = STOP_CLICK_PATTERN.find(onclick) ?: return null
        val fields = parseFunctionArgs(match.groupValues[1])
        if (fields.size < 5) return null

        val rawName = row.select("td")
            .asSequence()
            .filter { it.attr("align").trim().equals("left", ignoreCase = true) }
            .map { it.ownText().trim() }
            .firstOrNull { text ->
                text.isNotBlank() &&
                    text.toIntOrNull() == null &&
                    !text.startsWith("車費")
            }
            ?: return null

        val sequence = fields[3].toIntOrNull() ?: return null
        val latitude = fields[1].toDoubleOrNull() ?: return null
        val longitude = fields[2].toDoubleOrNull() ?: return null
        val stopId = fields[0].trim()
        val routeVariant = fields[4].trim()
        if (stopId.isBlank() || routeVariant.isBlank()) return null

        return ParsedStop(
            rawName = rawName,
            stopId = stopId,
            sequence = sequence,
            latitude = latitude,
            longitude = longitude,
            routeVariant = routeVariant
        )
    }

    private fun parseDirectionTexts(root: Element): List<String?> {
        return root.allElements
            .filter { it.tagName().equals("table", ignoreCase = true) }
            .filter { it.classNames().contains(ROUTE_TITLE_CLASS) }
            .map { title ->
            val text = title.text()
            val afterMarker = text.substringAfter("往", missingDelimiterValue = "").trim()
            afterMarker.substringBefore(" ").trim().takeIf { it.isNotBlank() }
        }
    }

    private fun parseFunctionArgs(argumentList: String): List<String> {
        return FUNCTION_ARG_PATTERN.findAll(argumentList)
            .map { it.groupValues[1] }
            .toList()
    }

    private fun ParsedStop.toRouteDetailStop(role: RouteDetailStopRole): RouteDetailStop {
        return RouteDetailStop(
            rawName = rawName,
            displayName = RouteDetailDisplayFormatter.stationDisplayName(rawName),
            stopId = stopId,
            sequence = sequence,
            latitude = latitude,
            longitude = longitude,
            routeVariant = routeVariant,
            role = role
        )
    }

    private data class ParsedStop(
        val rawName: String,
        val stopId: String,
        val sequence: Int,
        val latitude: Double,
        val longitude: Double,
        val routeVariant: String
    )

    private val STOP_CLICK_PATTERN = Regex("""stopclick1\(([^)]*)\)""")
    private val FUNCTION_ARG_PATTERN = Regex("""'([^']*)'""")
    private val WALKING_DISTANCE_PATTERN = Regex("""步行距離\s*\(約\)\s*([0-9,]+)\s*米""")
    private const val STOP_ROW_CLASS = "p2plistcell"
    private const val ROUTE_TITLE_CLASS = "p2proutetitle"
}
