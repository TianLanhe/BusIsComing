package com.example.busiscomming.ui.main

import android.content.Intent
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
import com.example.busiscomming.data.repository.MockBusRouteRepository
import com.example.busiscomming.data.repository.RouteConfigRepository
import com.example.busiscomming.ui.common.applyStatusBarPadding
import com.example.busiscomming.ui.edit.RouteEditActivity
import com.example.busiscomming.ui.manage.RouteManageActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class MainActivity : AppCompatActivity() {
    private lateinit var routeConfigRepository: RouteConfigRepository
    private val busRouteRepository: BusRouteRepository = MockBusRouteRepository()

    private lateinit var routeSelector: MaterialAutoCompleteTextView
    private lateinit var queryButton: MaterialButton
    private lateinit var emptyRouteState: LinearLayout
    private lateinit var queryControls: LinearLayout
    private lateinit var resultSection: LinearLayout
    private lateinit var emptyResultText: TextView
    private lateinit var resultList: RecyclerView
    private lateinit var priceHeader: TextView
    private lateinit var waitTimeHeader: TextView
    private lateinit var busRouteAdapter: BusRouteAdapter

    private var routeConfigs: List<RouteConfig> = emptyList()
    private var selectedRoute: RouteConfig? = null
    private var currentResults: List<BusRouteOption> = emptyList()
    private var sortField: SortField? = null
    private var sortDirection: SortDirection = SortDirection.ASC

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
        priceHeader = findViewById(R.id.priceHeader)
        waitTimeHeader = findViewById(R.id.waitTimeHeader)
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
        priceHeader.setOnClickListener { sortBy(SortField.PRICE) }
        waitTimeHeader.setOnClickListener { sortBy(SortField.WAIT_TIME) }
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

        currentResults = busRouteRepository.searchRoutes(route.origin, route.destination)
        sortField = null
        sortDirection = SortDirection.ASC
        updateSortHeaders()
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
        updateSortHeaders()
        displayResults(currentResults)
    }

    private fun displayResults(results: List<BusRouteOption>) {
        if (results.isEmpty()) {
            busRouteAdapter.submitList(emptyList())
            resultList.visibility = View.GONE
            emptyResultText.visibility = View.VISIBLE
        } else {
            emptyResultText.visibility = View.GONE
            resultList.visibility = View.VISIBLE
            busRouteAdapter.submitList(results)
        }
    }

    private fun clearResults() {
        currentResults = emptyList()
        sortField = null
        sortDirection = SortDirection.ASC
        updateSortHeaders()
        busRouteAdapter.submitList(emptyList())
        resultList.visibility = View.GONE
        emptyResultText.visibility = View.GONE
    }

    private fun updateSortHeaders() {
        priceHeader.text = headerText("价格", SortField.PRICE)
        waitTimeHeader.text = headerText("预计等候时间", SortField.WAIT_TIME)
    }

    private fun headerText(label: String, field: SortField): String {
        if (sortField != field) return label
        return "$label ${if (sortDirection == SortDirection.ASC) "↑" else "↓"}"
    }
}
