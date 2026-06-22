package com.example.busiscoming.ui.common

import android.content.Context
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscoming.R
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.repository.PlaceSearchRepository
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.ExecutorService

class PlaceInputController(
    context: Context,
    private val input: MaterialAutoCompleteTextView,
    private val inputLayout: TextInputLayout,
    private val loadingView: View,
    private val candidateList: RecyclerView,
    private val placeSearchRepository: PlaceSearchRepository,
    private val mainHandler: Handler,
    private val searchExecutor: ExecutorService,
    private val isActive: () -> Boolean,
    private val onCandidateVisibilityChanged: (Boolean) -> Unit = {},
    private val onPlaceSelected: (Place) -> Unit = {}
) {
    private val rowHeightPx = dp(context, CANDIDATE_ROW_HEIGHT_DP)
    private val adapter = PlaceCandidateAdapter(context) { place ->
        setSelectedPlace(place)
        onPlaceSelected(place)
    }
    private var suppressTextChange = false
    private var searchSequence = 0
    private var pendingSearch: Runnable? = null
    private var visibleHeightPx = context.resources.displayMetrics.heightPixels

    var selectedPlace: Place? = null
        private set

    init {
        input.threshold = MIN_SEARCH_LENGTH
        candidateList.layoutManager = LinearLayoutManager(context)
        candidateList.adapter = adapter
        candidateList.isNestedScrollingEnabled = true
        candidateList.visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(candidateList) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            visibleHeightPx = (view.rootView.height - imeBottom).coerceAtLeast(rowHeightPx * MIN_VISIBLE_ROWS)
            updateCandidateHeight()
            insets
        }
        input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus && adapter.itemCount > 0) {
                showCandidates()
            } else if (!hasFocus) {
                hideCandidates()
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                handleTextChanged(s?.toString()?.trim().orEmpty())
            }
        })
    }

    fun text(): String = input.text?.toString().orEmpty()

    fun setSelectedPlace(place: Place) {
        cancelPendingSearch()
        adapter.submitPlaces(emptyList())
        hideCandidates()
        selectedPlace = place
        suppressTextChange = true
        input.setText(place.name, false)
        input.setSelection(input.text?.length ?: 0)
        suppressTextChange = false
        clearMessages()
    }

    fun setRawText(text: String) {
        cancelPendingSearch()
        adapter.submitPlaces(emptyList())
        hideCandidates()
        selectedPlace = null
        suppressTextChange = true
        input.setText(text, false)
        input.setSelection(input.text?.length ?: 0)
        suppressTextChange = false
        clearMessages()
    }

    fun swapWith(other: PlaceInputController) {
        val thisText = text()
        val otherText = other.text()
        val thisPlace = selectedPlace
        val otherPlace = other.selectedPlace

        if (otherPlace != null) {
            setSelectedPlace(otherPlace)
        } else {
            setRawText(otherText)
        }

        if (thisPlace != null) {
            other.setSelectedPlace(thisPlace)
        } else {
            other.setRawText(thisText)
        }
    }

    fun hideCandidates(): Boolean {
        if (candidateList.visibility != View.VISIBLE) return false
        candidateList.visibility = View.GONE
        onCandidateVisibilityChanged(false)
        return true
    }

    fun setError(message: String?) {
        inputLayout.error = message
    }

    fun clearMessages() {
        inputLayout.error = null
        inputLayout.helperText = null
    }

    fun dispose() {
        pendingSearch?.let { mainHandler.removeCallbacks(it) }
        pendingSearch = null
        setSearchLoading(false)
        hideCandidates()
        ViewCompat.setOnApplyWindowInsetsListener(candidateList, null)
    }

    private fun handleTextChanged(keyword: String) {
        if (suppressTextChange) return

        selectedPlace = null
        clearMessages()
        schedulePlaceSearch(keyword)
    }

    private fun schedulePlaceSearch(keyword: String) {
        cancelPendingSearch(hideLoading = false)
        searchSequence += 1

        if (keyword.length < MIN_SEARCH_LENGTH) {
            adapter.submitPlaces(emptyList())
            hideCandidates()
            setSearchLoading(false)
            return
        }

        setSearchLoading(true)
        val searchId = searchSequence
        val searchRunnable = Runnable {
            runPlaceSearch(keyword, searchId)
        }
        pendingSearch = searchRunnable
        mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
    }

    private fun runPlaceSearch(keyword: String, searchId: Int) {
        searchExecutor.execute {
            val result = runCatching { placeSearchRepository.searchPlaces(keyword) }
            mainHandler.post {
                if (!isActive() || searchSequence != searchId) return@post

                setSearchLoading(false)
                result
                    .onSuccess { places ->
                        updatePlaceCandidates(PlaceCandidatePresentationPolicy.limit(places))
                    }
                    .onFailure {
                        adapter.submitPlaces(emptyList())
                        hideCandidates()
                        inputLayout.helperText = null
                        inputLayout.error = "地點搜尋失敗，請稍後重試"
                    }
            }
        }
    }

    private fun updatePlaceCandidates(places: List<Place>) {
        adapter.submitPlaces(places)
        inputLayout.error = null
        if (places.isEmpty()) {
            hideCandidates()
            inputLayout.helperText = "沒有匹配地點"
        } else {
            inputLayout.helperText = null
            if (input.hasFocus()) {
                showCandidates()
            }
        }
    }

    private fun showCandidates() {
        if (adapter.itemCount == 0 || !input.hasFocus()) return
        updateCandidateHeight()
        if (candidateList.visibility != View.VISIBLE) {
            candidateList.visibility = View.VISIBLE
            onCandidateVisibilityChanged(true)
        }
        ViewCompat.requestApplyInsets(candidateList)
    }

    private fun updateCandidateHeight() {
        val height = PlaceCandidatePresentationPolicy.heightPx(
            visibleHeightPx = visibleHeightPx,
            rowHeightPx = rowHeightPx,
            itemCount = adapter.itemCount
        )
        if (height <= 0) return
        candidateList.layoutParams = candidateList.layoutParams.apply {
            this.height = height
        }
    }

    private fun cancelPendingSearch(hideLoading: Boolean = true) {
        pendingSearch?.let { mainHandler.removeCallbacks(it) }
        pendingSearch = null
        searchSequence += 1
        if (hideLoading) {
            setSearchLoading(false)
        }
    }

    private fun setSearchLoading(isLoading: Boolean) {
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private class PlaceCandidateAdapter(
        private val context: Context,
        private val onClick: (Place) -> Unit
    ) : RecyclerView.Adapter<PlaceCandidateViewHolder>() {
        private val places = mutableListOf<Place>()

        fun submitPlaces(newPlaces: List<Place>) {
            places.clear()
            places.addAll(newPlaces)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceCandidateViewHolder {
            val view = TextView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, CANDIDATE_ROW_HEIGHT_DP)
                )
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(context, 14), 0, dp(context, 14), 0)
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                textSize = 15f
                background = ContextCompat.getDrawable(context, R.drawable.table_row_background)
            }
            return PlaceCandidateViewHolder(view, onClick)
        }

        override fun onBindViewHolder(holder: PlaceCandidateViewHolder, position: Int) {
            holder.bind(places[position])
        }

        override fun getItemCount(): Int = places.size
    }

    private class PlaceCandidateViewHolder(
        private val textView: TextView,
        private val onClick: (Place) -> Unit
    ) : RecyclerView.ViewHolder(textView) {
        fun bind(place: Place) {
            textView.text = place.name
            textView.setOnClickListener { onClick(place) }
        }
    }

    companion object {
        private const val MIN_SEARCH_LENGTH = 1
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val CANDIDATE_ROW_HEIGHT_DP = 48
        private const val MIN_VISIBLE_ROWS = 3

        private fun dp(context: Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }
    }
}
