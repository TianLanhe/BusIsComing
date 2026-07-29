package com.golink.busiscoming

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.data.location.CurrentPlaceSelectionResult
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.PlaceAttribution
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.BusRouteQueryCallback
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.PlaceSearchRepository
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.RouteConfigSaveGateway
import com.golink.busiscoming.ui.main.SearchFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SearchDestinationInstrumentedTest {
    @get:Rule
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @After
    fun resetDependencies() {
        SearchFragment.resetTestDependencies()
        MainActivity.resetTestDependencies()
    }

    @Test
    fun routeSearchCaptionMovesFromInstructionToLocationFailureThenSelectedPlace() {
        val callback = AtomicReference<((CurrentPlaceSelectionResult) -> Unit)?>(null)
        installDependencies(ImmediateRouteRepository())
        SearchFragment.currentPlaceRequestOverride = { _, result ->
            callback.set(result)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitUntil { callback.get() != null }

            scenario.onActivity { activity ->
                assertEquals(
                    "起點 · 從清單選擇",
                    activity.findViewById<TextInputLayout>(R.id.placePairOriginLayout)
                        .hint
                        .toString()
                )
            }

            callback.get()?.invoke(CurrentPlaceSelectionResult.Failure)
            waitUntil {
                var hint = ""
                scenario.onActivity { activity ->
                    hint = activity.findViewById<TextInputLayout>(R.id.placePairOriginLayout)
                        .hint
                        .toString()
                }
                hint == "起點 · 定位失敗，請手動選擇"
            }

            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            scenario.onActivity { activity ->
                assertEquals(
                    "起點",
                    activity.findViewById<TextInputLayout>(R.id.placePairOriginLayout)
                        .hint
                        .toString()
                )
            }
        }
    }

    @Test
    fun routeSearchToolsUseInputCentersAndSwapDoesNotJumpWithCandidates() {
        val callback = AtomicReference<((CurrentPlaceSelectionResult) -> Unit)?>(null)
        installDependencies(ImmediateRouteRepository())
        SearchFragment.currentPlaceRequestOverride = { _, result ->
            callback.set(result)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitUntil { callback.get() != null }
            onView(withId(R.id.placePairOriginLoading)).check(matches(isDisplayed()))

            var swapCenterBeforeCandidates = 0
            scenario.onActivity { activity ->
                val originInput = activity.findViewById<View>(R.id.placePairOriginInput)
                val destinationInput =
                    activity.findViewById<View>(R.id.placePairDestinationInput)
                val locationButton =
                    activity.findViewById<View>(R.id.placePairCurrentLocationButton)
                val originLoading = activity.findViewById<View>(R.id.placePairOriginLoading)
                val swapButton = activity.findViewById<View>(R.id.placePairSwapButton)
                val tolerance = dp(activity, 1)
                val originCenter = originInput.centerYOnScreen()
                val destinationCenter = destinationInput.centerYOnScreen()
                val expectedSwapCenter = (originCenter + destinationCenter) / 2

                val locationCenter = locationButton.centerYOnScreen()
                val loadingCenter = originLoading.centerYOnScreen()
                val swapCenter = swapButton.centerYOnScreen()
                assertTrue(
                    "origin=$originCenter location=$locationCenter tolerance=$tolerance",
                    abs(originCenter - locationCenter) <= tolerance
                )
                assertTrue(
                    "origin=$originCenter loading=$loadingCenter tolerance=$tolerance",
                    abs(originCenter - loadingCenter) <= tolerance
                )
                assertTrue(
                    "expectedSwap=$expectedSwapCenter actualSwap=$swapCenter tolerance=$tolerance",
                    abs(expectedSwapCenter - swapCenter) <= tolerance
                )
                swapCenterBeforeCandidates = swapButton.centerYOnScreen()
            }

            callback.get()?.invoke(CurrentPlaceSelectionResult.Failure)
            onView(withId(R.id.placePairOriginInput)).perform(
                click(),
                replaceText("o"),
                closeSoftKeyboard()
            )
            waitForText("測試起點")

            scenario.onActivity { activity ->
                val candidateList =
                    activity.findViewById<View>(R.id.placePairOriginCandidateList)
                val swapButton = activity.findViewById<View>(R.id.placePairSwapButton)
                assertEquals(View.VISIBLE, candidateList.visibility)
                assertTrue(
                    abs(swapCenterBeforeCandidates - swapButton.centerYOnScreen()) <=
                        dp(activity, 1)
                )
            }
        }
    }

    @Test
    fun searchCandidatesKeepTheRefreshableResultViewportAndOwnVerticalGesturesUntilTheyClose() {
        installDependencies(MultipleRouteRepository())
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            callback(CurrentPlaceSelectionResult.Failure)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val results = activity.findViewById<RecyclerView>(R.id.searchResultList)
                    ready = results.visibility == View.VISIBLE && results.adapter?.itemCount == 20
                }
                ready
            }
            onView(withId(R.id.searchEditButton)).perform(click())
            var resultViewport: OuterViewport? = null
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<SwipeRefreshLayout>(R.id.searchSwipeRefresh).isEnabled)
                val resultList = activity.findViewById<RecyclerView>(R.id.searchResultList)
                (resultList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    4,
                    dp(activity, 24)
                )
            }
            waitUntil {
                var positioned = false
                scenario.onActivity { activity ->
                    val viewport = outerViewport(activity)
                    positioned = viewport.firstResultPosition > 0 && viewport.firstResultTop != 0
                }
                positioned
            }
            scenario.onActivity { activity ->
                resultViewport = outerViewport(activity)
            }
            onView(withId(R.id.placePairOriginInput)).perform(
                click()
            )
            scenario.onActivity { activity ->
                showOriginCandidatesForExistingResult(activity)
            }
            waitUntil {
                var visible = false
                scenario.onActivity { activity ->
                    visible = activity.findViewById<RecyclerView>(
                        R.id.placePairOriginCandidateList
                    ).visibility == View.VISIBLE
                }
                visible
            }

            val expectedViewport = requireNotNull(resultViewport)
            var candidateStartOffset = 0
            scenario.onActivity { activity ->
                val searchContent = activity.findViewById<View>(R.id.searchContent)
                val candidates = activity.findViewById<RecyclerView>(
                    R.id.placePairOriginCandidateList
                )
                val refresh = activity.findViewById<SwipeRefreshLayout>(R.id.searchSwipeRefresh)
                val preservedScrollFlags = (searchContent.layoutParams as
                    com.google.android.material.appbar.AppBarLayout.LayoutParams).scrollFlags
                assertEquals(1, preservedScrollFlags)
                assertFalse(candidates.isNestedScrollingEnabled)
                assertFalse(refresh.isEnabled)
                assertEquals(0, candidates.height % dp(activity, 52))
                assertTrue(candidates.height / dp(activity, 52) in 5..6)
                candidateStartOffset = candidates.computeVerticalScrollOffset()
                assertOuterViewportUnchanged(expectedViewport, activity)
            }
            onView(withId(R.id.placePairOriginCandidateList)).perform(swipeUp())
            scenario.onActivity { activity ->
                val candidates = activity.findViewById<RecyclerView>(
                    R.id.placePairOriginCandidateList
                )
                assertTrue(candidates.computeVerticalScrollOffset() > candidateStartOffset)
                assertOuterViewportUnchanged(expectedViewport, activity)
                candidates.scrollToPosition(19)
            }
            onView(withId(R.id.placePairOriginCandidateList)).perform(swipeUp())
            scenario.onActivity { activity ->
                assertOuterViewportUnchanged(expectedViewport, activity)
                activity.findViewById<RecyclerView>(R.id.placePairOriginCandidateList)
                    .scrollToPosition(0)
            }
            onView(withId(R.id.placePairOriginCandidateList)).perform(swipeDown())
            scenario.onActivity { activity ->
                assertOuterViewportUnchanged(expectedViewport, activity)
            }

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
            waitUntil {
                var closed = false
                scenario.onActivity { activity ->
                    closed = activity.findViewById<View>(R.id.placePairOriginCandidateList).visibility ==
                        View.GONE
                }
                closed
            }
            scenario.onActivity { activity ->
                val searchContent = activity.findViewById<View>(R.id.searchContent)
                val preservedFlagsAfterClose = (searchContent.layoutParams as
                    com.google.android.material.appbar.AppBarLayout.LayoutParams).scrollFlags
                assertEquals(1, preservedFlagsAfterClose)
                assertFalse(activity.findViewById<SwipeRefreshLayout>(R.id.searchSwipeRefresh).isEnabled)
                assertEquals("測試起點", activity.findViewById<TextInputLayout>(
                    R.id.placePairOriginLayout
                ).editText?.text?.toString())
                assertEquals("測試終點", activity.findViewById<TextInputLayout>(
                    R.id.placePairDestinationLayout
                ).editText?.text?.toString())
                assertEquals(20, activity.findViewById<RecyclerView>(R.id.searchResultList)
                    .adapter?.itemCount)
                assertOuterViewportUnchanged(expectedViewport, activity)
            }
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.placePairOriginInput).clearFocus()
            }
            onView(withId(R.id.placePairOriginInput)).perform(closeSoftKeyboard())
            scenario.onActivity { activity ->
                assertEquals(
                    View.GONE,
                    activity.findViewById<View>(R.id.placePairOriginCandidateList).visibility
                )
                assertFalse(activity.findViewById<SwipeRefreshLayout>(R.id.searchSwipeRefresh).isEnabled)
                assertOuterViewportUnchanged(expectedViewport, activity)
            }
            onView(withId(R.id.searchResultList)).perform(swipeUpVisibleArea())
            waitUntil {
                var outerScrollResumed = false
                scenario.onActivity { activity ->
                    val viewport = outerViewport(activity)
                    outerScrollResumed = viewport.appBarTop < expectedViewport.appBarTop ||
                        viewport.firstResultPosition > expectedViewport.firstResultPosition
                }
                outerScrollResumed
            }
        }
    }

    @Test
    fun searchAppBarMovesOnlyFromTheResultListEvenWhileEditingRetainedResults() {
        installDependencies(MultipleRouteRepository())

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())

            var expandedTop = 0
            scenario.onActivity { activity ->
                val appBar = activity.findViewById<com.google.android.material.appbar.AppBarLayout>(
                    R.id.searchAppBar
                )
                appBar.setExpanded(true, false)
            }
            waitUntil {
                var expanded = false
                scenario.onActivity { activity ->
                    expanded = activity.findViewById<View>(R.id.searchAppBar).top == 0
                }
                expanded
            }
            scenario.onActivity { activity ->
                expandedTop = activity.findViewById<View>(R.id.searchAppBar).top
            }
            onView(withId(R.id.searchContent)).perform(swipeUpVisibleArea())
            scenario.onActivity { activity ->
                assertEquals(expandedTop, activity.findViewById<View>(R.id.searchAppBar).top)
            }

            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val list = activity.findViewById<RecyclerView>(R.id.searchResultList)
                    ready = list.visibility == View.VISIBLE && list.adapter?.itemCount == 20
                }
                ready
            }
            Thread.sleep(300)
            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.appbar.AppBarLayout>(
                    R.id.searchAppBar
                ).setExpanded(true, false)
            }
            waitUntil {
                var expanded = false
                scenario.onActivity { activity ->
                    expanded = activity.findViewById<View>(R.id.searchAppBar).top == 0
                }
                expanded
            }
            scenario.onActivity { activity ->
                expandedTop = activity.findViewById<View>(R.id.searchAppBar).top
            }
            onView(withId(R.id.searchContent)).perform(swipeUpVisibleArea())
            scenario.onActivity { activity ->
                assertEquals(expandedTop, activity.findViewById<View>(R.id.searchAppBar).top)
            }
            onView(withId(R.id.searchResultList)).perform(swipeUpVisibleArea())
            waitUntil {
                var collapsed = false
                scenario.onActivity { activity ->
                    collapsed = activity.findViewById<View>(R.id.searchAppBar).top < expandedTop
                }
                collapsed
            }

            scenario.onActivity { activity ->
                activity.findViewById<com.google.android.material.appbar.AppBarLayout>(
                    R.id.searchAppBar
                ).setExpanded(true, false)
            }
            waitUntil {
                var expanded = false
                scenario.onActivity { activity ->
                    expanded = activity.findViewById<View>(R.id.searchAppBar).top == 0
                }
                expanded
            }
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val tripContext = activity.findViewById<View>(R.id.searchTripContext)
                    val edit = activity.findViewById<View>(R.id.searchEditButton)
                    ready = tripContext.visibility == View.VISIBLE &&
                        tripContext.isEnabled &&
                        edit.visibility == View.VISIBLE
                }
                ready
            }
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.searchEditButton).performClick())
            }
            waitUntil {
                var visible = false
                scenario.onActivity { activity ->
                    visible = activity.findViewById<View>(
                        R.id.searchInputContainer
                    ).visibility == View.VISIBLE
                }
                visible
            }
            Thread.sleep(350)
            waitUntil {
                var expanded = false
                scenario.onActivity { activity ->
                    expanded = activity.findViewById<View>(R.id.searchAppBar).top == 0
                }
                expanded
            }
            scenario.onActivity { activity ->
                expandedTop = activity.findViewById<View>(R.id.searchAppBar).top
            }
            onView(withId(R.id.searchInputContainer)).perform(swipeUpVisibleArea())
            scenario.onActivity { activity ->
                assertEquals(expandedTop, activity.findViewById<View>(R.id.searchAppBar).top)
            }
            onView(withId(R.id.searchResultList)).perform(swipeUpVisibleArea())
            waitUntil {
                var collapsed = false
                scenario.onActivity { activity ->
                    collapsed = activity.findViewById<View>(R.id.searchAppBar).top < expandedTop
                }
                collapsed
            }
        }
    }

    @Test
    fun searchCandidatesStayAboveImeWhenMainNavigationIsCovered() {
        installDependencies(MultipleRouteRepository())
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            callback(CurrentPlaceSelectionResult.Failure)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.placePairOriginInput)).perform(click())
            scenario.onActivity(::showOriginCandidatesForExistingResult)
            waitUntil {
                var candidatesAboveIme = false
                scenario.onActivity { activity ->
                    val candidates = activity.findViewById<RecyclerView>(
                        R.id.placePairOriginCandidateList
                    )
                    val insets = requireNotNull(ViewCompat.getRootWindowInsets(candidates))
                    val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                    val rootLocation = IntArray(2)
                    val candidateLocation = IntArray(2)
                    candidates.rootView.getLocationOnScreen(rootLocation)
                    candidates.getLocationOnScreen(candidateLocation)
                    val imeTop = rootLocation[1] + candidates.rootView.height - imeInsets.bottom
                    candidatesAboveIme = insets.isVisible(WindowInsetsCompat.Type.ime()) &&
                        candidates.visibility == View.VISIBLE &&
                        candidateLocation[1] + candidates.height <= imeTop
                }
                candidatesAboveIme
            }
        }
    }

    @Test
    fun searchFlowSupportsFallbackSwapQueryActionsAndSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val routeRepository = ImmediateRouteRepository()
        val detailRepository = installDependencies(routeRepository)
        val monitorRequest = AtomicReference<Pair<BusRouteOption, Place?>?>()
        MainActivity.monitorSettingsRequestObserver = { route, origin ->
            monitorRequest.set(route to origin)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitForOriginHint("起點 · 定位失敗，請手動選擇")

            onView(withId(R.id.searchQueryButton)).check(matches(isNotEnabled()))

            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.placePairSwapButton)).perform(click())
            onView(withId(R.id.placePairOriginInput)).check(matches(withText("測試終點")))
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("測試起點")))

            onView(withId(R.id.searchQueryButton)).perform(click())
            waitForTextAndScroll("測試路線")
            onView(allOf(withId(R.id.resultSummaryText), isDisplayed())).check(matches(isDisplayed()))
            onView(allOf(withId(R.id.resultUpdatedAtText), isDisplayed())).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val save = activity.findViewById<View>(R.id.searchSaveButton)
                val tripContext = activity.findViewById<View>(R.id.searchTripContext)
                val inputContainer = activity.findViewById<View>(R.id.searchInputContainer)
                assertTrue(save.measuredHeight >= dp(activity, 48))
                assertEquals(View.VISIBLE, tripContext.visibility)
                assertEquals(View.GONE, inputContainer.visibility)
                assertTrue(save.right <= tripContext.right)
            }
            onView(allOf(withId(R.id.sortRouteButton), isDisplayed())).perform(click())
            onView(allOf(withId(R.id.sortRouteButton), isDisplayed())).check(matches(withText("路線 ↑")))

            onView(allOf(withId(R.id.busEtaTextColumn), isDisplayed(), isClickable()))
                .perform(click())
            waitForTextInDialog("第2班")
            pressBack()
            waitForAbsent("第2班")

            scenario.onActivity { activity ->
                val search = activity.supportFragmentManager.findFragmentByTag("search") as SearchFragment
                val list = search.requireView().findViewById<androidx.recyclerview.widget.RecyclerView>(
                    R.id.searchResultList
                )
                assertTrue(list.getChildAt(0).performClick())
            }
            waitForDisplayedInDialog(R.id.routeDetailScroll)
            pressBack()
            waitUntil {
                try {
                    onView(withId(R.id.routeDetailScroll)).check(doesNotExist())
                    true
                } catch (_: AssertionError) {
                    false
                }
            }

            val detailLoadsBeforeMonitor = detailRepository.loadCount
            scenario.onActivity { activity ->
                val search = activity.supportFragmentManager.findFragmentByTag("search") as SearchFragment
                assertTrue(search.requireView().findViewById<View>(R.id.busMonitorButton).performClick())
            }
            waitUntil { monitorRequest.get() != null }
            assertTrue(monitorRequest.get()?.first?.firstLegEtaQuery != null)
            assertTrue(monitorRequest.get()?.first?.waitTimeState is WaitTimeState.Available)
            assertTrue(monitorRequest.get()?.second != null)
            waitUntil { detailRepository.loadCount > detailLoadsBeforeMonitor }
            waitForTextInDialog("通知欄監控")
            pressBack()
            waitForAbsent("通知欄監控")

            var saveDialogTitle = ""
            var saveAction = ""
            scenario.onActivity { activity ->
                saveDialogTitle = activity.getString(R.string.save_frequent_title)
                saveAction = activity.getString(R.string.action_save)
            }
            onView(withId(R.id.searchSaveButton)).perform(click())
            waitForTextInDialog(saveDialogTitle)
            onView(withText(saveAction)).inRoot(isDialog()).perform(click())
            waitUntil {
                RouteConfigRepository(InstrumentationRegistry.getInstrumentation().targetContext)
                    .getAll()
                    .any { it.name == "測試終點 -> 測試起點" }
            }
            onView(withId(R.id.searchSaveButton)).check(matches(isNotEnabled()))
            scenario.onActivity { activity ->
                val save = activity.findViewById<View>(R.id.searchSaveButton)
                val route = activity.findViewById<TextView>(R.id.searchTripRouteText)
                val actions = activity.findViewById<View>(R.id.searchTripActions)
                assertTrue(save.measuredHeight >= dp(activity, 48))
                assertTrue(route.textSize > 0f)
                assertTrue(route.right <= actions.left || route.bottom < actions.top)
            }

            onView(withId(R.id.navigation_frequent_routes)).perform(click())
            onView(withId(R.id.frequentRoutesRoot)).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<android.view.ViewGroup>(R.id.routeShortcutCardsContainer)
                        .childCount > 0
                )
                assertTrue(activity.findViewById<SwipeRefreshLayout>(R.id.searchSwipeRefresh) != null)
            }
        }

        val repository = RouteConfigRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        repository.getAll()
            .filter { it.name == "測試終點 -> 測試起點" }
            .forEach { repository.delete(it.id) }
        repository.close()
    }

    @Test
    fun saveDialogKeepsAvailabilityAcrossDuplicateFailuresAndRetryThenPreventsRepeatInsert() {
        val gateway = RecordingSaveGateway().apply {
            duplicateNames += "測試起點 -> 測試終點"
            insertOutcomes += { -1L }
            insertOutcomes += { throw IllegalStateException("injected") }
            insertOutcomes += { 42L }
        }
        installDependencies(ImmediateRouteRepository())
        SearchFragment.routeConfigSaveGatewayFactory = { gateway }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitForText("測試路線")

            onView(withId(R.id.searchSaveButton)).perform(click())
            onView(withText(R.string.action_save)).inRoot(isDialog()).perform(click())
            waitForTextInDialog(
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getString(R.string.route_duplicate_detail)
            )
            onView(withHint(R.string.frequent_route_name_hint))
                .inRoot(isDialog())
                .perform(replaceText("可重試行程"))

            repeat(2) {
                onView(withText(R.string.action_save)).inRoot(isDialog()).perform(click())
                waitForTextInDialog(
                    InstrumentationRegistry.getInstrumentation().targetContext
                        .getString(R.string.save_frequent_failed)
                )
            }
            onView(withText(R.string.action_save)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.searchSaveButton)).check(matches(isNotEnabled()))
            onView(withText(R.string.search_trip_saved)).check(matches(isDisplayed()))
            assertEquals(3, gateway.insertCount)

            scenario.onActivity { activity ->
                val save = activity.findViewById<MaterialButton>(R.id.searchSaveButton)
                assertIconMatchesResource(
                    button = save,
                    expectedResource = R.drawable.ic_bookmark_filled,
                    unexpectedResource = R.drawable.ic_bookmark_outline
                )
                save.performClick()
            }
            assertEquals(3, gateway.insertCount)
        }
    }

    @Test
    fun editingRetainedResultsKeepsMonitorOriginFromTheLastSuccessfulQuery() {
        val routeRepository = ImmediateRouteRepository()
        installDependencies(routeRepository)
        val monitorRequest = AtomicReference<Pair<BusRouteOption, Place?>?>()
        MainActivity.monitorSettingsRequestObserver = { route, origin ->
            monitorRequest.set(route to origin)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitForText("測試路線")

            onView(withId(R.id.searchEditButton)).perform(click())
            selectPlace(R.id.placePairOriginInput, "d", "測試終點")
            onView(withText("測試路線")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                val search = activity.supportFragmentManager.findFragmentByTag("search") as SearchFragment
                assertTrue(search.requireView().findViewById<View>(R.id.busMonitorButton).performClick())
            }

            waitUntil { monitorRequest.get() != null }
            assertEquals("測試起點", monitorRequest.get()?.second?.name)
        }
    }

    @Test
    fun duplicateLookupFailureKeepsDialogOpenAndRetryInsertsExactlyOnce() {
        val gateway = RecordingSaveGateway().apply {
            duplicateOutcomes += { throw IllegalStateException("injected duplicate lookup failure") }
            duplicateOutcomes += { false }
            insertOutcomes += { 73L }
        }
        installDependencies(ImmediateRouteRepository())
        SearchFragment.routeConfigSaveGatewayFactory = { gateway }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitForText("測試路線")

            onView(withId(R.id.searchSaveButton)).perform(click())
            onView(withText(R.string.action_save)).inRoot(isDialog()).perform(click())
            waitForTextInDialog(
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getString(R.string.save_frequent_failed)
            )
            assertEquals(1, gateway.duplicateCheckCount)
            assertEquals(0, gateway.insertCount)

            onView(withText(R.string.action_save)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.searchSaveButton)).check(matches(isNotEnabled()))
            onView(withText(R.string.search_trip_saved)).check(matches(isDisplayed()))
            assertEquals(2, gateway.duplicateCheckCount)
            assertEquals(1, gateway.insertCount)
        }
    }

    @Test
    fun editingRevokesSaveAndAnewSuccessfulQueryCreatesTheNextSaveGeneration() {
        val gateway = RecordingSaveGateway().apply {
            insertOutcomes += { 51L }
        }
        val routeRepository = ImmediateRouteRepository()
        installDependencies(routeRepository)
        SearchFragment.routeConfigSaveGatewayFactory = { gateway }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitForText("測試路線")

            onView(withId(R.id.searchSaveButton)).perform(click())
            onView(withText(R.string.action_cancel)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.searchSaveButton)).check(matches(isEnabled()))
            assertEquals(0, gateway.insertCount)

            onView(withId(R.id.searchEditButton)).perform(click())
            onView(withId(R.id.searchTripContext))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.searchSaveButton))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withText("測試路線")).check(matches(isDisplayed()))

            onView(withId(R.id.navigation_frequent_routes)).perform(click())
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.searchInputContainer)).check(matches(isDisplayed()))
            onView(withText("測試路線")).check(matches(isDisplayed()))

            onView(withId(R.id.searchResultList)).perform(swipeDownVisibleArea())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, routeRepository.queryCount)

            selectPlace(R.id.placePairDestinationInput, "d2", "第二終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitForText("測試路線")
            onView(withId(R.id.searchSaveButton)).check(matches(isEnabled()))
            onView(withText(R.string.search_trip_save)).check(matches(isDisplayed()))
            onView(withId(R.id.searchSaveButton)).check { view, _ ->
                val save = view as MaterialButton
                assertIconMatchesResource(
                    button = save,
                    expectedResource = R.drawable.ic_bookmark_outline,
                    unexpectedResource = R.drawable.ic_bookmark_filled
                )
            }
            assertEquals(0, gateway.insertCount)
        }
    }

    @Test
    fun staleSaveDialogRejectsInsertAfterItsSuccessfulContextIsInvalidated() {
        val routeRepository = CapturingRouteRepository()
        val gateway = RecordingSaveGateway()
        installDependencies(routeRepository)
        SearchFragment.routeConfigSaveGatewayFactory = { gateway }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 1 }
            routeRepository.callbacks[0].onInitialRoutes(listOf(route("原查詢")))
            waitForText("原查詢")

            onView(withId(R.id.searchSaveButton)).perform(click())
            scenario.onActivity { activity ->
                val fragment =
                    activity.supportFragmentManager.findFragmentByTag("search") as SearchFragment
                SearchFragment::class.java.getDeclaredMethod(
                    "query",
                    Boolean::class.javaPrimitiveType
                ).apply { isAccessible = true }
                    .invoke(fragment, true)
            }
            waitUntil { routeRepository.callbacks.size == 2 }
            routeRepository.callbacks[1].onInitialRoutes(emptyList())
            waitUntil {
                var hidden = false
                scenario.onActivity { activity ->
                    hidden = activity.findViewById<View>(R.id.searchTripContext).visibility == View.GONE
                }
                hidden
            }

            onView(withText(R.string.action_save)).inRoot(isDialog()).perform(click())
            waitForTextInDialog(
                InstrumentationRegistry.getInstrumentation().targetContext
                    .getString(R.string.save_query_changed)
            )
            assertEquals(0, gateway.insertCount)
        }
    }

    @Test
    fun editingReplacesTripContextAndKeepsOldResultsUntilTheNextQueryStarts() {
        val routeRepository = CapturingRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 1 }

            routeRepository.callbacks.single().onInitialRoutes(listOf(route("折疊路線")))
            waitForText("折疊路線")
            onView(withId(R.id.searchTripContext)).check(matches(isDisplayed()))
            onView(withId(R.id.searchInputContainer))
                .check(matches(withEffectiveVisibility(GONE)))
            scenario.onActivity { activity ->
                val inputContainer = activity.findViewById<View>(R.id.searchInputContainer)
                val tripContext = activity.findViewById<View>(R.id.searchTripContext)
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    inputContainer.importantForAccessibility
                )
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
                    tripContext.importantForAccessibility
                )
                val down = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)
                try {
                    assertFalse(inputContainer.dispatchTouchEvent(down))
                } finally {
                    down.recycle()
                }
            }
            onView(withId(R.id.searchEditButton)).perform(click())
            onView(withId(R.id.searchInputContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.searchTripContext))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withText("折疊路線")).check(matches(isDisplayed()))
            onView(withId(R.id.searchRouteResultControls)).check(matches(isDisplayed()))
            onView(withId(R.id.searchSaveButton))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.searchSwipeRefresh)).check(matches(isNotEnabled()))
            scenario.onActivity { activity ->
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
                    activity.findViewById<View>(R.id.searchInputContainer)
                        .importantForAccessibility
                )
                assertFalse(activity.findViewById<View>(R.id.placePairOriginInput).hasFocus())
                assertFalse(activity.findViewById<View>(R.id.placePairDestinationInput).hasFocus())
            }

            selectPlace(R.id.placePairDestinationInput, "d2", "第二終點")
            onView(withId(R.id.searchTripContext))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withText("折疊路線")).check(matches(isDisplayed()))
            onView(withId(R.id.searchRouteResultControls)).check(matches(isDisplayed()))
            assertEquals(1, routeRepository.queryCount)

            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 2 }
            assertViewAbsent("折疊路線")
            onView(searchView(R.id.resultStatusCard)).check(matches(isDisplayed()))

            routeRepository.callbacks[1].onInitialRoutes(listOf(route("新查詢路線")))
            waitForText("新查詢路線")
            onView(withId(R.id.searchTripContext)).check(matches(isDisplayed()))
            onView(withId(R.id.searchInputContainer))
                .check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun foldedAndEditingResultsSurviveTopLevelDestinationRoundTrips() {
        val routeRepository = CapturingRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 1 }
            routeRepository.callbacks[0].onInitialRoutes(listOf(route("保留路線")))
            waitForText("保留路線")

            onView(withId(R.id.navigation_frequent_routes)).perform(click())
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.searchTripContext)).check(matches(isDisplayed()))
            onView(withText("保留路線")).check(matches(isDisplayed()))

            onView(withId(R.id.searchEditButton)).perform(click())
            onView(withId(R.id.navigation_frequent_routes)).perform(click())
            onView(withId(R.id.navigation_search)).perform(click())
            onView(withId(R.id.searchInputContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.searchTripContext))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withText("保留路線")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun staleProgressiveCallbackCannotReplaceNewSearch() {
        val routeRepository = CapturingRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 1 }

            selectPlace(R.id.placePairDestinationInput, "d2", "第二終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 2 }

            routeRepository.callbacks[0].onInitialRoutes(listOf(route("過期路線")))
            routeRepository.callbacks[1].onInitialRoutes(listOf(route("目前路線")))

            waitForText("目前路線")
            assertViewAbsent("過期路線")
        }
    }

    @Test
    fun searchUsesSharedStatusCardAndFixedRefreshFeedbackWithoutAllowingReentry() {
        val routeRepository = CapturingRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 1 }

            onView(searchStatusCard()).check(matches(isDisplayed()))
            onView(searchView(R.id.resultStatusProgress)).check(matches(isDisplayed()))
            onView(withId(R.id.searchQueryButton)).check(matches(isNotEnabled()))

            routeRepository.callbacks[0].onInitialRoutes(emptyList())
            waitUntil {
                var emptyVisible = false
                scenario.onActivity { activity ->
                    val search =
                        activity.supportFragmentManager.findFragmentByTag("search") as SearchFragment
                    emptyVisible = search.requireView().findViewById<View>(
                        R.id.resultStatusCard
                    ).visibility == View.VISIBLE && search.requireView().findViewById<View>(
                        R.id.resultStatusProgress
                    ).visibility == View.GONE
                }
                emptyVisible
            }
            onView(withId(R.id.searchQueryButton)).check(matches(isEnabled()))
            onView(withId(R.id.placePairOriginInput)).check(matches(isDisplayed()))

            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 2 }
            routeRepository.callbacks[1].onInitialRoutes(listOf(route("可刷新路線")))
            waitForText("可刷新路線")
            onView(withId(R.id.searchSaveButton)).check(matches(isDisplayed()))

            onView(withId(R.id.searchSwipeRefresh)).perform(swipeDownVisibleArea())
            waitUntil { routeRepository.callbacks.size == 3 }
            onView(withId(R.id.searchResultRefreshOverlay)).check(matches(isDisplayed()))
            onView(withId(R.id.searchResultRefreshProgress)).check(matches(isDisplayed()))
            onView(withId(R.id.searchQueryButton)).check(matches(isNotEnabled()))
            onView(withId(R.id.searchSaveButton)).check(matches(isDisplayed()))
            onView(withId(R.id.searchInputContainer))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.searchSwipeRefresh)).perform(swipeDownVisibleArea())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(3, routeRepository.queryCount)

            routeRepository.callbacks[2].onInitialRoutes(listOf(route("刷新後路線")))
            waitForText("刷新後路線")
            onView(withId(R.id.searchResultRefreshSuccess)).check(matches(isDisplayed()))
            onView(withId(R.id.searchQueryButton)).check(matches(isNotEnabled()))
            onView(withId(R.id.searchSaveButton)).check(matches(isDisplayed()))
            onView(withId(R.id.searchInputContainer))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.searchSwipeRefresh)).perform(swipeDownVisibleArea())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(3, routeRepository.queryCount)
            Thread.sleep(650)
            onView(withId(R.id.searchResultRefreshOverlay)).check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.searchTripContext)).check(matches(isDisplayed()))

            onView(withId(R.id.searchSwipeRefresh)).perform(swipeDownVisibleArea())
            waitUntil { routeRepository.callbacks.size == 4 }
            routeRepository.callbacks[3].onFailure(IllegalStateException("refresh failure"))
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    ready = activity.findViewById<View>(R.id.searchResultRefreshOverlay).visibility ==
                        View.GONE && activity.findViewById<View>(R.id.searchSaveButton).visibility ==
                        View.VISIBLE
                }
                ready
            }
            onView(withText("刷新後路線")).check(matches(isDisplayed()))
            onView(withId(R.id.searchTripContext)).check(matches(isDisplayed()))

            var baseResultPaddingTop = 0
            scenario.onActivity { activity ->
                baseResultPaddingTop =
                    activity.findViewById<RecyclerView>(R.id.searchResultList).paddingTop
            }
            onView(withId(R.id.searchSwipeRefresh)).perform(swipeDownVisibleArea())
            waitUntil { routeRepository.callbacks.size == 5 }
            scenario.onActivity { activity ->
                assertTrue(
                    activity.findViewById<RecyclerView>(R.id.searchResultList).paddingTop >
                        baseResultPaddingTop
                )
            }
            onView(withId(R.id.searchEditButton)).perform(click())
            onView(withId(R.id.placePairDestinationInput)).perform(replaceText("已清除"))
            waitUntil {
                var cancelled = false
                scenario.onActivity { activity ->
                    cancelled =
                        activity.findViewById<View>(R.id.searchResultRefreshOverlay).visibility ==
                            View.GONE &&
                            activity.findViewById<RecyclerView>(R.id.searchResultList).paddingTop ==
                            baseResultPaddingTop
                }
                cancelled
            }
            routeRepository.callbacks[4].onInitialRoutes(listOf(route("過期修改結果")))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertViewAbsent("過期修改結果")

            selectPlace(R.id.placePairDestinationInput, "d2", "第二終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 6 }
            routeRepository.callbacks[5].onInitialRoutes(listOf(route("修改後結果")))
            waitForText("修改後結果")

            onView(withId(R.id.searchSwipeRefresh)).perform(swipeDownVisibleArea())
            waitUntil { routeRepository.callbacks.size == 7 }
            onView(withId(R.id.searchEditButton)).perform(click())
            onView(withId(R.id.placePairSwapButton)).perform(click())
            onView(withId(R.id.searchResultRefreshOverlay))
                .check(matches(withEffectiveVisibility(GONE)))
            routeRepository.callbacks[6].onInitialRoutes(listOf(route("過期交換結果")))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertViewAbsent("過期交換結果")
            onView(withId(R.id.searchQueryButton)).check(matches(isEnabled()))
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 8 }
        }
    }

    @Test
    fun failedOrCancelledSearchKeepsTheEditorRetryableAndRejectsLateCallbacks() {
        val routeRepository = CapturingRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 1 }

            routeRepository.callbacks[0].onFailure(IllegalStateException("failed"))
            onView(searchStatusCard()).check(matches(isDisplayed()))
            onView(searchView(R.id.resultStatusProgress))
                .check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.placePairOriginInput)).check(matches(isDisplayed()))
            onView(withId(R.id.searchQueryButton)).check(matches(isEnabled()))

            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 2 }
            onView(withId(R.id.navigation_frequent_routes)).perform(click())
            onView(withId(R.id.navigation_search)).perform(click())
            routeRepository.callbacks[1].onInitialRoutes(listOf(route("過期取消結果")))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertViewAbsent("過期取消結果")
            onView(withId(R.id.searchQueryButton)).check(matches(isEnabled()))
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 3 }
        }
    }

    @Test
    fun googleAttributionFollowsSwapAndRecreationThenClearsOnManualEdit() {
        SearchFragment.placeSearchRepositoryFactory = { FakePlaceRepository() }
        SearchFragment.busRouteRepositoryFactory = { ImmediateRouteRepository() }
        SearchFragment.routeDetailRepositoryFactory = { FakeRouteDetailRepository() }
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            callback(
                CurrentPlaceSelectionResult.Success(
                    place = Place("Google 測試地址", 22.3, 114.1),
                    snapshot = CurrentLocationSnapshot(
                        latitude = 22.3,
                        longitude = 114.1,
                        accuracyMeters = 15f,
                        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                    ),
                    attribution = PlaceAttribution.GOOGLE_MAPS
                )
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitForText("Google 測試地址")
            scenario.onActivity { activity ->
                assertEquals(
                    "起點 · 地址由 Google Maps 提供",
                    activity.findViewById<TextInputLayout>(R.id.placePairOriginLayout)
                        .hint
                        .toString()
                )
            }

            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.placePairSwapButton)).perform(click())
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("Google 測試地址")))
            scenario.onActivity { activity ->
                assertEquals(
                    "起點",
                    activity.findViewById<TextInputLayout>(R.id.placePairOriginLayout)
                        .hint
                        .toString()
                )
                assertEquals(
                    "終點 · 地址由 Google Maps 提供",
                    activity.findViewById<TextInputLayout>(R.id.placePairDestinationLayout)
                        .hint
                        .toString()
                )
            }

            scenario.recreate()
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("Google 測試地址")))
            scenario.onActivity { activity ->
                assertEquals(
                    "終點 · 地址由 Google Maps 提供",
                    activity.findViewById<TextInputLayout>(R.id.placePairDestinationLayout)
                        .hint
                        .toString()
                )
            }

            onView(withId(R.id.placePairDestinationInput)).perform(replaceText("手動修改"))
            scenario.onActivity { activity ->
                assertEquals(
                    "終點 · 從清單選擇",
                    activity.findViewById<TextInputLayout>(R.id.placePairDestinationLayout)
                        .hint
                        .toString()
                )
            }
        }
    }

    @Test
    fun swapKeepsUnconfirmedTextUnconfirmedWithoutStartingAQuery() {
        val routeRepository = ImmediateRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            waitForOriginHint("起點 · 定位失敗，請手動選擇")
            onView(withId(R.id.placePairOriginInput)).perform(replaceText("未確認起點"))
            onView(withId(R.id.placePairSwapButton)).perform(click())
            onView(withId(R.id.placePairOriginInput)).check(matches(withText("")))
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("未確認起點")))
            assertTrue(routeRepository.queryCount == 0)
        }
    }

    @Test
    fun recreationRestoresInputsWithoutFakeFoldThenFoldsOnlyAfterTheNewQuerySucceeds() {
        val routeRepository = CapturingRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitForOriginHint("起點 · 定位失敗，請手動選擇")
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.callbacks.size == 1 }
            val oldFragmentCallback = routeRepository.callbacks[0]
            oldFragmentCallback.onInitialRoutes(listOf(route("重建前路線")))
            waitForText("重建前路線")
            onView(withId(R.id.searchTripContext)).check(matches(isDisplayed()))

            scenario.recreate()

            waitUntil { routeRepository.callbacks.size == 2 }
            onView(withId(R.id.placePairOriginInput)).check(matches(withText("測試起點")))
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("測試終點")))
            onView(withId(R.id.searchInputContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.searchTripContext))
                .check(matches(withEffectiveVisibility(GONE)))

            oldFragmentCallback.onInitialRoutes(listOf(route("過期舊 Fragment 路線")))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertViewAbsent("過期舊 Fragment 路線")
            onView(withId(R.id.searchTripContext))
                .check(matches(withEffectiveVisibility(GONE)))

            routeRepository.callbacks[1].onInitialRoutes(listOf(route("重建後路線")))
            waitForText("重建後路線")
            onView(withId(R.id.searchTripContext)).check(matches(isDisplayed()))
            onView(withId(R.id.searchInputContainer))
                .check(matches(withEffectiveVisibility(GONE)))
        }
    }

    @Test
    fun pendingAutoOriginKeepsInputsEnabledAndDestinationSelectionDoesNotCancelIt() {
        val callback = AtomicReference<((CurrentPlaceSelectionResult) -> Unit)?>(null)
        installDependencies(ImmediateRouteRepository())
        SearchFragment.currentPlaceRequestOverride = { isAuto, result ->
            if (isAuto) callback.set(result)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            waitUntil { callback.get() != null }
            onView(withId(R.id.placePairOriginInput)).check(matches(isEnabled()))
            onView(withId(R.id.placePairDestinationInput)).check(matches(isEnabled()))
            onView(withId(R.id.placePairOriginLoading)).check(matches(isDisplayed()))

            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            callback.get()?.invoke(currentPlaceSuccess("Google 自動起點"))

            waitForText("Google 自動起點")
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("測試終點")))
            onView(withId(R.id.searchQueryButton)).check(matches(isEnabled()))
        }
    }

    @Test
    fun originEditRejectsLateAutoOriginCallback() {
        val callback = AtomicReference<((CurrentPlaceSelectionResult) -> Unit)?>(null)
        installDependencies(ImmediateRouteRepository())
        SearchFragment.currentPlaceRequestOverride = { isAuto, result ->
            if (isAuto) callback.set(result)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            waitUntil { callback.get() != null }
            onView(withId(R.id.placePairOriginInput)).perform(
                replaceText("手動起點"),
                closeSoftKeyboard()
            )
            callback.get()?.invoke(currentPlaceSuccess("不應覆蓋"))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.placePairOriginInput)).check(matches(withText("手動起點")))
            onView(withId(R.id.placePairCurrentLocationButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun swapRejectsLateAutoOriginCallback() {
        val callback = AtomicReference<((CurrentPlaceSelectionResult) -> Unit)?>(null)
        installDependencies(ImmediateRouteRepository())
        SearchFragment.currentPlaceRequestOverride = { isAuto, result ->
            if (isAuto) callback.set(result)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navigation_search)).perform(click())
            waitUntil { callback.get() != null }
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.placePairSwapButton)).perform(click())
            callback.get()?.invoke(currentPlaceSuccess("不應覆蓋"))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            onView(withId(R.id.placePairOriginInput)).check(matches(withText("測試終點")))
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("")))
        }
    }

    @Test
    fun recreationSilentlyRestoresCandidateDistancesWithoutBlockingOrReplacingDraft() {
        installDependencies(ImmediateRouteRepository())
        val currentPlaceRequests = AtomicInteger(0)
        val snapshotCallback = AtomicReference<((CurrentLocationSnapshot?) -> Unit)?>(null)
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            currentPlaceRequests.incrementAndGet()
            callback(CurrentPlaceSelectionResult.Failure)
        }
        SearchFragment.currentLocationSnapshotRequestOverride = { callback ->
            snapshotCallback.set(callback)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitUntil { currentPlaceRequests.get() == 1 }
            waitUntil { snapshotCallback.get() != null }
            onView(withId(R.id.placePairOriginInput)).perform(
                replaceText("保留的起點草稿"),
                closeSoftKeyboard()
            )
            val requestsBeforeRecreation = currentPlaceRequests.get()
            val staleSnapshotCallback = requireNotNull(snapshotCallback.get())
            snapshotCallback.set(null)

            scenario.recreate()

            waitUntil { snapshotCallback.get() != null }
            val activeSnapshotCallback = requireNotNull(snapshotCallback.get())
            assertEquals(requestsBeforeRecreation, currentPlaceRequests.get())
            onView(withId(R.id.placePairOriginInput)).check(matches(withText("保留的起點草稿")))
            onView(withId(R.id.placePairOriginInput)).check(matches(isEnabled()))
            onView(withId(R.id.placePairDestinationInput)).check(matches(isEnabled()))
            onView(withId(R.id.placePairOriginLoading)).check(matches(withEffectiveVisibility(GONE)))

            staleSnapshotCallback.invoke(
                CurrentLocationSnapshot(
                    latitude = 22.31,
                    longitude = 114.17,
                    accuracyMeters = 10f,
                    elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                )
            )
            onView(withId(R.id.placePairDestinationInput)).perform(
                click(),
                replaceText("d"),
                closeSoftKeyboard()
            )
            waitForText("測試終點")
            scenario.onActivity { activity ->
                val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(
                    R.id.placePairDestinationCandidateList
                )
                val row = list.findViewHolderForAdapterPosition(0)?.itemView as? ViewGroup
                assertEquals(View.GONE, row?.getChildAt(1)?.visibility)
            }

            activeSnapshotCallback.invoke(
                CurrentLocationSnapshot(
                    latitude = 22.31,
                    longitude = 114.17,
                    accuracyMeters = 10f,
                    elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                )
            )
            waitUntil {
                var distanceVisible = false
                scenario.onActivity { activity ->
                    val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(
                        R.id.placePairDestinationCandidateList
                    )
                    val row = list.findViewHolderForAdapterPosition(0)?.itemView as? ViewGroup
                    distanceVisible = row?.getChildAt(1)?.visibility == View.VISIBLE
                }
                distanceVisible
            }
            onView(withId(R.id.placePairOriginInput)).check(matches(withText("保留的起點草稿")))
            assertEquals(requestsBeforeRecreation, currentPlaceRequests.get())
        }
    }

    @Test
    fun firstEntrySnapshotRunsBesideAutoOriginAndSurvivesOriginEditing() {
        installDependencies(ImmediateRouteRepository())
        val currentPlaceCallback =
            AtomicReference<((CurrentPlaceSelectionResult) -> Unit)?>(null)
        val snapshotCallback = AtomicReference<((CurrentLocationSnapshot?) -> Unit)?>(null)
        SearchFragment.currentPlaceRequestOverride = { isAuto, callback ->
            if (isAuto) currentPlaceCallback.set(callback)
        }
        SearchFragment.currentLocationSnapshotRequestOverride = { callback ->
            snapshotCallback.set(callback)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitUntil { currentPlaceCallback.get() != null && snapshotCallback.get() != null }
            onView(withId(R.id.placePairOriginInput)).check(matches(isEnabled()))
            onView(withId(R.id.placePairDestinationInput)).check(matches(isEnabled()))

            onView(withId(R.id.placePairOriginInput)).perform(
                click(),
                replaceText("o"),
                closeSoftKeyboard()
            )
            snapshotCallback.get()?.invoke(
                CurrentLocationSnapshot(
                    latitude = 22.31,
                    longitude = 114.17,
                    accuracyMeters = 10f,
                    elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                )
            )
            waitForText("測試起點")
            waitForCandidateDistance(scenario, R.id.placePairOriginCandidateList)
            onView(withText("測試起點")).perform(click())

            onView(withId(R.id.placePairDestinationInput)).perform(
                click(),
                replaceText("d"),
                closeSoftKeyboard()
            )
            waitForText("測試終點")
            waitForCandidateDistance(scenario, R.id.placePairDestinationCandidateList)
            currentPlaceCallback.get()?.invoke(CurrentPlaceSelectionResult.Failure)

            onView(withId(R.id.placePairOriginInput)).check(matches(withText("測試起點")))
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("d")))
        }
    }

    private fun installDependencies(routeRepository: BusRouteRepository): FakeRouteDetailRepository {
        val detailRepository = FakeRouteDetailRepository()
        SearchFragment.placeSearchRepositoryFactory = { FakePlaceRepository() }
        SearchFragment.busRouteRepositoryFactory = { routeRepository }
        SearchFragment.routeDetailRepositoryFactory = { detailRepository }
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            callback(CurrentPlaceSelectionResult.Failure)
        }
        MainActivity.routeDetailRepositoryFactory = { detailRepository }
        return detailRepository
    }

    private fun selectPlace(inputId: Int, keyword: String, expected: String) {
        onView(withId(inputId)).perform(click(), replaceText(keyword), closeSoftKeyboard())
        val candidateListId = when (inputId) {
            R.id.placePairOriginInput -> R.id.placePairOriginCandidateList
            R.id.placePairDestinationInput -> R.id.placePairDestinationCandidateList
            else -> error("Unsupported place input id: $inputId")
        }
        val candidate = allOf(
            withContentDescription(startsWith(expected)),
            isDescendantOfA(withId(candidateListId)),
            isDisplayed()
        )
        waitUntil {
            try {
                onView(candidate).check(matches(isDisplayed()))
                true
            } catch (_: NoMatchingViewException) {
                false
            } catch (_: AssertionError) {
                false
            }
        }
        onView(candidate).perform(click())
    }

    private fun waitForText(text: String) {
        waitUntil {
            try {
                onView(withText(text)).check(matches(isDisplayed()))
                true
            } catch (_: NoMatchingViewException) {
                false
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun waitForOriginHint(expected: String) {
        waitUntil {
            var hint = ""
            try {
                onView(withId(R.id.placePairOriginLayout)).check { view, _ ->
                    hint = (view as TextInputLayout).hint?.toString().orEmpty()
                }
            } catch (_: Throwable) {
                return@waitUntil false
            }
            hint == expected
        }
    }

    private fun waitForTextAndScroll(text: String) {
        waitUntil {
            try {
                onView(withText(text)).check(matches(isDisplayed()))
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun waitForDisplayedInDialog(viewId: Int) {
        waitUntil {
            try {
                onView(withId(viewId)).inRoot(isDialog()).check(matches(isDisplayed()))
                true
            } catch (_: NoMatchingViewException) {
                false
            } catch (_: AssertionError) {
                false
            } catch (_: RuntimeException) {
                false
            }
        }
    }

    private fun waitForTextInDialog(text: String) {
        waitUntil {
            try {
                onView(withText(text)).inRoot(isDialog()).check(matches(isDisplayed()))
                true
            } catch (_: NoMatchingViewException) {
                false
            } catch (_: AssertionError) {
                false
            } catch (_: RuntimeException) {
                false
            }
        }
    }

    private fun assertViewAbsent(text: String) {
        onView(withText(text)).check(doesNotExist())
    }

    private fun waitForAbsent(text: String) {
        waitUntil {
            try {
                onView(withText(text)).check(doesNotExist())
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }

    private fun waitUntil(timeoutMillis: Long = 10_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for condition", condition())
    }

    private fun waitForCandidateDistance(
        scenario: ActivityScenario<MainActivity>,
        listId: Int
    ) {
        waitUntil {
            var visible = false
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(listId)
                val row = list.findViewHolderForAdapterPosition(0)?.itemView as? ViewGroup
                visible = row?.getChildAt(1)?.visibility == View.VISIBLE
            }
            visible
        }
    }

    private fun showOriginCandidatesForExistingResult(activity: MainActivity) {
        val search = activity.supportFragmentManager.findFragmentByTag("search") as SearchFragment
        val controllerField = SearchFragment::class.java.getDeclaredField("originController")
        controllerField.isAccessible = true
        val controller = requireNotNull(controllerField.get(search))
        val updateCandidates = controller.javaClass.getDeclaredMethod(
            "updatePlaceCandidates",
            List::class.java
        )
        updateCandidates.isAccessible = true
        updateCandidates.invoke(
            controller,
            (1..20).map { index -> Place("保留結果候選$index", 22.3, 114.1 + index) }
        )
    }

    private fun outerViewport(activity: MainActivity): OuterViewport {
        val resultList = activity.findViewById<RecyclerView>(R.id.searchResultList)
        val manager = resultList.layoutManager as LinearLayoutManager
        val position = manager.findFirstVisibleItemPosition()
        return OuterViewport(
            appBarTop = activity.findViewById<View>(R.id.searchAppBar).top,
            firstResultPosition = position,
            firstResultTop = manager.findViewByPosition(position)?.top ?: 0
        )
    }

    private fun assertOuterViewportUnchanged(expected: OuterViewport, activity: MainActivity) {
        assertEquals(expected, outerViewport(activity))
    }

    private fun swipeDownVisibleArea(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "swipe down within the visible result area"

        override fun perform(uiController: UiController, view: View) {
            GeneralSwipeAction(
                Swipe.FAST,
                visibleCoordinates(verticalFraction = 0.2f),
                visibleCoordinates(verticalFraction = 0.8f),
                Press.FINGER
            ).perform(uiController, view)
        }
    }

    private fun swipeUpVisibleArea(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()

        override fun getDescription(): String = "swipe up within the visible result area"

        override fun perform(uiController: UiController, view: View) {
            GeneralSwipeAction(
                Swipe.FAST,
                visibleCoordinates(verticalFraction = 0.8f),
                visibleCoordinates(verticalFraction = 0.2f),
                Press.FINGER
            ).perform(uiController, view)
        }
    }

    private fun visibleCoordinates(verticalFraction: Float): CoordinatesProvider =
        CoordinatesProvider { view ->
            val visibleBounds = Rect()
            check(view.getGlobalVisibleRect(visibleBounds))
            floatArrayOf(
                visibleBounds.exactCenterX(),
                visibleBounds.top + visibleBounds.height() * verticalFraction
            )
        }

    private fun searchStatusCard(): Matcher<View> = allOf(
        searchView(R.id.resultStatusCard),
        isDisplayed()
    )

    private fun searchView(viewId: Int): Matcher<View> = allOf(
        withId(viewId),
        isDescendantOfA(allOf(withId(R.id.searchRoot), isDisplayed()))
    )

    private fun assertIconMatchesResource(
        button: MaterialButton,
        expectedResource: Int,
        unexpectedResource: Int
    ) {
        val actual = requireNotNull(button.icon)
        val width = button.iconSize.takeIf { it > 0 }
            ?: actual.intrinsicWidth.coerceAtLeast(1)
        val height = button.iconSize.takeIf { it > 0 }
            ?: actual.intrinsicHeight.coerceAtLeast(1)
        val actualPixels = drawablePixels(actual, width, height)
        val expectedPixels = drawablePixels(
            requireNotNull(ContextCompat.getDrawable(button.context, expectedResource)).apply {
                setTintList(button.iconTint)
                state = actual.state
            },
            width,
            height
        )
        val unexpectedPixels = drawablePixels(
            requireNotNull(ContextCompat.getDrawable(button.context, unexpectedResource)).apply {
                setTintList(button.iconTint)
                state = actual.state
            },
            width,
            height
        )
        assertTrue(actualPixels.contentEquals(expectedPixels))
        assertFalse(actualPixels.contentEquals(unexpectedPixels))
    }

    private fun drawablePixels(drawable: Drawable, width: Int, height: Int): IntArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.bounds = android.graphics.Rect(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return IntArray(width * height).also { pixels ->
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun dp(context: android.content.Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun View.centerYOnScreen(): Int {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return location[1] + height / 2
    }

    private fun currentPlaceSuccess(name: String): CurrentPlaceSelectionResult.Success {
        return CurrentPlaceSelectionResult.Success(
            place = Place(name, 22.31, 114.17),
            snapshot = CurrentLocationSnapshot(
                latitude = 22.31,
                longitude = 114.17,
                accuracyMeters = 12f,
                elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
            ),
            attribution = PlaceAttribution.GOOGLE_MAPS
        )
    }

    private class FakePlaceRepository : PlaceSearchRepository {
        override fun searchPlaces(keyword: String): List<Place> = when (keyword) {
            "o" -> listOf(Place("測試起點", 22.3, 114.1))
            "d2" -> listOf(Place("第二終點", 22.5, 114.3))
            "many" -> (1..20).map { index -> Place("測試候選$index", 22.3, 114.1 + index) }
            else -> listOf(Place("測試終點", 22.4, 114.2))
        }
    }

    private class ImmediateRouteRepository : BusRouteRepository {
        @Volatile
        var queryCount = 0

        override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> =
            listOf(route("測試路線"))

        override fun searchRoutesProgressively(
            origin: Place,
            destination: Place,
            callback: BusRouteQueryCallback
        ) {
            queryCount += 1
            callback.onInitialRoutes(searchRoutes(origin, destination))
            callback.onRouteStopPreviewUpdated(
                "測試路線",
                RouteCardStopPreview("測試起點站", "測試終點站")
            )
        }
    }

    private class MultipleRouteRepository : BusRouteRepository {
        override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> =
            (1..20).map { index -> route("測試路線$index") }

        override fun searchRoutesProgressively(
            origin: Place,
            destination: Place,
            callback: BusRouteQueryCallback
        ) {
            callback.onInitialRoutes(searchRoutes(origin, destination))
        }
    }

    private class CapturingRouteRepository : BusRouteRepository {
        val callbacks = mutableListOf<BusRouteQueryCallback>()
        @Volatile var queryCount = 0

        override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> = emptyList()

        override fun searchRoutesProgressively(
            origin: Place,
            destination: Place,
            callback: BusRouteQueryCallback
        ) {
            queryCount += 1
            callbacks += callback
        }
    }

    private class RecordingSaveGateway : RouteConfigSaveGateway {
        val duplicateNames = mutableSetOf<String>()
        val duplicateOutcomes = ArrayDeque<() -> Boolean>()
        val insertOutcomes = ArrayDeque<() -> Long>()
        var duplicateCheckCount: Int = 0
        var insertCount: Int = 0

        override fun hasDuplicate(name: String, origin: Place, destination: Place): Boolean {
            duplicateCheckCount += 1
            return duplicateOutcomes.removeFirstOrNull()?.invoke() ?: (name in duplicateNames)
        }

        override fun insert(name: String, origin: Place, destination: Place): Long {
            insertCount += 1
            return insertOutcomes.removeFirstOrNull()?.invoke() ?: insertCount.toLong()
        }
    }

    private class FakeRouteDetailRepository : RouteDetailRepository {
        @Volatile
        var loadCount = 0

        override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
            loadCount += 1
            return RouteDetail(
                routeName = route.routeName,
                priceHkd = route.priceHkd,
                durationMinutes = route.durationMinutes,
                walkingDistanceMeters = route.walkingDistanceMeters,
                legs = listOf(
                    RouteDetailLeg(
                        route = "88",
                        routeVariant = "88",
                        directionText = "往測試終點",
                        boardingStop = stop("測試起點站", 1, RouteDetailStopRole.BOARDING),
                        viaStops = listOf(stop("中途站", 2, RouteDetailStopRole.VIA)),
                        alightingStop = stop("測試終點站", 3, RouteDetailStopRole.ALIGHTING)
                    )
                ),
                originWalkingDistanceMeters = 120
            )
        }
    }

    private companion object {
        data class OuterViewport(
            val appBarTop: Int,
            val firstResultPosition: Int,
            val firstResultTop: Int
        )

        fun route(name: String): BusRouteOption = BusRouteOption(
            routeName = name,
            routeSegments = listOf("88"),
            priceHkd = 8.8,
            durationMinutes = 25,
            arrivalMinutes = 4,
            transferCount = 0,
            walkingDistanceMeters = 120,
            waitTimeState = WaitTimeState.Available(
                listOf(EtaArrival(1, 4), EtaArrival(2, 10))
            ),
            firstLegEtaQuery = FirstLegEtaQuery("CTB", "88", "88", 1, 3, "O", "outbound"),
            routeDetailQuery = P2pRouteDetailQuery(
                rawInfo = "test",
                generalInfo = "",
                listId = "1",
                lang = "0",
                plan = P2pRoutePlan(legs = emptyList())
            ),
            stopPreview = RouteCardStopPreview("測試起點站", "測試終點站"),
            resultId = name
        )

        fun stop(name: String, sequence: Int, role: RouteDetailStopRole): RouteDetailStop =
            RouteDetailStop(
                rawName = name,
                displayName = name,
                stopId = sequence.toString(),
                sequence = sequence,
                latitude = 22.3,
                longitude = 114.2,
                routeVariant = "88",
                role = role
            )
    }
}
