package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCardIconContractTest {
    @Test
    fun walkingIconKeepsApprovedFourFilledPathSilhouette() {
        val vector = File("src/main/res/drawable/ic_walking_person.xml").readText()

        assertEquals(4, Regex("<path\\b").findAll(vector).count())
        assertTrue(vector.contains("android:viewportWidth=\"100\""))
        assertTrue(vector.contains("android:viewportHeight=\"100\""))
        assertFalse(vector.contains("bitmap"))
    }

    @Test
    fun alarmAndWalkingIconsAreEighteenDpVectors() {
        listOf("ic_alarm_clock.xml", "ic_walking_person.xml").forEach { name ->
            val vector = File("src/main/res/drawable/$name").readText()
            assertTrue(vector.contains("android:width=\"18dp\""))
            assertTrue(vector.contains("android:height=\"18dp\""))
        }
    }
}
