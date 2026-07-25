package com.golink.busiscoming.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class PlacePairEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    val originInputLayout: TextInputLayout
    val originInput: MaterialAutoCompleteTextView
    val originLoading: View
    val originCandidateList: RecyclerView
    val destinationInputLayout: TextInputLayout
    val destinationInput: MaterialAutoCompleteTextView
    val destinationLoading: View
    val destinationCandidateList: RecyclerView
    val currentLocationButton: View
    val swapButton: View
    private val originToolSlot: View
    private val destinationToolSlot: View
    private val swapSlot: View

    init {
        LayoutInflater.from(context).inflate(R.layout.view_place_pair_editor, this, true)
        originInputLayout = findViewById(R.id.placePairOriginLayout)
        originInput = findViewById(R.id.placePairOriginInput)
        originLoading = findViewById(R.id.placePairOriginLoading)
        originCandidateList = findViewById(R.id.placePairOriginCandidateList)
        destinationInputLayout = findViewById(R.id.placePairDestinationLayout)
        destinationInput = findViewById(R.id.placePairDestinationInput)
        destinationLoading = findViewById(R.id.placePairDestinationLoading)
        destinationCandidateList = findViewById(R.id.placePairDestinationCandidateList)
        currentLocationButton = findViewById(R.id.placePairCurrentLocationButton)
        swapButton = findViewById(R.id.placePairSwapButton)
        originToolSlot = findViewById(R.id.placePairOriginToolSlot)
        destinationToolSlot = findViewById(R.id.placePairDestinationToolSlot)
        swapSlot = findViewById(R.id.placePairSwapSlot)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            requestToolAlignment()
        }
        requestToolAlignment()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (viewTreeObserver.isAlive) {
                        viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    alignToolSlots()
                    return true
                }
            }
        )
    }

    fun requestToolAlignment() {
        post(::alignToolSlots)
    }

    private fun alignToolSlots() {
        if (
            !isLaidOut ||
            originToolSlot.height == 0 ||
            destinationToolSlot.height == 0 ||
            swapSlot.height == 0
        ) {
            return
        }
        val originBounds = verticalBoundsInSelf(originInput)
        val destinationBounds = verticalBoundsInSelf(destinationInput)
        alignSlot(
            originToolSlot,
            PlacePairToolAlignment.centeredTop(
                inputTop = originBounds.first,
                inputBottom = originBounds.second,
                toolHeight = originToolSlot.height
            )
        )
        alignSlot(
            destinationToolSlot,
            PlacePairToolAlignment.centeredTop(
                inputTop = destinationBounds.first,
                inputBottom = destinationBounds.second,
                toolHeight = destinationToolSlot.height
            )
        )
        alignSlot(
            swapSlot,
            PlacePairToolAlignment.swapTop(
                originCenter = (originBounds.first + originBounds.second) / 2,
                destinationCenter =
                    (destinationBounds.first + destinationBounds.second) / 2,
                originCandidateOccupiedHeight = originCandidateOccupiedHeight(),
                toolHeight = swapSlot.height
            )
        )
    }

    private fun alignSlot(slot: View, targetTop: Int) {
        val currentTop = verticalBoundsInSelf(slot).first
        val delta = targetTop - currentTop
        if (delta != 0) {
            slot.translationY += delta
        }
    }

    private fun verticalBoundsInSelf(view: View): Pair<Int, Int> {
        val rootLocation = IntArray(2)
        val viewLocation = IntArray(2)
        getLocationOnScreen(rootLocation)
        view.getLocationOnScreen(viewLocation)
        val top = viewLocation[1] - rootLocation[1]
        return top to top + view.height
    }

    private fun originCandidateOccupiedHeight(): Int {
        if (originCandidateList.visibility != View.VISIBLE) return 0
        val margins = originCandidateList.layoutParams as? ViewGroup.MarginLayoutParams
        return originCandidateList.measuredHeight +
            (margins?.topMargin ?: 0) +
            (margins?.bottomMargin ?: 0)
    }
}
