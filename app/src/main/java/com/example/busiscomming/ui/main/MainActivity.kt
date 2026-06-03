package com.example.busiscomming.ui.main

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscomming.R
import com.example.busiscomming.data.model.BusRouteOption
import com.example.busiscomming.data.model.RouteConfig
import com.example.busiscomming.data.model.SortDirection
import com.example.busiscomming.data.model.SortField
import com.example.busiscomming.data.repository.BusRouteRepository
import com.example.busiscomming.data.repository.BusRouteSorter
import com.example.busiscomming.data.repository.CitybusBusRouteRepository
import com.example.busiscomming.data.repository.RouteConfigRepository
import com.example.busiscomming.ui.common.applyStatusBarPadding
import com.example.busiscomming.ui.edit.RouteEditActivity
import com.example.busiscomming.ui.manage.RouteManageActivity
import com.google.android.material.button.MaterialButton
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
    private lateinit var emptyResultText: TextView
    private lateinit var resultList: RecyclerView
    private lateinit var routeHeader: TextView
    private lateinit var priceHeader: TextView
    private lateinit var durationHeader: TextView
    private lateinit var arrivalHeader: TextView
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
        querySequence += 1
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
        emptyResultText = findViewById(R.id.emptyResultText)
        resultList = findViewById(R.id.busRouteList)
        routeHeader = findViewById(R.id.routeHeader)
        priceHeader = findViewById(R.id.priceHeader)
        durationHeader = findViewById(R.id.durationHeader)
        arrivalHeader = findViewById(R.id.arrivalHeader)
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
        routeHeader.setOnClickListener { sortBy(SortField.ROUTE) }
        priceHeader.setOnClickListener { sortBy(SortField.PRICE) }
        durationHeader.setOnClickListener { sortBy(SortField.DURATION) }
        arrivalHeader.setOnClickListener { sortBy(SortField.ARRIVAL) }
    }

    private fun loadRouteConfigs() {
        val previousSelectedId = selectedRoute?.id
        val previousRouteConfigs = routeConfigs
        routeConfigs = routeConfigRepository.getAll()

        if (routeConfigs.isEmpty()) {
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
        routeSelector.setText(labels.getOrElse(selectedIndex) { labels.first() }, false)
        routeSelector.setOnItemClickListener { _, _, position, _ ->
            selectedRoute = routeConfigs.getOrNull(position)
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
        showLoadingState()
        queryExecutor.execute {
            val result = runCatching { busRouteRepository.searchRoutes(route.origin, route.destination) }
            mainHandler.post {
                if (querySequence != queryId || isFinishing || isDestroyed) return@post

                setQueryLoading(false)
                result
                    .onSuccess { routes ->
                        currentResults = routes
                        sortField = null
                        sortDirection = SortDirection.ASC
                        updateSortHeaders()
                        displayResults(routes)
                    }
                    .onFailure {
                        currentResults = emptyList()
                        sortField = null
                        sortDirection = SortDirection.ASC
                        updateSortHeaders()
                        displayFailure()
                    }
            }
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
        updateSortHeaders()
        displayResults(currentResults)
    }

    private fun displayResults(results: List<BusRouteOption>) {
        if (results.isEmpty()) {
            busRouteAdapter.submitList(emptyList())
            resultList.visibility = View.GONE
            emptyResultText.text = "暂无可用巴士路线"
            emptyResultText.visibility = View.VISIBLE
        } else {
            emptyResultText.visibility = View.GONE
            resultList.visibility = View.VISIBLE
            busRouteAdapter.submitList(results)
        }
    }

    private fun clearResults() {
        querySequence += 1
        setQueryLoading(false)
        currentResults = emptyList()
        sortField = null
        sortDirection = SortDirection.ASC
        updateSortHeaders()
        busRouteAdapter.submitList(emptyList())
        resultList.visibility = View.GONE
        emptyResultText.visibility = View.GONE
    }

    private fun showLoadingState() {
        setQueryLoading(true)
        currentResults = emptyList()
        busRouteAdapter.submitList(emptyList())
        resultList.visibility = View.GONE
        emptyResultText.text = "查询中..."
        emptyResultText.visibility = View.VISIBLE
    }

    private fun displayFailure() {
        busRouteAdapter.submitList(emptyList())
        resultList.visibility = View.GONE
        emptyResultText.text = "路线查询失败，请稍后重试"
        emptyResultText.visibility = View.VISIBLE
    }

    private fun setQueryLoading(isLoading: Boolean) {
        queryButton.isEnabled = !isLoading
        queryButton.text = if (isLoading) "查询中..." else "查询"
    }

    private fun updateSortHeaders() {
        routeHeader.text = headerText("路线", SortField.ROUTE)
        priceHeader.text = headerText("价格\n(HKD)", SortField.PRICE)
        durationHeader.text = headerText("总耗时\n(分钟)", SortField.DURATION)
        arrivalHeader.text = headerText("预计汽车到站时间\n(分钟)", SortField.ARRIVAL)
    }

    private fun headerText(label: String, field: SortField): String {
        if (sortField != field) return label
        return "$label ${if (sortDirection == SortDirection.ASC) "↑" else "↓"}"
    }
}
