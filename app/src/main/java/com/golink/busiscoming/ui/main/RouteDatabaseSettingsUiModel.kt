package com.golink.busiscoming.ui.main

import androidx.annotation.StringRes
import com.golink.busiscoming.R
import com.golink.busiscoming.data.repository.RouteDatabaseUpdateState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class RouteDatabaseSettingsUiModel(
    @param:StringRes val summaryRes: Int,
    val completedAtText: String?,
    val rowEnabled: Boolean
)

object RouteDatabaseSettingsUiModelFactory {
    fun create(
        state: RouteDatabaseUpdateState,
        locale: Locale
    ): RouteDatabaseSettingsUiModel {
        val completedAtText = state.lastSuccessMillis?.let { formatHongKongTime(it, locale) }
        return when (state) {
            is RouteDatabaseUpdateState.Idle -> if (completedAtText == null) {
                RouteDatabaseSettingsUiModel(
                    R.string.route_database_status_never_completed,
                    null,
                    rowEnabled = true
                )
            } else {
                RouteDatabaseSettingsUiModel(
                    R.string.route_database_status_last_success,
                    completedAtText,
                    rowEnabled = true
                )
            }
            is RouteDatabaseUpdateState.Checking -> RouteDatabaseSettingsUiModel(
                R.string.route_database_status_checking,
                null,
                rowEnabled = false
            )
            is RouteDatabaseUpdateState.Success -> RouteDatabaseSettingsUiModel(
                if (state.changed) {
                    R.string.route_database_status_updated
                } else {
                    R.string.route_database_status_latest
                },
                requireNotNull(completedAtText),
                rowEnabled = true
            )
            is RouteDatabaseUpdateState.Failure -> RouteDatabaseSettingsUiModel(
                if (completedAtText == null) {
                    R.string.route_database_status_failed_no_data
                } else {
                    R.string.route_database_status_failed_using_previous
                },
                completedAtText,
                rowEnabled = true
            )
        }
    }

    private fun formatHongKongTime(millis: Long, locale: Locale): String {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).apply {
            timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
        }.format(Date(millis))
    }
}
