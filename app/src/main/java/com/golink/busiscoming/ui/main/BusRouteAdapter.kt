package com.golink.busiscoming.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.model.BusRouteOption

class BusRouteAdapter(
    private val onRouteClick: (BusRouteOption) -> Unit = {},
    private val onEtaClick: (BusRouteOption) -> Unit = {},
    private val onMonitorClick: (BusRouteOption) -> Unit = {}
) : RecyclerView.Adapter<BusRouteAdapter.BusRouteViewHolder>() {
    private val routes = mutableListOf<BusRouteOption>()

    fun submitList(newRoutes: List<BusRouteOption>) {
        routes.clear()
        routes.addAll(newRoutes)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusRouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus_route, parent, false)
        return BusRouteViewHolder(view, onRouteClick, onEtaClick, onMonitorClick)
    }

    override fun onBindViewHolder(holder: BusRouteViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount(): Int = routes.size

    class BusRouteViewHolder(
        itemView: View,
        private val onRouteClick: (BusRouteOption) -> Unit,
        private val onEtaClick: (BusRouteOption) -> Unit,
        private val onMonitorClick: (BusRouteOption) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val binder = BusRouteCardBinder(itemView)

        fun bind(route: BusRouteOption) {
            binder.bind(
                route,
                BusRouteCardActions(
                    routeClick = onRouteClick,
                    etaClick = onEtaClick,
                    monitorClick = onMonitorClick
                )
            )
        }
    }
}
