package com.golink.busiscoming.ui.main

data class MainActivityImeNavigationSnapshot(
    val isEnabled: Boolean,
    val isClickable: Boolean,
    val importantForAccessibility: Int,
    val menuItemEnabledStates: List<Boolean>
)

sealed interface MainActivityImeNavigationTransition {
    data object ApplyGuard : MainActivityImeNavigationTransition

    data class Restore(
        val snapshot: MainActivityImeNavigationSnapshot
    ) : MainActivityImeNavigationTransition
}

class MainActivityImeNavigationPolicy {
    private var restoreSnapshot: MainActivityImeNavigationSnapshot? = null

    fun update(
        imeVisible: Boolean,
        current: MainActivityImeNavigationSnapshot
    ): MainActivityImeNavigationTransition? {
        if (imeVisible) {
            if (restoreSnapshot != null) return null
            restoreSnapshot = current
            return MainActivityImeNavigationTransition.ApplyGuard
        }

        val snapshot = restoreSnapshot ?: return null
        restoreSnapshot = null
        return MainActivityImeNavigationTransition.Restore(snapshot)
    }
}
