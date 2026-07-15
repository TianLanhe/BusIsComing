package com.golink.busiscoming

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.RouteConfigDbHelper
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.data.repository.RouteImportMode
import com.golink.busiscoming.data.transfer.TransferRoute
import com.golink.busiscoming.data.transfer.RouteTransferCodec
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.settings.RouteTransferActivity
import java.io.File
import java.io.FileInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteTransferActivityInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
        context.cacheDir.resolve("route-transfer-test.bicroutes").delete()
        context.cacheDir.resolve("route-transfer-export.bicroutes").delete()
    }

    @Test
    fun emptyStateKeepsImportEnabledAndDisablesExport() {
        ActivityScenario.launch(RouteTransferActivity::class.java).use { scenario ->
            waitUntil(scenario) { activity ->
                activity.findViewById<TextView>(R.id.routeTransferCurrentCountText).text.contains("0")
            }
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.routeTransferImportButton).isEnabled)
                assertFalse(activity.findViewById<View>(R.id.routeTransferExportButton).isEnabled)
            }
        }
    }

    @Test
    fun validFilePreviewsAllNamesAndMergeUsesRepositoryResult() {
        val existingRepository = RouteConfigRepository(context)
        existingRepository.insert("上班", origin, destination)
        existingRepository.close()
        val uri = writeCandidate(validJson(includeExactDuplicate = true))

        ActivityScenario.launch(RouteTransferActivity::class.java).use { scenario ->
            scenario.onActivity { it.previewImportForTesting(uri, "route-transfer-test.bicroutes") }
            waitUntil(scenario) { it.findViewById<View>(R.id.routeTransferPreviewGroup).visibility == View.VISIBLE }
            scenario.onActivity { activity ->
                val impact = activity.findViewById<TextView>(R.id.routeTransferPreviewImpactText).text.toString()
                val names = activity.findViewById<View>(R.id.routeTransferPreviewNames) as android.widget.LinearLayout
                assertTrue(impact.contains("文件內重複：1"))
                assertTrue(impact.contains("新增 1 條，跳過 1 條"))
                assertEquals(2, names.childCount)
                activity.findViewById<View>(R.id.routeTransferMergeButton).performClick()
                activity.findViewById<View>(R.id.routeTransferMergeButton).performClick()
            }
            waitUntil(scenario) {
                it.findViewById<TextView>(R.id.routeTransferSummaryText).text.contains("新增 1 條")
            }
        }

        val repository = RouteConfigRepository(context)
        assertEquals(2, repository.getAll().size)
        repository.close()
    }

    @Test
    fun invalidVersionNeverEntersPreview() {
        val uri = writeCandidate(validJson().replace("\"version\":1", "\"version\":99"))

        ActivityScenario.launch(RouteTransferActivity::class.java).use { scenario ->
            scenario.onActivity { it.previewImportForTesting(uri, "route-transfer-test.bicroutes") }
            waitUntil(scenario) {
                it.findViewById<TextView>(R.id.routeTransferSummaryText).text.contains("版本不受支援")
            }
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.routeTransferPreviewGroup).visibility)
            }
        }
    }

    @Test
    fun previewRecreationReparsesUriAndExpiredUriReturnsHome() {
        val candidateFile = context.cacheDir.resolve("route-transfer-test.bicroutes")
        val uri = writeCandidate(validJson())

        ActivityScenario.launch(RouteTransferActivity::class.java).use { scenario ->
            scenario.onActivity { it.previewImportForTesting(uri, "route-transfer-test.bicroutes") }
            // 在舊 Activity 仍在解析時重建；舊結果必須被忽略，新 Activity 則從保存的 URI 重新解析。
            scenario.recreate()
            waitUntil(scenario) { it.findViewById<View>(R.id.routeTransferPreviewGroup).visibility == View.VISIBLE }

            scenario.recreate()
            waitUntil(scenario) { it.findViewById<View>(R.id.routeTransferPreviewGroup).visibility == View.VISIBLE }

            candidateFile.delete()
            scenario.recreate()
            waitUntil(scenario) {
                it.findViewById<TextView>(R.id.routeTransferSummaryText).text.contains("存取權已失效")
            }
            scenario.onActivity {
                assertEquals(View.GONE, it.findViewById<View>(R.id.routeTransferPreviewGroup).visibility)
                assertEquals(View.VISIBLE, it.findViewById<View>(R.id.routeTransferHomeGroup).visibility)
            }
        }
    }

    @Test
    fun replaceCancellationKeepsExistingRoutesAndConfirmationReplacesThem() {
        val repository = RouteConfigRepository(context)
        repository.insert("舊路線", Place("舊起點", 22.1, 114.1), Place("舊終點", 22.2, 114.2))
        repository.close()
        val uri = writeCandidate(validJson())

        ActivityScenario.launch(RouteTransferActivity::class.java).use { scenario ->
            scenario.onActivity { it.previewImportForTesting(uri, "route-transfer-test.bicroutes") }
            waitUntil(scenario) { it.findViewById<View>(R.id.routeTransferPreviewGroup).visibility == View.VISIBLE }

            scenario.onActivity { it.findViewById<View>(R.id.routeTransferReplaceButton).performClick() }
            onView(withText(R.string.route_transfer_cancel)).inRoot(isDialog()).perform(click())
            assertEquals(listOf("舊路線"), savedRouteNames())

            scenario.onActivity { it.findViewById<View>(R.id.routeTransferReplaceButton).performClick() }
            onView(withText(R.string.route_transfer_confirm_replace)).inRoot(isDialog()).perform(click())
            waitUntil(scenario) {
                it.findViewById<TextView>(R.id.routeTransferSummaryText).text.contains("取代完成")
            }
        }

        assertEquals(setOf("上班", "假日"), savedRouteNames().toSet())
    }

    @Test
    fun mainActivityReloadsImportedRoutesWhenResumed() {
        val repository = RouteConfigRepository(context)
        repository.insert("既有", Place("既有起點", 22.1, 114.1), Place("既有終點", 22.2, 114.2))
        repository.close()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            val importer = RouteConfigRepository(context)
            importer.importRoutes(
                listOf(TransferRoute("匯入後", Place("新起點", 22.3, 114.3), Place("新終點", 22.4, 114.4))),
                RouteImportMode.MERGE
            )
            importer.close()
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue(routeConfigs(activity).any { it.name == "匯入後" })
            }
        }
    }

    @Test
    fun longPreviewSupportsLargeTextNarrowScreenScrollingAndTouchTargets() {
        runShell("settings put system font_scale 1.3")
        runShell("wm size 720x1280")
        try {
            val uri = writeCandidate(manyRouteJson(30))
            ActivityScenario.launch(RouteTransferActivity::class.java).use { scenario ->
                scenario.onActivity { it.previewImportForTesting(uri, "route-transfer-test.bicroutes") }
                waitUntil(scenario) {
                    it.findViewById<View>(R.id.routeTransferPreviewGroup).visibility == View.VISIBLE
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    val density = activity.resources.displayMetrics.density
                    listOf(
                        R.id.routeTransferBackButton,
                        R.id.routeTransferMergeButton,
                        R.id.routeTransferReplaceButton,
                        R.id.routeTransferCancelPreviewButton
                    ).forEach { id ->
                        assertTrue(activity.findViewById<View>(id).height >= 48 * density)
                    }
                    val names = activity.findViewById<android.widget.LinearLayout>(R.id.routeTransferPreviewNames)
                    assertEquals(30, names.childCount)
                    assertTrue((names.getChildAt(0) as TextView).text.contains("大字體與窄屏下仍可完整辨識"))
                    val root = activity.findViewById<androidx.core.widget.NestedScrollView>(R.id.routeTransferRoot)
                    assertTrue(root.canScrollVertically(1))
                    assertTrue(activity.findViewById<TextView>(R.id.routeTransferPreviewFileText).text.isNotBlank())
                    assertTrue(activity.findViewById<TextView>(R.id.routeTransferPreviewImpactText).text.isNotBlank())
                }
            }
        } finally {
            runShell("wm size reset")
            runShell("settings put system font_scale 1.0")
        }
    }

    @Test
    fun exportWritesPortableDocumentAndReportsUnwritableDestination() {
        val repository = RouteConfigRepository(context)
        repository.insert("匯出測試", origin, destination)
        repository.close()
        val exportFile = context.cacheDir.resolve("route-transfer-export.bicroutes")

        ActivityScenario.launch(RouteTransferActivity::class.java).use { scenario ->
            waitUntil(scenario) {
                it.findViewById<TextView>(R.id.routeTransferCurrentCountText).text.contains("1")
            }
            scenario.onActivity { it.exportToForTesting(Uri.fromFile(exportFile)) }
            waitUntil(scenario) {
                it.findViewById<TextView>(R.id.routeTransferSummaryText).text.contains("已匯出 1 條")
            }
            assertEquals("匯出測試", RouteTransferCodec.decode(exportFile.readBytes()).routes.single().name)

            scenario.onActivity { it.exportToForTesting(Uri.fromFile(context.cacheDir)) }
            waitUntil(scenario) {
                it.findViewById<TextView>(R.id.routeTransferSummaryText).text.contains("匯出失敗")
            }
            assertEquals(listOf("匯出測試"), savedRouteNames())
        }
    }

    private fun waitUntil(
        scenario: ActivityScenario<RouteTransferActivity>,
        condition: (RouteTransferActivity) -> Boolean
    ) {
        repeat(100) {
            var matched = false
            scenario.onActivity { matched = condition(it) }
            if (matched) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for Activity state")
    }

    private fun writeCandidate(json: String): Uri {
        val file = context.cacheDir.resolve("route-transfer-test.bicroutes")
        file.writeText(json)
        return Uri.fromFile(file)
    }

    private fun savedRouteNames(): List<String> {
        val repository = RouteConfigRepository(context)
        return try {
            repository.getAll().map { it.name }
        } finally {
            repository.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun routeConfigs(activity: MainActivity): List<RouteConfig> =
        activity.javaClass.getDeclaredField("routeConfigs").apply { isAccessible = true }
            .get(activity) as List<RouteConfig>

    private fun validJson(includeExactDuplicate: Boolean = false): String {
        val first = """{"name":"上班","origin":{"name":"柴灣站","latitude":22.2642,"longitude":114.2371},"destination":{"name":"中環碼頭","latitude":22.2878,"longitude":114.1582}}"""
        val second = """{"name":"假日","origin":{"name":"柴灣站","latitude":22.2642,"longitude":114.2371},"destination":{"name":"中環碼頭","latitude":22.2878,"longitude":114.1582}}"""
        val routes = if (includeExactDuplicate) "$first,$first,$second" else "$first,$second"
        return """{"format":"com.golink.busiscoming.routes","version":1,"exportedAt":"2026-07-16T10:30:00Z","routes":[$routes]}"""
    }

    private fun manyRouteJson(count: Int): String {
        val routes = (1..count).joinToString(",") { index ->
            """{"name":"大字體與窄屏下仍可完整辨識的超長常用路線名稱 $index","origin":{"name":"起點 $index","latitude":22.2642,"longitude":114.2371},"destination":{"name":"終點 $index","latitude":22.2878,"longitude":114.1582}}"""
        }
        return """{"format":"com.golink.busiscoming.routes","version":1,"exportedAt":"2026-07-16T10:30:00Z","routes":[$routes]}"""
    }

    private fun runShell(command: String): String {
        val descriptor: ParcelFileDescriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
    }

    private val origin = Place("柴灣站", 22.2642, 114.2371)
    private val destination = Place("中環碼頭", 22.2878, 114.1582)
}
