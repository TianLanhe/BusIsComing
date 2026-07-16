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
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.golink.busiscoming.R
import com.golink.busiscoming.data.location.CurrentPlaceSelectionResult
import com.golink.busiscoming.data.location.PlaceAttribution
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
import com.golink.busiscoming.ui.navigation.RouteQueryGeneration
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
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
    private val interactionGeneration = RouteQueryGeneration()
    private val routeQueryCoordinator = RouteQueryCoordinator(
        repository = busRouteRepository,
        executor = queryExecutor,
        postToOwner = { runnable -> mainHandler.post(runnable) },
        isOwnerActive = ::isViewActive
    )
    private var originController: PlaceInputController? = null
    private var destinationController: PlaceInputController? = null
    private val routeQueryState = RouteQueryState()
    private val currentResults: List<BusRouteOption>
        get() = routeQueryState.results
    private val sortField: SortField?
        get() = routeQueryState.sortField
    private val sortDirection: SortDirection
        get() = routeQueryState.sortDirection
    private var restoredOrigin: Place? = null
    private var restoredDestination: Place? = null
    private lateinit var resultAdapter: BusRouteAdapter
    private lateinit var detailSheet: RouteDetailBottomSheet
    private lateinit var etaSheet: EtaArrivalsBottomSheet
    private lateinit var resultList: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var sortControls: View
    private lateinit var sortButtons: Map<SortField, MaterialButton>
    private lateinit var resultLoading: ProgressBar
    private lateinit var resultStatus: TextView
    private lateinit var resultSummaryContainer: View
    private lateinit var resultSummary: TextView
    private lateinit var resultUpdatedAt: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var originAttribution: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredOrigin = savedInstanceState?.placeFor(STATE_ORIGIN)
        restoredDestination = savedInstanceState?.placeFor(STATE_DESTINATION)
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
        val originInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.searchOriginInput)
        val destinationInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.searchDestinationInput)
        val originLayout = view.findViewById<TextInputLayout>(R.id.searchOriginLayout)
        val destinationLayout = view.findViewById<TextInputLayout>(R.id.searchDestinationLayout)
        originAttribution = view.findViewById(R.id.searchOriginAttribution)

        originController = PlaceInputController(
            context = context,
            input = originInput,
            inputLayout = originLayout,
            loadingView = view.findViewById(R.id.searchOriginLoading),
            candidateList = view.findViewById(R.id.searchOriginCandidateList),
            placeSearchRepository = placeSearchRepositoryFactory(),
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = ::isViewActive,
            onCandidateVisibilityChanged = { visible ->
                if (visible) destinationController?.hideCandidates()
                updateRefreshEnabled()
            },
            onUserTextEdited = { originAttribution.visibility = View.GONE }
        )
        destinationController = PlaceInputController(
            context = context,
            input = destinationInput,
            inputLayout = destinationLayout,
            loadingView = view.findViewById(R.id.searchDestinationLoading),
            candidateList = view.findViewById(R.id.searchDestinationCandidateList),
            placeSearchRepository = placeSearchRepositoryFactory(),
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = ::isViewActive,
            onCandidateVisibilityChanged = { visible ->
                if (visible) originController?.hideCandidates()
                updateRefreshEnabled()
            }
        )
        originLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        originLayout.setEndIconDrawable(R.drawable.ic_location_outline)
        originLayout.setEndIconContentDescription(getString(R.string.use_my_location))
        originLayout.setEndIconOnClickListener { requestCurrentOrigin(isAuto = false) }

        resultList = view.findViewById(R.id.searchResultList)
        swipeRefresh = view.findViewById(R.id.searchSwipeRefresh)
        sortControls = view.findViewById(R.id.searchSortControls)
        resultLoading = view.findViewById(R.id.searchResultLoading)
        resultStatus = view.findViewById(R.id.searchResultStatus)
        resultSummaryContainer = view.findViewById(R.id.searchResultSummaryContainer)
        resultSummary = view.findViewById(R.id.searchResultSummary)
        resultUpdatedAt = view.findViewById(R.id.searchResultUpdatedAt)
        saveButton = view.findViewById(R.id.searchSaveButton)
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
        resultList.isNestedScrollingEnabled = false
        swipeRefresh.setColorSchemeResources(R.color.bus_chip_selected)
        swipeRefresh.setOnRefreshListener { query(preserveSort = true) }
        updateRefreshEnabled()
        sortButtons = mapOf(
            SortField.ROUTE to view.findViewById(R.id.searchSortRouteButton),
            SortField.PRICE to view.findViewById(R.id.searchSortPriceButton),
            SortField.DURATION to view.findViewById(R.id.searchSortDurationButton),
            SortField.ARRIVAL to view.findViewById(R.id.searchSortArrivalButton),
            SortField.WALKING_DISTANCE to view.findViewById(R.id.searchSortWalkingButton)
        )
        sortButtons.forEach { (field, button) -> button.setOnClickListener { sortBy(field) } }
        restoredOrigin?.let { originController?.setSelectedPlace(it) }
        restoredDestination?.let { destinationController?.setSelectedPlace(it) }
        updateSortControls()

        view.findViewById<AppCompatImageButton>(R.id.searchSwapButton).setOnClickListener { button ->
            button.animate().rotationBy(180f).setDuration(220L).start()
            originController?.swapWith(destinationController ?: return@setOnClickListener)
        }
        view.findViewById<MaterialButton>(R.id.searchQueryButton).setOnClickListener { query() }
        view.findViewById<MaterialButton>(R.id.searchEditButton).setOnClickListener {
            view.findViewById<NestedScrollView>(R.id.searchRoot).smoothScrollTo(0, originLayout.top)
            originInput.requestFocus()
        }
        saveButton.setOnClickListener { saveCurrentRoute() }
    }

    fun onDestinationSelected() {
        if (originController?.selectedPlace == null) {
            requestCurrentOrigin(isAuto = true)
        }
    }

    fun onDestinationHidden() {
        interactionGeneration.invalidate()
        routeQueryCoordinator.invalidate()
        routeQueryState.cancel()
        swipeRefresh.isRefreshing = false
        updateRefreshEnabled()
    }

    override fun onDestroyView() {
        interactionGeneration.invalidate()
        routeQueryCoordinator.invalidate()
        routeQueryState.cancel()
        originController?.dispose()
        destinationController?.dispose()
        originController = null
        destinationController = null
        detailSheet.dispose()
        etaSheet.dispose()
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        originController?.selectedPlace?.writeTo(outState, STATE_ORIGIN)
        destinationController?.selectedPlace?.writeTo(outState, STATE_DESTINATION)
        sortField?.let { outState.putString(STATE_SORT_FIELD, it.name) }
        outState.putString(STATE_SORT_DIRECTION, sortDirection.name)
    }

    override fun onDestroy() {
        searchExecutor.shutdownNow()
        queryExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun requestCurrentOrigin(isAuto: Boolean) {
        val generation = interactionGeneration.begin()
        val handleResult: (CurrentPlaceSelectionResult) -> Unit = { result ->
            mainHandler.post {
                if (!isViewActive() || !interactionGeneration.isCurrent(generation)) return@post
                when (result) {
                    is CurrentPlaceSelectionResult.Success -> {
                        originController?.setCurrentLocationSnapshot(result.snapshot)
                        destinationController?.setCurrentLocationSnapshot(result.snapshot)
                        originController?.setSelectedPlace(result.place)
                        originAttribution.visibility = if (result.attribution == PlaceAttribution.GOOGLE_MAPS) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                    CurrentPlaceSelectionResult.Failure -> {
                        if (isAuto) {
                            originController?.setHelperText("暫時無法取得目前位置，請手動選擇起點")
                        } else {
                            Toast.makeText(requireContext(), "暫時無法取得目前位置", Toast.LENGTH_SHORT).show()
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

    private fun query(preserveSort: Boolean = false) {
        val origin = originController?.selectedPlace
        val destination = destinationController?.selectedPlace
        val validation = RouteConfigValidator.validate("搜尋", origin, destination)
        originController?.setError(validation.originError)
        destinationController?.setError(validation.destinationError)
        if (!validation.isValid || origin == null || destination == null) return

        interactionGeneration.invalidate()
        val isRefresh = preserveSort && currentResults.isNotEmpty()
        routeQueryState.begin(refresh = isRefresh)
        if (!isRefresh) {
            resultAdapter.submitList(emptyList())
            resultList.visibility = View.GONE
            sortControls.visibility = View.GONE
            resultUpdatedAt.visibility = View.GONE
        }
        resultLoading.visibility = if (isRefresh) View.GONE else View.VISIBLE
        resultSummaryContainer.visibility = View.VISIBLE
        resultSummary.text = "${origin.name} → ${destination.name}"
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
                sortControls.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
                resultUpdatedAt.text = "更新時間：${formatUpdatedAt(routeQueryState.updatedAtMillis)}"
                resultUpdatedAt.visibility = View.VISIBLE
                updateSortControls()
                showStatus(if (currentResults.isEmpty()) "沒有找到可用路線" else null)
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
                routeQueryState.fail("搜尋失敗，請稍後重試", preserveResults = isRefresh)
                if (isRefresh) {
                    resultAdapter.submitList(currentResults)
                    resultList.visibility = View.VISIBLE
                    sortControls.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "刷新失敗，請稍後重試", Toast.LENGTH_SHORT).show()
                } else {
                    resultAdapter.submitList(emptyList())
                    resultList.visibility = View.GONE
                    sortControls.visibility = View.GONE
                    showStatus("搜尋失敗，請稍後重試")
                }
                updateRefreshEnabled()
            }
        })
    }

    private fun updateRoute(routeId: String, transform: (BusRouteOption) -> BusRouteOption) {
        if (!routeQueryState.update(routeId, transform)) return
        resultAdapter.submitList(currentResults)
        currentResults.firstOrNull { it.resultId == routeId }?.let(etaSheet::update)
    }

    private fun saveCurrentRoute() {
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
        SortField.ROUTE -> "路線"
        SortField.PRICE -> "價格"
        SortField.DURATION -> "耗時"
        SortField.ARRIVAL -> "候車"
        SortField.WALKING_DISTANCE -> "步行"
    }

    private fun showStatus(message: String?) {
        resultStatus.text = message.orEmpty()
        resultStatus.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    private fun updateRefreshEnabled() {
        if (!::swipeRefresh.isInitialized) return
        val candidatesVisible = originController?.isCandidateVisible() == true ||
            destinationController?.isCandidateVisible() == true
        swipeRefresh.isEnabled = currentResults.isNotEmpty() &&
            !candidatesVisible &&
            (!routeQueryState.isQueryInProgress || routeQueryState.isRefreshing)
    }

    private fun formatUpdatedAt(value: Long?): String {
        val timestamp = value ?: return "--:--"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    private fun isViewActive(): Boolean = isAdded && view != null && !isRemoving

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

        internal fun resetTestDependencies() {
            busRouteRepositoryFactory = { CitybusBusRouteRepository() }
            placeSearchRepositoryFactory = { CitybusPlaceSearchRepository() }
            routeDetailRepositoryFactory = { CitybusRouteDetailRepository() }
            currentPlaceRequestOverride = null
        }

        const val STATE_ORIGIN = "search_origin"
        const val STATE_DESTINATION = "search_destination"
        const val STATE_SORT_FIELD = "search_sort_field"
        const val STATE_SORT_DIRECTION = "search_sort_direction"
    }
}
