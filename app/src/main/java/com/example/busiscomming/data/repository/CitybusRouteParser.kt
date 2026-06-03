package com.example.busiscomming.data.repository

import com.example.busiscomming.data.model.BusRouteOption
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
        return parseFromAriaLabel(table.attr("aria-label")) ?: parseFromTableText(table)
    }

    private fun parseFromAriaLabel(label: String): BusRouteOption? {
        if (label.isBlank()) return null

        val segments = ROUTE_PRICE_PATTERN.findAll(label).map { match ->
            RouteSegment(
                routeName = match.groupValues[1].trim(),
                priceHkd = match.groupValues[2].toDoubleOrNull() ?: return@map null
            )
        }.filterNotNull().toList()

        val duration = DURATION_PATTERN.find(label)?.groupValues?.get(1)?.toIntOrNull()
            ?: return null
        return segments.toRouteOption(duration)
    }

    private fun parseFromTableText(table: Element): BusRouteOption? {
        val routeNames = table.select("table")
            .filter { it.classNames().contains(ROUTE_CELL_CLASS) }
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val prices = PRICE_TEXT_PATTERN.findAll(table.text()).mapNotNull { match ->
            match.groupValues[1].toDoubleOrNull()
        }.toList()

        val duration = DURATION_TEXT_PATTERN.find(table.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: return null

        if (routeNames.isEmpty() || routeNames.size != prices.size) return null
        return routeNames.zip(prices)
            .map { (routeName, price) -> RouteSegment(routeName, price) }
            .toRouteOption(duration)
    }

    private fun List<RouteSegment>.toRouteOption(durationMinutes: Int): BusRouteOption? {
        if (isEmpty()) return null
        val routeName = joinToString(ROUTE_JOINER) { it.routeName }
        val totalPrice = sumOf { BigDecimal.valueOf(it.priceHkd) }
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()
        return BusRouteOption(
            routeName = routeName,
            priceHkd = totalPrice,
            durationMinutes = durationMinutes,
            arrivalMinutes = durationMinutes,
            transferCount = (size - 1).coerceAtLeast(0)
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
    private val ROUTE_PRICE_PATTERN = Regex("""([^\s]+)\s+港元\s*([0-9]+(?:\.[0-9]+)?)""")
    private val PRICE_TEXT_PATTERN = Regex("""\$\s*([0-9]+(?:\.[0-9]+)?)""")
    private val DURATION_PATTERN = Regex("""預計\s*([0-9]+)\s*分鐘""")
    private val DURATION_TEXT_PATTERN = Regex("""預計\s*([0-9]+)\s*分鐘""")
}
