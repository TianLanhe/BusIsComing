package com.golink.busiscoming.ui.main

import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.model.RoutePinSessionState

object RoutePinSessionReconciler {
    fun reconcile(
        previousRoutes: List<RouteConfig>,
        currentRoutes: List<RouteConfig>,
        sessionState: RoutePinSessionState
    ) {
        val currentById = currentRoutes.associateBy { it.id }
        previousRoutes.forEach { previous ->
            val current = currentById[previous.id]
            when {
                current == null -> sessionState.clearJourney(previous.id)
                current.origin != previous.origin || current.destination != previous.destination ->
                    sessionState.clearJourney(previous.id)
            }
        }
    }
}
