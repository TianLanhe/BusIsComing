package com.example.busiscomming.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.busiscomming.data.local.RouteConfigDbHelper
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_CREATED_AT
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_LATITUDE
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_LONGITUDE
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_NAME
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_ID
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_NAME
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_LATITUDE
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_LONGITUDE
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_NAME
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.COLUMN_UPDATED_AT
import com.example.busiscomming.data.local.RouteConfigDbHelper.Companion.TABLE_ROUTE_CONFIGS
import com.example.busiscomming.data.model.Place
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

    fun insert(name: String, origin: Place, destination: Place): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(COLUMN_NAME, name)
            putPlace(ORIGIN_PREFIX, origin)
            putPlace(DESTINATION_PREFIX, destination)
            put(COLUMN_CREATED_AT, now)
            put(COLUMN_UPDATED_AT, now)
        }
        return dbHelper.writableDatabase.insert(TABLE_ROUTE_CONFIGS, null, values)
    }

    fun update(config: RouteConfig) {
        val values = ContentValues().apply {
            put(COLUMN_NAME, config.name)
            putPlace(ORIGIN_PREFIX, config.origin)
            putPlace(DESTINATION_PREFIX, config.destination)
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

    fun hasDuplicate(
        name: String,
        origin: Place,
        destination: Place,
        excludedId: Long? = null
    ): Boolean {
        val normalizedName = name.trim()
        return getAll().any { route ->
            route.id != excludedId &&
                route.name.trim() == normalizedName &&
                route.origin == origin &&
                route.destination == destination
        }
    }

    fun close() {
        dbHelper.close()
    }

    private fun Cursor.toRouteConfig(): RouteConfig {
        return RouteConfig(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            name = getString(getColumnIndexOrThrow(COLUMN_NAME)),
            origin = getPlace(ORIGIN_PREFIX),
            destination = getPlace(DESTINATION_PREFIX)
        )
    }

    private fun ContentValues.putPlace(prefix: String, place: Place) {
        when (prefix) {
            ORIGIN_PREFIX -> {
                put(COLUMN_ORIGIN_NAME, place.name)
                put(COLUMN_ORIGIN_LATITUDE, place.latitude)
                put(COLUMN_ORIGIN_LONGITUDE, place.longitude)
            }
            DESTINATION_PREFIX -> {
                put(COLUMN_DESTINATION_NAME, place.name)
                put(COLUMN_DESTINATION_LATITUDE, place.latitude)
                put(COLUMN_DESTINATION_LONGITUDE, place.longitude)
            }
        }
    }

    private fun Cursor.getPlace(prefix: String): Place {
        return when (prefix) {
            ORIGIN_PREFIX -> Place(
                name = getString(getColumnIndexOrThrow(COLUMN_ORIGIN_NAME)),
                latitude = getDouble(getColumnIndexOrThrow(COLUMN_ORIGIN_LATITUDE)),
                longitude = getDouble(getColumnIndexOrThrow(COLUMN_ORIGIN_LONGITUDE))
            )
            else -> Place(
                name = getString(getColumnIndexOrThrow(COLUMN_DESTINATION_NAME)),
                latitude = getDouble(getColumnIndexOrThrow(COLUMN_DESTINATION_LATITUDE)),
                longitude = getDouble(getColumnIndexOrThrow(COLUMN_DESTINATION_LONGITUDE))
            )
        }
    }

    companion object {
        private const val ORIGIN_PREFIX = "origin"
        private const val DESTINATION_PREFIX = "destination"

        private val ROUTE_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_NAME,
            COLUMN_ORIGIN_NAME,
            COLUMN_ORIGIN_LATITUDE,
            COLUMN_ORIGIN_LONGITUDE,
            COLUMN_DESTINATION_NAME,
            COLUMN_DESTINATION_LATITUDE,
            COLUMN_DESTINATION_LONGITUDE
        )
    }
}
