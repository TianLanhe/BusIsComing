package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCardLayoutContractTest {
    private val itemXml = File("src/main/res/layout/item_bus_route.xml").readText()
    private val adapterKt = File("src/main/java/com/example/busiscoming/ui/main/BusRouteAdapter.kt").readText()

    @Test
    fun routeAndStopPreviewUseLeftColumnWithStableRightWaitBlock() {
        assertTrue(itemXml.contains("<FrameLayout"))
        assertFalse(itemXml.contains("android:layout_marginEnd=\"180dp\""))
        assertFalse(itemXml.contains("android:id=\"@+id/busRouteTextColumn\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busWaitArea\""))
        assertTrue(itemXml.contains("android:layout_width=\"176dp\""))
        assertTrue(itemXml.contains("android:layout_height=\"72dp\""))
        assertTrue(itemXml.contains("android:layout_gravity=\"end|top\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busNextArrivalText\""))
        assertTrue(itemXml.contains("android:includeFontPadding=\"false\""))
        assertTrue(itemXml.indexOf("android:id=\"@+id/busRouteNameText\"") < itemXml.indexOf("android:id=\"@+id/busStopPreviewText\""))
        assertTrue(itemXml.indexOf("android:id=\"@+id/busStopPreviewText\"") < itemXml.indexOf("android:id=\"@+id/busWaitArea\""))
    }

    @Test
    fun stopPreviewUsesSingleLineInsideFixedLeftColumn() {
        val stopStart = itemXml.indexOf("android:id=\"@+id/busStopPreviewText\"")
        assertTrue(stopStart >= 0)
        val stopBlock = itemXml.substring(stopStart, itemXml.indexOf("/>", stopStart))
        val regressionPreview = "興華邨豐興樓 → 海底隧道巴士轉乘站"

        assertTrue(regressionPreview.contains("海底隧道巴士轉乘站"))
        assertTrue(stopBlock.contains("android:maxLines=\"1\""))
        assertTrue(stopBlock.contains("android:ellipsize=\"end\""))
        assertTrue(stopBlock.contains("android:layout_marginEnd=\"176dp\""))
        assertFalse(adapterKt.contains("updateStopPreviewEndMargin"))
        assertFalse(adapterKt.contains("STOP_PREVIEW_WITH_NEXT_END_MARGIN_DP"))
        assertFalse(adapterKt.contains("STOP_PREVIEW_WITHOUT_NEXT_END_MARGIN_DP"))
    }

    @Test
    fun topSectionKeepsStableWaitBlockHeightAndWidth() {
        val topStart = itemXml.indexOf("<FrameLayout")
        assertTrue(topStart >= 0)
        val topBlock = itemXml.substring(topStart, itemXml.indexOf("</FrameLayout>", topStart))

        assertTrue(topBlock.contains("android:layout_height=\"72dp\""))
        assertTrue(topBlock.contains("android:id=\"@+id/busWaitArea\""))
        assertTrue(topBlock.contains("android:layout_width=\"176dp\""))
        assertTrue(topBlock.contains("android:layout_height=\"72dp\""))
        assertTrue(topBlock.contains("android:layout_gravity=\"end|top\""))
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
        assertTrue(adapterKt.contains("itemView.setOnClickListener { onRouteClick(route) }"))
        assertTrue(adapterKt.contains("waitArea.setOnClickListener { onEtaClick(route) }"))
        assertTrue(adapterKt.contains("monitorButton.setOnClickListener"))
        assertTrue(adapterKt.contains("if (canMonitor) onMonitorClick(route)"))
        assertTrue(adapterKt.contains("waitArea.isEnabled = true"))
        assertTrue(adapterKt.contains("LARGE_FONT_SCALE_THRESHOLD"))
    }
}
