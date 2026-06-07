package com.example.busiscoming.ui.main

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.busiscoming.R
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfigValidator
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object TemporaryRouteSaveDialog {
    fun show(
        context: Context,
        routeConfigRepository: RouteConfigRepository,
        origin: Place,
        destination: Place,
        onSaved: (Long) -> Unit
    ) {
        val nameInput = TextInputEditText(context).apply {
            setText("${origin.name} -> ${destination.name}")
            setSelectAllOnFocus(true)
            maxLines = 1
        }
        val nameLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = "常用路線名稱"
            addView(nameInput)
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 4), 0, dp(context, 4), 0)
            addView(TextView(context).apply {
                text = "路線預覽"
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
            .setTitle("保存為常用")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text?.toString()?.trim().orEmpty()
                        val validation = RouteConfigValidator.validate(name, origin, destination)
                        nameLayout.error = validation.nameError
                        if (!validation.isValid) return@setOnClickListener
                        if (routeConfigRepository.hasDuplicate(name, origin, destination)) {
                            nameLayout.error = "路線已存在，請修改名稱或起終點"
                            return@setOnClickListener
                        }
                        val id = routeConfigRepository.insert(name, origin, destination)
                        Toast.makeText(context, "已保存為常用", Toast.LENGTH_SHORT).show()
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
