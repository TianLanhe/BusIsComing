package com.example.busiscoming.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RouteConfigDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ROUTE_CONFIGS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_ORIGIN_NAME TEXT NOT NULL,
                $COLUMN_ORIGIN_LATITUDE REAL NOT NULL,
                $COLUMN_ORIGIN_LONGITUDE REAL NOT NULL,
                $COLUMN_DESTINATION_NAME TEXT NOT NULL,
                $COLUMN_DESTINATION_LATITUDE REAL NOT NULL,
                $COLUMN_DESTINATION_LONGITUDE REAL NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_USAGE_COUNT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_LAST_USED_AT INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_ROUTE_CONFIGS")
            onCreate(db)
            return
        }

        if (oldVersion < 3) {
            addColumnIfMissing(
                db,
                COLUMN_USAGE_COUNT,
                "$COLUMN_USAGE_COUNT INTEGER NOT NULL DEFAULT 0"
            )
            addColumnIfMissing(
                db,
                COLUMN_LAST_USED_AT,
                "$COLUMN_LAST_USED_AT INTEGER"
            )
        }
    }

    private fun addColumnIfMissing(db: SQLiteDatabase, columnName: String, columnDefinition: String) {
        val hasColumn = db.rawQuery("PRAGMA table_info($TABLE_ROUTE_CONFIGS)", null).use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == columnName) {
                    found = true
                    break
                }
            }
            found
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE $TABLE_ROUTE_CONFIGS ADD COLUMN $columnDefinition")
        }
    }

    companion object {
        const val DATABASE_NAME = "bus_is_coming.db"
        const val DATABASE_VERSION = 3

        const val TABLE_ROUTE_CONFIGS = "route_configs"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_ORIGIN_NAME = "origin_name"
        const val COLUMN_ORIGIN_LATITUDE = "origin_latitude"
        const val COLUMN_ORIGIN_LONGITUDE = "origin_longitude"
        const val COLUMN_DESTINATION_NAME = "destination_name"
        const val COLUMN_DESTINATION_LATITUDE = "destination_latitude"
        const val COLUMN_DESTINATION_LONGITUDE = "destination_longitude"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_USAGE_COUNT = "usage_count"
        const val COLUMN_LAST_USED_AT = "last_used_at"
    }
}
