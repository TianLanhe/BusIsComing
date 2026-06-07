package com.example.busiscoming.ui.common

import android.content.Context
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filter
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
    private val placeSearchRepository: PlaceSearchRepository,
    private val mainHandler: Handler,
    private val searchExecutor: ExecutorService,
    private val isActive: () -> Boolean
) {
    private val adapter = PlaceCandidateAdapter(context)
    private var suppressTextChange = false
    private var searchSequence = 0
    private var pendingSearch: Runnable? = null

    var selectedPlace: Place? = null
        private set

    init {
        input.threshold = MIN_SEARCH_LENGTH
        input.setAdapter(adapter)
        input.setOnItemClickListener { _, _, position, _ ->
            val place = adapter.getItem(position) ?: return@setOnItemClickListener
            setSelectedPlace(place)
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
        selectedPlace = place
        suppressTextChange = true
        input.setText(place.name, false)
        input.setSelection(input.text?.length ?: 0)
        suppressTextChange = false
        input.dismissDropDown()
        clearMessages()
    }

    fun setRawText(text: String) {
        cancelPendingSearch()
        adapter.submitPlaces(emptyList())
        selectedPlace = null
        suppressTextChange = true
        input.setText(text, false)
        input.setSelection(input.text?.length ?: 0)
        suppressTextChange = false
        input.dismissDropDown()
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
    }

    private fun handleTextChanged(keyword: String) {
        if (suppressTextChange) return

        if (input.isPerformingCompletion) {
            mainHandler.post {
                val currentKeyword = input.text?.toString()?.trim().orEmpty()
                if (selectedPlace?.name != currentKeyword) {
                    selectedPlace = null
                    clearMessages()
                    schedulePlaceSearch(currentKeyword)
                }
            }
            return
        }

        selectedPlace = null
        clearMessages()
        schedulePlaceSearch(keyword)
    }

    private fun schedulePlaceSearch(keyword: String) {
        cancelPendingSearch(hideLoading = false)
        searchSequence += 1

        if (keyword.length < MIN_SEARCH_LENGTH) {
            adapter.submitPlaces(emptyList())
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
                    .onSuccess { places -> updatePlaceCandidates(places.take(MAX_CANDIDATES)) }
                    .onFailure {
                        adapter.submitPlaces(emptyList())
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
            inputLayout.helperText = "沒有匹配地點"
        } else {
            inputLayout.helperText = null
            if (input.hasFocus()) {
                input.showDropDown()
            }
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
        context: Context
    ) : ArrayAdapter<Place>(context, android.R.layout.simple_dropdown_item_1line) {
        private val places = mutableListOf<Place>()
        private val noFilter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    values = places
                    count = places.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }
        }

        fun submitPlaces(newPlaces: List<Place>) {
            places.clear()
            places.addAll(newPlaces)
            super.clear()
            super.addAll(newPlaces)
            notifyDataSetChanged()
        }

        override fun getFilter(): Filter = noFilter
    }

    companion object {
        private const val MIN_SEARCH_LENGTH = 1
        private const val MAX_CANDIDATES = 100
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
