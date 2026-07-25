package com.golink.busiscoming.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
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
    }
}
