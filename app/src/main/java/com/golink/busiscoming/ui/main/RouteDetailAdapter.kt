package com.golink.busiscoming.ui.main

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import com.google.android.material.card.MaterialCardView

class RouteDetailAdapter(
    private val onToggleLeg: (Int) -> Unit,
    private val onRetry: () -> Unit,
    private val onTimelineStopSelected: (String) -> Unit = {}
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
            val card = MaterialCardView(root.context).apply {
                radius = dp(14).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = color(R.color.bus_divider)
                setCardBackgroundColor(color(R.color.bus_card_surface))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(16); marginEnd = dp(16); bottomMargin = dp(12)
                }
            }
            val content = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)) }
            content.addView(text(item.routeName, 22f, true, R.color.bus_text_primary))
            val arrival = item.plannedArrivalTime?.let { root.context.getString(R.string.route_detail_arrival, it) }
            val timing = listOfNotNull(
                root.context.getString(R.string.route_card_duration_value, item.durationMinutes),
                arrival
            ).joinToString("  ·  ")
            content.addView(text(timing, 15f, true, R.color.bus_text_primary).apply {
                layoutParams = marginTop(8)
            })
            content.addView(text(liveEta(item.firstLegEta), 14f, true, if (item.firstLegEta is WaitTimeState.Available) R.color.bus_wait_accent else R.color.bus_text_secondary).apply {
                layoutParams = marginTop(7)
            })
            val fare = price(item.priceHkd)
            val rideStopCount = when (val state = item.rideStopCount) {
                is RideStopCountState.Available -> root.context.getString(R.string.route_detail_total_stops, state.count)
                RideStopCountState.Loading -> root.context.getString(R.string.route_detail_stops_loading)
                RideStopCountState.Unavailable -> root.context.getString(R.string.route_detail_stops_unavailable)
            }
            content.addView(text("$fare  ·  $rideStopCount", 14f, false, R.color.bus_text_secondary).apply {
                layoutParams = marginTop(7)
            })
            val walkingRow = LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = marginTop(10) }
            walkingRow.addView(ImageView(root.context).apply {
                setImageResource(R.drawable.ic_walking_person); imageTintList = ColorStateList.valueOf(color(R.color.bus_text_secondary)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
            })
            walkingRow.addView(text(root.context.getString(R.string.route_detail_walk_distance, item.walkingDistanceMeters), 14f, false, R.color.bus_text_secondary).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(7) }
            })
            content.addView(walkingRow)
            if (!item.isWalkingDistanceComplete) content.addView(text(root.context.getString(R.string.route_detail_walking_incomplete), 12f, false, R.color.bus_text_secondary).apply { layoutParams = marginTop(5) })
            card.contentDescription = if (arrival == null) {
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
            card.addView(content)
            root.addView(card)
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
            root.addView(timelineRow(RouteTimelineRailView.Style.NODE, color(R.color.route_timeline_walk), title, details, true))
        }

        private fun bindWalking(item: RouteDetailUiItem.Walking) {
            val label = item.distanceMeters?.let { root.context.getString(R.string.route_detail_walk_distance, it) }
                ?: root.context.getString(R.string.route_detail_walk_unknown)
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
            val card = MaterialCardView(root.context).apply {
                radius = dp(12).toFloat(); cardElevation = 0f; strokeWidth = dp(1); strokeColor = color(R.color.bus_divider); setCardBackgroundColor(color(R.color.bus_card_surface))
            }
            val content = LinearLayout(root.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)) }
            val header = LinearLayout(root.context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            header.addView(text(item.route, 16f, true, R.color.bus_on_route_badge).apply {
                gravity = Gravity.CENTER; setPadding(dp(10), dp(5), dp(10), dp(5)); background = rounded(legColor(item.colorKey), dp(6).toFloat())
            })
            item.direction?.let { direction -> header.addView(text(root.context.getString(R.string.route_direction_format, direction), 14f, false, R.color.bus_text_secondary).apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) } }) }
            content.addView(header)
            item.liveEta?.let { eta -> content.addView(text(liveEta(eta), 14f, true, if (eta is WaitTimeState.Available) R.color.bus_wait_accent else R.color.bus_text_secondary).apply { layoutParams = marginTop(10) }) }
            val fare = item.fareHkd?.let(::price)
            val meta = listOfNotNull(fare, root.context.getString(R.string.route_detail_leg_stops, item.stopCount)).joinToString(" · ")
            content.addView(text(meta, 14f, false, R.color.bus_text_secondary).apply { layoutParams = marginTop(8) })
            card.contentDescription = root.context.getString(R.string.route_detail_leg_accessibility, item.route, item.direction.orEmpty(), liveEta(item.liveEta ?: WaitTimeState.Loading), meta)
            card.addView(content)
            root.addView(timelineRow(RouteTimelineRailView.Style.SOLID, legColor(item.colorKey), card))
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
