package com.golink.busiscoming

import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.RouteFingerprintFormatter
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteFingerprintFormatterTest {
    @Test
    fun `fingerprint encodes ordered identity fields with version and lengths`() {
        val route = route(listOf(leg()))

        assertEquals(
            "v1|1|3:CTB3:1185:118-11:O8:outbound1:22:10",
            RouteFingerprintFormatter.create(route)
        )
    }

    @Test
    fun `language raw info and dynamic display fields do not change fingerprint`() {
        val original = route(
            legs = listOf(leg()),
            rawInfo = "tc-raw",
            lang = "0",
            price = 12.3,
            duration = 25,
            walking = 90
        )
        val updated = route(
            legs = listOf(leg()),
            rawInfo = "en-raw-changed",
            lang = "1",
            price = 18.7,
            duration = 40,
            walking = 450
        )

        assertEquals(
            RouteFingerprintFormatter.create(original),
            RouteFingerprintFormatter.create(updated)
        )
    }

    @Test
    fun `leg order and every strict identity field change fingerprint`() {
        val first = leg()
        val second = leg(
            company = "KMB",
            route = "106",
            routeVariant = "106-2",
            bound = "I",
            directionPath = "inbound",
            boardingSeq = 4,
            alightingSeq = 18
        )
        val base = route(listOf(first, second))
        val mutations = listOf(
            route(listOf(second, first)),
            route(listOf(first.copy(company = "KMB"), second)),
            route(listOf(first.copy(route = "118P"), second)),
            route(listOf(first.copy(routeVariant = "118-2"), second)),
            route(listOf(first.copy(bound = "I"), second)),
            route(listOf(first.copy(directionPath = "inbound"), second)),
            route(listOf(first.copy(boardingSeq = 3), second)),
            route(listOf(first.copy(alightingSeq = 11), second))
        )

        mutations.forEach { mutation ->
            assertNotEquals(
                RouteFingerprintFormatter.create(base),
                RouteFingerprintFormatter.create(mutation)
            )
        }
    }

    @Test
    fun `missing or invalid plan identity cannot create fingerprint`() {
        assertNull(RouteFingerprintFormatter.create(route(emptyList())))
        assertNull(RouteFingerprintFormatter.create(route(listOf(leg(company = "")))))
        assertNull(RouteFingerprintFormatter.create(route(listOf(leg(directionPath = null)))))
        assertNull(RouteFingerprintFormatter.create(route(listOf(leg(boardingSeq = 0)))))
        assertNull(RouteFingerprintFormatter.create(route(listOf(leg(alightingSeq = 1)))))
        assertNull(
            RouteFingerprintFormatter.create(
                BusRouteOption("118", listOf("118"), 1.0, 10, 5, 0, 100)
            )
        )
    }

    @Test
    fun `duplicate strict fingerprints make every conflicting result ineligible`() {
        val routes = listOf(
            route(listOf(leg()), resultId = "first"),
            route(listOf(leg()), resultId = "second"),
            route(listOf(leg(route = "118P", routeVariant = "118P-1")), resultId = "third")
        )

        val resolved = RouteFingerprintFormatter.resolve(routes)

        assertEquals(
            listOf(
                RouteFingerprintResolution.Duplicate(
                    "v1|1|3:CTB3:1185:118-11:O8:outbound1:22:10"
                ),
                RouteFingerprintResolution.Duplicate(
                    "v1|1|3:CTB3:1185:118-11:O8:outbound1:22:10"
                ),
                RouteFingerprintResolution.Eligible(
                    "v1|1|3:CTB4:118P6:118P-11:O8:outbound1:22:10"
                )
            ),
            resolved
        )
    }

    private fun route(
        legs: List<P2pRouteLeg>,
        rawInfo: String = "raw",
        lang: String = "0",
        price: Double = 12.3,
        duration: Int = 25,
        walking: Int = 90,
        resultId: String = "result"
    ): BusRouteOption {
        val plan = P2pRoutePlan(rawInfo = rawInfo, lang = lang, legs = legs)
        return BusRouteOption(
            routeName = legs.joinToString(" → ") { it.route },
            routeSegments = legs.map { it.route },
            priceHkd = price,
            durationMinutes = duration,
            arrivalMinutes = 5,
            transferCount = (legs.size - 1).coerceAtLeast(0),
            walkingDistanceMeters = walking,
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo = rawInfo,
                generalInfo = "general",
                listId = "list",
                lang = lang,
                plan = plan
            ),
            resultId = resultId
        )
    }

    private fun leg(
        company: String = "CTB",
        route: String = "118",
        routeVariant: String = "118-1",
        bound: String = "O",
        directionPath: String? = "outbound",
        boardingSeq: Int = 2,
        alightingSeq: Int = 10
    ) = P2pRouteLeg(
        company = company,
        routeVariant = routeVariant,
        route = route,
        boardingSeq = boardingSeq,
        alightingSeq = alightingSeq,
        bound = bound,
        directionPath = directionPath
    )
}
