package com.example.busiscoming

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.UiController
import com.example.busiscoming.ui.edit.RouteEditActivity
import com.example.busiscoming.ui.main.MainActivity
import org.hamcrest.Matcher
import org.hamcrest.Matchers.any
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteEditCandidateBackInstrumentedTest {
    @Test
    fun firstBackClosesCandidatesAndSecondBackReturnsToMainPage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.startActivity(prefilledRouteEditIntent(activity))
            }
            onView(withId(R.id.routeEditTitle)).check(matches(isDisplayed()))
            onView(withId(R.id.originCandidateList)).perform(setVisible())

            pressBack()
            onView(withId(R.id.originCandidateList)).check(matches(withEffectiveVisibility(GONE)))
            onView(withId(R.id.routeEditTitle)).check(matches(isDisplayed()))

            pressBack()
            onView(withId(R.id.mainRoot)).check(matches(isDisplayed()))
        }
    }

    private fun setVisible(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = any(View::class.java)
            override fun getDescription(): String = "set candidate list visible"

            override fun perform(uiController: UiController, view: View) {
                view.visibility = View.VISIBLE
                uiController.loopMainThreadUntilIdle()
            }
        }
    }

    private fun prefilledRouteEditIntent(activity: MainActivity): Intent {
        return Intent(activity, RouteEditActivity::class.java).apply {
            putExtra(RouteEditActivity.EXTRA_PREFILL_NAME, "測試路線")
            putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_NAME, "測試起點")
            putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_LATITUDE, 22.3)
            putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_LONGITUDE, 114.2)
            putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_NAME, "測試終點")
            putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_LATITUDE, 22.4)
            putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_LONGITUDE, 114.3)
        }
    }
}
