package com.golink.busiscoming

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.Lifecycle
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteDetailLeg
import com.golink.busiscoming.data.model.RouteDetailStop
import com.golink.busiscoming.data.model.RouteDetailStopRole
import com.golink.busiscoming.data.model.RouteDetailWalkingKind
import com.golink.busiscoming.data.model.RouteDetailWalkingSegment
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import com.golink.busiscoming.ui.main.RouteDetailRuntime
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.CoreMatchers.any
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDetailActivityTest {
    @Test
    fun toolbarNavigateUpFinishesTheFullScreenPage() {
        val scenario = ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery()))
        try {
            onView(withContentDescription(R.string.route_detail_navigate_up)).perform(click())
            Thread.sleep(100)

            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        } finally {
            scenario.close()
        }
    }

    @Test
    fun summaryAndRecoverableMissingMetadataStateSurviveRecreation() {
        val route = BusRouteOption(
            routeName = "N118",
            routeSegments = listOf("N118"),
            priceHkd = 17.8,
            durationMinutes = 13,
            arrivalMinutes = 4,
            transferCount = 0,
            walkingDistanceMeters = 262,
            waitTimeState = WaitTimeState.Available(4)
        )
        val intent = Intent(ApplicationProvider.getApplicationContext(), RouteDetailActivity::class.java)
            .putExtras(RouteDetailLaunchArgs.fromRoute(route).toBundle())

        ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
            onView(withId(R.id.routeDetailToolbar)).check(matches(isDisplayed()))
            onView(withText("N118")).check(matches(isDisplayed()))
            onView(withText(R.string.route_detail_unavailable)).check(matches(isDisplayed()))

            scenario.recreate()

            onView(withText("N118")).check(matches(isDisplayed()))
            onView(withText(R.string.route_detail_unavailable)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun successfulLoadRetryAndExpandedStateRestorationUseTheFlatTimeline() {
        var attempts = 0
        RouteDetailRuntime.repositoryFactory = {
            object : com.golink.busiscoming.data.repository.RouteDetailRepository {
                override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                    attempts += 1
                    if (attempts == 1) error("first request fails")
                    return detail()
                }
            }
        }
        RouteDetailRuntime.etaResolver = { WaitTimeState.Available(6) }
        try {
            ActivityScenario.launch<RouteDetailActivity>(intent(routeWithDetailQuery())).use { scenario ->
                Thread.sleep(250)
                onView(withText(R.string.route_detail_unavailable)).check(matches(isDisplayed()))
                onView(withText(R.string.action_retry)).perform(click())
                Thread.sleep(250)
                onView(withText("上車站")).check(matches(isDisplayed()))
                onView(withText("1 個途經站")).perform(clickClickableParent())
                onView(withText("1 個途經站 · 收起")).check(matches(withEffectiveVisibility(VISIBLE)))

                scenario.recreate()
                Thread.sleep(250)

                onView(withText("1 個途經站 · 收起")).check(matches(withEffectiveVisibility(VISIBLE)))
                onView(withText("即時 · 還有 6 分鐘")).check(matches(withEffectiveVisibility(VISIBLE)))
            }
        } finally {
            RouteDetailRuntime.reset()
        }
    }

    private fun intent(route: BusRouteOption): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), RouteDetailActivity::class.java)
            .putExtras(RouteDetailLaunchArgs.fromRoute(route).toBundle())
    }

    private fun routeWithDetailQuery(): BusRouteOption {
        val leg = P2pRouteLeg("CTB", "N118-TOS-1", "N118", 5, 7, "O", "outbound")
        return BusRouteOption(
            routeName = "N118",
            routeSegments = listOf("N118"),
            priceHkd = 17.8,
            durationMinutes = 13,
            arrivalMinutes = 4,
            transferCount = 0,
            walkingDistanceMeters = 262,
            waitTimeState = WaitTimeState.Available(4),
            firstLegEtaQuery = FirstLegEtaQuery("CTB", leg.routeVariant, leg.route, 5, 7, "O", "outbound", "raw", "0"),
            routeDetailQuery = P2pRouteDetailQuery("raw", "02:04|*|13", "0", "0", P2pRoutePlan("raw", "0", listOf(leg)))
        )
    }

    private fun detail(): RouteDetail {
        val board = stop("上車站", 5, RouteDetailStopRole.BOARDING)
        val via = stop("途經站", 6, RouteDetailStopRole.VIA)
        val alight = stop("下車站", 7, RouteDetailStopRole.ALIGHTING)
        return RouteDetail(
            routeName = "N118",
            priceHkd = 17.8,
            durationMinutes = 13,
            walkingDistanceMeters = 262,
            legs = listOf(RouteDetailLeg("N118", "N118-TOS-1", "長沙灣", board, listOf(via), alight, 17.8, "02:01", "02:04")),
            originWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.ORIGIN, 236),
            destinationWalking = RouteDetailWalkingSegment(RouteDetailWalkingKind.DESTINATION, 26),
            plannedDepartureTime = "01:51",
            plannedArrivalTime = "02:04",
            originName = "起點",
            destinationName = "終點"
        )
    }

    private fun stop(name: String, sequence: Int, role: RouteDetailStopRole) = RouteDetailStop(
        name, name, sequence.toString(), sequence, 22.0, 114.0, "N118-TOS-1", role
    )

    private fun clickClickableParent(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = any(View::class.java)
        override fun getDescription(): String = "click the nearest clickable timeline row"
        override fun perform(uiController: UiController, view: View) {
            var target: View? = view
            while (target != null && !target.isClickable) target = target.parent as? View
            checkNotNull(target).performClick()
            uiController.loopMainThreadUntilIdle()
        }
    }

}
