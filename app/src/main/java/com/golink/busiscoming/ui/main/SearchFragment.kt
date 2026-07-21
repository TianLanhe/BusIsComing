package com.golink.busiscoming.ui.main

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.golink.busiscoming.R
import com.golink.busiscoming.data.location.CurrentPlaceSelectionResult
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.LocationPermissionUtils
import com.golink.busiscoming.data.location.PlaceAttribution
import com.golink.busiscoming.data.location.SystemLocationUtils
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.RouteConfigValidator
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.CitybusBusRouteRepository
import com.golink.busiscoming.data.repository.CitybusPlaceSearchRepository
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.PlaceSearchRepository
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.ui.common.PlaceInputController
import com.golink.busiscoming.ui.common.PlacePairEditorView
import com.golink.busiscoming.ui.common.RouteResultControlsView
import com.golink.busiscoming.ui.common.localizedMessage
import com.golink.busiscoming.ui.common.localizedText
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SearchFragment : Fragment() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val queryExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busRouteRepository: BusRouteRepository = busRouteRepositoryFactory()
    private val currentPlaceRequestState = SearchCurrentPlaceRequestState()
    private val routeQueryCoordinator = RouteQueryCoordinator(
        repository = busRouteRepository,
        executor = queryExecutor,
        postToOwner = { runnable -> mainHandler.post(runnable) },
        isOwnerActive = ::isViewActive
    )
    private var originController: PlaceInputController? = null
    private var destinationController: PlaceInputController? = null
    private val attributionState = SearchPlaceAttributionState()
    private val routeQueryState = RouteQueryState()
    private val currentResults: List<BusRouteOption>
        get() = routeQueryState.results
    private val sortField: SortField?
        get() = routeQueryState.sortField
    private val sortDirection: SortDirection
        get() = routeQueryState.sortDirection
    private var restoredOrigin: Place? = null
    private var restoredDestination: Place? = null
    private var restoredOriginInput: String? = null
    private var restoredDestinationInput: String? = null
    private var restoredOriginGoogleAttribution: Boolean = false
    private var restoredDestinationGoogleAttribution: Boolean = false
    private var restoredShouldRequery: Boolean = false
    private var hasSubmittedQuery: Boolean = false
    private var successfulQueryOrigin: Place? = null
    private var successfulQueryDestination: Place? = null
    private var isViewStateRestored: Boolean = false
    private var hasPendingDestinationSelection: Boolean = false
    private var pendingScrollPosition: Int? = null
    private var pendingScrollOffset: Int = 0
    private lateinit var resultAdapter: BusRouteAdapter
    private lateinit var detailSheet: RouteDetailBottomSheet
    private lateinit var etaSheet: EtaArrivalsBottomSheet
    private lateinit var resultList: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var routeResultControls: View
    private lateinit var sortControls: View
    private lateinit var sortButtons: Map<SortField, MaterialButton>
    private lateinit var resultLoading: ProgressBar
    private lateinit var resultStatus: TextView
    private lateinit var resultMetaContainer: View
    private lateinit var resultCount: TextView
    private lateinit var resultUpdatedAt: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var queryButton: MaterialButton
    private lateinit var originAttribution: TextView
    private lateinit var destinationAttribution: TextView
    private var candidateBackCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredOrigin = savedInstanceState?.placeFor(STATE_ORIGIN)
        restoredDestination = savedInstanceState?.placeFor(STATE_DESTINATION)
        restoredOriginInput = savedInstanceState?.getString(STATE_ORIGIN_INPUT)
        restoredDestinationInput = savedInstanceState?.getString(STATE_DESTINATION_INPUT)
        restoredOriginGoogleAttribution =
            savedInstanceState?.getBoolean(STATE_ORIGIN_GOOGLE_ATTRIBUTION) == true
        restoredDestinationGoogleAttribution =
            savedInstanceState?.getBoolean(STATE_DESTINATION_GOOGLE_ATTRIBUTION) == true
        attributionState.setOriginGoogleMaps(restoredOriginGoogleAttribution)
        attributionState.setDestinationGoogleMaps(restoredDestinationGoogleAttribution)
        restoredShouldRequery = savedInstanceState?.getBoolean(STATE_HAS_SUBMITTED_QUERY) == true
        hasSubmittedQuery = restoredShouldRequery
        pendingScrollPosition = savedInstanceState
            ?.takeIf { it.containsKey(STATE_SEARCH_SCROLL_POSITION) }
            ?.getInt(STATE_SEARCH_SCROLL_POSITION)
        pendingScrollOffset = savedInstanceState?.getInt(STATE_SEARCH_SCROLL_OFFSET) ?: 0
        val restoredSortField = savedInstanceState
            ?.getString(STATE_SORT_FIELD)
            ?.let { runCatching { SortField.valueOf(it) }.getOrNull() }
        val restoredSortDirection = savedInstanceState
            ?.getString(STATE_SORT_DIRECTION)
            ?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() }
            ?: SortDirection.ASC
        routeQueryState.restoreSort(restoredSortField, restoredSortDirection)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val placeEditor = view.findViewById<PlacePairEditorView>(R.id.searchPlacePairEditor)
        val originInput = placeEditor.originInput
        val destinationInput = placeEditor.destinationInput
        originInput.isSaveEnabled = false
        destinationInput.isSaveEnabled = false
        val originLayout = placeEditor.originInputLayout
        val destinationLayout = placeEditor.destinationInputLayout
        originAttribution = placeEditor.originAttribution
        destinationAttribution = placeEditor.destinationAttribution
        val currentLocationButton = placeEditor.currentLocationButton

        originController = PlaceInputController(
            context = context,
            input = originInput,
            inputLayout = originLayout,
            loadingView = placeEditor.originLoading,
            candidateList = placeEditor.originCandidateList,
            placeSearchRepository = placeSearchRepositoryFactory(),
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = ::isViewActive,
            maxVisibleRows = SEARCH_MAX_VISIBLE_CANDIDATE_ROWS,
            idleToolView = currentLocationButton,
            instructionText = getString(R.string.place_search_helper),
            onCandidateVisibilityChanged = { visible ->
                if (visible) destinationController?.hideCandidates()
                updateRefreshEnabled()
            },
            onPlaceSelected = {
                invalidateCurrentPlaceRequest()
                attributionState.clearOrigin()
                renderAttribution()
                onSearchSelectionChanged()
            },
            onUserTextEdited = {
                invalidateCurrentPlaceRequest()
                attributionState.clearOrigin()
                renderAttribution()
                onSearchSelectionChanged()
            }
        )
        destinationController = PlaceInputController(
            context = context,
            input = destinationInput,
            inputLayout = destinationLayout,
            loadingView = placeEditor.destinationLoading,
            candidateList = placeEditor.destinationCandidateList,
            placeSearchRepository = placeSearchRepositoryFactory(),
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = ::isViewActive,
            maxVisibleRows = SEARCH_MAX_VISIBLE_CANDIDATE_ROWS,
            instructionText = getString(R.string.place_search_helper),
            onCandidateVisibilityChanged = { visible ->
                if (visible) originController?.hideCandidates()
                updateRefreshEnabled()
            },
            onPlaceSelected = {
                attributionState.clearDestination()
                renderAttribution()
                onSearchSelectionChanged()
            },
            onUserTextEdited = {
                attributionState.clearDestination()
                renderAttribution()
                onSearchSelectionChanged()
            }
        )
        currentLocationButton.setOnClickListener { requestCurrentOrigin(isAuto = false) }

        resultList = view.findViewById(R.id.searchResultList)
        swipeRefresh = view.findViewById(R.id.searchSwipeRefresh)
        val resultControls = view.findViewById<RouteResultControlsView>(
            R.id.searchRouteResultControls
        )
        routeResultControls = resultControls
        sortControls = resultControls.sortControls
        resultLoading = view.findViewById(R.id.searchResultLoading)
        resultStatus = view.findViewById(R.id.searchResultStatus)
        resultMetaContainer = resultControls.summaryContainer
        resultCount = resultControls.summaryText
        resultUpdatedAt = resultControls.updatedAtText
        saveButton = view.findViewById(R.id.searchSaveButton)
        queryButton = view.findViewById(R.id.searchQueryButton)
        detailSheet = RouteDetailBottomSheet(
            requireActivity() as androidx.appcompat.app.AppCompatActivity,
            routeDetailRepositoryFactory()
        )
        etaSheet = EtaArrivalsBottomSheet(context)
        resultAdapter = BusRouteAdapter(
            onRouteClick = detailSheet::show,
            onEtaClick = etaSheet::show,
            onMonitorClick = { route ->
                (activity as? MainActivity)?.showMonitorSettings(route, originController?.selectedPlace)
            }
        )
        resultList.layoutManager = LinearLayoutManager(context)
        resultList.adapter = resultAdapter
        resultList.isNestedScrollingEnabled = true
        swipeRefresh.setColorSchemeResources(R.color.bus_chip_selected)
        swipeRefresh.setOnRefreshListener { query(preserveSort = true) }
        updateRefreshEnabled()
        sortButtons = resultControls.sortButtons
        sortButtons.forEach { (field, button) -> button.setOnClickListener { sortBy(field) } }
        updateSortControls()
        renderSearchActions()

        candidateBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                hideCandidateLists()
            }
        }.also { callback ->
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
        }

        placeEditor.swapButton.setOnClickListener { button ->
            button.animate().rotationBy(180f).setDuration(220L).start()
            swapSearchPlaces()
        }
        view.findViewById<View>(R.id.searchContent).setOnClickListener {
            originInput.clearFocus()
            destinationInput.clearFocus()
            hideCandidateLists()
        }
        queryButton.setOnClickListener { query() }
        saveButton.setOnClickListener { saveCurrentRoute() }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        restoredOrigin?.let { originController?.setSelectedPlace(it) }
        restoredDestination?.let { destinationController?.setSelectedPlace(it) }
        restoredOriginInput?.let { originController?.restoreInputText(it) }
        restoredDestinationInput?.let { destinationController?.restoreInputText(it) }
        attributionState.setOriginGoogleMaps(restoredOriginGoogleAttribution)
        attributionState.setDestinationGoogleMaps(restoredDestinationGoogleAttribution)
        renderAttribution()
        renderSearchActions()
        restoreSubmittedQueryIfNeeded()
        requestSilentCandidateLocationSnapshotIfNeeded()
        isViewStateRestored = true
        if (hasPendingDestinationSelection) {
            hasPendingDestinationSelection = false
            onDestinationSelected()
        }
    }

    fun onDestinationSelected() {
        if (!isViewStateRestored) {
            hasPendingDestinationSelection = true
            return
        }
        val generation = currentPlaceRequestState.beginAutoRequest(
            hasSelectedOrigin = originController?.selectedPlace != null || restoredOrigin != null,
            originInput = originController?.currentInputText().orEmpty(),
            hasSubmittedQuery = hasSubmittedQuery
        ) ?: return
        requestCurrentOrigin(isAuto = true, generation = generation)
    }

    fun onDestinationHidden() {
        invalidateCurrentPlaceRequest()
        routeQueryCoordinator.invalidate()
        routeQueryState.cancel()
        swipeRefresh.isRefreshing = false
        updateRefreshEnabled()
    }

    override fun onDestroyView() {
        invalidateCurrentPlaceRequest()
        routeQueryCoordinator.invalidate()
        routeQueryState.cancel()
        originController?.dispose()
        destinationController?.dispose()
        originController = null
        destinationController = null
        isViewStateRestored = false
        hasPendingDestinationSelection = false
        candidateBackCallback = null
        detailSheet.dispose()
        etaSheet.dispose()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        restoredOrigin = originController?.selectedPlace
        restoredDestination = destinationController?.selectedPlace
        restoredOriginInput = originController?.currentInputText()
        restoredDestinationInput = destinationController?.currentInputText()
        restoredOriginGoogleAttribution = attributionState.originUsesGoogleMaps
        restoredDestinationGoogleAttribution = attributionState.destinationUsesGoogleMaps
        originController?.selectedPlace?.writeTo(outState, STATE_ORIGIN)
        destinationController?.selectedPlace?.writeTo(outState, STATE_DESTINATION)
        outState.putString(STATE_ORIGIN_INPUT, originController?.currentInputText())
        outState.putString(STATE_DESTINATION_INPUT, destinationController?.currentInputText())
        outState.putBoolean(
            STATE_ORIGIN_GOOGLE_ATTRIBUTION,
            restoredOriginGoogleAttribution
        )
        outState.putBoolean(
            STATE_DESTINATION_GOOGLE_ATTRIBUTION,
            restoredDestinationGoogleAttribution
        )
        outState.putBoolean(STATE_HAS_SUBMITTED_QUERY, hasSubmittedQuery)
        sortField?.let { outState.putString(STATE_SORT_FIELD, it.name) }
        outState.putString(STATE_SORT_DIRECTION, sortDirection.name)
        if (::resultList.isInitialized) {
            val manager = resultList.layoutManager as? LinearLayoutManager
            val position = manager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            if (position != RecyclerView.NO_POSITION) {
                outState.putInt(STATE_SEARCH_SCROLL_POSITION, position)
                outState.putInt(
                    STATE_SEARCH_SCROLL_OFFSET,
                    manager?.findViewByPosition(position)?.top ?: 0
                )
            }
        }
    }

    override fun onDestroy() {
        searchExecutor.shutdownNow()
        queryExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestCurrentOrigin(
        isAuto: Boolean,
        generation: Int = currentPlaceRequestState.beginManualRequest()
    ) {
        originController?.setExternalLoading(true)
        val handleResult: (CurrentPlaceSelectionResult) -> Unit = { result ->
            mainHandler.post {
                if (!isViewActive() || !currentPlaceRequestState.finish(generation)) return@post
                originController?.setExternalLoading(false)
                when (result) {
                    is CurrentPlaceSelectionResult.Success -> {
                        originController?.setCurrentLocationSnapshot(result.snapshot)
                        destinationController?.setCurrentLocationSnapshot(result.snapshot)
                        originController?.setSelectedPlace(result.place)
                        attributionState.setOriginGoogleMaps(
                            result.attribution == PlaceAttribution.GOOGLE_MAPS
                        )
                        renderAttribution()
                        onSearchSelectionChanged()
                    }
                    CurrentPlaceSelectionResult.Failure -> {
                        if (isAuto) {
                            originController?.setHelperText(
                                getString(R.string.current_location_manual_origin)
                            )
                        } else {
                            Toast.makeText(
                                requireContext(),
                                R.string.current_location_unavailable,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
        val override = currentPlaceRequestOverride
        if (override != null) {
            override(isAuto, handleResult)
        } else {
            (activity as? MainActivity)?.requestCurrentPlace(isAuto, handleResult)
        }
    }

    private fun requestSilentCandidateLocationSnapshotIfNeeded() {
        val hasRestoredState = restoredOrigin != null ||
            restoredDestination != null ||
            !restoredOriginInput.isNullOrBlank() ||
            !restoredDestinationInput.isNullOrBlank() ||
            hasSubmittedQuery
        val context = context ?: return
        val canRequest = hasRestoredState &&
            LocationPermissionUtils.hasForegroundLocationPermission(context) &&
            SystemLocationUtils.isLocationEnabled(context)
        val generation = currentPlaceRequestState.beginSilentSnapshotRequest(canRequest) ?: return
        val handleResult: (CurrentLocationSnapshot?) -> Unit = { snapshot ->
            mainHandler.post {
                if (!isViewActive() || !currentPlaceRequestState.finish(generation)) return@post
                originController?.setCurrentLocationSnapshot(snapshot)
                destinationController?.setCurrentLocationSnapshot(snapshot)
            }
        }
        val override = currentLocationSnapshotRequestOverride
        if (override != null) {
            override(handleResult)
        } else {
            (activity as? MainActivity)?.requestCurrentLocationSnapshot(handleResult)
        }
    }

    private fun query(preserveSort: Boolean = false) {
        val origin = originController?.selectedPlace
        val destination = destinationController?.selectedPlace
        val validation = RouteConfigValidator.validate(getString(R.string.search_title), origin, destination)
        originController?.setError(validation.originError.localizedMessage(requireContext()))
        destinationController?.setError(validation.destinationError.localizedMessage(requireContext()))
        if (!validation.isValid || origin == null || destination == null) return

        hasSubmittedQuery = true
        invalidateCurrentPlaceRequest()
        clearSuccessfulQuery()
        val isRefresh = preserveSort && currentResults.isNotEmpty()
        routeQueryState.begin(refresh = isRefresh)
        if (!isRefresh) {
            resultAdapter.submitList(emptyList())
            resultList.visibility = View.GONE
            routeResultControls.visibility = View.GONE
            sortControls.visibility = View.GONE
            resultMetaContainer.visibility = View.GONE
        }
        resultLoading.visibility = if (isRefresh) View.GONE else View.VISIBLE
        showStatus(null)
        updateRefreshEnabled()
        routeQueryCoordinator.query(origin, destination, object : RouteQueryCoordinator.Callback {
            override fun onInitialRoutes(queryId: Int, routes: List<BusRouteOption>) {
                resultLoading.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                routeQueryState.complete(
                    routes = routes,
                    preserveSort = preserveSort,
                    updatedAtMillis = System.currentTimeMillis()
                )
                resultAdapter.submitList(currentResults)
                resultList.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
                routeResultControls.visibility =
                    if (currentResults.isEmpty()) View.GONE else View.VISIBLE
                sortControls.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
                resultCount.text = RouteResultCardFormatter.resultSummary(
                    currentResults,
                    requireContext().localizedText()
                )
                resultUpdatedAt.text = getString(
                    R.string.updated_at,
                    formatUpdatedAt(routeQueryState.updatedAtMillis)
                )
                resultMetaContainer.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
                if (currentResults.isNotEmpty()) {
                    successfulQueryOrigin = origin
                    successfulQueryDestination = destination
                }
                renderSearchActions()
                updateSortControls()
                restoreSearchViewportIfNeeded()
                showStatus(if (currentResults.isEmpty()) getString(R.string.search_no_routes) else null)
                updateRefreshEnabled()
            }

            override fun onRouteWaitTimeUpdated(
                queryId: Int,
                routeId: String,
                waitTimeState: WaitTimeState
            ) {
                updateRoute(routeId) { it.copy(waitTimeState = waitTimeState) }
            }

            override fun onRouteStopPreviewUpdated(
                queryId: Int,
                routeId: String,
                preview: RouteCardStopPreview
            ) {
                updateRoute(routeId) { it.copy(stopPreview = preview) }
            }

            override fun onFailure(queryId: Int, error: Throwable) {
                resultLoading.visibility = View.GONE
                swipeRefresh.isRefreshing = false
                routeQueryState.fail(getString(R.string.search_failed), preserveResults = isRefresh)
                clearSuccessfulQuery()
                if (isRefresh) {
                    resultAdapter.submitList(currentResults)
                    resultList.visibility = View.VISIBLE
                    routeResultControls.visibility = View.VISIBLE
                    sortControls.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), R.string.refresh_failed, Toast.LENGTH_SHORT).show()
                } else {
                    resultAdapter.submitList(emptyList())
                    resultList.visibility = View.GONE
                    routeResultControls.visibility = View.GONE
                    sortControls.visibility = View.GONE
                    showStatus(getString(R.string.search_failed))
                }
                updateRefreshEnabled()
            }
        })
    }

    private fun restoreSubmittedQueryIfNeeded() {
        if (!restoredShouldRequery) return
        restoredShouldRequery = false
        if (originController?.selectedPlace != null && destinationController?.selectedPlace != null) {
            query(preserveSort = true)
        }
    }

    private fun restoreSearchViewportIfNeeded() {
        val position = pendingScrollPosition ?: return
        if (currentResults.isEmpty()) return
        pendingScrollPosition = null
        (resultList.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(
                position.coerceIn(0, currentResults.lastIndex),
                pendingScrollOffset
            )
    }

    private fun updateRoute(routeId: String, transform: (BusRouteOption) -> BusRouteOption) {
        if (!routeQueryState.update(routeId, transform)) return
        resultAdapter.submitList(currentResults)
        currentResults.firstOrNull { it.resultId == routeId }?.let(etaSheet::update)
    }

    private fun saveCurrentRoute() {
        if (saveButton.visibility != View.VISIBLE) return
        val origin = originController?.selectedPlace ?: return
        val destination = destinationController?.selectedPlace ?: return
        TemporaryRouteSaveDialog.show(
            context = requireContext(),
            routeConfigRepository = RouteConfigRepository(requireContext()),
            origin = origin,
            destination = destination
        ) {
            (activity as? MainActivity)?.refreshFrequentRoutes()
        }
    }

    private fun sortBy(field: SortField) {
        if (currentResults.isEmpty()) return
        routeQueryState.toggleSort(field)
        resultAdapter.submitList(currentResults)
        updateSortControls()
    }

    private fun updateSortControls() {
        sortButtons.forEach { (field, button) ->
            val active = field == sortField
            button.isChecked = active
            button.text = if (active) {
                "${sortLabel(field)}${if (sortDirection == SortDirection.ASC) " ↑" else " ↓"}"
            } else {
                sortLabel(field)
            }
        }
    }

    private fun sortLabel(field: SortField): String = when (field) {
        SortField.ROUTE -> getString(R.string.sort_route)
        SortField.PRICE -> getString(R.string.sort_price)
        SortField.DURATION -> getString(R.string.sort_duration)
        SortField.ARRIVAL -> getString(R.string.sort_arrival)
        SortField.WALKING_DISTANCE -> getString(R.string.sort_walking)
    }

    private fun showStatus(message: String?) {
        resultStatus.text = message.orEmpty()
        resultStatus.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun updateRefreshEnabled() {
        if (!::swipeRefresh.isInitialized) return
        val candidatesVisible = originController?.isCandidateVisible() == true ||
            destinationController?.isCandidateVisible() == true
        candidateBackCallback?.isEnabled = candidatesVisible
        swipeRefresh.isEnabled = currentResults.isNotEmpty() &&
            !candidatesVisible &&
            (!routeQueryState.isQueryInProgress || routeQueryState.isRefreshing)
    }

    private fun formatUpdatedAt(value: Long?): String {
        val timestamp = value ?: return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun isViewActive(): Boolean = isAdded && view != null && !isRemoving

    private fun swapSearchPlaces() {
        invalidateCurrentPlaceRequest()
        val destination = destinationController ?: return
        originController?.swapWith(destination)
        attributionState.swap()
        renderAttribution()
        hideCandidateLists()
        onSearchSelectionChanged()
    }

    private fun hideCandidateLists() {
        originController?.hideCandidates()
        destinationController?.hideCandidates()
        updateRefreshEnabled()
    }

    private fun renderAttribution() {
        if (!::originAttribution.isInitialized || !::destinationAttribution.isInitialized) return
        originAttribution.visibility = if (attributionState.originUsesGoogleMaps) {
            View.VISIBLE
        } else {
            View.GONE
        }
        destinationAttribution.visibility = if (attributionState.destinationUsesGoogleMaps) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun onSearchSelectionChanged() {
        hasSubmittedQuery = false
        routeQueryCoordinator.invalidate()
        routeQueryState.clear()
        swipeRefresh.isRefreshing = false
        resultAdapter.submitList(emptyList())
        resultList.visibility = View.GONE
        routeResultControls.visibility = View.GONE
        sortControls.visibility = View.GONE
        resultMetaContainer.visibility = View.GONE
        resultLoading.visibility = View.GONE
        showStatus(null)
        clearSuccessfulQuery()
        renderSearchActions()
        updateRefreshEnabled()
    }

    private fun clearSuccessfulQuery() {
        successfulQueryOrigin = null
        successfulQueryDestination = null
        renderSaveAction()
    }

    private fun renderSearchActions() {
        if (::queryButton.isInitialized) {
            queryButton.isEnabled = originController?.selectedPlace != null &&
                destinationController?.selectedPlace != null
        }
        renderSaveAction()
    }

    private fun renderSaveAction() {
        if (!::saveButton.isInitialized) return
        saveButton.visibility = if (
            SearchResultSaveEligibility.isVisible(
                queryOrigin = successfulQueryOrigin,
                queryDestination = successfulQueryDestination,
                currentOrigin = originController?.selectedPlace,
                currentDestination = destinationController?.selectedPlace,
                resultCount = currentResults.size,
                queryInProgress = routeQueryState.isQueryInProgress,
                queryFailed = routeQueryState.errorMessage != null
            )
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun invalidateCurrentPlaceRequest() {
        currentPlaceRequestState.invalidate()
        originController?.setExternalLoading(false)
    }

    private fun Place.writeTo(bundle: Bundle, prefix: String) {
        bundle.putString("${prefix}_name", name)
        bundle.putDouble("${prefix}_latitude", latitude)
        bundle.putDouble("${prefix}_longitude", longitude)
    }

    private fun Bundle.placeFor(prefix: String): Place? {
        val name = getString("${prefix}_name") ?: return null
        return Place(
            name = name,
            latitude = getDouble("${prefix}_latitude"),
            longitude = getDouble("${prefix}_longitude")
        )
    }

    companion object {
        @Volatile
        internal var busRouteRepositoryFactory: () -> BusRouteRepository =
            { CitybusBusRouteRepository() }

        @Volatile
        internal var placeSearchRepositoryFactory: () -> PlaceSearchRepository =
            { CitybusPlaceSearchRepository() }

        @Volatile
        internal var routeDetailRepositoryFactory: () -> RouteDetailRepository =
            { CitybusRouteDetailRepository() }

        @Volatile
        internal var currentPlaceRequestOverride:
            ((Boolean, (CurrentPlaceSelectionResult) -> Unit) -> Unit)? = null

        @Volatile
        internal var currentLocationSnapshotRequestOverride:
            (((CurrentLocationSnapshot?) -> Unit) -> Unit)? = null

        internal fun resetTestDependencies() {
            busRouteRepositoryFactory = { CitybusBusRouteRepository() }
            placeSearchRepositoryFactory = { CitybusPlaceSearchRepository() }
            routeDetailRepositoryFactory = { CitybusRouteDetailRepository() }
            currentPlaceRequestOverride = null
            currentLocationSnapshotRequestOverride = null
        }

        const val STATE_ORIGIN = "search_origin"
        const val STATE_DESTINATION = "search_destination"
        const val STATE_SORT_FIELD = "search_sort_field"
        const val STATE_SORT_DIRECTION = "search_sort_direction"
        const val STATE_ORIGIN_INPUT = "search_origin_input"
        const val STATE_DESTINATION_INPUT = "search_destination_input"
        const val STATE_ORIGIN_GOOGLE_ATTRIBUTION = "search_origin_google_attribution"
        const val STATE_DESTINATION_GOOGLE_ATTRIBUTION = "search_destination_google_attribution"
        const val STATE_HAS_SUBMITTED_QUERY = "search_has_submitted_query"
        const val STATE_SEARCH_SCROLL_POSITION = "search_scroll_position"
        const val STATE_SEARCH_SCROLL_OFFSET = "search_scroll_offset"
        private const val SEARCH_MAX_VISIBLE_CANDIDATE_ROWS = 3
    }
}
