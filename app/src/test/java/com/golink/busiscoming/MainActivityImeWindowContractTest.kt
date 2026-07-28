package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityImeWindowContractTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `only the main host opts out of IME resize`() {
        val mainActivity = activityDeclaration(".ui.main.MainActivity")
        val routeEditActivity = activityDeclaration(".ui.edit.RouteEditActivity")

        assertTrue(mainActivity.contains("android:windowSoftInputMode=\"adjustNothing\""))
        assertTrue(routeEditActivity.contains("android:windowSoftInputMode=\"adjustResize\""))
        assertFalse(manifest.substringBefore("<application").contains("windowSoftInputMode"))
    }

    private fun activityDeclaration(name: String): String = manifest
        .substringAfter("android:name=\"$name\"")
        .substringBefore("</activity>")
}
