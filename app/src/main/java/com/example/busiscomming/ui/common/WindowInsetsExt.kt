package com.example.busiscomming.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.busiscomming.R

fun View.applyStatusBarPadding() {
    val initialPadding = getTag(R.id.tag_initial_padding) as? InitialPadding
        ?: InitialPadding(paddingLeft, paddingTop, paddingRight, paddingBottom).also {
            setTag(R.id.tag_initial_padding, it)
        }

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.setPadding(
            initialPadding.left,
            initialPadding.top + statusBarTop,
            initialPadding.right,
            initialPadding.bottom
        )
        insets
    }

    requestApplyInsetsWhenAttached()
}

private fun View.requestApplyInsetsWhenAttached() {
    if (ViewCompat.isAttachedToWindow(this)) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(view)
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        })
    }
}

private data class InitialPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
