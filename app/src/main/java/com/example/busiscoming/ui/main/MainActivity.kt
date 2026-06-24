package com.example.busiscoming.ui.main

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.busiscoming.R
import com.example.busiscoming.data.location.CurrentLocationCoordinator
import com.example.busiscoming.data.location.CurrentLocationResult
import com.example.busiscoming.data.location.CurrentPlaceSelectionResult
import com.example.busiscoming.data.location.LocationPermissionStateStore
import com.example.busiscoming.data.location.LocationPermissionUtils
import com.example.busiscoming.data.location.MockPlaceNameResolver
import com.example.busiscoming.data.location.NearbyRouteSelectionPolicy
import com.example.busiscoming.data.location.SystemLocationUtils
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.SortDirection
import com.example.busiscoming.data.model.SortField
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.model.WalkingTimeCalculator
import com.example.busiscoming.data.repository.BusRouteQueryCallback
import com.example.busiscoming.data.repository.BusRouteRepository
import com.example.busiscoming.data.repository.BusRouteSorter
import com.example.busiscoming.data.repository.CitybusBusRouteRepository
import com.example.busiscoming.data.repository.CitybusRouteDetailRepository
import com.example.busiscoming.data.repository.RouteDetailRepository
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.example.busiscoming.service.BusMonitorService
import com.example.busiscoming.service.BusMonitorSchedulingCapability
import com.example.busiscoming.service.BusMonitorSessionStore
import com.example.busiscoming.data.model.BusMonitorSessionPolicy
import com.example.busiscoming.ui.common.applyStatusBarPadding
import com.example.busiscoming.ui.edit.RouteEditActivity
import com.example.busiscoming.ui.manage.RouteManageActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private val busRouteRepository: BusRouteRepository = CitybusBusRouteRepository()
    private val routeDetailRepository: RouteDetailRepository = CitybusRouteDetailRepository()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queryExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val placeSearchExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var queryButton: MaterialButton
    private lateinit var emptyTemporaryQueryButton: MaterialButton
    private lateinit var emptyRouteState: LinearLayout
    private lateinit var queryControls: LinearLayout
    private lateinit var routeShortcutCardsContainer: LinearLayout
    private lateinit var routePickerButton: MaterialButton
    private lateinit var resultSection: LinearLayout
    private lateinit var temporaryQueryContextBar: MaterialCardView
    private lateinit var temporaryQueryContextPathText: TextView
    private lateinit var temporaryQuerySaveButton: MaterialButton
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
    private lateinit var temporaryRouteBottomSheet: TemporaryRouteBottomSheet
    private lateinit var transitCodePaymentLauncher: TransitCodePaymentLaunchAction

    private var routeConfigs: List<RouteConfig> = emptyList()
    private var selectedRoute: RouteConfig? = null
    private var currentResults: List<BusRouteOption> = emptyList()
    private var sortField: SortField? = null
    private var sortDirection: SortDirection = SortDirection.ASC
    private var querySequence: Int = 0
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
    private val shownLocationFallbackToasts = mutableSetOf<LocationFallbackToast>()
    private var pendingLocationPermissionAction: PendingLocationPermissionAction? = null
    private var pendingLocationSettingsCurrentPlaceCallback: ((CurrentPlaceSelectionResult) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "BusIsComing"

        routeConfigRepository = RouteConfigRepository(this)
        currentLocationCoordinator = CurrentLocationCoordinator(this)
        locationPermissionStateStore = LocationPermissionStateStore(this)
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
        temporaryRouteBottomSheet = TemporaryRouteBottomSheet(
            context = this,
            routeConfigRepository = routeConfigRepository,
            mainHandler = mainHandler,
            searchExecutor = placeSearchExecutor,
            onCurrentPlaceRequested = ::requestCurrentPlaceForTemporaryQuery,
            onQuery = ::queryTemporaryRoute,
            onSaved = { savedRouteId -> selectSavedRouteAfterCreate(savedRouteId) }
        )
        transitCodePaymentLauncher = TransitCodePaymentLauncher.forActivity(this)
        findViewById<View>(R.id.mainRoot).applyStatusBarPadding()
        bindViews()
        setupResultList()
        setupActions()
    }

    override fun onDestroy() {
        invalidateActiveQuery()
        mainHandler.removeCallbacksAndMessages(null)
        routeDetailBottomSheet.dispose()
        etaArrivalsBottomSheet.dispose()
        monitorSettingsBottomSheet.dispose()
        temporaryRouteBottomSheet.dispose()
        queryExecutor.shutdownNow()
        placeSearchExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        loadRouteConfigs()
        retryCurrentPlaceAfterLocationSettings()
    }

    private fun bindViews() {
        queryButton = findViewById(R.id.queryButton)
        emptyTemporaryQueryButton = findViewById(R.id.emptyTemporaryQueryButton)
        emptyRouteState = findViewById(R.id.emptyRouteState)
        queryControls = findViewById(R.id.queryControls)
        routeShortcutCardsContainer = findViewById(R.id.routeShortcutCardsContainer)
        routePickerButton = findViewById(R.id.routePickerButton)
        resultSection = findViewById(R.id.resultSection)
        temporaryQueryContextBar = findViewById(R.id.temporaryQueryContextBar)
        temporaryQueryContextPathText = findViewById(R.id.temporaryQueryContextPathText)
        temporaryQuerySaveButton = findViewById(R.id.temporaryQuerySaveButton)
        sortControls = findViewById(R.id.sortControls)
        resultSummaryContainer = findViewById(R.id.resultSummaryContainer)
        resultSummaryText = findViewById(R.id.resultSummaryText)
        resultUpdatedAtText = findViewById(R.id.resultUpdatedAtText)
        resultStatusCard = findViewById(R.id.resultStatusCard)
        resultStatusProgress = findViewById(R.id.resultStatusProgress)
        resultStatusTitle = findViewById(R.id.resultStatusTitle)
        resultStatusMessage = findViewById(R.id.resultStatusMessage)
        resultListContainer = findViewById(R.id.resultListContainer)
        resultSwipeRefresh = findViewById(R.id.resultSwipeRefresh)
        resultList = findViewById(R.id.busRouteList)
        resultRefreshOverlay = findViewById(R.id.resultRefreshOverlay)
        resultRefreshProgress = findViewById(R.id.resultRefreshProgress)
        resultRefreshSuccess = findViewById(R.id.resultRefreshSuccess)
        resultListBasePadding = ViewPadding(
            left = resultList.paddingLeft,
            top = resultList.paddingTop,
            right = resultList.paddingRight,
            bottom = resultList.paddingBottom
        )
        sortButtons = mapOf(
            SortField.ROUTE to findViewById(R.id.sortRouteButton),
            SortField.PRICE to findViewById(R.id.sortPriceButton),
            SortField.DURATION to findViewById(R.id.sortDurationButton),
            SortField.ARRIVAL to findViewById(R.id.sortArrivalButton),
            SortField.WALKING_DISTANCE to findViewById(R.id.sortWalkingDistanceButton)
        )
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
        findViewById<MaterialButton>(R.id.manageRoutesButton).setOnClickListener {
            startActivity(Intent(this, RouteManageActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.transitCodeButton).setOnClickListener {
            val outcome = transitCodePaymentLauncher.launchTransitCode()
            if (outcome.shouldShowFailureToast) {
                Toast.makeText(this, R.string.transit_code_launch_failed, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.emptyAddRouteButton).setOnClickListener {
            startActivity(Intent(this, RouteEditActivity::class.java))
        }
        emptyTemporaryQueryButton.setOnClickListener { showTemporaryRouteSheet() }
        routePickerButton.setOnClickListener { showRoutePicker() }
        temporaryQuerySaveButton.setOnClickListener { saveCurrentTemporaryQuery() }
        queryButton.setOnClickListener { querySelectedRoute() }
        resultSwipeRefresh.setOnRefreshListener { refreshCurrentResults() }
        sortButtons.forEach { (field, button) ->
            button.setOnClickListener {
                sortBy(field)
                pulse(button)
            }
        }
    }

    private fun loadRouteConfigs() {
        val previousSelectedId = selectedRoute?.id
        val previousRouteSnapshot = routeIdentitySnapshot(routeConfigs)
        routeConfigs = routeConfigRepository.getAll()

        if (routeConfigs.isEmpty()) {
            invalidateActiveQuery()
            selectedRoute = null
            currentQueryContext = null
            hideTemporaryQueryContext()
            emptyRouteState.visibility = View.VISIBLE
            queryControls.visibility = View.GONE
            resultSection.visibility = View.GONE
            currentResults = emptyList()
            busRouteAdapter.submitList(emptyList())
            updateSwipeRefreshState()
            return
        }

        emptyRouteState.visibility = View.GONE
        queryControls.visibility = View.VISIBLE
        resultSection.visibility = View.VISIBLE

        selectedRoute = routeConfigs.firstOrNull { it.id == previousSelectedId } ?: routeConfigs.first()
        renderRouteShortcuts()
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
            if (isFinishing || isDestroyed || manualRouteSelectionGeneration != generation) return@getCurrentLocation
            when (result) {
                is CurrentLocationResult.Success -> {
                    val route = NearbyRouteSelectionPolicy.selectRoute(result.snapshot, routeConfigs)
                    if (route == null) {
                        showLocationFallbackToast(LocationFallbackToast.IMPRECISE)
                        return@getCurrentLocation
                    }
                    selectedRoute = routeConfigs.firstOrNull { it.id == route.id } ?: route
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

    private fun queryTemporaryRoute(origin: Place, destination: Place) {
        queryRoute(origin, destination, null, QueryContext.Temporary(origin, destination))
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
            is QueryContext.Temporary -> {
                queryRoute(
                    origin = context.origin,
                    destination = context.destination,
                    sourceRoute = null,
                    queryContext = context,
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

        if (RouteResultsRefreshPolicy.shouldRecordUsage(isRefresh, recordUsage)) sourceRoute?.let { route ->
            routeConfigRepository.recordUsage(route.id)
            routeConfigs = routeConfigRepository.getAll()
            selectedRoute = routeConfigs.firstOrNull { it.id == route.id } ?: route
            renderRouteShortcuts()
        }

        val queryId = ++querySequence
        currentQueryContext = queryContext
        preserveSortOnNextResults = preserveSort
        updateTemporaryQueryContext()
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
                            if (querySequence != queryId || isFinishing || isDestroyed) return@post
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
                            if (querySequence != queryId || isFinishing || isDestroyed) return@post
                            updateRouteWaitTime(routeId, waitTimeState)
                        }
                    }

                    override fun onRouteStopPreviewUpdated(routeId: String, preview: RouteCardStopPreview) {
                        mainHandler.post {
                            if (querySequence != queryId || isFinishing || isDestroyed) return@post
                            updateRouteStopPreview(routeId, preview)
                        }
                    }

                    override fun onFailure(error: Throwable) {
                        Log.e(LOG_TAG, "Bus route query failed", error)
                        mainHandler.post {
                            if (querySequence != queryId || isFinishing || isDestroyed) return@post
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
        if (route.firstLegEtaQuery == null || route.waitTimeState !is WaitTimeState.Available) {
            Toast.makeText(this, "此路線暫時無法監控", Toast.LENGTH_SHORT).show()
            return
        }
        val origin = currentOriginPlace()
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
            is QueryContext.Temporary -> context.origin
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
                return
            }
        }
        if (!BusMonitorSchedulingCapability.isIgnoringBatteryOptimizations(this)) {
            val intent = BusMonitorSchedulingCapability.batteryOptimizationSettingsIntent(this)
            if (intent.resolveActivity(packageManager) != null) {
                Toast.makeText(this, "可允許忽略電池最佳化，提升鎖屏更新可靠性", Toast.LENGTH_LONG).show()
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

    private fun requestCurrentPlaceForTemporaryQuery(
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
        currentLocationCoordinator.getCurrentLocation { result ->
            when (result) {
                is CurrentLocationResult.Success -> {
                    val place = MockPlaceNameResolver.resolve(result.snapshot)
                    callback(CurrentPlaceSelectionResult.Success(place, result.snapshot))
                }
                CurrentLocationResult.NoPermission,
                CurrentLocationResult.Timeout,
                CurrentLocationResult.Unavailable -> callback(CurrentPlaceSelectionResult.Failure)
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

    private fun updateTemporaryQueryContext() {
        val context = currentQueryContext
        if (context !is QueryContext.Temporary) {
            hideTemporaryQueryContext()
            return
        }
        temporaryQueryContextPathText.text = "${context.origin.name} \u2192 ${context.destination.name}"
        temporaryQueryContextBar.visibility = View.VISIBLE
    }

    private fun hideTemporaryQueryContext() {
        temporaryQueryContextBar.visibility = View.GONE
        temporaryQueryContextPathText.text = ""
    }

    private fun saveCurrentTemporaryQuery() {
        val context = currentQueryContext as? QueryContext.Temporary ?: return
        promptSaveTemporaryRoute(context.origin, context.destination)
    }

    private fun promptSaveTemporaryRoute(origin: Place, destination: Place) {
        TemporaryRouteSaveDialog.show(
            context = this,
            routeConfigRepository = routeConfigRepository,
            origin = origin,
            destination = destination
        ) { id ->
            currentQueryContext = QueryContext.Saved(id)
            hideTemporaryQueryContext()
            selectSavedRouteAfterCreate(id, clearExistingResults = false)
        }
    }

    private fun clearResults() {
        invalidateActiveQuery()
        currentQueryContext = null
        hideTemporaryQueryContext()
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
        updateSwipeRefreshState()
    }

    private fun invalidateActiveQuery() {
        querySequence += 1
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
        content.addView(createTemporaryQueryRow {
            dialog.dismiss()
            showTemporaryRouteSheet()
        })
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

    private fun createTemporaryQueryRow(onClick: () -> Unit): View {
        return TextView(this).apply {
            text = "查詢臨時起點和終點"
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(52)
            setTextColor(ContextCompat.getColor(context, R.color.bus_text_secondary))
            textSize = 15f
            setOnClickListener { onClick() }
        }
    }

    private fun showTemporaryRouteSheet() {
        temporaryRouteBottomSheet.show()
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
        routeConfigs = routeConfigRepository.getAll()
        nearbySelectedRouteId = null
        selectedRoute = routeConfigs.firstOrNull { it.id == savedRouteId } ?: selectedRoute
        emptyRouteState.visibility = if (routeConfigs.isEmpty()) View.VISIBLE else View.GONE
        queryControls.visibility = if (routeConfigs.isEmpty()) View.GONE else View.VISIBLE
        resultSection.visibility = if (routeConfigs.isEmpty()) View.GONE else View.VISIBLE
        renderRouteShortcuts()
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
        data class Temporary(val origin: Place, val destination: Place) : QueryContext()
    }

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 301
        private const val REQUEST_LOCATION_PERMISSION = 302
        private const val REFRESH_LIST_TOP_INSET_DP = 44
        private const val REFRESH_SUCCESS_DURATION_MS = 500L

        private val RESULT_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH:mm:ss", Locale.US)
            }
        }
    }
}

private const val LOG_TAG = "MainActivity"
