package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole

enum class RouteDetailStructureInvalidReason {
    LEG_COUNT_MISMATCH,
    LEG_IDENTITY_MISMATCH,
    ENDPOINT_MISMATCH,
    ROLE_MISMATCH,
    SEQUENCE_COVERAGE_MISMATCH,
    INVALID_STOP_ID,
    INVALID_STOP_COORDINATE,
    STOP_VARIANT_MISMATCH
}

sealed interface RouteDetailStructureValidationResult {
    data object Valid : RouteDetailStructureValidationResult

    data class Invalid(
        val reason: RouteDetailStructureInvalidReason,
        val legIndex: Int? = null
    ) : RouteDetailStructureValidationResult
}

object RouteDetailStructureValidator {
    fun validate(
        plan: P2pRoutePlan,
        legs: List<RouteDetailLeg>
    ): RouteDetailStructureValidationResult {
        if (legs.size != plan.legs.size) {
            return invalid(RouteDetailStructureInvalidReason.LEG_COUNT_MISMATCH)
        }

        legs.forEachIndexed { index, leg ->
            val planned = plan.legs[index]
            if (leg.route != planned.route || leg.routeVariant != planned.routeVariant) {
                return invalid(RouteDetailStructureInvalidReason.LEG_IDENTITY_MISMATCH, index)
            }
            if (
                leg.boardingStop.sequence != planned.boardingSeq ||
                leg.alightingStop.sequence != planned.alightingSeq
            ) {
                return invalid(RouteDetailStructureInvalidReason.ENDPOINT_MISMATCH, index)
            }

            val stops = listOf(leg.boardingStop) + leg.viaStops + leg.alightingStop
            if (
                leg.boardingStop.role != RouteDetailStopRole.BOARDING ||
                leg.alightingStop.role != RouteDetailStopRole.ALIGHTING ||
                leg.viaStops.any { it.role != RouteDetailStopRole.VIA }
            ) {
                return invalid(RouteDetailStructureInvalidReason.ROLE_MISMATCH, index)
            }
            if (stops.map { it.sequence } != (planned.boardingSeq..planned.alightingSeq).toList()) {
                return invalid(RouteDetailStructureInvalidReason.SEQUENCE_COVERAGE_MISMATCH, index)
            }
            if (stops.any { it.stopId.isBlank() }) {
                return invalid(RouteDetailStructureInvalidReason.INVALID_STOP_ID, index)
            }
            if (stops.any { !it.hasValidCoordinate() }) {
                return invalid(RouteDetailStructureInvalidReason.INVALID_STOP_COORDINATE, index)
            }
            if (stops.any { it.routeVariant != planned.routeVariant }) {
                return invalid(RouteDetailStructureInvalidReason.STOP_VARIANT_MISMATCH, index)
            }
        }

        return RouteDetailStructureValidationResult.Valid
    }

    private fun invalid(
        reason: RouteDetailStructureInvalidReason,
        legIndex: Int? = null
    ): RouteDetailStructureValidationResult.Invalid {
        RouteDetailDiagnostics.record(
            RouteDetailDiagnosticEvent(
                category = "structure",
                action = "rejected",
                generation = legIndex,
                reason = reason.name
            )
        )
        return RouteDetailStructureValidationResult.Invalid(reason, legIndex)
    }

    private fun RouteDetailStop.hasValidCoordinate(): Boolean {
        return latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
    }
}
