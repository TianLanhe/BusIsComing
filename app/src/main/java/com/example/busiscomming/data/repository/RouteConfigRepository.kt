package com.example.busiscomming.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.busiscomming.data.local.RouteConfigDbHelper
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_CREATED_AT
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_ID
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_NAME
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_UPDATED_AT
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.TABLE_ROUTE_CONFIGS
import com.example.busiscomming.data.model.RouteConfig

class RouteConfigRepository(context: Context) {
    private val dbHelper = RouteConfigDbHelper(context.applicationContext)

    fun getAll(): List<RouteConfig> {
        val routes = mutableListOf<RouteConfig>()
        dbHelper.readableDatabase.query(
            TABLE_ROUTE_CONFIGS,
            ROUTE_COLUMNS,
            null,
            null,
            null,
            null,
            "$COLUMN_UPDATED_AT DESC, $COLUMN_ID DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                routes.add(cursor.toRouteConfig())
            }
        }
        return routes
    }

    fun getById(id: Long): RouteConfig? {
        dbHelper.readableDatabase.query(
            TABLE_ROUTE_CONFIGS,
            ROUTE_COLUMNS,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toRouteConfig() else null
        }
    }

    fun insert(name: String, origin: String, destination: String): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            put(COLUMN_ORIGIN, origin)
            put(COLUMN_DESTINATION, destination)
            put(COLUMN_CREATED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        return dbHelper.writableDatabase.insert(TABLE_ROUTE_CONFIGS, null, values)
    }

    fun update(config: RouteConfig) {
        val values = ContentValues().apply {
            put(COLUMN_NAME, config.name)
            put(COLUMN_ORIGIN, config.origin)
            put(COLUMN_DESTINATION, config.destination)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update(
            TABLE_ROUTE_CONFIGS,
            values,
            "$COLUMN_ID = ?",
            arrayOf(config.id.toString())
        )
    }

    fun delete(id: Long) {
        dbHelper.writableDatabase.delete(
            TABLE_ROUTE_CONFIGS,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
    }

    private fun Cursor.toRouteConfig(): RouteConfig {
        return RouteConfig(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            name = getString(getColumnIndexOrThrow(COLUMN_NAME)),
            origin = getString(getColumnIndexOrThrow(COLUMN_ORIGIN)),
            destination = getString(getColumnIndexOrThrow(COLUMN_DESTINATION))
        )
    }

    companion object {
        private val ROUTE_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_NAME,
            COLUMN_ORIGIN,
            COLUMN_DESTINATION
        )
    }
}
