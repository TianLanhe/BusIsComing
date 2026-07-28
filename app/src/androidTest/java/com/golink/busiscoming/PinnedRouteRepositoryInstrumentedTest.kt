package com.golink.busiscoming

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.RouteConfigDbHelper
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.PinnedRouteRepository
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.data.repository.RouteEndpointSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PinnedRouteRepositoryInstrumentedTest {
    private lateinit var context: Context
    private var pinRepository: PinnedRouteRepository? = null
    private var routeRepository: RouteConfigRepository? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        pinRepository?.close()
        routeRepository?.close()
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @Test
    fun freshVersionFourDatabaseCreatesPinTableIndexAndForeignKeys() {
        val helper = RouteConfigDbHelper(context)
        val db = helper.writableDatabase

        assertEquals(4, db.version)
        assertTrue(tableExists(db, RouteConfigDbHelper.TABLE_ROUTE_RESULT_PINS))
        assertTrue(indexExists(db, RouteConfigDbHelper.INDEX_ROUTE_RESULT_PINS_ROUTE_TIME))
        assertEquals(
            listOf("route_config_id", "pinned_at"),
            indexColumns(db, RouteConfigDbHelper.INDEX_ROUTE_RESULT_PINS_ROUTE_TIME)
        )
        assertEquals(1, pragmaInt(db, "foreign_keys"))
        helper.close()
    }

    @Test
    fun upgradingVersionThreePreservesJourneyStatisticsAndAddsPinSchema() {
        createVersionThreeDatabase()

        val helper = RouteConfigDbHelper(context)
        val db = helper.writableDatabase

        assertEquals(4, db.version)
        db.rawQuery(
            """
            SELECT name, usage_count, last_used_at
            FROM ${RouteConfigDbHelper.TABLE_ROUTE_CONFIGS}
            """.trimIndent(),
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("既有行程", cursor.getString(0))
            assertEquals(7, cursor.getInt(1))
            assertEquals(1234L, cursor.getLong(2))
        }
        assertTrue(tableExists(db, RouteConfigDbHelper.TABLE_ROUTE_RESULT_PINS))
        assertTrue(indexExists(db, RouteConfigDbHelper.INDEX_ROUTE_RESULT_PINS_ROUTE_TIME))
        helper.close()
    }

    @Test
    fun repositoryLoadsNewestFirstAndInsertIfAbsentPreservesOriginalToken() {
        val journeyId = createJourney()
        pinRepository = PinnedRouteRepository(context)

        assertTrue(pinRepository!!.insertIfAbsent(journeyId, "v1|old", 10L))
        assertTrue(pinRepository!!.insertIfAbsent(journeyId, "v1|new", 20L))
        assertFalse(pinRepository!!.insertIfAbsent(journeyId, "v1|old", 99L))

        val pins = pinRepository!!.load(journeyId)
        assertEquals(listOf("v1|new", "v1|old"), pins.map { it.fingerprint })
        assertEquals(listOf(20L, 10L), pins.map { it.pinnedAt })
        assertTrue(pins.all { it.level == PinLevel.PERSISTENT })
    }

    @Test
    fun repositoryDeletesCountsAndClearsOnlyRequestedJourney() {
        val firstJourney = createJourney("第一")
        val secondJourney = createJourney("第二")
        pinRepository = PinnedRouteRepository(context)
        pinRepository!!.insertIfAbsent(firstJourney, "v1|a", 1L)
        pinRepository!!.insertIfAbsent(firstJourney, "v1|b", 2L)
        pinRepository!!.insertIfAbsent(secondJourney, "v1|c", 3L)

        assertEquals(2, pinRepository!!.count(firstJourney))
        assertTrue(pinRepository!!.delete(firstJourney, "v1|a"))
        assertEquals(1, pinRepository!!.count(firstJourney))
        assertEquals(1, pinRepository!!.clear(firstJourney))
        assertEquals(0, pinRepository!!.count(firstJourney))
        assertEquals(listOf("v1|c"), pinRepository!!.load(secondJourney).map { it.fingerprint })
    }

    @Test
    fun deletingJourneyCascadesItsPinsWithoutTouchingOtherJourney() {
        val firstJourney = createJourney("第一")
        val secondJourney = createJourney("第二")
        pinRepository = PinnedRouteRepository(context)
        pinRepository!!.insertIfAbsent(firstJourney, "v1|a", 1L)
        pinRepository!!.insertIfAbsent(secondJourney, "v1|b", 2L)

        routeRepository!!.delete(firstJourney)

        assertTrue(pinRepository!!.load(firstJourney).isEmpty())
        assertEquals(listOf("v1|b"), pinRepository!!.load(secondJourney).map { it.fingerprint })
    }

    @Test
    fun queuedInsertCannotRestorePinAfterJourneyEndpointsChanged() {
        val journeyId = createJourney()
        pinRepository = PinnedRouteRepository(context)
        val original = requireNotNull(routeRepository!!.getById(journeyId))
        val originalEndpoints = RouteEndpointSnapshot.from(original)
        val changed = original.copy(destination = Place("新終點", 22.9, 114.9))

        routeRepository!!.update(changed, clearRouteResultPins = true)

        assertFalse(
            pinRepository!!.insertIfAbsentWhenEndpointsMatch(
                journeyId = journeyId,
                fingerprint = "v1|stale",
                pinnedAt = 10L,
                expectedEndpoints = originalEndpoints
            )
        )
        assertTrue(pinRepository!!.load(journeyId).isEmpty())
        assertTrue(
            pinRepository!!.insertIfAbsentWhenEndpointsMatch(
                journeyId = journeyId,
                fingerprint = "v1|current",
                pinnedAt = 11L,
                expectedEndpoints = RouteEndpointSnapshot.from(changed)
            )
        )
    }

    private fun createJourney(name: String = "行程"): Long {
        if (routeRepository == null) routeRepository = RouteConfigRepository(context)
        return routeRepository!!.insert(
            name,
            Place("$name 起點", 22.1 + name.length / 100.0, 114.1),
            Place("$name 終點", 22.2 + name.length / 100.0, 114.2)
        )
    }

    private fun createVersionThreeDatabase() {
        val dbFile = context.getDatabasePath(RouteConfigDbHelper.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            """
            CREATE TABLE route_configs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                origin_name TEXT NOT NULL,
                origin_latitude REAL NOT NULL,
                origin_longitude REAL NOT NULL,
                destination_name TEXT NOT NULL,
                destination_latitude REAL NOT NULL,
                destination_longitude REAL NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                usage_count INTEGER NOT NULL DEFAULT 0,
                last_used_at INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO route_configs
                (name, origin_name, origin_latitude, origin_longitude,
                 destination_name, destination_latitude, destination_longitude,
                 created_at, updated_at, usage_count, last_used_at)
            VALUES ('既有行程', '起點', 22.1, 114.1, '終點', 22.2, 114.2, 1, 2, 7, 1234)
            """.trimIndent()
        )
        db.version = 3
        db.close()
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { it.moveToFirst() }
    }

    private fun indexExists(db: SQLiteDatabase, index: String): Boolean {
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(index)
        ).use { it.moveToFirst() }
    }

    private fun indexColumns(db: SQLiteDatabase, index: String): List<String> {
        return db.rawQuery("PRAGMA index_info($index)", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
        }
    }

    private fun pragmaInt(db: SQLiteDatabase, name: String): Int {
        return db.rawQuery("PRAGMA $name", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }
}
