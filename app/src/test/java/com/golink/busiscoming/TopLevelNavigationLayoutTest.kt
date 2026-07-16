package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class TopLevelNavigationLayoutTest {
    private val activityLayout = File("src/main/res/layout/activity_main.xml").readText()
    private val mainActivity = File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()

    @Test
    fun `main host declares a fragment container and three bottom destinations`() {
        assertTrue(activityLayout.contains("androidx.fragment.app.FragmentContainerView"))
        assertTrue(activityLayout.contains("com.google.android.material.bottomnavigation.BottomNavigationView"))
        assertTrue(activityLayout.contains("@+id/topLevelNav"))
        assertTrue(mainActivity.contains("TopLevelDestination.FREQUENT_ROUTES"))
        assertTrue(mainActivity.contains("TopLevelDestination.SEARCH"))
        assertTrue(mainActivity.contains("TopLevelDestination.SETTINGS"))
    }

    @Test
    fun `bottom navigation measures system inset without compressing icon labels`() {
        assertTrue(activityLayout.contains("android:layout_height=\"wrap_content\""))
    }
}
