package com.golink.busiscoming

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node

class MonitorSettingsLayoutContractTest {
    private val layoutFile = File("src/main/res/layout/bottom_sheet_monitor_settings.xml")

    @Test
    fun settingsScrollsWhilePrimaryActionStaysOutsideScrollableContent() {
        assertTrue("Missing monitor settings layout", layoutFile.isFile)
        val root = documentRoot()
        val scroll = findById(root, "monitor_settings_scroll")
        val start = findById(root, "monitor_start_button")

        assertNotNull(scroll)
        assertNotNull(start)
        assertFalse(isDescendant(start!!, scroll!!))
        assertEquals("0dp", scroll.getAttribute("android:layout_height"))
        assertEquals("1", scroll.getAttribute("android:layout_weight"))
    }

    @Test
    fun walkingCardAndControlsUseSemanticStylesAndFortyEightDpTargets() {
        val root = documentRoot()
        val card = findById(root, "monitor_walking_card")!!
        assertEquals(
            "@style/Widget.BusIsComing.MonitorSettings.Card",
            card.getAttribute("style")
        )
        listOf(
            "monitor_decrease_button",
            "monitor_increase_button",
            "monitor_notification_settings_button",
            "monitor_start_button"
        ).forEach { id ->
            val view = findById(root, id)!!
            assertEquals("$id minHeight", "48dp", view.getAttribute("android:minHeight"))
            assertEquals("$id minWidth", "48dp", view.getAttribute("android:minWidth"))
        }

        val themes = File("src/main/res/values/themes.xml").readText()
        val cardStyle = themes.substringAfter(
            "<style name=\"Widget.BusIsComing.MonitorSettings.Card\""
        ).substringBefore("</style>")
        assertTrue(cardStyle.contains("@color/bus_surface_variant"))
        assertTrue(cardStyle.contains("@color/bus_divider"))
        assertTrue(cardStyle.contains("8dp"))
    }

    @Test
    fun titleAreaContainsNeitherRouteSubtitleNorExplanation() {
        assertTrue("Missing monitor settings layout", layoutFile.isFile)
        val layout = layoutFile.readText()
        assertFalse(layout.contains("monitor_explanation"))
        assertFalse(layout.contains("monitor_route_subtitle"))

        listOf(
            "src/main/res/values/strings_runtime.xml",
            "src/main/res/values-b+zh+Hans/strings_runtime.xml",
            "src/main/res/values-en/strings_runtime.xml"
        ).forEach { path ->
            assertFalse("$path still defines monitor_explanation", File(path).readText().contains(
                "name=\"monitor_explanation\""
            ))
        }
    }

    private fun documentRoot(): Element {
        assertTrue("Missing monitor settings layout", layoutFile.isFile)
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layoutFile)
            .documentElement
    }

    private fun findById(root: Element, id: String): Element? {
        if (root.getAttribute("android:id") == "@+id/$id" ||
            root.getAttribute("android:id") == "@id/$id"
        ) {
            return root
        }
        val children = root.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            findById(child, id)?.let { return it }
        }
        return null
    }

    private fun isDescendant(node: Node, ancestor: Node): Boolean {
        var parent = node.parentNode
        while (parent != null) {
            if (parent == ancestor) return true
            parent = parent.parentNode
        }
        return false
    }
}
