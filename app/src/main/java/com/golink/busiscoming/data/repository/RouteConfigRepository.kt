package com.golink.busiscoming.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.golink.busiscoming.data.local.RouteConfigDbHelper
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_CREATED_AT
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_LATITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_LONGITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_NAME
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ID
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_LAST_USED_AT
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_NAME
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_LATITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_LONGITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_NAME
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_PIN_ROUTE_CONFIG_ID
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_USAGE_COUNT
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_UPDATED_AT
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.TABLE_ROUTE_CONFIGS
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.TABLE_ROUTE_RESULT_PINS
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.transfer.TransferRoute

class RouteConfigRepository(
    context: Context,
    private val routeUpdateFailureInjector: RouteUpdateFailureInjector = RouteUpdateFailureInjector.NONE,
    private val importFailureInjector: RouteImportFailureInjector = RouteImportFailureInjector.NONE
) {
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
            "$COLUMN_USAGE_COUNT DESC, $COLUMN_LAST_USED_AT DESC, $COLUMN_UPDATED_AT DESC, $COLUMN_ID DESC"
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
        val values = routeValues(name, origin, destination, now)
        return dbHelper.writableDatabase.insert(TABLE_ROUTE_CONFIGS, null, values)
    }

    fun importRoutes(routes: List<TransferRoute>, mode: RouteImportMode): RouteImportResult {
        require(routes.isNotEmpty()) { "Import candidates must not be empty" }
        val database = dbHelper.writableDatabase
        database.beginTransaction()
        return try {
            val deletedCount: Int
            val identities: MutableSet<RouteIdentity>
            if (mode == RouteImportMode.REPLACE) {
                deletedCount = database.delete(TABLE_ROUTE_CONFIGS, null, null)
                importFailureInjector.invoke(RouteImportFailureStage.AFTER_DELETE, -1)
                identities = HashSet()
            } else {
                deletedCount = 0
                identities = getAll(database).mapTo(HashSet()) { it.identity() }
            }

            var addedCount = 0
            var skippedCount = 0
            routes.forEach { candidate ->
                val normalized = candidate.copy(name = candidate.name.trim())
                val identity = normalized.identity()
                if (!identities.add(identity)) {
                    skippedCount += 1
                } else {
                    importFailureInjector.invoke(RouteImportFailureStage.BEFORE_INSERT, addedCount)
                    val insertedId = database.insert(
                        TABLE_ROUTE_CONFIGS,
                        null,
                        routeValues(
                            normalized.name,
                            normalized.origin,
                            normalized.destination,
                            System.currentTimeMillis()
                        )
                    )
                    if (insertedId == -1L) throw SQLiteException("Failed to import route")
                    addedCount += 1
                }
            }
            val result = RouteImportResult(addedCount, skippedCount, deletedCount)
            database.setTransactionSuccessful()
            result
        } finally {
            database.endTransaction()
        }
    }

    fun update(config: RouteConfig, clearRouteResultPins: Boolean = false) {
        val values = ContentValues().apply {
            put(COLUMN_NAME, config.name)
            putPlace(ORIGIN_PREFIX, config.origin)
            putPlace(DESTINATION_PREFIX, config.destination)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        val database = dbHelper.writableDatabase
        database.beginTransaction()
        try {
            val updatedRows = database.update(
                TABLE_ROUTE_CONFIGS,
                values,
                "$COLUMN_ID = ?",
                arrayOf(config.id.toString())
            )
            if (updatedRows != 1) throw SQLiteException("Failed to update journey")
            if (clearRouteResultPins) {
                routeUpdateFailureInjector.invoke(RouteUpdateFailureStage.AFTER_ROUTE_UPDATE)
                database.delete(
                    TABLE_ROUTE_RESULT_PINS,
                    "$COLUMN_PIN_ROUTE_CONFIG_ID = ?",
                    arrayOf(config.id.toString())
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun recordUsage(id: Long, usedAtMillis: Long = System.currentTimeMillis()) {
        dbHelper.writableDatabase.execSQL(
            """
            UPDATE $TABLE_ROUTE_CONFIGS
            SET $COLUMN_USAGE_COUNT = $COLUMN_USAGE_COUNT + 1,
                $COLUMN_LAST_USED_AT = ?,
                $COLUMN_UPDATED_AT = $COLUMN_UPDATED_AT
            WHERE $COLUMN_ID = ?
            """.trimIndent(),
            arrayOf<Any>(usedAtMillis, id)
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
            destination = getPlace(DESTINATION_PREFIX),
            usageCount = getInt(getColumnIndexOrThrow(COLUMN_USAGE_COUNT)),
            lastUsedAt = getNullableLong(COLUMN_LAST_USED_AT)
        )
    }

    private fun getAll(database: SQLiteDatabase): List<RouteConfig> {
        val routes = mutableListOf<RouteConfig>()
        database.query(
            TABLE_ROUTE_CONFIGS,
            ROUTE_COLUMNS,
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) routes += cursor.toRouteConfig()
        }
        return routes
    }

    private fun RouteConfig.identity() = RouteIdentity(name.trim(), origin, destination)

    private fun TransferRoute.identity() = RouteIdentity(name.trim(), origin, destination)

    private fun routeValues(name: String, origin: Place, destination: Place, now: Long) =
        ContentValues().apply {
            put(COLUMN_NAME, name)
            putPlace(ORIGIN_PREFIX, origin)
            putPlace(DESTINATION_PREFIX, destination)
            put(COLUMN_CREATED_AT, now)
            put(COLUMN_UPDATED_AT, now)
            put(COLUMN_USAGE_COUNT, 0)
            putNull(COLUMN_LAST_USED_AT)
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

    private fun Cursor.getNullableLong(columnName: String): Long? {
        val columnIndex = getColumnIndexOrThrow(columnName)
        return if (isNull(columnIndex)) null else getLong(columnIndex)
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
            COLUMN_DESTINATION_LONGITUDE,
            COLUMN_USAGE_COUNT,
            COLUMN_LAST_USED_AT
        )
    }

    private data class RouteIdentity(
        val name: String,
        val origin: Place,
        val destination: Place
    )
}
