package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteMapDirectionPlacementPolicy
import com.golink.busiscoming.ui.main.RouteMapDirectionStyle
import com.golink.busiscoming.ui.main.RouteMapScreenPoint
import com.golink.busiscoming.ui.main.RouteMapScreenRect
import com.golink.busiscoming.ui.main.syncPooledItems
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMapDirectionPlacementTest {
    private val viewport = RouteMapScreenRect(0f, 0f, 360f, 640f)

    @Test
    fun approvedGlyphMetricsFitTheBusCoreAndKeepWalkingReadable() {
        val bus = RouteMapDirectionStyle.bus(1f)
        val walking = RouteMapDirectionStyle.walking(1f)
        assertEquals(36f, bus.spacing, 0f)
        assertEquals(5.5f, bus.glyphWidth, 0f)
        assertEquals(1.2f, bus.strokeWidth, 0f)
        assertTrue(bus.glyphHeight + bus.strokeWidth <= 7f)
        assertEquals(14f, walking.spacing, 0f)
        assertEquals(9f, walking.glyphWidth, 0f)
        assertEquals(2.4f, walking.strokeWidth, 0f)
    }

    @Test
    fun offscreenGeometryDoesNotDiluteVisibleBusDensity() {
        val placements = RouteMapDirectionPlacementPolicy.place(
            points = listOf(point(-10_000f, 100f), point(10_000f, 100f)),
            viewport = viewport,
            style = RouteMapDirectionStyle.bus(density = 1f)
        )

        assertTrue(placements.size in 9..12)
        placements.zipWithNext().forEach { (first, second) ->
            assertEquals(36f, second.point.x - first.point.x, 0.75f)
        }
    }

    @Test
    fun walkingUsesDenserFourteenDpSpacingAndKeepsPathsIndependent() {
        val first = RouteMapDirectionPlacementPolicy.place(
            points = listOf(point(20f, 80f), point(160f, 80f)),
            viewport = viewport,
            style = RouteMapDirectionStyle.walking(density = 1f)
        )
        val second = RouteMapDirectionPlacementPolicy.place(
            points = listOf(point(210f, 80f), point(350f, 80f)),
            viewport = viewport,
            style = RouteMapDirectionStyle.walking(density = 1f)
        )

        assertEquals(10, first.size)
        assertEquals(10, second.size)
        assertTrue(first.all { it.point.x < 180f })
        assertTrue(second.all { it.point.x > 180f })
    }

    @Test
    fun sharpCornerHasNoGlyphWhoseSafetyWindowCrossesTheCorner() {
        val style = RouteMapDirectionStyle.bus(density = 1f)
        val placements = RouteMapDirectionPlacementPolicy.place(
            points = listOf(point(40f, 240f), point(180f, 240f), point(180f, 100f)),
            viewport = viewport,
            style = style
        )

        val unsafeRadius = style.glyphWidth / 2f + style.strokeWidth
        assertTrue(placements.none { placement ->
            abs(placement.point.x - 180f) < unsafeRadius &&
                abs(placement.point.y - 240f) < unsafeRadius
        })
        assertTrue(placements.any { abs(it.rotation - 90f) < 1f })
        assertTrue(placements.any { abs(it.rotation) < 1f })
    }

    @Test
    fun reversingPointOrderReversesEveryStraightLineDirection() {
        val forward = RouteMapDirectionPlacementPolicy.place(
            points = listOf(point(30f, 300f), point(330f, 300f)),
            viewport = viewport,
            style = RouteMapDirectionStyle.bus(1f)
        )
        val reverse = RouteMapDirectionPlacementPolicy.place(
            points = listOf(point(330f, 300f), point(30f, 300f)),
            viewport = viewport,
            style = RouteMapDirectionStyle.bus(1f)
        )

        assertEquals(forward.size, reverse.size)
        assertTrue(forward.all { abs(it.rotation - 90f) < 1f })
        assertTrue(reverse.all { abs(it.rotation - 270f) < 1f })
    }

    @Test
    fun abnormalGeometryIsCappedWithoutChangingNormalSpacing() {
        val placements = RouteMapDirectionPlacementPolicy.place(
            points = listOf(point(-100f, 50f), point(10_000f, 50f)),
            viewport = RouteMapScreenRect(0f, 0f, 10_000f, 100f),
            style = RouteMapDirectionStyle.bus(1f),
            maxPlacements = 80
        )

        assertEquals(80, placements.size)
        placements.zipWithNext().forEach { (first, second) ->
            assertEquals(36f, second.point.x - first.point.x, 0.75f)
        }
    }

    @Test
    fun poolReusesExistingItemsAndOnlyAddsOrRemovesTheDifference() {
        val created = mutableListOf<Int>()
        val updated = mutableListOf<Pair<String, Int>>()
        val removed = mutableListOf<String>()
        val existing = mutableListOf("a", "b", "c")

        syncPooledItems(
            existing = existing,
            desired = listOf(1, 2, 3, 4),
            create = { value -> "new-$value".also { created += value } },
            update = { item, value -> updated += item to value },
            remove = { removed += it }
        )

        assertEquals(listOf(4), created)
        assertEquals(listOf("a" to 1, "b" to 2, "c" to 3, "new-4" to 4), updated)
        assertTrue(removed.isEmpty())

        syncPooledItems(
            existing = existing,
            desired = listOf(8),
            create = { error("不應新增") },
            update = { _, _ -> },
            remove = { removed += it }
        )
        assertEquals(setOf("b", "c", "new-4"), removed.toSet())
        assertEquals(listOf("a"), existing)
    }

    private fun point(x: Float, y: Float) = RouteMapScreenPoint(x, y)
}
