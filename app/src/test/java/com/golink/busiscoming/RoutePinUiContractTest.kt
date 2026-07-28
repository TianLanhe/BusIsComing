package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePinUiContractTest {
    private val card = File("src/main/res/layout/item_bus_route.xml").readText()
    private val divider = File("src/main/res/layout/item_unpinned_route_divider.xml").readText()
    private val binder =
        File("src/main/java/com/golink/busiscoming/ui/main/BusRouteCardBinder.kt").readText()

    @Test
    fun `persistent bookmark uses the agreed short shape without adding a card content row`() {
        val bookmarkStart = card.indexOf("android:id=\"@+id/busPersistentPinBookmark\"")
        val bookmark = card.substring(bookmarkStart, card.indexOf("/>", bookmarkStart))

        assertTrue(bookmark.contains("android:layout_width=\"10dp\""))
        assertTrue(bookmark.contains("android:layout_height=\"25dp\""))
        assertTrue(bookmark.contains("android:layout_marginStart=\"2dp\""))
        assertTrue(card.contains("android:paddingStart=\"14dp\""))
        assertTrue(bookmark.contains("android:importantForAccessibility=\"no\""))
        assertTrue(bookmark.contains("android:visibility=\"gone\""))
        assertTrue(card.contains("android:minHeight=\"86dp\""))
        assertFalse(card.contains("route_pin_state_temporary"))
        assertFalse(card.contains("route_pin_state_persistent"))
    }

    @Test
    fun `binder always resets recycled stroke bookmark state and accessibility actions`() {
        assertTrue(binder.contains("resetPinPresentation()"))
        assertTrue(binder.contains("bookmark.visibility = View.GONE"))
        assertTrue(binder.contains("card.strokeColor"))
        assertTrue(binder.contains("ViewCompat.removeAccessibilityAction"))
        assertTrue(binder.contains("ViewCompat.setStateDescription"))
    }

    @Test
    fun `divider is a normal nonsticky accessible list row`() {
        assertTrue(divider.contains("android:minHeight=\"48dp\""))
        assertTrue(divider.contains("@+id/unpinnedRouteDividerText"))
        assertFalse(divider.contains("sticky"))
        val adapter =
            File("src/main/java/com/golink/busiscoming/ui/main/BusRouteAdapter.kt").readText()
        assertTrue(adapter.contains("itemView.isClickable = false"))
        assertTrue(adapter.contains("itemView.contentDescription = label.text"))
    }

    @Test
    fun `pin resource keys and placeholders match across three locales`() {
        val files = listOf(
            File("src/main/res/values/strings.xml"),
            File("src/main/res/values-b+zh+Hans/strings.xml"),
            File("src/main/res/values-en/strings.xml")
        )
        val keys = listOf(
            "unpinned_routes_divider",
            "route_pin_temporary_success",
            "route_pin_persistent_success",
            "route_pin_cancelled",
            "route_pin_load_failed",
            "route_pin_action_temporary",
            "route_pin_action_persistent",
            "route_pin_action_cancel"
        )
        keys.forEach { key ->
            val values = files.map { file -> stringValue(file.readText(), key) }
            assertTrue(values.all { it.isNotBlank() })
            assertEquals(
                "Placeholder mismatch for $key",
                placeholders(values.first()),
                placeholders(values[1])
            )
            assertEquals(
                "Placeholder mismatch for $key",
                placeholders(values.first()),
                placeholders(values[2])
            )
        }
    }

    private fun stringValue(xml: String, key: String): String {
        return Regex("""<string name="$key">([\s\S]*?)</string>""")
            .find(xml)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    }

    private fun placeholders(value: String): List<String> =
        Regex("%\\d+\\$[a-zA-Z]").findAll(value).map { it.value }.toList()
}
