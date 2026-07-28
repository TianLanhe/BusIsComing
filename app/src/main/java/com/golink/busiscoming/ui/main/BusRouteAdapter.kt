package com.golink.busiscoming.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField

class BusRouteAdapter(
    private val onRouteClick: (BusRouteOption) -> Unit = {},
    private val onEtaClick: (BusRouteOption) -> Unit = {},
    private val onMonitorClick: (BusRouteOption) -> Unit = {},
    private val onPinAction: ((RouteCardItem, RoutePinAction) -> Unit)? = null
) : ListAdapter<BusRouteListItem, RecyclerView.ViewHolder>(BusRouteItemDiff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ROUTE -> BusRouteViewHolder(
                inflater.inflate(R.layout.item_bus_route, parent, false),
                onRouteClick,
                onEtaClick,
                onMonitorClick,
                onPinAction
            )
            VIEW_TYPE_DIVIDER -> UnpinnedDividerViewHolder(
                inflater.inflate(R.layout.item_unpinned_route_divider, parent, false)
            )
            else -> error("Unknown bus route item view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is BusRouteViewHolder -> holder.bind(getItem(position) as RouteCardItem)
            is UnpinnedDividerViewHolder -> holder.bind(getItem(position) as UnpinnedDividerItem)
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is RouteCardItem -> VIEW_TYPE_ROUTE
        is UnpinnedDividerItem -> VIEW_TYPE_DIVIDER
    }

    fun routeCardAt(position: Int): RouteCardItem? {
        if (position !in 0 until itemCount) return null
        return getItem(position) as? RouteCardItem
    }

    class BusRouteViewHolder(
        itemView: View,
        private val onRouteClick: (BusRouteOption) -> Unit,
        private val onEtaClick: (BusRouteOption) -> Unit,
        private val onMonitorClick: (BusRouteOption) -> Unit,
        private val onPinAction: ((RouteCardItem, RoutePinAction) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val binder = BusRouteCardBinder(itemView)

        fun bind(item: RouteCardItem) {
            binder.bind(
                item,
                BusRouteCardActions(
                    routeClick = onRouteClick,
                    etaClick = onEtaClick,
                    monitorClick = onMonitorClick,
                    pinAction = onPinAction
                )
            )
        }
    }

    class UnpinnedDividerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label: TextView = itemView.findViewById(R.id.unpinnedRouteDividerText)

        fun bind(item: UnpinnedDividerItem) {
            val context = itemView.context
            val sortLabel = context.getString(
                when (item.sortField) {
                    SortField.ROUTE -> R.string.sort_route
                    SortField.PRICE -> R.string.sort_price
                    SortField.DURATION -> R.string.sort_duration
                    SortField.ARRIVAL -> R.string.sort_arrival
                    SortField.WALKING_DISTANCE -> R.string.sort_walking
                }
            )
            val direction = if (item.sortDirection == SortDirection.ASC) "↑" else "↓"
            label.text = context.getString(
                R.string.unpinned_routes_divider,
                item.unpinnedCount,
                sortLabel,
                direction
            )
            itemView.contentDescription = label.text
            itemView.isClickable = false
            itemView.isFocusable = true
        }
    }

    private companion object {
        const val VIEW_TYPE_ROUTE = 1
        const val VIEW_TYPE_DIVIDER = 2
    }
}

object BusRouteItemDiff : DiffUtil.ItemCallback<BusRouteListItem>() {
    override fun areItemsTheSame(oldItem: BusRouteListItem, newItem: BusRouteListItem): Boolean {
        return oldItem::class == newItem::class && oldItem.stableId == newItem.stableId
    }

    override fun areContentsTheSame(oldItem: BusRouteListItem, newItem: BusRouteListItem): Boolean {
        return oldItem == newItem
    }
}

enum class RoutePinAction {
    PIN_TEMPORARY,
    PIN_PERSISTENT,
    CANCEL
}
