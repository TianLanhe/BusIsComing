package com.golink.busiscoming.ui.main

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat

object SearchTripEditorTransitionPolicy {
    const val DURATION_MS = 240L

    fun shouldAnimate(
        requested: Boolean,
        isLaidOut: Boolean,
        isAttached: Boolean,
        systemAnimationsEnabled: Boolean,
        lifecycleStarted: Boolean
    ): Boolean =
        requested &&
            isLaidOut &&
            isAttached &&
            systemAnimationsEnabled &&
            lifecycleStarted
}

/**
 * 協調搜尋編輯器與「本次行程」的互斥 Content Transform。
 *
 * 控制器只處理展示；查詢、保存與結果生命週期仍由 [SearchPresentationState] 擁有。
 */
class SearchTripEditorTransitionController(
    private val parent: ViewGroup,
    private val editor: View,
    private val tripContext: View,
    private val lifecycleStarted: () -> Boolean
) {
    private var renderedEditorVisibility: Boolean? = null

    fun render(showEditor: Boolean, animate: Boolean) {
        if (renderedEditorVisibility == showEditor) {
            applyFinalState(showEditor)
            return
        }
        val shouldAnimate = SearchTripEditorTransitionPolicy.shouldAnimate(
            requested = animate && renderedEditorVisibility != null,
            isLaidOut = ViewCompat.isLaidOut(parent),
            isAttached = ViewCompat.isAttachedToWindow(parent),
            systemAnimationsEnabled = systemAnimationsEnabled(),
            lifecycleStarted = lifecycleStarted()
        )
        val outgoing = if (showEditor) tripContext else editor
        outgoing.isEnabled = false
        outgoing.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        if (shouldAnimate) {
            TransitionManager.beginDelayedTransition(
                parent,
                TransitionSet()
                    .setOrdering(TransitionSet.ORDERING_TOGETHER)
                    .addTransition(ChangeBounds())
                    .addTransition(Fade())
                    .setDuration(SearchTripEditorTransitionPolicy.DURATION_MS)
            )
        }
        applyFinalState(showEditor)
        renderedEditorVisibility = showEditor
    }

    private fun applyFinalState(showEditor: Boolean) {
        editor.visibility = if (showEditor) View.VISIBLE else View.GONE
        editor.isEnabled = showEditor
        editor.importantForAccessibility = if (showEditor) {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        tripContext.visibility = if (showEditor) View.GONE else View.VISIBLE
        tripContext.isEnabled = !showEditor
        tripContext.importantForAccessibility = if (showEditor) {
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        }
    }

    private fun systemAnimationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            Settings.Global.getFloat(
                parent.context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }
}
