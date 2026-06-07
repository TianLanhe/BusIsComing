package com.example.busiscoming.ui.main

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.EtaArrival
import com.example.busiscoming.data.model.WaitTimeState
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EtaArrivalsBottomSheet(
    private val context: Context
) {
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
            text = "首程 ${route.routeSegments.firstOrNull() ?: route.routeName} 候車時間"
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })

        root.addView(TextView(context).apply {
            text = subtitle(route, arrivals.firstOrNull())
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        })
        updateTimeText(arrivals.firstOrNull())?.let { text ->
            root.addView(TextView(context).apply {
                this.text = text
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }

        arrivals.take(3).forEach { arrival ->
            root.addView(arrivalRow(arrival))
        }
    }

    private fun subtitle(route: BusRouteOption, firstArrival: EtaArrival?): String {
        val boarding = route.stopPreview?.boardingStopName
        val destination = firstArrival?.destination
        return when {
            !boarding.isNullOrBlank() && !destination.isNullOrBlank() -> "$boarding 往 $destination"
            route.stopPreview != null -> route.stopPreview.displayText()
            !destination.isNullOrBlank() -> "往 $destination"
            else -> route.routeSegments.joinToString(" → ")
        }
    }

    private fun arrivalRow(arrival: EtaArrival): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        row.addView(TextView(context).apply {
            text = "第${arrival.sequence}班"
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT)
        })

        row.addView(TextView(context).apply {
            text = minuteText(arrival.minutes)
            setTextColor(ContextCompat.getColor(context, R.color.bus_wait_accent))
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        row.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(TextView(context).apply {
                text = arrival.arrivalTimeText
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                textSize = 14f
                gravity = Gravity.END
            })
            val remark = arrival.remark
            if (!remark.isNullOrBlank()) {
                addView(TextView(context).apply {
                    text = remark
                    setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                    textSize = 12f
                    gravity = Gravity.END
                    maxLines = 1
                })
            }
        })
        return row
    }

    private fun minuteText(minutes: Int): String {
        return if (minutes <= 0) "即將到站" else "$minutes 分鐘"
    }

    private fun updateTimeText(arrival: EtaArrival?): String? {
        val timestampMillis = arrival?.dataTimestampMillis ?: return null
        return "更新 ${ARRIVAL_TIME_FORMAT.get()!!.format(Date(timestampMillis))}"
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private val ARRIVAL_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH:mm", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Hong_Kong")
                }
            }
        }
    }
}
