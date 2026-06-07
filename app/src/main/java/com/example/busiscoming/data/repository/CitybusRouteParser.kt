package com.example.busiscoming.data.repository

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.P2pRouteDetailQuery
import com.example.busiscoming.data.model.P2pRouteLeg
import com.example.busiscoming.data.model.P2pRoutePlan
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.model.buildBusRouteResultId
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.math.RoundingMode

class CitybusRouteParseException(message: String) : IllegalArgumentException(message)

object CitybusRouteParser {
    fun parse(response: String, lang: String = DEFAULT_LANG): List<BusRouteOption> {
        val document = Jsoup.parse(response)
        val routeList = document.allElements.firstOrNull { it.id().trim() == ROUTE_LIST_ID }
            ?: throw CitybusRouteParseException("Citybus route response missing routelist2")

        val candidates = routeList.children()
            .filter { it.tagName().equals("table", ignoreCase = true) }
            .filter { it.hasRouteCandidateContent() }

        return candidates.mapNotNull { parseCandidate(it, lang) }
    }

    private fun parseCandidate(table: Element, lang: String): BusRouteOption? {
        val routeOption = parseFromAriaLabel(table.attr("aria-label")) ?: parseFromTableText(table)
        val routeDetailQuery = parseRouteDetailQuery(table, lang)
        val firstLegEtaQuery = routeDetailQuery?.plan?.legs?.firstOrNull()?.toFirstLegEtaQuery()
        return routeOption?.copy(
            firstLegEtaQuery = firstLegEtaQuery,
            routeDetailQuery = routeDetailQuery,
            waitTimeState = if (firstLegEtaQuery == null) {
                WaitTimeState.Unavailable
            } else {
                WaitTimeState.Loading
            },
            resultId = buildBusRouteResultId(
                routeSegments = routeOption.routeSegments,
                priceHkd = routeOption.priceHkd,
                durationMinutes = routeOption.durationMinutes,
                walkingDistanceMeters = routeOption.walkingDistanceMeters,
                rawInfo = routeDetailQuery?.rawInfo
            )
        )
    }

    private fun parseFromAriaLabel(label: String): BusRouteOption? {
        if (label.isBlank()) return null

        val segments = ROUTE_PRICE_PATTERN.findAll(label).mapNotNull { match ->
            val price = parsePrice(
                numericPrice = match.groupValues[2],
                freePrice = match.groupValues[3]
            ) ?: return@mapNotNull null
            RouteSegment(
                routeName = match.groupValues[1].trim(),
                priceHkd = price
            )
        }.toList()

        val duration = DURATION_PATTERN.find(label)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val walkingDistance = WALKING_DISTANCE_PATTERN.find(label)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        return segments.toRouteOption(duration, walkingDistance)
    }

    private fun parseFromTableText(table: Element): BusRouteOption? {
        val routeNames = table.select("table")
            .filter { it.classNames().contains(ROUTE_CELL_CLASS) }
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val prices = PRICE_TEXT_PATTERN.findAll(table.text()).mapNotNull { match ->
            parsePrice(
                numericPrice = match.groupValues[1],
                freePrice = match.groupValues[2]
            )
        }.toList()

        val duration = DURATION_TEXT_PATTERN.find(table.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        val walkingDistance = WALKING_DISTANCE_PATTERN.find(table.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: return null

        if (routeNames.isEmpty() || routeNames.size != prices.size) return null
        return routeNames.zip(prices)
            .map { (routeName, price) -> RouteSegment(routeName, price) }
            .toRouteOption(duration, walkingDistance)
    }

    private fun parsePrice(numericPrice: String, freePrice: String): Double? {
        if (freePrice.isNotBlank()) return 0.0
        return numericPrice.toDoubleOrNull()
    }

    private fun parseRouteDetailQuery(table: Element, lang: String): P2pRouteDetailQuery? {
        val args = findShowRouteP2pArgs(table) ?: return null
        val plan = parseP2pRoutePlan(args.rawInfo)?.copy(lang = lang) ?: return null
        return P2pRouteDetailQuery(
            rawInfo = args.rawInfo,
            generalInfo = args.generalInfo,
            listId = args.listId,
            lang = lang,
            plan = plan
        )
    }

    fun parseP2pRoutePlan(rawInfo: String): P2pRoutePlan? {
        val parts = rawInfo.split(P2P_LEG_SEPARATOR)
        val legCount = parts.firstOrNull()?.toIntOrNull() ?: return null
        if (legCount < 1 || parts.size <= 1) return null

        val legs = parts.drop(1)
            .take(legCount)
            .mapNotNull { part -> parseP2pRouteLeg(part) }
        if (legs.size != legCount) return null
        return P2pRoutePlan(rawInfo = rawInfo, lang = DEFAULT_LANG, legs = legs)
    }

    private fun parseP2pRouteLeg(rawLeg: String): P2pRouteLeg? {
        val fields = rawLeg.split(P2P_FIELD_SEPARATOR)
        if (fields.size < 5) return null

        val company = fields[0].trim()
        val routeVariant = fields[1].trim()
        val boardingSeq = fields[2].trim().toIntOrNull() ?: return null
        val alightingSeq = fields[3].trim().toIntOrNull() ?: return null
        val bound = fields[4].trim()
        if (company.isBlank() || routeVariant.isBlank()) return null

        return P2pRouteLeg(
            company = company,
            routeVariant = routeVariant,
            route = routeVariant.toPublicRoute(),
            boardingSeq = boardingSeq,
            alightingSeq = alightingSeq,
            bound = bound,
            directionPath = bound.toDirectionPath()
        )
    }

    private fun findShowRouteP2pArgs(table: Element): ShowRouteP2pArgs? {
        val attributes = table.attributes().asList()
        for (attribute in attributes) {
            val match = SHOW_ROUTE_P2P_PATTERN.find(attribute.value)
            if (match != null) {
                return ShowRouteP2pArgs(
                    rawInfo = match.groupValues[1],
                    listId = match.groupValues[2],
                    generalInfo = match.groupValues[3]
                )
            }
        }
        return null
    }

    private fun String.toPublicRoute(): String = substringBefore("-")

    private fun String.toDirectionPath(): String? {
        return when (this) {
            "O" -> "outbound"
            "I" -> "inbound"
            else -> null
        }
    }

    private fun List<RouteSegment>.toRouteOption(
        durationMinutes: Int,
        walkingDistanceMeters: Int
    ): BusRouteOption? {
        if (isEmpty()) return null
        val routeSegments = map { it.routeName }
        val routeName = joinToString(ROUTE_JOINER) { it.routeName }
        val totalPrice = sumOf { BigDecimal.valueOf(it.priceHkd) }
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()
        return BusRouteOption(
            routeName = routeName,
            routeSegments = routeSegments,
            priceHkd = totalPrice,
            durationMinutes = durationMinutes,
            arrivalMinutes = durationMinutes,
            transferCount = (size - 1).coerceAtLeast(0),
            walkingDistanceMeters = walkingDistanceMeters
        )
    }

    private fun Element.hasRouteCandidateContent(): Boolean {
        val ariaLabel = attr("aria-label")
        return ariaLabel.contains(DURATION_KEYWORD) || text().contains(DURATION_KEYWORD)
    }

    private data class RouteSegment(
        val routeName: String,
        val priceHkd: Double
    )

    private data class ShowRouteP2pArgs(
        val rawInfo: String,
        val listId: String,
        val generalInfo: String
    )

    private const val ROUTE_LIST_ID = "routelist2"
    private const val ROUTE_CELL_CLASS = "routenocell"
    private const val ROUTE_JOINER = " \u2192 "
    private const val DURATION_KEYWORD = "預計"
    private const val DEFAULT_LANG = "0"
    private const val P2P_LEG_SEPARATOR = "|*|"
    private const val P2P_FIELD_SEPARATOR = "||"
    private val SHOW_ROUTE_P2P_PATTERN =
        Regex("""showroutep2p\('([^']*)'\s*,\s*'([^']*)'\s*,\s*'([^']*)'""")
    private val ROUTE_PRICE_PATTERN =
        Regex("""(?:^|\s+至\s+)([^\s]+)\s+(?:港元\s*([0-9]+(?:\.[0-9]+)?)|(免費)(?:\s*\*)?)""")
    private val PRICE_TEXT_PATTERN = Regex("""(?:\$\s*([0-9]+(?:\.[0-9]+)?)|(免費)(?:\s*\*)?)""")
    private val DURATION_PATTERN = Regex("""預計\s*([0-9]+)\s*分鐘""")
    private val DURATION_TEXT_PATTERN = Regex("""預計\s*([0-9]+)\s*分鐘""")
    private val WALKING_DISTANCE_PATTERN = Regex("""步行距離\s*\(約\)\s*([0-9]+)\s*米""")
}
