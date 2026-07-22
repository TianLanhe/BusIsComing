package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationDestinationContractTest {
    private val menu = File("src/main/res/menu/top_level_navigation.xml").readText()
    private val traditional = values(File("src/main/res/values"))
    private val simplified = values(File("src/main/res/values-b+zh+Hans"))
    private val english = values(File("src/main/res/values-en"))

    @Test
    fun `bottom navigation names destinations and exposes full accessibility copy`() {
        assertMenuItem(
            id = "navigation_frequent_routes",
            title = "@string/nav_journeys",
            description = "@string/nav_journeys_content_description",
            icon = "@drawable/ic_nav_frequent_routes_state"
        )
        assertMenuItem(
            id = "navigation_search",
            title = "@string/nav_routes",
            description = "@string/nav_routes_content_description",
            icon = "@drawable/ic_nav_search_state"
        )
        assertMenuItem(
            id = "navigation_settings",
            title = "@string/settings",
            description = "@string/settings",
            icon = "@drawable/ic_nav_settings_state"
        )

        assertCopy(
            traditional,
            mapOf(
                "nav_journeys" to "行程",
                "nav_routes" to "路線",
                "nav_journeys_content_description" to "已儲存行程",
                "nav_routes_content_description" to "搜尋巴士路線"
            )
        )
        assertCopy(
            simplified,
            mapOf(
                "nav_journeys" to "行程",
                "nav_routes" to "路线",
                "nav_journeys_content_description" to "已保存行程",
                "nav_routes_content_description" to "搜索公交路线"
            )
        )
        assertCopy(
            english,
            mapOf(
                "nav_journeys" to "Journeys",
                "nav_routes" to "Routes",
                "nav_journeys_content_description" to "Saved journeys",
                "nav_routes_content_description" to "Find bus routes"
            )
        )

        listOf(traditional, simplified, english).forEach { localized ->
            assertFalse(localized.containsKey("nav_frequent"))
            assertFalse(localized.containsKey("nav_search"))
        }
    }

    private fun assertMenuItem(
        id: String,
        title: String,
        description: String,
        icon: String
    ) {
        val item = menu.substringAfter("android:id=\"@+id/$id\"").substringBefore("/>")
        assertTrue("Missing title $title for $id", item.contains("android:title=\"$title\""))
        assertTrue(
            "Missing content description $description for $id",
            item.contains("app:contentDescription=\"$description\"")
        )
        assertTrue("Missing icon $icon for $id", item.contains("android:icon=\"$icon\""))
    }

    private fun assertCopy(actual: Map<String, String>, expected: Map<String, String>) {
        expected.forEach { (key, value) ->
            assertEquals("Unexpected copy for $key", value, actual.getValue(key))
        }
    }

    private fun values(directory: File): Map<String, String> =
        Regex(
            "<string\\s+name=\"([^\"]+)\"(?:\\s+[^>]*)?>(.*?)</string>",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
            .findAll(
                directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "xml" }
                    .joinToString("\n") { it.readText() }
            )
            .associate { it.groupValues[1] to it.groupValues[2] }
}
