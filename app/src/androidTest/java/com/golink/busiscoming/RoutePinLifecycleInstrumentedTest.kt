package com.golink.busiscoming

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.RouteConfigDbHelper
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RoutePinSessionState
import com.golink.busiscoming.data.model.TemporaryRoutePinSavedState
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.TemporaryRoutePinBundleCodec
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutePinLifecycleInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @Test
    fun bundleCodecStoresOnlyLightweightTemporaryPinFields() {
        val bundle = Bundle()
        val saved = listOf(TemporaryRoutePinSavedState(7L, "v1|route", 99L))

        TemporaryRoutePinBundleCodec.write(bundle, saved)

        assertEquals(saved, TemporaryRoutePinBundleCodec.read(bundle))
        assertEquals(
            setOf(
                "temporary_pin_journey_ids",
                "temporary_pin_fingerprints",
                "temporary_pin_tokens"
            ),
            bundle.keySet()
        )
    }

    @Test
    fun temporaryPinSurvivesTopLevelNavigationAndActivityRecreationButNotFreshLaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                session(activity).pinTemporary(7L, "v1|route", 99L)
                activity.findViewById<BottomNavigationView>(R.id.topLevelNav).selectedItemId =
                    R.id.navigation_search
                activity.findViewById<BottomNavigationView>(R.id.topLevelNav).selectedItemId =
                    R.id.navigation_frequent_routes
                assertEquals(PinLevel.TEMPORARY, session(activity).record(7L, "v1|route")?.level)
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                assertEquals(PinLevel.TEMPORARY, session(activity).record(7L, "v1|route")?.level)
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { freshScenario ->
            freshScenario.onActivity { activity ->
                assertNull(session(activity).record(7L, "v1|route"))
            }
        }
    }

    private fun session(activity: MainActivity): RoutePinSessionState {
        return activity.javaClass.getDeclaredField("routePinSessionState").run {
            isAccessible = true
            get(activity) as RoutePinSessionState
        }
    }
}
