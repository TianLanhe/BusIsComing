package com.golink.busiscoming

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.RouteDatabaseSnapshot
import com.golink.busiscoming.data.repository.CrossOperatorEtaRuntime
import com.golink.busiscoming.data.repository.GlobalUpdateResult
import com.golink.busiscoming.data.repository.HongKongDataDay
import com.golink.busiscoming.data.repository.RouteDatabaseUpdateCoordinator
import com.golink.busiscoming.ui.main.MainActivity
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteDatabaseSettingsInstrumentedTest {
    @Test
    fun manualSingleFlightSurvivesFragmentRecreationAndPublishesOneResult() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val queue = QueueExecutor()
        val updateCalls = AtomicInteger()
        val completedAt = System.currentTimeMillis() - 60_000L
        val snapshot = RouteDatabaseSnapshot(
            id = "settings-test",
            dataDay = HongKongDataDay.forInstant(System.currentTimeMillis()),
            completedAtMillis = completedAt,
            jointRoutes = emptyList(),
            ctbRoutes = emptyList(),
            variants = emptyList()
        )
        val coordinator = RouteDatabaseUpdateCoordinator(
            activeSnapshot = { snapshot },
            update = {
                updateCalls.incrementAndGet()
                GlobalUpdateResult.Success(changed = true, snapshot.copy(completedAtMillis = completedAt + 1))
            },
            executor = queue
        )
        CrossOperatorEtaRuntime.replaceUpdateCoordinatorForTesting(coordinator).use {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                queue.runAll()
                onView(withId(R.id.navigation_settings)).perform(click())
                instrumentation.waitForIdleSync()

                onView(withId(R.id.settingsRouteDatabaseRow)).perform(click())
                onView(withId(R.id.settingsRouteDatabaseRow)).check(matches(isNotEnabled()))
                scenario.recreate()
                instrumentation.waitForIdleSync()
                onView(withId(R.id.settingsRouteDatabaseRow)).check(matches(isNotEnabled()))

                queue.runAll()
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val row = activity.findViewById<View>(R.id.settingsRouteDatabaseRow)
                    val summary = activity.findViewById<TextView>(R.id.settingsRouteDatabaseSummary)
                    assertTrue(row.isEnabled)
                    assertTrue(row.contentDescription.toString().contains(summary.text))
                    assertTrue(summary.text.toString().contains(activity.getString(R.string.route_database_status_updated).substringBefore('%')))
                }
                assertTrue(updateCalls.get() == 1)
            }
        }
    }

    private class QueueExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) {
            tasks += command
        }
        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
