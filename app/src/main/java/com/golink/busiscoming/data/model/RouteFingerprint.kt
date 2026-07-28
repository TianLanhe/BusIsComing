package com.golink.busiscoming.data.model

sealed interface RouteFingerprintResolution {
    data class Eligible(val fingerprint: String) : RouteFingerprintResolution
    data class Duplicate(val fingerprint: String) : RouteFingerprintResolution
    data object MissingIdentity : RouteFingerprintResolution
}

object RouteFingerprintFormatter {
    private const val VERSION_PREFIX = "v1|"

    fun create(route: BusRouteOption): String? {
        val legs = route.routeDetailQuery?.plan?.legs.orEmpty()
        if (legs.isEmpty() || legs.any { !it.hasStrictIdentity() }) return null
        return buildString {
            append(VERSION_PREFIX)
            append(legs.size)
            append('|')
            legs.forEachIndexed { index, leg ->
                if (index > 0) append('|')
                appendField(leg.company.trim())
                appendField(leg.route.trim())
                appendField(leg.routeVariant.trim())
                appendField(leg.bound.trim())
                appendField(leg.directionPath!!.trim())
                appendField(leg.boardingSeq.toString())
                appendField(leg.alightingSeq.toString())
            }
        }
    }

    fun resolve(routes: List<BusRouteOption>): List<RouteFingerprintResolution> {
        val fingerprints = routes.map(::create)
        val counts = fingerprints.filterNotNull().groupingBy { it }.eachCount()
        return fingerprints.map { fingerprint ->
            when {
                fingerprint == null -> RouteFingerprintResolution.MissingIdentity
                counts.getValue(fingerprint) > 1 ->
                    RouteFingerprintResolution.Duplicate(fingerprint)
                else -> RouteFingerprintResolution.Eligible(fingerprint)
            }
        }
    }

    private fun P2pRouteLeg.hasStrictIdentity(): Boolean {
        return company.isNotBlank() &&
            route.isNotBlank() &&
            routeVariant.isNotBlank() &&
            bound.isNotBlank() &&
            !directionPath.isNullOrBlank() &&
            boardingSeq > 0 &&
            alightingSeq >= boardingSeq
    }

    private fun StringBuilder.appendField(value: String) {
        append(value.length)
        append(':')
        append(value)
    }
}
