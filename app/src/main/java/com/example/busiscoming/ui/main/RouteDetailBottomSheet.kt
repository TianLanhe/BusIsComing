package com.example.busiscoming.ui.main

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.RouteDetail
import com.example.busiscoming.data.model.RouteDetailDisplayFormatter
import com.example.busiscoming.data.model.RouteDetailExpansionState
import com.example.busiscoming.data.model.RouteDetailLeg
import com.example.busiscoming.data.model.RouteDetailStop
import com.example.busiscoming.data.repository.RouteDetailRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteDetailBottomSheet(
    private val activity: AppCompatActivity,
    private val repository: RouteDetailRepository
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val detailExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var activeRequestId = 0
    private var activeDialog: BottomSheetDialog? = null

    fun show(route: BusRouteOption) {
        val requestId = ++activeRequestId
        activeDialog?.dismiss()

        val dialog = BottomSheetDialog(activity)
        activeDialog = dialog

        val state = SheetViews(route)
        dialog.setContentView(state.root)
        dialog.setOnDismissListener {
            if (activeDialog == dialog) {
                activeRequestId += 1
                activeDialog = null
            }
        }
        state.closeButton.setOnClickListener { dialog.dismiss() }
        state.retryButton.setOnClickListener {
            startLoad(route, dialog, state)
        }

        dialog.show()
        startLoad(route, dialog, state, requestId)
    }

    fun dispose() {
        activeRequestId += 1
        activeDialog?.dismiss()
        activeDialog = null
        detailExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun startLoad(
        route: BusRouteOption,
        dialog: BottomSheetDialog,
        state: SheetViews,
        requestId: Int = ++activeRequestId
    ) {
        if (route.routeDetailQuery == null) {
            showFailure(state, "路線詳情暫不可用")
            return
        }

        showLoading(state)
        detailExecutor.execute {
            val result = runCatching { repository.loadRouteDetail(route) }
            mainHandler.post {
                if (requestId != activeRequestId || activeDialog != dialog || !dialog.isShowing) {
                    return@post
                }
                result
                    .onSuccess { showDetail(state, it) }
                    .onFailure { showFailure(state, "路線詳情暫不可用") }
            }
        }
    }

    private fun showLoading(state: SheetViews) {
        state.loadingRow.visibility = View.VISIBLE
        state.errorContainer.visibility = View.GONE
        state.detailScroll.visibility = View.GONE
    }

    private fun showFailure(state: SheetViews, message: String) {
        state.loadingRow.visibility = View.GONE
        state.detailScroll.visibility = View.GONE
        state.errorMessage.text = message
        state.errorContainer.visibility = View.VISIBLE
    }

    private fun showDetail(state: SheetViews, detail: RouteDetail) {
        state.loadingRow.visibility = View.GONE
        state.errorContainer.visibility = View.GONE
        state.detailContainer.removeAllViews()
        state.detailContainer.addView(summaryText(detail))

        val expansionState = RouteDetailExpansionState(detail.legs.size)
        detail.legs.forEachIndexed { index, leg ->
            state.detailContainer.addView(legView(leg, index, expansionState))
            if (index < detail.legs.lastIndex) {
                state.detailContainer.addView(transferConnectorView())
            }
        }
        state.detailScroll.visibility = View.VISIBLE
    }

    private fun summaryText(detail: RouteDetail): TextView {
        return TextView(activity).apply {
            text = "耗時 ${detail.durationMinutes} 分鐘 · ${formatPrice(detail.priceHkd)} · 步行 ${detail.walkingDistanceMeters} 米"
            setTextColor(ContextCompat.getColor(activity, R.color.bus_text_secondary))
            textSize = 14f
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }
    }

    private fun legView(
        leg: RouteDetailLeg,
        legIndex: Int,
        expansionState: RouteDetailExpansionState
    ): View {
        val legColor = LEG_COLORS[legIndex % LEG_COLORS.size]
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(LegRailView(activity, legColor).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT)
        })

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                bottomMargin = dp(8)
            }
        }
        root.addView(content)

        content.addView(legHeader(leg, legColor))
        content.addView(stationView("上車", leg.boardingStop, isEndpoint = true))

        val viaContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        leg.viaStops.forEach { via ->
            viaContainer.addView(stationView(null, via, isEndpoint = false))
        }

        if (leg.viaStops.isNotEmpty()) {
            val toggle = viaToggle(leg.viaStops.size, expanded = false)
            toggle.setOnClickListener {
                expansionState.toggle(legIndex)
                val expanded = expansionState.isExpanded(legIndex)
                viaContainer.visibility = if (expanded) View.VISIBLE else View.GONE
                toggle.text = viaToggleText(leg.viaStops.size, expanded)
            }
            content.addView(toggle)
            content.addView(viaContainer)
        }

        content.addView(stationView("下車", leg.alightingStop, isEndpoint = true))
        return root
    }

    private fun legHeader(leg: RouteDetailLeg, color: Int): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        row.addView(TextView(activity).apply {
            text = leg.route
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minWidth = dp(44)
            background = roundedBackground(color, dp(5).toFloat())
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })

        val directionLabel = RouteDetailDisplayFormatter.directionLabel(leg.directionText)
        if (directionLabel != null) {
            row.addView(TextView(activity).apply {
                text = directionLabel
                textSize = 14f
                setTextColor(ContextCompat.getColor(activity, R.color.bus_text_secondary))
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = dp(10)
                }
            })
        }
        return row
    }

    private fun stationView(label: String?, stop: RouteDetailStop, isEndpoint: Boolean): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(if (isEndpoint) 12 else 8)
            }
        }

        container.addView(TextView(activity).apply {
            text = stop.displayName
            textSize = if (isEndpoint) 17f else 15f
            typeface = if (isEndpoint) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(ContextCompat.getColor(activity, R.color.bus_text_primary))
            includeFontPadding = false
        })

        if (label != null) {
            container.addView(TextView(activity).apply {
                text = label
                textSize = 13f
                setTextColor(ContextCompat.getColor(activity, R.color.bus_text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4)
                }
            })
        }
        return container
    }

    private fun viaToggle(count: Int, expanded: Boolean): TextView {
        return TextView(activity).apply {
            text = viaToggleText(count, expanded)
            textSize = 14f
            setTextColor(ContextCompat.getColor(activity, R.color.bus_chip_selected))
            setPadding(0, dp(8), 0, dp(10))
            isClickable = true
            isFocusable = true
            background = roundedBackground(
                ContextCompat.getColor(activity, R.color.bus_surface_variant),
                dp(6).toFloat()
            )
        }
    }

    private fun viaToggleText(count: Int, expanded: Boolean): String {
        return if (expanded) {
            "⌃ 途經 $count 個站 · 收起"
        } else {
            "⌄ 途經 $count 個站 · 展開"
        }
    }

    private fun transferConnectorView(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        }
        root.addView(TransferRailView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT)
        })
        root.addView(FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        })
        return root
    }

    private fun formatPrice(priceHkd: Double): String {
        return if (priceHkd == 0.0) "免費" else String.format(Locale.US, "HK$ %.1f", priceHkd)
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }

    private inner class SheetViews(route: BusRouteOption) {
        val root: LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(20))
            background = roundedBackground(Color.WHITE, dp(8).toFloat())
        }
        val closeButton: MaterialButton
        val loadingRow: LinearLayout
        val detailScroll: NestedScrollView
        val detailContainer: LinearLayout
        val errorContainer: LinearLayout
        val errorMessage: TextView
        val retryButton: MaterialButton

        init {
            root.addView(View(activity).apply {
                background = roundedBackground(ContextCompat.getColor(activity, R.color.bus_divider), dp(2).toFloat())
                layoutParams = LinearLayout.LayoutParams(dp(42), dp(4)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(16)
                }
            })

            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            header.addView(TextView(activity).apply {
                text = route.routeName
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(activity, R.color.bus_text_primary))
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            closeButton = MaterialButton(activity).apply {
                text = "×"
                textSize = 20f
                minWidth = 0
                minHeight = 0
                insetTop = 0
                insetBottom = 0
                setPadding(dp(10), 0, dp(10), 0)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.bus_surface_variant)
                )
                setTextColor(ContextCompat.getColor(activity, R.color.bus_text_primary))
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(40))
            }
            header.addView(closeButton)
            root.addView(header)

            loadingRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(24), 0, dp(24))
                visibility = View.GONE
            }
            loadingRow.addView(ProgressBar(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            })
            loadingRow.addView(TextView(activity).apply {
                text = "正在載入路線詳情"
                textSize = 15f
                setTextColor(ContextCompat.getColor(activity, R.color.bus_text_secondary))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(12)
                }
            })
            root.addView(loadingRow)

            errorContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(20), 0, dp(4))
                visibility = View.GONE
            }
            errorMessage = TextView(activity).apply {
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(activity, R.color.bus_text_primary))
            }
            retryButton = MaterialButton(activity).apply {
                text = "重試"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }
            }
            errorContainer.addView(errorMessage)
            errorContainer.addView(retryButton)
            root.addView(errorContainer)

            detailContainer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            detailScroll = NestedScrollView(activity).apply {
                id = R.id.routeDetailScroll
                visibility = View.GONE
                isFillViewport = false
                isNestedScrollingEnabled = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }
                addView(detailContainer)
            }
            root.addView(detailScroll)
        }
    }

    private inner class LegRailView(
        context: android.content.Context,
        private val color: Int
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(7).toFloat()
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val x = width / 2f
            paint.color = color
            canvas.drawLine(x, dp(18).toFloat(), x, (height - dp(18)).toFloat(), paint)
            dotPaint.color = Color.WHITE
            canvas.drawCircle(x, dp(18).toFloat(), dp(8).toFloat(), dotPaint)
            canvas.drawCircle(x, (height - dp(18)).toFloat(), dp(8).toFloat(), dotPaint)
            dotPaint.color = color
            dotPaint.style = Paint.Style.STROKE
            dotPaint.strokeWidth = dp(3).toFloat()
            canvas.drawCircle(x, dp(18).toFloat(), dp(8).toFloat(), dotPaint)
            canvas.drawCircle(x, (height - dp(18)).toFloat(), dp(8).toFloat(), dotPaint)
            dotPaint.style = Paint.Style.FILL
        }
    }

    private inner class TransferRailView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(activity, R.color.bus_divider)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val x = width / 2f
            var y = dp(6).toFloat()
            while (y < height) {
                canvas.drawCircle(x, y, dp(2).toFloat(), paint)
                y += dp(8)
            }
        }
    }

    companion object {
        private val LEG_COLORS = intArrayOf(
            Color.parseColor("#286DE8"),
            Color.parseColor("#8A4EA3"),
            Color.parseColor("#0D8A72"),
            Color.parseColor("#C87919")
        )
    }
}
