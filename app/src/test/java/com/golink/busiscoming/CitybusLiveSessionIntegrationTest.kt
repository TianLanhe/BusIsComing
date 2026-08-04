package com.golink.busiscoming

import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.localization.LanguageSnapshot
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.CitybusBusRouteRepository
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.CitybusSessionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class CitybusLiveSessionIntegrationTest {
    @Test
    fun liveSessionABReturnsCompleteSingleAndMultiLegWalkingInAllLanguages() {
        assumeTrue(
            System.getProperty(RUN_LIVE_PROPERTY) == "true" ||
                System.getenv(RUN_LIVE_ENVIRONMENT) == "true"
        )

        listOf(
            AppLanguage.TRADITIONAL_CHINESE,
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.ENGLISH
        ).forEachIndexed { index, language ->
            val snapshot = LanguageSnapshot.create(AppLanguageChoice.FOLLOW_SYSTEM, language, index.toLong())
            val registry = CitybusSessionRegistry()
            val routeRepository = CitybusBusRouteRepository(
                languageSnapshotProvider = { snapshot },
                clock = { LIVE_QUERY_TIME_MILLIS },
                sessionRegistry = registry,
                requestLogger = {}
            )
            val routes = routeRepository.searchRoutes(ORIGIN, DESTINATION)
            val single = routes.firstOrNull { it.routeSegments.size == 1 }
            val multi = routes.firstOrNull { it.routeSegments.size > 1 }
            assertNotNull("Live Citybus response did not include a single-leg candidate", single)
            assertNotNull("Live Citybus response did not include a multi-leg candidate", multi)

            assertSessionBound(single!!, registry)
            assertSessionBound(multi!!, registry)

            val noSessionDetail = CitybusRouteDetailRepository(sessionRegistry = registry).loadRouteDetail(
                single.copy(
                    routeDetailQuery = requireNotNull(single.routeDetailQuery).copy(
                        sessionRef = null,
                        recoveryContext = null
                    )
                )
            )
            assertFalse(noSessionDetail.hasCompleteWalkingDistance)

            val detailRepository = CitybusRouteDetailRepository(sessionRegistry = registry)
            listOf(single, multi).forEach { route ->
                val detail = detailRepository.loadRouteDetail(route)
                assertTrue("Matching Citybus session must return every walking segment", detail.hasCompleteWalkingDistance)
                assertNotNull(detail.originWalking?.distanceMeters)
                assertNotNull(detail.destinationWalking?.distanceMeters)
                assertEquals(route.routeSegments.size, detail.legs.size)
                assertEquals(
                    detail.completeWalkingDistanceMeters,
                    detail.originWalking?.distanceMeters!! +
                        detail.transfers.sumOf { it.walking?.distanceMeters ?: 0 } +
                    detail.destinationWalking?.distanceMeters!!
                )
            }

            registry.invalidate(requireNotNull(single.routeDetailQuery?.sessionRef))
            val recoveryRouteRepository = CitybusBusRouteRepository(
                languageSnapshotProvider = { snapshot },
                clock = { LIVE_QUERY_TIME_MILLIS },
                sessionRegistry = registry,
                requestLogger = {}
            )
            val recoveredDetail = CitybusRouteDetailRepository(
                sessionRegistry = registry,
                recoverySearcher = recoveryRouteRepository::searchRouteCandidatesForRecovery
            ).loadRouteDetail(single)
            assertTrue("An expired session must recover once by matching the original plan", recoveredDetail.hasCompleteWalkingDistance)
        }
    }

    private fun assertSessionBound(route: BusRouteOption, registry: CitybusSessionRegistry) {
        val query = requireNotNull(route.routeDetailQuery)
        assertNotNull(query.sessionRef)
        assertNotNull(query.recoveryContext)
        assertNotNull(registry.resolve(query.sessionRef))
    }

    private companion object {
        const val RUN_LIVE_PROPERTY = "runRealCitybusSession"
        const val RUN_LIVE_ENVIRONMENT = "RUN_REAL_CITYBUS_SESSION"
        const val LIVE_QUERY_TIME_MILLIS = 1_785_888_000_000L // 香港時間 2026-08-05 08:00
        val ORIGIN = Place("", 22.267079693838, 114.24208950984)
        val DESTINATION = Place("", 22.282043425996, 114.15760138031)
    }
}
