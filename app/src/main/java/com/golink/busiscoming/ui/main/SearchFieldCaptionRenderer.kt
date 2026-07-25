package com.golink.busiscoming.ui.main

import android.content.res.ColorStateList
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R
import com.golink.busiscoming.ui.common.PlaceInputMessage
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

internal class SearchFieldCaptionRenderer(
    private val inputLayout: TextInputLayout,
    private val input: MaterialAutoCompleteTextView,
    @StringRes labelResource: Int
) {
    private val state = SearchFieldCaptionState()
    private val label = inputLayout.context.getString(labelResource)
    private val captionPaint = AppCompatTextView(inputLayout.context).apply {
        setTextAppearance(R.style.TextAppearance_BusIsComing_SearchFieldCaption)
    }.paint
    private var lastMeasuredWidth = 0

    init {
        inputLayout.addOnLayoutChangeListener(
            View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                val width = right - left
                if (width != oldRight - oldLeft || width != lastMeasuredWidth) {
                    lastMeasuredWidth = width
                    render()
                }
            }
        )
        render()
    }

    fun onPlaceInputMessage(message: PlaceInputMessage) {
        state.onPlaceInputMessage(message)
        render()
    }

    fun setGoogleMaps(value: Boolean) {
        state.setGoogleMaps(value)
        render()
    }

    fun setLocationFailure(value: Boolean) {
        state.setLocationFailure(value)
        render()
    }

    fun setValidation(value: SearchFieldValidation?) {
        state.setValidation(value)
        render()
    }

    private fun render() {
        val status = state.visibleStatus()
        val fullStatus = status?.let { inputLayout.context.getString(textResources(it).first) }
        val compactStatus = status?.let { inputLayout.context.getString(textResources(it).second) }
        val fullHint = combine(label, fullStatus)
        val compactHint = combine(label, compactStatus)
        inputLayout.hint = if (fits(fullHint)) fullHint else compactHint
        inputLayout.contentDescription = accessibilityDescription(fullStatus)
        applyErrorColors(status?.isError == true)
    }

    private fun combine(fieldLabel: String, status: String?): String =
        if (status == null) {
            fieldLabel
        } else {
            inputLayout.context.getString(R.string.search_field_caption_format, fieldLabel, status)
        }

    private fun fits(text: String): Boolean {
        if (inputLayout.width <= 0) return true
        val safetyInset = (8 * inputLayout.resources.displayMetrics.density).toInt()
        val availableWidth =
            (inputLayout.width - input.paddingStart - safetyInset).coerceAtLeast(0)
        return captionPaint.measureText(text) <= availableWidth
    }

    private fun accessibilityDescription(status: String?): String {
        val value = input.text?.toString()?.trim().orEmpty()
        return when {
            value.isNotEmpty() && status != null -> inputLayout.context.getString(
                R.string.search_field_accessibility_value_status,
                label,
                value,
                status
            )
            value.isNotEmpty() -> inputLayout.context.getString(
                R.string.search_field_accessibility_value,
                label,
                value
            )
            status != null -> inputLayout.context.getString(
                R.string.search_field_accessibility_status,
                label,
                status
            )
            else -> label
        }
    }

    private fun applyErrorColors(isError: Boolean) {
        val context = inputLayout.context
        val hintColor = ContextCompat.getColor(
            context,
            if (isError) R.color.bus_danger else R.color.bus_text_secondary
        )
        val hintColors = ColorStateList.valueOf(hintColor)
        inputLayout.setDefaultHintTextColor(hintColors)
        inputLayout.setHintTextColor(hintColors)
        val strokeColors = if (isError) {
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.bus_danger))
        } else {
            requireNotNull(ContextCompat.getColorStateList(context, R.color.search_input_stroke))
        }
        inputLayout.setBoxStrokeColorStateList(strokeColors)
    }

    private fun textResources(status: SearchFieldCaptionStatus): Pair<Int, Int> =
        when (status) {
            SearchFieldCaptionStatus.INSTRUCTION ->
                R.string.search_field_choose_from_list to
                    R.string.search_field_choose_from_list_compact
            SearchFieldCaptionStatus.GOOGLE_MAPS ->
                R.string.search_field_google_maps_address to
                    R.string.search_field_google_maps_address_compact
            SearchFieldCaptionStatus.NO_MATCHES ->
                R.string.search_field_no_matches to R.string.search_field_no_matches_compact
            SearchFieldCaptionStatus.SEARCH_FAILED ->
                R.string.search_field_search_failed to
                    R.string.search_field_search_failed_compact
            SearchFieldCaptionStatus.LOCATION_FAILURE ->
                R.string.search_field_location_failure to
                    R.string.search_field_location_failure_compact
            SearchFieldCaptionStatus.MISSING_PLACE ->
                R.string.search_field_choose_place to R.string.search_field_choose_place_compact
            SearchFieldCaptionStatus.SAME_AS_ORIGIN ->
                R.string.search_field_same_as_origin to
                    R.string.search_field_same_as_origin_compact
        }
}
