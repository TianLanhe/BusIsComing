package com.golink.busiscoming

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.*
import com.golink.busiscoming.ui.main.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteViewportBoundaryInstrumentedTest {
    @Test fun shortDividerClampsExposureAndEmptyResultDoesNotResurrectPosition() = withList { scenario, list, submissions ->
        scenario.onActivity { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(8, -200) }
        settle()
        scenario.onActivity {
            val items = items().toMutableList()
            items[8] = UnpinnedDividerItem(31, SortField.ROUTE, SortDirection.ASC, "divider")
            submissions.submit(items)
        }
        settle()
        scenario.onActivity {
            val manager = list.layoutManager as LinearLayoutManager
            assertEquals(8, manager.findFirstVisibleItemPosition())
            val child = manager.findViewByPosition(8)!!
            assertEquals(1 - manager.getDecoratedMeasuredHeight(child), manager.getDecoratedTop(child) - list.paddingTop)
            submissions.submit(emptyList())
        }
        settle()
        scenario.onActivity { submissions.submit(items()) }
        settle()
        scenario.onActivity { assertEquals(0, (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()) }
    }

    @Test fun replacedDiffAndLateAnimationKeepLatestUserPosition() = withList { scenario, list, submissions ->
        scenario.onActivity { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(8, -20) }
        settle()
        scenario.onActivity {
            submissions.submit(items().reversed())
            submissions.submit(items().drop(1) + items().take(1))
            // diff 完成前，新的明確捲動必須優先。
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(12, -30)
        }
        settle()
        scenario.onActivity {
            val manager = list.layoutManager as LinearLayoutManager
            assertEquals(12, manager.findFirstVisibleItemPosition())
            assertEquals(-30, manager.getDecoratedTop(manager.findViewByPosition(12)!!) - list.paddingTop)
        }
        settle()
        scenario.onActivity { assertEquals(12, (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()) }
    }

    @Test fun cancelledOwnerCannotRestoreAfterNavigation() = withList { scenario, list, submissions ->
        scenario.onActivity {
            submissions.submit(items().reversed())
            submissions.cancel()
            (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
        }
        settle()
        scenario.onActivity { assertEquals(0, (list.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()) }
    }

    @Test fun leavingForegroundStillAcceptsLatestData() = withList { scenario, list, submissions ->
        val updated = items().reversed()
        scenario.onActivity {
            submissions.submit(updated)
            submissions.cancel(discardPendingList = false)
        }
        settle()
        scenario.onActivity {
            assertEquals(updated, (list.adapter as BusRouteAdapter).currentList)
        }
    }

    @Test fun ordinaryUpdatesDoNotCancelPinNavigationButOwnerChangesDo() = withList { scenario, _, submissions ->
        scenario.onActivity {
            val isCurrent = submissions.navigationGuard()
            submissions.submit(items().reversed())
            assertTrue(isCurrent())
            submissions.cancel()
            assertFalse(isCurrent())
        }
    }

    @Test fun shortenedResultClampsToAvailableRows() = withList { scenario, list, submissions ->
        scenario.onActivity { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(28, -20) }
        settle()
        scenario.onActivity { submissions.submit(items().take(12)) }
        settle()
        scenario.onActivity {
            val manager = list.layoutManager as LinearLayoutManager
            assertEquals(11, manager.findLastVisibleItemPosition())
            assertTrue(manager.findFirstVisibleItemPosition() in 0..11)
        }
    }

    @Test fun hiddenEmptyListCannotCarryOldPositionIntoNewQuery() = withList { scenario, list, submissions ->
        scenario.onActivity { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(18, -30) }
        settle()
        scenario.onActivity {
            list.visibility = View.GONE
            submissions.submit(emptyList())
            submissions.submit(items().reversed())
            list.visibility = View.VISIBLE
        }
        settle()
        scenario.onActivity {
            val manager = list.layoutManager as LinearLayoutManager
            assertEquals(0, manager.findFirstVisibleItemPosition())
            assertEquals(0, manager.getDecoratedTop(manager.findViewByPosition(0)!!) - list.paddingTop)
        }
    }

    private fun withList(block: (ActivityScenario<MainActivity>, RecyclerView, RouteListSubmissionController) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var list: RecyclerView
            lateinit var submissions: RouteListSubmissionController
            scenario.onActivity { activity ->
                list = RecyclerView(activity).apply {
                    layoutManager = RouteListPositionLayoutManager(activity)
                    setPadding(0, 30, 0, 0)
                }
                val adapter = BusRouteAdapter()
                list.adapter = adapter
                activity.setContentView(list)
                submissions = RouteListSubmissionController(list, adapter)
                submissions.submit(items())
            }
            settle()
            block(scenario, list, submissions)
        }
    }

    private fun items(): List<BusRouteListItem> = SearchRouteItemProjector.project((0..39).map { index ->
        BusRouteOption("R$index", listOf("R$index"), 5.0, 20, 4, 0, 100,
            resultId = "boundary-$index", stopPreview = RouteCardStopPreview("上車站 $index", "下車站 $index"))
    })
    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(650)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
