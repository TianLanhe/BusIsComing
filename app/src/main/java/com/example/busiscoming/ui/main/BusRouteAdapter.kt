package com.example.busiscoming.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.WaitTimeState

class BusRouteAdapter(
    private val onRouteClick: (BusRouteOption) -> Unit = {}
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
        return BusRouteViewHolder(view, onRouteClick)
    }

    override fun onBindViewHolder(holder: BusRouteViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount(): Int = routes.size

    class BusRouteViewHolder(
        itemView: View,
        private val onRouteClick: (BusRouteOption) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val routeNameText: TextView = itemView.findViewById(R.id.busRouteNameText)
        private val arrivalText: TextView = itemView.findViewById(R.id.busArrivalText)
        private val stopPreviewText: TextView = itemView.findViewById(R.id.busStopPreviewText)
        private val routeInfoText: TextView = itemView.findViewById(R.id.busRouteInfoText)

        fun bind(route: BusRouteOption) {
            routeNameText.text = route.routeName
            arrivalText.text = RouteResultCardFormatter.waitStatus(route.waitTimeState)
            arrivalText.setTextColor(waitStatusColor(route.waitTimeState))
            routeInfoText.text = RouteResultCardFormatter.info(route)

            val preview = route.stopPreview
            if (preview == null) {
                stopPreviewText.visibility = View.GONE
            } else {
                stopPreviewText.text = preview.displayText()
                stopPreviewText.visibility = View.VISIBLE
            }
            itemView.setOnClickListener { onRouteClick(route) }
        }

        private fun waitStatusColor(waitTimeState: WaitTimeState): Int {
            val colorRes = when (waitTimeState) {
                is WaitTimeState.Available -> R.color.bus_wait_accent
                WaitTimeState.Loading -> R.color.bus_text_secondary
                WaitTimeState.Unavailable -> R.color.bus_wait_unavailable
            }
            return ContextCompat.getColor(itemView.context, colorRes)
        }

    }
}
