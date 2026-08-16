package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.local.CrossOperatorSnapshotStore
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CrossOperatorEtaQuery
import com.golink.busiscoming.data.model.CrossOperatorMatchStatus
import com.golink.busiscoming.data.model.CrossOperatorRouteMatch
import com.golink.busiscoming.data.model.CtbRouteSlice
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.StaticRouteVariant
import java.security.MessageDigest
import java.util.concurrent.Executor
import java.util.concurrent.Executors

interface CrossOperatorMatchStore {
    fun load(
        route: String,
        direction: String,
        ctbFingerprint: String,
        snapshotId: String,
        operatorFingerprint: String
    ): CrossOperatorRouteMatch?

    fun save(
        route: String,
        direction: String,
        ctbFingerprint: String,
        snapshotId: String,
        operatorFingerprint: String,
        match: CrossOperatorRouteMatch
    )
}

enum class CrossOperatorMappingReason {
    NO_SNAPSHOT,
    NOT_JOINT,
    SLICE_UNAVAILABLE,
    NO_CANDIDATE,
    NO_MATCH,
    P2P_MAPPING_INVALID,
    INPUT_CHANGED
}

sealed interface CrossOperatorMappingResolution {
    data class Enabled(
        val query: CrossOperatorEtaQuery,
        val match: CrossOperatorRouteMatch
    ) : CrossOperatorMappingResolution

    data class Disabled(val reason: CrossOperatorMappingReason) : CrossOperatorMappingResolution
}

class CrossOperatorMappingRepository(
    private val snapshotStore: CrossOperatorSnapshotStore,
    private val sliceStore: CtbRouteSliceStore,
    private val matchStore: CrossOperatorMatchStore,
    private val routeLoader: (String, String) -> List<CtbRouteSlice>,
    private val backgroundExecutor: Executor = Executors.newFixedThreadPool(2),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val matchRoutes: (StaticRouteVariant, List<StaticRouteVariant>) -> CrossOperatorRouteMatch =
        { ctb, candidates -> CrossOperatorRouteMatcher().match(ctb, candidates) }
) {
    fun resolve(
        query: FirstLegEtaQuery,
        boardingCtbStopId: String,
        alightingCtbStopId: String
    ): CrossOperatorMappingResolution {
        return resolveInternal(query, boardingCtbStopId, alightingCtbStopId, allowVersionRetry = true)
    }

    private fun resolveInternal(
        query: FirstLegEtaQuery,
        boardingCtbStopId: String,
        alightingCtbStopId: String,
        allowVersionRetry: Boolean
    ): CrossOperatorMappingResolution {
        val snapshot = runCatching(snapshotStore::activeSnapshot).getOrNull()
            ?: return CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.NO_SNAPSHOT)
        val partner = snapshot.jointRoutes.firstOrNull { it.route == query.route }?.partner
            ?: return CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.NOT_JOINT)
        if (partner != BusOperator.KMB && partner != BusOperator.LWB) {
            return CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.NOT_JOINT)
        }
        val dataDay = HongKongDataDay.forInstant(clock())
        val storedSlice = runCatching {
            sliceStore.loadCtbRouteSlice(query.route, query.directionPath)
        }.getOrNull()
        val slice = when {
            storedSlice == null -> runCatching { routeLoader(query.route, dataDay) }
                .getOrNull()
                ?.firstOrNull { it.direction == query.directionPath }
            storedSlice.verifiedDataDay != dataDay -> {
                backgroundExecutor.execute { runCatching { routeLoader(query.route, dataDay) } }
                storedSlice
            }
            else -> storedSlice
        } ?: return CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.SLICE_UNAVAILABLE)

        val candidates = snapshot.variants.filter { variant ->
            variant.route == query.route && variant.operator == partner
        }
        if (candidates.isEmpty()) {
            return CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.NO_CANDIDATE)
        }
        val ctbVariant = StaticRouteVariant(
            operator = BusOperator.CTB,
            route = slice.route,
            direction = slice.direction,
            serviceType = "",
            stops = slice.stops
        )
        val operatorFingerprint = candidatesFingerprint(candidates)
        var match = matchStore.load(
            query.route,
            query.directionPath,
            slice.fingerprint,
            snapshot.id,
            operatorFingerprint
        )
        if (match == null) {
            match = matchRoutes(ctbVariant, candidates)
            val currentSnapshot = runCatching(snapshotStore::activeSnapshot).getOrNull()
            val currentSlice = runCatching {
                sliceStore.loadCtbRouteSlice(query.route, query.directionPath)
            }.getOrNull()
            if (currentSnapshot?.id != snapshot.id || currentSlice?.fingerprint != slice.fingerprint) {
                return if (allowVersionRetry) {
                    resolveInternal(query, boardingCtbStopId, alightingCtbStopId, false)
                } else {
                    CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.INPUT_CHANGED)
                }
            }
            matchStore.save(
                query.route,
                query.directionPath,
                slice.fingerprint,
                snapshot.id,
                operatorFingerprint,
                match
            )
        }
        if (match.status != CrossOperatorMatchStatus.MATCHED) {
            return CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.NO_MATCH)
        }
        val etaQuery = P2pCrossOperatorGate.resolve(match, boardingCtbStopId, alightingCtbStopId)
            ?: return CrossOperatorMappingResolution.Disabled(CrossOperatorMappingReason.P2P_MAPPING_INVALID)
        return CrossOperatorMappingResolution.Enabled(etaQuery, match)
    }

    private fun candidatesFingerprint(candidates: List<StaticRouteVariant>): String {
        val value = candidates.map(RouteSemanticFingerprint::of).sorted().joinToString("\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}

