package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.ParsedRouteDetail
import com.golink.busiscoming.data.model.RouteDetailDisplayFormatter
import com.golink.busiscoming.data.model.RouteDetailCompleteness
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailTransfer
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

open class CitybusRouteDetailParseException(message: String) : IllegalArgumentException(message)

class CitybusRouteDetailStructureException(
    val validation: RouteDetailStructureValidationResult.Invalid
) : CitybusRouteDetailParseException(
    "Citybus route detail station structure is invalid: ${validation.reason} at leg ${validation.legIndex}"
)

object CitybusRouteDetailParser {
    fun parseDetail(response: String, plan: P2pRoutePlan): ParsedRouteDetail {
        val document = Jsoup.parse(response)
        val legs = parse(response, plan)
        val timetable = parseTimetable(document.root(), plan)
        val endpointRows = parseEndpointRows(document.root())
        val distances = timetable?.walkingDistances.orEmpty()
        val fallbackDistances = if (timetable == null) {
            parseFallbackWalkingDistances(response, plan.legs.size + 1)
        } else {
            List(plan.legs.size + 1) { null }
        }
        val transferCount = (legs.size - 1).coerceAtLeast(0)
        val sameStopTransfer = SAME_STOP_PATTERN.containsMatchIn(document.text())

        val enrichedLegs = legs.mapIndexed { index, leg ->
            val timing = timetable?.legs?.firstOrNull { it.routeVariant == leg.routeVariant }
                ?: timetable?.legs?.getOrNull(index)
            leg.copy(
                fareHkd = timing?.fareHkd,
                plannedBoardingTime = timing?.plannedBoardingTime,
                plannedAlightingTime = timing?.plannedAlightingTime
            )
        }
        val transfers = (0 until transferCount).map { index ->
            if (sameStopTransfer && transferCount == 1) {
                RouteDetailTransfer(RouteDetailTransferType.SAME_STOP)
            } else {
                RouteDetailTransfer(
                    type = RouteDetailTransferType.WALK_TO_TRANSFER_STOP,
                    walking = RouteDetailWalkingSegment(
                        kind = RouteDetailWalkingKind.TRANSFER,
                        distanceMeters = distances.getOrNull(index + 1)
                    )
                )
            }
        }

        val originWalking = RouteDetailWalkingSegment(
            RouteDetailWalkingKind.ORIGIN,
            distances.firstOrNull() ?: fallbackDistances.firstOrNull() ?: parseOriginWalkingDistanceMeters(response)
        )
        val destinationWalking = RouteDetailWalkingSegment(
            RouteDetailWalkingKind.DESTINATION,
            distances.getOrNull(transferCount + 1) ?:
                fallbackDistances.getOrNull(transferCount + 1) ?:
                parseDestinationWalkingDistanceMeters(response)
        )
        val mergedTransfers = transfers.mapIndexed { index, transfer ->
            if (transfer.type == RouteDetailTransferType.SAME_STOP || transfer.walking?.distanceMeters != null) {
                transfer
            } else {
                transfer.copy(
                    walking = transfer.walking?.copy(
                        distanceMeters = fallbackDistances.getOrNull(index + 1)
                    )
                )
            }
        }
        val allRequiredDistancesPresent = originWalking.distanceMeters != null &&
            destinationWalking.distanceMeters != null &&
            mergedTransfers.all { transfer ->
                transfer.type == RouteDetailTransferType.SAME_STOP || transfer.walking?.distanceMeters != null
            }
        val hasAnyWalkingDistance = originWalking.distanceMeters != null ||
            destinationWalking.distanceMeters != null ||
            mergedTransfers.any { it.walking?.distanceMeters != null }
        val completeness = when {
            allRequiredDistancesPresent -> RouteDetailCompleteness.COMPLETE
            timetable == null && !hasAnyWalkingDistance -> RouteDetailCompleteness.SESSION_MISSING
            else -> RouteDetailCompleteness.PARTIAL
        }

        return ParsedRouteDetail(
            legs = enrichedLegs,
            originWalking = originWalking,
            transfers = mergedTransfers,
            destinationWalking = destinationWalking,
            plannedDepartureTime = endpointRows.first,
            plannedArrivalTime = timetable?.plannedArrivalTime ?: endpointRows.second,
            originName = parseEndpointName(document.root(), "wpoint_from"),
            destinationName = parseEndpointName(document.root(), "wpoint_to"),
            completeness = completeness
        )
    }

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
        val legs = plan.legs.mapIndexed { index, leg ->
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
        val validation = RouteDetailStructureValidator.validate(plan, legs)
        if (validation is RouteDetailStructureValidationResult.Invalid) {
            throw CitybusRouteDetailStructureException(validation)
        }
        return legs
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

    fun parseDestinationWalkingDistanceMeters(response: String): Int? {
        val lastDestinationIndex = response.lastIndexOf("wpoint_to", ignoreCase = true)
        if (lastDestinationIndex < 0) return null
        val searchScope = response.substring(lastDestinationIndex)
        return WALKING_DISTANCE_PATTERN.find(searchScope)
            ?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()
    }

    private fun parseFallbackWalkingDistances(response: String, expectedCount: Int): List<Int?> {
        val values = WALKING_DISTANCE_PATTERN.findAll(response)
            .mapNotNull { match -> match.groupValues.getOrNull(1)?.replace(",", "")?.toIntOrNull() }
            .toList()
        if (values.isEmpty()) return List(expectedCount) { null }
        return List(expectedCount) { index -> values.getOrNull(index) }
    }

    private fun parseTimetable(root: Element, plan: P2pRoutePlan): ParsedTimetable? {
        val handler = root.allElements.asSequence()
            .map { it.attr("onclick") }
            .firstOrNull { it.contains("showtimetable1(") }
            ?: return null
        val payload = TIMETABLE_PATTERN.find(handler)?.groupValues?.getOrNull(1) ?: return null
        val sections = payload.split("|*|")
        val header = sections.firstOrNull()?.split("||") ?: return null
        val legCount = header.firstOrNull()?.toIntOrNull() ?: plan.legs.size
        val distances = (0..legCount).map { offset -> header.getOrNull(offset + 2)?.toIntOrNull() }
        val plannedArrival = header.getOrNull(1)?.substringAfter(',', "")?.toClockTime()
        val legs = sections.drop(1).mapNotNull { section ->
            val fields = section.split("||")
            val routeVariant = fields.getOrNull(2)?.trim().orEmpty()
            if (routeVariant.isBlank()) return@mapNotNull null
            ParsedTimetableLeg(
                routeVariant = routeVariant,
                fareHkd = fields.getOrNull(6)?.toDoubleOrNull(),
                plannedBoardingTime = fields.getOrNull(7)?.toClockTime(),
                plannedAlightingTime = fields.getOrNull(8)?.toClockTime()
            )
        }
        return ParsedTimetable(distances, plannedArrival, legs)
    }

    private fun parseEndpointRows(root: Element): Pair<String?, String?> {
        return parseEndpointTime(root, "wpoint_from") to parseEndpointTime(root, "wpoint_to")
    }

    private fun parseEndpointTime(root: Element, imageMarker: String): String? {
        val rowText = root.select("img[src*=$imageMarker]").firstOrNull()?.closest("tr")?.text()
        return rowText?.let { CLOCK_PATTERN.findAll(it).lastOrNull()?.value }
    }

    private fun parseEndpointName(root: Element, imageMarker: String): String? {
        val row = root.select("img[src*=$imageMarker]").firstOrNull()?.closest("tr") ?: return null
        return row.select("td").map { it.text().trim() }
            .firstOrNull { text ->
                text.isNotBlank() && !CLOCK_PATTERN.containsMatchIn(text) &&
                    !text.contains("起點") && !text.contains("终点") && !text.contains("終點")
            }
    }

    private fun String.toClockTime(): String? {
        return CLOCK_PATTERN.findAll(this).lastOrNull()?.value
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

    private data class ParsedTimetable(
        val walkingDistances: List<Int?>,
        val plannedArrivalTime: String?,
        val legs: List<ParsedTimetableLeg>
    )

    private data class ParsedTimetableLeg(
        val routeVariant: String,
        val fareHkd: Double?,
        val plannedBoardingTime: String?,
        val plannedAlightingTime: String?
    )

    private val STOP_CLICK_PATTERN = Regex("""stopclick1\(([^)]*)\)""")
    private val FUNCTION_ARG_PATTERN = Regex("""'([^']*)'""")
    private val WALKING_DISTANCE_PATTERN = Regex(
        """(?:步行距離\s*\(約\)|步行距离\s*\(约\)|Walking\s+distance\s*\((?:approx\.?|approximately)\))\s*([0-9,]+)\s*(?:米|m|metres?|meters?)""",
        RegexOption.IGNORE_CASE
    )
    private val TIMETABLE_PATTERN = Regex("""showtimetable1\('([^']*)'""")
    private val CLOCK_PATTERN = Regex("""\b(?:[01]\d|2[0-3]):[0-5]\d\b""")
    private val SAME_STOP_PATTERN = Regex("""同站[轉转]乘|same\s+stop""", RegexOption.IGNORE_CASE)
    private const val STOP_ROW_CLASS = "p2plistcell"
    private const val ROUTE_TITLE_CLASS = "p2proutetitle"
}
