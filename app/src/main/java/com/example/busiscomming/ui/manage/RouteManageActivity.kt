package com.example.busiscomming.ui.manage

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.busiscomming.R
import com.example.busiscomming.data.model.RouteConfig
import com.example.busiscomming.data.repository.RouteConfigRepository
import com.example.busiscomming.ui.common.applyStatusBarPadding
import com.example.busiscomming.ui.edit.RouteEditActivity
import com.google.android.material.button.MaterialButton

class RouteManageActivity : AppCompatActivity() {
    private lateinit var repository: RouteConfigRepository
    private lateinit var adapter: RouteConfigAdapter
    private lateinit var routeList: RecyclerView
    private lateinit var emptyRoutesText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_manage)
        title = "路线管理"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.routeManageRoot).applyStatusBarPadding()
        repository = RouteConfigRepository(this)
        routeList = findViewById(R.id.routeConfigList)
        emptyRoutesText = findViewById(R.id.emptyRoutesText)

        adapter = RouteConfigAdapter(
            onEdit = { route -> openEdit(route) },
            onClone = { route -> openClone(route) },
            onDelete = { route -> confirmDelete(route) }
        )
        routeList.layoutManager = LinearLayoutManager(this)
        routeList.adapter = adapter

        findViewById<MaterialButton>(R.id.addRouteButton).setOnClickListener {
            startActivity(Intent(this, RouteEditActivity::class.java))
        }
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
        emptyRoutesText.visibility = if (routes.isEmpty()) View.VISIBLE else View.GONE
        routeList.visibility = if (routes.isEmpty()) View.GONE else View.VISIBLE
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
            .setTitle("删除路线")
            .setMessage("确定删除“${route.name}”？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                repository.delete(route.id)
                loadRoutes()
            }
            .show()
    }
}
