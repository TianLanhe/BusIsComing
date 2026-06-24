package com.example.busiscoming.ui.edit

import android.content.ActivityNotFoundException
import android.graphics.Rect
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscoming.R
import com.example.busiscoming.data.location.CurrentLocationCoordinator
import com.example.busiscoming.data.location.CurrentLocationResult
import com.example.busiscoming.data.location.LocationPermissionStateStore
import com.example.busiscoming.data.location.LocationPermissionUtils
import com.example.busiscoming.data.location.MockPlaceNameResolver
import com.example.busiscoming.data.location.SystemLocationUtils
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.model.RouteConfigValidator
import com.example.busiscoming.data.repository.CitybusPlaceSearchRepository
import com.example.busiscoming.data.repository.PlaceSearchRepository
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.example.busiscoming.ui.common.PlaceInputController
import com.example.busiscoming.ui.common.applyStatusBarPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteEditActivity : AppCompatActivity() {
    private lateinit var repository: RouteConfigRepository
    private lateinit var placeSearchRepository: PlaceSearchRepository
    private lateinit var currentLocationCoordinator: CurrentLocationCoordinator
    private lateinit var locationPermissionStateStore: LocationPermissionStateStore
    private lateinit var routeEditScroll: NestedScrollView
    private lateinit var routeEditContent: View
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var originInputLayout: TextInputLayout
    private lateinit var destinationInputLayout: TextInputLayout
    private lateinit var screenTitleText: TextView
    private lateinit var nameInput: TextInputEditText
    private lateinit var originInput: MaterialAutoCompleteTextView
    private lateinit var destinationInput: MaterialAutoCompleteTextView
    private lateinit var originSearchLoading: View
    private lateinit var destinationSearchLoading: View
    private lateinit var originCandidateList: RecyclerView
    private lateinit var destinationCandidateList: RecyclerView
    private lateinit var swapPlacesButton: View
    private lateinit var originController: PlaceInputController
    private lateinit var destinationController: PlaceInputController
    private var candidateBackCallback: OnBackInvokedCallback? = null

    private var routeId: Long = NO_ROUTE_ID
    private val mainHandler = Handler(Looper.getMainLooper())
    private val searchExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var originTouchedByUser: Boolean = false
    private var currentPlaceGeneration: Int = 0
    private var pendingCurrentPlaceRequest: PendingCurrentPlaceRequest? = null
    private var pendingLocationSettingsRetry: PendingCurrentPlaceRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.routeEditContent).applyStatusBarPadding()
        repository = RouteConfigRepository(this)
        placeSearchRepository = CitybusPlaceSearchRepository()
        currentLocationCoordinator = CurrentLocationCoordinator(this)
        locationPermissionStateStore = LocationPermissionStateStore(this)
        routeId = intent.getLongExtra(EXTRA_ROUTE_ID, NO_ROUTE_ID)

        bindViews()
        configureLocationEndIcon()
        setupPlaceInputs()
        setupBackHandling()
        setupMode()
    }

    override fun onDestroy() {
        if (::originController.isInitialized) originController.dispose()
        if (::destinationController.isInitialized) destinationController.dispose()
        updateCandidateBackPriority(false)
        mainHandler.removeCallbacksAndMessages(null)
        searchExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        retryCurrentOriginAfterLocationSettings()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            handleBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindViews() {
        routeEditScroll = findViewById<NestedScrollView>(R.id.routeEditScroll).apply {
            isNestedScrollingEnabled = true
        }
        routeEditContent = findViewById(R.id.routeEditContent)
        nameInputLayout = findViewById(R.id.routeNameInputLayout)
        originInputLayout = findViewById(R.id.originInputLayout)
        destinationInputLayout = findViewById(R.id.destinationInputLayout)
        screenTitleText = findViewById(R.id.routeEditTitle)
        nameInput = findViewById(R.id.routeNameInput)
        originInput = findViewById(R.id.originInput)
        destinationInput = findViewById(R.id.destinationInput)
        originSearchLoading = findViewById(R.id.originSearchLoading)
        destinationSearchLoading = findViewById(R.id.destinationSearchLoading)
        originCandidateList = findViewById(R.id.originCandidateList)
        destinationCandidateList = findViewById(R.id.destinationCandidateList)
        findViewById<MaterialButton>(R.id.backRouteButton).setOnClickListener { handleBack() }
        swapPlacesButton = findViewById(R.id.swapPlacesButton)
        swapPlacesButton.setOnClickListener { view ->
            animateSwap(view)
            swapPlaces()
        }
        findViewById<MaterialButton>(R.id.saveRouteButton).setOnClickListener { saveRoute() }
        routeEditContent.setOnClickListener { hideCandidateLists() }
    }

    private fun setupPlaceInputs() {
        originController = PlaceInputController(
            context = this,
            input = originInput,
            inputLayout = originInputLayout,
            loadingView = originSearchLoading,
            candidateList = originCandidateList,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { !isFinishing && !isDestroyed },
            onCandidateVisibilityChanged = { visible ->
                if (visible) {
                    destinationController.hideCandidates()
                    ensureCandidateVisible(originInputLayout, originCandidateList)
                }
                syncSwapButtonVisibility()
                syncCandidateBackPriority()
            },
            onUserTextEdited = {
                originTouchedByUser = true
                currentPlaceGeneration += 1
            },
            onPlaceSelected = {
                originTouchedByUser = true
                currentPlaceGeneration += 1
                focusUnselectedPeer(destinationController, destinationInput)
            }
        )
        destinationController = PlaceInputController(
            context = this,
            input = destinationInput,
            inputLayout = destinationInputLayout,
            loadingView = destinationSearchLoading,
            candidateList = destinationCandidateList,
            placeSearchRepository = placeSearchRepository,
            mainHandler = mainHandler,
            searchExecutor = searchExecutor,
            isActive = { !isFinishing && !isDestroyed },
            onCandidateVisibilityChanged = { visible ->
                if (visible) {
                    originController.hideCandidates()
                    ensureCandidateVisible(destinationInputLayout, destinationCandidateList)
                }
                syncSwapButtonVisibility()
                syncCandidateBackPriority()
            },
            onPlaceSelected = {
                focusUnselectedPeer(originController, originInput)
            }
        )
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
    }

    private fun configureLocationEndIcon() {
        originInputLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        originInputLayout.setEndIconDrawable(R.drawable.ic_location_outline)
        originInputLayout.setEndIconContentDescription(getString(R.string.use_my_location))
        originInputLayout.setEndIconOnClickListener {
            requestCurrentOrigin(isAuto = false)
        }
    }

    private fun requestCurrentOrigin(isAuto: Boolean) {
        if (isAuto && originTouchedByUser) return
        val generation = ++currentPlaceGeneration
        if (!LocationPermissionUtils.hasForegroundLocationPermission(this)) {
            if (isAuto && locationPermissionStateStore.isAutoRequestDenied()) {
                handleCurrentOriginFailure(isAuto)
                return
            }
            pendingCurrentPlaceRequest = PendingCurrentPlaceRequest(
                generation = generation,
                isAuto = isAuto
            )
            ActivityCompat.requestPermissions(
                this,
                LocationPermissionUtils.permissions,
                REQUEST_LOCATION_PERMISSION
            )
            return
        }
        continueCurrentOriginWithPermission(generation, isAuto)
    }

    private fun continueCurrentOriginWithPermission(generation: Int, isAuto: Boolean) {
        if (!SystemLocationUtils.isLocationEnabled(this)) {
            if (isAuto) {
                handleCurrentOriginFailure(isAuto)
            } else {
                promptLocationSettingsForCurrentOrigin(
                    PendingCurrentPlaceRequest(
                        generation = generation,
                        isAuto = isAuto
                    )
                )
            }
            return
        }
        resolveCurrentOrigin(generation, isAuto)
    }

    private fun resolveCurrentOrigin(generation: Int, isAuto: Boolean) {
        currentLocationCoordinator.getCurrentLocation { result ->
            if (isFinishing || isDestroyed || currentPlaceGeneration != generation) return@getCurrentLocation
            when (result) {
                is CurrentLocationResult.Success -> {
                    originController.setCurrentLocationSnapshot(result.snapshot)
                    destinationController.setCurrentLocationSnapshot(result.snapshot)
                    originController.setSelectedPlace(MockPlaceNameResolver.resolve(result.snapshot))
                }
                CurrentLocationResult.NoPermission,
                CurrentLocationResult.Timeout,
                CurrentLocationResult.Unavailable -> handleCurrentOriginFailure(isAuto)
            }
        }
    }

    private fun requestCandidateLocationSnapshotIfPermitted() {
        if (!LocationPermissionUtils.hasForegroundLocationPermission(this)) return
        currentLocationCoordinator.getCurrentLocation { result ->
            if (isFinishing || isDestroyed) return@getCurrentLocation
            if (result is CurrentLocationResult.Success) {
                originController.setCurrentLocationSnapshot(result.snapshot)
                destinationController.setCurrentLocationSnapshot(result.snapshot)
            }
        }
    }

    private fun handleCurrentOriginFailure(isAuto: Boolean) {
        if (isAuto) {
            originController.setHelperText("暫時無法取得目前位置，請手動選擇起點")
        } else {
            Toast.makeText(this, "暫時無法取得目前位置", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_LOCATION_PERMISSION) return
        val pending = pendingCurrentPlaceRequest ?: return
        pendingCurrentPlaceRequest = null
        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        if (!granted) {
            if (pending.isAuto) {
                locationPermissionStateStore.setAutoRequestDenied(true)
            }
            handleCurrentOriginFailure(pending.isAuto)
            return
        }
        continueCurrentOriginWithPermission(pending.generation, pending.isAuto)
    }

    private fun promptLocationSettingsForCurrentOrigin(pending: PendingCurrentPlaceRequest) {
        pendingLocationSettingsRetry = pending
        Toast.makeText(this, "請開啟系統定位", Toast.LENGTH_SHORT).show()
        try {
            startActivity(SystemLocationUtils.settingsIntent())
        } catch (_: ActivityNotFoundException) {
            pendingLocationSettingsRetry = null
            handleCurrentOriginFailure(pending.isAuto)
        }
    }

    private fun retryCurrentOriginAfterLocationSettings() {
        val pending = pendingLocationSettingsRetry ?: return
        pendingLocationSettingsRetry = null
        if (currentPlaceGeneration != pending.generation) return
        if (!SystemLocationUtils.isLocationEnabled(this)) {
            handleCurrentOriginFailure(pending.isAuto)
            return
        }
        resolveCurrentOrigin(pending.generation, pending.isAuto)
    }

    private fun handleBack() {
        if (!hideCandidateLists()) {
            finish()
        }
    }

    private fun hideCandidateLists(): Boolean {
        val originHidden = originController.hideCandidates()
        val destinationHidden = destinationController.hideCandidates()
        return originHidden || destinationHidden
    }

    private fun syncCandidateBackPriority() {
        updateCandidateBackPriority(
            originController.isCandidateVisible() ||
                destinationController.isCandidateVisible()
        )
    }

    private fun syncSwapButtonVisibility() {
        swapPlacesButton.visibility = if (
            originController.isCandidateVisible() ||
            destinationController.isCandidateVisible()
        ) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun updateCandidateBackPriority(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (enabled && candidateBackCallback == null) {
            val callback = OnBackInvokedCallback { hideCandidateLists() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
            candidateBackCallback = callback
        } else if (!enabled) {
            candidateBackCallback?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            candidateBackCallback = null
        }
    }

    private fun focusUnselectedPeer(
        peerController: PlaceInputController,
        peerInput: MaterialAutoCompleteTextView
    ) {
        if (peerController.selectedPlace != null) return
        peerInput.requestFocus()
        peerInput.post {
            getSystemService(InputMethodManager::class.java)
                .showSoftInput(peerInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun ensureCandidateVisible(
        inputLayout: TextInputLayout,
        candidateList: RecyclerView
    ) {
        candidateList.post {
            val parent = inputLayout.parent as? View ?: return@post
            val rect = Rect(0, inputLayout.top, parent.width, candidateList.bottom)
            parent.requestRectangleOnScreen(rect, true)
            scrollCandidateIntoVisibleArea(candidateList)
            candidateList.postDelayed({
                ViewCompat.requestApplyInsets(candidateList)
                candidateList.post {
                    scrollCandidateIntoVisibleArea(candidateList)
                    ViewCompat.requestApplyInsets(candidateList)
                }
            }, CANDIDATE_VISIBILITY_RECHECK_MS)
        }
    }

    private fun scrollCandidateIntoVisibleArea(candidateList: RecyclerView) {
        if (candidateList.height <= 0) return
        val visibleFrame = Rect()
        routeEditScroll.getWindowVisibleDisplayFrame(visibleFrame)
        val candidateLocation = IntArray(2)
        candidateList.getLocationOnScreen(candidateLocation)
        val candidateBottom = candidateLocation[1] + candidateList.height
        val desiredBottom = visibleFrame.bottom - dp(CANDIDATE_BOTTOM_SAFE_INSET_DP)
        val overlap = candidateBottom - desiredBottom
        if (overlap > 0) {
            routeEditScroll.scrollBy(0, overlap)
        }
    }

    private fun animateSwap(view: View) {
        view.animate()
            .rotationBy(180f)
            .setDuration(220L)
            .start()
    }

    private fun setupMode() {
        if (routeId == NO_ROUTE_ID) {
            val isClone = intent.hasExtra(EXTRA_PREFILL_NAME)
            val pageTitle = if (isClone) "複製路線" else "新增路線"
            title = pageTitle
            screenTitleText.text = pageTitle
            applyPrefillIfPresent()
            if (isClone) {
                requestCandidateLocationSnapshotIfPermitted()
            } else {
                requestCurrentOrigin(isAuto = true)
            }
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
        requestCandidateLocationSnapshotIfPermitted()
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

    private fun swapPlaces() {
        originController.swapWith(destinationController)
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

    private data class PendingCurrentPlaceRequest(
        val generation: Int,
        val isAuto: Boolean
    )

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
        private const val REQUEST_LOCATION_PERMISSION = 401
        private const val CANDIDATE_VISIBILITY_RECHECK_MS = 80L
        private const val CANDIDATE_BOTTOM_SAFE_INSET_DP = 8
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
