package com.example.busiscomming.ui.edit

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.busiscomming.R
import com.example.busiscomming.data.model.RouteConfig
import com.example.busiscomming.data.repository.RouteConfigRepository
import com.example.busiscomming.ui.common.applyStatusBarPadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RouteEditActivity : AppCompatActivity() {
    private lateinit var repository: RouteConfigRepository
    private lateinit var nameInputLayout: TextInputLayout
    private lateinit var originInputLayout: TextInputLayout
    private lateinit var destinationInputLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var originInput: TextInputEditText
    private lateinit var destinationInput: TextInputEditText

    private var routeId: Long = NO_ROUTE_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_route_edit)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.routeEditContent).applyStatusBarPadding()
        repository = RouteConfigRepository(this)
        routeId = intent.getLongExtra(EXTRA_ROUTE_ID, NO_ROUTE_ID)

        bindViews()
        setupMode()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun bindViews() {
        nameInputLayout = findViewById(R.id.routeNameInputLayout)
        originInputLayout = findViewById(R.id.originInputLayout)
        destinationInputLayout = findViewById(R.id.destinationInputLayout)
        nameInput = findViewById(R.id.routeNameInput)
        originInput = findViewById(R.id.originInput)
        destinationInput = findViewById(R.id.destinationInput)
        findViewById<MaterialButton>(R.id.saveRouteButton).setOnClickListener {
            saveRoute()
        }
    }

    private fun setupMode() {
        if (routeId == NO_ROUTE_ID) {
            title = "新增路线"
            return
        }

        title = "编辑路线"
        val route = repository.getById(routeId)
        if (route == null) {
            Toast.makeText(this, "路线配置不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        nameInput.setText(route.name)
        originInput.setText(route.origin)
        destinationInput.setText(route.destination)
    }

    private fun saveRoute() {
        val name = nameInput.text?.toString()?.trim().orEmpty()
        val origin = originInput.text?.toString()?.trim().orEmpty()
        val destination = destinationInput.text?.toString()?.trim().orEmpty()

        val isValid = validateRequired(nameInputLayout, name) and
            validateRequired(originInputLayout, origin) and
            validateRequired(destinationInputLayout, destination)
        if (!isValid) return

        if (routeId == NO_ROUTE_ID) {
            repository.insert(name, origin, destination)
            Toast.makeText(this, "已新增路线", Toast.LENGTH_SHORT).show()
        } else {
            repository.update(RouteConfig(routeId, name, origin, destination))
            Toast.makeText(this, "已保存修改", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun validateRequired(inputLayout: TextInputLayout, value: String): Boolean {
        return if (value.isBlank()) {
            inputLayout.error = "必填"
            false
        } else {
            inputLayout.error = null
            true
        }
    }

    companion object {
        const val EXTRA_ROUTE_ID = "extra_route_id"
        const val NO_ROUTE_ID = -1L
    }
}
