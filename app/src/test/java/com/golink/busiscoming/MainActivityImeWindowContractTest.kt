package com.golink.busiscoming

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

class MainActivityImeWindowContractTest {
    private val document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    @Test
    fun `only the main host opts out of IME resize`() {
        val activities = document.getElementsByTagName("activity")
            .let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }
            }
        val softInputModesByActivity = activities.associate { activity ->
            activity.androidAttribute("name") to activity.androidAttribute("windowSoftInputMode")
        }

        assertEquals("adjustNothing", softInputModesByActivity[".ui.main.MainActivity"])
        assertEquals("adjustResize", softInputModesByActivity[".ui.edit.RouteEditActivity"])
        assertEquals(
            listOf(".ui.main.MainActivity"),
            activities
                .filter { it.androidAttribute("windowSoftInputMode") == "adjustNothing" }
                .map { it.androidAttribute("name") }
        )
        assertEquals(
            "",
            (document.getElementsByTagName("application").item(0) as Element)
                .androidAttribute("windowSoftInputMode")
        )
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
