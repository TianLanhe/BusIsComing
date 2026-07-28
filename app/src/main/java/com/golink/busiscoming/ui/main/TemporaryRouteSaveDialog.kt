package com.golink.busiscoming.ui.main

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfigValidator
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.ui.common.applyStableShortTextLayout
import com.golink.busiscoming.ui.common.localizedMessage
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object TemporaryRouteSaveDialog {
    fun defaultName(origin: Place, destination: Place): String {
        return "${origin.name} -> ${destination.name}"
    }

    fun show(
        context: Context,
        routeConfigRepository: RouteConfigRepository,
        origin: Place,
        destination: Place,
        onSaved: (Long) -> Unit,
        onSaveFailed: () -> Unit = {}
    ) {
        val nameInput = TextInputEditText(context).apply {
            setText(defaultName(origin, destination))
            setSelectAllOnFocus(true)
            maxLines = 1
        }
        val nameLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = context.getString(R.string.frequent_route_name_hint)
            addView(nameInput)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 4), 0, dp(context, 4), 0)
            addView(TextView(context).apply {
                text = context.getString(R.string.route_preview)
                applyStableShortTextLayout(Gravity.START)
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = "${origin.name} → ${destination.name}"
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                textSize = 15f
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 12) }
            })
            addView(nameLayout)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.save_frequent_title)
            .setView(content)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(android.content.DialogInterface.BUTTON_NEGATIVE)
                        .applyStableShortTextLayout(Gravity.CENTER)
                    val positiveButton = getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                        .applyStableShortTextLayout(Gravity.CENTER)
                    positiveButton.setOnClickListener {
                        val name = nameInput.text?.toString()?.trim().orEmpty()
                        val validation = RouteConfigValidator.validate(name, origin, destination)
                        nameLayout.error = validation.nameError.localizedMessage(context)
                        if (!validation.isValid) return@setOnClickListener
                        if (routeConfigRepository.hasDuplicate(name, origin, destination)) {
                            nameLayout.error = context.getString(R.string.route_duplicate_detail)
                            return@setOnClickListener
                        }
                        val id = try {
                            routeConfigRepository.insert(name, origin, destination)
                        } catch (_: Exception) {
                            -1L
                        }
                        if (id <= 0L) {
                            nameLayout.error = context.getString(R.string.save_frequent_failed)
                            onSaveFailed()
                            return@setOnClickListener
                        }
                        Toast.makeText(context, R.string.saved_as_frequent, Toast.LENGTH_SHORT).show()
                        dismiss()
                        onSaved(id)
                    }
                }
                show()
            }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
