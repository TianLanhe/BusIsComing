package com.golink.busiscoming.ui.common

import android.content.Context
import androidx.annotation.StringRes
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.RouteConfigValidationError

fun interface LocalizedText {
    fun get(@StringRes resourceId: Int, arguments: Array<out Any>): String
}

fun Context.localizedText(): LocalizedText = LocalizedText { resourceId, arguments ->
    getString(resourceId, *arguments)
}

fun RouteConfigValidationError?.localizedMessage(context: Context): String? = when (this) {
    RouteConfigValidationError.REQUIRED -> context.getString(R.string.validation_required)
    RouteConfigValidationError.ORIGIN_REQUIRED -> context.getString(R.string.validation_origin_required)
    RouteConfigValidationError.DESTINATION_REQUIRED ->
        context.getString(R.string.validation_destination_required)
    RouteConfigValidationError.SAME_PLACES -> context.getString(R.string.validation_same_places)
    null -> null
}
