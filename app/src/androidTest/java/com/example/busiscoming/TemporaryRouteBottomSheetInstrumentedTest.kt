package com.example.busiscoming

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.hasFocus
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.busiscoming.data.location.CurrentPlaceSelectionResult
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.repository.PlaceSearchRepository
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.example.busiscoming.ui.main.MainActivity
import com.example.busiscoming.ui.main.TemporaryRouteBottomSheet
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.allOf

@RunWith(AndroidJUnit4::class)
class TemporaryRouteBottomSheetInstrumentedTest {
    @Test
    fun candidatesExpandSheetAndBackClosesCandidatesBeforeExistingBackFlow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val executor = Executors.newSingleThreadExecutor()
            lateinit var sheet: TemporaryRouteBottomSheet
            scenario.onActivity { activity ->
                sheet = TemporaryRouteBottomSheet(
                    context = activity,
                    routeConfigRepository = RouteConfigRepository(activity),
                    mainHandler = Handler(Looper.getMainLooper()),
                    searchExecutor = executor,
                    placeSearchRepository = object : PlaceSearchRepository {
                        override fun searchPlaces(keyword: String): List<Place> {
                            return (1..20).map { index ->
                                Place("$keyword 候選$index", 22.3, 114.2)
                            }
                        }
                    },
                    onQuery = { _, _ -> },
                    onSaved = {}
                )
                sheet.show()
            }

            onView(withId(R.id.temporaryOriginInput)).inRoot(isDialog()).perform(click(), typeText("a"))
            waitUntil {
                try {
                    onView(withId(R.id.temporaryOriginCandidateList))
                        .inRoot(isDialog())
                        .check(matches(isDisplayed()))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            onView(withId(com.google.android.material.R.id.design_bottom_sheet))
                .inRoot(isDialog())
                .check { view, _ ->
                val bottomSheet = view as FrameLayout
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, bottomSheet.layoutParams.height)
                assertEquals(
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.from(bottomSheet).state
                )
            }
            onView(withId(R.id.temporaryOriginInput)).inRoot(isDialog()).check { view, _ ->
                val insets = checkNotNull(ViewCompat.getRootWindowInsets(view))
                assertTrue(insets.isVisible(WindowInsetsCompat.Type.ime()))
            }
            saveScreenshot("temporary-inline-candidates")

            pressBack()
            onView(withId(R.id.temporaryOriginCandidateList)).inRoot(isDialog())
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.temporaryOriginInput)).inRoot(isDialog()).check(matches(isDisplayed()))

            scenario.onActivity {
                sheet.dispose()
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun selectingOneEndpointFocusesTheOtherAndBothSelectionsDoNotSubmit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val executor = Executors.newSingleThreadExecutor()
            val queryCalled = AtomicBoolean(false)
            lateinit var sheet: TemporaryRouteBottomSheet
            scenario.onActivity { activity ->
                sheet = TemporaryRouteBottomSheet(
                    context = activity,
                    routeConfigRepository = RouteConfigRepository(activity),
                    mainHandler = Handler(Looper.getMainLooper()),
                    searchExecutor = executor,
                    placeSearchRepository = object : PlaceSearchRepository {
                        override fun searchPlaces(keyword: String): List<Place> {
                            return (1..20).map { index ->
                                Place("$keyword 候選$index", 22.3, 114.2)
                            }
                        }
                    },
                    onQuery = { _, _ -> queryCalled.set(true) },
                    onSaved = {}
                )
                sheet.show()
            }

            onView(withId(R.id.temporaryOriginInput)).inRoot(isDialog()).perform(click(), typeText("a"))
            waitUntil {
                try {
                    onView(withId(R.id.temporaryOriginCandidateList))
                        .inRoot(isDialog())
                        .check(matches(isDisplayed()))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            onView(allOf(withText("a 候選1"), isDisplayed())).inRoot(isDialog()).perform(click())
            onView(withId(R.id.temporaryDestinationInput)).inRoot(isDialog()).check(matches(hasFocus()))

            onView(withId(R.id.temporaryDestinationInput)).inRoot(isDialog()).perform(typeText("b"))
            waitUntil {
                try {
                    onView(withId(R.id.temporaryDestinationCandidateList))
                        .inRoot(isDialog())
                        .check(matches(isDisplayed()))
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            onView(allOf(withText("b 候選1"), isDisplayed())).inRoot(isDialog()).perform(click())
            onView(withId(R.id.temporaryDestinationInput)).inRoot(isDialog()).check(matches(hasFocus()))
            onView(withId(com.google.android.material.R.id.design_bottom_sheet))
                .inRoot(isDialog())
                .check(matches(isDisplayed()))
            assertTrue(!queryCalled.get())

            scenario.onActivity {
                sheet.dispose()
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun prefilledTemporaryEditCanQueryWithoutAutoCurrentLocationOverride() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val executor = Executors.newSingleThreadExecutor()
            val origin = Place("中環碼頭", 22.2879, 114.1588)
            val destination = Place("太古城中心", 22.2867, 114.2166)
            val autoCurrentRequested = AtomicBoolean(false)
            val queriedOrigin = AtomicReference<Place>()
            val queriedDestination = AtomicReference<Place>()
            lateinit var sheet: TemporaryRouteBottomSheet
            scenario.onActivity { activity ->
                sheet = TemporaryRouteBottomSheet(
                    context = activity,
                    routeConfigRepository = RouteConfigRepository(activity),
                    mainHandler = Handler(Looper.getMainLooper()),
                    searchExecutor = executor,
                    placeSearchRepository = object : PlaceSearchRepository {
                        override fun searchPlaces(keyword: String): List<Place> = emptyList()
                    },
                    onCurrentPlaceRequested = { isAuto, callback ->
                        if (isAuto) autoCurrentRequested.set(true)
                        callback(CurrentPlaceSelectionResult.Failure)
                    },
                    onQuery = { queryOrigin, queryDestination ->
                        queriedOrigin.set(queryOrigin)
                        queriedDestination.set(queryDestination)
                    },
                    onSaved = {}
                )
                sheet.show(initialOrigin = origin, initialDestination = destination)
            }

            onView(withId(R.id.temporaryOriginInput)).inRoot(isDialog())
                .check(matches(withText("中環碼頭")))
            onView(withId(R.id.temporaryDestinationInput)).inRoot(isDialog())
                .check(matches(withText("太古城中心")))
            assertFalse(autoCurrentRequested.get())
            saveScreenshot("temporary-edit-prefilled")

            onView(withText("使用此路線查詢")).inRoot(isDialog()).perform(click())
            assertEquals(origin, queriedOrigin.get())
            assertEquals(destination, queriedDestination.get())

            scenario.onActivity {
                sheet.dispose()
            }
            executor.shutdownNow()
        }
    }

    private fun waitUntil(timeoutMillis: Long = 3_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for temporary candidates", condition())
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/BusIsComing-$name.png"
        )
        FileInputStream(command.fileDescriptor).use { input ->
            input.readBytes()
        }
    }
}
