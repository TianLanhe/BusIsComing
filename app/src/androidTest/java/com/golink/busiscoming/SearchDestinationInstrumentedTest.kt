package com.golink.busiscoming

import android.Manifest
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
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
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
import com.golink.busiscoming.ui.main.SearchFragment
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun searchFlowSupportsFallbackSwapQueryActionsRefreshAndSave() {
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
                val placeColumn = activity.findViewById<View>(R.id.placePairInputColumn)
                val swapSlot = activity.findViewById<View>(R.id.placePairSwapSlot)
                assertTrue(save.measuredHeight >= dp(activity, 48))
                assertTrue(save.right <= placeColumn.right)
                assertTrue(save.right <= swapSlot.left)
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

            onView(withId(R.id.searchResultList)).perform(swipeDownVisibleArea())
            waitUntil { routeRepository.queryCount >= 2 }

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
    fun recreationRestoresConfirmedPlacesAndRepeatsTheSubmittedQuery() {
        val routeRepository = ImmediateRouteRepository()
        installDependencies(routeRepository)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.navigation_search)).perform(click())
            waitForOriginHint("起點 · 定位失敗，請手動選擇")
            selectPlace(R.id.placePairOriginInput, "o", "測試起點")
            selectPlace(R.id.placePairDestinationInput, "d", "測試終點")
            onView(withId(R.id.searchQueryButton)).perform(click())
            waitUntil { routeRepository.queryCount == 1 }

            scenario.recreate()

            waitUntil { routeRepository.queryCount == 2 }
            onView(withId(R.id.placePairOriginInput)).check(matches(withText("測試起點")))
            onView(withId(R.id.placePairDestinationInput)).check(matches(withText("測試終點")))
            waitForTextAndScroll("測試路線")
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
        waitForText(expected)
        onView(withText(expected)).perform(click())
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

    private fun visibleCoordinates(verticalFraction: Float): CoordinatesProvider =
        CoordinatesProvider { view ->
            val visibleBounds = Rect()
            check(view.getGlobalVisibleRect(visibleBounds))
            floatArrayOf(
                visibleBounds.exactCenterX(),
                visibleBounds.top + visibleBounds.height() * verticalFraction
            )
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

    private class CapturingRouteRepository : BusRouteRepository {
        val callbacks = mutableListOf<BusRouteQueryCallback>()

        override fun searchRoutes(origin: Place, destination: Place): List<BusRouteOption> = emptyList()

        override fun searchRoutesProgressively(
            origin: Place,
            destination: Place,
            callback: BusRouteQueryCallback
        ) {
            callbacks += callback
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
