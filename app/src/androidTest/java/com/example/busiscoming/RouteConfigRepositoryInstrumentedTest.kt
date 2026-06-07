package com.example.busiscoming

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.busiscoming.data.local.RouteConfigDbHelper
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.model.RouteConfig
import com.example.busiscoming.data.repository.RouteConfigRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteConfigRepositoryInstrumentedTest {
    private lateinit var context: Context
    private var repository: RouteConfigRepository? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        repository?.close()
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @Test
    fun insertsReadsUpdatesAndDeletesRoutePlaces() {
        repository = RouteConfigRepository(context)
        val origin = Place("漁灣村漁進樓", 22.264, 114.248)
        val destination = Place("興華二村豐興樓", 22.262, 114.236)
        val updatedDestination = Place("會展站", 22.281604205483, 114.174971227790)

        val id = repository!!.insert("F", origin, destination)
        val savedRoute = repository!!.getById(id)

        assertEquals("F", savedRoute?.name)
        assertEquals(origin, savedRoute?.origin)
        assertEquals(destination, savedRoute?.destination)
        assertEquals(0, savedRoute?.usageCount)
        assertNull(savedRoute?.lastUsedAt)
        assertEquals(1, repository!!.getAll().size)

        repository!!.update(RouteConfig(id, "F 改", origin, updatedDestination))

        val updatedRoute = repository!!.getById(id)
        assertEquals("F 改", updatedRoute?.name)
        assertEquals(updatedDestination, updatedRoute?.destination)

        repository!!.delete(id)

        assertNull(repository!!.getById(id))
        assertTrue(repository!!.getAll().isEmpty())
    }

    @Test
    fun recordsUsageAndSortsRoutesByUsageStatistics() {
        repository = RouteConfigRepository(context)
        val origin = Place("起點", 22.1, 114.1)
        val destination = Place("終點", 22.2, 114.2)
        val firstId = repository!!.insert("第一條", origin, destination)
        val secondId = repository!!.insert("第二條", Place("起點2", 22.3, 114.3), destination)

        repository!!.recordUsage(firstId, usedAtMillis = 100)
        repository!!.recordUsage(secondId, usedAtMillis = 200)
        repository!!.recordUsage(secondId, usedAtMillis = 300)

        val routes = repository!!.getAll()

        assertEquals(listOf(secondId, firstId), routes.map { it.id })
        assertEquals(2, routes.first().usageCount)
        assertEquals(300L, routes.first().lastUsedAt)
    }

    @Test
    fun upgradingFromTextRouteDatabaseDropsOldRowsAndCreatesPlaceColumns() {
        createOldVersionDatabase()

        val helper = RouteConfigDbHelper(context)
        val db = helper.writableDatabase

        assertEquals(RouteConfigDbHelper.DATABASE_VERSION, db.version)
        assertEquals(
            setOf(
                "id",
                "name",
                "origin_name",
                "origin_latitude",
                "origin_longitude",
                "destination_name",
                "destination_latitude",
                "destination_longitude",
                "created_at",
                "updated_at",
                "usage_count",
                "last_used_at"
            ),
            readRouteConfigColumns(db)
        )
        assertEquals(0, routeConfigCount(db))
        helper.close()
    }

    @Test
    fun upgradingFromPlaceRouteDatabasePreservesRowsAndAddsUsageColumns() {
        createPlaceVersionDatabase()

        val helper = RouteConfigDbHelper(context)
        val db = helper.writableDatabase

        assertEquals(RouteConfigDbHelper.DATABASE_VERSION, db.version)
        assertEquals(1, routeConfigCount(db))
        assertTrue(readRouteConfigColumns(db).containsAll(setOf("usage_count", "last_used_at")))
        db.rawQuery(
            "SELECT usage_count, last_used_at FROM ${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS}",
            null
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
        }
        helper.close()
    }

    private fun createOldVersionDatabase() {
        val dbFile = context.getDatabasePath(RouteConfigDbHelper.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            """
            CREATE TABLE ${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                origin TEXT NOT NULL,
                destination TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS}
                (name, origin, destination, created_at, updated_at)
            VALUES ('舊路線', '舊起點', '舊終點', 1, 1)
            """.trimIndent()
        )
        db.version = 1
        db.close()
    }

    private fun createPlaceVersionDatabase() {
        val dbFile = context.getDatabasePath(RouteConfigDbHelper.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            """
            CREATE TABLE ${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS} (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                origin_name TEXT NOT NULL,
                origin_latitude REAL NOT NULL,
                origin_longitude REAL NOT NULL,
                destination_name TEXT NOT NULL,
                destination_latitude REAL NOT NULL,
                destination_longitude REAL NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO ${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS}
                (name, origin_name, origin_latitude, origin_longitude, destination_name, destination_latitude, destination_longitude, created_at, updated_at)
            VALUES ('既有路線', '起點', 22.1, 114.1, '終點', 22.2, 114.2, 1, 2)
            """.trimIndent()
        )
        db.version = 2
        db.close()
    }

    private fun readRouteConfigColumns(db: SQLiteDatabase): Set<String> {
        return db.rawQuery("PRAGMA table_info(${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS})", null)
            .use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                columns
            }
    }

    private fun routeConfigCount(db: SQLiteDatabase): Int {
        return db.rawQuery("SELECT COUNT(*) FROM ${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS}", null)
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
    }
}
