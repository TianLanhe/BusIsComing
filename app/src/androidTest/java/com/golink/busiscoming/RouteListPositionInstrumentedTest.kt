package com.golink.busiscoming

import android.os.Build

import android.os.LocaleList

import android.app.LocaleManager

import androidx.appcompat.app.AppCompatDelegate

import com.golink.busiscoming.data.model.RouteCardStopPreview

import com.golink.busiscoming.data.model.WalkingDistanceDisplayState

import com.golink.busiscoming.data.model.WaitTimeState

import com.golink.busiscoming.data.model.AppThemeMode

import com.golink.busiscoming.data.localization.AppLanguageChoice

import com.golink.busiscoming.data.local.AppThemePreferenceStore

import com.golink.busiscoming.data.local.AppLanguageRepository

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.ui.main.RouteQueryState
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.SearchFragment
import com.google.android.material.appbar.AppBarLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteListPositionInstrumentedTest {
    @Before fun isolatePositionTestsFromAutomaticOriginLookup() {
        SearchFragment.currentPlaceRequestOverride = { _, callback ->
            callback(com.golink.busiscoming.data.location.CurrentPlaceSelectionResult.Failure)
        }
        SearchFragment.currentLocationSnapshotRequestOverride = { it(null) }
    }

    @After fun restoreSources() = SearchFragment.resetTestDependencies()

    @Test fun frequentSortingKeepsIndexAndPartialExposure() = verifySorting(false)
    @Test fun searchSortingKeepsIndexAndPartialExposure() = verifySorting(true)

    @Test fun manualRefreshUsesLatestScrollAndFailureNeverRestoresRequestStart() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var list: RecyclerView
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
                activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
                activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
                invoke(activity, "showInitialRoutes", routes())
                list = activity.findViewById(R.id.busRouteList)
                activity.findViewById<AppBarLayout>(R.id.frequentAppBar).setExpanded(false, false)
            }
            settle()
            for (failure in listOf(false, true)) {
                scenario.onActivity { activity ->
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(5, -10)
                    invoke(activity, "showRefreshLoadingState", 51)
                }
                settle()
                scenario.onActivity {
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(12, -25)
                }
                settle()
                scenario.onActivity { activity ->
                    if (failure) invoke(activity, "handleRefreshFailure", 51) else {
                        field<RouteQueryState>(activity, "routeQueryState").replaceInitial(routes().reversed(), true)
                        invoke(activity, "handleRefreshSuccess", 51, routes().reversed())
                    }
                }
                settle()
                scenario.onActivity { assertPosition(list, 12, -25) }
            }
        }
    }

    private fun verifySorting(search: Boolean, fields: List<SortField> = SortField.entries) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var owner: Any
            lateinit var list: RecyclerView
            scenario.onActivity { activity ->
                if (search) {
                    activity.findViewById<View>(R.id.navigation_search).performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    owner = activity.supportFragmentManager.fragments.filterIsInstance<SearchFragment>().single()
                    invoke(owner, "displayInitialResults", routes(), false,
                        Place("起點", 22.3, 114.1), Place("終點", 22.4, 114.2), true, 1)
                    list = field(owner, "resultList")
                } else {
                    activity.findViewById<View>(R.id.navigation_frequent_routes).performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    owner = activity
                    activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
                    activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
                    activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
                    invoke(activity, "showInitialRoutes", routes())
                    list = activity.findViewById(R.id.busRouteList)
                    activity.findViewById<AppBarLayout>(R.id.frequentAppBar).setExpanded(false, false)
                }
            }
            settle()
            for (sort in fields) repeat(2) {
                scenario.onActivity {
                    (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(8, -20)
                }
                settle()
                var appBarTop = 0
                scenario.onActivity { activity ->
                    assertPosition(list, 8, -20)
                    appBarTop = activity.findViewById<View>(R.id.frequentAppBar).top
                    invoke(owner, "sortBy", sort)
                }
                settle()
                scenario.onActivity { activity ->
                    assertPosition(list, 8, -20)
                    assertEquals(appBarTop, activity.findViewById<View>(R.id.frequentAppBar).top)
                }
            }
        }
    }

    @Test fun localeThemeMatrixKeepsBothListPositions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        try {
            for ((language, tag) in listOf(AppLanguageChoice.TRADITIONAL_CHINESE to "zh-Hant-HK", AppLanguageChoice.SIMPLIFIED_CHINESE to "zh-Hans", AppLanguageChoice.ENGLISH to "en")) {
                AppLanguageRepository(context).setChoice(language)
                if (Build.VERSION.SDK_INT >= 33) {
                    context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
                }
                for (mode in listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)) {
                    AppThemePreferenceStore(context).setMode(mode)
                    instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode.nightMode) }
                    // 等待語言／主題配置通知收束後才持有本輪 Activity 的 View。
                    instrumentation.waitForIdleSync()
                    Thread.sleep(800)
                    verifySorting(false, listOf(SortField.ROUTE))
                    verifySorting(true, listOf(SortField.ROUTE))
                }
            }
        } finally {
            AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
            if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("zh-Hant-HK")
            AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        }
    }

    @Test fun progressiveAndAutomaticUpdatesKeepBothListPositions() {
        for (search in listOf(false, true)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var owner: Any
                lateinit var list: RecyclerView
                scenario.onActivity { activity ->
                    if (search) {
                        activity.findViewById<View>(R.id.navigation_search).performClick()
                        activity.supportFragmentManager.executePendingTransactions()
                        owner = activity.supportFragmentManager.fragments.filterIsInstance<SearchFragment>().single()
                        invoke(owner, "displayInitialResults", routes(), false, Place("起點", 22.3, 114.1), Place("終點", 22.4, 114.2), true, 1)
                        list = field(owner, "resultList")
                    } else {
                        owner = activity
                        activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
                        activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
                        activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
                        invoke(activity, "showInitialRoutes", routes())
                        list = activity.findViewById(R.id.busRouteList)
                        activity.findViewById<AppBarLayout>(R.id.frequentAppBar).setExpanded(false, false)
                    }
                }
                settle()
                for (sort in listOf(SortField.ARRIVAL, SortField.WALKING_DISTANCE)) {
                    scenario.onActivity { invoke(owner, "sortBy", sort) }
                    settle()
                    scenario.onActivity { (list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(8, -20) }
                    settle()
                    val updated = routes().mapIndexed { index, route -> route.copy(
                        waitTimeState = WaitTimeState.Available(index),
                        walkingDistanceDisplayState = WalkingDistanceDisplayState.CsdiSuccess(index + 1),
                        stopPreview = RouteCardStopPreview("很長的上車站名稱 $index", "很長的下車站名稱 $index")
                    ) }
                    scenario.onActivity {
                        if (search) invoke(owner, "applyProgressiveRouteSnapshot", updated) else {
                            field<RouteQueryState>(owner, "routeQueryState").replaceProgressiveSnapshot(updated)
                            invoke(owner, "renderProjectedResultsPreservingViewport")
                        }
                    }
                    settle()
                    scenario.onActivity { assertPosition(list, 8, -20) }
                    scenario.onActivity {
                        if (search) invoke(owner, "displayAutomaticResults", routes(), Place("起點", 22.3, 114.1), Place("終點", 22.4, 114.2)) else {
                            field<RouteQueryState>(owner, "routeQueryState").complete(routes(), true, System.currentTimeMillis())
                            invoke(owner, "renderProjectedResultsPreservingViewport")
                        }
                    }
                    settle()
                    scenario.onActivity { assertPosition(list, 8, -20) }
                }
            }
        }
    }

    private fun assertPosition(list: RecyclerView, position: Int, offset: Int) {
        val manager = list.layoutManager as LinearLayoutManager
        val parents = generateSequence(list as View) { it.parent as? View }
            .joinToString { "${it.id}:${it.visibility}:${it.width}x${it.height}" }
        assertEquals("第一可見序號；shown=${list.isShown}, attached=${list.isAttachedToWindow}, items=${list.adapter?.itemCount}, parents=$parents", position, manager.findFirstVisibleItemPosition())
        val view = requireNotNull(manager.findViewByPosition(position))
        assertEquals("相對內容頂部偏移", offset, manager.getDecoratedTop(view) - list.paddingTop)
    }

    private fun routes() = (0..35).map { index ->
        BusRouteOption(routeName = "R${35 - index}", routeSegments = listOf("R${35 - index}"),
            priceHkd = (35 - index).toDouble(), durationMinutes = index + 10,
            arrivalMinutes = 35 - index, transferCount = 0, walkingDistanceMeters = 350 - index,
            resultId = "viewport-$index")
    }

    private fun invoke(owner: Any, name: String, vararg args: Any?) {
        owner.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }
            .apply { isAccessible = true }.invoke(owner, *args)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner) as T

    private fun settle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(650)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}
