package com.example.busiscomming.ui.edit

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.widget.Filter
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.busiscomming.R
import com.example.busiscomming.data.model.Place
import com.example.busiscomming.data.model.RouteConfig
import com.example.busiscomming.data.model.RouteConfigValidator
import com.example.busiscomming.data.repository.CitybusPlaceSearchRepository
import com.example.busiscomming.data.repository.PlaceSearchRepository
import com.example.busiscomming.data.repository.RouteConfigRepository
import com.example.busiscomming.ui.common.applyStatusBarPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteEditActivity : AppCompatActivity() {
    private lateinit var repository: RouteConfigRepository
    private lateinit var placeSearchRepository: PlaceSearchRepository
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var originInputLayout: TextInputLayout
    private lateinit var destinationInputLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var originInput: MaterialAutoCompleteTextView
    private lateinit var destinationInput: MaterialAutoCompleteTextView
    private lateinit var originAdapter: PlaceCandidateAdapter
    private lateinit var destinationAdapter: PlaceCandidateAdapter

    private var routeId: Long = NO_ROUTE_ID
    private val originState = PlaceFieldState()
    private val destinationState = PlaceFieldState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.routeEditContent).applyStatusBarPadding()
        repository = RouteConfigRepository(this)
        placeSearchRepository = CitybusPlaceSearchRepository()
        routeId = intent.getLongExtra(EXTRA_ROUTE_ID, NO_ROUTE_ID)

        bindViews()
        setupPlaceInputs()
        setupMode()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        searchExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindViews() {
        nameInputLayout = findViewById(R.id.routeNameInputLayout)
        originInputLayout = findViewById(R.id.originInputLayout)
        destinationInputLayout = findViewById(R.id.destinationInputLayout)
        nameInput = findViewById(R.id.routeNameInput)
        originInput = findViewById(R.id.originInput)
        destinationInput = findViewById(R.id.destinationInput)
        findViewById<MaterialButton>(R.id.saveRouteButton).setOnClickListener {
            saveRoute()
        }
    }

    private fun setupPlaceInputs() {
        originAdapter = PlaceCandidateAdapter(this)
        destinationAdapter = PlaceCandidateAdapter(this)
        originInput.setAdapter(originAdapter)
        destinationInput.setAdapter(destinationAdapter)

        setupPlaceInput(originInput, originInputLayout, originAdapter, originState)
        setupPlaceInput(
            destinationInput,
            destinationInputLayout,
            destinationAdapter,
            destinationState
        )
    }

    private fun setupPlaceInput(
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        adapter: PlaceCandidateAdapter,
        state: PlaceFieldState
    ) {
        input.threshold = MIN_SEARCH_LENGTH
        input.setOnItemClickListener { _, _, position, _ ->
            val place = adapter.getItem(position) ?: return@setOnItemClickListener
            setSelectedPlace(input, inputLayout, state, place, adapter)
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                handlePlaceTextChanged(
                    keyword = s?.toString()?.trim().orEmpty(),
                    input = input,
                    inputLayout = inputLayout,
                    adapter = adapter,
                    state = state
                )
            }
        })
    }

    private fun handlePlaceTextChanged(
        keyword: String,
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        adapter: PlaceCandidateAdapter,
        state: PlaceFieldState
    ) {
        if (state.suppressTextChange) return

        if (input.isPerformingCompletion) {
            mainHandler.post {
                val currentKeyword = input.text?.toString()?.trim().orEmpty()
                if (state.selectedPlace?.name != currentKeyword) {
                    state.selectedPlace = null
                    inputLayout.error = null
                    inputLayout.helperText = null
                    schedulePlaceSearch(currentKeyword, input, inputLayout, adapter, state)
                }
            }
            return
        }

        state.selectedPlace = null
        inputLayout.error = null
        inputLayout.helperText = null
        schedulePlaceSearch(keyword, input, inputLayout, adapter, state)
    }

    private fun setupMode() {
        if (routeId == NO_ROUTE_ID) {
            title = "新增路线"
            return
        }

        title = "编辑路线"
        val route = repository.getById(routeId)
        if (route == null) {
            Toast.makeText(this, "路线配置不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        nameInput.setText(route.name)
        setSelectedPlace(originInput, originInputLayout, originState, route.origin, originAdapter)
        setSelectedPlace(
            destinationInput,
            destinationInputLayout,
            destinationState,
            route.destination,
            destinationAdapter
        )
    }

    private fun saveRoute() {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val origin = originState.selectedPlace
        val destination = destinationState.selectedPlace

        val validation = RouteConfigValidator.validate(name, origin, destination)
        nameInputLayout.error = validation.nameError
        originInputLayout.error = validation.originError
        destinationInputLayout.error = validation.destinationError
        if (!validation.isValid || origin == null || destination == null) return

        if (routeId == NO_ROUTE_ID) {
            repository.insert(name, origin, destination)
            Toast.makeText(this, "已新增路线", Toast.LENGTH_SHORT).show()
        } else {
            repository.update(RouteConfig(routeId, name, origin, destination))
            Toast.makeText(this, "已保存修改", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun setSelectedPlace(
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        state: PlaceFieldState,
        place: Place,
        adapter: PlaceCandidateAdapter
    ) {
        cancelPendingSearch(state)
        adapter.submitPlaces(emptyList())
        state.selectedPlace = place
        state.suppressTextChange = true
        input.setText(place.name, false)
        input.setSelection(input.text?.length ?: 0)
        state.suppressTextChange = false
        input.dismissDropDown()
        inputLayout.error = null
        inputLayout.helperText = null
    }

    private fun schedulePlaceSearch(
        keyword: String,
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        adapter: PlaceCandidateAdapter,
        state: PlaceFieldState
    ) {
        cancelPendingSearch(state)
        state.searchSequence += 1

        if (keyword.length < MIN_SEARCH_LENGTH) {
            adapter.submitPlaces(emptyList())
            return
        }

        val searchId = state.searchSequence
        val searchRunnable = Runnable {
            runPlaceSearch(keyword, input, inputLayout, adapter, state, searchId)
        }
        state.pendingSearch = searchRunnable
        mainHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
    }

    private fun runPlaceSearch(
        keyword: String,
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        adapter: PlaceCandidateAdapter,
        state: PlaceFieldState,
        searchId: Int
    ) {
        searchExecutor.execute {
            val result = runCatching { placeSearchRepository.searchPlaces(keyword) }
            mainHandler.post {
                if (isFinishing || isDestroyed || state.searchSequence != searchId) return@post

                result
                    .onSuccess { places ->
                        updatePlaceCandidates(
                            places = places.take(MAX_CANDIDATES),
                            input = input,
                            inputLayout = inputLayout,
                            adapter = adapter
                        )
                    }
                    .onFailure {
                        adapter.submitPlaces(emptyList())
                        inputLayout.helperText = null
                        inputLayout.error = "地点搜索失败，请稍后重试"
                    }
            }
        }
    }

    private fun updatePlaceCandidates(
        places: List<Place>,
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        adapter: PlaceCandidateAdapter
    ) {
        adapter.submitPlaces(places)
        inputLayout.error = null

        if (places.isEmpty()) {
            inputLayout.helperText = "没有匹配地点"
        } else {
            inputLayout.helperText = null
            if (input.hasFocus()) {
                input.showDropDown()
            }
        }
    }

    private fun cancelPendingSearch(state: PlaceFieldState) {
        state.pendingSearch?.let { mainHandler.removeCallbacks(it) }
        state.pendingSearch = null
        state.searchSequence += 1
    }

    private class PlaceCandidateAdapter(
        context: android.content.Context
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

    private class PlaceFieldState {
        var selectedPlace: Place? = null
        var suppressTextChange: Boolean = false
        var searchSequence: Int = 0
        var pendingSearch: Runnable? = null
    }

    companion object {
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val NO_ROUTE_ID = -1L
        private const val MIN_SEARCH_LENGTH = 1
        private const val MAX_CANDIDATES = 100
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
