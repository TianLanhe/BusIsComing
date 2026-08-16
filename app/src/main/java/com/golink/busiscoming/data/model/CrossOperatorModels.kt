package com.golink.busiscoming.data.model

enum class BusOperator(val code: String) {
    CTB("CTB"),
    KMB("KMB"),
    LWB("LWB");

    companion object {
        fun fromCode(value: String?): BusOperator? {
            val normalized = value?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.code == normalized }
        }
    }
}

enum class GlobalStaticSource {
    GTFS_ROUTES,
    KMB_ROUTES,
    KMB_ROUTE_STOPS,
    KMB_STOPS,
    CTB_ROUTES
}

data class CachedStaticSource(
    val etag: String?,
    val lastModified: String?,
    val body: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        return other is CachedStaticSource &&
            etag == other.etag &&
            lastModified == other.lastModified &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        return 31 * (31 * (etag?.hashCode() ?: 0) + (lastModified?.hashCode() ?: 0)) +
            body.contentHashCode()
    }
}

data class JointOperatorRoute(
    val route: String,
    val partner: BusOperator
)

data class StaticRouteRecord(
    val operator: BusOperator,
    val route: String,
    val direction: String,
    val serviceType: String
)

data class StaticRouteStopRecord(
    val operator: BusOperator,
    val route: String,
    val direction: String,
    val serviceType: String,
    val sequence: Int,
    val stopId: String
)

data class StaticStopRecord(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val nameTraditionalChinese: String = "",
    val nameSimplifiedChinese: String = "",
    val nameEnglish: String = ""
)

data class StaticRouteStop(
    val id: String,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
    val name: String = ""
)

data class StaticRouteVariant(
    val operator: BusOperator,
    val route: String,
    val direction: String,
    val serviceType: String,
    val stops: List<StaticRouteStop>
)

data class CtbRouteSlice(
    val route: String,
    val direction: String,
    val verifiedDataDay: String,
    val fingerprint: String,
    val stops: List<StaticRouteStop>
)

enum class CrossOperatorMatchStatus {
    MATCHED,
    NO_MATCH
}

data class CrossOperatorStopPair(
    val ctbStopId: String,
    val operatorStopId: String,
    val operatorSequence: Int,
    val distanceMeters: Double
)

data class CrossOperatorRouteMatch(
    val status: CrossOperatorMatchStatus,
    val winner: StaticRouteVariant?,
    val rawCost: Double,
    val normalizedCost: Double,
    val stopPairs: List<CrossOperatorStopPair>,
    val algorithmVersion: Int,
    val gapCostMeters: Double,
    val thresholdMetersPerStop: Double
)

data class CrossOperatorEtaQuery(
    val operator: BusOperator,
    val route: String,
    val direction: String,
    val serviceType: String,
    val boardingStopId: String,
    val alightingStopId: String
)
