package com.golink.busiscoming

import android.content.Intent
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.location.AndroidGoogleServiceCapabilityProbe
import com.golink.busiscoming.data.location.GoogleServiceCapability
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.update.AndroidPlayPackageProbe
import com.golink.busiscoming.data.update.PlayStoreAvailability
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.RouteDetailActivity
import com.golink.busiscoming.ui.main.RouteDetailLaunchArgs
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 由主機在任務持有的 Play AVD 上分別停用／恢復套件，測試不偽造探測結果。 */
@RunWith(AndroidJUnit4::class)
class GoogleCapabilityDeviceInstrumentedTest {
    @Test fun nativeLocationHonoursDevicePermissionsAndSwitch() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.containsKey("expectedNativeResult"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(GoogleServiceCapability.UNAVAILABLE, AndroidGoogleServiceCapabilityProbe(context).detect())
        val done = java.util.concurrent.CountDownLatch(1)
        var result: com.golink.busiscoming.data.location.CurrentLocationResult? = null
        lateinit var coordinator: com.golink.busiscoming.data.location.CurrentLocationCoordinator
        instrumentation.runOnMainSync {
            coordinator = com.golink.busiscoming.data.location.CurrentLocationCoordinator(context)
            coordinator.getCurrentLocation { result = it; done.countDown() }
        }
        try {
            assertTrue(done.await(4, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(args.getString("expectedNativeResult"), result?.javaClass?.simpleName)
        } finally { instrumentation.runOnMainSync { coordinator.close() } }
    }

    @Test fun realPackageStatesRemainIndependentAndPagesCanLaunch() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.containsKey("expectedGms"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gms = GoogleServiceCapability.valueOf(args.getString("expectedGms")!!)
        val store = PlayStoreAvailability.valueOf(args.getString("expectedStore")!!)
        assertEquals(gms, AndroidGoogleServiceCapabilityProbe(context).detect())
        assertEquals(store, AndroidPlayPackageProbe(context).availability())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertNotNull(it.findViewById<View>(R.id.navigation_search)) }
        }
        val route = BusRouteOption("1", listOf("1"), 5.0, 20, 4, 0, 100)
        val intent = Intent(context, RouteDetailActivity::class.java).putExtras(
            RouteDetailLaunchArgs.fromRoute(route, Place("起點", 22.3, 114.1), Place("終點", 22.4, 114.2)).toBundle())
        ActivityScenario.launch<RouteDetailActivity>(intent).use { scenario ->
            scenario.onActivity {
                if (gms == GoogleServiceCapability.UNAVAILABLE) {
                    assertNull(it.findViewById<View>(R.id.routeDetailMap))
                    assertEquals(View.GONE, it.findViewById<View>(R.id.routeDetailMapControls).visibility)
                } else if (store == PlayStoreAvailability.AVAILABLE) {
                    assertNotNull(it.findViewById<View>(R.id.routeDetailMap))
                }
            }
        }
    }
}
