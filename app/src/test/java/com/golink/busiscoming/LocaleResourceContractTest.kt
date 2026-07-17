package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleResourceContractTest {
    private val traditional = File("src/main/res/values")
    private val simplified = File("src/main/res/values-b+zh+Hans")
    private val english = File("src/main/res/values-en")

    @Test
    fun allThreeLanguagesProvideTheSameTranslatableKeysAndPlaceholders() {
        assertTrue("Missing Simplified Chinese resources", simplified.isDirectory)
        assertTrue("Missing English resources", english.isDirectory)
        val base = values(traditional)
        val simplifiedValues = values(simplified)
        val englishValues = values(english)

        assertEquals(base.keys, simplifiedValues.keys)
        assertEquals(base.keys, englishValues.keys)
        base.keys.forEach { key ->
            assertEquals("Simplified placeholders differ for $key", placeholders(base.getValue(key)), placeholders(simplifiedValues.getValue(key)))
            assertEquals("English placeholders differ for $key", placeholders(base.getValue(key)), placeholders(englishValues.getValue(key)))
        }
    }

    @Test
    fun manifestDeclaresSupportedLocales() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val localeConfig = File("src/main/res/xml/locales_config.xml")
        assertTrue(manifest.contains("android:localeConfig=\"@xml/locales_config\""))
        assertTrue(localeConfig.isFile)
        val contents = localeConfig.readText()
        listOf("zh-Hant-HK", "zh-Hans-CN", "en").forEach { tag ->
            assertTrue("Missing locale $tag", contents.contains("android:name=\"$tag\""))
        }
    }

    private fun values(directory: File): Map<String, String> =
        Regex("<string\\s+name=\"([^\"]+)\"(?:\\s+translatable=\"(?:true|false)\")?>(.*?)</string>", setOf(RegexOption.DOT_MATCHES_ALL))
            .findAll(
                directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "xml" }
                    .joinToString("\n") { it.readText() }
            )
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun placeholders(value: String): List<String> =
        Regex("%\\d+\\$[a-zA-Z]").findAll(value).map { it.value }.toList()
}
