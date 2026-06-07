package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRouteSelectionLayoutTest {
    private val layoutXml = File("src/main/res/layout/activity_main.xml").readText()

    @Test
    fun exposesAllEntryAndTemporaryQueryContextBar() {
        assertTrue(layoutXml.contains("android:id=\"@+id/routePickerButton\""))
        assertTrue(layoutXml.contains("android:text=\"全部\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQueryContextBar\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQueryContextPathText\""))
        assertTrue(layoutXml.contains("android:id=\"@+id/temporaryQuerySaveButton\""))
    }

    @Test
    fun doesNotShowStandaloneSortTitle() {
        assertFalse(layoutXml.contains("android:text=\"排序\""))
    }
}
