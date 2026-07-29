package com.golink.busiscoming.ui.main

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
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
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
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
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.model.AppUpdateState
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.model.RouteCardStopPreview
import com.golink.busiscoming.data.model.SortDirection
import com.golink.busiscoming.data.model.SortField
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.model.WalkingTimeCalculator
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.update.AppUpdateExternalActions
import com.golink.busiscoming.data.update.AppUpdateRuntime
import com.golink.busiscoming.data.repository.BusRouteRepository
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
import com.golink.busiscoming.ui.common.localizedText
import com.golink.busiscoming.ui.edit.RouteEditActivity
import com.golink.busiscoming.ui.manage.RouteManageActivity
import com.golink.busiscoming.ui.navigation.TopLevelDestination
import com.golink.busiscoming.ui.navigation.TopLevelDestinationState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val appUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        AppUpdateRuntime.coordinator.refreshPlayInstallStatus()
    }
    private lateinit var routeConfigRepository: RouteConfigRepository
    private lateinit var currentLocationCoordinator: CurrentLocationCoordinator
    private lateinit var locationPermissionStateStore: LocationPermissionStateStore
    private lateinit var placeNameResolver: PlaceNameResolver
    private val busRouteRepository: BusRouteRepository = CitybusBusRouteRepository()
    private val routeDetailRepository: RouteDetailRepository = routeDetailRepositoryFactory()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queryExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val routeQueryCoordinator = RouteQueryCoordinator(
        repository = busRouteRepository,
        executor = queryExecutor,
        postToOwner = { runnable -> mainHandler.post(runnable) },
        isOwnerActive = { !isFinishing && !isDestroyed }
    )

    private lateinit var queryButton: MaterialButton
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
    private lateinit var stickyResultControls: View
    private lateinit var sortControls: View
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
    private val topLevelNavImePolicy = MainActivityImeNavigationPolicy()

    private var routeConfigs: List<RouteConfig> = emptyList()
    private var selectedRoute: RouteConfig? = null
    private val routeQueryState = RouteQueryState()
    private val currentResults: List<BusRouteOption>
        get() = routeQueryState.results
    private val sortField: SortField?
        get() = routeQueryState.sortField
    private val sortDirection: SortDirection
        get() = routeQueryState.sortDirection
    private var currentQueryContext: QueryContext? = null
    private val isQueryInProgress: Boolean
        get() = routeQueryState.isQueryInProgress
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
    private var appUpdateSubscription: AutoCloseable? = null
    private var updatePromptDialog: AlertDialog? = null
    private var updateDownloadedSnackbar: Snackbar? = null
    private var hasRequestedAutomaticUpdateCheck = false
    private var pendingLocationPermissionAction: PendingLocationPermissionAction? = null
    private var pendingLocationSettingsCurrentPlaceCallback: ((CurrentPlaceSelectionResult) -> Unit)? = null
    private var hasPlayedFirstRunIntroAnimation: Boolean = false
    private var frequentRoutesInitialized: Boolean = false
    private var restoredActiveQueryRouteId: Long? = null
    private var restoredFrequentSortField: SortField? = null
    private var restoredFrequentSortDirection: SortDirection = SortDirection.ASC
    private var pendingFrequentViewport: RefreshViewport? = null
    private var legacyImeLayoutRoot: View? = null
    private var legacyImeLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var legacyImeExpandedRootHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLegacyImeWindow()
        restoreSavedRouteUsageSession(savedInstanceState)
        restoreFrequentQueryState(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "BusIsComing"

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
        consumeTransitCodeIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeTransitCodeIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val selectedRouteId = selectedRoute?.id ?: savedRouteUsageSession.selectedRouteId
        selectedRouteId?.let { outState.putLong(STATE_SELECTED_ROUTE_ID, it) }
        savedRouteUsageSession.recordedRouteId
            ?.takeIf { it == selectedRouteId }
            ?.let { outState.putLong(STATE_RECORDED_USAGE_ROUTE_ID, it) }
        outState.putString(STATE_SELECTED_DESTINATION, destinationState.selected.name)
        (currentQueryContext as? QueryContext.Saved)?.routeId?.let {
            outState.putLong(STATE_ACTIVE_QUERY_ROUTE_ID, it)
        }
        sortField?.let { outState.putString(STATE_FREQUENT_SORT_FIELD, it.name) }
        outState.putString(STATE_FREQUENT_SORT_DIRECTION, sortDirection.name)
        if (::resultList.isInitialized) {
            val manager = resultList.layoutManager as? LinearLayoutManager
            val position = manager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
            if (position != RecyclerView.NO_POSITION) {
                outState.putInt(STATE_FREQUENT_SCROLL_POSITION, position)
                outState.putInt(
                    STATE_FREQUENT_SCROLL_OFFSET,
                    manager?.findViewByPosition(position)?.top ?: 0
                )
            }
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        removeLegacyImeNavigationListener()
        appUpdateSubscription?.close()
        appUpdateSubscription = null
        updatePromptDialog?.dismiss()
        updatePromptDialog = null
        updateDownloadedSnackbar?.dismiss()
        updateDownloadedSnackbar = null
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
        AppUpdateRuntime.coordinator.reloadPersistedState()
        AppUpdateRuntime.coordinator.refreshPlayInstallStatus()
        handleAppUpdateState(AppUpdateRuntime.coordinator.currentState())
        if (frequentRoutesInitialized) {
            loadRouteConfigs()
        }
        retryCurrentPlaceAfterLocationSettings()
    }

    override fun onStart() {
        super.onStart()
        appUpdateSubscription?.close()
        appUpdateSubscription = AppUpdateRuntime.coordinator.observe(::handleAppUpdateState)
    }

    override fun onStop() {
        appUpdateSubscription?.close()
        appUpdateSubscription = null
        super.onStop()
    }

    fun onFrequentRoutesViewReady() {
        if (frequentRoutesInitialized) return
        bindViews()
        setupResultList()
        setupActions()
        frequentRoutesInitialized = true
        loadRouteConfigs()
        restoreFrequentQueryIfNeeded()
        if (!hasRequestedAutomaticUpdateCheck) {
            hasRequestedAutomaticUpdateCheck = true
            mainHandler.post {
                AppUpdateRuntime.coordinator.check(UpdateCheckTrigger.AUTOMATIC)
            }
        }
    }

    private fun handleAppUpdateState(state: AppUpdateState) {
        renderDownloadedUpdate(state.playUpdateDownloaded)
        if (
            !state.isChecking && state.snapshot.hasNewerVersion &&
            state.lastFailure == null &&
            AppUpdateRuntime.coordinator.shouldPrompt() && canShowUpdateUi()
        ) {
            showUpdatePrompt(state)
        }
    }

    private fun canShowUpdateUi(): Boolean =
        !isFinishing && !isDestroyed &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !supportFragmentManager.isStateSaved

    private fun showUpdatePrompt(state: AppUpdateState) {
        if (updatePromptDialog?.isShowing == true) return
        val version = state.snapshot.availableVersionName
            ?: state.snapshot.availableVersionCode?.toString()
            ?: return
        val content = layoutInflater.inflate(R.layout.dialog_app_update, null)
        content.findViewById<TextView>(R.id.updatePromptVersion).text =
            getString(R.string.update_prompt_version, version)
        applyUpdatePromptLayout(content)
        val dialog = MaterialAlertDialogBuilder(
            this,
            R.style.ThemeOverlay_BusIsComing_UpdatePrompt
        )
            .setView(content)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { updatePromptDialog = null }
        content.findViewById<MaterialButton>(R.id.updatePromptLaterButton).setOnClickListener {
            AppUpdateRuntime.coordinator.deferCurrentVersion()
            dialog.dismiss()
        }
        content.findViewById<MaterialButton>(R.id.updatePromptSkipButton).setOnClickListener {
            AppUpdateRuntime.coordinator.skipCurrentVersion()
            dialog.dismiss()
        }
        content.findViewById<MaterialButton>(R.id.updatePromptUpdateButton).setOnClickListener {
            AppUpdateRuntime.coordinator.deferCurrentVersion()
            dialog.dismiss()
            startSelectedUpdate()
        }
        updatePromptDialog = dialog
        dialog.show()
    }

    private fun applyUpdatePromptLayout(content: View) {
        val actions = content.findViewById<LinearLayout>(R.id.updatePromptActions)
        val buttons = listOf(
            content.findViewById<MaterialButton>(R.id.updatePromptLaterButton),
            content.findViewById<MaterialButton>(R.id.updatePromptSkipButton),
            content.findViewById<MaterialButton>(R.id.updatePromptUpdateButton)
        )
        val configuration = resources.configuration
        val horizontal = UpdatePromptLayoutPolicy.resolve(
            screenWidthDp = configuration.screenWidthDp,
            fontScale = configuration.fontScale
        ) == UpdatePromptLayoutMode.HORIZONTAL

        actions.orientation = if (horizontal) {
            LinearLayout.HORIZONTAL
        } else {
            LinearLayout.VERTICAL
        }
        buttons.forEachIndexed { index, button ->
            button.layoutParams = if (horizontal) {
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1.0f
                )
            } else {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(4)
                }
            }
        }
    }

    private fun startSelectedUpdate() {
        val snapshot = AppUpdateRuntime.coordinator.currentState().snapshot
        when (snapshot.channel) {
            UpdateChannel.PLAY -> {
                val flexibleStarted = snapshot.flexibleAllowed &&
                    AppUpdateRuntime.coordinator.startFlexibleUpdate(this, appUpdateLauncher)
                if (!flexibleStarted) {
                    AppUpdateExternalActions.openPlayListing(this)
                }
            }
            UpdateChannel.WEBSITE -> AppUpdateExternalActions.openWebsiteDownloadPage(
                context = this,
                language = AppLanguageRuntime.snapshot().effectiveLanguage
            )
            UpdateChannel.PLAY_UNAVAILABLE,
            null -> Toast.makeText(
                this,
                R.string.update_play_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun renderDownloadedUpdate(downloaded: Boolean) {
        if (!downloaded) {
            updateDownloadedSnackbar?.dismiss()
            updateDownloadedSnackbar = null
            return
        }
        if (updateDownloadedSnackbar?.isShown == true) return
        val snackbar = Snackbar.make(
            findViewById(R.id.mainRoot),
            R.string.update_downloaded_message,
            Snackbar.LENGTH_INDEFINITE
        ).setAnchorView(topLevelNav).setAction(R.string.update_downloaded_action) {
            AppUpdateRuntime.coordinator.completePlayUpdate { success ->
                if (!success) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            R.string.update_complete_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
        snackbar.view.findViewById<TextView>(
            com.google.android.material.R.id.snackbar_text
        ).apply {
            maxLines = 4
            ellipsize = null
        }
        snackbar.view.findViewById<TextView>(
            com.google.android.material.R.id.snackbar_action
        ).apply {
            isSingleLine = false
            maxLines = 2
            ellipsize = null
        }
        updateDownloadedSnackbar = snackbar.also(Snackbar::show)
    }

    private fun bindViews() {
        val root = requireTopLevelFragment(TAG_FREQUENT_ROUTES).requireView()
        queryButton = root.findViewById(R.id.queryButton)
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
        stickyResultControls = root.findViewById(R.id.stickyResultControls)
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
        routeManageButton.setOnClickListener {
            startActivity(Intent(this, RouteManageActivity::class.java))
        }
        requireTopLevelFragment(TAG_FREQUENT_ROUTES).requireView()
            .findViewById<MaterialButton>(R.id.emptyAddRouteButton).setOnClickListener {
            startActivity(Intent(this, RouteEditActivity::class.java))
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
        val mainRoot = findViewById<View>(R.id.mainRoot)
        mainRoot.applyStatusBarPadding { insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                applyTopLevelNavigationImePolicy(
                    imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                )
            }
        }
        installLegacyImeNavigationListener(mainRoot)
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

    private fun installLegacyImeNavigationListener(mainRoot: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (mainRoot.height <= 0) return@OnGlobalLayoutListener
            legacyImeExpandedRootHeight = maxOf(legacyImeExpandedRootHeight, mainRoot.height)
            val coveredHeight = (legacyImeExpandedRootHeight - mainRoot.height).coerceAtLeast(0)
            val imeVisible = coveredHeight > dp(100)
            topLevelNav.translationY = if (imeVisible) coveredHeight.toFloat() else 0f
            applyTopLevelNavigationImePolicy(
                imeVisible = imeVisible
            )
        }
        legacyImeLayoutRoot = mainRoot
        legacyImeLayoutListener = listener
        mainRoot.viewTreeObserver.addOnGlobalLayoutListener(listener)
        mainRoot.post { listener.onGlobalLayout() }
    }

    private fun removeLegacyImeNavigationListener() {
        if (::topLevelNav.isInitialized) topLevelNav.translationY = 0f
        val root = legacyImeLayoutRoot
        val listener = legacyImeLayoutListener
        if (root != null && listener != null) {
            root.viewTreeObserver
                .takeIf { it.isAlive }
                ?.removeOnGlobalLayoutListener(listener)
        }
        legacyImeLayoutRoot = null
        legacyImeLayoutListener = null
        legacyImeExpandedRootHeight = 0
    }

    private fun configureLegacyImeWindow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    private fun applyTopLevelNavigationImePolicy(imeVisible: Boolean) {
        when (
            val transition = topLevelNavImePolicy.update(
                imeVisible = imeVisible,
                current = captureTopLevelNavigationSnapshot()
            )
        ) {
            MainActivityImeNavigationTransition.ApplyGuard -> {
                topLevelNav.isEnabled = false
                topLevelNav.isClickable = false
                topLevelNav.importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                val disabledStates = List(topLevelNav.menu.size()) { false }
                applyTopLevelMenuItemEnabledStates(disabledStates)
            }
            is MainActivityImeNavigationTransition.Restore -> {
                val snapshot = transition.snapshot
                applyTopLevelMenuItemEnabledStates(snapshot.menuItemEnabledStates)
                topLevelNav.isEnabled = snapshot.isEnabled
                topLevelNav.isClickable = snapshot.isClickable
                topLevelNav.importantForAccessibility = snapshot.importantForAccessibility
            }
            null -> Unit
        }
    }

    private fun captureTopLevelNavigationSnapshot(): MainActivityImeNavigationSnapshot {
        return MainActivityImeNavigationSnapshot(
            isEnabled = topLevelNav.isEnabled,
            isClickable = topLevelNav.isClickable,
            importantForAccessibility = topLevelNav.importantForAccessibility,
            menuItemEnabledStates = (0 until topLevelNav.menu.size())
                .map { topLevelNav.menu.getItem(it).isEnabled }
        )
    }

    private fun applyTopLevelMenuItemEnabledStates(enabledStates: List<Boolean>) {
        (0 until topLevelNav.menu.size()).forEach { index ->
            topLevelNav.menu.getItem(index).isEnabled = enabledStates.getOrElse(index) { false }
        }
        val menuView = topLevelNav.getChildAt(0) as? ViewGroup ?: return
        (0 until menuView.childCount).forEach { index ->
            menuView.getChildAt(index).isEnabled = enabledStates.getOrElse(index) { false }
        }
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

    private fun restoreFrequentQueryState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return
        restoredActiveQueryRouteId = savedInstanceState.longOrNull(STATE_ACTIVE_QUERY_ROUTE_ID)
        restoredFrequentSortField = savedInstanceState.getString(STATE_FREQUENT_SORT_FIELD)
            ?.let { runCatching { SortField.valueOf(it) }.getOrNull() }
        restoredFrequentSortDirection = savedInstanceState
            .getString(STATE_FREQUENT_SORT_DIRECTION)
            ?.let { runCatching { SortDirection.valueOf(it) }.getOrNull() }
            ?: SortDirection.ASC
        if (savedInstanceState.containsKey(STATE_FREQUENT_SCROLL_POSITION)) {
            pendingFrequentViewport = RefreshViewport(
                position = savedInstanceState.getInt(STATE_FREQUENT_SCROLL_POSITION),
                offset = savedInstanceState.getInt(STATE_FREQUENT_SCROLL_OFFSET)
            )
        }
    }

    private fun restoreFrequentQueryIfNeeded() {
        val routeId = restoredActiveQueryRouteId ?: return
        restoredActiveQueryRouteId = null
        val route = routeConfigRepository.getById(routeId) ?: return
        selectedRoute = routeConfigs.firstOrNull { it.id == routeId } ?: route
        savedRouteUsageSession.selectSavedRoute(routeId)
        routeQueryState.restoreSort(restoredFrequentSortField, restoredFrequentSortDirection)
        renderRouteShortcuts()
        queryRoute(
            origin = route.origin,
            destination = route.destination,
            sourceRoute = route,
            queryContext = QueryContext.Saved(routeId),
            recordUsage = false,
            preserveSort = true
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
            Toast.makeText(this, R.string.select_route_first, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, R.string.route_no_longer_exists, Toast.LENGTH_SHORT).show()
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

        currentQueryContext = queryContext
        preserveSortOnNextResults = preserveSort
        routeQueryState.begin(refresh = isRefresh)
        renderHomeShell()
        if (isRefresh) {
            resultSwipeRefresh.isRefreshing = false
        } else {
            showLoadingState()
        }
        val queryId = routeQueryCoordinator.query(
            origin,
            destination,
            object : RouteQueryCoordinator.Callback {
                override fun onInitialRoutes(queryId: Int, routes: List<BusRouteOption>) {
                    if (isRefresh) {
                        handleRefreshSuccess(queryId, routes)
                    } else {
                        showInitialRoutes(routes)
                        finishQueryLoading()
                    }
                }

                override fun onRouteWaitTimeUpdated(
                    queryId: Int,
                    routeId: String,
                    waitTimeState: WaitTimeState
                ) {
                    updateRouteWaitTime(routeId, waitTimeState)
                }

                override fun onRouteStopPreviewUpdated(
                    queryId: Int,
                    routeId: String,
                    preview: RouteCardStopPreview
                ) {
                    updateRouteStopPreview(routeId, preview)
                }

                override fun onFailure(queryId: Int, error: Throwable) {
                    Log.e(LOG_TAG, "Bus route query failed", error)
                    if (isRefresh) {
                        handleRefreshFailure(queryId)
                    } else {
                        routeQueryState.fail(getString(R.string.route_query_failed), preserveResults = false)
                        updateSortControls()
                        displayFailure()
                        finishQueryLoading()
                    }
                }
            }
        )
        if (isRefresh) {
            showRefreshLoadingState(queryId)
        }
    }

    private fun showInitialRoutes(routes: List<BusRouteOption>) {
        routeQueryState.complete(
            routes = routes,
            preserveSort = preserveSortOnNextResults,
            updatedAtMillis = System.currentTimeMillis()
        )
        preserveSortOnNextResults = false
        updateSortControls()
        updateResultSummary(routes)
        displayResults(currentResults)
        restorePendingFrequentViewport()
    }

    private fun restorePendingFrequentViewport() {
        val viewport = pendingFrequentViewport ?: return
        if (currentResults.isEmpty()) return
        pendingFrequentViewport = null
        (resultList.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(
                viewport.position.coerceIn(0, currentResults.lastIndex),
                viewport.offset
            )
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
        routeQueryState.fail(getString(R.string.refresh_failed), preserveResults = true)
        refreshFinishRunnable?.let(mainHandler::removeCallbacks)
        refreshFinishRunnable = null
        renderRefreshFeedback()
        finishQueryLoading()
        restoreRefreshViewport()
        Toast.makeText(this, R.string.refresh_failed, Toast.LENGTH_SHORT).show()
    }

    private fun updateRouteWaitTime(routeId: String, waitTimeState: WaitTimeState) {
        if (!routeQueryState.updateWaitTime(routeId, waitTimeState)) return
        currentResults.firstOrNull { it.resultId == routeId }?.let { etaArrivalsBottomSheet.update(it) }
        displayResults(currentResults)
    }

    private fun updateRouteStopPreview(routeId: String, preview: RouteCardStopPreview) {
        if (!routeQueryState.updateStopPreview(routeId, preview)) return
        displayResults(currentResults)
    }

    private fun sortBy(field: SortField) {
        if (currentResults.isEmpty()) return

        routeQueryState.toggleSort(field)
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
                title = getString(R.string.no_routes_title),
                message = getString(R.string.no_routes_message),
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
        updateStickyResultControlsVisibility()
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
        monitorSettingsRequestObserver?.invoke(route, origin)
        if (route.firstLegEtaQuery == null || route.waitTimeState !is WaitTimeState.Available) {
            Toast.makeText(this, R.string.monitor_route_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (origin == null) {
            Toast.makeText(this, R.string.monitor_origin_missing, Toast.LENGTH_SHORT).show()
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
        Toast.makeText(this, R.string.monitor_started, Toast.LENGTH_SHORT).show()
    }

    private fun promptHighPriorityMonitorSettingsIfNeeded() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (!BusMonitorSchedulingCapability.canScheduleExactAlarms(alarmManager)) {
            val intent = BusMonitorSchedulingCapability.exactAlarmSettingsIntent(this)
            if (intent != null && intent.resolveActivity(packageManager) != null) {
                Toast.makeText(this, R.string.monitor_alarm_hint, Toast.LENGTH_LONG).show()
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
                    Toast.makeText(
                        this,
                        R.string.monitor_notification_permission_denied,
                        Toast.LENGTH_SHORT
                    ).show()
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

    fun requestCurrentLocationSnapshot(callback: (CurrentLocationSnapshot?) -> Unit) {
        if (
            !LocationPermissionUtils.hasForegroundLocationPermission(this) ||
            !SystemLocationUtils.isLocationEnabled(this)
        ) {
            callback(null)
            return
        }
        currentLocationCoordinator.getCurrentLocation { result ->
            callback((result as? CurrentLocationResult.Success)?.snapshot)
        }
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
        Toast.makeText(this, R.string.enable_system_location, Toast.LENGTH_SHORT).show()
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
        resultSummaryText.text = RouteResultCardFormatter.resultSummary(routes, localizedText())
        val updatedAt = routeQueryState.updatedAtMillis ?: System.currentTimeMillis()
        resultUpdatedAtText.text = getString(
            R.string.updated_at,
            RESULT_TIME_FORMAT.get()!!.format(Date(updatedAt))
        )
        resultSummaryContainer.visibility = View.VISIBLE
        updateStickyResultControlsVisibility()
    }

    private fun hideResultSummary() {
        resultSummaryContainer.visibility = View.GONE
        resultSummaryText.text = ""
        resultUpdatedAtText.text = ""
        updateStickyResultControlsVisibility()
    }

    private fun updateStickyResultControlsVisibility() {
        stickyResultControls.visibility = if (
            sortControls.visibility == View.VISIBLE ||
            resultSummaryContainer.visibility == View.VISIBLE
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun clearResults() {
        invalidateActiveQuery()
        currentQueryContext = null
        routeQueryState.clear()
        setQueryLoading(false)
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
        routeQueryCoordinator.invalidate()
        routeQueryState.cancel()
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
                            text = getString(R.string.nearby)
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
            text = getString(R.string.frequent_routes_label)
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

    private fun consumeTransitCodeIntent(intent: Intent?) {
        if (!TransitCodeEntryPoint.isLaunchAction(intent?.action)) return
        intent?.action = null
        launchTransitCode()
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
            LocationFallbackToast.PERMISSION_DENIED -> getString(R.string.location_fallback_permission)
            LocationFallbackToast.UNAVAILABLE -> getString(R.string.location_fallback_unavailable)
            LocationFallbackToast.IMPRECISE -> getString(R.string.location_fallback_imprecise)
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
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        hideResultSummary()
        resultListContainer.visibility = View.GONE
        showStatus(
            title = getString(R.string.route_query_loading_title),
            message = getString(R.string.route_query_loading_message),
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
            title = getString(R.string.route_query_failed),
            message = getString(R.string.route_query_failure_message),
            showProgress = false
        )
    }

    private fun setQueryLoading(isLoading: Boolean) {
        queryButton.isEnabled = !isLoading
        queryButton.setText(if (isLoading) R.string.action_querying else R.string.action_query)
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
        sortButtons.forEach { (field, button) ->
            val isSelected = sortField == field
            button.isChecked = isSelected
            button.text = sortButtonText(field)
        }
    }

    private fun sortButtonText(field: SortField): String {
        val label = when (field) {
            SortField.ROUTE -> getString(R.string.sort_route)
            SortField.PRICE -> getString(R.string.sort_price)
            SortField.DURATION -> getString(R.string.sort_duration)
            SortField.ARRIVAL -> getString(R.string.sort_arrival)
            SortField.WALKING_DISTANCE -> getString(R.string.sort_walking)
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
        @Volatile
        internal var routeDetailRepositoryFactory: () -> RouteDetailRepository =
            { CitybusRouteDetailRepository() }

        @Volatile
        internal var monitorSettingsRequestObserver: ((BusRouteOption, Place?) -> Unit)? = null

        internal fun resetTestDependencies() {
            routeDetailRepositoryFactory = { CitybusRouteDetailRepository() }
            monitorSettingsRequestObserver = null
        }

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
        private const val STATE_ACTIVE_QUERY_ROUTE_ID = "active_query_route_id"
        private const val STATE_FREQUENT_SORT_FIELD = "frequent_sort_field"
        private const val STATE_FREQUENT_SORT_DIRECTION = "frequent_sort_direction"
        private const val STATE_FREQUENT_SCROLL_POSITION = "frequent_scroll_position"
        private const val STATE_FREQUENT_SCROLL_OFFSET = "frequent_scroll_offset"
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
