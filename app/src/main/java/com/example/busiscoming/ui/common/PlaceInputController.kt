package com.example.busiscoming.ui.common

import android.content.Context
import android.os.Handler
import android.text.TextUtils
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscoming.R
import com.example.busiscoming.data.location.CurrentLocationSnapshot
import com.example.busiscoming.data.location.GeoDistanceCalculator
import com.example.busiscoming.data.location.PlaceDistanceFormatter
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
    private val onPlaceSelected: (Place) -> Unit = {},
    private val onUserTextEdited: () -> Unit = {}
) {
    private val rowHeightPx = dp(context, CANDIDATE_ROW_HEIGHT_DP)
    private val adapter = PlaceCandidateAdapter(context) { place ->
        setSelectedPlace(place)
        onPlaceSelected(place)
    }
    private var suppressTextChange = false
    private var searchSequence = 0
    private var pendingSearch: Runnable? = null
    private var imeTopPx = context.resources.displayMetrics.heightPixels

    var selectedPlace: Place? = null
        private set

    init {
        input.threshold = MIN_SEARCH_LENGTH
        candidateList.layoutManager = LinearLayoutManager(context)
        candidateList.adapter = adapter
        candidateList.isNestedScrollingEnabled = true
        candidateList.background = ContextCompat.getDrawable(context, R.drawable.place_candidate_list_background)
        candidateList.elevation = dp(context, 2).toFloat()
        candidateList.visibility = View.GONE
        ViewCompat.setOnApplyWindowInsetsListener(candidateList) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            imeTopPx = (view.rootView.height - imeBottom).coerceAtLeast(rowHeightPx * MIN_VISIBLE_ROWS)
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

    fun isCandidateVisible(): Boolean = candidateList.visibility == View.VISIBLE

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

    fun setHelperText(message: String?) {
        inputLayout.helperText = message
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

    fun setCurrentLocationSnapshot(snapshot: CurrentLocationSnapshot?) {
        adapter.setCurrentLocationSnapshot(snapshot)
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
        onUserTextEdited()
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
        val candidateTop = candidateTopInRoot()
        val availableHeight = (imeTopPx - candidateTop - dp(candidateList.context, CANDIDATE_BOTTOM_SAFE_INSET_DP))
            .coerceAtLeast(0)
        val height = PlaceCandidatePresentationPolicy.heightPx(
            availableHeightPx = availableHeight,
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

    private fun candidateTopInRoot(): Int {
        val rootLocation = IntArray(2)
        val candidateLocation = IntArray(2)
        candidateList.rootView.getLocationOnScreen(rootLocation)
        candidateList.getLocationOnScreen(candidateLocation)
        return candidateLocation[1] - rootLocation[1]
    }

    private class PlaceCandidateAdapter(
        private val context: Context,
        private val onClick: (Place) -> Unit
    ) : RecyclerView.Adapter<PlaceCandidateViewHolder>() {
        private val places = mutableListOf<Place>()
        private var currentLocationSnapshot: CurrentLocationSnapshot? = null

        fun submitPlaces(newPlaces: List<Place>) {
            places.clear()
            places.addAll(newPlaces)
            notifyDataSetChanged()
        }

        fun setCurrentLocationSnapshot(snapshot: CurrentLocationSnapshot?) {
            currentLocationSnapshot = snapshot
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaceCandidateViewHolder {
            val view = LinearLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, CANDIDATE_ROW_HEIGHT_DP)
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 16), 0, dp(context, 12), 0)
                background = ContextCompat.getDrawable(context, R.drawable.place_candidate_item_background)
                isClickable = true
                isFocusable = true
            }
            val nameView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                textSize = 16f
            }
            val distanceContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(context, 10) }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                visibility = View.GONE
            }
            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 14), dp(context, 14))
                setImageResource(R.drawable.ic_location_outline)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val distanceView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(context, 4) }
                maxLines = 1
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 13f
            }
            distanceContainer.addView(icon)
            distanceContainer.addView(distanceView)
            view.addView(nameView)
            view.addView(distanceContainer)
            return PlaceCandidateViewHolder(view, onClick)
        }

        override fun onBindViewHolder(holder: PlaceCandidateViewHolder, position: Int) {
            holder.bind(places[position], currentLocationSnapshot)
        }

        override fun getItemCount(): Int = places.size
    }

    private class PlaceCandidateViewHolder(
        private val rowView: LinearLayout,
        private val onClick: (Place) -> Unit
    ) : RecyclerView.ViewHolder(rowView) {
        private val nameView = rowView.getChildAt(0) as TextView
        private val distanceContainer = rowView.getChildAt(1) as LinearLayout
        private val distanceView = distanceContainer.getChildAt(1) as TextView

        fun bind(place: Place, snapshot: CurrentLocationSnapshot?) {
            nameView.text = place.name
            val distanceMeters = snapshot?.let {
                GeoDistanceCalculator.distanceMeters(
                    fromLatitude = it.latitude,
                    fromLongitude = it.longitude,
                    toLatitude = place.latitude,
                    toLongitude = place.longitude
                )
            }
            if (distanceMeters == null) {
                distanceContainer.visibility = View.GONE
                rowView.contentDescription = place.name
            } else {
                distanceContainer.visibility = View.VISIBLE
                distanceView.text = PlaceDistanceFormatter.compact(distanceMeters)
                rowView.contentDescription = "${place.name}，${PlaceDistanceFormatter.accessibility(distanceMeters)}"
            }
            rowView.setOnClickListener { onClick(place) }
        }
    }

    companion object {
        private const val MIN_SEARCH_LENGTH = 1
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val CANDIDATE_ROW_HEIGHT_DP = 52
        private const val MIN_VISIBLE_ROWS = 3
        private const val CANDIDATE_BOTTOM_SAFE_INSET_DP = 8

        private fun dp(context: Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }
    }
}
