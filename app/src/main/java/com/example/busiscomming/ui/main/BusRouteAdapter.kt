package com.example.busiscomming.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscomming.R
import com.example.busiscomming.data.model.BusRouteOption
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
        private val waitTimeText: TextView = itemView.findViewById(R.id.busWaitTimeText)

        fun bind(route: BusRouteOption) {
            routeNameText.text = route.routeName
            priceText.text = String.format(Locale.US, "HK$%.1f", route.priceHkd)
            waitTimeText.text = "约 ${route.waitMinutes} 分钟"
        }
    }
}
