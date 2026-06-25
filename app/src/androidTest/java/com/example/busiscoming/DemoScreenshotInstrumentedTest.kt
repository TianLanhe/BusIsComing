package com.example.busiscoming

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private val outputDir: File by lazy {
        File(targetContext.filesDir, DemoScreenshotFixtures.outputDirectory)
    }

    @Before
    fun prepareDeviceAndOutput() {
        targetContext.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        clearPublicScreenshots()
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
            val dialogRef = AtomicReference<BottomSheetDialog>()
            scenario.onActivity { activity ->
                configureHome(activity)
            }
            waitForUi()
            saveAppAreaScreenshot(scenario, DemoScreenshotFixtures.homeFavoritesResults)

            scenario.onActivity { activity ->
                dialogRef.set(showAllSavedRoutesSheet(activity))
            }
            waitForUi()
            saveAppAreaScreenshot(scenario, DemoScreenshotFixtures.homeAllRoutesSheet)
            scenario.onActivity {
                dialogRef.get()?.dismiss()
            }
        }
    }

    private fun captureRouteDetail() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val sheetRef = AtomicReference<RouteDetailBottomSheet>()
            scenario.onActivity { activity ->
                configureHome(activity)
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
            waitUntil(5_000L) {
                collectText(dialogRoot(sheetRef.get())).any { it.contains("途經 2 個站") }
            }
            scenario.onActivity {
                expandBottomSheet(sheetRef.get())
                dialogRoot(sheetRef.get())?.let { root ->
                    clickAllTextViewsContaining(root, "途經 2 個站")
                }
            }
            waitForUi()
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
                configureHome(activity)
                val sheet = EtaArrivalsBottomSheet(activity)
                sheetRef.set(sheet)
                sheet.show(DemoScreenshotFixtures.primaryRoute())
            }
            waitUntil(5_000L) {
                collectText(dialogRoot(sheetRef.get())).any { it.contains("第3班") }
            }
            scenario.onActivity {
                expandBottomSheet(sheetRef.get())
            }
            waitForUi()
            saveAppAreaScreenshot(scenario, DemoScreenshotFixtures.etaArrivalsSheet)
            scenario.onActivity {
                sheetRef.get()?.dispose()
            }
        }
    }

    private fun captureLockscreenMonitor() {
        postMonitorNotification()
        executeShell("locksettings set-disabled false")
        executeShell("settings put secure lockscreen.disabled 0")
        executeShell("settings put secure lock_screen_show_notifications 1")
        executeShell("settings put secure lock_screen_allow_private_notifications 1")
        executeShell("cmd statusbar collapse")
        executeShell("input keyevent 223")
        SystemClock.sleep(600L)
        executeShell("input keyevent 224")
        SystemClock.sleep(800L)
        executeShell("cmd statusbar expand-notifications")
        SystemClock.sleep(1_000L)
        saveFullScreenshot(DemoScreenshotFixtures.lockscreenMonitor)
        executeShell("cmd statusbar collapse")
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

    private fun showAllSavedRoutesSheet(activity: MainActivity): BottomSheetDialog {
        val dialog = BottomSheetDialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(12))
        }
        content.addView(TextView(activity).apply {
            text = "常用路線"
            setTextColor(ContextCompat.getColor(activity, R.color.bus_text_primary))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })
        DemoScreenshotFixtures.savedRoutes.forEach { route ->
            content.addView(LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                addView(TextView(activity).apply {
                    text = route.name
                    setTextColor(ContextCompat.getColor(activity, R.color.bus_text_primary))
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(activity).apply {
                    text = route.pathLabel()
                    setTextColor(ContextCompat.getColor(activity, R.color.bus_text_secondary))
                    textSize = 13f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                })
            })
        }
        dialog.setContentView(content)
        dialog.show()
        return dialog
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
                val insets = decor.rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())
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
        writePublicPng(bitmap, name)
    }

    private fun writePublicPng(bitmap: Bitmap, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/BusIsComingDemoScreenshots"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = targetContext.contentResolver
        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Failed to create public screenshot uri for $name" }
        resolver.openOutputStream(uri).use { output ->
            requireNotNull(output) { "Failed to open public screenshot output for $name" }
            val written = bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            assertTrue("Failed to write public $name", written)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    private fun clearPublicScreenshots() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                targetContext.contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                    arrayOf("${Environment.DIRECTORY_PICTURES}/BusIsComingDemoScreenshots/")
                )
            }
        }
        executeShell("rm -rf /sdcard/Pictures/BusIsComingDemoScreenshots")
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

    private fun waitForUi() {
        instrumentation.waitForIdleSync()
        SystemClock.sleep(500L)
        instrumentation.waitForIdleSync()
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

    private fun dialogRoot(owner: Any?): View? {
        return bottomSheetDialog(owner)?.window?.decorView?.rootView
    }

    private fun expandBottomSheet(owner: Any?) {
        val dialog = bottomSheetDialog(owner) ?: return
        val bottomSheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun bottomSheetDialog(owner: Any?): BottomSheetDialog? {
        if (owner == null) return null
        return owner.javaClass.declaredFields
            .firstOrNull { BottomSheetDialog::class.java.isAssignableFrom(it.type) }
            ?.apply { isAccessible = true }
            ?.get(owner) as? BottomSheetDialog
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

    private fun dp(value: Int): Int {
        return (value * targetContext.resources.displayMetrics.density).toInt()
    }

    private fun executeShell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                input.readBytes()
            }
        }
    }
}
