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
        assertTrue(itemXml.contains("android:id=\"@+id/busRouteTextColumn\""))
        assertTrue(itemXml.contains("android:layout_marginEnd=\"142dp\""))
        assertTrue(itemXml.contains("android:id=\"@+id/busWaitArea\""))
        assertTrue(itemXml.contains("android:layout_width=\"138dp\""))
        assertTrue(itemXml.contains("android:layout_height=\"60dp\""))
        assertTrue(itemXml.contains("android:layout_gravity=\"end|center_vertical\""))
        assertTrue(itemXml.indexOf("android:id=\"@+id/busRouteNameText\"") < itemXml.indexOf("android:id=\"@+id/busStopPreviewText\""))
        assertTrue(itemXml.indexOf("android:id=\"@+id/busStopPreviewText\"") < itemXml.indexOf("android:id=\"@+id/busWaitArea\""))
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
    }
}
