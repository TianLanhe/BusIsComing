package com.golink.busiscoming

import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import com.golink.busiscoming.data.repository.CitybusHttpResponse
import com.golink.busiscoming.data.repository.CitybusSessionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class CitybusSessionRegistryTest {
    @Test
    fun extractsOnlyPhpSessionIdFromCitybusSameOriginResponse() {
        val response = CitybusHttpResponse(
            body = "<html></html>",
            setCookieHeaders = listOf(
                "ETWEBID=tracking; Path=/",
                "PHPSESSID=citybus-session; Path=/; HttpOnly; SameSite=Lax",
                "FCCDCF=consent; Path=/",
                "unknown=value; Path=/"
            )
        )

        assertEquals(
            "citybus-session",
            response.phpSessionIdFor(URL("https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php"))
        )
        assertNull(response.phpSessionIdFor(URL("https://example.com/nwp3/ppsearch_p3.php")))
        assertNull(response.phpSessionIdFor(URL("https://mobile.citybus.com.hk:444/nwp3/ppsearch_p3.php")))
    }

    @Test
    fun rejectsMalformedOrAmbiguousPhpSessionCookies() {
        assertNull(
            CitybusHttpResponse("", listOf("PHPSESSID=; Path=/"))
                .phpSessionIdFor(URL("https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php"))
        )
        assertNull(
            CitybusHttpResponse("", listOf("PHPSESSID=first", "PHPSESSID=second"))
                .phpSessionIdFor(URL("https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php"))
        )
    }

    @Test
    fun registryUsesOpaqueReferencesAndExpiresRawSessionInMemory() {
        var now = 1_000L
        val registry = CitybusSessionRegistry(
            clock = { now },
            ttlMillis = 500L,
            referenceFactory = { "opaque-reference" }
        )
        val context = P2pRouteRecoveryContext(
            originLatitude = 22.29361,
            originLongitude = 114.20056,
            destinationLatitude = 22.28190,
            destinationLongitude = 114.15815,
            searchMode = "F"
        )

        val reference = registry.register("raw-php-session", "0", context)

        assertEquals("opaque-reference", reference)
        assertFalse(reference.contains("raw-php-session"))
        assertEquals("raw-php-session", registry.resolve(reference)?.phpSessionId)
        assertEquals("F", registry.resolve(reference)?.recoveryContext?.searchMode)

        now += 500L

        assertNull(registry.resolve(reference))
        assertEquals(0, registry.size())
    }

    @Test
    fun replacingSearchScopeImmediatelyRemovesOnlyTheSupersededSessions() {
        val registry = CitybusSessionRegistry(referenceFactory = { raw -> "ref-$raw" })
        val context = P2pRouteRecoveryContext(22.3, 114.2, 22.28, 114.16, "T")
        val oldReference = registry.register("old", "0", context, ownerScope = "old-search")
        val currentReference = registry.register("current", "0", context, ownerScope = "current-search")

        registry.invalidateScope("old-search")

        assertNull(registry.resolve(oldReference))
        assertEquals("current", registry.resolve(currentReference)?.phpSessionId)
        assertEquals(1, registry.size())
    }

    @Test
    fun planFingerprintUsesOnlyStableRouteChainAndStationSequences() {
        val plan = P2pRoutePlan(
            rawInfo = "session-shaped-raw-info",
            lang = "0",
            legs = listOf(
                P2pRouteLeg("CTB", "N8P-ISR-1", "N8P", 6, 15, "O", "outbound"),
                P2pRouteLeg("CTB", "N969-ISR-1", "N969", 10, 17, "O", "outbound")
            )
        )

        val fingerprint = plan.fingerprint()

        assertEquals(
            "N8P>N969|N8P-ISR-1:6:15>N969-ISR-1:10:17",
            fingerprint
        )
        assertFalse(fingerprint.contains(plan.rawInfo))
        assertTrue(fingerprint.contains("N8P-ISR-1"))
    }
}
