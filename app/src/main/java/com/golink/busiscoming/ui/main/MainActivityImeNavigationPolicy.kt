package com.golink.busiscoming.ui.main

data class MainActivityImeNavigationPolicy(
    val isEnabled: Boolean,
    val isClickable: Boolean,
    val hidesDescendantsFromAccessibility: Boolean
) {
    companion object {
        fun resolve(imeVisible: Boolean): MainActivityImeNavigationPolicy {
            return if (imeVisible) {
                MainActivityImeNavigationPolicy(
                    isEnabled = false,
                    isClickable = false,
                    hidesDescendantsFromAccessibility = true
                )
            } else {
                MainActivityImeNavigationPolicy(
                    isEnabled = true,
                    isClickable = true,
                    hidesDescendantsFromAccessibility = false
                )
            }
        }
    }
}
