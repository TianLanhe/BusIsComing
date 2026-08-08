package com.golink.busiscoming.ui.main

import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.RouteDetailTransferType
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.ui.common.applyStableShortTextLayout
import com.google.android.material.button.MaterialButton

class RouteDetailAdapter(
    private val onToggleLeg: (Int) -> Unit,
    private val onRetry: () -> Unit,
    private val onTimelineStopSelected: (String) -> Unit = {},
    private val onSummarySegmentSelected: (RouteSummarySegment) -> Unit = {}
) : ListAdapter<RouteDetailUiItem, RouteDetailAdapter.Holder>(DIFF) {
    private var selectedStableId: String? = null

    init { setHasStableIds(true) }

    fun selectTimelineItem(stableId: String?) {
        val previous = selectedStableId
        if (previous == stableId) return
        selectedStableId = stableId
        listOfNotNull(previous, stableId).forEach { id ->
            currentList.indexOfFirst { it.stableId == id }.takeIf { it >= 0 }?.let(::notifyItemChanged)
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val root: LinearLayout) : RecyclerView.ViewHolder(root) {
        fun bind(item: RouteDetailUiItem) {
            root.removeAllViews()
            root.setPadding(0, 0, 0, 0)
            root.background = null
            root.contentDescription = null
            root.setOnClickListener(null)
            root.isClickable = false
            root.isFocusable = false
            root.isSelected = item.stableId == selectedStableId
            if (root.isSelected) {
                val selected = color(R.color.route_map_selected)
                root.setBackgroundColor((selected and 0x00FFFFFF) or 0x22000000)
            }
            when (item) {
                RouteDetailUiItem.Loading -> bindLoading()
                RouteDetailUiItem.Error -> bindError()
                is RouteDetailUiItem.Summary -> bindSummary(item)
                is RouteDetailUiItem.DynamicStatus -> bindDynamicStatus(item)
                is RouteDetailUiItem.Walking -> bindWalking(item)
                is RouteDetailUiItem.Stop -> bindStop(item)
                is RouteDetailUiItem.BusLeg -> bindBusLeg(item)
                is RouteDetailUiItem.ViaToggle -> bindToggle(item)
                is RouteDetailUiItem.ViaStop -> bindViaStop(item)
                is RouteDetailUiItem.Transfer -> bindTransfer(item)
                is RouteDetailUiItem.Endpoint -> bindEndpoint(item)
            }
        }

        private fun bindLoading() {
            root.gravity = Gravity.CENTER_HORIZONTAL
            root.setPadding(dp(20), dp(24), dp(20), dp(24))
            root.addView(ProgressBar(root.context).apply { layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)) })
            root.addView(text(root.context.getString(R.string.route_detail_loading), 15f, false, R.color.bus_text_secondary).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            })
        }

        private fun bindError() {
            root.setPadding(dp(20), dp(20), dp(20), dp(24))
            root.addView(text(root.context.getString(R.string.route_detail_unavailable), 16f, true, R.color.bus_text_primary))
            root.addView(MaterialButton(root.context).apply {
                text = root.context.getString(R.string.action_retry)
                contentDescription = root.context.getString(R.string.route_detail_retry)
                setOnClickListener { onRetry() }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)).apply { topMargin = dp(12) }
            })
        }

        private fun bindSummary(item: RouteDetailUiItem.Summary) {
            val content = LinearLayout(root.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(7), dp(16), dp(10))
            }
            val arrival = item.plannedArrivalTime?.let { root.context.getString(R.string.route_detail_arrival, it) }
            val timing = listOfNotNull(
                root.context.getString(R.string.route_card_duration_value, item.durationMinutes),
                arrival
            ).joinToString("  ·  ")
            content.addView(text(timing, 21f, true, R.color.bus_text_primary))

            val segmentRow = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val segmentTargets = mutableListOf<View>()
            item.segments.forEachIndexed { index, segment ->
                val target = summarySegment(segment)
                segmentTargets += target
                segmentRow.addView(target, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(30)
                ).apply { if (index > 0) marginStart = dp(2) })
            }
            content.addView(HorizontalScrollView(root.context).apply {
                isHorizontalScrollBarEnabled = false
                isFillViewport = false
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(segmentRow)
                layoutParams = marginTop(4)
            })
            content.post {
                if (content.parent === root) {
                    installSummarySegmentTouchDelegates(content, segmentTargets)
                }
            }

            val fare = price(item.priceHkd)
            val rideStopCount = when (val state = item.rideStopCount) {
                is RideStopCountState.Available -> root.context.getString(R.string.route_detail_total_stops, state.count)
                RideStopCountState.Loading -> root.context.getString(R.string.route_detail_stops_loading)
                RideStopCountState.Unavailable -> root.context.getString(R.string.route_detail_stops_unavailable)
            }
            val summaryWalking = if (item.isWalkingDistanceLoading) {
                root.context.getString(R.string.route_detail_walk_loading)
            } else {
                root.context.getString(R.string.route_detail_walk_distance, item.walkingDistanceMeters)
            }
            val meta = listOf(
                rideStopCount,
                summaryWalking,
                fare,
                liveEta(item.firstLegEta)
            ).joinToString("  ·  ")
            content.addView(text(meta, 13f, false, R.color.bus_text_secondary).apply {
                layoutParams = marginTop(3)
            })
            root.contentDescription = if (arrival == null) {
                root.context.getString(
                    R.string.route_detail_summary_accessibility_without_arrival,
                    item.routeName,
                    item.durationMinutes,
                    fare,
                    rideStopCount,
                    item.walkingDistanceMeters
                )
            } else {
                root.context.getString(
                    R.string.route_detail_summary_accessibility,
                    item.routeName,
                    item.durationMinutes,
                    arrival,
                    fare,
                    rideStopCount,
                    item.walkingDistanceMeters
                )
            }
            root.addView(content)
        }

        private fun summarySegment(segment: RouteSummarySegment): View {
            val visible = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                isBaselineAligned = true
                setPadding(dp(4), dp(2), dp(4), dp(2))
                minimumHeight = dp(30)
                background = rounded(
                    when (segment.kind) {
                        RouteSummarySegmentKind.BUS -> legColor(segment.colorKey ?: 0)
                        RouteSummarySegmentKind.WALKING,
                        RouteSummarySegmentKind.SAME_STOP_TRANSFER -> color(R.color.bus_surface_variant)
                    },
                    dp(6).toFloat()
                )
            }
            when (segment.kind) {
                RouteSummarySegmentKind.BUS -> visible.addView(
                    text(segment.routeLabel.orEmpty(), 17f, true, R.color.bus_on_route_badge)
                )
                RouteSummarySegmentKind.WALKING,
                RouteSummarySegmentKind.SAME_STOP_TRANSFER -> visible.addView(ImageView(root.context).apply {
                    setImageResource(
                        if (segment.kind == RouteSummarySegmentKind.WALKING) {
                            R.drawable.ic_walking_person
                        } else {
                            R.drawable.ic_swap_curved
                        }
                    )
                    imageTintList = ColorStateList.valueOf(color(R.color.bus_text_secondary))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
                })
            }
            segment.durationMinutes?.let { minutes ->
                visible.addView(
                    text(minutes.toString(), 12f, false, if (segment.kind == RouteSummarySegmentKind.BUS) {
                        R.color.bus_on_route_badge
                    } else {
                        R.color.bus_text_secondary
                    }).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(3) }
                    }
                )
            }
            val label = when (segment.kind) {
                RouteSummarySegmentKind.BUS -> segment.routeLabel.orEmpty()
                RouteSummarySegmentKind.WALKING -> root.context.getString(R.string.route_detail_walk_unknown)
                RouteSummarySegmentKind.SAME_STOP_TRANSFER -> root.context.getString(R.string.route_detail_same_stop_transfer)
            }
            val description = segment.durationMinutes?.let {
                "$label, ${root.context.getString(R.string.route_card_duration_value, it)}"
            } ?: label
            return FrameLayout(root.context).apply {
                minimumHeight = dp(30)
                isClickable = true
                isFocusable = true
                contentDescription = description
                foreground = ContextCompat.getDrawable(root.context, android.R.drawable.list_selector_background)
                addView(visible, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(30),
                    Gravity.CENTER_VERTICAL
                ))
                setOnClickListener { onSummarySegmentSelected(segment) }
            }
        }

        private fun installSummarySegmentTouchDelegates(
            parent: ViewGroup,
            targets: List<View>
        ) {
            val delegate = SummarySegmentTouchDelegate(parent)
            targets.forEach { target ->
                val bounds = Rect(0, 0, target.width, target.height)
                parent.offsetDescendantRectToMyCoords(target, bounds)
                val missingHeight = (dp(48) - bounds.height()).coerceAtLeast(0)
                bounds.top -= missingHeight / 2
                bounds.bottom += missingHeight - missingHeight / 2
                delegate.add(TouchDelegate(bounds, target))
            }
            parent.touchDelegate = delegate
        }

        private fun bindDynamicStatus(item: RouteDetailUiItem.DynamicStatus) {
            val message = when (item.status) {
                RouteDynamicDetailStatus.CURRENT -> return
                RouteDynamicDetailStatus.REFRESHING -> R.string.route_detail_dynamic_refreshing
                RouteDynamicDetailStatus.STALE_AFTER_ERROR -> R.string.route_detail_dynamic_refresh_failed
            }
            root.setPadding(dp(20), 0, dp(20), dp(12))
            root.addView(text(root.context.getString(message), 13f, false, R.color.bus_text_secondary))
        }

        private fun bindEndpoint(item: RouteDetailUiItem.Endpoint) {
            val title = item.name ?: root.context.getString(if (item.isOrigin) R.string.route_detail_origin else R.string.route_detail_destination)
            val details = item.plannedTime?.let { root.context.getString(R.string.route_detail_planned_time, it) }
            root.addView(timelineRow(
                if (item.isOrigin) RouteTimelineRailView.Style.ORIGIN else RouteTimelineRailView.Style.DESTINATION,
                color(if (item.isOrigin) R.color.bus_chip_selected else R.color.bus_danger),
                title,
                details,
                true
            ))
        }

        private fun bindWalking(item: RouteDetailUiItem.Walking) {
            val label = when {
                item.isLoading -> root.context.getString(R.string.route_detail_walk_loading)
                item.isUnavailable -> root.context.getString(R.string.route_detail_walk_unavailable)
                item.distanceMeters != null && item.approximateMinutes != null -> root.context.getString(
                    R.string.route_detail_walk_distance_with_time,
                    item.distanceMeters,
                    item.approximateMinutes
                )
                item.distanceMeters != null -> root.context.getString(R.string.route_detail_walk_distance, item.distanceMeters)
                else -> root.context.getString(R.string.route_detail_walk_unavailable)
            }
            val content = LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            content.addView(ImageView(root.context).apply {
                setImageResource(R.drawable.ic_walking_person); imageTintList = ColorStateList.valueOf(color(R.color.bus_text_secondary)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            })
            content.addView(text(label, 15f, false, R.color.bus_text_secondary).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) } })
            root.addView(timelineRow(RouteTimelineRailView.Style.DASHED, color(R.color.route_timeline_walk), content))
            root.contentDescription = label
        }

        private fun bindStop(item: RouteDetailUiItem.Stop) {
            val label = root.context.getString(if (item.isBoarding) R.string.boarding_stop else R.string.alighting_stop)
            val time = item.plannedTime?.let { root.context.getString(R.string.route_detail_planned_time, it) }
            root.addView(timelineRow(RouteTimelineRailView.Style.SOLID, legColor(item.colorKey), item.stop.displayName, listOfNotNull(label, time).joinToString(" · "), true))
            bindTimelineSelection(item.stableId)
        }

        private fun bindBusLeg(item: RouteDetailUiItem.BusLeg) {
            val content = LinearLayout(root.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val header = LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(text(item.route, 16f, true, R.color.bus_on_route_badge).apply {
                gravity = Gravity.CENTER; setPadding(dp(10), dp(5), dp(10), dp(5)); background = rounded(legColor(item.colorKey), dp(6).toFloat())
            })
            item.direction?.let { direction -> header.addView(text(root.context.getString(R.string.route_direction_format, direction), 14f, false, R.color.bus_text_secondary).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) } }) }
                ?: header.addView(View(root.context), LinearLayout.LayoutParams(0, 1, 1f))
            item.fareHkd?.let { fare ->
                header.addView(text(price(fare), 13f, false, R.color.bus_text_secondary).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(8) }
                })
            }
            content.addView(header)
            content.contentDescription = listOfNotNull(
                item.route,
                item.direction,
                item.fareHkd?.let(::price)
            ).joinToString(", ")
            root.addView(timelineRow(RouteTimelineRailView.Style.SOLID, legColor(item.colorKey), content))
        }

        private fun bindToggle(item: RouteDetailUiItem.ViaToggle) {
            val row = LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(48)
                setPadding(0, 0, dp(8), 0)
            }
            val label = root.context.getString(if (item.expanded) R.string.via_stops_collapse else R.string.via_stops_expand, item.count)
            row.addView(text(label, 14f, true, R.color.bus_text_primary).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(ImageView(root.context).apply {
                setImageResource(R.drawable.ic_chevron_down); rotation = if (item.expanded) 180f else 0f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            })
            root.contentDescription = label
            root.isClickable = true
            root.isFocusable = true
            root.setBackgroundResource(android.R.drawable.list_selector_background)
            root.setOnClickListener { onToggleLeg(item.legIndex) }
            root.addView(timelineRow(RouteTimelineRailView.Style.SOLID, legColor(item.colorKey), row))
        }

        private fun bindViaStop(item: RouteDetailUiItem.ViaStop) {
            root.addView(timelineRow(RouteTimelineRailView.Style.SOLID, legColor(item.colorKey), item.stop.displayName, null, false))
            bindTimelineSelection(item.stableId)
        }

        private fun bindTimelineSelection(stableId: String) {
            root.isClickable = true
            root.isFocusable = true
            root.setOnClickListener { onTimelineStopSelected(stableId) }
        }

        private fun bindTransfer(item: RouteDetailUiItem.Transfer) {
            val label = root.context.getString(if (item.type == RouteDetailTransferType.SAME_STOP) R.string.route_detail_same_stop_transfer else R.string.route_detail_walk_transfer)
            root.addView(timelineRow(RouteTimelineRailView.Style.NODE, color(R.color.route_timeline_walk), label, null, true))
        }

        private fun timelineRow(style: RouteTimelineRailView.Style, railColor: Int, title: String, detail: String?, bold: Boolean): View {
            val content = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL }
            content.addView(text(title, if (bold) 17f else 14f, bold, R.color.bus_text_primary))
            detail?.takeIf { it.isNotBlank() }?.let { content.addView(text(it, 13f, false, R.color.bus_text_secondary).apply { layoutParams = marginTop(4) }) }
            return timelineRow(style, railColor, content)
        }

        private fun timelineRow(style: RouteTimelineRailView.Style, railColor: Int, content: View): View {
            return LinearLayout(root.context).apply {
                orientation = LinearLayout.HORIZONTAL; minimumHeight = dp(52); setPadding(dp(8), 0, dp(16), 0)
                addView(RouteTimelineRailView(root.context).apply { this.style = style; this.railColor = railColor; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO; layoutParams = LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT) })
                addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.CENTER_VERTICAL; topMargin = dp(6); bottomMargin = dp(6) })
            }
        }

        private fun text(value: CharSequence, size: Float, bold: Boolean, colorRes: Int) = TextView(root.context).apply {
            text = value; textSize = size; setTextColor(color(colorRes)); includeFontPadding = false
            applyStableShortTextLayout(Gravity.START)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

        private fun liveEta(state: WaitTimeState): String = when (state) {
            is WaitTimeState.Available -> if (state.minutes <= 0) root.context.getString(R.string.route_detail_live_due) else root.context.getString(R.string.route_detail_live_eta, state.minutes)
            WaitTimeState.Loading -> root.context.getString(R.string.route_detail_live_loading)
            WaitTimeState.NoArrivals -> root.context.getString(R.string.route_detail_live_none)
            is WaitTimeState.Unavailable -> root.context.getString(R.string.route_detail_live_unavailable)
        }

        private fun price(value: Double): String = if (value == 0.0) root.context.getString(R.string.price_free) else root.context.getString(R.string.price_hkd, value)
        private fun legColor(key: Int): Int = color(intArrayOf(R.color.route_leg_0, R.color.route_leg_1, R.color.route_leg_2, R.color.route_leg_3)[key.mod(4)])
        private fun color(res: Int) = ContextCompat.getColor(root.context, res)
        private fun rounded(fill: Int, radius: Float) = GradientDrawable().apply { cornerRadius = radius; setColor(fill) }
        private fun marginTop(value: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(value) }
        private fun dp(value: Int) = (value * root.resources.displayMetrics.density).toInt()
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RouteDetailUiItem>() {
            override fun areItemsTheSame(oldItem: RouteDetailUiItem, newItem: RouteDetailUiItem) = oldItem.stableId == newItem.stableId
            override fun areContentsTheSame(oldItem: RouteDetailUiItem, newItem: RouteDetailUiItem) = oldItem == newItem
        }
    }
}

private class SummarySegmentTouchDelegate(anchor: View) : TouchDelegate(Rect(), anchor) {
    private val delegates = mutableListOf<TouchDelegate>()

    fun add(delegate: TouchDelegate) {
        delegates += delegate
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean =
        delegates.any { it.onTouchEvent(event) }
}
