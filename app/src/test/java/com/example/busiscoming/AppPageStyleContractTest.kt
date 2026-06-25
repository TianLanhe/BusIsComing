package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPageStyleContractTest {
    private val mainLayoutXml = File("src/main/res/layout/activity_main.xml").readText()
    private val manageLayoutXml = File("src/main/res/layout/activity_route_manage.xml").readText()
    private val editLayoutXml = File("src/main/res/layout/activity_route_edit.xml").readText()
    private val backgroundXml = File("src/main/res/drawable/app_page_background.xml").readText()
    private val editFormBackgroundXml =
        File("src/main/res/drawable/route_edit_form_background.xml").readText()

    @Test
    fun primaryPagesShareSubtleGradientRootBackground() {
        assertTrue(mainLayoutXml.contains("android:background=\"@drawable/app_page_background\""))
        assertTrue(manageLayoutXml.contains("android:background=\"@drawable/app_page_background\""))
        assertTrue(editLayoutXml.contains("android:background=\"@drawable/app_page_background\""))
        assertTrue(backgroundXml.contains("<gradient"))
        assertTrue(backgroundXml.contains("@color/bus_page_gradient_start"))
        assertTrue(backgroundXml.contains("@color/bus_page_gradient_end"))
    }

    @Test
    fun editPageUsesGradientFunctionalAreaAndKeepsInputsAvailable() {
        assertTrue(manageLayoutXml.contains("android:id=\"@+id/routeConfigList\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/routeEditFormCard\""))
        assertTrue(editLayoutXml.contains("android:gravity=\"center_vertical\""))
        assertTrue(editLayoutXml.contains("android:background=\"@drawable/route_edit_form_background\""))
        assertTrue(editFormBackgroundXml.contains("<gradient"))
        assertTrue(editFormBackgroundXml.contains("@color/bus_form_gradient_start"))
        assertTrue(editFormBackgroundXml.contains("@color/bus_form_gradient_end"))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/originCandidateList\""))
        assertTrue(editLayoutXml.contains("android:id=\"@+id/destinationCandidateList\""))
    }
}
