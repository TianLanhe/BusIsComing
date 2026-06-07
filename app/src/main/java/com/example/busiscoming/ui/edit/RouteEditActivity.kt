package com.example.busiscoming.ui.edit

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.widget.Filter
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.busiscoming.R
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.model.RouteConfigValidator
import com.example.busiscoming.data.repository.CitybusPlaceSearchRepository
import com.example.busiscoming.data.repository.PlaceSearchRepository
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.example.busiscoming.ui.common.applyStatusBarPadding
import com.example.busiscoming.ui.common.PlaceInputController
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
    private lateinit var screenTitleText: TextView
    private lateinit var nameInput: TextInputEditText
    private lateinit var originInput: MaterialAutoCompleteTextView
    private lateinit var destinationInput: MaterialAutoCompleteTextView
    private lateinit var originSearchLoading: View
    private lateinit var destinationSearchLoading: View
    private lateinit var originAdapter: PlaceCandidateAdapter
    private lateinit var destinationAdapter: PlaceCandidateAdapter
    private lateinit var originController: PlaceInputController
    private lateinit var destinationController: PlaceInputController

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
        if (::originController.isInitialized) originController.dispose()
        if (::destinationController.isInitialized) destinationController.dispose()
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
        screenTitleText = findViewById(R.id.routeEditTitle)
        nameInput = findViewById(R.id.routeNameInput)
        originInput = findViewById(R.id.originInput)
        destinationInput = findViewById(R.id.destinationInput)
        originSearchLoading = findViewById(R.id.originSearchLoading)
        destinationSearchLoading = findViewById(R.id.destinationSearchLoading)
        findViewById<MaterialButton>(R.id.backRouteButton).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.swapPlacesButton).setOnClickListener { view ->
            animateSwap(view)
            swapPlaces()
        }
        findViewById<MaterialButton>(R.id.saveRouteButton).setOnClickListener {
            saveRoute()
        }
    }

    private fun animateSwap(view: View) {
        view.animate()
            .rotationBy(180f)
            .setDuration(220L)
            .start()
    }

    private fun setupPlaceInputs() {
        originController = PlaceInputController(
            context = this,
            input = originInput,
            inputLayout = originInputLayout,
            loadingView = originSearchLoading,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { !isFinishing && !isDestroyed }
        )
        destinationController = PlaceInputController(
            context = this,
            input = destinationInput,
            inputLayout = destinationInputLayout,
            loadingView = destinationSearchLoading,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { !isFinishing && !isDestroyed }
        )
    }

    private fun setupPlaceInput(
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        adapter: PlaceCandidateAdapter,
        state: PlaceFieldState,
        loadingView: View
    ) {
        state.loadingView = loadingView
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
            val isClone = intent.hasExtra(EXTRA_PREFILL_NAME)
            val pageTitle = if (isClone) "複製路線" else "新增路線"
            title = pageTitle
            screenTitleText.text = pageTitle
            applyPrefillIfPresent()
            return
        }

        title = "編輯路線"
        screenTitleText.text = "編輯路線"
        val route = repository.getById(routeId)
        if (route == null) {
            Toast.makeText(this, "路線配置不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        nameInput.setText(route.name)
        originController.setSelectedPlace(route.origin)
        destinationController.setSelectedPlace(route.destination)
    }

    private fun saveRoute() {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val origin = originController.selectedPlace
        val destination = destinationController.selectedPlace

        val validation = RouteConfigValidator.validate(name, origin, destination)
        nameInputLayout.error = validation.nameError
        originController.setError(validation.originError)
        destinationController.setError(validation.destinationError)
        if (!validation.isValid || origin == null || destination == null) return

        val excludedRouteId = routeId.takeIf { it != NO_ROUTE_ID }
        if (repository.hasDuplicate(name, origin, destination, excludedRouteId)) {
            nameInputLayout.error = "路線已存在，請修改名稱或起終點"
            Toast.makeText(this, "路線已存在", Toast.LENGTH_SHORT).show()
            return
        }

        if (routeId == NO_ROUTE_ID) {
            repository.insert(name, origin, destination)
            Toast.makeText(this, "已新增路線", Toast.LENGTH_SHORT).show()
        } else {
            repository.update(RouteConfig(routeId, name, origin, destination))
            Toast.makeText(this, "已儲存修改", Toast.LENGTH_SHORT).show()
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

    private fun setRawPlaceText(
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        state: PlaceFieldState,
        adapter: PlaceCandidateAdapter,
        text: String
    ) {
        cancelPendingSearch(state)
        adapter.submitPlaces(emptyList())
        state.selectedPlace = null
        state.suppressTextChange = true
        input.setText(text, false)
        input.setSelection(input.text?.length ?: 0)
        state.suppressTextChange = false
        input.dismissDropDown()
        inputLayout.error = null
        inputLayout.helperText = null
    }

    private fun swapPlaces() {
        originController.swapWith(destinationController)
    }

    private fun schedulePlaceSearch(
        keyword: String,
        input: MaterialAutoCompleteTextView,
        inputLayout: TextInputLayout,
        adapter: PlaceCandidateAdapter,
        state: PlaceFieldState
    ) {
        cancelPendingSearch(state, hideLoading = false)
        state.searchSequence += 1

        if (keyword.length < MIN_SEARCH_LENGTH) {
            adapter.submitPlaces(emptyList())
            setSearchLoading(state, false)
            return
        }

        setSearchLoading(state, true)
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

                setSearchLoading(state, false)
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
                        inputLayout.error = "地點搜尋失敗，請稍後重試"
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
            inputLayout.helperText = "沒有匹配地點"
        } else {
            inputLayout.helperText = null
            if (input.hasFocus()) {
                input.showDropDown()
            }
        }
    }

    private fun cancelPendingSearch(state: PlaceFieldState, hideLoading: Boolean = true) {
        state.pendingSearch?.let { mainHandler.removeCallbacks(it) }
        state.pendingSearch = null
        state.searchSequence += 1
        if (hideLoading) {
            setSearchLoading(state, false)
        }
    }

    private fun setSearchLoading(state: PlaceFieldState, isLoading: Boolean) {
        state.loadingView?.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun applyPrefillIfPresent() {
        val prefillName = intent.getStringExtra(EXTRA_PREFILL_NAME) ?: return
        val origin = readPrefillPlace(
            nameKey = EXTRA_PREFILL_ORIGIN_NAME,
            latitudeKey = EXTRA_PREFILL_ORIGIN_LATITUDE,
            longitudeKey = EXTRA_PREFILL_ORIGIN_LONGITUDE
        )
        val destination = readPrefillPlace(
            nameKey = EXTRA_PREFILL_DESTINATION_NAME,
            latitudeKey = EXTRA_PREFILL_DESTINATION_LATITUDE,
            longitudeKey = EXTRA_PREFILL_DESTINATION_LONGITUDE
        )
        if (origin == null || destination == null) return

        nameInput.setText(prefillName)
        originController.setSelectedPlace(origin)
        destinationController.setSelectedPlace(destination)
    }

    private fun readPrefillPlace(
        nameKey: String,
        latitudeKey: String,
        longitudeKey: String
    ): Place? {
        val name = intent.getStringExtra(nameKey) ?: return null
        if (!intent.hasExtra(latitudeKey) || !intent.hasExtra(longitudeKey)) return null
        return Place(
            name = name,
            latitude = intent.getDoubleExtra(latitudeKey, 0.0),
            longitude = intent.getDoubleExtra(longitudeKey, 0.0)
        )
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
        var loadingView: View? = null
    }

    companion object {
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val EXTRA_PREFILL_NAME = "extra_prefill_name"
        const val EXTRA_PREFILL_ORIGIN_NAME = "extra_prefill_origin_name"
        const val EXTRA_PREFILL_ORIGIN_LATITUDE = "extra_prefill_origin_latitude"
        const val EXTRA_PREFILL_ORIGIN_LONGITUDE = "extra_prefill_origin_longitude"
        const val EXTRA_PREFILL_DESTINATION_NAME = "extra_prefill_destination_name"
        const val EXTRA_PREFILL_DESTINATION_LATITUDE = "extra_prefill_destination_latitude"
        const val EXTRA_PREFILL_DESTINATION_LONGITUDE = "extra_prefill_destination_longitude"
        const val NO_ROUTE_ID = -1L
        private const val MIN_SEARCH_LENGTH = 1
        private const val MAX_CANDIDATES = 100
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
