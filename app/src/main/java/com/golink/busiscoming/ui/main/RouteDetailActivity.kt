package com.golink.busiscoming.ui.main

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.local.RouteAutoRefreshInterval
import com.golink.busiscoming.data.local.RouteAutoRefreshSettingsEvents
import com.golink.busiscoming.data.local.RouteAutoRefreshSettingsStore
import com.golink.busiscoming.data.location.CurrentLocationCoordinator
import com.golink.busiscoming.data.location.CurrentLocationResult
import com.golink.busiscoming.data.location.LocationPermissionUtils
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.EtaUnavailableReason
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.RouteGeometryCoordinate
import com.golink.busiscoming.data.model.RouteGeometryKey
import com.golink.busiscoming.data.model.RouteGeometrySegment
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.CitybusFirstLegEtaService
import com.golink.busiscoming.data.repository.CitybusP2pStopMapResolver
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.CitybusRouteGeometryRepository
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.data.repository.RouteDetailCacheOwner
import com.golink.busiscoming.data.repository.RouteDetailRequestIdentity
import com.golink.busiscoming.data.repository.RouteDetailDiagnosticEvent
import com.golink.busiscoming.data.repository.RouteDetailDiagnostics
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.data.repository.PedestrianRouteProcessRuntime
import com.golink.busiscoming.data.repository.PedestrianRouteRequestRuntime
import com.golink.busiscoming.data.repository.SingleFlightRequestCoordinator
import com.golink.busiscoming.data.repository.SingleFlightRequestHandle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class RouteDetailActivity : AppCompatActivity() {
    private val pageGeneration = RouteDetailRuntime.nextPageGeneration()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val repository by lazy { RouteDetailRuntime.repositoryFactory() }
    private val geometryRepository by lazy { RouteDetailRuntime.geometryRepositoryFactory() }
    private val locationCoordinator by lazy { CurrentLocationCoordinator(this) }
    private val walkingViewModel by lazy {
        ViewModelProvider(this)[RouteDetailWalkingViewModel::class.java]
    }
    private val detailAutoRefreshController = ForegroundAutoRefreshController { generation ->
        startDetailAutomaticRefresh(generation)
    }
    private val detailRefreshCycleCoordinator = RouteDetailRefreshCycleCoordinator()
    private val expandedLegIndexes = linkedSetOf<Int>()
    private val geometries = linkedMapOf<RouteGeometryKey, RouteGeometrySegment>()
    private val failedGeometryKeys = linkedSetOf<RouteGeometryKey>()
    private lateinit var geometryCoordinator: RouteGeometryLoadCoordinator
    private lateinit var pageState: RouteDetailPageState

    private lateinit var args: RouteDetailLaunchArgs
    private lateinit var adapter: RouteDetailAdapter
    private lateinit var list: RecyclerView
    private lateinit var listLayoutManager: LinearLayoutManager
    private lateinit var root: View
    private lateinit var sheet: MaterialCardView
    private lateinit var sheetContent: View
    private lateinit var sheetBehavior: BottomSheetBehavior<MaterialCardView>
    private lateinit var sheetHandle: View
    private lateinit var floatingBack: MaterialButton
    private lateinit var mapView: MapView
    private lateinit var mapControls: View
    private lateinit var mapError: TextView
    private lateinit var sheetMapError: TextView
    private lateinit var csdiAttribution: View

    private var renderer: GoogleRouteMapRenderer? = null
    private var lastPresentation: RouteMapPresentation? = null
    private var detail: RouteDetail? = null
    private var waitTimeState: WaitTimeState = WaitTimeState.Loading
    private val detailLoading: Boolean
        get() = pageState.detail is ProgressiveValue.Loading || pageState.detail is ProgressiveValue.Refreshing
    private val detailFailed: Boolean
        get() = pageState.detail is ProgressiveValue.Failure
    private var detent = RouteDetailSheetDetent.SUMMARY
    private var selectedMarkerId: String? = null
    private var selectedTimelineId: String? = null
    private var detailGeneration = 0
    private var geometryGeneration = 0
    private var etaGeneration = 0
    private val geometryHandles = mutableListOf<RouteGeometryLoadHandle>()
    private var detailRequestHandle: SingleFlightRequestHandle? = null
    private var autoDetailRequestHandle: SingleFlightRequestHandle? = null
    private var geometryPendingCount = 0
    private var foreground = false
    private var destroyed = false
    private var initialFitDone = false
    private var mapReady = false
    private var baseMapLoaded = false
    private var mapUnavailable = false
    private var locationPermissionRequestedBefore = false
    private var repeatedPermissionRequest = false
    private var statusBarInset = 0
    private var navigationBarInset = 0
    private var cameraLatitude: Double? = null
    private var cameraLongitude: Double? = null
    private var cameraZoom: Float? = null
    private var cameraBearing: Float = 0f
    private var cameraTilt: Float = 0f
    private var cameraOwner: RouteDetailCameraOwner = RouteDetailCameraOwner.PAGE
    private var pendingListPosition = RecyclerView.NO_POSITION
    private var pendingListOffset = 0
    private var lastEtaSuccessMillis: Long? = null
    private lateinit var autoRefreshSettingsStore: RouteAutoRefreshSettingsStore
    private var autoRefreshSettingsSubscription: AutoCloseable? = null
    private var detailAutoRefreshBaselineRecorded = false
    private var summaryTouchStartY = 0f
    private var summaryTouchStartedCollapsed = false
    private var walkingEventGeneration = 0
    private var pendingSummaryTarget: PendingSummaryTarget? = null
    private var summaryHighlightRunnable: Runnable? = null
    private val walkingObserver: (RouteDetailWalkingSnapshot) -> Unit = { snapshot ->
        mainHandler.post {
            if (destroyed || !::pageState.isInitialized) return@post
            dispatch(
                RouteDetailPageEvent.WalkingStarted(
                    pageGeneration,
                    ++walkingEventGeneration,
                    snapshot.segments
                )
            )
            if (detail != null) renderDetail() else renderMap()
        }
    }

    private val mapLoadTimeout = Runnable {
        if (!destroyed && foreground && mapReady && !baseMapLoaded) onMapUnavailable()
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            enableMyLocationAndFocus()
        } else {
            val permanentlyDenied = repeatedPermissionRequest &&
                LocationPermissionUtils.permissions.none(::shouldShowRequestPermissionRationale)
            if (permanentlyDenied) {
                showMessage(R.string.route_map_location_permission_settings, R.string.settings) {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
            } else {
                showMessage(R.string.route_map_location_permission_denied)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoRefreshSettingsStore = RouteAutoRefreshSettingsStore(this)
        detailAutoRefreshController.setInterval(autoRefreshSettingsStore.getInterval())
        autoRefreshSettingsSubscription = RouteAutoRefreshSettingsEvents.observe { interval ->
            mainHandler.post {
                if (interval == RouteAutoRefreshInterval.OFF) cancelActiveDetailAutoRefresh()
                detailAutoRefreshController.setInterval(interval)
                updateDetailAutoRefreshEligibility()
            }
        }
        val decoded = intent.extras?.let(RouteDetailLaunchArgs::fromBundle)
        if (decoded == null) {
            finish()
            return
        }
        args = decoded
        val expectedGeometryKeys = args.routeDetailQuery?.plan?.legs.orEmpty().map { leg ->
                RouteGeometryKey(leg.routeVariant, leg.boardingSeq, leg.alightingSeq)
            }.filter { it.isValid }.toSet()
        geometryCoordinator = RouteGeometryLoadCoordinator(expectedGeometryKeys.toList())
        pageState = RouteDetailPageState.initial(pageGeneration, expectedGeometryKeys).copy(
            eta = ProgressiveValue.Success(args.waitTimeState)
        )
        syncPageStateFields()
        restorePageState(savedInstanceState)
        args.routeDetailQuery?.let { query ->
            walkingViewModel.observe(walkingObserver)
            walkingViewModel.initialize(
                query = query,
                stopMapLoader = { RouteDetailRuntime.stopMapResolverFactory().resolveStopMap(it) },
                runtime = RouteDetailRuntime.pedestrianRuntime
            )
        }
        setContentView(R.layout.activity_route_detail)
        bindViews()
        setupTimeline()
        setupBottomSheet()
        setupInsets()
        setupMap(savedInstanceState?.getBundle(STATE_MAP))

        showLaunchSummary(loading = args.routeDetailQuery != null, failed = args.routeDetailQuery == null)
        if (args.routeDetailQuery == null) {
            showLaunchSummary(loading = false, failed = true)
        } else {
            repository.loadCachedRouteDetail(args.toRoute())?.let { cached ->
                dispatch(RouteDetailPageEvent.DetailCacheAvailable(pageGeneration, cached))
                renderDetail()
                recordDetailAutoRefreshBaselineIfNeeded()
            }
            loadDetail()
            loadGeometry()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
        foreground = true
        updateDetailAutoRefreshEligibility()
        if (lastEtaSuccessMillis == null) {
            refreshFirstLegEta()
        }
        scheduleMapLoadTimeout()
        syncGeometryFailureState()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        updateMyLocationLayer()
    }

    override fun onPause() {
        renderer?.setMyLocationEnabled(false)
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        foreground = false
        cancelActiveDetailAutoRefresh()
        updateDetailAutoRefreshEligibility()
        etaGeneration += 1
        mainHandler.removeCallbacks(mapLoadTimeout)
        if (::mapView.isInitialized) mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putIntArray(STATE_EXPANDED, expandedLegIndexes.toIntArray())
        outState.putString(STATE_DETENT, detent.name)
        outState.putString(STATE_SELECTED_MARKER, selectedMarkerId)
        outState.putString(STATE_SELECTED_TIMELINE, selectedTimelineId)
        outState.putBoolean(STATE_INITIAL_FIT, initialFitDone)
        outState.putString(STATE_CAMERA_OWNER, cameraOwner.name)
        outState.putBoolean(STATE_LOCATION_REQUESTED, locationPermissionRequestedBefore)
        cameraLatitude?.let { outState.putDouble(STATE_CAMERA_LATITUDE, it) }
        cameraLongitude?.let { outState.putDouble(STATE_CAMERA_LONGITUDE, it) }
        cameraZoom?.let { outState.putFloat(STATE_CAMERA_ZOOM, it) }
        outState.putFloat(STATE_CAMERA_BEARING, cameraBearing)
        outState.putFloat(STATE_CAMERA_TILT, cameraTilt)
        if (::listLayoutManager.isInitialized) {
            val position = listLayoutManager.findFirstVisibleItemPosition()
            if (position != RecyclerView.NO_POSITION) {
                outState.putInt(STATE_LIST_POSITION, position)
                outState.putInt(STATE_LIST_OFFSET, listLayoutManager.findViewByPosition(position)?.top ?: 0)
            }
        }
        if (::mapView.isInitialized) {
            val mapState = Bundle()
            mapView.onSaveInstanceState(mapState)
            outState.putBundle(STATE_MAP, mapState)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        destroyed = true
        pendingSummaryTarget = null
        summaryHighlightRunnable?.let(mainHandler::removeCallbacks)
        summaryHighlightRunnable = null
        if (::pageState.isInitialized) dispatch(RouteDetailPageEvent.Destroyed(pageGeneration))
        detailGeneration += 1
        geometryGeneration += 1
        etaGeneration += 1
        detailRequestHandle?.cancel()
        detailRequestHandle = null
        autoDetailRequestHandle?.cancel()
        autoDetailRequestHandle = null
        detailRefreshCycleCoordinator.invalidate()
        detailAutoRefreshController.close()
        autoRefreshSettingsSubscription?.close()
        autoRefreshSettingsSubscription = null
        if (::args.isInitialized && args.routeDetailQuery != null) {
            walkingViewModel.clearObserver(walkingObserver)
        }
        geometryHandles.forEach(RouteGeometryLoadHandle::close)
        geometryHandles.clear()
        renderer?.clear()
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }

    private fun bindViews() {
        root = findViewById(R.id.routeDetailRoot)
        sheet = findViewById(R.id.routeDetailSheet)
        sheetContent = findViewById(R.id.routeDetailSheetContent)
        sheetHandle = findViewById(R.id.routeDetailSheetHandle)
        floatingBack = findViewById(R.id.routeDetailFloatingBack)
        mapView = findViewById(R.id.routeDetailMap)
        mapControls = findViewById(R.id.routeDetailMapControls)
        mapError = findViewById(R.id.routeDetailMapError)
        sheetMapError = findViewById(R.id.routeDetailSheetMapError)
        csdiAttribution = findViewById(R.id.routeDetailCsdiAttribution)

        floatingBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<MaterialButton>(R.id.routeDetailOverview).setOnClickListener {
            renderer?.fitOverview(animated = true, paddingPx = dimension(R.dimen.route_map_camera_padding))
        }
        findViewById<MaterialButton>(R.id.routeDetailLocation).setOnClickListener { onLocationClicked() }
        csdiAttribution.setOnClickListener { showCsdiNotice() }
    }

    private fun setupTimeline() {
        adapter = RouteDetailAdapter(
            ::toggleLeg,
            ::retryDetail,
            ::onTimelineStopSelected,
            ::onSummarySegmentSelected
        )
        listLayoutManager = LinearLayoutManager(this)
        list = findViewById<RecyclerView>(R.id.routeDetailList).apply {
            layoutManager = listLayoutManager
            adapter = this@RouteDetailActivity.adapter
            itemAnimator = null
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        summaryTouchStartY = event.y
                        summaryTouchStartedCollapsed = detent == RouteDetailSheetDetent.SUMMARY
                        if (summaryTouchStartedCollapsed) sheetBehavior.isDraggable = false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (summaryTouchStartedCollapsed) sheetBehavior.isDraggable = true
                        if (summaryTouchStartedCollapsed && summaryTouchStartY - event.y > dimension(R.dimen.route_detail_sheet_swipe_threshold)) {
                            list.post { applyDetent(RouteDetailSheetPolicy.onSummaryContentSwipeUp()) }
                        }
                    }
                    MotionEvent.ACTION_CANCEL -> if (summaryTouchStartedCollapsed) sheetBehavior.isDraggable = true
                }
                false
            }
        }
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() = scheduleSheetMetrics()
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = scheduleSheetMetrics()
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = scheduleSheetMetrics()
        })
    }

    private fun setupBottomSheet() {
        sheetBehavior = BottomSheetBehavior.from(sheet).apply {
            isHideable = false
            isFitToContents = false
            skipCollapsed = false
            isDraggable = true
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    val next = when (newState) {
                        BottomSheetBehavior.STATE_COLLAPSED -> RouteDetailSheetDetent.SUMMARY
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> RouteDetailSheetDetent.HALF
                        BottomSheetBehavior.STATE_EXPANDED -> RouteDetailSheetDetent.FULL
                        else -> null
                    } ?: return
                    updateDetentUi(next)
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    updateMapPadding()
                }
            })
        }
        sheetHandle.setOnClickListener { applyDetent(RouteDetailSheetPolicy.onHandleClick(detent)) }
        sheet.post {
            scheduleSheetMetrics()
            applyDetent(detent)
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            statusBarInset = status.top
            navigationBarInset = navigation.bottom
            updateTopMargin(floatingBack, dimension(R.dimen.route_map_edge_margin) + statusBarInset)
            updateTopMargin(mapControls, dimension(R.dimen.route_map_controls_top_margin) + statusBarInset)
            updateMapPadding()
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupMap(savedMapState: Bundle?) {
        mapView.onCreate(savedMapState)
        if (!RouteDetailRuntime.mapsAvailabilityChecker(this)) {
            onMapUnavailable()
            return
        }
        runCatching {
            mapView.getMapAsync { map ->
                if (destroyed) return@getMapAsync
                mapReady = true
                dispatch(RouteDetailPageEvent.MapReady(pageGeneration, pageState.mapGeneration + 1))
                renderer = GoogleRouteMapRenderer(this, map, onMarkerSelected = ::onMapMarkerSelected).also {
                    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    it.setDarkMode(night)
                }
                map.setOnMapLoadedCallback {
                    baseMapLoaded = true
                    mainHandler.removeCallbacks(mapLoadTimeout)
                    if (mapUnavailable) onMapRecovered()
                }
                map.setOnCameraIdleListener {
                    saveCamera(map)
                    renderer?.onCameraIdle()
                }
                map.setOnCameraMoveStartedListener { reason ->
                    val origin = if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                        RouteDetailCameraMoveOrigin.GESTURE
                    } else {
                        RouteDetailCameraMoveOrigin.PROGRAMMATIC
                    }
                    val updatedOwner = RouteDetailCameraPolicy.ownerAfterMoveStarted(cameraOwner, origin)
                    if (updatedOwner != cameraOwner) {
                        cameraOwner = updatedOwner
                        RouteDetailDiagnostics.record(
                            RouteDetailDiagnosticEvent(
                                category = "camera",
                                action = "owner_changed",
                                reason = cameraOwner.name
                            )
                        )
                        updateInteractionState()
                    }
                }
                restoreCamera(map)
                renderMap()
                updateMyLocationLayer()
                updateMapPadding()
                scheduleMapLoadTimeout()
            }
        }.onFailure { onMapUnavailable() }
    }

    private fun onMapUnavailable() {
        mapUnavailable = true
        if (::pageState.isInitialized) {
            dispatch(RouteDetailPageEvent.MapFailed(pageGeneration, pageState.mapGeneration, "map_unavailable"))
        }
        mapError.visibility = View.VISIBLE
        sheetMapError.visibility = View.VISIBLE
        findViewById<View>(R.id.routeDetailMapControls).visibility = View.GONE
        sheet.post { applyDetent(RouteDetailSheetDetent.FULL) }
    }

    private fun onMapRecovered() {
        mapUnavailable = false
        dispatch(RouteDetailPageEvent.MapReady(pageGeneration, pageState.mapGeneration + 1))
        mapError.visibility = View.GONE
        sheetMapError.visibility = View.GONE
        findViewById<View>(R.id.routeDetailMapControls).visibility = View.VISIBLE
        updateMapPadding()
    }

    private fun scheduleMapLoadTimeout() {
        mainHandler.removeCallbacks(mapLoadTimeout)
        if (foreground && mapReady && !baseMapLoaded) {
            mainHandler.postDelayed(mapLoadTimeout, MAP_LOAD_TIMEOUT_MILLIS)
        }
    }

    private fun loadDetail() {
        if (!detailAutoRefreshController.canStartExternalQuery()) return
        detailAutoRefreshController.setExternalBusy(true)
        val requestGeneration = ++detailGeneration
        dispatch(RouteDetailPageEvent.DetailStarted(pageGeneration, requestGeneration))
        if (detail != null) renderDetail()
        val languageVersion = AppLanguageRuntime.snapshot().version
        val route = args.toRoute()
        val query = route.routeDetailQuery ?: run {
            detailAutoRefreshController.setExternalBusy(false)
            return
        }
        detailRequestHandle?.cancel()
        detailRequestHandle = RouteDetailRuntime.detailRequestCoordinator.request(
            key = RouteDetailRequestIdentity.from(query),
            work = { repository.loadRouteDetail(route) }
        ) { result ->
            mainHandler.post {
                if (!isCurrentLanguage(languageVersion)) return@post
                result.onSuccess {
                    dispatch(RouteDetailPageEvent.DetailSucceeded(pageGeneration, requestGeneration, it))
                    renderDetail()
                    validateLoadedGeometryAgainstDetail()
                    detailAutoRefreshController.setExternalBusy(false)
                    recordDetailAutoRefreshBaselineIfNeeded()
                }.onFailure {
                    dispatch(
                        RouteDetailPageEvent.DetailFailed(
                            pageGeneration,
                            requestGeneration,
                            it::class.java.simpleName
                        )
                    )
                    if (detail != null) {
                        renderDetail()
                    } else {
                        showLaunchSummary(loading = false, failed = true)
                        clearPendingSummaryTarget(announceUnavailable = true)
                    }
                    renderMap()
                    detailAutoRefreshController.setExternalBusy(false)
                    updateDetailAutoRefreshEligibility()
                }
            }
        }
    }

    private fun retryDetail() {
        if (!detailAutoRefreshController.canStartExternalQuery()) return
        showLaunchSummary(loading = true)
        walkingViewModel.retry()
        loadDetail()
    }

    private fun loadGeometry(onlyKeys: Set<RouteGeometryKey>? = null, manualRetry: Boolean = false) {
        val query = args.routeDetailQuery ?: return
        if (manualRetry && onlyKeys != null) geometryCoordinator.beginManualRetry(onlyKeys)
        val requests = query.plan.legs.mapIndexedNotNull { index, leg ->
            val key = RouteGeometryKey(leg.routeVariant, leg.boardingSeq, leg.alightingSeq)
            if (!key.isValid || (onlyKeys != null && key !in onlyKeys)) return@mapIndexedNotNull null
            geometryRequest(key, index)
        }
        val requestGeneration = if (onlyKeys == null) ++geometryGeneration else geometryGeneration
        val languageVersion = AppLanguageRuntime.snapshot().version
        val domainGenerations = requests.associate { request ->
            request.key to ((pageState.geometryGenerations[request.key] ?: 0) + 1)
        }
        domainGenerations.forEach { (key, generation) ->
            geometryCoordinator.beginGeneration(key, generation)
            dispatch(RouteDetailPageEvent.GeometryStarted(pageGeneration, key, generation))
        }
        geometryPendingCount = geometryCoordinator.loadingCount()
        if (manualRetry && onlyKeys != null) failedGeometryKeys.removeAll(onlyKeys)
        val handle = geometryRepository.loadGeometries(requests) { request, result ->
            mainHandler.post {
                if (!isCurrentGeometry(requestGeneration, languageVersion)) return@post
                result.onSuccess { segment ->
                    val generation = domainGenerations.getValue(request.key)
                    val validationRequest = geometryRequestForKey(request.key)
                    val endpointsAvailable = validationRequest.boardingCoordinate != null &&
                        validationRequest.alightingCoordinate != null
                    if (!endpointsAvailable) {
                        geometryCoordinator.onCandidate(
                            request.key,
                            generation,
                            segment,
                            endpointsAvailable = false
                        )
                    } else {
                        geometryRepository.validateGeometry(validationRequest, segment)
                            .onSuccess { validated ->
                                geometryCoordinator.onCandidate(
                                    request.key,
                                    generation,
                                    validated,
                                    endpointsAvailable = true
                                )?.let { publishable ->
                                    dispatch(
                                        RouteDetailPageEvent.GeometrySucceeded(
                                            pageGeneration,
                                            request.key,
                                            generation,
                                            publishable
                                        )
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                handleGeometryFailure(
                                    request.key,
                                    generation,
                                    throwable,
                                    requestGeneration,
                                    languageVersion
                                )
                            }
                    }
                }.onFailure { throwable ->
                    handleGeometryFailure(
                        request.key,
                        domainGenerations.getValue(request.key),
                        throwable,
                        requestGeneration,
                        languageVersion
                    )
                }
                syncGeometryFailureState()
                renderMap()
            }
        }
        geometryHandles += handle
    }

    private fun handleGeometryFailure(
        key: RouteGeometryKey,
        domainGeneration: Int,
        throwable: Throwable,
        requestGeneration: Int,
        languageVersion: Long
    ) {
        dispatch(
            RouteDetailPageEvent.GeometryFailed(
                pageGeneration,
                key,
                domainGeneration,
                throwable::class.java.simpleName
            )
        )
        if (
            geometryCoordinator.onFailure(
                key,
                domainGeneration,
                throwable,
                allowAutoRetry = foreground
            ) == RouteGeometryRetryDecision.AUTO_RETRY
        ) {
            mainHandler.postDelayed(
                {
                    if (!isCurrentGeometry(requestGeneration, languageVersion)) return@postDelayed
                    if (foreground) {
                        loadGeometry(setOf(key))
                    } else {
                        geometryCoordinator.onFailure(
                            key,
                            domainGeneration,
                            throwable,
                            allowAutoRetry = false
                        )
                        syncGeometryFailureState()
                    }
                },
                GEOMETRY_RETRY_BACKOFF_MILLIS
            )
        }
    }

    private fun geometryRequest(key: RouteGeometryKey, legIndex: Int): RouteGeometryRequest {
        val detailLeg = detail?.legs?.getOrNull(legIndex)
        return RouteGeometryRequest(
            key = key,
            boardingCoordinate = detailLeg?.boardingStop?.let {
                RouteGeometryCoordinate(it.latitude, it.longitude)
            },
            alightingCoordinate = detailLeg?.alightingStop?.let {
                RouteGeometryCoordinate(it.latitude, it.longitude)
            }
        )
    }

    private fun geometryRequestForKey(key: RouteGeometryKey): RouteGeometryRequest {
        val legIndex = args.routeDetailQuery?.plan?.legs.orEmpty().indexOfFirst { leg ->
            leg.routeVariant == key.routeVariant &&
                leg.boardingSeq == key.boardingSeq &&
                leg.alightingSeq == key.alightingSeq
        }
        return geometryRequest(key, legIndex)
    }

    private fun validateLoadedGeometryAgainstDetail() {
        pageState.geometryGenerations.forEach { (key, generation) ->
            val candidate = geometryCoordinator.candidate(key, generation) ?: return@forEach
            geometryRepository.validateGeometry(geometryRequestForKey(key), candidate)
                .onSuccess {
                    geometryCoordinator.onValidated(key, generation)?.let { publishable ->
                        dispatch(
                            RouteDetailPageEvent.GeometrySucceeded(
                                pageGeneration,
                                key,
                                generation,
                                publishable
                            )
                        )
                    }
                }
                .onFailure { throwable ->
                    dispatch(
                        RouteDetailPageEvent.GeometryFailed(
                            pageGeneration,
                            key,
                            generation,
                            throwable::class.java.simpleName
                        )
                    )
                    geometryCoordinator.onFailure(
                        key,
                        generation,
                        throwable,
                        allowAutoRetry = false
                    )
                }
        }
        syncGeometryFailureState()
        renderMap()
    }

    private fun syncGeometryFailureState() {
        failedGeometryKeys.clear()
        failedGeometryKeys += geometryCoordinator.failedKeys()
        geometryPendingCount = geometryCoordinator.loadingCount()
        if (foreground && geometryPendingCount == 0 && failedGeometryKeys.isNotEmpty()) {
            showMessage(R.string.route_map_geometry_partial, R.string.action_retry) {
                loadGeometry(onlyKeys = failedGeometryKeys.toSet(), manualRetry = true)
            }
        }
    }

    private fun refreshFirstLegEta() {
        val query = args.firstLegEtaQuery ?: return
        val requestGeneration = ++etaGeneration
        dispatch(RouteDetailPageEvent.EtaStarted(pageGeneration, requestGeneration))
        val languageVersion = AppLanguageRuntime.snapshot().version
        executor.execute {
            val state = runCatching { RouteDetailRuntime.etaResolver(query) }
                .getOrElse { WaitTimeState.Unavailable(EtaUnavailableReason.UNEXPECTED_ERROR) }
            mainHandler.post {
                if (
                    !foreground ||
                    !isCurrentEta(requestGeneration, languageVersion)
                ) return@post
                dispatch(RouteDetailPageEvent.EtaSucceeded(pageGeneration, requestGeneration, state))
                if (state !is WaitTimeState.Unavailable) lastEtaSuccessMillis = System.currentTimeMillis()
                if (detail != null) renderDetail() else showLaunchSummary(detailLoading, detailFailed)
            }
        }
    }

    private fun recordDetailAutoRefreshBaselineIfNeeded() {
        if (detailAutoRefreshBaselineRecorded || detail == null) return
        detailAutoRefreshBaselineRecorded = true
        detailAutoRefreshController.recordSuccessfulBaseline()
        updateDetailAutoRefreshEligibility()
    }

    private fun updateDetailAutoRefreshEligibility() {
        if (!::autoRefreshSettingsStore.isInitialized || !::args.isInitialized) return
        detailAutoRefreshController.setInterval(autoRefreshSettingsStore.getInterval())
        detailAutoRefreshController.setEligible(
            foreground &&
                args.routeDetailQuery != null &&
                detail != null &&
                detailAutoRefreshBaselineRecorded
        )
    }

    private fun startDetailAutomaticRefresh(generation: Int) {
        val currentDetail = detail
        val query = args.routeDetailQuery
        if (!foreground || currentDetail == null || query == null) {
            detailAutoRefreshController.completeAutomatic(generation, success = false)
            return
        }
        detailRefreshCycleCoordinator.begin(generation)
        val languageVersion = AppLanguageRuntime.snapshot().version

        val detailDomainGeneration = ++detailGeneration
        dispatch(RouteDetailPageEvent.DetailStarted(pageGeneration, detailDomainGeneration))
        renderDetail(updateWalking = false)
        autoDetailRequestHandle?.cancel()
        autoDetailRequestHandle = RouteDetailRuntime.detailRequestCoordinator.request(
            key = RouteDetailRequestIdentity.from(query),
            work = { repository.loadRouteDetail(args.toRoute()) }
        ) { result ->
            mainHandler.post {
                if (!isActiveDetailAutoRefresh(generation, languageVersion)) return@post
                val merged = result.getOrNull()?.let { candidate ->
                    detail?.let { stable -> RouteDetailDynamicMerger.merge(stable, candidate) }
                }
                if (merged != null) {
                    dispatch(
                        RouteDetailPageEvent.DetailSucceeded(
                            pageGeneration,
                            detailDomainGeneration,
                            merged
                        )
                    )
                    renderDetail(updateWalking = false)
                    finishDetailAutoDomain(
                        generation,
                        RouteDetailRefreshDomain.DYNAMIC_DETAIL,
                        success = true
                    )
                } else {
                    dispatch(
                        RouteDetailPageEvent.DetailFailed(
                            pageGeneration,
                            detailDomainGeneration,
                            "structure_mismatch_or_request_failed"
                        )
                    )
                    renderDetail(updateWalking = false)
                    finishDetailAutoDomain(
                        generation,
                        RouteDetailRefreshDomain.DYNAMIC_DETAIL,
                        success = false
                    )
                }
            }
        }

        val etaQuery = args.firstLegEtaQuery
        if (etaQuery == null) {
            finishDetailAutoDomain(
                generation,
                RouteDetailRefreshDomain.FIRST_LEG_ETA,
                success = false
            )
        } else {
            val etaDomainGeneration = ++etaGeneration
            dispatch(RouteDetailPageEvent.EtaStarted(pageGeneration, etaDomainGeneration))
            executor.execute {
                val state = runCatching { RouteDetailRuntime.etaResolver(etaQuery) }
                    .getOrElse { WaitTimeState.Unavailable(EtaUnavailableReason.UNEXPECTED_ERROR) }
                mainHandler.post {
                    if (!isActiveDetailAutoRefresh(generation, languageVersion)) return@post
                    val success = state !is WaitTimeState.Unavailable
                    if (success) {
                        dispatch(
                            RouteDetailPageEvent.EtaSucceeded(
                                pageGeneration,
                                etaDomainGeneration,
                                state
                            )
                        )
                        lastEtaSuccessMillis = System.currentTimeMillis()
                    } else {
                        dispatch(
                            RouteDetailPageEvent.EtaFailed(
                                pageGeneration,
                                etaDomainGeneration,
                                "eta_unavailable"
                            )
                        )
                    }
                    renderDetail(updateWalking = false)
                    finishDetailAutoDomain(
                        generation,
                        RouteDetailRefreshDomain.FIRST_LEG_ETA,
                        success
                    )
                }
            }
        }
    }

    private fun finishDetailAutoDomain(
        generation: Int,
        domain: RouteDetailRefreshDomain,
        success: Boolean
    ) {
        val result = detailRefreshCycleCoordinator.finish(generation, domain, success)
        if (!result.accepted || !result.finished) return
        detailAutoRefreshController.completeAutomatic(
            generation,
            success = result.anyDomainSucceeded
        )
    }

    private fun isActiveDetailAutoRefresh(generation: Int, languageVersion: Long): Boolean =
        foreground &&
            isCurrentLanguage(languageVersion) &&
            (detailAutoRefreshController.state as? ForegroundAutoRefreshState.Refreshing)
                ?.generation == generation

    private fun cancelActiveDetailAutoRefresh() {
        val active = detailAutoRefreshController.state as? ForegroundAutoRefreshState.Refreshing
            ?: return
        autoDetailRequestHandle?.cancel()
        autoDetailRequestHandle = null
        detailRefreshCycleCoordinator.invalidate()
        pageState.detail.valueOrNull()?.let { stable ->
            dispatch(
                RouteDetailPageEvent.DetailSucceeded(
                    pageGeneration,
                    pageState.detailGeneration,
                    stable
                )
            )
        }
        pageState.eta.valueOrNull()?.let { stable ->
            dispatch(
                RouteDetailPageEvent.EtaSucceeded(
                    pageGeneration,
                    pageState.etaGeneration,
                    stable
                )
            )
        }
        detailAutoRefreshController.completeAutomatic(active.generation, success = false)
        if (detail != null) renderDetail(updateWalking = false)
    }

    private fun renderDetail(updateWalking: Boolean = true) {
        val value = detail ?: return
        if (updateWalking) walkingViewModel.updateDetail(value)
        val dynamicStatus = when (pageState.detail) {
            is ProgressiveValue.Refreshing -> RouteDynamicDetailStatus.REFRESHING
            is ProgressiveValue.Failure -> RouteDynamicDetailStatus.STALE_AFTER_ERROR
            else -> RouteDynamicDetailStatus.CURRENT
        }
        adapter.submitList(
            RouteDetailUiFormatter.items(
                value,
                expandedLegIndexes,
                waitTimeState,
                dynamicStatus,
                pageState.walkingSegments
            )
        ) {
            adapter.selectTimelineItem(selectedTimelineId)
            restoreListPositionIfNeeded()
            scheduleSheetMetrics()
            resolvePendingSummaryTarget()
        }
        renderMap()
    }

    private fun showLaunchSummary(loading: Boolean, failed: Boolean = false) {
        val items = mutableListOf<RouteDetailUiItem>(
            RouteDetailUiFormatter.launchSummary(
                args = args,
                firstLegEta = waitTimeState,
                rideStopCount = if (failed) {
                    RideStopCountState.Unavailable
                } else {
                    RideStopCountState.Loading
                }
            )
        )
        if (loading) items += RouteDetailUiItem.Loading
        if (failed) items += RouteDetailUiItem.Error
        adapter.submitList(items) {
            restoreListPositionIfNeeded()
            scheduleSheetMetrics()
            resolvePendingSummaryTarget()
        }
    }

    private fun renderMap() {
        val routePlan = args.routeDetailQuery?.plan?.legs.orEmpty()
        val presentation = RouteMapPresentationBuilder.build(
            detail = detail,
            queryOrigin = args.queryOrigin,
            queryDestination = args.queryDestination,
            geometries = geometries,
            selectedMarkerId = selectedMarkerId,
            routePlan = routePlan,
            walkingSegments = pageState.walkingSegments
        )
        lastPresentation = presentation
        RouteDetailRuntime.presentationObserver(presentation)
        renderer?.render(presentation)
        updateCsdiAttributionVisibility()
        if (
            mapReady &&
            presentation.boundsPoints.isNotEmpty() &&
            RouteDetailCameraPolicy.shouldAutoFit(
                hasReliableStructure = detail != null,
                owner = cameraOwner,
                initialFitDone = initialFitDone,
                geometryStates = pageState.geometries
            )
        ) {
            mapView.post {
                if (
                    RouteDetailCameraPolicy.shouldAutoFit(
                        hasReliableStructure = detail != null,
                        owner = cameraOwner,
                        initialFitDone = initialFitDone,
                        geometryStates = pageState.geometries
                    ) &&
                    renderer?.fitOverview(true, dimension(R.dimen.route_map_camera_padding)) == true
                ) {
                    initialFitDone = true
                    updateInteractionState()
                }
            }
        }
    }

    private fun onMapMarkerSelected(stableId: String) {
        val marker = lastPresentation?.markers?.firstOrNull { it.stableId == stableId } ?: return
        selectedMarkerId = marker.stableId
        selectedTimelineId = marker.timelineStopIds.firstOrNull()
        expandedLegIndexes.clear()
        expandedLegIndexes.addAll(marker.legIndexes)
        updateInteractionState()
        if (detent == RouteDetailSheetDetent.SUMMARY) applyDetent(RouteDetailSheetDetent.HALF)
        renderDetail()
        renderer?.focusMarker(marker.stableId)
        scrollToSelectedTimelineItem()
    }

    private fun onTimelineStopSelected(stableId: String) {
        val marker = lastPresentation?.markers?.firstOrNull {
            it.stableId == stableId || stableId in it.timelineStopIds
        } ?: return
        selectedMarkerId = marker.stableId
        selectedTimelineId = stableId
        updateInteractionState()
        adapter.selectTimelineItem(stableId)
        renderMap()
        applyDetent(RouteDetailSheetDetent.HALF)
        renderer?.focusMarker(marker.stableId)
    }

    private fun onSummarySegmentSelected(segment: RouteSummarySegment) {
        applyDetent(RouteDetailSheetDetent.FULL)
        pendingSummaryTarget = PendingSummaryTarget(
            pageGeneration = pageGeneration,
            structureIdentity = currentStructureIdentity(),
            targetId = segment.detailTargetId
        )
        if (!resolvePendingSummaryTarget() && detailFailed) {
            clearPendingSummaryTarget(announceUnavailable = true)
        }
    }

    private fun resolvePendingSummaryTarget(): Boolean {
        val pending = pendingSummaryTarget ?: return false
        if (
            pending.pageGeneration != pageGeneration ||
            pending.structureIdentity != currentStructureIdentity()
        ) {
            clearPendingSummaryTarget(announceUnavailable = false)
            return false
        }
        val position = adapter.currentList.indexOfFirst { it.stableId == pending.targetId }
        if (position < 0) return false
        pendingSummaryTarget = null
        listLayoutManager.scrollToPositionWithOffset(
            position,
            dimension(R.dimen.route_detail_timeline_focus_offset)
        )
        list.post {
            adapter.selectTimelineItem(pending.targetId)
            list.findViewHolderForAdapterPosition(position)?.itemView?.apply {
                requestFocus()
                announceForAccessibility(
                    contentDescription ?: summaryTargetAnnouncement(pending.targetId)
                )
            }
            summaryHighlightRunnable?.let(mainHandler::removeCallbacks)
            val clear = Runnable { adapter.selectTimelineItem(selectedTimelineId) }
            summaryHighlightRunnable = clear
            mainHandler.postDelayed(clear, SUMMARY_TARGET_HIGHLIGHT_MILLIS)
        }
        return true
    }

    private fun currentStructureIdentity(): Int = args.routeDetailQuery
        ?.let(RouteDetailRequestIdentity::from)
        ?.hashCode()
        ?: args.routeName.hashCode()

    private fun clearPendingSummaryTarget(announceUnavailable: Boolean) {
        if (pendingSummaryTarget == null) return
        pendingSummaryTarget = null
        if (announceUnavailable && foreground) {
            root.announceForAccessibility(getString(R.string.route_detail_target_unavailable))
        }
    }

    private fun summaryTargetAnnouncement(targetId: String): String = when {
        targetId.startsWith("leg-") -> getString(R.string.route_detail_bus_segment)
        targetId.startsWith("transfer-") -> getString(R.string.route_detail_same_stop_transfer)
        else -> getString(R.string.route_detail_walk_unknown)
    }

    private fun scrollToSelectedTimelineItem() {
        val stableId = selectedTimelineId ?: return
        list.post {
            val position = adapter.currentList.indexOfFirst { it.stableId == stableId }
            if (position >= 0) listLayoutManager.scrollToPositionWithOffset(position, dimension(R.dimen.route_detail_timeline_focus_offset))
        }
    }

    private fun toggleLeg(index: Int) {
        if (!expandedLegIndexes.add(index)) expandedLegIndexes.remove(index)
        updateInteractionState()
        renderDetail()
    }

    private fun applyDetent(value: RouteDetailSheetDetent) {
        val state = when (value) {
            RouteDetailSheetDetent.SUMMARY -> BottomSheetBehavior.STATE_COLLAPSED
            RouteDetailSheetDetent.HALF -> BottomSheetBehavior.STATE_HALF_EXPANDED
            RouteDetailSheetDetent.FULL -> BottomSheetBehavior.STATE_EXPANDED
        }
        sheetBehavior.state = state
        updateDetentUi(value)
    }

    private fun updateDetentUi(value: RouteDetailSheetDetent) {
        detent = value
        val full = value == RouteDetailSheetDetent.FULL
        floatingBack.visibility = if (full) View.GONE else View.VISIBLE
        sheetContent.setPadding(0, if (full) statusBarInset else 0, 0, 0)
        sheetHandle.contentDescription = getString(
            when (value) {
                RouteDetailSheetDetent.SUMMARY -> R.string.route_detail_sheet_handle_summary
                RouteDetailSheetDetent.HALF -> R.string.route_detail_sheet_handle_half
                RouteDetailSheetDetent.FULL -> R.string.route_detail_sheet_handle_full
            }
        )
        if (value == RouteDetailSheetDetent.SUMMARY) listLayoutManager.scrollToPositionWithOffset(0, 0)
        updateCsdiAttributionVisibility()
        updateMapPadding()
    }

    private fun scheduleSheetMetrics() {
        if (!::list.isInitialized) return
        list.post {
            if (root.height <= 0) return@post
            val summaryRows = adapter.currentList.takeWhile {
                it is RouteDetailUiItem.Summary || it is RouteDetailUiItem.Loading || it is RouteDetailUiItem.Error
            }.indices
            val summaryHeight = summaryRows.sumOf { position ->
                listLayoutManager.findViewByPosition(position)?.measuredHeight ?: 0
            } + sheetHandle.measuredHeight
            val metrics = RouteDetailSheetPolicy.metrics(root.height, summaryHeight)
            sheetBehavior.peekHeight = metrics.summaryHeight
            sheetBehavior.halfExpandedRatio = metrics.halfExpandedRatio.coerceIn(0.01f, 0.99f)
            updateMapPadding()
        }
    }

    private fun updateMapPadding() {
        if (!::sheet.isInitialized || root.height <= 0) return
        sheet.post {
            val sheetVisibleHeight = (root.height - sheet.top).coerceAtLeast(0)
            val bottomPadding = (sheetVisibleHeight + navigationBarInset)
                .coerceAtMost((root.height - statusBarInset - 1).coerceAtLeast(0))
            if (::csdiAttribution.isInitialized) {
                val params = csdiAttribution.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = sheetVisibleHeight + dimension(R.dimen.route_map_csdi_google_clearance)
                csdiAttribution.layoutParams = params
                csdiAttribution.post { updateRendererPadding(bottomPadding) }
            } else {
                updateRendererPadding(bottomPadding)
            }
        }
    }

    private fun updateRendererPadding(bottomPadding: Int) {
        if (!::mapView.isInitialized || mapView.width <= 0 || mapView.height <= 0) return
        val reservedRects = if (
            ::csdiAttribution.isInitialized &&
            csdiAttribution.visibility == View.VISIBLE &&
            csdiAttribution.width > 0 &&
            csdiAttribution.height > 0
        ) {
            val mapLocation = IntArray(2)
            val attributionLocation = IntArray(2)
            mapView.getLocationOnScreen(mapLocation)
            csdiAttribution.getLocationOnScreen(attributionLocation)
            listOf(
                RouteMapLabelRect(
                    left = (attributionLocation[0] - mapLocation[0]).toFloat(),
                    top = (attributionLocation[1] - mapLocation[1]).toFloat(),
                    right = (attributionLocation[0] - mapLocation[0] + csdiAttribution.width).toFloat(),
                    bottom = (attributionLocation[1] - mapLocation[1] + csdiAttribution.height).toFloat()
                )
            )
        } else {
            emptyList()
        }
        renderer?.updatePadding(
            left = 0,
            top = statusBarInset,
            right = 0,
            bottom = bottomPadding,
            viewportWidth = mapView.width,
            viewportHeight = mapView.height,
            reservedLabelRects = reservedRects
        )
    }

    private fun updateCsdiAttributionVisibility() {
        if (!::csdiAttribution.isInitialized) return
        val nextVisibility = if (
            detent != RouteDetailSheetDetent.FULL &&
            !mapUnavailable &&
            renderer?.hasRenderedWalkingPaths() == true
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        if (csdiAttribution.visibility != nextVisibility) {
            csdiAttribution.visibility = nextVisibility
            updateMapPadding()
        }
    }

    private fun showCsdiNotice() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.route_map_csdi_notice_title)
            .setMessage(R.string.route_map_csdi_notice_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.route_map_csdi_open_source) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CSDI_PEDESTRIAN_SOURCE_URL)))
            }
            .show()
    }

    private fun onLocationClicked() {
        if (!LocationPermissionUtils.hasForegroundLocationPermission(this)) {
            repeatedPermissionRequest = locationPermissionRequestedBefore
            locationPermissionRequestedBefore = true
            requestLocationPermission.launch(LocationPermissionUtils.permissions)
            return
        }
        if (!isSystemLocationEnabled()) {
            showMessage(R.string.route_map_location_disabled, R.string.settings) {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            return
        }
        enableMyLocationAndFocus()
    }

    private fun enableMyLocationAndFocus() {
        if (!foreground || !isSystemLocationEnabled()) return
        renderer?.setMyLocationEnabled(true)
        locationCoordinator.getCurrentLocation { result ->
            if (!foreground || destroyed) return@getCurrentLocation
            when (result) {
                is CurrentLocationResult.Success -> {
                    val coordinate = RouteMapCoordinate(result.snapshot.latitude, result.snapshot.longitude)
                    cameraLatitude = coordinate.latitude
                    cameraLongitude = coordinate.longitude
                    cameraZoom = LOCATION_FOCUS_ZOOM
                    renderer?.focusCoordinate(coordinate, LOCATION_FOCUS_ZOOM)
                }
                CurrentLocationResult.NoPermission -> showMessage(R.string.route_map_location_permission_denied)
                CurrentLocationResult.Timeout,
                CurrentLocationResult.Unavailable -> showMessage(R.string.route_map_location_unavailable)
            }
        }
    }

    private fun updateMyLocationLayer() {
        val enabled = foreground && LocationPermissionUtils.hasForegroundLocationPermission(this) && isSystemLocationEnabled()
        renderer?.setMyLocationEnabled(enabled)
    }

    private fun isSystemLocationEnabled(): Boolean {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.isLocationEnabled
        } else {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    private fun showMessage(messageRes: Int, actionRes: Int? = null, action: (() -> Unit)? = null) {
        Snackbar.make(sheet, messageRes, Snackbar.LENGTH_LONG).apply {
            if (actionRes != null && action != null) setAction(actionRes) { action() }
            view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = 3
        }.show()
    }

    private fun saveCamera(map: GoogleMap) {
        val position = map.cameraPosition
        cameraLatitude = position.target.latitude
        cameraLongitude = position.target.longitude
        cameraZoom = position.zoom
        cameraBearing = position.bearing
        cameraTilt = position.tilt
        updateInteractionState()
    }

    private fun restoreCamera(map: GoogleMap) {
        val latitude = cameraLatitude ?: return
        val longitude = cameraLongitude ?: return
        val zoom = cameraZoom ?: return
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(latitude, longitude))
                    .zoom(zoom)
                    .bearing(cameraBearing)
                    .tilt(cameraTilt)
                    .build()
            )
        )
    }

    private fun restorePageState(state: Bundle?) {
        state ?: return
        state.getIntArray(STATE_EXPANDED)?.forEach(expandedLegIndexes::add)
        detent = state.getString(STATE_DETENT)?.let {
            runCatching { RouteDetailSheetDetent.valueOf(it) }.getOrNull()
        } ?: RouteDetailSheetDetent.SUMMARY
        selectedMarkerId = state.getString(STATE_SELECTED_MARKER)
        selectedTimelineId = state.getString(STATE_SELECTED_TIMELINE)
        initialFitDone = state.getBoolean(STATE_INITIAL_FIT)
        cameraOwner = state.getString(STATE_CAMERA_OWNER)?.let { value ->
            runCatching { RouteDetailCameraOwner.valueOf(value) }.getOrNull()
        } ?: RouteDetailCameraOwner.PAGE
        locationPermissionRequestedBefore = state.getBoolean(STATE_LOCATION_REQUESTED)
        if (state.containsKey(STATE_CAMERA_LATITUDE)) cameraLatitude = state.getDouble(STATE_CAMERA_LATITUDE)
        if (state.containsKey(STATE_CAMERA_LONGITUDE)) cameraLongitude = state.getDouble(STATE_CAMERA_LONGITUDE)
        if (state.containsKey(STATE_CAMERA_ZOOM)) cameraZoom = state.getFloat(STATE_CAMERA_ZOOM)
        cameraBearing = state.getFloat(STATE_CAMERA_BEARING, 0f)
        cameraTilt = state.getFloat(STATE_CAMERA_TILT, 0f)
        pendingListPosition = state.getInt(STATE_LIST_POSITION, RecyclerView.NO_POSITION)
        pendingListOffset = state.getInt(STATE_LIST_OFFSET, 0)
        updateInteractionState()
    }

    private fun updateInteractionState() {
        if (!::pageState.isInitialized) return
        dispatch(
            RouteDetailPageEvent.InteractionChanged(
                pageGeneration,
                RouteDetailInteractionState(
                    expandedLegIndexes = expandedLegIndexes.toSet(),
                    selectedMarkerId = selectedMarkerId,
                    selectedTimelineId = selectedTimelineId,
                    firstVisibleListPosition = pendingListPosition.takeIf { it != RecyclerView.NO_POSITION } ?: 0,
                    firstVisibleListOffset = pendingListOffset,
                    cameraSnapshot = if (cameraLatitude != null && cameraLongitude != null && cameraZoom != null) {
                        RouteDetailCameraSnapshot(
                            requireNotNull(cameraLatitude),
                            requireNotNull(cameraLongitude),
                            requireNotNull(cameraZoom),
                            cameraBearing,
                            cameraTilt
                        )
                    } else {
                        null
                    },
                    cameraOwner = cameraOwner,
                    initialFitDone = initialFitDone
                )
            )
        )
    }

    private fun restoreListPositionIfNeeded() {
        if (pendingListPosition == RecyclerView.NO_POSITION) return
        val position = pendingListPosition.coerceAtMost((adapter.itemCount - 1).coerceAtLeast(0))
        listLayoutManager.scrollToPositionWithOffset(position, pendingListOffset)
        pendingListPosition = RecyclerView.NO_POSITION
    }

    private fun dispatch(event: RouteDetailPageEvent) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Route detail events must be reduced on the main thread" }
        val reduced = RouteDetailPageReducer.reduce(pageState, event)
        if (reduced === pageState) return
        pageState = reduced
        syncPageStateFields()
    }

    private fun syncPageStateFields() {
        detail = pageState.detail.valueOrNull()
        waitTimeState = pageState.eta.valueOrNull() ?: args.waitTimeState
        geometries.clear()
        geometries.putAll(pageState.successfulGeometries)
    }

    private fun isCurrentDetail(requestGeneration: Int, languageVersion: Long): Boolean {
        return requestGeneration == detailGeneration && isCurrentLanguage(languageVersion)
    }

    private fun isCurrentGeometry(requestGeneration: Int, languageVersion: Long): Boolean {
        return requestGeneration == geometryGeneration && isCurrentLanguage(languageVersion)
    }

    private fun isCurrentEta(requestGeneration: Int, languageVersion: Long): Boolean {
        return requestGeneration == etaGeneration && isCurrentLanguage(languageVersion)
    }

    private fun isCurrentLanguage(languageVersion: Long): Boolean {
        return languageVersion == AppLanguageRuntime.snapshot().version && !isFinishing && !destroyed
    }

    private fun RouteDetailLaunchArgs.toRoute(): BusRouteOption {
        return BusRouteOption(
            routeName = routeName,
            routeSegments = routeSegments,
            priceHkd = priceHkd,
            durationMinutes = durationMinutes,
            arrivalMinutes = (waitTimeState as? WaitTimeState.Available)?.minutes ?: durationMinutes,
            transferCount = (routeSegments.size - 1).coerceAtLeast(0),
            walkingDistanceMeters = walkingDistanceMeters,
            waitTimeState = waitTimeState,
            firstLegEtaQuery = firstLegEtaQuery,
            routeDetailQuery = routeDetailQuery
        )
    }

    private fun updateTopMargin(view: View, value: Int) {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.topMargin == value) return
        params.topMargin = value
        view.layoutParams = params
    }

    private fun dimension(resource: Int): Int = resources.getDimensionPixelSize(resource)

    private data class PendingSummaryTarget(
        val pageGeneration: Long,
        val structureIdentity: Int,
        val targetId: String
    )

    private companion object {
        const val STATE_EXPANDED = "route_detail.expanded"
        const val STATE_DETENT = "route_detail.detent"
        const val STATE_SELECTED_MARKER = "route_detail.selected_marker"
        const val STATE_SELECTED_TIMELINE = "route_detail.selected_timeline"
        const val STATE_INITIAL_FIT = "route_detail.initial_fit"
        const val STATE_LOCATION_REQUESTED = "route_detail.location_requested"
        const val STATE_LIST_POSITION = "route_detail.list_position"
        const val STATE_LIST_OFFSET = "route_detail.list_offset"
        const val STATE_CAMERA_LATITUDE = "route_detail.camera_latitude"
        const val STATE_CAMERA_LONGITUDE = "route_detail.camera_longitude"
        const val STATE_CAMERA_ZOOM = "route_detail.camera_zoom"
        const val STATE_CAMERA_BEARING = "route_detail.camera_bearing"
        const val STATE_CAMERA_TILT = "route_detail.camera_tilt"
        const val STATE_CAMERA_OWNER = "route_detail.camera_owner"
        const val STATE_MAP = "route_detail.map"
        const val MAP_LOAD_TIMEOUT_MILLIS = 15_000L
        const val GEOMETRY_RETRY_BACKOFF_MILLIS = 250L
        const val LOCATION_FOCUS_ZOOM = 16f
        const val SUMMARY_TARGET_HIGHLIGHT_MILLIS = 900L
        const val CSDI_PEDESTRIAN_SOURCE_URL =
            "https://portal.csdi.gov.hk/csdi-webpage/apidoc/3d-pedestrian-route-search"
    }
}

object RouteDetailNavigator {
    fun open(
        context: Context,
        route: BusRouteOption,
        queryOrigin: com.golink.busiscoming.data.model.Place? = null,
        queryDestination: com.golink.busiscoming.data.model.Place? = null
    ) {
        context.startActivity(
            Intent(context, RouteDetailActivity::class.java).apply {
                putExtras(RouteDetailLaunchArgs.fromRoute(route, queryOrigin, queryDestination).toBundle())
            }
        )
    }
}

object RouteDetailRuntime {
    private val pageGeneration = AtomicLong()
    @Volatile var cacheOwner: RouteDetailCacheOwner = RouteDetailCacheOwner()
    private val defaultDetailRequestExecutor = Executors.newCachedThreadPool()
    private val defaultDetailRequestCoordinator =
        SingleFlightRequestCoordinator<RouteDetailRequestIdentity, RouteDetail>(defaultDetailRequestExecutor)
    private val defaultRepositoryFactory: () -> RouteDetailRepository = {
        CitybusRouteDetailRepository(cacheOwner = cacheOwner)
    }
    private val defaultGeometryRepository: RouteGeometryDataSource by lazy { CitybusRouteGeometryRepository() }
    private val defaultGeometryRepositoryFactory: () -> RouteGeometryDataSource = { defaultGeometryRepository }
    private val defaultEtaResolver: (FirstLegEtaQuery) -> WaitTimeState =
        { query -> CitybusFirstLegEtaService().resolveWaitTime(query) }
    private val defaultStopMapResolverFactory: () -> CitybusP2pStopMapResolver = {
        CitybusP2pStopMapResolver()
    }
    private val defaultPedestrianRuntime: PedestrianRouteRequestRuntime = PedestrianRouteProcessRuntime.shared
    private val defaultMapsAvailabilityChecker: (Context) -> Boolean = { context ->
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    private val defaultPresentationObserver: (RouteMapPresentation) -> Unit = {}

    @Volatile var repositoryFactory: () -> RouteDetailRepository = defaultRepositoryFactory
    @Volatile var detailRequestCoordinator = defaultDetailRequestCoordinator
    @Volatile var geometryRepositoryFactory: () -> RouteGeometryDataSource = defaultGeometryRepositoryFactory
    @Volatile var etaResolver: (FirstLegEtaQuery) -> WaitTimeState = defaultEtaResolver
    @Volatile var stopMapResolverFactory: () -> CitybusP2pStopMapResolver = defaultStopMapResolverFactory
    @Volatile var pedestrianRuntime: PedestrianRouteRequestRuntime = defaultPedestrianRuntime
    @Volatile var mapsAvailabilityChecker: (Context) -> Boolean = defaultMapsAvailabilityChecker
    @Volatile var presentationObserver: (RouteMapPresentation) -> Unit = defaultPresentationObserver

    fun nextPageGeneration(): Long = pageGeneration.incrementAndGet()

    fun reset() {
        cacheOwner = RouteDetailCacheOwner()
        repositoryFactory = defaultRepositoryFactory
        detailRequestCoordinator = defaultDetailRequestCoordinator
        geometryRepositoryFactory = defaultGeometryRepositoryFactory
        etaResolver = defaultEtaResolver
        stopMapResolverFactory = defaultStopMapResolverFactory
        pedestrianRuntime = defaultPedestrianRuntime
        mapsAvailabilityChecker = defaultMapsAvailabilityChecker
        presentationObserver = defaultPresentationObserver
    }
}
