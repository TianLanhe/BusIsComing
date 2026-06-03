package com.example.busiscomming.ui.manage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscomming.R
import com.example.busiscomming.data.model.RouteConfig
import com.google.android.material.button.MaterialButton

class RouteConfigAdapter(
    private val onEdit: (RouteConfig) -> Unit,
    private val onDelete: (RouteConfig) -> Unit
) : RecyclerView.Adapter<RouteConfigAdapter.RouteConfigViewHolder>() {
    private val routes = mutableListOf<RouteConfig>()

    fun submitList(newRoutes: List<RouteConfig>) {
        routes.clear()
        routes.addAll(newRoutes)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteConfigViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_config, parent, false)
        return RouteConfigViewHolder(view)
    }

    override fun onBindViewHolder(holder: RouteConfigViewHolder, position: Int) {
        holder.bind(routes[position])
    }

    override fun getItemCount(): Int = routes.size

    inner class RouteConfigViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val routeNameText: TextView = itemView.findViewById(R.id.routeNameText)
        private val routePathText: TextView = itemView.findViewById(R.id.routePathText)
        private val editButton: MaterialButton = itemView.findViewById(R.id.editRouteButton)
        private val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteRouteButton)

        fun bind(route: RouteConfig) {
            routeNameText.text = route.name
            routePathText.text = route.pathLabel()
            editButton.setOnClickListener { onEdit(route) }
            deleteButton.setOnClickListener { onDelete(route) }
        }
    }
}
