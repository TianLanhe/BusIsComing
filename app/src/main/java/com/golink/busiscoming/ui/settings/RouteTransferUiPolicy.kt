package com.golink.busiscoming.ui.settings

import com.golink.busiscoming.data.repository.RouteImportMode
import com.golink.busiscoming.data.repository.RouteImportResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RouteTransferActionState(
    val importEnabled: Boolean,
    val exportEnabled: Boolean,
    val mergeEnabled: Boolean,
    val replaceEnabled: Boolean
)

object RouteTransferUiPolicy {
    const val EXPORT_MIME = "application/octet-stream"
    const val IMPORT_MIME = "*/*"

    fun suggestedFileName(timestampMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val formatter = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply {
            this.timeZone = timeZone
        }
        return "BusIsComing-routes-${formatter.format(Date(timestampMillis))}.bicroutes"
    }

    fun exportedAtUtc(timestampMillis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestampMillis))

    fun actionState(routeCount: Int, isBusy: Boolean, hasPreview: Boolean) =
        RouteTransferActionState(
            importEnabled = !isBusy,
            exportEnabled = !isBusy && routeCount > 0,
            mergeEnabled = !isBusy && hasPreview,
            replaceEnabled = !isBusy && hasPreview
        )

    fun exportSummary(routeCount: Int) = "已匯出 $routeCount 條常用路線。"

    fun importSummary(mode: RouteImportMode, result: RouteImportResult): String = when (mode) {
        RouteImportMode.MERGE -> if (result.addedCount == 0) {
            "匯入完成：沒有新增路線，${result.skippedCount} 條已存在。"
        } else {
            "匯入完成：新增 ${result.addedCount} 條，跳過 ${result.skippedCount} 條已存在路線。"
        }
        RouteImportMode.REPLACE ->
            "取代完成：已刪除 ${result.deletedCount} 條並匯入 ${result.addedCount} 條。"
    }
}
