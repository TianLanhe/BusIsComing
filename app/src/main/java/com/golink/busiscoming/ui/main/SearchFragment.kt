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
import com.golink.busiscoming.data.repository.BusRouteQueryCallback
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.CitybusBusRouteRepository
import com.golink.busiscoming.data.repository.CitybusPlaceSearchRepository
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.data.repository.BusRouteSorter
import com.golink.busiscoming.ui.common.PlaceInputController
import com.golink.busiscoming.ui.navigation.RouteQueryGeneration
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SearchFragment : Fragment() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val queryExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val busRouteRepository: BusRouteRepository = CitybusBusRouteRepository()

    private val queryGeneration = RouteQueryGeneration()
    private var originController: PlaceInputController? = null
    private var destinationController: PlaceInputController? = null
    private var currentResults: List<BusRouteOption> = emptyList()
    private var sortField: SortField? = null
    private var sortDirection = SortDirection.ASC
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
    private lateinit var resultSummary: TextView
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
        sortField = savedInstanceState
            ?.getString(STATE_SORT_FIELD)
            ?.let { runCatching { SortField.valueOf(it) }.getOrNull() }
        sortDirection = savedInstanceState
            ?.getString(STATE_SORT_DIRECTION)
            ?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() }
            ?: SortDirection.ASC
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
            placeSearchRepository = CitybusPlaceSearchRepository(),
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = ::isViewActive,
            onCandidateVisibilityChanged = { visible ->
                if (visible) destinationController?.hideCandidates()
            },
            onUserTextEdited = { originAttribution.visibility = View.GONE }
        )
        destinationController = PlaceInputController(
            context = context,
            input = destinationInput,
            inputLayout = destinationLayout,
            loadingView = view.findViewById(R.id.searchDestinationLoading),
            candidateList = view.findViewById(R.id.searchDestinationCandidateList),
            placeSearchRepository = CitybusPlaceSearchRepository(),
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = ::isViewActive,
            onCandidateVisibilityChanged = { visible ->
                if (visible) originController?.hideCandidates()
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
        resultSummary = view.findViewById(R.id.searchResultSummary)
        saveButton = view.findViewById(R.id.searchSaveButton)
        detailSheet = RouteDetailBottomSheet(requireActivity() as androidx.appcompat.app.AppCompatActivity, CitybusRouteDetailRepository())
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
        swipeRefresh.setColorSchemeResources(R.color.bus_chip_selected)
        swipeRefresh.setOnRefreshListener { query(preserveSort = true) }
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
        saveButton.setOnClickListener { saveCurrentRoute() }
    }

    fun onDestinationSelected() {
        if (originController?.selectedPlace == null) {
            requestCurrentOrigin(isAuto = true)
        }
    }

    fun onDestinationHidden() {
        queryGeneration.invalidate()
        busRouteRepository.cancelProgressiveQueries()
        swipeRefresh.isRefreshing = false
    }

    override fun onDestroyView() {
        queryGeneration.invalidate()
        busRouteRepository.cancelProgressiveQueries()
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
        val generation = queryGeneration.begin()
        (activity as? MainActivity)?.requestCurrentPlace(isAuto) { result ->
            mainHandler.post {
                if (!isViewActive() || !queryGeneration.isCurrent(generation)) return@post
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
    }

    private fun query(preserveSort: Boolean = false) {
        val origin = originController?.selectedPlace
        val destination = destinationController?.selectedPlace
        val validation = RouteConfigValidator.validate("搜尋", origin, destination)
        originController?.setError(validation.originError)
        destinationController?.setError(validation.destinationError)
        if (!validation.isValid || origin == null || destination == null) return

        val generation = queryGeneration.begin()
        currentResults = emptyList()
        if (!preserveSort) {
            sortField = null
            sortDirection = SortDirection.ASC
            updateSortControls()
        }
        resultAdapter.submitList(emptyList())
        swipeRefresh.visibility = View.GONE
        resultLoading.visibility = View.VISIBLE
        resultSummary.visibility = View.VISIBLE
        resultSummary.text = "${origin.name} → ${destination.name}"
        saveButton.visibility = View.VISIBLE
        showStatus(null)
        busRouteRepository.cancelProgressiveQueries()
        queryExecutor.execute {
            busRouteRepository.searchRoutesProgressively(origin, destination, object : BusRouteQueryCallback {
                override fun onInitialRoutes(routes: List<BusRouteOption>) {
                    mainHandler.post {
                        if (!isViewActive() || !queryGeneration.isCurrent(generation)) return@post
                        resultLoading.visibility = View.GONE
                        swipeRefresh.isRefreshing = false
                        currentResults = sortField?.let { field ->
                            BusRouteSorter.sort(routes, field, sortDirection)
                        } ?: routes
                        resultAdapter.submitList(currentResults)
                        swipeRefresh.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
                        sortControls.visibility = if (currentResults.isEmpty()) View.GONE else View.VISIBLE
                        updateSortControls()
                        showStatus(if (currentResults.isEmpty()) "沒有找到可用路線" else null)
                    }
                }

                override fun onRouteWaitTimeUpdated(routeId: String, waitTimeState: WaitTimeState) {
                    updateRoute(routeId) { it.copy(waitTimeState = waitTimeState) }
                }

                override fun onRouteStopPreviewUpdated(routeId: String, preview: RouteCardStopPreview) {
                    updateRoute(routeId) { it.copy(stopPreview = preview) }
                }

                override fun onFailure(error: Throwable) {
                    mainHandler.post {
                        if (!isViewActive() || !queryGeneration.isCurrent(generation)) return@post
                        resultLoading.visibility = View.GONE
                        swipeRefresh.isRefreshing = false
                        currentResults = emptyList()
                        resultAdapter.submitList(emptyList())
                        swipeRefresh.visibility = View.GONE
                        sortControls.visibility = View.GONE
                        showStatus("搜尋失敗，請稍後重試")
                    }
                }
            })
        }
    }

    private fun updateRoute(routeId: String, transform: (BusRouteOption) -> BusRouteOption) {
        mainHandler.post {
            if (!isViewActive()) return@post
            currentResults = currentResults.map { route ->
                if (route.resultId == routeId) transform(route) else route
            }
            if (sortField == SortField.ARRIVAL) {
                currentResults = BusRouteSorter.sort(currentResults, SortField.ARRIVAL, sortDirection)
            }
            resultAdapter.submitList(currentResults)
            currentResults.firstOrNull { it.resultId == routeId }?.let(etaSheet::update)
        }
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
        sortDirection = if (sortField == field && sortDirection == SortDirection.ASC) {
            SortDirection.DESC
        } else {
            SortDirection.ASC
        }
        sortField = field
        currentResults = BusRouteSorter.sort(currentResults, field, sortDirection)
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

    private companion object {
        const val STATE_ORIGIN = "search_origin"
        const val STATE_DESTINATION = "search_destination"
        const val STATE_SORT_FIELD = "search_sort_field"
        const val STATE_SORT_DIRECTION = "search_sort_direction"
    }
}
