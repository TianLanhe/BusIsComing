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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.localization.AppLanguageRuntime
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
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.CitybusRouteGeometryRepository
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.data.repository.RouteGeometryLoadHandle
import com.golink.busiscoming.data.repository.RouteGeometryRequest
import com.golink.busiscoming.data.repository.RouteGeometryDataSource
import com.golink.busiscoming.ui.common.applyStatusBarPadding
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteDetailActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newFixedThreadPool(3)
    private val repository by lazy { RouteDetailRuntime.repositoryFactory() }
    private val geometryRepository by lazy { RouteDetailRuntime.geometryRepositoryFactory() }
    private val locationCoordinator by lazy { CurrentLocationCoordinator(this) }
    private val expandedLegIndexes = linkedSetOf<Int>()
    private val geometries = linkedMapOf<RouteGeometryKey, RouteGeometrySegment>()
    private val failedGeometryKeys = linkedSetOf<RouteGeometryKey>()
    private lateinit var geometryCoordinator: RouteGeometryLoadCoordinator

    private lateinit var args: RouteDetailLaunchArgs
    private lateinit var adapter: RouteDetailAdapter
    private lateinit var list: RecyclerView
    private lateinit var listLayoutManager: LinearLayoutManager
    private lateinit var root: View
    private lateinit var sheet: MaterialCardView
    private lateinit var sheetBehavior: BottomSheetBehavior<MaterialCardView>
    private lateinit var sheetHandle: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var floatingBack: MaterialButton
    private lateinit var mapView: MapView
    private lateinit var mapControls: View
    private lateinit var mapError: TextView
    private lateinit var sheetMapError: TextView

    private var renderer: GoogleRouteMapRenderer? = null
    private var lastPresentation: RouteMapPresentation? = null
    private var detail: RouteDetail? = null
    private var detailLoading = false
    private var detailFailed = false
    private var waitTimeState: WaitTimeState = WaitTimeState.Loading
    private var detent = RouteDetailSheetDetent.SUMMARY
    private var selectedMarkerId: String? = null
    private var selectedTimelineId: String? = null
    private var detailGeneration = 0
    private var geometryGeneration = 0
    private var etaGeneration = 0
    private val geometryHandles = mutableListOf<RouteGeometryLoadHandle>()
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
    private var pendingListPosition = RecyclerView.NO_POSITION
    private var pendingListOffset = 0
    private var lastEtaSuccessMillis: Long? = null
    private var summaryTouchStartY = 0f
    private var summaryTouchStartedCollapsed = false

    private val mapLoadTimeout = Runnable {
        if (!destroyed && foreground && mapReady && !baseMapLoaded) onMapUnavailable()
    }

    private val etaTick = object : Runnable {
        override fun run() {
            if (!foreground) return
            refreshFirstLegEta()
        }
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
        val decoded = intent.extras?.let(RouteDetailLaunchArgs::fromBundle)
        if (decoded == null) {
            finish()
            return
        }
        args = decoded
        geometryCoordinator = RouteGeometryLoadCoordinator(
            args.routeDetailQuery?.plan?.legs.orEmpty().map { leg ->
                RouteGeometryKey(leg.routeVariant, leg.boardingSeq, leg.alightingSeq)
            }.filter { it.isValid }
        )
        waitTimeState = args.waitTimeState
        restorePageState(savedInstanceState)
        setContentView(R.layout.activity_route_detail)
        bindViews()
        setupTimeline()
        setupBottomSheet()
        setupInsets()
        setupMap(savedInstanceState?.getBundle(STATE_MAP))

        detailLoading = args.routeDetailQuery != null
        detailFailed = args.routeDetailQuery == null
        showLaunchSummary(loading = detailLoading, failed = detailFailed)
        if (args.routeDetailQuery == null) {
            showLaunchSummary(loading = false, failed = true)
        } else {
            loadDetail()
            loadGeometry()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
        foreground = true
        mainHandler.removeCallbacks(etaTick)
        if (RouteDetailEtaRefreshPolicy.shouldRefreshOnForeground(System.currentTimeMillis(), lastEtaSuccessMillis)) {
            refreshFirstLegEta()
        } else {
            mainHandler.postDelayed(etaTick, RouteDetailEtaRefreshPolicy.REFRESH_INTERVAL_MILLIS)
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
        etaGeneration += 1
        mainHandler.removeCallbacks(etaTick)
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
        outState.putBoolean(STATE_LOCATION_REQUESTED, locationPermissionRequestedBefore)
        cameraLatitude?.let { outState.putDouble(STATE_CAMERA_LATITUDE, it) }
        cameraLongitude?.let { outState.putDouble(STATE_CAMERA_LONGITUDE, it) }
        cameraZoom?.let { outState.putFloat(STATE_CAMERA_ZOOM, it) }
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
        detailGeneration += 1
        geometryGeneration += 1
        etaGeneration += 1
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
        sheetHandle = findViewById(R.id.routeDetailSheetHandle)
        toolbar = findViewById(R.id.routeDetailToolbar)
        floatingBack = findViewById(R.id.routeDetailFloatingBack)
        mapView = findViewById(R.id.routeDetailMap)
        mapControls = findViewById(R.id.routeDetailMapControls)
        mapError = findViewById(R.id.routeDetailMapError)
        sheetMapError = findViewById(R.id.routeDetailSheetMapError)

        toolbar.applyStatusBarPadding()
        toolbar.navigationContentDescription = getString(R.string.route_detail_navigate_up)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        floatingBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<MaterialButton>(R.id.routeDetailOverview).setOnClickListener {
            renderer?.fitOverview(animated = true, paddingPx = dimension(R.dimen.route_map_camera_padding))
        }
        findViewById<MaterialButton>(R.id.routeDetailLocation).setOnClickListener { onLocationClicked() }
    }

    private fun setupTimeline() {
        adapter = RouteDetailAdapter(::toggleLeg, ::retryDetail, ::onTimelineStopSelected)
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
                renderer = GoogleRouteMapRenderer(this, map, onMarkerSelected = ::onMapMarkerSelected).also {
                    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    it.setDarkMode(night)
                }
                map.setOnMapLoadedCallback {
                    baseMapLoaded = true
                    mainHandler.removeCallbacks(mapLoadTimeout)
                    if (mapUnavailable) onMapRecovered()
                }
                map.setOnCameraIdleListener { saveCamera(map) }
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
        mapError.visibility = View.VISIBLE
        sheetMapError.visibility = View.VISIBLE
        findViewById<View>(R.id.routeDetailMapControls).visibility = View.GONE
        sheet.post { applyDetent(RouteDetailSheetDetent.FULL) }
    }

    private fun onMapRecovered() {
        mapUnavailable = false
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
        detailLoading = true
        detailFailed = false
        val requestGeneration = ++detailGeneration
        val languageVersion = AppLanguageRuntime.snapshot().version
        val route = args.toRoute()
        executor.execute {
            val result = runCatching { repository.loadRouteDetail(route) }
            mainHandler.post {
                if (!isCurrentDetail(requestGeneration, languageVersion)) return@post
                result.onSuccess {
                    detailLoading = false
                    detailFailed = false
                    detail = it
                    renderDetail()
                    validateLoadedGeometryAgainstDetail()
                }.onFailure {
                    detailLoading = false
                    detailFailed = true
                    showLaunchSummary(loading = false, failed = true)
                    renderMap()
                }
            }
        }
    }

    private fun retryDetail() {
        detailLoading = true
        detailFailed = false
        showLaunchSummary(loading = true)
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
        geometryPendingCount = geometryCoordinator.loadingCount()
        if (manualRetry && onlyKeys != null) failedGeometryKeys.removeAll(onlyKeys)
        val handle = geometryRepository.loadGeometries(requests) { request, result ->
            mainHandler.post {
                if (!isCurrentGeometry(requestGeneration, languageVersion)) return@post
                val validatedResult = result.mapCatching { segment ->
                    val validationRequest = geometryRequestForKey(request.key)
                    geometryRepository.validateGeometry(validationRequest, segment).getOrThrow()
                }
                validatedResult.onSuccess { segment ->
                    geometries[request.key] = segment
                    geometryCoordinator.onCandidate(request.key, endpointsAvailable = detail != null)
                }.onFailure { throwable ->
                    geometries.remove(request.key)
                    if (
                        geometryCoordinator.onFailure(
                            request.key,
                            throwable,
                            allowAutoRetry = foreground
                        ) == RouteGeometryRetryDecision.AUTO_RETRY
                    ) {
                        mainHandler.postDelayed(
                            {
                                if (!isCurrentGeometry(requestGeneration, languageVersion)) return@postDelayed
                                if (foreground) {
                                    loadGeometry(setOf(request.key))
                                } else {
                                    geometryCoordinator.onFailure(
                                        request.key,
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
                syncGeometryFailureState()
                renderMap()
            }
        }
        geometryHandles += handle
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
        geometries.toMap().forEach { (key, segment) ->
            geometryRepository.validateGeometry(geometryRequestForKey(key), segment)
                .onSuccess { geometryCoordinator.onValidated(key) }
                .onFailure { throwable ->
                    geometries.remove(key)
                    geometryCoordinator.onFailure(key, throwable)
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
        val languageVersion = AppLanguageRuntime.snapshot().version
        executor.execute {
            val state = runCatching { RouteDetailRuntime.etaResolver(query) }
                .getOrElse { WaitTimeState.Unavailable(EtaUnavailableReason.UNEXPECTED_ERROR) }
            mainHandler.post {
                if (!foreground || !isCurrentEta(requestGeneration, languageVersion)) return@post
                waitTimeState = state
                if (state !is WaitTimeState.Unavailable) lastEtaSuccessMillis = System.currentTimeMillis()
                if (detail != null) renderDetail() else showLaunchSummary(detailLoading, detailFailed)
                mainHandler.removeCallbacks(etaTick)
                mainHandler.postDelayed(etaTick, RouteDetailEtaRefreshPolicy.REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    private fun renderDetail() {
        val value = detail ?: return
        adapter.submitList(RouteDetailUiFormatter.items(value, expandedLegIndexes, waitTimeState)) {
            adapter.selectTimelineItem(selectedTimelineId)
            restoreListPositionIfNeeded()
            scheduleSheetMetrics()
        }
        renderMap()
    }

    private fun showLaunchSummary(loading: Boolean, failed: Boolean = false) {
        val arrival = args.routeDetailQuery?.generalInfo?.substringBefore("|*|")?.takeIf { it.contains(':') }
        val items = mutableListOf<RouteDetailUiItem>(
            RouteDetailUiItem.Summary(
                routeName = args.routeName,
                durationMinutes = args.durationMinutes,
                plannedArrivalTime = arrival,
                priceHkd = args.priceHkd,
                totalViaStops = args.estimatedViaStopCount,
                walkingDistanceMeters = args.walkingDistanceMeters,
                isWalkingDistanceComplete = false,
                firstLegEta = waitTimeState
            )
        )
        if (loading) items += RouteDetailUiItem.Loading
        if (failed) items += RouteDetailUiItem.Error
        adapter.submitList(items) { restoreListPositionIfNeeded(); scheduleSheetMetrics() }
    }

    private fun renderMap() {
        val routePlan = args.routeDetailQuery?.plan?.legs.orEmpty()
        val presentation = RouteMapPresentationBuilder.build(
            detail = detail,
            queryOrigin = args.queryOrigin,
            queryDestination = args.queryDestination,
            geometries = geometries,
            selectedMarkerId = selectedMarkerId,
            routePlan = routePlan
        )
        lastPresentation = presentation
        RouteDetailRuntime.presentationObserver(presentation)
        renderer?.render(presentation)
        if (!initialFitDone && mapReady && geometryPendingCount == 0 && presentation.boundsPoints.isNotEmpty()) {
            mapView.post {
                if (!initialFitDone && renderer?.fitOverview(false, dimension(R.dimen.route_map_camera_padding)) == true) {
                    initialFitDone = true
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
        adapter.selectTimelineItem(stableId)
        renderMap()
        applyDetent(RouteDetailSheetDetent.HALF)
        renderer?.focusMarker(marker.stableId)
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
        toolbar.visibility = if (full) View.VISIBLE else View.GONE
        floatingBack.visibility = if (full) View.GONE else View.VISIBLE
        sheetHandle.contentDescription = getString(
            when (value) {
                RouteDetailSheetDetent.SUMMARY -> R.string.route_detail_sheet_handle_summary
                RouteDetailSheetDetent.HALF -> R.string.route_detail_sheet_handle_half
                RouteDetailSheetDetent.FULL -> R.string.route_detail_sheet_handle_full
            }
        )
        if (value == RouteDetailSheetDetent.SUMMARY) listLayoutManager.scrollToPositionWithOffset(0, 0)
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
            renderer?.updatePadding(0, statusBarInset, 0, bottomPadding)
        }
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
    }

    private fun restoreCamera(map: GoogleMap) {
        val latitude = cameraLatitude ?: return
        val longitude = cameraLongitude ?: return
        val zoom = cameraZoom ?: return
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
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
        locationPermissionRequestedBefore = state.getBoolean(STATE_LOCATION_REQUESTED)
        if (state.containsKey(STATE_CAMERA_LATITUDE)) cameraLatitude = state.getDouble(STATE_CAMERA_LATITUDE)
        if (state.containsKey(STATE_CAMERA_LONGITUDE)) cameraLongitude = state.getDouble(STATE_CAMERA_LONGITUDE)
        if (state.containsKey(STATE_CAMERA_ZOOM)) cameraZoom = state.getFloat(STATE_CAMERA_ZOOM)
        pendingListPosition = state.getInt(STATE_LIST_POSITION, RecyclerView.NO_POSITION)
        pendingListOffset = state.getInt(STATE_LIST_OFFSET, 0)
    }

    private fun restoreListPositionIfNeeded() {
        if (pendingListPosition == RecyclerView.NO_POSITION) return
        val position = pendingListPosition.coerceAtMost((adapter.itemCount - 1).coerceAtLeast(0))
        listLayoutManager.scrollToPositionWithOffset(position, pendingListOffset)
        pendingListPosition = RecyclerView.NO_POSITION
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
        const val STATE_MAP = "route_detail.map"
        const val MAP_LOAD_TIMEOUT_MILLIS = 15_000L
        const val GEOMETRY_RETRY_BACKOFF_MILLIS = 250L
        const val LOCATION_FOCUS_ZOOM = 16f
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
    private val defaultRepositoryFactory: () -> RouteDetailRepository = { CitybusRouteDetailRepository() }
    private val defaultGeometryRepository: RouteGeometryDataSource by lazy { CitybusRouteGeometryRepository() }
    private val defaultGeometryRepositoryFactory: () -> RouteGeometryDataSource = { defaultGeometryRepository }
    private val defaultEtaResolver: (FirstLegEtaQuery) -> WaitTimeState =
        { query -> CitybusFirstLegEtaService().resolveWaitTime(query) }
    private val defaultMapsAvailabilityChecker: (Context) -> Boolean = { context ->
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    private val defaultPresentationObserver: (RouteMapPresentation) -> Unit = {}

    @Volatile var repositoryFactory: () -> RouteDetailRepository = defaultRepositoryFactory
    @Volatile var geometryRepositoryFactory: () -> RouteGeometryDataSource = defaultGeometryRepositoryFactory
    @Volatile var etaResolver: (FirstLegEtaQuery) -> WaitTimeState = defaultEtaResolver
    @Volatile var mapsAvailabilityChecker: (Context) -> Boolean = defaultMapsAvailabilityChecker
    @Volatile var presentationObserver: (RouteMapPresentation) -> Unit = defaultPresentationObserver

    fun reset() {
        repositoryFactory = defaultRepositoryFactory
        geometryRepositoryFactory = defaultGeometryRepositoryFactory
        etaResolver = defaultEtaResolver
        mapsAvailabilityChecker = defaultMapsAvailabilityChecker
        presentationObserver = defaultPresentationObserver
    }
}
