package com.golink.busiscoming

import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.RouteConfigDbHelper
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.TransitCodeEntryPoint
import com.golink.busiscoming.ui.main.TransitCodePaymentLaunchAction
import com.golink.busiscoming.ui.main.TransitCodePaymentLaunchOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitCodePaymentLauncherInstrumentedTest {
    @Before
    fun clearRoutes() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @Test
    fun explicitTransitCodeActionUsesFormalLauncherAndKeepsDisplayedRouteResults() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val paymentLauncher = RecordingPaymentLaunchAction()
            lateinit var before: ResultSnapshot
            scenario.onActivity { activity ->
                setPaymentLauncher(activity, paymentLauncher)
                prepareResults(activity, routes("保留", 4))
                before = snapshot(activity)
            }

            scenario.onActivity { activity ->
                invoke(
                    activity,
                    "consumeTransitCodeIntent",
                    arrayOf(Intent::class.java),
                    TransitCodeEntryPoint.createIntent(activity)
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                assertEquals(1, paymentLauncher.calls)
                assertFalse(rootText(activity).contains("實驗性乘車碼入口"))
                assertEquals(before, snapshot(activity))
            }
        }
    }

    private fun prepareResults(activity: MainActivity, routes: List<BusRouteOption>) {
        activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
        activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
        invoke(activity, "showInitialRoutes", arrayOf(List::class.java), routes)
    }

    private fun routes(prefix: String, count: Int): List<BusRouteOption> {
        return (1..count).map { index ->
            BusRouteOption(
                routeName = "$prefix$index",
                routeSegments = listOf("$index"),
                priceHkd = index.toDouble(),
                durationMinutes = index + 10,
                arrivalMinutes = index,
                transferCount = 0,
                walkingDistanceMeters = index * 10
            )
        }
    }

    private fun snapshot(activity: MainActivity): ResultSnapshot {
        return ResultSnapshot(
            emptyStateVisibility = activity.findViewById<View>(R.id.emptyRouteState).visibility,
            resultSectionVisibility = activity.findViewById<View>(R.id.resultSection).visibility,
            resultListVisibility = activity.findViewById<View>(R.id.resultListContainer).visibility,
            sortControlsVisibility = activity.findViewById<View>(R.id.sortControls).visibility,
            routeCount = activity.findViewById<RecyclerView>(R.id.busRouteList).adapter?.itemCount ?: -1,
            summary = activity.findViewById<TextView>(R.id.resultSummaryText).text.toString(),
            updatedAt = activity.findViewById<TextView>(R.id.resultUpdatedAtText).text.toString()
        )
    }

    private fun invoke(
        target: Any,
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any
    ) {
        target.javaClass.getDeclaredMethod(name, *parameterTypes).apply {
            isAccessible = true
        }.invoke(target, *args)
    }

    private fun setPaymentLauncher(
        activity: MainActivity,
        launcher: RecordingPaymentLaunchAction
    ) {
        activity.javaClass.getDeclaredField("transitCodePaymentLauncher").apply {
            isAccessible = true
        }.set(activity, launcher)
    }

    private fun rootText(activity: MainActivity): String {
        return collectText(activity.window.decorView.rootView).joinToString("\n")
    }

    private fun collectText(view: View): List<String> {
        return when (view) {
            is TextView -> listOf(view.text.toString())
            is android.view.ViewGroup -> {
                (0 until view.childCount).flatMap { index -> collectText(view.getChildAt(index)) }
            }
            else -> emptyList()
        }
    }

    private data class ResultSnapshot(
        val emptyStateVisibility: Int,
        val resultSectionVisibility: Int,
        val resultListVisibility: Int,
        val sortControlsVisibility: Int,
        val routeCount: Int,
        val summary: String,
        val updatedAt: String
    )

    private class RecordingPaymentLaunchAction : TransitCodePaymentLaunchAction {
        var calls: Int = 0

        override fun launchTransitCode(): TransitCodePaymentLaunchOutcome {
            calls += 1
            return TransitCodePaymentLaunchOutcome(
                started = true,
                startedTarget = null,
                attempts = emptyList(),
                shouldShowFailureToast = false
            )
        }
    }
}
