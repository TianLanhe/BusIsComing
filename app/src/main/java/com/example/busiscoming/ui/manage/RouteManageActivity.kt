package com.example.busiscoming.ui.manage

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscoming.R
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.repository.RouteConfigRepository
import com.example.busiscoming.ui.common.applyStatusBarPadding
import com.example.busiscoming.ui.edit.RouteEditActivity
import com.google.android.material.button.MaterialButton

class RouteManageActivity : AppCompatActivity() {
    private lateinit var repository: RouteConfigRepository
    private lateinit var adapter: RouteConfigAdapter
    private lateinit var routeList: RecyclerView
    private lateinit var emptyRoutesState: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_manage)
        title = "路線管理"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.routeManageRoot).applyStatusBarPadding()
        repository = RouteConfigRepository(this)
        routeList = findViewById(R.id.routeConfigList)
        emptyRoutesState = findViewById(R.id.emptyRoutesState)

        adapter = RouteConfigAdapter(
            onEdit = { route -> openEdit(route) },
            onClone = { route -> openClone(route) },
            onDelete = { route -> confirmDelete(route) }
        )
        routeList.layoutManager = LinearLayoutManager(this)
        routeList.adapter = adapter

        findViewById<MaterialButton>(R.id.addRouteButton).setOnClickListener { openAdd() }
        findViewById<MaterialButton>(R.id.emptyManageAddRouteButton).setOnClickListener { openAdd() }
    }

    override fun onResume() {
        super.onResume()
        loadRoutes()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadRoutes() {
        val routes = repository.getAll()
        adapter.submitList(routes)
        emptyRoutesState.visibility = if (routes.isEmpty()) View.VISIBLE else View.GONE
        routeList.visibility = if (routes.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openAdd() {
        startActivity(Intent(this, RouteEditActivity::class.java))
    }

    private fun openEdit(route: RouteConfig) {
        val intent = Intent(this, RouteEditActivity::class.java)
            .putExtra(RouteEditActivity.EXTRA_ROUTE_ID, route.id)
        startActivity(intent)
    }

    private fun openClone(route: RouteConfig) {
        val intent = Intent(this, RouteEditActivity::class.java)
            .putExtra(RouteEditActivity.EXTRA_PREFILL_NAME, "${route.name}（副本）")
            .putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_NAME, route.origin.name)
            .putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_LATITUDE, route.origin.latitude)
            .putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_LONGITUDE, route.origin.longitude)
            .putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_NAME, route.destination.name)
            .putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_LATITUDE, route.destination.latitude)
            .putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_LONGITUDE, route.destination.longitude)
        startActivity(intent)
    }

    private fun confirmDelete(route: RouteConfig) {
        AlertDialog.Builder(this)
            .setTitle("刪除路線")
            .setMessage("確定刪除“${route.name}”？")
            .setNegativeButton("取消", null)
            .setPositiveButton("刪除") { _, _ ->
                repository.delete(route.id)
                loadRoutes()
            }
            .show()
    }
}
