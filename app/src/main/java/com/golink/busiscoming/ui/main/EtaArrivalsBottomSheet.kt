package com.golink.busiscoming.ui.main

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.common.applyStableShortTextLayout
import com.golink.busiscoming.ui.common.LocalizedText
import com.golink.busiscoming.ui.common.localizedText
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EtaArrivalsBottomSheet(
    private val context: Context
) {
    private val localizedText = context.localizedText()
    private var dialog: BottomSheetDialog? = null
    private var activeRouteId: String? = null
    private var content: LinearLayout? = null

    fun show(route: BusRouteOption) {
        val available = route.waitTimeState as? WaitTimeState.Available ?: return
        if (available.arrivals.size < 2) return

        dialog?.dismiss()
        activeRouteId = route.resultId
        val bottomSheetDialog = BottomSheetDialog(context)
        dialog = bottomSheetDialog
        val root = createRoot()
        content = root
        render(root, route, available.arrivals)
        bottomSheetDialog.setContentView(root)
        bottomSheetDialog.setOnDismissListener {
            if (dialog == bottomSheetDialog) {
                activeRouteId = null
                content = null
                dialog = null
            }
        }
        bottomSheetDialog.show()
    }

    fun update(route: BusRouteOption) {
        if (activeRouteId != route.resultId || dialog?.isShowing != true) return
        val available = route.waitTimeState as? WaitTimeState.Available ?: return
        val root = content ?: return
        render(root, route, available.arrivals)
    }

    fun dispose() {
        activeRouteId = null
        content = null
        dialog?.dismiss()
        dialog = null
    }

    private fun createRoot(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
    }

    private fun render(root: LinearLayout, route: BusRouteOption, arrivals: List<EtaArrival>) {
        root.removeAllViews()
        root.addView(TextView(context).apply {
            text = EtaArrivalsSheetFormatter.title(route, localizedText)
            applyStableShortTextLayout(Gravity.START)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(context).apply {
            text = EtaArrivalsSheetFormatter.subtitle(route, arrivals.firstOrNull(), localizedText)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        })
        EtaArrivalsSheetFormatter.updateTimeText(arrivals, localizedText)?.let { text ->
            root.addView(TextView(context).apply {
                this.text = text
                applyStableShortTextLayout(Gravity.START)
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }

        val arrivalList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            arrivals.forEach { arrival -> addView(arrivalRow(arrival)) }
        }
        root.addView(ScrollView(context).apply {
            isFillViewport = false
            addView(arrivalList)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(dp(74) * arrivals.size, (context.resources.displayMetrics.heightPixels * 0.40f).toInt())
            ).apply { topMargin = dp(2) }
        })
    }

    private fun arrivalRow(arrival: EtaArrival): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val primaryLine = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(primaryLine)

        val sequenceText = context.getString(R.string.eta_arrival_sequence, arrival.sequence)
        val minuteText = EtaArrivalsSheetFormatter.minuteText(arrival.minutes, localizedText)
        val badge = EtaOperatorBadge.forOperator(arrival.operator)
        val operatorText = badge?.let { context.getString(it.labelRes) }.orEmpty()
        row.contentDescription = if (arrival.remark.isNullOrBlank()) {
            context.getString(
                R.string.eta_arrival_row_content_description,
                sequenceText,
                operatorText,
                minuteText,
                arrival.arrivalTimeText
            )
        } else {
            context.getString(
                R.string.eta_arrival_row_with_remark_content_description,
                sequenceText,
                operatorText,
                minuteText,
                arrival.arrivalTimeText,
                arrival.remark
            )
        }
        row.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

        primaryLine.addView(TextView(context).apply {
            text = sequenceText
            applyStableShortTextLayout(Gravity.START)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 14f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        badge?.let { presentation ->
            primaryLine.addView(TextView(context).apply {
                text = operatorText
                setTextColor(ContextCompat.getColor(context, presentation.textColorRes))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(20).toFloat()
                    setColor(ContextCompat.getColor(context, presentation.backgroundColorRes))
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(10) }
            })
        }

        primaryLine.addView(TextView(context).apply {
            text = minuteText
            applyStableShortTextLayout(Gravity.START)
            setTextColor(ContextCompat.getColor(context, R.color.bus_wait_accent))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        })

        primaryLine.addView(TextView(context).apply {
            text = arrival.arrivalTimeText
            applyStableShortTextLayout(Gravity.END)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 14f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
        val remark = arrival.remark
        if (!remark.isNullOrBlank()) {
            row.addView(TextView(context).apply {
                text = remark
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 12f
                gravity = Gravity.END
                maxLines = 3
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }
        return row
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}

object EtaArrivalsSheetFormatter {
    fun title(route: BusRouteOption, text: LocalizedText): String {
        return text.get(
            R.string.eta_sheet_title,
            arrayOf(route.routeSegments.firstOrNull() ?: route.routeName)
        )
    }

    fun subtitle(route: BusRouteOption, firstArrival: EtaArrival?, text: LocalizedText): String {
        val boarding = route.stopPreview?.boardingStopName
        val destination = firstArrival?.destination
        return when {
            !boarding.isNullOrBlank() && !destination.isNullOrBlank() ->
                text.get(R.string.direction_from_to, arrayOf(boarding, destination))
            route.stopPreview != null -> route.stopPreview.displayText()
            !destination.isNullOrBlank() -> text.get(R.string.direction_to, arrayOf(destination))
            else -> route.routeSegments.joinToString(" → ")
        }
    }

    fun minuteText(minutes: Int, text: LocalizedText): String {
        return if (minutes <= 0) {
            text.get(R.string.eta_due, emptyArray())
        } else {
            text.get(R.string.minutes_count, arrayOf(minutes))
        }
    }

    fun updateTimeText(arrivals: List<EtaArrival>, text: LocalizedText): String? {
        val timestampMillis = arrivals.mapNotNull(EtaArrival::dataTimestampMillis).minOrNull() ?: return null
        return text.get(
            R.string.eta_updated,
            arrayOf(ARRIVAL_TIME_FORMAT.get()!!.format(Date(timestampMillis)))
        )
    }

    private val ARRIVAL_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("HH:mm", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
            }
        }
    }
}

data class EtaOperatorBadge(
    @param:StringRes val labelRes: Int,
    @param:ColorRes val backgroundColorRes: Int,
    @param:ColorRes val textColorRes: Int
) {
    companion object {
        fun forOperator(operator: BusOperator?): EtaOperatorBadge? = when (operator) {
            BusOperator.CTB -> EtaOperatorBadge(
                R.string.operator_ctb,
                R.color.operator_ctb_background,
                R.color.operator_ctb_text
            )
            BusOperator.KMB -> EtaOperatorBadge(
                R.string.operator_kmb,
                R.color.operator_kmb_background,
                R.color.operator_kmb_text
            )
            BusOperator.LWB -> EtaOperatorBadge(
                R.string.operator_lwb,
                R.color.operator_lwb_background,
                R.color.operator_lwb_text
            )
            null -> null
        }
    }
}
