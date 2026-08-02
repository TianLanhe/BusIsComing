package com.golink.busiscoming.ui.main

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
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
import com.golink.busiscoming.data.model.RouteConfigValidationError
import com.golink.busiscoming.data.model.RouteConfigValidator
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.CitybusBusRouteRepository
import com.golink.busiscoming.data.repository.CitybusPlaceSearchRepository
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.PlaceSearchRepository
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.ui.common.PlaceInputController
import com.golink.busiscoming.ui.common.PlacePairEditorView
import com.golink.busiscoming.ui.common.ResultListDrivenAppBar
import com.golink.busiscoming.ui.common.RouteResultControlsView
import com.golink.busiscoming.ui.common.localizedText
import com.google.android.material.appbar.AppBarLayout
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
    private val candidateLocationSnapshotRequestState =
        SearchCandidateLocationSnapshotRequestState()
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
    private val presentationState = SearchPresentationState()
    private val successfulQueryContextState = SuccessfulSearchContextState()
    private val refreshFeedbackState = RouteRefreshFeedbackState()
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
    private var suppressCancelledQueryStatus: Boolean = false
    private var candidateLocationSnapshot: CurrentLocationSnapshot? = null
    private var hasSubmittedQuery: Boolean = false
    private var successfulQueryOrigin: Place? = null
    private var successfulQueryDestination: Place? = null
    private var isViewStateRestored: Boolean = false
    private var hasPendingDestinationSelection: Boolean = false
    private var pendingScrollPosition: Int? = null
    private var pendingScrollOffset: Int = 0
    private var refreshFeedbackGeneration: Int = 0
    private var refreshFinishRunnable: Runnable? = null
    private lateinit var resultAdapter: BusRouteAdapter
    private lateinit var etaSheet: EtaArrivalsBottomSheet
    private lateinit var resultList: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var routeResultControls: View
    private lateinit var sortControls: View
    private lateinit var sortButtons: Map<SortField, MaterialButton>
    private lateinit var resultStatusCard: View
    private lateinit var resultStatusProgress: ProgressBar
    private lateinit var resultStatusTitle: TextView
    private lateinit var resultStatusMessage: TextView
    private lateinit var resultRefreshOverlay: View
    private lateinit var resultRefreshProgress: View
    private lateinit var resultRefreshSuccess: View
    private lateinit var resultListBasePadding: SearchResultListPadding
    private lateinit var resultMetaContainer: View
    private lateinit var resultCount: TextView
    private lateinit var resultUpdatedAt: TextView
    private lateinit var inputContainer: View
    private lateinit var tripContext: View
    private lateinit var tripRouteText: TextView
    private lateinit var editButton: MaterialButton
    private lateinit var saveButton: MaterialButton
    private lateinit var queryButton: MaterialButton
    private lateinit var appBar: AppBarLayout
    private lateinit var tripEditorTransitionController: SearchTripEditorTransitionController
    private var originCaptionRenderer: SearchFieldCaptionRenderer? = null
    private var destinationCaptionRenderer: SearchFieldCaptionRenderer? = null
    private var candidateBackCallback: OnBackPressedCallback? = null
    private val candidateScrollLock = SearchCandidateScrollLock()

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
        appBar = view.findViewById(R.id.searchAppBar)
        ResultListDrivenAppBar.install(appBar)
        val context = requireContext()
        val placeEditor = view.findViewById<PlacePairEditorView>(R.id.searchPlacePairEditor)
        val originInput = placeEditor.originInput
        val destinationInput = placeEditor.destinationInput
        originInput.isSaveEnabled = false
        destinationInput.isSaveEnabled = false
        val originLayout = placeEditor.originInputLayout
        val destinationLayout = placeEditor.destinationInputLayout
        val currentLocationButton = placeEditor.currentLocationButton
        originCaptionRenderer = SearchFieldCaptionRenderer(
            inputLayout = originLayout,
            input = originInput,
            labelResource = R.string.search_field_origin_label
        )
        destinationCaptionRenderer = SearchFieldCaptionRenderer(
            inputLayout = destinationLayout,
            input = destinationInput,
            labelResource = R.string.search_field_destination_label
        )

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
            exclusiveVerticalScroll = true,
            onCandidateVisibilityChanged = { visible ->
                if (visible) destinationController?.hideCandidates()
                placeEditor.requestToolAlignment()
                setCandidateScrollLock()
                updateRefreshEnabled()
            },
            onPlaceSelected = {
                invalidateCurrentPlaceRequest()
                attributionState.clearOrigin()
                renderAttribution()
                onPotentialSearchSelectionChanged()
            },
            onUserTextEdited = {
                invalidateCurrentPlaceRequest()
                attributionState.clearOrigin()
                renderAttribution()
                onPotentialSearchSelectionChanged()
            },
            onMessageChanged = { message ->
                originCaptionRenderer?.onPlaceInputMessage(message)
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
            exclusiveVerticalScroll = true,
            onCandidateVisibilityChanged = { visible ->
                if (visible) originController?.hideCandidates()
                placeEditor.requestToolAlignment()
                setCandidateScrollLock()
                updateRefreshEnabled()
            },
            onPlaceSelected = {
                attributionState.clearDestination()
                renderAttribution()
                onPotentialSearchSelectionChanged()
            },
            onUserTextEdited = {
                attributionState.clearDestination()
                renderAttribution()
                onPotentialSearchSelectionChanged()
            },
            onMessageChanged = { message ->
                destinationCaptionRenderer?.onPlaceInputMessage(message)
            }
        )
        applyCandidateLocationSnapshotIfFresh()
        currentLocationButton.setOnClickListener { requestCurrentOrigin(isAuto = false) }

        resultList = view.findViewById(R.id.searchResultList)
        swipeRefresh = view.findViewById(R.id.searchSwipeRefresh)
        val resultControls = view.findViewById<RouteResultControlsView>(
            R.id.searchRouteResultControls
        )
        routeResultControls = resultControls
        sortControls = resultControls.sortControls
        resultStatusCard = view.findViewById(R.id.resultStatusCard)
        resultStatusProgress = view.findViewById(R.id.resultStatusProgress)
        resultStatusTitle = view.findViewById(R.id.resultStatusTitle)
        resultStatusMessage = view.findViewById(R.id.resultStatusMessage)
        resultRefreshOverlay = view.findViewById(R.id.searchResultRefreshOverlay)
        resultRefreshProgress = view.findViewById(R.id.searchResultRefreshProgress)
        resultRefreshSuccess = view.findViewById(R.id.searchResultRefreshSuccess)
        resultMetaContainer = resultControls.summaryContainer
        resultCount = resultControls.summaryText
        resultUpdatedAt = resultControls.updatedAtText
        inputContainer = view.findViewById(R.id.searchInputContainer)
        tripContext = view.findViewById(R.id.searchTripContext)
        tripRouteText = view.findViewById(R.id.searchTripRouteText)
        editButton = view.findViewById(R.id.searchEditButton)
        saveButton = view.findViewById(R.id.searchSaveButton)
        queryButton = view.findViewById(R.id.searchQueryButton)
        tripEditorTransitionController = SearchTripEditorTransitionController(
            parent = view as ViewGroup,
            editor = inputContainer,
            tripContext = tripContext,
            lifecycleStarted = {
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            }
        )
        etaSheet = EtaArrivalsBottomSheet(context)
        resultAdapter = BusRouteAdapter(
            onRouteClick = { route ->
                RouteDetailNavigator.open(
                    context = requireContext(),
                    route = route,
                    queryOrigin = successfulQueryOrigin,
                    queryDestination = successfulQueryDestination
                )
            },
            onEtaClick = etaSheet::show,
            onMonitorClick = { route ->
                successfulQueryOrigin?.let { origin ->
                    (activity as? MainActivity)?.showMonitorSettings(route, origin)
                }
            }
        )
        resultList.layoutManager = LinearLayoutManager(context)
        resultList.adapter = resultAdapter
        resultList.isNestedScrollingEnabled = true
        resultListBasePadding = SearchResultListPadding(
            left = resultList.paddingLeft,
            top = resultList.paddingTop,
            right = resultList.paddingRight,
            bottom = resultList.paddingBottom
        )
        swipeRefresh.setColorSchemeResources(R.color.bus_chip_selected)
        swipeRefresh.setOnRefreshListener { query(preserveSort = true) }
        updateRefreshEnabled()
        sortButtons = resultControls.sortButtons
        sortButtons.forEach { (field, button) -> button.setOnClickListener { sortBy(field) } }
        updateSortControls()
        renderSearchUi()

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
        editButton.setOnClickListener { beginEditingCurrentTrip() }
        saveButton.setOnClickListener { saveCurrentRoute() }
        originInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
                false
            } else {
                query()
                true
            }
        }
        destinationInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH && actionId != EditorInfo.IME_ACTION_DONE) {
                false
            } else {
                query()
                true
            }
        }
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
        renderSearchUi()
        restoreSubmittedQueryIfNeeded()
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
        requestCandidateLocationSnapshotIfNeeded()
        val generation = currentPlaceRequestState.beginAutoRequest(
            hasSelectedOrigin = originController?.selectedPlace != null || restoredOrigin != null,
            originInput = originController?.currentInputText().orEmpty(),
            hasSubmittedQuery = hasSubmittedQuery
        ) ?: return
        requestCurrentOrigin(isAuto = true, generation = generation)
    }

    fun onDestinationHidden() {
        invalidateCurrentPlaceRequest()
        candidateLocationSnapshotRequestState.resetForNextGeneration()
        routeQueryCoordinator.invalidate()
        routeQueryState.cancel()
        presentationState.cancelQuery()
        suppressCancelledQueryStatus = true
        cancelRefreshFeedback()
        swipeRefresh.isRefreshing = false
        renderSearchUi()
        updateRefreshEnabled()
    }

    override fun onDestroyView() {
        invalidateCurrentPlaceRequest()
        candidateLocationSnapshotRequestState.resetForNextGeneration()
        routeQueryCoordinator.invalidate()
        routeQueryState.cancel()
        cancelRefreshFeedback()
        originController?.dispose()
        destinationController?.dispose()
        originController = null
        destinationController = null
        originCaptionRenderer = null
        destinationCaptionRenderer = null
        isViewStateRestored = false
        hasPendingDestinationSelection = false
        candidateBackCallback = null
        candidateScrollLock.reset()
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
        originCaptionRenderer?.setLocationFailure(false)
        originController?.setExternalLoading(true)
        val handleResult: (CurrentPlaceSelectionResult) -> Unit = { result ->
            mainHandler.post {
                if (!isViewActive() || !currentPlaceRequestState.finish(generation)) return@post
                originController?.setExternalLoading(false)
                when (result) {
                    is CurrentPlaceSelectionResult.Success -> {
                        applyCandidateLocationSnapshot(
                            result.snapshot,
                            invalidatePendingRequest = true
                        )
                        originController?.setSelectedPlace(result.place)
                        attributionState.setOriginGoogleMaps(
                            result.attribution == PlaceAttribution.GOOGLE_MAPS
                        )
                        renderAttribution()
                        onPotentialSearchSelectionChanged()
                    }
                    CurrentPlaceSelectionResult.Failure -> {
                        requestCandidateLocationSnapshotIfNeeded()
                        if (isAuto) {
                            originCaptionRenderer?.setLocationFailure(true)
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

    private fun requestCandidateLocationSnapshotIfNeeded() {
        if (applyCandidateLocationSnapshotIfFresh()) return
        val context = context ?: return
        val canRequest = LocationPermissionUtils.hasForegroundLocationPermission(context) &&
            SystemLocationUtils.isLocationEnabled(context)
        val generation = candidateLocationSnapshotRequestState.beginRequest(canRequest) ?: return
        val handleResult: (CurrentLocationSnapshot?) -> Unit = { snapshot ->
            mainHandler.post {
                if (
                    !isViewActive() ||
                    !candidateLocationSnapshotRequestState.finish(generation)
                ) {
                    return@post
                }
                snapshot?.let { applyCandidateLocationSnapshot(it) }
            }
        }
        val override = currentLocationSnapshotRequestOverride
        if (override != null) {
            override(handleResult)
        } else {
            (activity as? MainActivity)?.requestCurrentLocationSnapshot(handleResult)
        }
    }

    private fun applyCandidateLocationSnapshotIfFresh(): Boolean {
        val snapshot = candidateLocationSnapshot ?: return false
        val ageMillis = SystemClock.elapsedRealtime() - snapshot.elapsedRealtimeMillis
        if (ageMillis !in 0..CANDIDATE_LOCATION_SNAPSHOT_MAX_AGE_MS) {
            candidateLocationSnapshot = null
            return false
        }
        applyCandidateLocationSnapshot(snapshot)
        return true
    }

    private fun applyCandidateLocationSnapshot(
        snapshot: CurrentLocationSnapshot,
        invalidatePendingRequest: Boolean = false
    ) {
        if (invalidatePendingRequest) {
            candidateLocationSnapshotRequestState.invalidatePending()
        }
        candidateLocationSnapshot = snapshot
        originController?.setCurrentLocationSnapshot(snapshot)
        destinationController?.setCurrentLocationSnapshot(snapshot)
    }

    private fun query(preserveSort: Boolean = false) {
        if (routeQueryState.isQueryInProgress || refreshFeedbackState.blocksQueries) return
        val origin = originController?.selectedPlace
        val destination = destinationController?.selectedPlace
        val validation = RouteConfigValidator.validate(getString(R.string.search_title), origin, destination)
        originCaptionRenderer?.setValidation(captionValidation(validation.originError))
        destinationCaptionRenderer?.setValidation(captionValidation(validation.destinationError))
        if (!validation.isValid || origin == null || destination == null) return

        hasSubmittedQuery = true
        suppressCancelledQueryStatus = false
        invalidateCurrentPlaceRequest()
        val isRefresh = preserveSort && currentResults.isNotEmpty()
        if (!isRefresh) clearSuccessfulQuery()
        routeQueryState.begin(refresh = isRefresh)
        if (!isRefresh) {
            presentationState.beginQuery(origin, destination)
        }
        if (!isRefresh) {
            resultAdapter.submitList(emptyList())
            resultList.visibility = View.GONE
            routeResultControls.visibility = View.GONE
            sortControls.visibility = View.GONE
            resultMetaContainer.visibility = View.GONE
        }
        val refreshToken = if (isRefresh) beginRefreshFeedback() else null
        renderSearchUi()
        updateRefreshEnabled()
        routeQueryCoordinator.query(origin, destination, object : RouteQueryCoordinator.Callback {
            override fun onInitialRoutes(queryId: Int, routes: List<BusRouteOption>) {
                swipeRefresh.isRefreshing = false
                if (isRefresh) {
                    handleRefreshSuccess(
                        refreshToken = requireNotNull(refreshToken),
                        routes = routes,
                        origin = origin,
                        destination = destination,
                        preserveSort = preserveSort
                    )
                } else {
                    displayInitialResults(
                        routes = routes,
                        preserveSort = preserveSort,
                        origin = origin,
                        destination = destination,
                        updatePresentation = true,
                        queryId = queryId
                    )
                }
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
                swipeRefresh.isRefreshing = false
                if (isRefresh) {
                    handleRefreshFailure(requireNotNull(refreshToken))
                } else {
                    routeQueryState.fail(getString(R.string.search_failed), preserveResults = false)
                    presentationState.failQuery()
                    clearSuccessfulQuery()
                    resultAdapter.submitList(emptyList())
                    resultList.visibility = View.GONE
                    routeResultControls.visibility = View.GONE
                    sortControls.visibility = View.GONE
                    renderSearchUi()
                    updateRefreshEnabled()
                }
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

    private fun displayInitialResults(
        routes: List<BusRouteOption>,
        preserveSort: Boolean,
        origin: Place,
        destination: Place,
        updatePresentation: Boolean,
        queryId: Int? = null
    ) {
        routeQueryState.complete(
            routes = routes,
            preserveSort = preserveSort,
            updatedAtMillis = System.currentTimeMillis()
        )
        resultAdapter.submitList(SearchRouteItemProjector.project(currentResults))
        resultList.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
        routeResultControls.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
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
            if (updatePresentation) {
                presentationState.completeWithResults()
                successfulQueryContextState.recordSuccess(
                    queryId = requireNotNull(queryId),
                    snapshot = SearchQuerySnapshot(origin, destination)
                )
            } else if (
                !successfulQueryContextState.retainForRefresh(
                    SearchQuerySnapshot(origin, destination)
                )
            ) {
                successfulQueryContextState.invalidate()
            }
        } else if (updatePresentation) {
            presentationState.completeEmpty()
        }
        renderSearchUi()
        updateSortControls()
        restoreSearchViewportIfNeeded()
        updateRefreshEnabled()
    }

    private fun beginRefreshFeedback(): Int {
        val token = ++refreshFeedbackGeneration
        check(refreshFeedbackState.start(token))
        renderRefreshFeedback()
        return token
    }

    private fun handleRefreshSuccess(
        refreshToken: Int,
        routes: List<BusRouteOption>,
        origin: Place,
        destination: Place,
        preserveSort: Boolean
    ) {
        val result = if (routes.isEmpty()) RouteRefreshResult.EMPTY else RouteRefreshResult.NON_EMPTY
        if (!refreshFeedbackState.succeed(refreshToken, result)) return
        if (routes.isNotEmpty()) {
            displayInitialResults(
                routes = routes,
                preserveSort = preserveSort,
                origin = origin,
                destination = destination,
                updatePresentation = false
            )
            resultList.scrollToPosition(0)
        }
        renderRefreshFeedback()
        renderSearchUi()
        scheduleRefreshSuccessFinish(refreshToken, origin, destination, preserveSort)
    }

    private fun scheduleRefreshSuccessFinish(
        refreshToken: Int,
        origin: Place,
        destination: Place,
        preserveSort: Boolean
    ) {
        refreshFinishRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            finishRefreshSuccess(refreshToken, origin, destination, preserveSort)
        }
        refreshFinishRunnable = runnable
        mainHandler.postDelayed(runnable, SEARCH_REFRESH_SUCCESS_DURATION_MS)
    }

    private fun finishRefreshSuccess(
        refreshToken: Int,
        origin: Place,
        destination: Place,
        preserveSort: Boolean
    ) {
        val action = refreshFeedbackState.finishSuccess(refreshToken) ?: return
        refreshFinishRunnable = null
        if (action == RouteRefreshFinishAction.SHOW_EMPTY_RESULTS) {
            presentationState.completeRefreshEmpty()
            clearSuccessfulQuery()
            displayInitialResults(
                routes = emptyList(),
                preserveSort = preserveSort,
                origin = origin,
                destination = destination,
                updatePresentation = false
            )
        }
        renderRefreshFeedback()
        renderSearchUi()
        updateRefreshEnabled()
    }

    private fun handleRefreshFailure(refreshToken: Int) {
        if (!refreshFeedbackState.fail(refreshToken)) return
        routeQueryState.fail(getString(R.string.refresh_failed), preserveResults = true)
        refreshFinishRunnable?.let(mainHandler::removeCallbacks)
        refreshFinishRunnable = null
        renderRefreshFeedback()
        renderSearchUi()
        updateRefreshEnabled()
        Toast.makeText(requireContext(), R.string.refresh_failed, Toast.LENGTH_SHORT).show()
    }

    private fun cancelRefreshFeedback() {
        refreshFinishRunnable?.let(mainHandler::removeCallbacks)
        refreshFinishRunnable = null
        refreshFeedbackState.cancel()
        if (::resultRefreshOverlay.isInitialized) renderRefreshFeedback()
    }

    private fun renderRefreshFeedback() {
        if (!::resultRefreshOverlay.isInitialized || !::resultListBasePadding.isInitialized) return
        val visualState = refreshFeedbackState.visualState
        val isVisible = visualState != RouteRefreshVisualState.IDLE
        resultRefreshOverlay.visibility = if (isVisible) View.VISIBLE else View.GONE
        resultRefreshProgress.visibility = if (visualState == RouteRefreshVisualState.REFRESHING) {
            View.VISIBLE
        } else {
            View.GONE
        }
        resultRefreshSuccess.visibility = if (visualState == RouteRefreshVisualState.SUCCESS) {
            View.VISIBLE
        } else {
            View.GONE
        }
        resultRefreshOverlay.contentDescription = getString(
            if (visualState == RouteRefreshVisualState.SUCCESS) {
                R.string.route_refresh_complete
            } else {
                R.string.route_refreshing
            }
        )
        resultList.setPadding(
            resultListBasePadding.left,
            resultListBasePadding.top + if (isVisible) dp(SEARCH_REFRESH_LIST_TOP_INSET_DP) else 0,
            resultListBasePadding.right,
            resultListBasePadding.bottom
        )
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
        resultAdapter.submitList(SearchRouteItemProjector.project(currentResults))
        currentResults.firstOrNull { it.resultId == routeId }?.let(etaSheet::update)
    }

    private fun saveCurrentRoute() {
        val context = currentSavableContext() ?: return
        TemporaryRouteSaveDialog.show(
            context = requireContext(),
            saveGateway = routeConfigSaveGatewayFactory(requireContext()),
            capturedContext = context,
            canSave = ::isCurrentSavableContext,
            onSaved = {
                val activeContext = currentSavableContext() ?: return@show
                if (
                    activeContext.queryId != context.queryId ||
                    activeContext.snapshot != context.snapshot
                ) {
                    return@show
                }
                if (!presentationState.markSaved()) return@show
                (activity as? MainActivity)?.refreshFrequentRoutes()
                renderSearchUi()
            }
        )
    }

    private fun sortBy(field: SortField) {
        if (currentResults.isEmpty()) return
        routeQueryState.toggleSort(field)
        resultAdapter.submitList(SearchRouteItemProjector.project(currentResults))
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

    private fun updateRefreshEnabled() {
        if (!::swipeRefresh.isInitialized) return
        val candidatesVisible = candidateScrollLock.isOuterScrollLocked()
        candidateBackCallback?.isEnabled = candidatesVisible
        swipeRefresh.isEnabled = presentationState.mode == SearchDisplayMode.RESULTS &&
            currentResults.isNotEmpty() &&
            !candidatesVisible &&
            !routeQueryState.isQueryInProgress &&
            !refreshFeedbackState.blocksQueries
    }

    private fun setCandidateScrollLock() {
        candidateScrollLock.update(
            originCandidatesVisible = originController?.isCandidateVisible() == true,
            destinationCandidatesVisible = destinationController?.isCandidateVisible() == true
        )
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
        onPotentialSearchSelectionChanged()
    }

    private fun hideCandidateLists() {
        originController?.hideCandidates()
        destinationController?.hideCandidates()
        updateRefreshEnabled()
    }

    private fun renderAttribution() {
        originCaptionRenderer?.setGoogleMaps(attributionState.originUsesGoogleMaps)
        destinationCaptionRenderer?.setGoogleMaps(attributionState.destinationUsesGoogleMaps)
    }

    private fun captionValidation(
        error: RouteConfigValidationError?
    ): SearchFieldValidation? = when (error) {
        RouteConfigValidationError.ORIGIN_REQUIRED,
        RouteConfigValidationError.DESTINATION_REQUIRED,
        RouteConfigValidationError.REQUIRED -> SearchFieldValidation.MISSING_PLACE
        RouteConfigValidationError.SAME_PLACES -> SearchFieldValidation.SAME_AS_ORIGIN
        null -> null
    }

    private fun onSearchSelectionChanged() {
        if (presentationState.mode == SearchDisplayMode.EDITING_RESULTS) {
            presentationState.onInputChanged()
            renderSearchUi()
            updateRefreshEnabled()
            return
        }
        hasSubmittedQuery = false
        suppressCancelledQueryStatus = false
        routeQueryCoordinator.invalidate()
        routeQueryState.clear()
        presentationState.onInputChanged()
        cancelRefreshFeedback()
        swipeRefresh.isRefreshing = false
        resultAdapter.submitList(emptyList())
        resultList.visibility = View.GONE
        routeResultControls.visibility = View.GONE
        sortControls.visibility = View.GONE
        resultMetaContainer.visibility = View.GONE
        clearSuccessfulQuery()
        renderSearchUi()
        updateRefreshEnabled()
    }

    private fun onPotentialSearchSelectionChanged() {
        if (
            SearchPlacePairMutationPolicy.shouldInvalidate(
                querySnapshot = presentationState.querySnapshot,
                currentOrigin = originController?.selectedPlace,
                currentDestination = destinationController?.selectedPlace
            )
        ) {
            onSearchSelectionChanged()
        } else {
            renderSearchUi()
            updateRefreshEnabled()
        }
    }

    private fun beginEditingCurrentTrip() {
        if (!presentationState.beginEditingResults()) return
        if (routeQueryState.isRefreshing) {
            routeQueryCoordinator.invalidate()
            routeQueryState.cancel()
            cancelRefreshFeedback()
            swipeRefresh.isRefreshing = false
        }
        renderSearchUi()
        appBar.doOnNextLayout {
            if (presentationState.mode == SearchDisplayMode.EDITING_RESULTS) {
                appBar.setExpanded(true, false)
            }
        }
        updateRefreshEnabled()
    }

    private fun clearSuccessfulQuery() {
        successfulQueryOrigin = null
        successfulQueryDestination = null
        successfulQueryContextState.invalidate()
        renderSaveAction()
    }

    private fun renderSearchUi() {
        if (!::queryButton.isInitialized) return
        val ui = SearchQueryUiPolicy.resolve(
            queryState = routeQueryState,
            hasSubmittedQuery = hasSubmittedQuery && !suppressCancelledQueryStatus,
            hasValidPlaces = originController?.selectedPlace != null &&
                destinationController?.selectedPlace != null,
            displayMode = presentationState.mode,
            refreshFeedbackVisible = refreshFeedbackState.visualState != RouteRefreshVisualState.IDLE,
            refreshFeedbackBlocksQueries = refreshFeedbackState.blocksQueries
        )
        queryButton.isEnabled = ui.isQueryEnabled
        queryButton.setText(if (ui.isQuerying) R.string.action_querying else R.string.search_routes)
        renderTripContext()
        renderRetainedResults()
        renderStatusCard(ui.statusCard)
        renderSaveAction()
    }

    private fun renderTripContext() {
        val hasSnapshot = presentationState.querySnapshot != null
        val showTripContext = when (presentationState.mode) {
            SearchDisplayMode.RESULTS -> SearchTripContextVisibility.shouldRestoreFoldedContext(
                savedMode = presentationState.mode,
                retainedResultCount = currentResults.size,
                hasValidSnapshot = hasSnapshot
            )

            SearchDisplayMode.EDITING_RESULTS ->
                SearchTripContextVisibility.isVisible(
                    mode = presentationState.mode,
                    resultCount = currentResults.size
                ) && hasSnapshot

            else -> false
        }
        val showEditor = !showTripContext
        if (!showEditor) {
            originController?.clearFocusAndHideCandidates()
            destinationController?.clearFocusAndHideCandidates()
        }
        val snapshot = presentationState.querySnapshot
        if (showTripContext && snapshot != null) {
            tripRouteText.text = getString(
                R.string.search_trip_route,
                snapshot.origin.name,
                snapshot.destination.name
            )
        } else {
            tripRouteText.text = null
        }
        editButton.visibility = if (showTripContext) View.VISIBLE else View.GONE
        tripEditorTransitionController.render(showEditor = showEditor, animate = true)
    }

    private fun renderRetainedResults() {
        val showResults = currentResults.isNotEmpty() &&
            (presentationState.mode == SearchDisplayMode.RESULTS ||
                presentationState.mode == SearchDisplayMode.EDITING_RESULTS)
        if (showResults) resultAdapter.submitList(SearchRouteItemProjector.project(currentResults))
        resultList.visibility = if (showResults) View.VISIBLE else View.GONE
        routeResultControls.visibility = if (showResults) View.VISIBLE else View.GONE
        sortControls.visibility = if (showResults) View.VISIBLE else View.GONE
        resultMetaContainer.visibility = if (showResults) View.VISIBLE else View.GONE
    }

    private fun renderStatusCard(status: SearchQueryStatusCard) {
        if (!::resultStatusCard.isInitialized) return
        val content = when (status) {
            SearchQueryStatusCard.LOADING -> StatusCardContent(
                title = R.string.route_query_loading_title,
                message = R.string.route_query_loading_message,
                showProgress = true
            )

            SearchQueryStatusCard.EMPTY -> StatusCardContent(
                title = R.string.search_no_routes,
                message = R.string.search_no_routes_message,
                showProgress = false
            )

            SearchQueryStatusCard.FAILURE -> StatusCardContent(
                title = R.string.route_query_failed,
                message = R.string.search_route_query_failure_message,
                showProgress = false
            )

            SearchQueryStatusCard.HIDDEN -> null
        }
        if (content == null) {
            resultStatusCard.visibility = View.GONE
            resultStatusProgress.visibility = View.GONE
            return
        }
        resultStatusTitle.setText(content.title)
        resultStatusMessage.setText(content.message)
        resultStatusMessage.visibility = View.VISIBLE
        resultStatusProgress.visibility = if (content.showProgress) View.VISIBLE else View.GONE
        resultStatusCard.visibility = View.VISIBLE
    }

    private fun renderSaveAction() {
        if (!::saveButton.isInitialized) return
        val savable = currentSavableContext() != null
        val snapshot = presentationState.querySnapshot
        val saved = presentationState.saveState == SearchSaveState.SAVED &&
            snapshot != null &&
            successfulQueryContextState.currentFor(snapshot, currentResults.size) != null &&
            SearchTripContextVisibility.isVisible(presentationState.mode, currentResults.size)
        saveButton.visibility = if (savable || saved) View.VISIBLE else View.GONE
        saveButton.isEnabled = savable
        saveButton.setText(if (saved) R.string.search_trip_saved else R.string.search_trip_save)
        saveButton.setIconResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
        saveButton.contentDescription = getString(
            if (saved) R.string.search_trip_saved_description else R.string.search_trip_save_description
        )
    }

    private fun currentSavableContext(): SuccessfulSearchContext? {
        if (presentationState.saveState != SearchSaveState.AVAILABLE) return null
        if (!SearchTripContextVisibility.isVisible(presentationState.mode, currentResults.size)) return null
        val snapshot = presentationState.querySnapshot ?: return null
        val context = successfulQueryContextState.currentFor(snapshot, currentResults.size) ?: return null
        if (successfulQueryOrigin != snapshot.origin || successfulQueryDestination != snapshot.destination) return null
        return context
    }

    private fun isCurrentSavableContext(context: SuccessfulSearchContext): Boolean =
        successfulQueryContextState.isCurrent(context) && currentSavableContext() == context

    private fun invalidateCurrentPlaceRequest() {
        currentPlaceRequestState.invalidate()
        originController?.setExternalLoading(false)
    }

    private data class StatusCardContent(
        val title: Int,
        val message: Int,
        val showProgress: Boolean
    )

    private data class SearchResultListPadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
        internal var routeConfigSaveGatewayFactory: (android.content.Context) -> RouteConfigSaveGateway =
            { context -> RouteConfigRepositorySaveGateway(context) }

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
            routeConfigSaveGatewayFactory = { context -> RouteConfigRepositorySaveGateway(context) }
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
        private const val SEARCH_REFRESH_LIST_TOP_INSET_DP = 44
        private const val SEARCH_REFRESH_SUCCESS_DURATION_MS = 500L
        const val STATE_HAS_SUBMITTED_QUERY = "search_has_submitted_query"
        const val STATE_SEARCH_SCROLL_POSITION = "search_scroll_position"
        const val STATE_SEARCH_SCROLL_OFFSET = "search_scroll_offset"
        private const val SEARCH_MAX_VISIBLE_CANDIDATE_ROWS = 6
        private const val CANDIDATE_LOCATION_SNAPSHOT_MAX_AGE_MS = 30_000L
    }
}
