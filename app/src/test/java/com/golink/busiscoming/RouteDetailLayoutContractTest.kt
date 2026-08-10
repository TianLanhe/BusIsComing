package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailLayoutContractTest {
    private val adapterSource = File(
        "src/main/java/com/golink/busiscoming/ui/main/RouteDetailAdapter.kt"
    ).readText()
    private val activitySource = File(
        "src/main/java/com/golink/busiscoming/ui/main/RouteDetailActivity.kt"
    ).readText()
    private val layoutSource = File("src/main/res/layout/activity_route_detail.xml").readText()
    private val themeSource = File("src/main/res/values/themes.xml").readText()
    private val overviewIconSource = File("src/main/res/drawable/ic_route_overview.xml").readText()
    private val lucideLicenseSource = File("src/main/res/raw/lucide_license.txt").readText()
    private val rendererSource = File(
        "src/main/java/com/golink/busiscoming/ui/main/GoogleRouteMapRenderer.kt"
    ).readText()
    private val dayColorsSource = File("src/main/res/values/colors.xml").readText()
    private val nightColorsSource = File("src/main/res/values-night/colors.xml").readText()

    @Test
    fun enlargedSummaryStillDoesNotUseVisibleFortyEightDpSegmentRows() {
        assertTrue(adapterSource.contains("SummarySegmentTouchDelegate"))
        assertTrue(adapterSource.contains("ViewGroup.LayoutParams.WRAP_CONTENT,\n                    dp(30)"))
        assertFalse(
            adapterSource.contains(
                "ViewGroup.LayoutParams.WRAP_CONTENT,\n                    dp(48)"
            )
        )
    }

    @Test
    fun fullScreenSafeInsetIsAppliedToTheSheetContentContainer() {
        assertTrue(layoutSource.contains("android:id=\"@+id/routeDetailSheetContent\""))
        assertTrue(activitySource.contains("sheetContent.setPadding"))
        assertFalse(activitySource.contains("sheet.setPadding(0, if (full) statusBarInset"))
    }

    @Test
    fun revisedHandleAndSummaryUseCompactVisibleSizesWithExpandedTouchTargets() {
        assertTrue(layoutSource.contains("android:id=\"@+id/routeDetailSheetHandle\""))
        assertTrue(layoutSource.contains("android:layout_height=\"28dp\""))
        assertTrue(activitySource.contains("installSheetHandleTouchDelegate"))
        assertTrue(adapterSource.contains("dp(30)"))
        assertTrue(adapterSource.contains("dp(24)"))
        assertTrue(adapterSource.contains("17f, true"))
        assertTrue(adapterSource.contains("12f, false"))
        assertTrue(adapterSource.contains("text(timing, 21f"))
        assertTrue(adapterSource.contains("text(meta, 13f"))
    }

    @Test
    fun csdiAttributionHasCompactVisibleSurfaceAndSeparateTouchHeight() {
        val block = layoutSource.substringAfter("android:id=\"@+id/routeDetailCsdiAttribution\"")
            .substringBefore("android:id=\"@+id/routeDetailSheet\"")
        assertTrue(block.contains("android:layout_width=\"116dp\""))
        assertTrue(block.contains("android:layout_height=\"48dp\""))
        assertTrue(block.contains("android:id=\"@+id/routeDetailCsdiAttributionSurface\""))
        assertTrue(block.contains("android:layout_height=\"29dp\""))
        assertTrue(block.contains("android:layout_width=\"15dp\""))
        assertTrue(block.contains("app:strokeWidth=\"0dp\""))
        assertTrue(block.contains("app:cardElevation=\"0dp\""))
        assertTrue(block.contains("MaxFontScaleTextView"))
        assertTrue(activitySource.contains("csdiAttributionSurface.getLocationOnScreen"))
        assertTrue(activitySource.contains("csdiAttribution.translationY"))
    }

    @Test
    fun slideHotPathDoesNotCommitLayoutOrRebuildOverlays() {
        val slideBlock = activitySource.substringAfter("private fun onMapTransitionSlide")
            .substringBefore("private fun nextUpwardDetentVisibleHeight")
        assertTrue(slideBlock.contains("translationY"))
        assertTrue(slideBlock.contains("ApplyCandidatePadding"))
        assertFalse(slideBlock.contains("layoutParams"))
        assertFalse(slideBlock.contains("commitStableMapLayout"))
        assertFalse(slideBlock.contains("relayout"))
    }

    @Test
    fun mapControlsShareCenteredFortyEightDpButtonStyleWithTwentyFourDpIcons() {
        val styleName = "Widget.BusIsComing.RouteDetailMapControl"
        val style = themeSource.substringAfter("<style name=\"$styleName\"")
            .substringBefore("</style>")
        assertTrue(style.contains("android:layout_width\">48dp"))
        assertTrue(style.contains("android:layout_height\">48dp"))
        assertTrue(style.contains("android:gravity\">center"))
        assertTrue(style.contains("android:padding\">0dp"))
        assertTrue(style.contains("name=\"iconSize\">24dp"))

        listOf(
            "routeDetailFloatingBack",
            "routeDetailLocation",
            "routeDetailOverview"
        ).forEach { id ->
            val button = layoutSource.substringAfter("android:id=\"@+id/$id\"")
                .substringBefore("/>")
            assertTrue(button.contains("style=\"@style/$styleName\""))
        }
    }

    @Test
    fun overviewUsesLucideRouteInsteadOfScanFrame() {
        assertTrue(lucideLicenseSource.lineSequence().first() == "Lucide icons")
        assertTrue(overviewIconSource.contains("M6,16a3,3 0,1 0,0 6"))
        assertTrue(overviewIconSource.contains("M18,2a3,3 0,1 0,0 6"))
        assertFalse(overviewIconSource.contains("M4,4h5v2H6v3H4z"))
    }

    @Test
    fun alightingMarkerUsesOpaqueRouteFillContrastOutlineAndWhiteGlyph() {
        val block = rendererSource.substringAfter("RouteMapMarkerRole.ALIGHTING -> {")
            .substringBefore("RouteMapMarkerRole.TRANSFER ->")
        assertTrue(block.contains("canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, fill)"))
        assertTrue(block.contains("canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, outline)"))
        assertTrue(block.contains("R.drawable.ic_route_map_log_out"))
        assertTrue(block.contains("palette.markerOutlineColor"))
        assertFalse(block.contains("style = Paint.Style.STROKE"))

        listOf(dayColorsSource, nightColorsSource).forEach { colors ->
            (0..3).forEach { index ->
                assertTrue(colorValue(colors, "route_leg_$index").startsWith("#FF"))
            }
            assertTrue(colorValue(colors, "route_map_marker_outline").startsWith("#FF"))
        }
    }

    private fun colorValue(xml: String, name: String): String =
        xml.substringAfter("<color name=\"$name\">").substringBefore("</color>")
}
