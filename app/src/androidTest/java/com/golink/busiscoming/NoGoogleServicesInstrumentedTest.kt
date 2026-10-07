package com.golink.busiscoming

import android.os.Build

import android.os.LocaleList

import android.app.LocaleManager

import androidx.appcompat.app.AppCompatDelegate

import com.golink.busiscoming.data.repository.RouteDetailRepository

import com.golink.busiscoming.data.model.WaitTimeState

import com.golink.busiscoming.data.model.AppThemeMode

import com.golink.busiscoming.data.localization.AppLanguageChoice

import com.golink.busiscoming.data.local.AppThemePreferenceStore

import com.golink.busiscoming.data.local.AppLanguageRepository

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.golink.busiscoming.data.location.*
import com.golink.busiscoming.service.*
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.ui.main.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 只在真正沒有 GMS 的 AOSP 裝置執行，不能用停用商店替代。 */
@RunWith(AndroidJUnit4::class)
class NoGoogleServicesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @get:Rule val permission = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    @After fun reset() {
        RouteDetailRuntime.reset()
        AppLanguageRepository(context).setChoice(AppLanguageChoice.TRADITIONAL_CHINESE)
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("zh-Hant-HK")
        AppThemePreferenceStore(context).setMode(AppThemeMode.SYSTEM)
        instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
    }

    @Test fun coldLaunchAndRecreationKeepMainAndTextDetailsUsableWithoutGoogle() {
        assertEquals(GoogleServiceCapability.UNAVAILABLE, AndroidGoogleServiceCapabilityProbe(context).detect())
        var mapCreations = 0
        RouteDetailRuntime.mapViewFactory = { _, _ -> mapCreations++; error("無 GMS 不可建立 MapView") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> assertNotNull(activity.findViewById<View>(R.id.navigation_search)) }
            scenario.recreate()
            scenario.onActivity { activity -> activity.findViewById<View>(R.id.navigation_search).performClick() }
        }
        RouteDetailRuntime.locationHeadingTrackerFactory = { error("文字詳情不得建立 Google heading") }
        val route = BusRouteOption("1", listOf("1"), 5.0, 20, 4, 0, 100, resultId = "no-gms")
        val intent = Intent(context, RouteDetailActivity::class.java)
        intent.putExtras(RouteDetailLaunchArgs.fromRoute(route, Place("起點", 22.3, 114.1), Place("終點", 22.4, 114.2)).toBundle())
        ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
            repeat(2) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(700)
                scenario.onActivity { activity ->
                    assertNull(activity.findViewById<View>(R.id.routeDetailMap))
                    assertEquals(View.GONE, activity.findViewById<View>(R.id.routeDetailMapControls).visibility)
                    assertEquals(BottomSheetBehavior.STATE_EXPANDED,
                        BottomSheetBehavior.from(activity.findViewById<View>(R.id.routeDetailSheet)).state)
                    assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.routeDetailSheetMapError).visibility)
                }
                if (it == 0) scenario.recreate()
            }
        }
        assertEquals(0, mapCreations)
    }

    @Test fun textDetailsFitThreeLanguagesAndBothThemes() {
        assertEquals(GoogleServiceCapability.UNAVAILABLE, AndroidGoogleServiceCapabilityProbe(context).detect())
        RouteDetailRuntime.locationHeadingTrackerFactory = { error("文字詳情不得建立 Google heading") }
        RouteDetailRuntime.repositoryFactory = { object : RouteDetailRepository {
            override fun loadRouteDetail(route: BusRouteOption) = DemoScreenshotFixtures.routeDetail()
        } }
        RouteDetailRuntime.etaResolver = { WaitTimeState.Available(6) }
        for ((language, tag) in listOf(AppLanguageChoice.TRADITIONAL_CHINESE to "zh-Hant-HK", AppLanguageChoice.SIMPLIFIED_CHINESE to "zh-Hans", AppLanguageChoice.ENGLISH to "en")) {
            AppLanguageRepository(context).setChoice(language)
            if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
            for (mode in listOf(AppThemeMode.LIGHT, AppThemeMode.DARK)) {
                AppThemePreferenceStore(context).setMode(mode)
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode.nightMode) }
                val intent = Intent(context, RouteDetailActivity::class.java).putExtras(RouteDetailLaunchArgs.fromRoute(
                    DemoScreenshotFixtures.primaryRoute(), Place("起點", 22.3, 114.1), Place("終點", 22.4, 114.2)).toBundle())
                ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
                    instrumentation.waitForIdleSync()
                    SystemClock.sleep(900)
                    scenario.onActivity { activity ->
                        assertNull(activity.findViewById<View>(R.id.routeDetailMap))
                        val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.routeDetailList)
                        assertTrue(list.isShown)
                        assertTrue(list.adapter!!.itemCount > 0)
                        val notice = activity.findViewById<android.widget.TextView>(R.id.routeDetailSheetMapError)
                        assertTrue(notice.isShown)
                        assertEquals(activity.getString(R.string.route_map_unavailable), notice.text.toString())
                        val bounds = android.graphics.Rect()
                        assertTrue(notice.getGlobalVisibleRect(bounds))
                        assertTrue(bounds.left >= 0 && bounds.right <= activity.resources.displayMetrics.widthPixels)
                        val node = notice.createAccessibilityNodeInfo()
                        assertEquals(notice.text.toString(), node.text.toString())
                        node.recycle()
                        assertEquals(BottomSheetBehavior.STATE_EXPANDED, BottomSheetBehavior.from(activity.findViewById<View>(R.id.routeDetailSheet)).state)
                    }
                }
            }
        }
    }

    @Test fun noSpeechEngineReturnsExistingFailureWithoutBlockingThePage() {
        val engines = context.packageManager.queryIntentServices(Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
        org.junit.Assume.assumeTrue("此案例需要沒有 TTS 引擎的裝置", engines.isEmpty())
        instrumentation.runOnMainSync {
            val speech = BusMonitorSpeechController(context)
            try {
                val result = speech.speak("測試")
                assertEquals(BusMonitorSpeechFailureReason.NO_ENGINE, (result as BusMonitorSpeechResult.Failure).reason)
            } finally { speech.release() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertNotNull(it.findViewById<View>(R.id.navigation_search)) }
        }
    }

    @Test fun systemProviderFixCompletesNativeRequest() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("runSystemProviderFix") == "true")
        assertEquals(GoogleServiceCapability.UNAVAILABLE, AndroidGoogleServiceCapabilityProbe(context).detect())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val done = CountDownLatch(1)
            var result: CurrentLocationResult? = null
            lateinit var coordinator: CurrentLocationCoordinator
            scenario.onActivity {
                coordinator = CurrentLocationCoordinator(it)
                coordinator.getCurrentLocation { value -> result = value; done.countDown() }
            }
            try {
                assertTrue(done.await(4, TimeUnit.SECONDS))
                assertTrue("應收到測試供應者經 LocationManager 交付的 fix，實際：$result", result is CurrentLocationResult.Success)
                assertEquals(22.3, (result as CurrentLocationResult.Success).snapshot.latitude, 0.001)
            } finally { instrumentation.runOnMainSync { coordinator.close() } }
        }
    }

    @Test fun nativeLocationCompletesOrFailsWithinThreeSecondsAndOwnerCanCancel() {
        assertEquals(GoogleServiceCapability.UNAVAILABLE, AndroidGoogleServiceCapabilityProbe(context).detect())
        val done = CountDownLatch(1)
        var result: CurrentLocationResult? = null
        lateinit var coordinator: CurrentLocationCoordinator
        val started = SystemClock.elapsedRealtime()
        instrumentation.runOnMainSync {
            coordinator = CurrentLocationCoordinator(context)
            coordinator.getCurrentLocation { result = it; done.countDown() }
        }
        assertTrue(done.await(4, TimeUnit.SECONDS))
        assertTrue(SystemClock.elapsedRealtime() - started < 4_000)
        assertNotNull(result)
        assertFalse(result is CurrentLocationResult.NoPermission)
        instrumentation.runOnMainSync { coordinator.close() }
    }
}
