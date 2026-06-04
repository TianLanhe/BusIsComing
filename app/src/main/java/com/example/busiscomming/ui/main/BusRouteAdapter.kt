package com.example.busiscomming.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscomming.R
import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.WaitTimeState
import java.util.Locale

class BusRouteAdapter : RecyclerView.Adapter<BusRouteAdapter.BusRouteViewHolder>() {
    private val routes = mutableListOf<BusRouteOption>()

    fun submitList(newRoutes: List<BusRouteOption>) {
        routes.clear()
        routes.addAll(newRoutes)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BusRouteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bus_route, parent, false)
        return BusRouteViewHolder(view)
    }

    override fun onBindViewHolder(holder: BusRouteViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount(): Int = routes.size

    class BusRouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val routeNameText: TextView = itemView.findViewById(R.id.busRouteNameText)
        private val priceText: TextView = itemView.findViewById(R.id.busPriceText)
        private val durationText: TextView = itemView.findViewById(R.id.busDurationText)
        private val arrivalText: TextView = itemView.findViewById(R.id.busArrivalText)
        private val walkingDistanceText: TextView = itemView.findViewById(R.id.busWalkingDistanceText)

        fun bind(route: BusRouteOption) {
            routeNameText.text = route.routeName
            priceText.text = formatPrice(route.priceHkd)
            durationText.text = "总耗时 ${route.durationMinutes} 分钟"
            arrivalText.text = formatWaitTime(route.waitTimeState)
            walkingDistanceText.text = "步行 ${route.walkingDistanceMeters} 米"
        }

        private fun formatPrice(priceHkd: Double): String {
            return if (priceHkd == 0.0) {
                "免费"
            } else {
                String.format(Locale.US, "HK$ %.1f", priceHkd)
            }
        }

        private fun formatWaitTime(waitTimeState: WaitTimeState): String {
            return when (waitTimeState) {
                is WaitTimeState.Available -> "约 ${waitTimeState.minutes} 分钟"
                WaitTimeState.Loading -> "候车查询中"
                WaitTimeState.Unavailable -> "候车暂无"
            }
        }
    }
}
