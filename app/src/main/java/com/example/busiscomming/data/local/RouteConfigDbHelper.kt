package com.example.busiscomming.data.local

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
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ROUTE_CONFIGS")
        onCreate(db)
    }

    companion object {
        const val DATABASE_NAME = "bus_is_comming.db"
        const val DATABASE_VERSION = 2

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
    }
}
