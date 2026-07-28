package com.golink.busiscoming.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.golink.busiscoming.data.local.RouteConfigDbHelper
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_LATITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_LONGITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_DESTINATION_NAME
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ID
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_LATITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_LONGITUDE
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_ORIGIN_NAME
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_PINNED_AT
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_PIN_ROUTE_CONFIG_ID
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.COLUMN_PIN_ROUTE_FINGERPRINT
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.TABLE_ROUTE_CONFIGS
import com.golink.busiscoming.data.local.RouteConfigDbHelper.Companion.TABLE_ROUTE_RESULT_PINS
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.RoutePinRecord

class PinnedRouteRepository(context: Context) {
    private val dbHelper = RouteConfigDbHelper(context.applicationContext)

    fun load(journeyId: Long): List<RoutePinRecord> {
        return dbHelper.readableDatabase.query(
            TABLE_ROUTE_RESULT_PINS,
            arrayOf(COLUMN_PIN_ROUTE_FINGERPRINT, COLUMN_PINNED_AT),
            "$COLUMN_PIN_ROUTE_CONFIG_ID = ?",
            arrayOf(journeyId.toString()),
            null,
            null,
            "$COLUMN_PINNED_AT DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        RoutePinRecord(
                            fingerprint = cursor.getString(0),
                            level = PinLevel.PERSISTENT,
                            pinnedAt = cursor.getLong(1)
                        )
                    )
                }
            }
        }
    }

    fun insertIfAbsent(journeyId: Long, fingerprint: String, pinnedAt: Long): Boolean {
        require(fingerprint.isNotBlank())
        return dbHelper.writableDatabase.insertWithOnConflict(
            TABLE_ROUTE_RESULT_PINS,
            null,
            pinValues(journeyId, fingerprint, pinnedAt),
            SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun insertIfAbsentWhenEndpointsMatch(
        journeyId: Long,
        fingerprint: String,
        pinnedAt: Long,
        expectedEndpoints: RouteEndpointSnapshot
    ): Boolean {
        require(fingerprint.isNotBlank())
        val database = dbHelper.writableDatabase
        database.beginTransaction()
        return try {
            if (!endpointsMatch(database, journeyId, expectedEndpoints)) {
                false
            } else {
                database.insertWithOnConflict(
                    TABLE_ROUTE_RESULT_PINS,
                    null,
                    pinValues(journeyId, fingerprint, pinnedAt),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                database.setTransactionSuccessful()
                true
            }
        } finally {
            database.endTransaction()
        }
    }

    fun delete(journeyId: Long, fingerprint: String): Boolean {
        return dbHelper.writableDatabase.delete(
            TABLE_ROUTE_RESULT_PINS,
            "$COLUMN_PIN_ROUTE_CONFIG_ID = ? AND $COLUMN_PIN_ROUTE_FINGERPRINT = ?",
            arrayOf(journeyId.toString(), fingerprint)
        ) > 0
    }

    fun count(journeyId: Long): Int {
        return dbHelper.readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_ROUTE_RESULT_PINS
            WHERE $COLUMN_PIN_ROUTE_CONFIG_ID = ?
            """.trimIndent(),
            arrayOf(journeyId.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    fun clear(journeyId: Long): Int {
        return dbHelper.writableDatabase.delete(
            TABLE_ROUTE_RESULT_PINS,
            "$COLUMN_PIN_ROUTE_CONFIG_ID = ?",
            arrayOf(journeyId.toString())
        )
    }

    fun close() {
        dbHelper.close()
    }

    private fun pinValues(journeyId: Long, fingerprint: String, pinnedAt: Long) =
        ContentValues().apply {
            put(COLUMN_PIN_ROUTE_CONFIG_ID, journeyId)
            put(COLUMN_PIN_ROUTE_FINGERPRINT, fingerprint)
            put(COLUMN_PINNED_AT, pinnedAt)
        }

    private fun endpointsMatch(
        database: SQLiteDatabase,
        journeyId: Long,
        expected: RouteEndpointSnapshot
    ): Boolean {
        return database.query(
            TABLE_ROUTE_CONFIGS,
            arrayOf(
                COLUMN_ORIGIN_NAME,
                COLUMN_ORIGIN_LATITUDE,
                COLUMN_ORIGIN_LONGITUDE,
                COLUMN_DESTINATION_NAME,
                COLUMN_DESTINATION_LATITUDE,
                COLUMN_DESTINATION_LONGITUDE
            ),
            "$COLUMN_ID = ?",
            arrayOf(journeyId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                false
            } else {
                val actualOrigin = Place(
                    name = cursor.getString(0),
                    latitude = cursor.getDouble(1),
                    longitude = cursor.getDouble(2)
                )
                val actualDestination = Place(
                    name = cursor.getString(3),
                    latitude = cursor.getDouble(4),
                    longitude = cursor.getDouble(5)
                )
                actualOrigin == expected.origin && actualDestination == expected.destination
            }
        }
    }
}
