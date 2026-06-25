package com.example.busiscoming

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.EtaArrival
import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.P2pRouteDetailQuery
import com.example.busiscoming.data.model.P2pRouteLeg
import com.example.busiscoming.data.model.P2pRoutePlan
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.model.RouteDetail
import com.example.busiscoming.data.model.RouteDetailLeg
import com.example.busiscoming.data.model.RouteDetailStop
import com.example.busiscoming.data.model.RouteDetailStopRole
import com.example.busiscoming.data.model.WaitTimeState

object DemoScreenshotFixtures {
    const val outputDirectory = "demo-screenshots"
    const val homeFavoritesResults = "home-favorites-results.png"
    const val homeAllRoutesSheet = "home-all-routes-sheet.png"
    const val routeDetailExpanded = "route-detail-expanded.png"
    const val etaArrivalsSheet = "eta-arrivals-sheet.png"
    const val lockscreenMonitor = "lockscreen-monitor.png"

    val allOutputFiles = listOf(
        homeFavoritesResults,
        homeAllRoutesSheet,
        routeDetailExpanded,
        etaArrivalsSheet,
        lockscreenMonitor
    )

    val savedRoutes = listOf(
        routeConfig(1L, "上班", "海庭苑", "景澄站", 22.2861, 114.1912, 22.2916, 114.2046),
        routeConfig(2L, "回家", "東灣碼頭", "松嶺邨", 22.2817, 114.1861, 22.2984, 114.2126),
        routeConfig(3L, "接送", "朗翠閣", "南灣書院", 22.2741, 114.1766, 22.2662, 114.1984),
        routeConfig(4L, "週末", "柏岸廣場", "雲海公園", 22.3034, 114.1792, 22.3128, 114.1935)
    )

    fun routeResults(): List<BusRouteOption> {
        return listOf(
            routeOption(
                routeName = "28X",
                routeSegments = listOf("28X"),
                priceHkd = 7.8,
                durationMinutes = 26,
                walkingDistanceMeters = 180,
                arrivals = listOf(
                    arrival(1, 4, "08:24", "景澄站", "正常班次"),
                    arrival(2, 11, "08:31", "景澄站", "低地台"),
                    arrival(3, 19, "08:39", "景澄站", "正常班次")
                ),
                boarding = "海庭苑平台",
                alighting = "景澄站北",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("28X")
            ),
            routeOption(
                routeName = "71A \u2192 28X",
                routeSegments = listOf("71A", "28X"),
                priceHkd = 9.6,
                durationMinutes = 31,
                walkingDistanceMeters = 240,
                arrivals = listOf(arrival(1, 6, "08:26", "景澄站", "正常班次")),
                boarding = "海庭苑南",
                alighting = "景澄站北",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("71A")
            ),
            routeOption(
                routeName = "16P",
                routeSegments = listOf("16P"),
                priceHkd = 6.4,
                durationMinutes = 34,
                walkingDistanceMeters = 210,
                arrivals = listOf(arrival(1, 9, "08:29", "景澄站", "正常班次")),
                boarding = "翠明街花園",
                alighting = "景澄站南",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("16P")
            ),
            routeOption(
                routeName = "52M \u2192 86",
                routeSegments = listOf("52M", "86"),
                priceHkd = 10.2,
                durationMinutes = 38,
                walkingDistanceMeters = 320,
                arrivals = listOf(arrival(1, 13, "08:33", "景澄站", "正常班次")),
                boarding = "海庭苑平台",
                alighting = "景澄站東",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("52M")
            )
        )
    }

    fun primaryRoute(): BusRouteOption = routeResults().first()

    fun routeDetail(): RouteDetail {
        return RouteDetail(
            routeName = "28X \u2192 86",
            priceHkd = 10.2,
            durationMinutes = 34,
            walkingDistanceMeters = 180,
            originWalkingDistanceMeters = 180,
            legs = listOf(
                RouteDetailLeg(
                    route = "28X",
                    routeVariant = "28X-DEMO-1",
                    directionText = "往景澄站方向",
                    boardingStop = stop("海庭苑平台", 1, "28X-DEMO-1", RouteDetailStopRole.BOARDING),
                    viaStops = listOf(
                        stop("翠明街花園", 2, "28X-DEMO-1", RouteDetailStopRole.VIA),
                        stop("雅湖里", 3, "28X-DEMO-1", RouteDetailStopRole.VIA)
                    ),
                    alightingStop = stop("景澄站北", 4, "28X-DEMO-1", RouteDetailStopRole.ALIGHTING)
                ),
                RouteDetailLeg(
                    route = "86",
                    routeVariant = "86-DEMO-1",
                    directionText = "往松嶺邨方向",
                    boardingStop = stop("景澄站南", 1, "86-DEMO-1", RouteDetailStopRole.BOARDING),
                    viaStops = listOf(
                        stop("雲海路口", 2, "86-DEMO-1", RouteDetailStopRole.VIA),
                        stop("柏岸廣場", 3, "86-DEMO-1", RouteDetailStopRole.VIA)
                    ),
                    alightingStop = stop("松嶺邨總站", 4, "86-DEMO-1", RouteDetailStopRole.ALIGHTING)
                )
            )
        )
    }

    fun lockscreenBodyText(): String {
        return "剩餘 2 分鐘 · 車 6 分鐘到 · 步行 4 分鐘 · 下一班 18 分鐘 · 08:20 更新"
    }

    private fun routeConfig(
        id: Long,
        name: String,
        originName: String,
        destinationName: String,
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): RouteConfig {
        return RouteConfig(
            id = id,
            name = name,
            origin = Place(originName, originLatitude, originLongitude),
            destination = Place(destinationName, destinationLatitude, destinationLongitude),
            usageCount = (10 - id).toInt(),
            lastUsedAt = 1_800_000_000_000L - id
        )
    }

    private fun routeOption(
        routeName: String,
        routeSegments: List<String>,
        priceHkd: Double,
        durationMinutes: Int,
        walkingDistanceMeters: Int,
        arrivals: List<EtaArrival>,
        boarding: String,
        alighting: String,
        routeDetailQuery: P2pRouteDetailQuery,
        firstLegEtaQuery: FirstLegEtaQuery
    ): BusRouteOption {
        return BusRouteOption(
            routeName = routeName,
            routeSegments = routeSegments,
            priceHkd = priceHkd,
            durationMinutes = durationMinutes,
            arrivalMinutes = arrivals.first().minutes,
            transferCount = routeSegments.size - 1,
            walkingDistanceMeters = walkingDistanceMeters,
            waitTimeState = WaitTimeState.Available(arrivals),
            firstLegEtaQuery = firstLegEtaQuery,
            routeDetailQuery = routeDetailQuery,
            stopPreview = RouteCardStopPreview(boarding, alighting)
        )
    }

    private fun arrival(
        sequence: Int,
        minutes: Int,
        arrivalTimeText: String,
        destination: String,
        remark: String
    ): EtaArrival {
        return EtaArrival(
            sequence = sequence,
            minutes = minutes,
            arrivalTimeText = arrivalTimeText,
            destination = destination,
            remark = remark,
            dataTimestampMillis = 1_780_000_000_000L
        )
    }

    private fun etaQuery(route: String): FirstLegEtaQuery {
        return FirstLegEtaQuery(
            company = "CTB",
            routeVariant = "$route-DEMO-1",
            route = route,
            boardingSeq = 1,
            alightingSeq = 4,
            bound = "O",
            directionPath = "outbound",
            rawInfo = "demo-$route",
            lang = "0"
        )
    }

    private fun detailQuery(): P2pRouteDetailQuery {
        return P2pRouteDetailQuery(
            rawInfo = "demo-detail",
            generalInfo = "",
            listId = "1",
            lang = "0",
            plan = P2pRoutePlan(
                rawInfo = "demo-detail",
                lang = "0",
                legs = listOf(
                    P2pRouteLeg("CTB", "28X-DEMO-1", "28X", 1, 4, "O", "outbound"),
                    P2pRouteLeg("CTB", "86-DEMO-1", "86", 1, 4, "O", "outbound")
                )
            )
        )
    }

    private fun stop(
        name: String,
        sequence: Int,
        routeVariant: String,
        role: RouteDetailStopRole
    ): RouteDetailStop {
        return RouteDetailStop(
            rawName = name,
            displayName = name,
            stopId = "$routeVariant-$sequence",
            sequence = sequence,
            latitude = 22.28 + sequence / 10_000.0,
            longitude = 114.18 + sequence / 10_000.0,
            routeVariant = routeVariant,
            role = role
        )
    }
}
