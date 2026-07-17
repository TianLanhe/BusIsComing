package com.golink.busiscoming

import com.golink.busiscoming.data.repository.RouteImportMode
import com.golink.busiscoming.data.repository.RouteImportResult
import com.golink.busiscoming.ui.settings.RouteTransferUiPolicy
import com.golink.busiscoming.ui.common.LocalizedText
import java.io.File
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTransferUiContractTest {
    private val layout = File("src/main/res/layout/activity_route_transfer.xml").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()
    private val activity = File(
        "src/main/java/com/golink/busiscoming/ui/settings/RouteTransferActivity.kt"
    ).readText()
    private val text = LocalizedText { resourceId, arguments ->
        when (resourceId) {
            R.string.route_export_success -> "已匯出 ${arguments[0]} 條常用路線。"
            R.string.route_import_none -> "匯入完成：沒有新增路線，${arguments[0]} 條已存在。"
            R.string.route_import_merged ->
                "匯入完成：新增 ${arguments[0]} 條，跳過 ${arguments[1]} 條已存在路線。"
            R.string.route_import_replaced ->
                "取代完成：已刪除 ${arguments[0]} 條並匯入 ${arguments[1]} 條。"
            else -> error("Unexpected resource: $resourceId")
        }
    }

    @Test
    fun transferPageOffersOnlyImportAndExportAll() {
        assertTrue(layout.contains("@+id/routeTransferImportButton"))
        assertTrue(layout.contains("@+id/routeTransferExportButton"))
        assertTrue(layout.contains("@+id/routeTransferMergeButton"))
        assertTrue(layout.contains("@+id/routeTransferReplaceButton"))
        assertFalse(layout.contains("部分匯出"))
        assertFalse(layout.contains("分享"))
        assertFalse(layout.contains("密碼"))
        assertFalse(layout.contains("加密"))
    }

    @Test
    fun exportContractUsesSafAndAlwaysShowsPrivacyWarning() {
        assertEquals("application/octet-stream", RouteTransferUiPolicy.EXPORT_MIME)
        assertEquals("*/*", RouteTransferUiPolicy.IMPORT_MIME)
        assertEquals(
            "BusIsComing-routes-20231114-2213.bicroutes",
            RouteTransferUiPolicy.suggestedFileName(1_700_000_000_000, TimeZone.getTimeZone("UTC"))
        )
        assertTrue(activity.contains("ActivityResultContracts.CreateDocument"))
        assertTrue(activity.contains("ActivityResultContracts.OpenDocument"))
        assertTrue(strings.contains("包含所有常用路線的地點名稱和精確座標"))
        assertTrue(strings.contains("只與你信任的人分享"))
        assertTrue(strings.contains("<string name=\"route_transfer_cancel\">取消</string>"))
        assertFalse(activity.contains("android.R.string.cancel"))
        assertEquals(2, Regex("setNegativeButton\\(R\\.string\\.route_transfer_cancel").findAll(activity).count())
    }

    @Test
    fun actionStateDisablesExportWhenEmptyAndAllActionsWhileBusy() {
        val empty = RouteTransferUiPolicy.actionState(routeCount = 0, isBusy = false, hasPreview = false)
        val ready = RouteTransferUiPolicy.actionState(routeCount = 2, isBusy = false, hasPreview = true)
        val busy = RouteTransferUiPolicy.actionState(routeCount = 2, isBusy = true, hasPreview = true)

        assertTrue(empty.importEnabled)
        assertFalse(empty.exportEnabled)
        assertTrue(ready.exportEnabled)
        assertTrue(ready.mergeEnabled)
        assertTrue(ready.replaceEnabled)
        assertFalse(busy.importEnabled)
        assertFalse(busy.exportEnabled)
        assertFalse(busy.mergeEnabled)
        assertFalse(busy.replaceEnabled)
    }

    @Test
    fun summariesUseActualRepositoryResultIncludingAllDuplicates() {
        assertEquals("已匯出 3 條常用路線。", RouteTransferUiPolicy.exportSummary(3, text))
        assertEquals(
            "匯入完成：沒有新增路線，2 條已存在。",
            RouteTransferUiPolicy.importSummary(RouteImportMode.MERGE, RouteImportResult(0, 2, 0), text)
        )
        assertEquals(
            "匯入完成：新增 3 條，跳過 1 條已存在路線。",
            RouteTransferUiPolicy.importSummary(RouteImportMode.MERGE, RouteImportResult(3, 1, 0), text)
        )
        assertEquals(
            "取代完成：已刪除 4 條並匯入 2 條。",
            RouteTransferUiPolicy.importSummary(RouteImportMode.REPLACE, RouteImportResult(2, 0, 4), text)
        )
    }
}
