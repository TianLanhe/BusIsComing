package com.example.busiscoming.ui.main

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.example.busiscoming.R
import com.example.busiscoming.data.model.TransitCodeLaunchTarget
import com.example.busiscoming.data.model.TransitCodeLaunchTargets
import com.example.busiscoming.data.model.TransitCodeProvider
import com.google.android.material.bottomsheet.BottomSheetDialog

class TransitCodeBottomSheet(
    private val context: Context,
    private val launcher: TransitCodeLauncher
) {
    private var dialog: BottomSheetDialog? = null
    private var contentRoot: LinearLayout? = null

    fun show() {
        dialog?.dismiss()

        val bottomSheetDialog = BottomSheetDialog(context)
        dialog = bottomSheetDialog

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        contentRoot = content
        val scroll = NestedScrollView(context).apply {
            isFillViewport = true
            isNestedScrollingEnabled = true
            addView(content)
        }

        content.addView(TextView(context).apply {
            text = "實驗性乘車碼入口"
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(context).apply {
            text = "逐條嘗試微信或支付寶候選入口；不會自動嘗試下一條。"
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        })

        addProviderSection(content, TransitCodeProvider.WECHAT)
        addProviderSection(content, TransitCodeProvider.ALIPAY)

        bottomSheetDialog.setContentView(scroll)
        bottomSheetDialog.setOnDismissListener {
            if (dialog == bottomSheetDialog) {
                dialog = null
                contentRoot = null
            }
        }
        bottomSheetDialog.show()
    }

    fun dispose() {
        dialog?.dismiss()
        dialog = null
        contentRoot = null
    }

    fun isShowing(): Boolean {
        return dialog?.isShowing == true
    }

    fun textSnapshot(): List<String> {
        val texts = mutableListOf<String>()
        collectText(contentRoot, texts)
        return texts
    }

    private fun addProviderSection(content: LinearLayout, provider: TransitCodeProvider) {
        content.addView(TextView(context).apply {
            text = provider.displayName
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18) }
        })
        TransitCodeLaunchTargets.forProvider(provider).forEach { target ->
            content.addView(targetRow(target))
        }
    }

    private fun targetRow(target: TransitCodeLaunchTarget): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(62)
            setPadding(0, dp(11), 0, dp(11))
            isClickable = true
            isFocusable = true
            background = selectableItemBackground()
            setOnClickListener { launch(target) }

            addView(TextView(context).apply {
                text = target.title
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = target.description
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 13f
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3) }
            })
        }
    }

    private fun launch(target: TransitCodeLaunchTarget) {
        when (launcher.launch(target)) {
            TransitCodeLaunchResult.SUCCESS -> Unit
            TransitCodeLaunchResult.UNAVAILABLE -> {
                Toast.makeText(context, unavailableMessage(target.provider), Toast.LENGTH_SHORT).show()
            }
            TransitCodeLaunchResult.UNEXPECTED_ERROR -> {
                Toast.makeText(context, R.string.transit_code_generic_launch_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun unavailableMessage(provider: TransitCodeProvider): Int {
        return when (provider) {
            TransitCodeProvider.WECHAT -> R.string.transit_code_wechat_launch_failed
            TransitCodeProvider.ALIPAY -> R.string.transit_code_alipay_launch_failed
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun selectableItemBackground() = TypedValue().let { value ->
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        ContextCompat.getDrawable(context, value.resourceId)
    }

    private fun collectText(view: View?, texts: MutableList<String>) {
        when (view) {
            is TextView -> texts.add(view.text.toString())
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    collectText(view.getChildAt(index), texts)
                }
            }
        }
    }
}
