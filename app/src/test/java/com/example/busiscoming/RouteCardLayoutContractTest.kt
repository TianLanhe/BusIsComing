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
        assertTrue(itemXml.contains("android:id=\"@+id/busRouteTextColumn\""))
        assertTrue(itemXml.contains("android:layout_marginEnd=\"132dp\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busWaitArea\""))
        assertTrue(itemXml.contains("android:layout_width=\"160dp\""))
        assertTrue(itemXml.contains("android:layout_height=\"56dp\""))
        assertTrue(itemXml.contains("android:layout_gravity=\"end|center_vertical\""))
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
        val columnStart = itemXml.indexOf("android:id=\"@+id/busRouteTextColumn\"")
        assertTrue(columnStart >= 0)
        val columnBlock = itemXml.substring(columnStart, itemXml.indexOf("</LinearLayout>", columnStart))
        assertTrue(columnBlock.contains("android:layout_marginEnd=\"132dp\""))
        assertFalse(adapterKt.contains("updateStopPreviewEndMargin"))
        assertFalse(adapterKt.contains("STOP_PREVIEW_WITH_NEXT_END_MARGIN_DP"))
        assertFalse(adapterKt.contains("STOP_PREVIEW_WITHOUT_NEXT_END_MARGIN_DP"))
    }

    @Test
    fun topSectionKeepsStableWaitBlockHeightAndWidth() {
        val topStart = itemXml.indexOf("<FrameLayout")
        assertTrue(topStart >= 0)
        val topBlock = itemXml.substring(topStart, itemXml.indexOf("</FrameLayout>", topStart))

        assertTrue(topBlock.contains("android:layout_height=\"56dp\""))
        assertTrue(topBlock.contains("android:id=\"@+id/busWaitArea\""))
        assertTrue(topBlock.contains("android:layout_width=\"160dp\""))
        assertTrue(topBlock.contains("android:layout_height=\"56dp\""))
        assertTrue(topBlock.contains("android:layout_gravity=\"end|center_vertical\""))
        assertTrue(topBlock.contains("android:id=\"@+id/busEtaTextColumn\""))
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
        assertTrue(adapterKt.contains("etaTextColumn.setOnClickListener { onEtaClick(route) }"))
        assertFalse(adapterKt.contains("waitArea.setOnClickListener"))
        assertTrue(adapterKt.contains("monitorButton.setOnClickListener"))
        assertTrue(adapterKt.contains("if (canMonitor) onMonitorClick(route)"))
        assertTrue(adapterKt.contains("etaTextColumn.isEnabled = true"))
        assertTrue(adapterKt.contains("LARGE_FONT_SCALE_THRESHOLD"))
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
