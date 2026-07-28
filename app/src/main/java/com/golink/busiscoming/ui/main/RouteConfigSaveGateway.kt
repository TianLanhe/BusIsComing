package com.golink.busiscoming.ui.main

import android.content.Context
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.RouteConfigRepository

/** 搜尋頁保存對話框所需的最小資料邊界，production 仍沿用既有 repository。 */
interface RouteConfigSaveGateway {
    fun hasDuplicate(name: String, origin: Place, destination: Place): Boolean

    fun insert(name: String, origin: Place, destination: Place): Long
}

class RouteConfigRepositorySaveGateway(context: Context) : RouteConfigSaveGateway {
    private val repository = RouteConfigRepository(context)

    override fun hasDuplicate(name: String, origin: Place, destination: Place): Boolean =
        repository.hasDuplicate(name, origin, destination)

    override fun insert(name: String, origin: Place, destination: Place): Long =
        repository.insert(name, origin, destination)
}
