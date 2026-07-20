package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCardLayoutContractTest {
    private val itemXml = File("src/main/res/layout/item_bus_route.xml").readText()
    private val adapterKt = File("src/main/java/com/golink/busiscoming/ui/main/BusRouteAdapter.kt").readText()
    private val binderKt = File("src/main/java/com/golink/busiscoming/ui/main/BusRouteCardBinder.kt").readText()

    @Test
    fun routeAndStopPreviewUseFlexibleWeightedColumnBesideWaitBlock() {
        assertFalse(itemXml.contains("<FrameLayout"))
        assertFalse(itemXml.contains("android:layout_marginEnd=\"180dp\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busRouteTextColumn\""))
        assertTrue(itemXml.contains("android:layout_weight=\"1\""))
        assertTrue(itemXml.contains("android:layout_marginEnd=\"8dp\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busWaitArea\""))
        assertTrue(itemXml.contains("android:minHeight=\"56dp\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busNextArrivalText\""))
        assertTrue(itemXml.contains("android:includeFontPadding=\"false\""))
        assertTrue(itemXml.indexOf("android:id=\"@+id/busRouteNameText\"") < itemXml.indexOf("android:id=\"@+id/busStopPreviewLayout\""))
        assertTrue(itemXml.indexOf("android:id=\"@+id/busStopPreviewLayout\"") < itemXml.indexOf("android:id=\"@+id/busWaitArea\""))
    }

    @Test
    fun stopPreviewUsesAdaptiveSingleLineNamesInsideFlexibleColumn() {
        val stopStart = itemXml.indexOf("android:id=\"@+id/busStopPreviewLayout\"")
        assertTrue(stopStart >= 0)
        val stopBlock = itemXml.substring(stopStart, itemXml.indexOf("</com.golink.busiscoming.ui.main.AdaptiveStopPreviewLayout>", stopStart))

        assertTrue(itemXml.contains("com.golink.busiscoming.ui.main.AdaptiveStopPreviewLayout"))
        assertTrue(stopBlock.contains("android:id=\"@+id/busStopOriginText\""))
        assertTrue(stopBlock.contains("android:id=\"@+id/busStopDirectionText\""))
        assertTrue(stopBlock.contains("android:id=\"@+id/busStopDestinationText\""))
        assertEquals(2, Regex("android:maxLines=\"1\"").findAll(stopBlock).count())
        assertEquals(2, Regex("android:ellipsize=\"end\"").findAll(stopBlock).count())
        val columnStart = itemXml.indexOf("android:id=\"@+id/busRouteTextColumn\"")
        assertTrue(columnStart >= 0)
        val columnBlock = itemXml.substring(columnStart, itemXml.indexOf("</LinearLayout>", columnStart))
        assertTrue(columnBlock.contains("android:layout_weight=\"1\""))
        assertFalse(adapterKt.contains("updateStopPreviewEndMargin"))
        assertFalse(adapterKt.contains("STOP_PREVIEW_WITH_NEXT_END_MARGIN_DP"))
        assertFalse(adapterKt.contains("STOP_PREVIEW_WITHOUT_NEXT_END_MARGIN_DP"))
        assertTrue(binderKt.contains("stopPreviewLayout.contentDescription = preview.displayText()"))
    }

    @Test
    fun topSectionUsesMinimumHeightAndContentDrivenWidth() {
        assertTrue(itemXml.contains("android:minHeight=\"56dp\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busWaitArea\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busEtaTextColumn\""))
        assertFalse(itemXml.contains("android:layout_width=\"160dp\""))
    }

    @Test
    fun monitorBellKeepsBorderlessStyleAndFortyEightDpTouchTarget() {
        val bellStart = itemXml.indexOf("android:id=\"@+id/busMonitorButton\"")
        assertTrue(bellStart >= 0)
        val bellBlock = itemXml.substring(bellStart, itemXml.indexOf("/>", bellStart))

        assertTrue(bellBlock.contains("android:layout_width=\"48dp\""))
        assertTrue(bellBlock.contains("android:layout_height=\"48dp\""))
        assertTrue(bellBlock.contains("android:background=\"?attr/selectableItemBackgroundBorderless\""))
        assertTrue(bellBlock.contains("android:padding=\"14dp\""))
        assertTrue(bellBlock.contains("app:tint=\"@color/bus_text_secondary\""))
        assertFalse(bellBlock.contains("shape"))
        assertFalse(bellBlock.contains("card"))
    }

    @Test
    fun routeEtaAndMonitorClicksRemainSeparate() {
        assertTrue(adapterKt.contains("BusRouteCardBinder(itemView)"))
        assertTrue(adapterKt.contains("routeClick = onRouteClick"))
        assertTrue(adapterKt.contains("etaClick = onEtaClick"))
        assertTrue(adapterKt.contains("monitorClick = onMonitorClick"))
        assertTrue(binderKt.contains("itemView.setOnClickListener"))
        assertTrue(binderKt.contains("actions.routeClick"))
        assertTrue(binderKt.contains("etaTextColumn.setOnClickListener"))
        assertFalse(binderKt.contains("waitArea.setOnClickListener"))
        assertTrue(binderKt.contains("monitorButton.setOnClickListener"))
        assertTrue(binderKt.contains("actions.monitorClick"))
        assertTrue(binderKt.contains("etaTextColumn.isEnabled = true"))
        assertTrue(binderKt.contains("LARGE_FONT_SCALE_THRESHOLD"))
    }

    @Test
    fun topRightPaddingUsesBellVisualPaddingWhileBottomKeepsContentInset() {
        assertTrue(itemXml.contains("android:paddingStart=\"14dp\""))
        assertTrue(itemXml.contains("android:paddingEnd=\"0dp\""))
        assertTrue(itemXml.contains("android:layout_marginEnd=\"14dp\""))

        val dividerStart = itemXml.indexOf("android:layout_height=\"1dp\"")
        assertTrue(dividerStart >= 0)
        val dividerBlock = itemXml.substring(dividerStart, itemXml.indexOf("/>", dividerStart))
        assertTrue(dividerBlock.contains("android:layout_marginEnd=\"14dp\""))
    }
}
