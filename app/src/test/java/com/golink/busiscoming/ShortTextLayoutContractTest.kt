package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortTextLayoutContractTest {
    private val commonHelper =
        File("src/main/java/com/golink/busiscoming/ui/common/ShortTextLayout.kt")
    private val layoutsDir = File("src/main/res/layout")
    private val stylesXml = File("src/main/res/values/themes.xml").readText()
    private val stylesV26File = File("src/main/res/values-v26/themes.xml")

    private val dynamicShortTextFiles = listOf(
        "src/main/java/com/golink/busiscoming/ui/main/TemporaryRouteBottomSheet.kt",
        "src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt",
        "src/main/java/com/golink/busiscoming/ui/main/RouteDetailBottomSheet.kt",
        "src/main/java/com/golink/busiscoming/ui/main/EtaArrivalsBottomSheet.kt",
        "src/main/java/com/golink/busiscoming/ui/main/MonitorSettingsBottomSheet.kt",
        "src/main/java/com/golink/busiscoming/ui/main/TemporaryRouteSaveDialog.kt"
    )

    @Test
    fun helperDisablesJustificationAndPreservesCallerAlignment() {
        assertTrue("Missing shared short text helper", commonHelper.isFile)
        val source = commonHelper.readText()

        assertTrue(source.contains("fun TextView.applyStableShortTextLayout"))
        assertTrue(source.contains("LineBreaker.JUSTIFICATION_MODE_NONE"))
        assertTrue(source.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.O"))
        assertTrue(source.contains("letterSpacing = 0f"))
        assertTrue(source.contains("gravity = textGravity"))
        assertTrue(source.contains("textAlignment = alignment"))
        assertFalse(source.contains("maxLines = 1"))
        assertFalse(source.contains("height ="))
    }

    @Test
    fun dynamicShortTextSurfacesUseSharedHelper() {
        dynamicShortTextFiles.forEach { path ->
            val source = File(path).readText()
            assertTrue("$path should import shared short text helper", source.contains("applyStableShortTextLayout"))
        }

        val placeInputController =
            File("src/main/java/com/golink/busiscoming/ui/common/PlaceInputController.kt").readText()
        assertFalse(
            "Candidate place rows are dynamic data and should not use short text helper",
            placeInputController.contains("applyStableShortTextLayout")
        )
    }

    @Test
    fun xmlShortTextControlsUseStableStyles() {
        assertStyleDefinesStableShortText("StableShortText")
        assertStyleDefinesStableShortText("StableShortText.Button")
        assertStyleDefinesStableShortText("StableShortText.Label")
        assertStyleDefinesStableShortText("StableShortText.Title")

        val expectedStylesByLayout = mapOf(
            "activity_main.xml" to listOf(
                "@style/StableShortText.Button",
                "@style/StableShortText.Label",
                "@style/StableShortText.Title"
            ),
            "activity_route_edit.xml" to listOf(
                "@style/StableShortText.Button",
                "@style/StableShortText.Title",
                "@style/StableShortText.Label"
            ),
            "activity_route_manage.xml" to listOf(
                "@style/StableShortText.Button",
                "@style/StableShortText.Title"
            ),
            "item_route_config.xml" to listOf("@style/StableShortText.Button"),
            "activity_settings.xml" to listOf(
                "@style/StableShortText.Button",
                "@style/StableShortText.Title",
                "@style/SettingsGroupLabel",
                "@style/SettingsRowText"
            ),
            "activity_about.xml" to listOf(
                "@style/StableShortText.Button",
                "@style/StableShortText.Title",
                "@style/StableShortText.Label"
            )
        )

        expectedStylesByLayout.forEach { (layout, styleRefs) ->
            val xml = File(layoutsDir, layout).readText()
            styleRefs.forEach { styleRef ->
                assertTrue("$layout should reference $styleRef", xml.contains(styleRef))
            }
        }
    }

    private fun assertStyleDefinesStableShortText(styleName: String) {
        val block = styleBlock(styleName)
        assertTrue(block.contains("<item name=\"android:letterSpacing\">0</item>"))
        assertTrue(
            block.contains("<item name=\"android:gravity\">") ||
                block.contains("<item name=\"android:textAlignment\">")
        )

        assertTrue("Missing API 26 style overrides", stylesV26File.isFile)
        val v26Block = styleBlock(stylesV26File.readText(), styleName)
        assertTrue(v26Block.contains("<item name=\"android:justificationMode\">none</item>"))
    }

    private fun styleBlock(styleName: String): String {
        return styleBlock(stylesXml, styleName)
    }

    private fun styleBlock(xml: String, styleName: String): String {
        val start = xml.indexOf("<style name=\"$styleName")
        assertTrue("Missing style $styleName", start >= 0)
        val end = xml.indexOf("</style>", start)
        assertTrue("Missing style end for $styleName", end >= 0)
        return xml.substring(start, end)
    }
}
