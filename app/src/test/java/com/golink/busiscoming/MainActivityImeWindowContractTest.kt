package com.golink.busiscoming

import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

class MainActivityImeWindowContractTest {
    private val document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    @Test
    fun `combined soft input flags still identify adjustNothing owner`() {
        val fixture = parseXml(
            """
            <manifest xmlns:android="$ANDROID_NAMESPACE">
                <application>
                    <activity
                        android:name=".CombinedActivity"
                        android:windowSoftInputMode=" stateHidden | adjustNothing " />
                    <activity
                        android:name=".ResizeActivity"
                        android:windowSoftInputMode="stateHidden|adjustResize" />
                </application>
            </manifest>
            """.trimIndent()
        )

        assertEquals(listOf(".CombinedActivity"), adjustNothingOwners(fixture))
    }

    @Test
    fun `only the main host opts out of IME resize`() {
        val activities = activities(document)
        val softInputModeTokensByActivity = activities.associate { activity ->
            activity.androidAttribute("name") to activity.softInputModeTokens()
        }

        assertEquals(
            setOf("adjustNothing"),
            softInputModeTokensByActivity[".ui.main.MainActivity"]
        )
        assertEquals(
            setOf("adjustResize"),
            softInputModeTokensByActivity[".ui.edit.RouteEditActivity"]
        )
        assertEquals(listOf(".ui.main.MainActivity"), adjustNothingOwners(document))
        assertEquals(
            emptySet<String>(),
            (document.getElementsByTagName("application").item(0) as Element)
                .softInputModeTokens()
        )
    }

    private fun adjustNothingOwners(document: Document): List<String> =
        activities(document)
            .filter { "adjustNothing" in it.softInputModeTokens() }
            .map { it.androidAttribute("name") }

    private fun activities(document: Document): List<Element> =
        document.getElementsByTagName("activity").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
        }

    private fun parseXml(source: String): Document =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(
            ByteArrayInputStream(source.toByteArray())
        )

    private fun Element.softInputModeTokens(): Set<String> =
        androidAttribute("windowSoftInputMode")
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
