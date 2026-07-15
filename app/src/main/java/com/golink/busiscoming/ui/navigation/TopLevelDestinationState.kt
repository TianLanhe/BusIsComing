package com.golink.busiscoming.ui.navigation

enum class TopLevelDestination {
    FREQUENT_ROUTES,
    SEARCH,
    SETTINGS
}

class TopLevelDestinationState(
    initial: TopLevelDestination = TopLevelDestination.FREQUENT_ROUTES
) {
    var selected: TopLevelDestination = initial
        private set

    fun select(destination: TopLevelDestination) {
        selected = destination
    }
}

class RouteQueryGeneration {
    private var value = 0

    fun begin(): Int = ++value

    fun invalidate() {
        value += 1
    }

    fun isCurrent(generation: Int): Boolean = value == generation
}
