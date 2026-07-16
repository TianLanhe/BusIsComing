package com.golink.busiscoming.ui.main

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.res.ColorStateList
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Gravity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.graphics.Typeface
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.golink.busiscoming.R
import com.golink.busiscoming.data.location.CurrentLocationCoordinator
import com.golink.busiscoming.data.location.CurrentLocationResult
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.location.CurrentPlaceSelectionResult
import com.golink.busiscoming.data.location.GoogleReverseGeocodingPlaceNameResolver
import com.golink.busiscoming.data.location.LocationPermissionStateStore
import com.golink.busiscoming.data.location.LocationPermissionUtils
import com.golink.busiscoming.data.location.NearbyRouteSelectionPolicy
import com.golink.busiscoming.data.location.PlaceNameResolutionResult
import com.golink.busiscoming.data.location.PlaceNameResolver
import com.golink.busiscoming.data.location.SavedRouteLocationSorter
import com.golink.busiscoming.data.location.SystemLocationUtils
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.WalkingTimeCalculator
import com.golink.busiscoming.data.repository.BusRouteQueryCallback
import com.golink.busiscoming.data.repository.BusRouteRepository
import com.golink.busiscoming.data.repository.BusRouteSorter
import com.golink.busiscoming.data.repository.CitybusBusRouteRepository
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.service.BusMonitorService
import com.golink.busiscoming.service.BusMonitorSchedulingCapability
import com.golink.busiscoming.service.BusMonitorSessionStore
import com.golink.busiscoming.data.model.BusMonitorSessionPolicy
import com.golink.busiscoming.ui.common.applyStableShortTextLayout
import com.golink.busiscoming.ui.common.applyStatusBarPadding
import com.golink.busiscoming.ui.edit.RouteEditActivity
import com.golink.busiscoming.ui.manage.RouteManageActivity
import com.golink.busiscoming.ui.navigation.TopLevelDestination
import com.golink.busiscoming.ui.navigation.TopLevelDestinationState
import com.golink.busiscoming.ui.navigation.RouteQueryGeneration
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var routeConfigRepository: RouteConfigRepository
    private lateinit var currentLocationCoordinator: CurrentLocationCoordinator
    private lateinit var locationPermissionStateStore: LocationPermissionStateStore
    private lateinit var placeNameResolver: PlaceNameResolver
    private val busRouteRepository: BusRouteRepository = CitybusBusRouteRepository()
    private val routeDetailRepository: RouteDetailRepository = CitybusRouteDetailRepository()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queryExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var queryButton: MaterialButton
    private lateinit var normalTopActions: LinearLayout
    private lateinit var firstRunTopActions: LinearLayout
    private lateinit var transitCodeButton: MaterialButton
    private lateinit var firstRunTransitCodeButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var firstRunSettingsButton: MaterialButton
    private lateinit var emptySearchButton: MaterialButton
    private lateinit var emptyRouteState: LinearLayout
    private lateinit var firstRunHeadlineText: TextView
    private lateinit var firstRunSampleLabelText: TextView
    private lateinit var firstRunSampleRouteCard: View
    private lateinit var firstRunActionGroup: LinearLayout
    private lateinit var queryControls: LinearLayout
    private lateinit var routeShortcutCardsContainer: LinearLayout
    private lateinit var routePickerButton: MaterialButton
    private lateinit var routeManageButton: MaterialButton
    private lateinit var resultSection: LinearLayout
    private lateinit var sortControls: LinearLayout
    private lateinit var resultSummaryContainer: LinearLayout
    private lateinit var resultSummaryText: TextView
    private lateinit var resultUpdatedAtText: TextView
    private lateinit var resultStatusCard: MaterialCardView
    private lateinit var resultStatusProgress: ProgressBar
    private lateinit var resultStatusTitle: TextView
    private lateinit var resultStatusMessage: TextView
    private lateinit var resultListContainer: View
    private lateinit var resultSwipeRefresh: SwipeRefreshLayout
    private lateinit var resultList: RecyclerView
    private lateinit var resultRefreshOverlay: MaterialCardView
    private lateinit var resultRefreshProgress: ProgressBar
    private lateinit var resultRefreshSuccess: ImageView
    private lateinit var sortButtons: Map<SortField, MaterialButton>
    private lateinit var busRouteAdapter: BusRouteAdapter
    private lateinit var routeDetailBottomSheet: RouteDetailBottomSheet
    private lateinit var etaArrivalsBottomSheet: EtaArrivalsBottomSheet
    private lateinit var monitorSettingsBottomSheet: MonitorSettingsBottomSheet
    private lateinit var transitCodePaymentLauncher: TransitCodePaymentLaunchAction
    private lateinit var topLevelNav: BottomNavigationView
    private val destinationState = TopLevelDestinationState()

    private var routeConfigs: List<RouteConfig> = emptyList()
    private var selectedRoute: RouteConfig? = null
    private var currentResults: List<BusRouteOption> = emptyList()
    private var sortField: SortField? = null
    private var sortDirection: SortDirection = SortDirection.ASC
    private val routeQueryGeneration = RouteQueryGeneration()
    private var currentQueryContext: QueryContext? = null
    private var isQueryInProgress: Boolean = false
    private var preserveSortOnNextResults: Boolean = false
    private var pendingMonitorStart: PendingMonitorStart? = null
    private val refreshFeedbackState = RouteRefreshFeedbackState()
    private var refreshFinishRunnable: Runnable? = null
    private var refreshViewport: RefreshViewport? = null
    private var resultListBasePadding: ViewPadding? = null
    private var hasAttemptedNearbyRouteSelection: Boolean = false
    private var nearbySelectedRouteId: Long? = null
    private var manualRouteSelectionGeneration: Int = 0
    private var currentLocationSnapshot: CurrentLocationSnapshot? = null
    private var savedRouteUsageSession = SavedRouteUsageSession()
    private val shownLocationFallbackToasts = mutableSetOf<LocationFallbackToast>()
    private var pendingLocationPermissionAction: PendingLocationPermissionAction? = null
    private var pendingLocationSettingsCurrentPlaceCallback: ((CurrentPlaceSelectionResult) -> Unit)? = null
    private var hasPlayedFirstRunIntroAnimation: Boolean = false
    private var frequentRoutesInitialized: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreSavedRouteUsageSession(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "BusIsComing"
        findViewById<View>(R.id.mainRoot).applyStatusBarPadding()

        routeConfigRepository = RouteConfigRepository(this)
        currentLocationCoordinator = CurrentLocationCoordinator(this)
        locationPermissionStateStore = LocationPermissionStateStore(this)
        placeNameResolver = GoogleReverseGeocodingPlaceNameResolver(this)
        clearExpiredMonitorSession()
        routeDetailBottomSheet = RouteDetailBottomSheet(this, routeDetailRepository)
        etaArrivalsBottomSheet = EtaArrivalsBottomSheet(this)
        monitorSettingsBottomSheet = MonitorSettingsBottomSheet(
            context = this,
            onStart = { result ->
                pendingMonitorStart?.copy(
                    walkingMinutes = result.walkingMinutes,
                    voiceEnabled = result.voiceEnabled
                )?.let { startMonitor(it) }
            }
        )
        transitCodePaymentLauncher = TransitCodePaymentLauncher.forActivity(this)
        installTopLevelFragments(savedInstanceState)
        setupTopLevelNavigation(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val selectedRouteId = selectedRoute?.id ?: savedRouteUsageSession.selectedRouteId
        selectedRouteId?.let { outState.putLong(STATE_SELECTED_ROUTE_ID, it) }
        savedRouteUsageSession.recordedRouteId
            ?.takeIf { it == selectedRouteId }
            ?.let { outState.putLong(STATE_RECORDED_USAGE_ROUTE_ID, it) }
        outState.putString(STATE_SELECTED_DESTINATION, destinationState.selected.name)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        invalidateActiveQuery()
        mainHandler.removeCallbacksAndMessages(null)
        routeDetailBottomSheet.dispose()
        etaArrivalsBottomSheet.dispose()
        monitorSettingsBottomSheet.dispose()
        queryExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (frequentRoutesInitialized) {
            loadRouteConfigs()
        }
        retryCurrentPlaceAfterLocationSettings()
    }

    fun onFrequentRoutesViewReady() {
        if (frequentRoutesInitialized) return
        bindViews()
        setupResultList()
        setupActions()
        frequentRoutesInitialized = true
        loadRouteConfigs()
    }

    private fun bindViews() {
        val root = requireTopLevelFragment(TAG_FREQUENT_ROUTES).requireView()
        queryButton = root.findViewById(R.id.queryButton)
        normalTopActions = root.findViewById(R.id.normalTopActions)
        firstRunTopActions = root.findViewById(R.id.firstRunTopActions)
        transitCodeButton = root.findViewById(R.id.transitCodeButton)
        firstRunTransitCodeButton = root.findViewById(R.id.firstRunTransitCodeButton)
        settingsButton = root.findViewById(R.id.settingsButton)
        firstRunSettingsButton = root.findViewById(R.id.firstRunSettingsButton)
        emptySearchButton = root.findViewById(R.id.emptySearchButton)
        emptyRouteState = root.findViewById(R.id.emptyRouteState)
        firstRunHeadlineText = root.findViewById(R.id.firstRunHeadlineText)
        firstRunSampleLabelText = root.findViewById(R.id.firstRunSampleLabelText)
        firstRunSampleRouteCard = root.findViewById(R.id.firstRunSampleRouteCard)
        firstRunActionGroup = root.findViewById(R.id.firstRunActionGroup)
        queryControls = root.findViewById(R.id.queryControls)
        routeShortcutCardsContainer = root.findViewById(R.id.routeShortcutCardsContainer)
        routePickerButton = root.findViewById(R.id.routePickerButton)
        routeManageButton = root.findViewById(R.id.routeManageButton)
        resultSection = root.findViewById(R.id.resultSection)
        sortControls = root.findViewById(R.id.sortControls)
        resultSummaryContainer = root.findViewById(R.id.resultSummaryContainer)
        resultSummaryText = root.findViewById(R.id.resultSummaryText)
        resultUpdatedAtText = root.findViewById(R.id.resultUpdatedAtText)
        resultStatusCard = root.findViewById(R.id.resultStatusCard)
        resultStatusProgress = root.findViewById(R.id.resultStatusProgress)
        resultStatusTitle = root.findViewById(R.id.resultStatusTitle)
        resultStatusMessage = root.findViewById(R.id.resultStatusMessage)
        resultListContainer = root.findViewById(R.id.resultListContainer)
        resultSwipeRefresh = root.findViewById(R.id.resultSwipeRefresh)
        resultList = root.findViewById(R.id.busRouteList)
        resultRefreshOverlay = root.findViewById(R.id.resultRefreshOverlay)
        resultRefreshProgress = root.findViewById(R.id.resultRefreshProgress)
        resultRefreshSuccess = root.findViewById(R.id.resultRefreshSuccess)
        resultListBasePadding = ViewPadding(
            left = resultList.paddingLeft,
            top = resultList.paddingTop,
            right = resultList.paddingRight,
            bottom = resultList.paddingBottom
        )
        sortButtons = mapOf(
            SortField.ROUTE to root.findViewById(R.id.sortRouteButton),
            SortField.PRICE to root.findViewById(R.id.sortPriceButton),
            SortField.DURATION to root.findViewById(R.id.sortDurationButton),
            SortField.ARRIVAL to root.findViewById(R.id.sortArrivalButton),
            SortField.WALKING_DISTANCE to root.findViewById(R.id.sortWalkingDistanceButton)
        )
        BusRouteCardBinder(firstRunSampleRouteCard).bind(FirstRunRoutePreview.route())
    }

    private fun clearExpiredMonitorSession() {
        val store = BusMonitorSessionStore(this)
        val snapshot = store.load() ?: return
        if (BusMonitorSessionPolicy.shouldClearOnRestore(System.currentTimeMillis(), snapshot) ||
            !isMonitorNotificationActive()
        ) {
            store.clear()
        }
    }

    private fun isMonitorNotificationActive(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        return manager.activeNotifications.any { notification ->
            notification.id == BusMonitorService.NOTIFICATION_ID
        }
    }

    private fun setupResultList() {
        busRouteAdapter = BusRouteAdapter(
            onRouteClick = ::showRouteDetail,
            onEtaClick = ::showEtaArrivals,
            onMonitorClick = ::showMonitorSettings
        )
        resultList.layoutManager = LinearLayoutManager(this)
        resultList.adapter = busRouteAdapter
        resultSwipeRefresh.setColorSchemeResources(R.color.bus_chip_selected)
        renderRefreshFeedback()
        updateSwipeRefreshState()
    }

    private fun setupActions() {
        settingsButton.visibility = View.GONE
        firstRunSettingsButton.visibility = View.GONE
        routeManageButton.setOnClickListener {
            startActivity(Intent(this, RouteManageActivity::class.java))
        }
        transitCodeButton.setOnClickListener { launchTransitCode() }
        firstRunTransitCodeButton.setOnClickListener { launchTransitCode() }
        requireTopLevelFragment(TAG_FREQUENT_ROUTES).requireView()
            .findViewById<MaterialButton>(R.id.emptyAddRouteButton).setOnClickListener {
            startActivity(Intent(this, RouteEditActivity::class.java))
        }
        emptySearchButton.setOnClickListener {
            topLevelNav.selectedItemId = R.id.navigation_search
        }
        routePickerButton.setOnClickListener { showRoutePicker() }
        queryButton.setOnClickListener { querySelectedRoute() }
        resultSwipeRefresh.setOnRefreshListener { refreshCurrentResults() }
        sortButtons.forEach { (field, button) ->
            button.setOnClickListener {
                sortBy(field)
                pulse(button)
            }
        }
    }

    private fun installTopLevelFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val frequentRoutes = FrequentRoutesFragment()
        val search = SearchFragment()
        val settings = SettingsFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.topLevelFragmentContainer, frequentRoutes, TAG_FREQUENT_ROUTES)
            .add(R.id.topLevelFragmentContainer, search, TAG_SEARCH)
            .hide(search)
            .add(R.id.topLevelFragmentContainer, settings, TAG_SETTINGS)
            .hide(settings)
            .commitNow()
    }

    private fun setupTopLevelNavigation(savedInstanceState: Bundle?) {
        topLevelNav = findViewById(R.id.topLevelNav)
        val restored = savedInstanceState
            ?.getString(STATE_SELECTED_DESTINATION)
            ?.let { runCatching { TopLevelDestination.valueOf(it) }.getOrNull() }
            ?: TopLevelDestination.FREQUENT_ROUTES
        topLevelNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_frequent_routes -> selectDestination(TopLevelDestination.FREQUENT_ROUTES)
                R.id.navigation_search -> selectDestination(TopLevelDestination.SEARCH)
                R.id.navigation_settings -> selectDestination(TopLevelDestination.SETTINGS)
                else -> false
            }
        }
        topLevelNav.selectedItemId = restored.menuItemId()
    }

    private fun selectDestination(destination: TopLevelDestination): Boolean {
        if (destinationState.selected == destination) return true
        val current = requireTopLevelFragment(destinationState.selected.tag)
        when (current) {
            is FrequentRoutesFragment -> invalidateActiveQuery()
            is SearchFragment -> current.onDestinationHidden()
        }
        val next = requireTopLevelFragment(destination.tag)
        supportFragmentManager.beginTransaction()
            .hide(current)
            .show(next)
            .commit()
        destinationState.select(destination)
        (next as? SearchFragment)?.onDestinationSelected()
        return true
    }

    private fun requireTopLevelFragment(tag: String): Fragment {
        return requireNotNull(supportFragmentManager.findFragmentByTag(tag))
    }

    private fun TopLevelDestination.menuItemId(): Int = when (this) {
        TopLevelDestination.FREQUENT_ROUTES -> R.id.navigation_frequent_routes
        TopLevelDestination.SEARCH -> R.id.navigation_search
        TopLevelDestination.SETTINGS -> R.id.navigation_settings
    }

    private val TopLevelDestination.tag: String
        get() = when (this) {
            TopLevelDestination.FREQUENT_ROUTES -> TAG_FREQUENT_ROUTES
            TopLevelDestination.SEARCH -> TAG_SEARCH
            TopLevelDestination.SETTINGS -> TAG_SETTINGS
        }

    private fun restoreSavedRouteUsageSession(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        val selectedRouteId = savedInstanceState.longOrNull(STATE_SELECTED_ROUTE_ID)
        val recordedRouteId = savedInstanceState.longOrNull(STATE_RECORDED_USAGE_ROUTE_ID)
        savedRouteUsageSession = SavedRouteUsageSession(
            selectedRouteId = selectedRouteId,
            recordedRouteId = recordedRouteId
        )
    }

    private fun Bundle.longOrNull(key: String): Long? {
        return if (containsKey(key)) getLong(key) else null
    }

    private fun loadRankedRouteConfigs(): List<RouteConfig> {
        return SavedRouteLocationSorter.sort(
            routes = routeConfigRepository.getAll(),
            location = currentLocationSnapshot
        )
    }

    private fun loadRouteConfigs() {
        val previousSelectedId = selectedRoute?.id ?: savedRouteUsageSession.selectedRouteId
        val previousRouteSnapshot = routeIdentitySnapshot(routeConfigs)
        routeConfigs = loadRankedRouteConfigs()

        if (routeConfigs.isEmpty()) {
            if (currentQueryContext is QueryContext.Saved) {
                clearResults()
            }
            selectedRoute = null
            routeShortcutCardsContainer.removeAllViews()
            routePickerButton.visibility = View.GONE
            renderHomeShell()
            updateSwipeRefreshState()
            return
        }

        selectedRoute = routeConfigs.firstOrNull { it.id == previousSelectedId } ?: routeConfigs.first()
        savedRouteUsageSession.selectSavedRoute(selectedRoute!!.id)
        renderRouteShortcuts()
        renderHomeShell()
        maybeStartNearbyRouteSelection()
        if (previousRouteSnapshot != routeIdentitySnapshot(routeConfigs)) {
            clearResults()
        }
    }

    private fun maybeStartNearbyRouteSelection() {
        if (hasAttemptedNearbyRouteSelection || routeConfigs.size < 2) return
        hasAttemptedNearbyRouteSelection = true
        val generation = manualRouteSelectionGeneration
        if (LocationPermissionUtils.hasForegroundLocationPermission(this)) {
            selectNearbyRouteWhenLocationAvailable(generation)
            return
        }
        if (locationPermissionStateStore.isAutoRequestDenied()) {
            showLocationFallbackToast(LocationFallbackToast.PERMISSION_DENIED)
            return
        }
        pendingLocationPermissionAction = PendingLocationPermissionAction.NearbyRoute(generation)
        ActivityCompat.requestPermissions(
            this,
            LocationPermissionUtils.permissions,
            REQUEST_LOCATION_PERMISSION
        )
    }

    private fun selectNearbyRouteWhenLocationAvailable(generation: Int) {
        if (!SystemLocationUtils.isLocationEnabled(this)) {
            showLocationFallbackToast(LocationFallbackToast.UNAVAILABLE)
            return
        }
        currentLocationCoordinator.getCurrentLocation { result ->
            if (isFinishing || isDestroyed) return@getCurrentLocation
            when (result) {
                is CurrentLocationResult.Success -> {
                    val canAutoSelect = manualRouteSelectionGeneration == generation
                    currentLocationSnapshot = result.snapshot
                    val selectedRouteId = selectedRoute?.id
                    routeConfigs = loadRankedRouteConfigs()
                    selectedRoute = routeConfigs.firstOrNull { it.id == selectedRouteId } ?: selectedRoute
                    if (!canAutoSelect) {
                        renderRouteShortcuts()
                        return@getCurrentLocation
                    }
                    val route = NearbyRouteSelectionPolicy.selectRoute(result.snapshot, routeConfigs)
                    if (route == null) {
                        renderRouteShortcuts()
                        showLocationFallbackToast(LocationFallbackToast.IMPRECISE)
                        return@getCurrentLocation
                    }
                    selectedRoute = routeConfigs.firstOrNull { it.id == route.id } ?: route
                    savedRouteUsageSession.selectSavedRoute(route.id)
                    nearbySelectedRouteId = selectedRoute?.id
                    renderRouteShortcuts()
                }
                CurrentLocationResult.NoPermission -> showLocationFallbackToast(LocationFallbackToast.PERMISSION_DENIED)
                CurrentLocationResult.Timeout,
                CurrentLocationResult.Unavailable -> showLocationFallbackToast(LocationFallbackToast.UNAVAILABLE)
            }
        }
    }

    private fun querySelectedRoute() {
        val route = selectedRoute
        if (route == null) {
            Toast.makeText(this, "請先選擇路線或查詢臨時起點和終點", Toast.LENGTH_SHORT).show()
            return
        }
        queryRoute(route.origin, route.destination, route, QueryContext.Saved(route.id))
    }

    private fun refreshCurrentResults() {
        val context = currentQueryContext
        if (context == null || currentResults.isEmpty()) {
            resultSwipeRefresh.isRefreshing = false
            updateSwipeRefreshState()
            return
        }

        when (context) {
            is QueryContext.Saved -> {
                val route = routeConfigRepository.getById(context.routeId)
                if (route == null) {
                    resultSwipeRefresh.isRefreshing = false
                    Toast.makeText(this, "路線已不存在", Toast.LENGTH_SHORT).show()
                    clearResults()
                    return
                }
                queryRoute(
                    origin = route.origin,
                    destination = route.destination,
                    sourceRoute = route,
                    queryContext = QueryContext.Saved(route.id),
                    recordUsage = false,
                    preserveSort = true,
                    isRefresh = true
                )
            }
        }
    }

    private fun queryRoute(
        origin: Place,
        destination: Place,
        sourceRoute: RouteConfig?,
        queryContext: QueryContext,
        recordUsage: Boolean = true,
        preserveSort: Boolean = false,
        isRefresh: Boolean = false
    ) {
        if (isQueryInProgress) {
            resultSwipeRefresh.isRefreshing = false
            return
        }

        val shouldRecordRouteUsage = sourceRoute?.let { route ->
            RouteResultsRefreshPolicy.shouldRecordUsage(isRefresh, recordUsage) &&
                savedRouteUsageSession.consumeUsageRecord(route.id)
        } ?: false
        if (shouldRecordRouteUsage) {
            val route = sourceRoute ?: return
            routeConfigRepository.recordUsage(route.id)
            routeConfigs = loadRankedRouteConfigs()
            selectedRoute = routeConfigs.firstOrNull { it.id == route.id } ?: route
            renderRouteShortcuts()
        }

        val queryId = routeQueryGeneration.begin()
        currentQueryContext = queryContext
        preserveSortOnNextResults = preserveSort
        renderHomeShell()
        busRouteRepository.cancelProgressiveQueries()
        if (isRefresh) {
            showRefreshLoadingState(queryId)
        } else {
            showLoadingState()
        }
        queryExecutor.execute {
            busRouteRepository.searchRoutesProgressively(
                origin,
                destination,
                object : BusRouteQueryCallback {
                    override fun onInitialRoutes(routes: List<BusRouteOption>) {
                        mainHandler.post {
                            if (!routeQueryGeneration.isCurrent(queryId) || isFinishing || isDestroyed) return@post
                            if (isRefresh) {
                                handleRefreshSuccess(queryId, routes)
                            } else {
                                finishQueryLoading()
                                showInitialRoutes(routes)
                            }
                        }
                    }

                    override fun onRouteWaitTimeUpdated(routeId: String, waitTimeState: WaitTimeState) {
                        mainHandler.post {
                            if (!routeQueryGeneration.isCurrent(queryId) || isFinishing || isDestroyed) return@post
                            updateRouteWaitTime(routeId, waitTimeState)
                        }
                    }

                    override fun onRouteStopPreviewUpdated(routeId: String, preview: RouteCardStopPreview) {
                        mainHandler.post {
                            if (!routeQueryGeneration.isCurrent(queryId) || isFinishing || isDestroyed) return@post
                            updateRouteStopPreview(routeId, preview)
                        }
                    }

                    override fun onFailure(error: Throwable) {
                        Log.e(LOG_TAG, "Bus route query failed", error)
                        mainHandler.post {
                            if (!routeQueryGeneration.isCurrent(queryId) || isFinishing || isDestroyed) return@post
                            if (isRefresh) {
                                handleRefreshFailure(queryId)
                            } else {
                                finishQueryLoading()
                                currentResults = emptyList()
                                sortField = null
                                sortDirection = SortDirection.ASC
                                updateSortControls()
                                displayFailure()
                            }
                        }
                    }
                }
            )
        }
    }

    private fun showInitialRoutes(routes: List<BusRouteOption>) {
        val nextSortField = RouteResultsRefreshPolicy.resolveSortField(
            preserveSort = preserveSortOnNextResults,
            currentSortField = sortField
        )
        if (RouteResultsRefreshPolicy.shouldResetSortDirection(preserveSortOnNextResults, sortField)) {
            sortDirection = SortDirection.ASC
        }
        sortField = nextSortField
        preserveSortOnNextResults = false
        currentResults = BusRouteSorter.sort(routes, nextSortField, sortDirection)
        updateSortControls()
        updateResultSummary(routes)
        displayResults(currentResults)
    }

    private fun handleRefreshSuccess(queryId: Int, routes: List<BusRouteOption>) {
        val result = if (routes.isEmpty()) RouteRefreshResult.EMPTY else RouteRefreshResult.NON_EMPTY
        if (!refreshFeedbackState.succeed(queryId, result)) return

        if (routes.isNotEmpty()) {
            showInitialRoutes(routes)
            resultList.scrollToPosition(0)
        }
        renderRefreshFeedback()
        scheduleRefreshSuccessFinish(queryId)
    }

    private fun scheduleRefreshSuccessFinish(queryId: Int) {
        refreshFinishRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable { finishRefreshSuccess(queryId) }
        refreshFinishRunnable = runnable
        mainHandler.postDelayed(runnable, REFRESH_SUCCESS_DURATION_MS)
    }

    private fun finishRefreshSuccess(queryId: Int) {
        val action = refreshFeedbackState.finishSuccess(queryId) ?: return
        refreshFinishRunnable = null
        if (action == RouteRefreshFinishAction.SHOW_EMPTY_RESULTS) {
            showInitialRoutes(emptyList())
        }
        refreshViewport = null
        renderRefreshFeedback()
        finishQueryLoading()
    }

    private fun handleRefreshFailure(queryId: Int) {
        if (!refreshFeedbackState.fail(queryId)) return
        refreshFinishRunnable?.let(mainHandler::removeCallbacks)
        refreshFinishRunnable = null
        renderRefreshFeedback()
        finishQueryLoading()
        restoreRefreshViewport()
        Toast.makeText(this, "刷新失敗，請稍後重試", Toast.LENGTH_SHORT).show()
    }

    private fun updateRouteWaitTime(routeId: String, waitTimeState: WaitTimeState) {
        var changed = false
        currentResults = currentResults.map { route ->
            if (route.resultId == routeId) {
                changed = true
                route.copy(waitTimeState = waitTimeState)
            } else {
                route
            }
        }
        if (!changed) return

        if (sortField == SortField.ARRIVAL) {
            currentResults = BusRouteSorter.sort(currentResults, SortField.ARRIVAL, sortDirection)
        }
        currentResults.firstOrNull { it.resultId == routeId }?.let { etaArrivalsBottomSheet.update(it) }
        displayResults(currentResults)
    }

    private fun updateRouteStopPreview(routeId: String, preview: RouteCardStopPreview) {
        var changed = false
        currentResults = currentResults.map { route ->
            if (route.resultId == routeId) {
                changed = true
                route.copy(stopPreview = preview)
            } else {
                route
            }
        }
        if (!changed) return
        displayResults(currentResults)
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
        updateSortControls()
        displayResults(currentResults)
    }

    private fun displayResults(results: List<BusRouteOption>) {
        if (results.isEmpty()) {
            busRouteAdapter.submitList(emptyList())
            sortControls.visibility = View.GONE
            hideResultSummary()
            resultListContainer.visibility = View.GONE
            showStatus(
                title = "暫無可用巴士路線",
                message = "可以換一條常用路線，或稍後再試。",
                showProgress = false
            )
        } else {
            hideStatus()
            sortControls.visibility = View.VISIBLE
            resultSummaryContainer.visibility = View.VISIBLE
            val shouldAnimate = resultListContainer.visibility != View.VISIBLE
            resultListContainer.visibility = View.VISIBLE
            busRouteAdapter.submitList(results)
            if (shouldAnimate) {
                animateIn(resultListContainer)
            }
        }
        updateSwipeRefreshState()
    }

    private fun showRouteDetail(route: BusRouteOption) {
        routeDetailBottomSheet.show(route)
    }

    private fun showEtaArrivals(route: BusRouteOption) {
        etaArrivalsBottomSheet.show(route)
    }

    private fun showMonitorSettings(route: BusRouteOption) {
        showMonitorSettings(route, currentOriginPlace())
    }

    fun showMonitorSettings(route: BusRouteOption, origin: Place?) {
        if (route.firstLegEtaQuery == null || route.waitTimeState !is WaitTimeState.Available) {
            Toast.makeText(this, "此路線暫時無法監控", Toast.LENGTH_SHORT).show()
            return
        }
        if (origin == null) {
            Toast.makeText(this, "缺少起點資訊，無法估算步行時間", Toast.LENGTH_SHORT).show()
            return
        }

        pendingMonitorStart = PendingMonitorStart(route = route)
        queryExecutor.execute {
            val detail = runCatching { routeDetailRepository.loadRouteDetail(route) }.getOrNull()
            val boardingStop = detail?.legs?.firstOrNull()?.boardingStop
            val straightLineDistanceMeters = boardingStop?.let { stop ->
                WalkingTimeCalculator.straightLineDistanceMeters(
                    from = origin,
                    toLatitude = stop.latitude,
                    toLongitude = stop.longitude
                )
            }
            val interfaceDistanceMeters = detail?.originWalkingDistanceMeters
                ?: route.walkingDistanceMeters.takeIf { it > 0 }

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                monitorSettingsBottomSheet.show(
                    route = route,
                    inputs = MonitorWalkingInputs(
                        interfaceDistanceMeters = interfaceDistanceMeters,
                        straightLineDistanceMeters = straightLineDistanceMeters
                    )
                )
            }
        }
    }

    private fun currentOriginPlace(): Place? {
        return when (val context = currentQueryContext) {
            is QueryContext.Saved -> routeConfigRepository.getById(context.routeId)?.origin
            null -> selectedRoute?.origin
        }
    }

    private fun startMonitor(start: PendingMonitorStart) {
        val walkingMinutes = start.walkingMinutes ?: return
        if (requiresNotificationPermission()) {
            pendingMonitorStart = start
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
            return
        }

        promptHighPriorityMonitorSettingsIfNeeded()
        ContextCompat.startForegroundService(
            this,
            BusMonitorService.startIntent(
                context = this,
                route = start.route,
                walkingMinutes = walkingMinutes,
                voiceEnabled = start.voiceEnabled
            )
        )
        pendingMonitorStart = null
        Toast.makeText(this, "已開始通知欄監控", Toast.LENGTH_SHORT).show()
    }

    private fun promptHighPriorityMonitorSettingsIfNeeded() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (!BusMonitorSchedulingCapability.canScheduleExactAlarms(alarmManager)) {
            val intent = BusMonitorSchedulingCapability.exactAlarmSettingsIntent(this)
            if (intent != null && intent.resolveActivity(packageManager) != null) {
                Toast.makeText(this, "可開啟鬧鐘與提醒，提升候車監控準時性", Toast.LENGTH_LONG).show()
                startActivity(intent)
            }
        }
    }

    private fun requiresNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_POST_NOTIFICATIONS -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    pendingMonitorStart?.let { startMonitor(it) }
                } else {
                    pendingMonitorStart = null
                    Toast.makeText(this, "未允許通知權限，無法啟動通知欄監控", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_LOCATION_PERMISSION -> {
                val action = pendingLocationPermissionAction
                pendingLocationPermissionAction = null
                val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
                if (!granted) {
                    if (action?.isAuto == true) {
                        locationPermissionStateStore.setAutoRequestDenied(true)
                    }
                    when (action) {
                        is PendingLocationPermissionAction.NearbyRoute -> {
                            showLocationFallbackToast(LocationFallbackToast.PERMISSION_DENIED)
                        }
                        is PendingLocationPermissionAction.CurrentPlace -> {
                            action.callback(CurrentPlaceSelectionResult.Failure)
                        }
                        null -> Unit
                    }
                    return
                }
                when (action) {
                    is PendingLocationPermissionAction.NearbyRoute -> {
                        selectNearbyRouteWhenLocationAvailable(action.generation)
                    }
                    is PendingLocationPermissionAction.CurrentPlace -> {
                        continueCurrentPlaceWithPermission(action.isAuto, action.callback)
                    }
                    null -> Unit
                }
            }
        }
    }

    fun requestCurrentPlace(
        isAuto: Boolean,
        callback: (CurrentPlaceSelectionResult) -> Unit
    ) {
        if (LocationPermissionUtils.hasForegroundLocationPermission(this)) {
            continueCurrentPlaceWithPermission(isAuto, callback)
            return
        }
        if (isAuto && locationPermissionStateStore.isAutoRequestDenied()) {
            callback(CurrentPlaceSelectionResult.Failure)
            return
        }
        pendingLocationPermissionAction = PendingLocationPermissionAction.CurrentPlace(
            callback = callback,
            requestIsAuto = isAuto
        )
        ActivityCompat.requestPermissions(
            this,
            LocationPermissionUtils.permissions,
            REQUEST_LOCATION_PERMISSION
        )
    }

    fun refreshFrequentRoutes() {
        loadRouteConfigs()
    }

    private fun continueCurrentPlaceWithPermission(
        isAuto: Boolean,
        callback: (CurrentPlaceSelectionResult) -> Unit
    ) {
        if (!SystemLocationUtils.isLocationEnabled(this)) {
            if (isAuto) {
                callback(CurrentPlaceSelectionResult.Failure)
            } else {
                promptLocationSettingsForCurrentPlace(callback)
            }
            return
        }
        resolveCurrentPlace(callback)
    }

    private fun promptLocationSettingsForCurrentPlace(
        callback: (CurrentPlaceSelectionResult) -> Unit
    ) {
        pendingLocationSettingsCurrentPlaceCallback = callback
        Toast.makeText(this, "請開啟系統定位", Toast.LENGTH_SHORT).show()
        try {
            startActivity(SystemLocationUtils.settingsIntent())
        } catch (_: ActivityNotFoundException) {
            pendingLocationSettingsCurrentPlaceCallback = null
            callback(CurrentPlaceSelectionResult.Failure)
        }
    }

    private fun retryCurrentPlaceAfterLocationSettings() {
        val callback = pendingLocationSettingsCurrentPlaceCallback ?: return
        pendingLocationSettingsCurrentPlaceCallback = null
        if (!SystemLocationUtils.isLocationEnabled(this)) {
            callback(CurrentPlaceSelectionResult.Failure)
            return
        }
        resolveCurrentPlace(callback)
    }

    private fun resolveCurrentPlace(callback: (CurrentPlaceSelectionResult) -> Unit) {
        var finished = false
        val timeout = Runnable {
            if (finished) return@Runnable
            finished = true
            callback(CurrentPlaceSelectionResult.Failure)
        }
        mainHandler.postDelayed(timeout, CURRENT_PLACE_TOTAL_TIMEOUT_MS)

        fun finish(result: CurrentPlaceSelectionResult) {
            if (finished) return
            finished = true
            mainHandler.removeCallbacks(timeout)
            callback(result)
        }

        currentLocationCoordinator.getCurrentLocation { result ->
            when (result) {
                is CurrentLocationResult.Success -> {
                    placeNameResolver.resolve(result.snapshot) { nameResult ->
                        when (nameResult) {
                            is PlaceNameResolutionResult.Success -> {
                                finish(
                                    CurrentPlaceSelectionResult.Success(
                                        place = Place(
                                            name = nameResult.addressName,
                                            latitude = result.snapshot.latitude,
                                            longitude = result.snapshot.longitude
                                        ),
                                        snapshot = result.snapshot,
                                        attribution = nameResult.attribution
                                    )
                                )
                            }
                            PlaceNameResolutionResult.Failure -> finish(CurrentPlaceSelectionResult.Failure)
                        }
                    }
                }
                CurrentLocationResult.NoPermission,
                CurrentLocationResult.Timeout,
                CurrentLocationResult.Unavailable -> finish(CurrentPlaceSelectionResult.Failure)
            }
        }
    }

    private fun updateResultSummary(routes: List<BusRouteOption>) {
        if (routes.isEmpty()) {
            hideResultSummary()
            return
        }
        resultSummaryText.text = RouteResultCardFormatter.resultSummary(routes)
        resultUpdatedAtText.text = "更新時間：${RESULT_TIME_FORMAT.get()!!.format(Date())}"
        resultSummaryContainer.visibility = View.VISIBLE
    }

    private fun hideResultSummary() {
        resultSummaryContainer.visibility = View.GONE
        resultSummaryText.text = ""
        resultUpdatedAtText.text = ""
    }

    private fun clearResults() {
        invalidateActiveQuery()
        currentQueryContext = null
        setQueryLoading(false)
        currentResults = emptyList()
        sortField = null
        sortDirection = SortDirection.ASC
        preserveSortOnNextResults = false
        updateSortControls()
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        hideResultSummary()
        resultListContainer.visibility = View.GONE
        hideStatus()
        renderHomeShell()
        updateSwipeRefreshState()
    }

    private fun invalidateActiveQuery() {
        routeQueryGeneration.invalidate()
        busRouteRepository.cancelProgressiveQueries()
        cancelRefreshFeedback()
        if (::queryButton.isInitialized) {
            setQueryLoading(false)
        }
    }

    private fun renderRouteShortcuts() {
        routeShortcutCardsContainer.removeAllViews()
        val visibleRoutes = RouteShortcutSelector.visibleRoutes(routeConfigs, selectedRoute)
        visibleRoutes.forEachIndexed { index, route ->
            routeShortcutCardsContainer.addView(createRouteShortcutCard(route, index))
        }
        routePickerButton.visibility = if (routeConfigs.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun createRouteShortcutCard(route: RouteConfig, index: Int): MaterialCardView {
        val isSelected = selectedRoute?.id == route.id
        return MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(8)
            }
            radius = dp(8).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (isSelected) R.color.bus_surface_variant else R.color.bus_card_surface
                )
            )
            strokeWidth = dp(if (isSelected) 2 else 1)
            strokeColor = ContextCompat.getColor(
                context,
                if (isSelected) R.color.bus_chip_selected else R.color.bus_divider
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { selectRoute(route) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                minimumHeight = dp(74)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = route.name
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                        setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    if (isSelected && nearbySelectedRouteId == route.id) {
                        addView(TextView(context).apply {
                            text = "附近"
                            applyStableShortTextLayout(Gravity.CENTER)
                            setTextColor(ContextCompat.getColor(context, R.color.bus_chip_selected))
                            textSize = 11f
                            typeface = Typeface.DEFAULT_BOLD
                            background = ContextCompat.getDrawable(context, R.drawable.sort_chip_background)
                            setPadding(dp(6), dp(2), dp(6), dp(2))
                        })
                    }
                })
                addView(TextView(context).apply {
                    text = route.pathLabel()
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                    textSize = 12f
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                })
            })
        }
    }

    private fun selectRoute(route: RouteConfig) {
        if (selectedRoute?.id == route.id) return
        manualRouteSelectionGeneration += 1
        nearbySelectedRouteId = null
        selectedRoute = routeConfigs.firstOrNull { it.id == route.id } ?: route
        savedRouteUsageSession.selectSavedRoute(route.id)
        renderRouteShortcuts()
        clearResults()
    }

    private fun showRoutePicker() {
        val dialog = BottomSheetDialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(20))
        }
        content.addView(TextView(this).apply {
            text = "常用路線"
            applyStableShortTextLayout(Gravity.START)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.bus_text_primary))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        })
        routeConfigs.forEach { route ->
            content.addView(createRoutePickerRow(route) {
                dialog.dismiss()
                selectRoute(route)
            })
        }
        dialog.setContentView(content)
        dialog.show()
    }

    private fun createRoutePickerRow(route: RouteConfig, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            addView(TextView(context).apply {
                text = route.name
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(context).apply {
                text = route.pathLabel()
                setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
                textSize = 13f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }
    }

    private fun renderHomeShell() {
        if (!::emptyRouteState.isInitialized) return
        val isFirstRun = shouldShowFirstRun()
        normalTopActions.visibility = if (isFirstRun) View.GONE else View.VISIBLE
        firstRunTopActions.visibility = if (isFirstRun) View.VISIBLE else View.GONE
        emptyRouteState.visibility = if (isFirstRun) View.VISIBLE else View.GONE
        queryControls.visibility = if (routeConfigs.isEmpty()) View.GONE else View.VISIBLE
        resultSection.visibility = if (routeConfigs.isEmpty() && isFirstRun) View.GONE else View.VISIBLE
        routeManageButton.visibility = if (routeConfigs.isEmpty()) View.GONE else View.VISIBLE
        if (isFirstRun) animateFirstRunIntroIfNeeded()
    }

    private fun shouldShowFirstRun(): Boolean {
        return routeConfigs.isEmpty() &&
            currentQueryContext == null &&
            !isQueryInProgress &&
            currentResults.isEmpty()
    }

    private fun launchTransitCode() {
        val outcome = transitCodePaymentLauncher.launchTransitCode()
        if (outcome.shouldShowFailureToast) {
            Toast.makeText(this, R.string.transit_code_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun animateFirstRunIntroIfNeeded() {
        if (hasPlayedFirstRunIntroAnimation) return
        hasPlayedFirstRunIntroAnimation = true
        val views = listOf(
            firstRunHeadlineText,
            firstRunSampleLabelText,
            firstRunSampleRouteCard,
            firstRunActionGroup
        )
        if (!areSystemAnimationsEnabled()) {
            views.forEach { view ->
                view.alpha = 1f
                view.translationY = 0f
            }
            return
        }
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(8).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * FIRST_RUN_INTRO_STAGGER_MS)
                .setDuration(FIRST_RUN_INTRO_DURATION_MS)
                .start()
        }
    }

    private fun areSystemAnimationsEnabled(): Boolean {
        return Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }

    private fun showLocationFallbackToast(type: LocationFallbackToast) {
        if (!shownLocationFallbackToasts.add(type)) return
        val message = when (type) {
            LocationFallbackToast.PERMISSION_DENIED -> "未允許定位，已按常用排序選擇路線"
            LocationFallbackToast.UNAVAILABLE -> "暫時無法取得目前位置，已按常用排序選擇路線"
            LocationFallbackToast.IMPRECISE -> "目前位置不夠精確，已按常用排序選擇路線"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun selectSavedRouteAfterCreate(savedRouteId: Long, clearExistingResults: Boolean = true) {
        routeConfigs = loadRankedRouteConfigs()
        nearbySelectedRouteId = null
        selectedRoute = routeConfigs.firstOrNull { it.id == savedRouteId } ?: selectedRoute
        selectedRoute?.let { savedRouteUsageSession.selectSavedRoute(it.id) }
        renderRouteShortcuts()
        renderHomeShell()
        if (clearExistingResults) {
            clearResults()
        }
    }

    private fun showLoadingState() {
        setQueryLoading(true)
        currentResults = emptyList()
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        hideResultSummary()
        resultListContainer.visibility = View.GONE
        showStatus(
            title = "正在查詢路線",
            message = "正在匹配可用巴士方案和候車時間。",
            showProgress = true
        )
    }

    private fun showRefreshLoadingState(queryId: Int) {
        if (!refreshFeedbackState.start(queryId)) {
            resultSwipeRefresh.isRefreshing = false
            return
        }
        captureRefreshViewport()
        setQueryLoading(true)
        resultSwipeRefresh.isRefreshing = false
        hideStatus()
        renderRefreshFeedback()
    }

    private fun displayFailure() {
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        hideResultSummary()
        resultListContainer.visibility = View.GONE
        showStatus(
            title = "路線查詢失敗",
            message = "請稍後重試，或換一條常用路線再查。",
            showProgress = false
        )
    }

    private fun setQueryLoading(isLoading: Boolean) {
        isQueryInProgress = isLoading
        queryButton.isEnabled = !isLoading
        queryButton.text = if (isLoading) "查詢中..." else "查詢"
        updateSwipeRefreshState()
    }

    private fun finishQueryLoading() {
        resultSwipeRefresh.isRefreshing = false
        setQueryLoading(false)
    }

    private fun captureRefreshViewport() {
        val layoutManager = resultList.layoutManager as? LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return
        val offset = layoutManager.findViewByPosition(position)?.top ?: 0
        refreshViewport = RefreshViewport(position, offset)
    }

    private fun restoreRefreshViewport() {
        val viewport = refreshViewport ?: return
        refreshViewport = null
        resultList.post {
            val layoutManager = resultList.layoutManager as? LinearLayoutManager ?: return@post
            layoutManager.scrollToPositionWithOffset(viewport.position, viewport.offset)
        }
    }

    private fun cancelRefreshFeedback() {
        refreshFinishRunnable?.let(mainHandler::removeCallbacks)
        refreshFinishRunnable = null
        refreshViewport = null
        refreshFeedbackState.cancel()
        if (::resultRefreshOverlay.isInitialized) {
            renderRefreshFeedback()
        }
    }

    private fun renderRefreshFeedback() {
        val basePadding = resultListBasePadding ?: return
        val isVisible = refreshFeedbackState.visualState != RouteRefreshVisualState.IDLE
        resultRefreshOverlay.visibility = if (isVisible) View.VISIBLE else View.GONE
        resultRefreshProgress.visibility =
            if (refreshFeedbackState.visualState == RouteRefreshVisualState.REFRESHING) View.VISIBLE else View.GONE
        resultRefreshSuccess.visibility =
            if (refreshFeedbackState.visualState == RouteRefreshVisualState.SUCCESS) View.VISIBLE else View.GONE
        resultRefreshOverlay.contentDescription = getString(
            if (refreshFeedbackState.visualState == RouteRefreshVisualState.SUCCESS) {
                R.string.route_refresh_complete
            } else {
                R.string.route_refreshing
            }
        )
        resultList.setPadding(
            basePadding.left,
            basePadding.top + if (isVisible) dp(REFRESH_LIST_TOP_INSET_DP) else 0,
            basePadding.right,
            basePadding.bottom
        )
    }

    private fun updateSwipeRefreshState() {
        if (!::resultSwipeRefresh.isInitialized) return
        resultSwipeRefresh.isEnabled = RouteResultsRefreshPolicy.canRefresh(
            hasQueryContext = currentQueryContext != null,
            hasResults = currentResults.isNotEmpty(),
            isQueryInProgress = isQueryInProgress
        )
    }

    private fun updateSortControls() {
        val selectedBackground = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.bus_chip_selected)
        )
        val defaultBackground = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.bus_chip_surface)
        )
        val selectedTextColor = ContextCompat.getColor(this, R.color.white)
        val defaultTextColor = ContextCompat.getColor(this, R.color.bus_text_primary)
        val defaultStroke = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.bus_divider)
        )
        val selectedStroke = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.bus_chip_selected)
        )

        sortButtons.forEach { (field, button) ->
            val isSelected = sortField == field
            button.text = sortButtonText(field)
            button.backgroundTintList = if (isSelected) selectedBackground else defaultBackground
            button.setTextColor(if (isSelected) selectedTextColor else defaultTextColor)
            button.strokeColor = if (isSelected) selectedStroke else defaultStroke
        }
    }

    private fun sortButtonText(field: SortField): String {
        val label = when (field) {
            SortField.ROUTE -> "路線"
            SortField.PRICE -> "價格"
            SortField.DURATION -> "耗時"
            SortField.ARRIVAL -> "候車"
            SortField.WALKING_DISTANCE -> "步行"
        }
        if (sortField != field) return label
        return "$label ${if (sortDirection == SortDirection.ASC) "↑" else "↓"}"
    }

    private fun showStatus(title: String, message: String, showProgress: Boolean) {
        val shouldAnimate = resultStatusCard.visibility != View.VISIBLE
        resultStatusTitle.text = title
        resultStatusMessage.text = message
        resultStatusMessage.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
        resultStatusProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
        resultStatusCard.visibility = View.VISIBLE
        if (shouldAnimate) {
            animateIn(resultStatusCard)
        }
    }

    private fun hideStatus() {
        resultStatusCard.visibility = View.GONE
        resultStatusProgress.visibility = View.GONE
    }

    private fun animateIn(view: View) {
        view.alpha = 0f
        view.translationY = 12f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .start()
    }

    private fun pulse(view: View) {
        view.animate()
            .alpha(0.72f)
            .setDuration(90L)
            .withEndAction {
                view.animate()
                    .alpha(1f)
                    .setDuration(90L)
                    .start()
            }
            .start()
    }

    private fun routeIdentitySnapshot(routes: List<RouteConfig>): List<RouteIdentitySnapshot> {
        return routes.map { route ->
            RouteIdentitySnapshot(route.id, route.name, route.origin, route.destination)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class RouteIdentitySnapshot(
        val id: Long,
        val name: String,
        val origin: Place,
        val destination: Place
    )

    private data class PendingMonitorStart(
        val route: BusRouteOption,
        val walkingMinutes: Int? = null,
        val voiceEnabled: Boolean = true
    )

    private data class RefreshViewport(
        val position: Int,
        val offset: Int
    )

    private data class ViewPadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private enum class LocationFallbackToast {
        PERMISSION_DENIED,
        UNAVAILABLE,
        IMPRECISE
    }

    private sealed class PendingLocationPermissionAction(val isAuto: Boolean) {
        data class NearbyRoute(val generation: Int) : PendingLocationPermissionAction(isAuto = true)
        data class CurrentPlace(
            val callback: (CurrentPlaceSelectionResult) -> Unit,
            val requestIsAuto: Boolean
        ) : PendingLocationPermissionAction(isAuto = requestIsAuto)
    }

    private sealed class QueryContext {
        data class Saved(val routeId: Long) : QueryContext()
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 301
        private const val REQUEST_LOCATION_PERMISSION = 302
        private const val REFRESH_LIST_TOP_INSET_DP = 44
        private const val REFRESH_SUCCESS_DURATION_MS = 500L
        private const val CURRENT_PLACE_TOTAL_TIMEOUT_MS = 5_000L
        private const val FIRST_RUN_INTRO_DURATION_MS = 180L
        private const val FIRST_RUN_INTRO_STAGGER_MS = 45L
        private const val STATE_SELECTED_ROUTE_ID = "selected_route_id"
        private const val STATE_RECORDED_USAGE_ROUTE_ID = "recorded_usage_route_id"
        private const val STATE_SELECTED_DESTINATION = "selected_destination"
        private const val TAG_FREQUENT_ROUTES = "frequent_routes"
        private const val TAG_SEARCH = "search"
        private const val TAG_SETTINGS = "settings"

        private val RESULT_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH:mm:ss", Locale.US)
            }
        }
    }
}

private const val LOG_TAG = "MainActivity"
