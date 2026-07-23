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

    @Test
    fun `journeys and routes use official outline and fill state pairs`() {
        assertSelector(
            name = "ic_nav_frequent_routes_state",
            checked = "ic_nav_journeys_filled",
            unchecked = "ic_nav_journeys_outline"
        )
        assertSelector(
            name = "ic_nav_search_state",
            checked = "ic_nav_routes_filled",
            unchecked = "ic_nav_routes_outline"
        )

        assertVector("ic_nav_journeys_outline", BOOKMARKS_OUTLINE)
        assertVector("ic_nav_journeys_filled", BOOKMARKS_FILLED)
        assertVector("ic_nav_routes_outline", ROUTE_OUTLINE)
        assertVector("ic_nav_routes_filled", ROUTE_FILLED)

        assertFalse(File("src/main/res/drawable/ic_nav_frequent_routes.xml").exists())
        assertFalse(File("src/main/res/drawable/ic_nav_search.xml").exists())
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

    private fun assertSelector(name: String, checked: String, unchecked: String) {
        val selector = File("src/main/res/drawable/$name.xml").readText()
        val checkedItem = selector.substringAfter("@drawable/$checked").substringBefore("/>")
        assertTrue(checkedItem.contains("android:state_checked=\"true\""))
        assertTrue(selector.contains("<item android:drawable=\"@drawable/$unchecked\" />"))
    }

    private fun assertVector(name: String, pathData: String) {
        val vector = File("src/main/res/drawable/$name.xml").readText()
        assertTrue(vector.contains("android:width=\"24dp\""))
        assertTrue(vector.contains("android:height=\"24dp\""))
        assertTrue(vector.contains("android:viewportWidth=\"960\""))
        assertTrue(vector.contains("android:viewportHeight=\"960\""))
        assertTrue(vector.contains("android:translateY=\"960\""))
        assertTrue(vector.contains("android:pathData=\"$pathData\""))
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

    private companion object {
        const val BOOKMARKS_OUTLINE =
            "M120-40v-640q0-33 23.5-56.5T200-760h400q33 0 56.5 23.5T680-680v640L400-160 120-40Zm80-122 200-86 200 86v-518H200v518Zm560 2v-680H240v-80h520q33 0 56.5 23.5T840-840v680h-80ZM200-680h400-400Z"
        const val BOOKMARKS_FILLED =
            "M120-40v-640q0-33 23.5-56.5T200-760h400q33 0 56.5 23.5T680-680v640L400-160 120-40Zm640-120v-680H240v-80h520q33 0 56.5 23.5T840-840v680h-80Z"
        const val ROUTE_OUTLINE =
            "M360-120q-66 0-113-47t-47-113v-327q-35-13-57.5-43.5T120-720q0-50 35-85t85-35q50 0 85 35t35 85q0 39-22.5 69.5T280-607v327q0 33 23.5 56.5T360-200q33 0 56.5-23.5T440-280v-400q0-66 47-113t113-47q66 0 113 47t47 113v327q35 13 57.5 43.5T840-240q0 50-35 85t-85 35q-50 0-85-35t-35-85q0-39 22.5-70t57.5-43v-327q0-33-23.5-56.5T600-760q-33 0-56.5 23.5T520-680v400q0 66-47 113t-113 47ZM240-680q17 0 28.5-11.5T280-720q0-17-11.5-28.5T240-760q-17 0-28.5 11.5T200-720q0 17 11.5 28.5T240-680Zm480 480q17 0 28.5-11.5T760-240q0-17-11.5-28.5T720-280q-17 0-28.5 11.5T680-240q0 17 11.5 28.5T720-200ZM240-720Zm480 480Z"
        const val ROUTE_FILLED =
            "M360-120q-66 0-113-47t-47-113v-327q-35-13-57.5-43.5T120-720q0-50 35-85t85-35q50 0 85 35t35 85q0 39-22.5 69.5T280-607v327q0 33 23.5 56.5T360-200q33 0 56.5-23.5T440-280v-400q0-66 47-113t113-47q66 0 113 47t47 113v327q35 13 57.5 43.5T840-240q0 50-35 85t-85 35q-50 0-85-35t-35-85q0-39 22.5-70t57.5-43v-327q0-33-23.5-56.5T600-760q-33 0-56.5 23.5T520-680v400q0 66-47 113t-113 47Z"
    }
}
