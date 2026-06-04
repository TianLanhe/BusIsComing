package com.example.busiscomming.ui.main

import android.content.res.ColorStateList
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscomming.R
import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.RouteConfig
import com.example.busiscomming.data.model.SortDirection
import com.example.busiscomming.data.model.SortField
import com.example.busiscomming.data.model.WaitTimeState
import com.example.busiscomming.data.repository.BusRouteQueryCallback
import com.example.busiscomming.data.repository.BusRouteRepository
import com.example.busiscomming.data.repository.BusRouteSorter
import com.example.busiscomming.data.repository.CitybusBusRouteRepository
import com.example.busiscomming.data.repository.RouteConfigRepository
import com.example.busiscomming.ui.common.applyStatusBarPadding
import com.example.busiscomming.ui.edit.RouteEditActivity
import com.example.busiscomming.ui.manage.RouteManageActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var routeConfigRepository: RouteConfigRepository
    private val busRouteRepository: BusRouteRepository = CitybusBusRouteRepository()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val queryExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var routeSelector: MaterialAutoCompleteTextView
    private lateinit var queryButton: MaterialButton
    private lateinit var emptyRouteState: LinearLayout
    private lateinit var queryControls: LinearLayout
    private lateinit var resultSection: LinearLayout
    private lateinit var sortControls: LinearLayout
    private lateinit var resultStatusCard: MaterialCardView
    private lateinit var resultStatusProgress: ProgressBar
    private lateinit var resultStatusTitle: TextView
    private lateinit var resultStatusMessage: TextView
    private lateinit var resultList: RecyclerView
    private lateinit var sortButtons: Map<SortField, MaterialButton>
    private lateinit var busRouteAdapter: BusRouteAdapter

    private var routeConfigs: List<RouteConfig> = emptyList()
    private var selectedRoute: RouteConfig? = null
    private var currentResults: List<BusRouteOption> = emptyList()
    private var sortField: SortField? = null
    private var sortDirection: SortDirection = SortDirection.ASC
    private var querySequence: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "BusIsComming"

        routeConfigRepository = RouteConfigRepository(this)
        findViewById<View>(R.id.mainRoot).applyStatusBarPadding()
        bindViews()
        setupResultList()
        setupActions()
    }

    override fun onDestroy() {
        invalidateActiveQuery()
        mainHandler.removeCallbacksAndMessages(null)
        queryExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        loadRouteConfigs()
    }

    private fun bindViews() {
        routeSelector = findViewById(R.id.routeSelector)
        queryButton = findViewById(R.id.queryButton)
        emptyRouteState = findViewById(R.id.emptyRouteState)
        queryControls = findViewById(R.id.queryControls)
        resultSection = findViewById(R.id.resultSection)
        sortControls = findViewById(R.id.sortControls)
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
        busRouteAdapter = BusRouteAdapter()
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
        val previousRouteConfigs = routeConfigs
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

        val labels = routeConfigs.map { it.displayLabel() }
        routeSelector.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
        )

        selectedRoute = routeConfigs.firstOrNull { it.id == previousSelectedId } ?: routeConfigs.first()
        val selectedIndex = routeConfigs.indexOfFirst { it.id == selectedRoute?.id }
        showSelectedRoute()
        routeSelector.setOnItemClickListener { _, _, position, _ ->
            selectedRoute = routeConfigs.getOrNull(position)
            showSelectedRoute()
            clearResults()
        }
        if (previousRouteConfigs != routeConfigs) {
            clearResults()
        }
    }

    private fun querySelectedRoute() {
        val route = selectedRoute
        if (route == null) {
            Toast.makeText(this, "请先新增路线", Toast.LENGTH_SHORT).show()
            return
        }

        val queryId = ++querySequence
        busRouteRepository.cancelProgressiveQueries()
        showLoadingState()
        queryExecutor.execute {
            busRouteRepository.searchRoutesProgressively(
                route.origin,
                route.destination,
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
            resultList.visibility = View.GONE
            showStatus(
                title = "暂无可用巴士路线",
                message = "可以换一条常用路线，或稍后再试。",
                showProgress = false
            )
        } else {
            hideStatus()
            sortControls.visibility = View.VISIBLE
            val shouldAnimate = resultList.visibility != View.VISIBLE
            resultList.visibility = View.VISIBLE
            busRouteAdapter.submitList(results)
            if (shouldAnimate) {
                animateIn(resultList)
            }
        }
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
        resultList.visibility = View.GONE
        hideStatus()
    }

    private fun invalidateActiveQuery() {
        querySequence += 1
        busRouteRepository.cancelProgressiveQueries()
    }

    private fun showLoadingState() {
        setQueryLoading(true)
        currentResults = emptyList()
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        resultList.visibility = View.GONE
        showStatus(
            title = "正在查询路线",
            message = "正在匹配可用巴士方案和候车时间。",
            showProgress = true
        )
    }

    private fun displayFailure() {
        busRouteAdapter.submitList(emptyList())
        sortControls.visibility = View.GONE
        resultList.visibility = View.GONE
        showStatus(
            title = "路线查询失败",
            message = "请稍后重试，或换一条常用路线再查。",
            showProgress = false
        )
    }

    private fun setQueryLoading(isLoading: Boolean) {
        queryButton.isEnabled = !isLoading
        queryButton.text = if (isLoading) "查询中..." else "查询"
    }

    private fun showSelectedRoute() {
        val route = selectedRoute ?: return
        val title = route.name
        val subtitle = route.pathLabel()
        val displayText = "$title\n$subtitle"
        val styledText = SpannableString(displayText).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(RelativeSizeSpan(1.08f), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@MainActivity, R.color.bus_text_primary)),
                0,
                title.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                RelativeSizeSpan(0.82f),
                title.length + 1,
                displayText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@MainActivity, R.color.bus_text_secondary)),
                title.length + 1,
                displayText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        routeSelector.setText(styledText, false)
        routeSelector.setSelection(routeSelector.text?.length ?: 0)
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
            SortField.ROUTE -> "路线"
            SortField.PRICE -> "价格"
            SortField.DURATION -> "总耗时"
            SortField.ARRIVAL -> "候车"
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
}
