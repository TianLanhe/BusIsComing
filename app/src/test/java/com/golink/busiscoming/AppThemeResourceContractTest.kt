package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeResourceContractTest {
    private val dayColors = File("src/main/res/values/colors.xml")
    private val nightColors = File("src/main/res/values-night/colors.xml")
    private val dayTheme = File("src/main/res/values/themes.xml")
    private val nightTheme = File("src/main/res/values-night/themes.xml")
    private val dayV26Theme = File("src/main/res/values-v26/themes.xml")
    private val nightV26Theme = File("src/main/res/values-night-v26/themes.xml")
    private val dayV27Theme = File("src/main/res/values-v27/themes.xml")
    private val nightV27Theme = File("src/main/res/values-night-v27/themes.xml")

    @Test
    fun dayAndNightPalettesDefineTheSameSemanticTokens() {
        assertTrue("Missing values-night/colors.xml", nightColors.isFile)
        val requiredTokens = setOf(
            "bus_page_gradient_start",
            "bus_page_gradient_center",
            "bus_page_gradient_end",
            "bus_form_gradient_start",
            "bus_form_gradient_end",
            "bus_surface",
            "bus_surface_variant",
            "bus_card_surface",
            "bus_state_surface",
            "bus_chip_surface",
            "bus_chip_selected",
            "bus_wait_accent",
            "bus_wait_unavailable",
            "bus_text_primary",
            "bus_text_secondary",
            "bus_divider",
            "bus_outline_strong",
            "bus_danger",
            "bus_action_secondary",
            "bus_on_accent",
            "bus_on_secondary",
            "bus_on_danger"
        )

        val dayTokens = colorNames(dayColors)
        val nightTokens = colorNames(nightColors)
        assertTrue("Day palette is missing ${requiredTokens - dayTokens}", dayTokens.containsAll(requiredTokens))
        assertTrue("Night palette is missing ${requiredTokens - nightTokens}", nightTokens.containsAll(requiredTokens))
        assertEquals(requiredTokens, requiredTokens.intersect(nightTokens))
    }

    @Test
    fun materialThemesMapAllCoreSurfaceAndOnColors() {
        val requiredAttributes = setOf(
            "colorPrimary",
            "colorOnPrimary",
            "colorSecondary",
            "colorOnSecondary",
            "colorSurface",
            "colorOnSurface",
            "colorError",
            "colorOnError",
            "android:statusBarColor",
            "android:navigationBarColor"
        )

        val baseTheme = dayTheme.readText()
            .substringAfter("<style name=\"Theme.BusIsComing.Base\"")
            .substringBefore("</style>")
        requiredAttributes.forEach { attribute ->
            assertTrue("Base theme does not map $attribute", baseTheme.contains("name=\"$attribute\""))
        }

        listOf(dayTheme, nightTheme, dayV26Theme, nightV26Theme, dayV27Theme, nightV27Theme).forEach { file ->
            val appTheme = file.readText()
                .substringAfter("<style name=\"Theme.BusIsComing\"")
                .substringBefore("</style>")
            assertTrue(
                "${file.path} does not inherit the semantic base theme",
                appTheme.contains("parent=\"Theme.BusIsComing.Base\"")
            )
        }
    }

    @Test
    fun lightNavigationBarAttributeIsLimitedToApi27Resources() {
        listOf(dayTheme, nightTheme, dayV26Theme, nightV26Theme).forEach { file ->
            assertTrue(
                "${file.path} must not reference an API 27 attribute",
                !file.readText().contains("android:windowLightNavigationBar")
            )
        }
        assertTrue(dayV27Theme.readText().contains("android:windowLightNavigationBar\">true"))
        assertTrue(nightV27Theme.readText().contains("android:windowLightNavigationBar\">false"))
    }

    private fun colorNames(file: File): Set<String> =
        Regex("<color\\s+name=\"([^\"]+)\"")
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
}
