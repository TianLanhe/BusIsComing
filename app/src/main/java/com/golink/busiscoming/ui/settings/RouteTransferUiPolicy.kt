package com.golink.busiscoming.ui.settings

import com.golink.busiscoming.data.repository.RouteImportMode
import com.golink.busiscoming.data.repository.RouteImportResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.golink.busiscoming.R
import com.golink.busiscoming.ui.common.LocalizedText

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

    fun exportSummary(routeCount: Int, text: LocalizedText) =
        text.get(R.string.route_export_success, arrayOf(routeCount))

    fun importSummary(
        mode: RouteImportMode,
        result: RouteImportResult,
        text: LocalizedText
    ): String = when (mode) {
        RouteImportMode.MERGE -> if (result.addedCount == 0) {
            text.get(R.string.route_import_none, arrayOf(result.skippedCount))
        } else {
            text.get(
                R.string.route_import_merged,
                arrayOf(result.addedCount, result.skippedCount)
            )
        }
        RouteImportMode.REPLACE ->
            text.get(
                R.string.route_import_replaced,
                arrayOf(result.deletedCount, result.addedCount)
            )
    }
}
