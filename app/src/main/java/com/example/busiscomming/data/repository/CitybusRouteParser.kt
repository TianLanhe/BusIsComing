package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.FirstLegEtaQuery
import com.example.busiscomming.data.model.WaitTimeState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.math.BigDecimal
import java.math.RoundingMode

class CitybusRouteParseException(message: String) : IllegalArgumentException(message)

object CitybusRouteParser {
    fun parse(response: String): List<BusRouteOption> {
        val document = Jsoup.parse(response)
        val routeList = document.allElements.firstOrNull { it.id().trim() == ROUTE_LIST_ID }
            ?: throw CitybusRouteParseException("Citybus route response missing routelist2")

        val candidates = routeList.children()
            .filter { it.tagName().equals("table", ignoreCase = true) }
            .filter { it.hasRouteCandidateContent() }

        return candidates.mapNotNull { parseCandidate(it) }
    }

    private fun parseCandidate(table: Element): BusRouteOption? {
        val routeOption = parseFromAriaLabel(table.attr("aria-label")) ?: parseFromTableText(table)
        val firstLegEtaQuery = parseFirstLegEtaQuery(table)
        return routeOption?.copy(
            firstLegEtaQuery = firstLegEtaQuery,
            waitTimeState = if (firstLegEtaQuery == null) {
                WaitTimeState.Unavailable
            } else {
                WaitTimeState.Loading
            }
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

    private fun parseFirstLegEtaQuery(table: Element): FirstLegEtaQuery? {
        val info = findShowRouteP2pInfo(table) ?: return null
        val parts = info.split(P2P_LEG_SEPARATOR)
        val legCount = parts.firstOrNull()?.toIntOrNull() ?: return null
        if (legCount < 1 || parts.size <= 1) return null

        val fields = parts[1].split(P2P_FIELD_SEPARATOR)
        if (fields.size < 5) return null

        val company = fields[0].trim()
        val routeVariant = fields[1].trim()
        val boardingSeq = fields[2].trim().toIntOrNull() ?: return null
        val alightingSeq = fields[3].trim().toIntOrNull() ?: return null
        val bound = fields[4].trim()
        val directionPath = bound.toDirectionPath() ?: return null
        if (company.isBlank() || routeVariant.isBlank()) return null

        return FirstLegEtaQuery(
            company = company,
            routeVariant = routeVariant,
            route = routeVariant.toPublicRoute(),
            boardingSeq = boardingSeq,
            alightingSeq = alightingSeq,
            bound = bound,
            directionPath = directionPath
        )
    }

    private fun findShowRouteP2pInfo(table: Element): String? {
        val attributes = table.attributes().asList()
        for (attribute in attributes) {
            val match = SHOW_ROUTE_P2P_PATTERN.find(attribute.value)
            if (match != null) return match.groupValues[1]
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

    private const val ROUTE_LIST_ID = "routelist2"
    private const val ROUTE_CELL_CLASS = "routenocell"
    private const val ROUTE_JOINER = " \u2192 "
    private const val DURATION_KEYWORD = "預計"
    private const val P2P_LEG_SEPARATOR = "|*|"
    private const val P2P_FIELD_SEPARATOR = "||"
    private val SHOW_ROUTE_P2P_PATTERN = Regex("""showroutep2p\('([^']*)'""")
    private val ROUTE_PRICE_PATTERN =
        Regex("""(?:^|\s+至\s+)([^\s]+)\s+(?:港元\s*([0-9]+(?:\.[0-9]+)?)|(免費)(?:\s*\*)?)""")
    private val PRICE_TEXT_PATTERN = Regex("""(?:\$\s*([0-9]+(?:\.[0-9]+)?)|(免費)(?:\s*\*)?)""")
    private val DURATION_PATTERN = Regex("""預計\s*([0-9]+)\s*分鐘""")
    private val DURATION_TEXT_PATTERN = Regex("""預計\s*([0-9]+)\s*分鐘""")
    private val WALKING_DISTANCE_PATTERN = Regex("""步行距離\s*\(約\)\s*([0-9]+)\s*米""")
}
