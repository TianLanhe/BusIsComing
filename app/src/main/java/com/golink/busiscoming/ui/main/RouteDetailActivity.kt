package com.golink.busiscoming.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.R
import com.golink.busiscoming.data.localization.AppLanguageRuntime
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.RouteDetail
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.CitybusFirstLegEtaService
import com.golink.busiscoming.data.repository.CitybusRouteDetailRepository
import com.golink.busiscoming.data.repository.RouteDetailRepository
import com.golink.busiscoming.ui.common.applyStatusBarPadding
import com.google.android.material.appbar.MaterialToolbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RouteDetailActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newFixedThreadPool(2)
    private val repository by lazy { RouteDetailRuntime.repositoryFactory() }
    private val expandedLegIndexes = linkedSetOf<Int>()
    private lateinit var args: RouteDetailLaunchArgs
    private lateinit var adapter: RouteDetailAdapter
    private var detail: RouteDetail? = null
    private var waitTimeState: WaitTimeState = WaitTimeState.Loading
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decoded = intent.extras?.let(RouteDetailLaunchArgs::fromBundle)
        if (decoded == null) {
            finish()
            return
        }
        args = decoded
        waitTimeState = args.waitTimeState
        savedInstanceState?.getIntArray(STATE_EXPANDED)?.forEach(expandedLegIndexes::add)
        setContentView(R.layout.activity_route_detail)

        val toolbar: MaterialToolbar = findViewById(R.id.routeDetailToolbar)
        toolbar.applyStatusBarPadding()
        toolbar.navigationContentDescription = getString(R.string.route_detail_navigate_up)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        adapter = RouteDetailAdapter(::toggleLeg, ::retry)
        findViewById<RecyclerView>(R.id.routeDetailList).apply {
            layoutManager = LinearLayoutManager(this@RouteDetailActivity)
            adapter = this@RouteDetailActivity.adapter
            itemAnimator = null
        }
        showLaunchSummary(loading = args.routeDetailQuery != null)
        if (args.routeDetailQuery == null) {
            showLaunchSummary(loading = false, failed = true)
        } else {
            loadDetail()
            refreshFirstLegEta()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putIntArray(STATE_EXPANDED, expandedLegIndexes.toIntArray())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        generation += 1
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun retry() {
        showLaunchSummary(loading = true)
        loadDetail()
        refreshFirstLegEta()
    }

    private fun loadDetail() {
        val requestGeneration = ++generation
        val languageVersion = AppLanguageRuntime.snapshot().version
        val route = args.toRoute()
        executor.execute {
            val result = runCatching { repository.loadRouteDetail(route) }
            mainHandler.post {
                if (!isCurrent(requestGeneration, languageVersion)) return@post
                result.onSuccess {
                    detail = it
                    renderDetail()
                }.onFailure {
                    showLaunchSummary(loading = false, failed = true)
                }
            }
        }
    }

    private fun refreshFirstLegEta() {
        val query = args.firstLegEtaQuery ?: return
        val requestGeneration = generation
        val languageVersion = AppLanguageRuntime.snapshot().version
        executor.execute {
            val state = runCatching { RouteDetailRuntime.etaResolver(query) }
                .getOrElse { WaitTimeState.Unavailable(com.golink.busiscoming.data.model.EtaUnavailableReason.UNEXPECTED_ERROR) }
            mainHandler.post {
                if (!isCurrent(requestGeneration, languageVersion)) return@post
                waitTimeState = state
                if (detail != null) renderDetail()
            }
        }
    }

    private fun isCurrent(requestGeneration: Int, languageVersion: Long): Boolean {
        return requestGeneration == generation &&
            languageVersion == AppLanguageRuntime.snapshot().version &&
            !isFinishing && !isDestroyed
    }

    private fun toggleLeg(index: Int) {
        if (!expandedLegIndexes.add(index)) expandedLegIndexes.remove(index)
        renderDetail()
    }

    private fun renderDetail() {
        val value = detail ?: return
        adapter.submitList(RouteDetailUiFormatter.items(value, expandedLegIndexes, waitTimeState))
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
                isWalkingDistanceComplete = false
            )
        )
        if (loading) items += RouteDetailUiItem.Loading
        if (failed) items += RouteDetailUiItem.Error
        adapter.submitList(items)
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

    private companion object {
        const val STATE_EXPANDED = "route_detail.expanded"
    }
}

object RouteDetailNavigator {
    fun open(context: Context, route: BusRouteOption) {
        context.startActivity(
            Intent(context, RouteDetailActivity::class.java).apply {
                putExtras(RouteDetailLaunchArgs.fromRoute(route).toBundle())
            }
        )
    }
}

object RouteDetailRuntime {
    private val defaultRepositoryFactory: () -> RouteDetailRepository = { CitybusRouteDetailRepository() }
    private val defaultEtaResolver: (FirstLegEtaQuery) -> WaitTimeState =
        { query -> CitybusFirstLegEtaService().resolveWaitTime(query) }

    @Volatile
    var repositoryFactory: () -> RouteDetailRepository = defaultRepositoryFactory

    @Volatile
    var etaResolver: (FirstLegEtaQuery) -> WaitTimeState = defaultEtaResolver

    fun reset() {
        repositoryFactory = defaultRepositoryFactory
        etaResolver = defaultEtaResolver
    }
}
