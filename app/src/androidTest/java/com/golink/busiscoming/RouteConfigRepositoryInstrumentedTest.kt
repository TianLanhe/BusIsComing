package com.golink.busiscoming

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.RouteConfigDbHelper
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteConfig
import com.golink.busiscoming.data.model.RoutePinRecord
import com.golink.busiscoming.data.repository.RouteImportFailureStage
import com.golink.busiscoming.data.repository.RouteImportMode
import com.golink.busiscoming.data.repository.RouteConfigRepository
import com.golink.busiscoming.data.repository.PinnedRouteRepository
import com.golink.busiscoming.data.repository.RouteUpdateFailureInjector
import com.golink.busiscoming.data.repository.RouteUpdateFailureStage
import com.golink.busiscoming.data.transfer.TransferRoute
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
    private var pinRepository: PinnedRouteRepository? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(RouteConfigDbHelper.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        pinRepository?.close()
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
    fun renamingJourneyPreservesItsPinnedRoutes() {
        repository = RouteConfigRepository(context)
        pinRepository = PinnedRouteRepository(context)
        val origin = Place("起點", 22.1, 114.1)
        val destination = Place("終點", 22.2, 114.2)
        val id = repository!!.insert("舊名稱", origin, destination)
        pinRepository!!.insertIfAbsent(id, "v1|route", 10L)

        repository!!.update(
            RouteConfig(id, "新名稱", origin, destination),
            clearRouteResultPins = false
        )

        assertEquals("新名稱", repository!!.getById(id)?.name)
        assertEquals(listOf("v1|route"), pinRepository!!.load(id).map { it.fingerprint })
    }

    @Test
    fun endpointUpdateAndPinClearAreAtomicAndJourneyScoped() {
        repository = RouteConfigRepository(context)
        pinRepository = PinnedRouteRepository(context)
        val origin = Place("起點", 22.1, 114.1)
        val destination = Place("終點", 22.2, 114.2)
        val firstId = repository!!.insert("第一", origin, destination)
        val secondId = repository!!.insert("第二", origin, destination)
        pinRepository!!.insertIfAbsent(firstId, "v1|first", 10L)
        pinRepository!!.insertIfAbsent(secondId, "v1|second", 20L)
        val changedOrigin = Place("新起點", 22.3, 114.3)

        repository!!.update(
            RouteConfig(firstId, "第一", changedOrigin, destination),
            clearRouteResultPins = true
        )

        assertEquals(changedOrigin, repository!!.getById(firstId)?.origin)
        assertTrue(pinRepository!!.load(firstId).isEmpty())
        assertEquals(listOf("v1|second"), pinRepository!!.load(secondId).map { it.fingerprint })
    }

    @Test
    fun endpointUpdateFailureRollsBackJourneyAndPinnedRoutes() {
        repository = RouteConfigRepository(
            context,
            routeUpdateFailureInjector = RouteUpdateFailureInjector { stage ->
                if (stage == RouteUpdateFailureStage.AFTER_ROUTE_UPDATE) error("injected")
            }
        )
        pinRepository = PinnedRouteRepository(context)
        val origin = Place("起點", 22.1, 114.1)
        val destination = Place("終點", 22.2, 114.2)
        val id = repository!!.insert("保留", origin, destination)
        pinRepository!!.insertIfAbsent(id, "v1|route", 10L)

        try {
            repository!!.update(
                RouteConfig(id, "不應保留", Place("新起點", 22.3, 114.3), destination),
                clearRouteResultPins = true
            )
            throw AssertionError("Expected injected failure")
        } catch (_: IllegalStateException) {
            assertEquals(RouteConfig(id, "保留", origin, destination), repository!!.getById(id))
            assertEquals(listOf("v1|route"), pinRepository!!.load(id).map { it.fingerprint })
        }
    }

    @Test
    fun mergeAddsOnlyDistinctRoutesAndPreservesExistingStatistics() {
        repository = RouteConfigRepository(context)
        val origin = Place("柴灣站", 22.2642, 114.2371)
        val destination = Place("中環碼頭", 22.2878, 114.1582)
        val existingId = repository!!.insert("上班", origin, destination)
        repository!!.recordUsage(existingId, usedAtMillis = 1234)
        pinRepository = PinnedRouteRepository(context)
        pinRepository!!.insertIfAbsent(existingId, "v1|existing", 10L)

        val result = repository!!.importRoutes(
            listOf(
                TransferRoute(" 上班 ", origin, destination),
                TransferRoute("假日", origin, destination),
                TransferRoute("上班", origin, Place("灣仔站", 22.277, 114.173))
            ),
            RouteImportMode.MERGE
        )

        assertEquals(2, result.addedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(0, result.deletedCount)
        val routes = repository!!.getAll()
        assertEquals(3, routes.size)
        assertEquals(1, routes.single { it.id == existingId }.usageCount)
        assertEquals(1234L, routes.single { it.id == existingId }.lastUsedAt)
        assertEquals(
            listOf("v1|existing"),
            pinRepository!!.load(existingId).map { it.fingerprint }
        )
        routes.filter { it.id != existingId }.forEach {
            assertEquals(0, it.usageCount)
            assertNull(it.lastUsedAt)
            assertTrue(pinRepository!!.load(it.id).isEmpty())
        }
    }

    @Test
    fun replaceDeletesExistingRoutesRegeneratesIdsAndResetsStatistics() {
        repository = RouteConfigRepository(context)
        val firstId = repository!!.insert(
            "舊一",
            Place("舊起點一", 22.1, 114.1),
            Place("舊終點一", 22.2, 114.2)
        )
        val secondId = repository!!.insert(
            "舊二",
            Place("舊起點二", 22.3, 114.3),
            Place("舊終點二", 22.4, 114.4)
        )
        repository!!.recordUsage(firstId, 999)
        pinRepository = PinnedRouteRepository(context)
        pinRepository!!.insertIfAbsent(firstId, "v1|old", 10L)
        val imported = TransferRoute(
            "新路線",
            Place("新起點", 22.5, 114.5),
            Place("新終點", 22.6, 114.6)
        )

        val result = repository!!.importRoutes(listOf(imported, imported), RouteImportMode.REPLACE)

        assertEquals(1, result.addedCount)
        assertEquals(1, result.skippedCount)
        assertEquals(2, result.deletedCount)
        val saved = repository!!.getAll().single()
        assertTrue(saved.id > maxOf(firstId, secondId))
        assertEquals("新路線", saved.name)
        assertEquals(0, saved.usageCount)
        assertNull(saved.lastUsedAt)
        assertTrue(pinRepository!!.load(firstId).isEmpty())
        assertTrue(pinRepository!!.load(secondId).isEmpty())
        assertTrue(pinRepository!!.load(saved.id).isEmpty())
    }

    @Test
    fun replaceRejectsEmptyCandidatesWithoutChangingExistingRoutes() {
        repository = RouteConfigRepository(context)
        val id = repository!!.insert(
            "保留",
            Place("起點", 22.1, 114.1),
            Place("終點", 22.2, 114.2)
        )

        try {
            repository!!.importRoutes(emptyList(), RouteImportMode.REPLACE)
            throw AssertionError("Expected empty replace to be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals(id, repository!!.getAll().single().id)
        }
    }

    @Test
    fun mergeFailureRollsBackEveryInsertedRoute() {
        repository = RouteConfigRepository(context) { stage, index ->
            if (stage == RouteImportFailureStage.BEFORE_INSERT && index == 1) error("injected")
        }
        val existingId = repository!!.insert(
            "既有",
            Place("既有起點", 22.1, 114.1),
            Place("既有終點", 22.2, 114.2)
        )
        val incoming = listOf(
            TransferRoute("新增一", Place("起點一", 22.3, 114.3), Place("終點一", 22.4, 114.4)),
            TransferRoute("新增二", Place("起點二", 22.5, 114.5), Place("終點二", 22.6, 114.6))
        )

        try {
            repository!!.importRoutes(incoming, RouteImportMode.MERGE)
            throw AssertionError("Expected injected failure")
        } catch (_: IllegalStateException) {
            assertEquals(listOf(existingId), repository!!.getAll().map { it.id })
        }
    }

    @Test
    fun replaceFailureAfterDeleteRollsBackRoutesAndStatistics() {
        repository = RouteConfigRepository(context) { stage, _ ->
            if (stage == RouteImportFailureStage.AFTER_DELETE) error("injected")
        }
        val existingId = repository!!.insert(
            "保留",
            Place("起點", 22.1, 114.1),
            Place("終點", 22.2, 114.2)
        )
        repository!!.recordUsage(existingId, 5678)
        pinRepository = PinnedRouteRepository(context)
        pinRepository!!.insertIfAbsent(existingId, "v1|keep", 33L)

        try {
            repository!!.importRoutes(
                listOf(TransferRoute("新增", Place("新起點", 22.3, 114.3), Place("新終點", 22.4, 114.4))),
                RouteImportMode.REPLACE
            )
            throw AssertionError("Expected injected failure")
        } catch (_: IllegalStateException) {
            val existing = repository!!.getAll().single()
            assertEquals(existingId, existing.id)
            assertEquals(1, existing.usageCount)
            assertEquals(5678L, existing.lastUsedAt)
            assertEquals(
                listOf(RoutePinRecord("v1|keep", PinLevel.PERSISTENT, 33L)),
                pinRepository!!.load(existingId)
            )
        }
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
        assertTrue(tableExists(db, RouteConfigDbHelper.TABLE_ROUTE_RESULT_PINS))
        assertTrue(indexExists(db, RouteConfigDbHelper.INDEX_ROUTE_RESULT_PINS_ROUTE_TIME))
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
        assertTrue(tableExists(db, RouteConfigDbHelper.TABLE_ROUTE_RESULT_PINS))
        assertTrue(indexExists(db, RouteConfigDbHelper.INDEX_ROUTE_RESULT_PINS_ROUTE_TIME))
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
}
