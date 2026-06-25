# Demo Screenshots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a repeatable instrumentation screenshot flow that generates five synthetic BusIsComing website sample screenshots into `~/Desktop/appmock截图`.

**Architecture:** Keep all synthetic data and screenshot orchestration in `androidTest`, with no production demo entry point and no real Citybus/DATA.GOV.HK calls. A small host script starts or reuses an emulator, runs the targeted instrumentation test, then pulls the generated PNGs from the app sandbox into the desktop output directory.

**Tech Stack:** Kotlin instrumentation tests, AndroidX Test/Espresso, existing XML/AppCompat/Material UI, `UiAutomation.takeScreenshot()`, adb, Gradle.

---

## File Structure

- Create: `app/src/androidTest/java/com/example/busiscoming/DemoScreenshotFixtures.kt`
  - Owns all synthetic routes, route results, ETA rows, route detail stops, and fixed display times.
  - Contains no Android UI code and no network calls.
- Create: `app/src/androidTest/java/com/example/busiscoming/DemoScreenshotInstrumentedTest.kt`
  - Launches existing UI surfaces, injects fixture state through test-only reflection, captures/crops screenshots, posts the synthetic lock-screen notification, and writes PNGs to app-private files and public Pictures via MediaStore.
- Create: `scripts/generate-demo-screenshots.sh`
  - Starts or reuses `Pixel_8_API_36`, forces light mode, runs only `DemoScreenshotInstrumentedTest`, and pulls the five PNGs to `~/Desktop/appmock截图`.
- Modify only if required by compile errors: `app/build.gradle.kts`
  - Do not add UIAutomator unless `UiAutomation` shell and screenshot APIs prove insufficient.

---

### Task 1: Add Synthetic Screenshot Fixtures

**Files:**
- Create: `app/src/androidTest/java/com/example/busiscoming/DemoScreenshotFixtures.kt`
- Test: `./gradlew :app:compileDebugAndroidTestKotlin --no-daemon`

- [x] **Step 1: Create the fixture file**

Create `app/src/androidTest/java/com/example/busiscoming/DemoScreenshotFixtures.kt` with this implementation:

```kotlin
package com.example.busiscoming

import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.EtaArrival
import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.P2pRouteDetailQuery
import com.example.busiscoming.data.model.P2pRouteLeg
import com.example.busiscoming.data.model.P2pRoutePlan
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.model.RouteDetail
import com.example.busiscoming.data.model.RouteDetailLeg
import com.example.busiscoming.data.model.RouteDetailStop
import com.example.busiscoming.data.model.RouteDetailStopRole
import com.example.busiscoming.data.model.WaitTimeState

object DemoScreenshotFixtures {
    const val outputDirectory = "demo-screenshots"
    const val homeFavoritesResults = "home-favorites-results.png"
    const val homeAllRoutesSheet = "home-all-routes-sheet.png"
    const val routeDetailExpanded = "route-detail-expanded.png"
    const val etaArrivalsSheet = "eta-arrivals-sheet.png"
    const val lockscreenMonitor = "lockscreen-monitor.png"

    val allOutputFiles = listOf(
        homeFavoritesResults,
        homeAllRoutesSheet,
        routeDetailExpanded,
        etaArrivalsSheet,
        lockscreenMonitor
    )

    val savedRoutes = listOf(
        routeConfig(1L, "上班", "海庭苑", "景澄站", 22.2861, 114.1912, 22.2916, 114.2046),
        routeConfig(2L, "回家", "東灣碼頭", "松嶺邨", 22.2817, 114.1861, 22.2984, 114.2126),
        routeConfig(3L, "接送", "朗翠閣", "南灣書院", 22.2741, 114.1766, 22.2662, 114.1984),
        routeConfig(4L, "週末", "柏岸廣場", "雲海公園", 22.3034, 114.1792, 22.3128, 114.1935)
    )

    fun routeResults(): List<BusRouteOption> {
        return listOf(
            routeOption(
                routeName = "28X",
                routeSegments = listOf("28X"),
                priceHkd = 7.8,
                durationMinutes = 26,
                walkingDistanceMeters = 180,
                arrivals = listOf(
                    arrival(1, 4, "08:24", "景澄站", "正常班次"),
                    arrival(2, 11, "08:31", "景澄站", "低地台"),
                    arrival(3, 19, "08:39", "景澄站", "正常班次")
                ),
                boarding = "海庭苑平台",
                alighting = "景澄站北",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("28X")
            ),
            routeOption(
                routeName = "71A → 28X",
                routeSegments = listOf("71A", "28X"),
                priceHkd = 9.6,
                durationMinutes = 31,
                walkingDistanceMeters = 240,
                arrivals = listOf(arrival(1, 6, "08:26", "景澄站", "正常班次")),
                boarding = "海庭苑南",
                alighting = "景澄站北",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("71A")
            ),
            routeOption(
                routeName = "16P",
                routeSegments = listOf("16P"),
                priceHkd = 6.4,
                durationMinutes = 34,
                walkingDistanceMeters = 210,
                arrivals = listOf(arrival(1, 9, "08:29", "景澄站", "正常班次")),
                boarding = "翠明街花園",
                alighting = "景澄站南",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("16P")
            ),
            routeOption(
                routeName = "52M → 86",
                routeSegments = listOf("52M", "86"),
                priceHkd = 10.2,
                durationMinutes = 38,
                walkingDistanceMeters = 320,
                arrivals = listOf(arrival(1, 13, "08:33", "景澄站", "正常班次")),
                boarding = "海庭苑平台",
                alighting = "景澄站東",
                routeDetailQuery = detailQuery(),
                firstLegEtaQuery = etaQuery("52M")
            )
        )
    }

    fun primaryRoute(): BusRouteOption = routeResults().first()

    fun routeDetail(): RouteDetail {
        return RouteDetail(
            routeName = "28X → 86",
            priceHkd = 10.2,
            durationMinutes = 34,
            walkingDistanceMeters = 180,
            originWalkingDistanceMeters = 180,
            legs = listOf(
                RouteDetailLeg(
                    route = "28X",
                    routeVariant = "28X-DEMO-1",
                    directionText = "往景澄站方向",
                    boardingStop = stop("海庭苑平台", 1, "28X-DEMO-1", RouteDetailStopRole.BOARDING),
                    viaStops = listOf(
                        stop("翠明街花園", 2, "28X-DEMO-1", RouteDetailStopRole.VIA),
                        stop("雅湖里", 3, "28X-DEMO-1", RouteDetailStopRole.VIA)
                    ),
                    alightingStop = stop("景澄站北", 4, "28X-DEMO-1", RouteDetailStopRole.ALIGHTING)
                ),
                RouteDetailLeg(
                    route = "86",
                    routeVariant = "86-DEMO-1",
                    directionText = "往松嶺邨方向",
                    boardingStop = stop("景澄站南", 1, "86-DEMO-1", RouteDetailStopRole.BOARDING),
                    viaStops = listOf(
                        stop("雲海路口", 2, "86-DEMO-1", RouteDetailStopRole.VIA),
                        stop("柏岸廣場", 3, "86-DEMO-1", RouteDetailStopRole.VIA)
                    ),
                    alightingStop = stop("松嶺邨總站", 4, "86-DEMO-1", RouteDetailStopRole.ALIGHTING)
                )
            )
        )
    }

    fun lockscreenBodyText(): String {
        return "剩餘 2 分鐘 · 車 6 分鐘到 · 步行 4 分鐘 · 下一班 18 分鐘 · 08:20 更新"
    }

    private fun routeConfig(
        id: Long,
        name: String,
        originName: String,
        destinationName: String,
        originLatitude: Double,
        originLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): RouteConfig {
        return RouteConfig(
            id = id,
            name = name,
            origin = Place(originName, originLatitude, originLongitude),
            destination = Place(destinationName, destinationLatitude, destinationLongitude),
            usageCount = (10 - id).toInt(),
            lastUsedAt = 1_800_000_000_000L - id
        )
    }

    private fun routeOption(
        routeName: String,
        routeSegments: List<String>,
        priceHkd: Double,
        durationMinutes: Int,
        walkingDistanceMeters: Int,
        arrivals: List<EtaArrival>,
        boarding: String,
        alighting: String,
        routeDetailQuery: P2pRouteDetailQuery,
        firstLegEtaQuery: FirstLegEtaQuery
    ): BusRouteOption {
        return BusRouteOption(
            routeName = routeName,
            routeSegments = routeSegments,
            priceHkd = priceHkd,
            durationMinutes = durationMinutes,
            arrivalMinutes = arrivals.first().minutes,
            transferCount = routeSegments.size - 1,
            walkingDistanceMeters = walkingDistanceMeters,
            waitTimeState = WaitTimeState.Available(arrivals),
            firstLegEtaQuery = firstLegEtaQuery,
            routeDetailQuery = routeDetailQuery,
            stopPreview = RouteCardStopPreview(boarding, alighting)
        )
    }

    private fun arrival(
        sequence: Int,
        minutes: Int,
        arrivalTimeText: String,
        destination: String,
        remark: String
    ): EtaArrival {
        return EtaArrival(
            sequence = sequence,
            minutes = minutes,
            arrivalTimeText = arrivalTimeText,
            destination = destination,
            remark = remark,
            dataTimestampMillis = 1_780_000_000_000L
        )
    }

    private fun etaQuery(route: String): FirstLegEtaQuery {
        return FirstLegEtaQuery(
            company = "CTB",
            routeVariant = "$route-DEMO-1",
            route = route,
            boardingSeq = 1,
            alightingSeq = 4,
            bound = "O",
            directionPath = "outbound",
            rawInfo = "demo-$route",
            lang = "0"
        )
    }

    private fun detailQuery(): P2pRouteDetailQuery {
        return P2pRouteDetailQuery(
            rawInfo = "demo-detail",
            generalInfo = "",
            listId = "1",
            lang = "0",
            plan = P2pRoutePlan(
                rawInfo = "demo-detail",
                lang = "0",
                legs = listOf(
                    P2pRouteLeg("CTB", "28X-DEMO-1", "28X", 1, 4, "O", "outbound"),
                    P2pRouteLeg("CTB", "86-DEMO-1", "86", 1, 4, "O", "outbound")
                )
            )
        )
    }

    private fun stop(
        name: String,
        sequence: Int,
        routeVariant: String,
        role: RouteDetailStopRole
    ): RouteDetailStop {
        return RouteDetailStop(
            rawName = name,
            displayName = name,
            stopId = "$routeVariant-$sequence",
            sequence = sequence,
            latitude = 22.28 + sequence / 10_000.0,
            longitude = 114.18 + sequence / 10_000.0,
            routeVariant = routeVariant,
            role = role
        )
    }
}
```

- [x] **Step 2: Compile androidTest Kotlin**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

Expected: the command reaches `BUILD SUCCESSFUL`. If it fails on constructor signatures, inspect the referenced model file and update the fixture to match the current data class signature before continuing.

- [x] **Step 3: Commit the fixture**

Run:

```bash
git add app/src/androidTest/java/com/example/busiscoming/DemoScreenshotFixtures.kt
git commit -m "test: add demo screenshot fixtures"
```

Expected: a commit containing only `DemoScreenshotFixtures.kt`.

---

### Task 2: Add the Instrumentation Screenshot Test

**Files:**
- Create: `app/src/androidTest/java/com/example/busiscoming/DemoScreenshotInstrumentedTest.kt`
- Test: `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.busiscoming.DemoScreenshotInstrumentedTest --no-daemon`

- [x] **Step 1: Create the screenshot test file**

Create `app/src/androidTest/java/com/example/busiscoming/DemoScreenshotInstrumentedTest.kt` with this structure and fill the helper methods exactly as shown:

```kotlin
package com.example.busiscoming

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.WindowInsets
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.busiscoming.data.local.RouteConfigDbHelper
import com.example.busiscoming.data.model.BusMonitorNotificationFormatter
import com.example.busiscoming.data.model.BusMonitorStatus
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.RouteDetail
import com.example.busiscoming.data.repository.RouteDetailRepository
import com.example.busiscoming.service.BusMonitorNotificationContract
import com.example.busiscoming.ui.main.EtaArrivalsBottomSheet
import com.example.busiscoming.ui.main.MainActivity
import com.example.busiscoming.ui.main.RouteDetailBottomSheet
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoScreenshotInstrumentedTest {
    private companion object {
        const val monitorNotificationId = 8201
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext: Context = instrumentation.targetContext
    private val lastActivityRoot = AtomicReference<View>()
    private val outputDir: File by lazy {
        File(targetContext.filesDir, DemoScreenshotFixtures.outputDirectory)
    }

    @Before
    fun prepareDeviceAndOutput() {
        targetContext.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        executeShell("cmd uimode night no")
        executeShell("settings put system font_scale 1.0")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            executeShell("pm grant ${targetContext.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        }
        clearMonitorNotification()
        wakeAndDismissKeyguard()
    }

    @Test
    fun generateWebsiteSampleScreenshots() {
        captureHomeAndRoutePicker()
        captureRouteDetail()
        captureEtaArrivals()
        captureLockscreenMonitor()

        DemoScreenshotFixtures.allOutputFiles.forEach { name ->
            val file = File(outputDir, name)
            assertTrue("Missing screenshot $name", file.isFile)
            assertTrue("Screenshot $name is empty", file.length() > 16_000L)
        }
    }

    private fun captureHomeAndRoutePicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                lastActivityRoot.set(activity.window.decorView.rootView)
                configureHome(activity)
            }
            instrumentation.waitForIdleSync()
            saveAppAreaScreenshot(scenario, DemoScreenshotFixtures.homeFavoritesResults)

            scenario.onActivity { activity ->
                lastActivityRoot.set(activity.window.decorView.rootView)
                invoke(activity, "showRoutePicker", emptyArray())
            }
            waitUntilText("查詢臨時起點和終點")
            saveAppAreaScreenshot(scenario, DemoScreenshotFixtures.homeAllRoutesSheet)
        }
    }

    private fun captureRouteDetail() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val sheetRef = AtomicReference<RouteDetailBottomSheet>()
            scenario.onActivity { activity ->
                lastActivityRoot.set(activity.window.decorView.rootView)
                val sheet = RouteDetailBottomSheet(
                    activity = activity,
                    repository = object : RouteDetailRepository {
                        override fun loadRouteDetail(route: BusRouteOption): RouteDetail {
                            return DemoScreenshotFixtures.routeDetail()
                        }
                    }
                )
                sheetRef.set(sheet)
                sheet.show(DemoScreenshotFixtures.primaryRoute())
            }
            waitUntilText("途經 2 個站")
            scenario.onActivity { activity ->
                lastActivityRoot.set(activity.window.decorView.rootView)
                clickAllTextViewsContaining(activity.window.decorView.rootView, "途經 2 個站")
            }
            instrumentation.waitForIdleSync()
            saveAppAreaScreenshot(scenario, DemoScreenshotFixtures.routeDetailExpanded)
            scenario.onActivity {
                sheetRef.get()?.dispose()
            }
        }
    }

    private fun captureEtaArrivals() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val sheetRef = AtomicReference<EtaArrivalsBottomSheet>()
            scenario.onActivity { activity ->
                lastActivityRoot.set(activity.window.decorView.rootView)
                val sheet = EtaArrivalsBottomSheet(activity)
                sheetRef.set(sheet)
                sheet.show(DemoScreenshotFixtures.primaryRoute())
            }
            waitUntilText("第3班")
            saveAppAreaScreenshot(scenario, DemoScreenshotFixtures.etaArrivalsSheet)
            scenario.onActivity {
                sheetRef.get()?.dispose()
            }
        }
    }

    private fun captureLockscreenMonitor() {
        postMonitorNotification()
        executeShell("input keyevent 223")
        SystemClock.sleep(600L)
        executeShell("input keyevent 224")
        SystemClock.sleep(1_200L)
        saveFullScreenshot(DemoScreenshotFixtures.lockscreenMonitor)
        wakeAndDismissKeyguard()
        clearMonitorNotification()
    }

    private fun configureHome(activity: MainActivity) {
        setField(activity, "routeConfigs", DemoScreenshotFixtures.savedRoutes)
        setField(activity, "selectedRoute", DemoScreenshotFixtures.savedRoutes.first())
        setField(activity, "nearbySelectedRouteId", DemoScreenshotFixtures.savedRoutes.first().id)
        activity.findViewById<View>(R.id.emptyRouteState).visibility = View.GONE
        activity.findViewById<View>(R.id.queryControls).visibility = View.VISIBLE
        activity.findViewById<View>(R.id.resultSection).visibility = View.VISIBLE
        invoke(activity, "renderRouteShortcuts", emptyArray())
        invoke(
            activity,
            "showInitialRoutes",
            arrayOf(List::class.java),
            DemoScreenshotFixtures.routeResults()
        )
    }

    private fun saveAppAreaScreenshot(
        scenario: ActivityScenario<MainActivity>,
        name: String
    ) {
        val bounds = appAreaBounds(scenario)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "UiAutomation returned no screenshot"
        }
        val crop = safeCrop(bitmap, bounds)
        writePng(crop, name)
        if (crop !== bitmap) crop.recycle()
        bitmap.recycle()
    }

    private fun saveFullScreenshot(name: String) {
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "UiAutomation returned no screenshot"
        }
        writePng(bitmap, name)
        bitmap.recycle()
    }

    private fun appAreaBounds(scenario: ActivityScenario<MainActivity>): Rect {
        val ref = AtomicReference<Rect>()
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val width = decor.rootView.width
            val height = decor.rootView.height
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insets = decor.rootWindowInsets
                    ?.getInsets(WindowInsets.Type.systemBars())
                Rect(
                    insets?.left ?: 0,
                    insets?.top ?: 0,
                    width - (insets?.right ?: 0),
                    height - (insets?.bottom ?: 0)
                )
            } else {
                Rect().also(decor::getWindowVisibleDisplayFrame)
            }
            ref.set(bounds)
        }
        return requireNotNull(ref.get()) { "Activity bounds were not captured" }
    }

    private fun safeCrop(bitmap: Bitmap, requested: Rect): Bitmap {
        val left = requested.left.coerceIn(0, bitmap.width - 1)
        val top = requested.top.coerceIn(0, bitmap.height - 1)
        val right = requested.right.coerceIn(left + 1, bitmap.width)
        val bottom = requested.bottom.coerceIn(top + 1, bitmap.height)
        if (left == 0 && top == 0 && right == bitmap.width && bottom == bitmap.height) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun writePng(bitmap: Bitmap, name: String) {
        val file = File(outputDir, name)
        FileOutputStream(file).use { output ->
            val written = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            assertTrue("Failed to write $name", written)
        }
    }

    private fun postMonitorNotification() {
        val manager = targetContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    BusMonitorNotificationContract.ALERT_CHANNEL_ID,
                    "巴士出門提醒",
                    BusMonitorNotificationContract.ALERT_CHANNEL_IMPORTANCE
                ).apply {
                    lockscreenVisibility = BusMonitorNotificationContract.LOCKSCREEN_VISIBILITY
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
        val title = BusMonitorNotificationFormatter.title("28X", BusMonitorStatus.LEAVE_NOW)
        val body = DemoScreenshotFixtures.lockscreenBodyText()
        val notification = NotificationCompat.Builder(
            targetContext,
            BusMonitorNotificationContract.ALERT_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicNotification(title, body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        manager.notify(monitorNotificationId, notification)
    }

    private fun publicNotification(title: String, body: String): Notification {
        return NotificationCompat.Builder(
            targetContext,
            BusMonitorNotificationContract.ALERT_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun clearMonitorNotification() {
        targetContext.getSystemService(NotificationManager::class.java)
            .cancel(monitorNotificationId)
    }

    private fun wakeAndDismissKeyguard() {
        executeShell("input keyevent 224")
        executeShell("wm dismiss-keyguard")
        executeShell("input keyevent 82")
        SystemClock.sleep(500L)
    }

    private fun waitUntilText(text: String, timeoutMillis: Long = 5_000L) {
        waitUntil(timeoutMillis) {
            collectText(lastActivityRoot.get()).any { it.contains(text) }
        }
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(100L)
        }
        assertTrue("Timed out waiting for condition", condition())
    }

    private fun collectText(view: View?): List<String> {
        if (view == null) return emptyList()
        return when (view) {
            is TextView -> listOf(view.text.toString())
            is android.view.ViewGroup -> {
                (0 until view.childCount).flatMap { index ->
                    collectText(view.getChildAt(index))
                }
            }
            else -> emptyList()
        }
    }

    private fun clickAllTextViewsContaining(root: View, text: String) {
        val matches = mutableListOf<TextView>()
        collectTextViews(root, matches)
        matches.filter { it.text?.contains(text) == true }.forEach { it.performClick() }
    }

    private fun collectTextViews(view: View, output: MutableList<TextView>) {
        if (view is TextView) output += view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                collectTextViews(view.getChildAt(index), output)
            }
        }
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

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
        }.set(target, value)
    }

    private fun executeShell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                input.readBytes()
            }
        }
    }
}
```

- [x] **Step 2: Compile androidTest Kotlin**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Run the screenshot test on an already booted emulator**

Run:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.busiscoming.DemoScreenshotInstrumentedTest \
  --no-daemon
```

Expected: the single instrumentation class runs and passes. If the lock-screen capture is blank, change the lock sequence in `captureLockscreenMonitor()` to:

```kotlin
executeShell("input keyevent 224")
executeShell("cmd statusbar expand-notifications")
SystemClock.sleep(1_200L)
saveFullScreenshot(DemoScreenshotFixtures.lockscreenMonitor)
```

This still captures the posted monitor notification without using real data.

- [x] **Step 4: Commit the screenshot test**

Run:

```bash
git add app/src/androidTest/java/com/example/busiscoming/DemoScreenshotInstrumentedTest.kt
git commit -m "test: add demo screenshot instrumentation"
```

Expected: a commit containing only the instrumentation screenshot test.

---

### Task 3: Add the Host Pull Script

**Files:**
- Create: `scripts/generate-demo-screenshots.sh`
- Test: `bash scripts/generate-demo-screenshots.sh`

- [x] **Step 1: Create the script**

Create `scripts/generate-demo-screenshots.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.example.busiscoming"
AVD_NAME="${AVD_NAME:-Pixel_8_API_36}"
OUT_DIR="${HOME}/Desktop/appmock截图"
DEVICE_DIR="files/demo-screenshots"
FILES=(
  "home-favorites-results.png"
  "home-all-routes-sheet.png"
  "route-detail-expanded.png"
  "eta-arrivals-sheet.png"
  "lockscreen-monitor.png"
)

cd "${ROOT_DIR}"
mkdir -p "${OUT_DIR}"
rm -f "${OUT_DIR}"/*.png

if ! adb get-state >/dev/null 2>&1; then
  emulator -avd "${AVD_NAME}" -no-snapshot-load >/tmp/busiscoming-demo-emulator.log 2>&1 &
  adb wait-for-device
fi

until [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; do
  sleep 2
done

adb shell input keyevent 224 >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell cmd uimode night no >/dev/null 2>&1 || true
adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.busiscoming.DemoScreenshotInstrumentedTest \
  --no-daemon

for file in "${FILES[@]}"; do
  adb exec-out run-as "${APP_ID}" cat "${DEVICE_DIR}/${file}" > "${OUT_DIR}/${file}"
  test -s "${OUT_DIR}/${file}"
done

printf 'Generated demo screenshots:\n'
for file in "${FILES[@]}"; do
  printf '  %s/%s\n' "${OUT_DIR}" "${file}"
done
```

- [x] **Step 2: Make the script executable**

Run:

```bash
chmod +x scripts/generate-demo-screenshots.sh
```

Expected: `ls -l scripts/generate-demo-screenshots.sh` shows executable bits, for example `-rwxr-xr-x`.

- [x] **Step 3: Run the script**

Run:

```bash
bash scripts/generate-demo-screenshots.sh
```

Expected:

- The emulator boots or an existing emulator is reused.
- The targeted instrumentation class passes.
- The script prints five generated paths under `~/Desktop/appmock截图`.

- [x] **Step 4: Commit the script**

Run:

```bash
git add scripts/generate-demo-screenshots.sh
git commit -m "test: add demo screenshot script"
```

Expected: a commit containing only `scripts/generate-demo-screenshots.sh`.

---

### Task 4: Validate the Generated PNGs

**Files:**
- Read: `~/Desktop/appmock截图/*.png`
- Test: local image inspection and shell checks

- [x] **Step 1: Check file count and sizes**

Run:

```bash
find "${HOME}/Desktop/appmock截图" -maxdepth 1 -name '*.png' -print | sort
du -h "${HOME}/Desktop/appmock截图"/*.png
```

Expected: exactly these five files appear and each file is larger than 16 KB:

```text
eta-arrivals-sheet.png
home-all-routes-sheet.png
home-favorites-results.png
lockscreen-monitor.png
route-detail-expanded.png
```

- [x] **Step 2: Inspect image dimensions**

Run:

```bash
sips -g pixelWidth -g pixelHeight "${HOME}/Desktop/appmock截图"/*.png
```

Expected:

- `lockscreen-monitor.png` uses the full emulator screen height.
- The other four PNGs are shorter than the full screen because system bars are cropped.
- Width is the emulator screen width for all five files.

- [x] **Step 3: Visual review**

Open the images from `~/Desktop/appmock截图` and verify:

- `home-favorites-results.png` shows 3 shortcut cards, first card has `附近`, and 4 route result cards are visible.
- `home-all-routes-sheet.png` shows the `常用路線` bottom sheet with 4 routes and `查詢臨時起點和終點`.
- `route-detail-expanded.png` shows two legs and all 8 stops.
- `eta-arrivals-sheet.png` shows `第1班`, `第2班`, `第3班`.
- `route-detail-expanded.png` and `eta-arrivals-sheet.png` show the route shortcuts/results page dimmed behind the sheet, not the empty route state.
- `lockscreen-monitor.png` shows `28X · 立即出門` and the monitor body text.
- No screenshot contains `示例` or `Example`.
- Text does not visibly overlap.

- [x] **Step 4: Run full build if emulator and Gradle remain stable**

Run:

```bash
./gradlew build --no-daemon
```

Expected: `BUILD SUCCESSFUL`. If this fails for a pre-existing unrelated issue, record the failing task and error output in the final response before committing implementation changes.

---

### Task 5: Final Commit and Handoff

**Files:**
- Commit: screenshot instrumentation test, host script, plan file, and design-spec correction. Fixture support was committed separately.
- Do not commit: `~/Desktop/appmock截图/*.png`.
- Ignore: existing unrelated untracked directories such as `dsv4p/`, `glm52/`, `qwen37/`, and `seed21/`.

- [x] **Step 1: Confirm git status**

Run:

```bash
git status --short
```

Expected: only files created for this screenshot generator are modified or staged. Existing unrelated untracked directories may still appear and must not be added.

- [x] **Step 2: Stage the implementation scope**

Run:

```bash
git add \
  docs/superpowers/specs/2026-06-25-demo-screenshots-design.md \
  docs/superpowers/plans/2026-06-25-demo-screenshots.md \
  app/src/androidTest/java/com/example/busiscoming/DemoScreenshotInstrumentedTest.kt \
  scripts/generate-demo-screenshots.sh
```

Expected: no unrelated files are staged.

- [x] **Step 3: Inspect staged files**

Run:

```bash
git diff --cached --stat
git diff --cached --name-only
```

Expected: the staged file list contains only the four remaining paths from Step 2.

- [x] **Step 4: Commit**

Run:

```bash
git commit -m "test: add demo screenshot generator"
```

Expected: a commit is created for the repeatable screenshot generator.

- [x] **Step 5: Final response checklist**

Report:

- The five screenshot paths under `~/Desktop/appmock截图`.
- The validation commands that passed.
- Whether `./gradlew build --no-daemon` passed or why it was not completed.
- The final commit hash.

---

## Self-Review

- Spec coverage: the plan covers 5 PNGs, synthetic Hong Kong-style data, app-area crop for first 4 images, full lock-screen capture for the monitor image, no real API calls, repeatable `androidTest`, desktop output, and git exclusion for PNG files.
- Red-flag scan: this plan contains no unresolved marker strings and no unspecified follow-up tasks.
- Type consistency: fixture types match the current model classes read from `RouteConfig.kt`, `Place.kt`, `BusRouteOption.kt`, and `BusMonitorModels.kt`; screenshot UI paths match current `MainActivity`, `RouteDetailBottomSheet`, and `EtaArrivalsBottomSheet` entry points.
