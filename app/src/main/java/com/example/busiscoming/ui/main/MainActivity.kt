package com.example.busiscoming.ui.main

import android.content.res.ColorStateList
import android.content.Intent
import android.view.Gravity
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscoming.R
import com.example.busiscoming.data.model.BusRouteOption
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.model.RouteCardStopPreview
import com.example.busiscoming.data.model.SortDirection
import com.example.busiscoming.data.model.SortField
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.repository.BusRouteQueryCallback
import com.example.busiscoming.data.repository.BusRouteRepository
import com.example.busiscoming.data.repository.BusRouteSorter
import com.example.busiscoming.data.repository.CitybusBusRouteRepository
import com.example.busiscoming.data.repository.CitybusRouteDetailRepository
import com.example.busiscoming.data.repository.RouteDetailRepository
import com.example.busiscoming.data.repository.RouteConfigRepository
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
    private lateinit var sortControls: LinearLayout
    private lateinit var resultSummaryContainer: LinearLayout
    private lateinit var resultSummaryText: TextView
    private lateinit var resultUpdatedAtText: TextView
    private lateinit var resultStatusCard: MaterialCardView
    private lateinit var resultStatusProgress: ProgressBar
    private lateinit var resultStatusTitle: TextView
    private lateinit var resultStatusMessage: TextView
    private lateinit var resultList: RecyclerView
    private lateinit var sortButtons: Map<SortField, MaterialButton>
    private lateinit var busRouteAdapter: BusRouteAdapter
    private lateinit var routeDetailBottomSheet: RouteDetailBottomSheet
    private lateinit var temporaryRouteBottomSheet: TemporaryRouteBottomSheet

    private var routeConfigs: List<RouteConfig> = emptyList()
    private var selectedRoute: RouteConfig? = null
    private var currentResults: List<BusRouteOption> = emptyList()
    private var sortField: SortField? = null
    private var sortDirection: SortDirection = SortDirection.ASC
    private var querySequence: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "BusIsComing"

        routeConfigRepository = RouteConfigRepository(this)
        routeDetailBottomSheet = RouteDetailBottomSheet(this, routeDetailRepository)
        temporaryRouteBottomSheet = TemporaryRouteBottomSheet(
            context = this,
            routeConfigRepository = routeConfigRepository,
            mainHandler = mainHandler,
            searchExecutor = placeSearchExecutor,
            onQuery = { origin, destination -> queryRoute(origin, destination, null) },
            onSaved = { savedRouteId -> selectSavedRouteAfterCreate(savedRouteId) }
        )
        findViewById<View>(R.id.mainRoot).applyStatusBarPadding()
        bindViews()
        setupResultList()
        setupActions()
    }

    override fun onDestroy() {
        invalidateActiveQuery()
        mainHandler.removeCallbacksAndMessages(null)
        routeDetailBottomSheet.dispose()
        temporaryRouteBottomSheet.dispose()
        queryExecutor.shutdownNow()
        placeSearchExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        loadRouteConfigs()
    }

    private fun bindViews() {
        queryButton = findViewById(R.id.queryButton)
        emptyTemporaryQueryButton = findViewById(R.id.emptyTemporaryQueryButton)
        emptyRouteState = findViewById(R.id.emptyRouteState)
        queryControls = findViewById(R.id.queryControls)
        routeShortcutCardsContainer = findViewById(R.id.routeShortcutCardsContainer)
        routePickerButton = findViewById(R.id.routePickerButton)
        resultSection = findViewById(R.id.resultSection)
        sortControls = findViewById(R.id.sortControls)
        resultSummaryContainer = findViewById(R.id.resultSummaryContainer)
        resultSummaryText = findViewById(R.id.resultSummaryText)
        resultUpdatedAtText = findViewById(R.id.resultUpdatedAtText)
        resultStatusCard = findViewById(R.id.resultStatusCard)
        resultStatusProgress = findViewById(R.id.resultStatusProgress)
        resultStatusTitle = findViewById(R.id.resultStatusTitle)
        resultStatusMessage = findViewById(R.id.resultStatusMessage)
        resultList = findViewById(R.id.busRouteList)
        sortButtons = mapOf(
            SortField.ROUTE to findViewById(R.id.sortRouteButton),
            SortField.PRICE to findViewById(R.id.sortPriceButton),
            SortField.DURATION to findViewById(R.id.sortDurationButton),
            SortField.ARRIVAL to findViewById(R.id.sortArrivalButton),
            SortField.WALKING_DISTANCE to findViewById(R.id.sortWalkingDistanceButton)
        )
    }

    private fun setupResultList() {
        busRouteAdapter = BusRouteAdapter(onRouteClick = ::showRouteDetail)
        resultList.layoutManager = LinearLayoutManager(this)
        resultList.adapter = busRouteAdapter
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.manageRoutesButton).setOnClickListener {
            startActivity(Intent(this, RouteManageActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.emptyAddRouteButton).setOnClickListener {
            startActivity(Intent(this, RouteEditActivity::class.java))
        }
        emptyTemporaryQueryButton.setOnClickListener { showTemporaryRouteSheet() }
        routePickerButton.setOnClickListener { showRoutePicker() }
        queryButton.setOnClickListener { querySelectedRoute() }
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
            emptyRouteState.visibility = View.VISIBLE
            queryControls.visibility = View.GONE
            resultSection.visibility = View.GONE
            currentResults = emptyList()
            busRouteAdapter.submitList(emptyList())
            return
        }

        emptyRouteState.visibility = View.GONE
        queryControls.visibility = View.VISIBLE
        resultSection.visibility = View.VISIBLE

        selectedRoute = routeConfigs.firstOrNull { it.id == previousSelectedId } ?: routeConfigs.first()
        renderRouteShortcuts()
        if (previousRouteSnapshot != routeIdentitySnapshot(routeConfigs)) {
            clearResults()
        }
    }

    private fun querySelectedRoute() {
        val route = selectedRoute
        if (route == null) {
            Toast.makeText(this, "請先選擇路線或查詢臨時起點和終點", Toast.LENGTH_SHORT).show()
            return
        }
        queryRoute(route.origin, route.destination, route)
    }

    private fun queryRoute(origin: com.example.busiscoming.data.model.Place, destination: com.example.busiscoming.data.model.Place, sourceRoute: RouteConfig?) {
        sourceRoute?.let { route ->
            routeConfigRepository.recordUsage(route.id)
            routeConfigs = routeConfigRepository.getAll()
            selectedRoute = routeConfigs.firstOrNull { it.id == route.id } ?: route
            renderRouteShortcuts()
        }

        val queryId = ++querySequence
        busRouteRepository.cancelProgressiveQueries()
        showLoadingState()
        queryExecutor.execute {
            busRouteRepository.searchRoutesProgressively(
                origin,
                destination,
                object : BusRouteQueryCallback {
                    override fun onInitialRoutes(routes: List<BusRouteOption>) {
                        mainHandler.post {
                            if (querySequence != queryId || isFinishing || isDestroyed) return@post
                            setQueryLoading(false)
                            showInitialRoutes(routes)
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
                        mainHandler.post {
                            if (querySequence != queryId || isFinishing || isDestroyed) return@post
                            setQueryLoading(false)
                            currentResults = emptyList()
                            sortField = null
                            sortDirection = SortDirection.ASC
                            updateSortControls()
                            displayFailure()
                        }
                    }
                }
            )
        }
    }

    private fun showInitialRoutes(routes: List<BusRouteOption>) {
        sortField = SortField.DURATION
        sortDirection = SortDirection.ASC
        currentResults = BusRouteSorter.sort(routes, SortField.DURATION, sortDirection)
        updateSortControls()
        updateResultSummary(routes)
        displayResults(currentResults)
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
            resultList.visibility = View.GONE
            showStatus(
                title = "暫無可用巴士路線",
                message = "可以換一條常用路線，或稍後再試。",
                showProgress = false
            )
        } else {
            hideStatus()
            sortControls.visibility = View.VISIBLE
            resultSummaryContainer.visibility = View.VISIBLE
            val shouldAnimate = resultList.visibility != View.VISIBLE
            resultList.visibility = View.VISIBLE
            busRouteAdapter.submitList(results)
            if (shouldAnimate) {
                animateIn(resultList)
            }
        }
    }

    private fun showRouteDetail(route: BusRouteOption) {
        routeDetailBottomSheet.show(route)
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
        setQueryLoading(false)
        currentResults = emptyList()
        sortField = null
        sortDirection = SortDirection.ASC
        updateSortControls()
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        hideResultSummary()
        resultList.visibility = View.GONE
        hideStatus()
    }

    private fun invalidateActiveQuery() {
        querySequence += 1
        busRouteRepository.cancelProgressiveQueries()
    }

    private fun renderRouteShortcuts() {
        routeShortcutCardsContainer.removeAllViews()
        val visibleRoutes = if (routeConfigs.size <= 2) routeConfigs else routeConfigs.take(3)
        visibleRoutes.forEachIndexed { index, route ->
            routeShortcutCardsContainer.addView(createRouteShortcutCard(route, index))
        }
        routePickerButton.visibility = if (routeConfigs.size >= 3) View.VISIBLE else View.GONE
    }

    private fun createRouteShortcutCard(route: RouteConfig, index: Int): MaterialCardView {
        val isSelected = selectedRoute?.id == route.id
        return MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(8)
            }
            radius = dp(8).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.bus_card_surface))
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
                addView(TextView(context).apply {
                    text = route.name
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(context, R.color.bus_text_primary))
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
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
        selectedRoute = route
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

    private fun selectSavedRouteAfterCreate(savedRouteId: Long) {
        routeConfigs = routeConfigRepository.getAll()
        selectedRoute = routeConfigs.firstOrNull { it.id == savedRouteId } ?: selectedRoute
        emptyRouteState.visibility = if (routeConfigs.isEmpty()) View.VISIBLE else View.GONE
        queryControls.visibility = if (routeConfigs.isEmpty()) View.GONE else View.VISIBLE
        resultSection.visibility = if (routeConfigs.isEmpty()) View.GONE else View.VISIBLE
        renderRouteShortcuts()
        clearResults()
    }

    private fun showLoadingState() {
        setQueryLoading(true)
        currentResults = emptyList()
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        hideResultSummary()
        resultList.visibility = View.GONE
        showStatus(
            title = "正在查詢路線",
            message = "正在匹配可用巴士方案和候車時間。",
            showProgress = true
        )
    }

    private fun displayFailure() {
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        hideResultSummary()
        resultList.visibility = View.GONE
        showStatus(
            title = "路線查詢失敗",
            message = "請稍後重試，或換一條常用路線再查。",
            showProgress = false
        )
    }

    private fun setQueryLoading(isLoading: Boolean) {
        queryButton.isEnabled = !isLoading
        queryButton.text = if (isLoading) "查詢中..." else "查詢"
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
        val origin: com.example.busiscoming.data.model.Place,
        val destination: com.example.busiscoming.data.model.Place
    )

    companion object {
        private val RESULT_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat {
                return SimpleDateFormat("HH:mm:ss", Locale.US)
            }
        }
    }
}
