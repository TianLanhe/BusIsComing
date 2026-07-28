package com.golink.busiscoming.ui.common

import android.content.Context
import android.os.Handler
import android.text.TextUtils
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
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
import com.golink.busiscoming.R
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.GeoDistanceCalculator
import com.golink.busiscoming.data.location.PlaceDistanceFormatter
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.PlaceSearchRepository
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
    private val maxVisibleRows: Int = PlaceCandidatePresentationPolicy.DEFAULT_MAX_VISIBLE_ROWS,
    private val idleToolView: View? = null,
    instructionText: CharSequence? = null,
    private val exclusiveVerticalScroll: Boolean = false,
    private val onCandidateSpaceRequired: ((Int) -> Unit)? = null,
    private val onCandidateVisibilityChanged: (Boolean) -> Unit = {},
    private val onPlaceSelected: (Place) -> Unit = {},
    private val onUserTextEdited: () -> Unit = {},
    private val onMessageChanged: ((PlaceInputMessage) -> Unit)? = null
) {
    private val defaultInstructionText = instructionText ?: inputLayout.helperText
    private val rowHeightPx = dp(context, CANDIDATE_ROW_HEIGHT_DP)
    private val adapter = PlaceCandidateAdapter(context) { place ->
        setSelectedPlace(place)
        onPlaceSelected(place)
    }
    private var suppressTextChange = false
    private var searchSequence = 0
    private var pendingSearch: Runnable? = null
    private var imeTopPx = context.resources.displayMetrics.heightPixels
    private var searchLoading = false
    private var externalLoading = false
    private var candidateSpaceRequestPending = false
    private val candidateGestureOwnership = object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
            if (!exclusiveVerticalScroll || candidateList.visibility != View.VISIBLE) return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> claimCandidateGesture(recyclerView)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> releaseCandidateGesture(recyclerView)
            }
            return false
        }
    }

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
        if (exclusiveVerticalScroll) candidateList.addOnItemTouchListener(candidateGestureOwnership)
        ViewCompat.setOnApplyWindowInsetsListener(candidateList) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            imeTopPx = (view.rootView.height - imeBottom).coerceAtLeast(0)
            val hasCompleteRows = updateCandidateHeight()
            if (
                hasCompleteRows &&
                candidateList.visibility != View.VISIBLE &&
                input.hasFocus() &&
                adapter.itemCount > 0
            ) {
                showCandidates()
            }
            insets
        }
        input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (hasFocus && adapter.itemCount > 0) {
                showCandidates()
            } else if (
                hasFocus &&
                selectedPlace == null &&
                input.text.isNullOrBlank() &&
                inputLayout.error == null
            ) {
                showInstruction()
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
        clearAfterSelection()
    }

    fun restoreInputText(text: String) {
        if (text.isBlank() || selectedPlace != null) return
        suppressTextChange = true
        input.setText(text, false)
        input.setSelection(input.text?.length ?: 0)
        suppressTextChange = false
    }

    fun currentInputText(): String = input.text?.toString().orEmpty()

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
        val hadCandidatePresentation =
            candidateList.visibility != View.GONE || candidateList.layoutParams.height > 0
        if (!hadCandidatePresentation) return false
        setCandidatePresentation(View.GONE, 0)
        candidateSpaceRequestPending = false
        return hadCandidatePresentation
    }

    /** 搜尋輸入器折疊時，同步清除焦點與欄位級候選，避免隱藏控制項繼續接收操作。 */
    fun clearFocusAndHideCandidates() {
        input.clearFocus()
        hideCandidates()
    }

    fun setError(message: String?) {
        inputLayout.error = message
    }

    fun clearMessages() {
        inputLayout.error = null
        if (onMessageChanged == null) {
            inputLayout.helperText = defaultInstructionText
        } else {
            inputLayout.helperText = null
            onMessageChanged.invoke(PlaceInputMessage.INSTRUCTION)
        }
    }

    fun setCurrentLocationSnapshot(snapshot: CurrentLocationSnapshot?) {
        adapter.setCurrentLocationSnapshot(snapshot)
    }

    fun setExternalLoading(isLoading: Boolean) {
        externalLoading = isLoading
        renderLoading()
    }

    fun dispose() {
        pendingSearch?.let { mainHandler.removeCallbacks(it) }
        pendingSearch = null
        externalLoading = false
        setSearchLoading(false)
        hideCandidates()
        ViewCompat.setOnApplyWindowInsetsListener(candidateList, null)
        if (exclusiveVerticalScroll) candidateList.removeOnItemTouchListener(candidateGestureOwnership)
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
        val languageVersion = AppLanguageRuntime.snapshot().version
        val searchRunnable = Runnable {
            runPlaceSearch(keyword, searchId, languageVersion)
        }
        pendingSearch = searchRunnable
        mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
    }

    private fun runPlaceSearch(keyword: String, searchId: Int, languageVersion: Long) {
        searchExecutor.execute {
            val result = runCatching { placeSearchRepository.searchPlaces(keyword) }
            mainHandler.post {
                if (
                    !isActive() ||
                    searchSequence != searchId ||
                    AppLanguageRuntime.snapshot().version != languageVersion
                ) return@post

                setSearchLoading(false)
                result
                    .onSuccess { places ->
                        updatePlaceCandidates(PlaceCandidatePresentationPolicy.limit(places))
                    }
                    .onFailure {
                        adapter.submitPlaces(emptyList())
                        hideCandidates()
                        showSearchFailed()
                    }
            }
        }
    }

    private fun updatePlaceCandidates(places: List<Place>) {
        candidateSpaceRequestPending = false
        adapter.submitPlaces(places)
        inputLayout.error = null
        if (places.isEmpty()) {
            hideCandidates()
            showNoMatches()
        } else {
            if (onMessageChanged == null) {
                inputLayout.helperText = null
            } else {
                inputLayout.helperText = null
                onMessageChanged.invoke(PlaceInputMessage.INSTRUCTION)
            }
            if (input.hasFocus()) {
                showCandidates()
            }
        }
    }

    private fun showInstruction() {
        inputLayout.error = null
        if (onMessageChanged == null) {
            inputLayout.helperText = defaultInstructionText
        } else {
            inputLayout.helperText = null
            onMessageChanged.invoke(PlaceInputMessage.INSTRUCTION)
        }
    }

    private fun showNoMatches() {
        inputLayout.error = null
        if (onMessageChanged == null) {
            inputLayout.helperText = input.context.getString(R.string.place_search_empty)
        } else {
            inputLayout.helperText = null
            onMessageChanged.invoke(PlaceInputMessage.NO_MATCHES)
        }
    }

    private fun showSearchFailed() {
        if (onMessageChanged == null) {
            inputLayout.helperText = null
            inputLayout.error = input.context.getString(R.string.place_search_failed)
        } else {
            inputLayout.error = null
            inputLayout.helperText = null
            onMessageChanged.invoke(PlaceInputMessage.SEARCH_FAILED)
        }
    }

    private fun clearAfterSelection() {
        inputLayout.error = null
        if (onMessageChanged == null) {
            inputLayout.helperText = defaultInstructionText
        } else {
            inputLayout.helperText = null
            onMessageChanged.invoke(PlaceInputMessage.NONE)
        }
    }

    private fun showCandidates() {
        if (adapter.itemCount == 0 || !input.hasFocus()) return
        if (!updateCandidateHeight()) return
        candidateSpaceRequestPending = false
        if (candidateList.visibility != View.VISIBLE) {
            setCandidateScrollLock(true)
            candidateList.visibility = View.VISIBLE
            onCandidateVisibilityChanged(true)
        }
        ViewCompat.requestApplyInsets(candidateList)
    }

    private fun setCandidateScrollLock(visible: Boolean) {
        if (!exclusiveVerticalScroll) return
        candidateList.isNestedScrollingEnabled = !visible
        if (visible) {
            claimCandidateGesture(candidateList)
        } else {
            releaseCandidateGesture(candidateList)
        }
    }

    private fun claimCandidateGesture(recyclerView: RecyclerView) {
        recyclerView.parent?.requestDisallowInterceptTouchEvent(true)
        recyclerView.stopNestedScroll()
    }

    private fun releaseCandidateGesture(recyclerView: RecyclerView) {
        recyclerView.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun updateCandidateHeight(): Boolean {
        val candidateTop = candidateTopInRoot()
        val availableHeight = (imeTopPx - candidateTop - dp(candidateList.context, CANDIDATE_BOTTOM_SAFE_INSET_DP))
            .coerceAtLeast(0)
        val height = PlaceCandidatePresentationPolicy.heightPx(
            availableHeightPx = availableHeight,
            rowHeightPx = rowHeightPx,
            itemCount = adapter.itemCount,
            maxVisibleRows = maxVisibleRows
        )
        if (height <= 0) {
            val bootstrapHeight = onCandidateSpaceRequired?.let {
                PlaceCandidatePresentationPolicy.editorBootstrapHeightPx(
                    availableHeightPx = availableHeight,
                    rowHeightPx = rowHeightPx,
                    itemCount = adapter.itemCount
                )
            } ?: 0
            if (bootstrapHeight > 0) {
                setCandidatePresentation(View.INVISIBLE, bootstrapHeight)
                if (!candidateSpaceRequestPending) {
                    candidateSpaceRequestPending = true
                    onCandidateSpaceRequired?.invoke(bootstrapHeight)
                }
            } else {
                setCandidatePresentation(View.GONE, 0)
            }
            return false
        }
        candidateList.layoutParams = candidateList.layoutParams.apply {
            this.height = height
        }
        return true
    }

    private fun setCandidatePresentation(visibility: Int, height: Int) {
        val wasVisible = candidateList.visibility == View.VISIBLE
        if (wasVisible && visibility != View.VISIBLE) {
            setCandidateScrollLock(false)
        }
        candidateList.layoutParams = candidateList.layoutParams.apply {
            this.height = height
        }
        candidateList.visibility = visibility
        if (wasVisible && visibility != View.VISIBLE) {
            onCandidateVisibilityChanged(false)
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
        searchLoading = isLoading
        renderLoading()
    }

    private fun renderLoading() {
        val isLoading = searchLoading || externalLoading
        loadingView.visibility = if (isLoading) View.VISIBLE else View.GONE
        idleToolView?.visibility = if (isLoading) View.INVISIBLE else View.VISIBLE
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
                val distanceDescription = if (distanceMeters < 1000) {
                    rowView.context.getString(R.string.distance_from_current_meters, distanceMeters)
                } else {
                    rowView.context.getString(
                        R.string.distance_from_current,
                        PlaceDistanceFormatter.compact(distanceMeters)
                    )
                }
                rowView.contentDescription = listOf(place.name, distanceDescription).joinToString(", ")
            }
            rowView.setOnClickListener { onClick(place) }
        }
    }

    companion object {
        private const val MIN_SEARCH_LENGTH = 1
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val CANDIDATE_ROW_HEIGHT_DP = 52
        private const val CANDIDATE_BOTTOM_SAFE_INSET_DP = 8

        private fun dp(context: Context, value: Int): Int {
            return (value * context.resources.displayMetrics.density).toInt()
        }
    }
}
