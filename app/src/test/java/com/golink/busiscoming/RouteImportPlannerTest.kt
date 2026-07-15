package com.golink.busiscoming

import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.transfer.RouteImportPlanner
import com.golink.busiscoming.data.transfer.TransferRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteImportPlannerTest {
    private val origin = Place("柴灣站", 22.2642, 114.2371)
    private val destination = Place("中環碼頭", 22.2878, 114.1582)

    @Test
    fun planReportsMergeAndReplaceImpactUsingExactDuplicateRule() {
        val existing = listOf(routeConfig(1, "上班", origin, destination))
        val incoming = listOf(
            TransferRoute(" 上班 ", origin, destination),
            TransferRoute("假日", origin, destination),
            TransferRoute("上班", origin, Place("灣仔站", 22.277, 114.173))
        )

        val plan = RouteImportPlanner.plan(incoming, inFileDuplicateCount = 2, existing = existing)

        assertEquals(3, plan.uniqueRouteCount)
        assertEquals(2, plan.inFileDuplicateCount)
        assertEquals(2, plan.mergeAddCount)
        assertEquals(1, plan.mergeSkipCount)
        assertEquals(1, plan.replaceDeleteCount)
        assertEquals(3, plan.replaceImportCount)
    }

    @Test
    fun allExistingDuplicatesIsStillAValidZeroAdditionPlan() {
        val existing = listOf(routeConfig(1, "上班", origin, destination))

        val plan = RouteImportPlanner.plan(
            incoming = listOf(TransferRoute("上班", origin, destination)),
            inFileDuplicateCount = 0,
            existing = existing
        )

        assertEquals(0, plan.mergeAddCount)
        assertEquals(1, plan.mergeSkipCount)
    }

    private fun routeConfig(id: Long, name: String, origin: Place, destination: Place) = RouteConfig(
        id = id,
        name = name,
        origin = origin,
        destination = destination,
        usageCount = 8,
        lastUsedAt = 1_725_000_000_000
    )
}
