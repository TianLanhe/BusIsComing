package com.golink.busiscoming.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.CachedStaticSource
import com.golink.busiscoming.data.model.CtbRouteSlice
import com.golink.busiscoming.data.model.CrossOperatorMatchStatus
import com.golink.busiscoming.data.model.CrossOperatorRouteMatch
import com.golink.busiscoming.data.model.CrossOperatorStopPair
import com.golink.busiscoming.data.model.JointOperatorRoute
import com.golink.busiscoming.data.model.GlobalStaticSource
import com.golink.busiscoming.data.model.StaticRouteRecord
import com.golink.busiscoming.data.model.StaticRouteStop
import com.golink.busiscoming.data.model.StaticRouteVariant
import com.golink.busiscoming.data.repository.CtbRouteSliceStore
import com.golink.busiscoming.data.repository.CrossOperatorMatchStore
import com.golink.busiscoming.data.repository.CrossOperatorRouteMatcher
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class RouteDatabaseSnapshot(
    val id: String,
    val dataDay: String,
    val completedAtMillis: Long,
    val jointRoutes: List<JointOperatorRoute>,
    val ctbRoutes: List<StaticRouteRecord>,
    val variants: List<StaticRouteVariant>,
    val sourceCaches: Map<GlobalStaticSource, CachedStaticSource> = emptyMap()
)

interface CrossOperatorSnapshotStore {
    fun stageSnapshot(snapshot: RouteDatabaseSnapshot)
    fun activateSnapshot(snapshotId: String)
    fun activeSnapshot(): RouteDatabaseSnapshot?
    fun invalidateMatchesForRoute(route: String)
}

class CrossOperatorRouteDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    CrossOperatorSnapshotStore,
    CtbRouteSliceStore,
    CrossOperatorMatchStore {

    @Volatile private var activeSnapshotCache: RouteDatabaseSnapshot? = null
    @Volatile private var stagedSnapshotCache: RouteDatabaseSnapshot? = null

    override fun onConfigure(database: SQLiteDatabase) {
        super.onConfigure(database)
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        createSchema(database)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        rebuild(database)
    }

    override fun onDowngrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        rebuild(database)
    }

    override fun stageSnapshot(snapshot: RouteDatabaseSnapshot) {
        require(snapshot.id.isNotBlank()) { "Snapshot ID is blank" }
        val database = writableDatabase
        database.transaction {
            deleteSnapshotRows(this, snapshot.id)
            insertOrThrow(
                TABLE_SNAPSHOT,
                null,
                valuesOf(
                    "id" to snapshot.id,
                    "data_day" to snapshot.dataDay,
                    "completed_at" to snapshot.completedAtMillis
                )
            )
            snapshot.jointRoutes.forEach { joint ->
                insertOrThrow(
                    TABLE_JOINT_ROUTE,
                    null,
                    valuesOf(
                        "snapshot_id" to snapshot.id,
                        "route" to joint.route,
                        "partner" to joint.partner.code
                    )
                )
            }
            snapshot.sourceCaches.forEach { (source, cache) ->
                insertOrThrow(
                    TABLE_SOURCE_CACHE,
                    null,
                    valuesOf(
                        "snapshot_id" to snapshot.id,
                        "source" to source.name,
                        "etag" to cache.etag,
                        "last_modified" to cache.lastModified,
                        "body" to compressSourceBody(cache.body)
                    )
                )
            }
            snapshot.ctbRoutes.forEach { route ->
                insertOrThrow(
                    TABLE_CTB_ROUTE,
                    null,
                    valuesOf(
                        "snapshot_id" to snapshot.id,
                        "route" to route.route,
                        "direction" to route.direction
                    )
                )
            }
            snapshot.variants.forEach { variant ->
                insertOrThrow(
                    TABLE_VARIANT,
                    null,
                    valuesOf(
                        "snapshot_id" to snapshot.id,
                        "operator" to variant.operator.code,
                        "route" to variant.route,
                        "direction" to variant.direction,
                        "service_type" to variant.serviceType
                    )
                )
                variant.stops.forEach { stop ->
                    insertOrThrow(
                        TABLE_VARIANT_STOP,
                        null,
                        valuesOf(
                            "snapshot_id" to snapshot.id,
                            "operator" to variant.operator.code,
                            "route" to variant.route,
                            "direction" to variant.direction,
                            "service_type" to variant.serviceType,
                            "sequence" to stop.sequence,
                            "stop_id" to stop.id,
                            "latitude" to stop.latitude,
                            "longitude" to stop.longitude,
                            "name" to stop.name
                        )
                    )
                }
            }
        }
        stagedSnapshotCache = snapshot
    }

    override fun activateSnapshot(snapshotId: String) {
        val database = writableDatabase
        database.transaction {
            val exists = rawQuery(
                "SELECT 1 FROM $TABLE_SNAPSHOT WHERE id = ? LIMIT 1",
                arrayOf(snapshotId)
            ).use { it.moveToFirst() }
            require(exists) { "Snapshot $snapshotId has not been staged" }
            insertWithOnConflict(
                TABLE_METADATA,
                null,
                valuesOf("key" to KEY_ACTIVE_SNAPSHOT, "value" to snapshotId),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            // Readers that began before this transaction retain their SQLite snapshot. Once the
            // active pointer is switched, inactive immutable snapshots are safe to cascade-delete.
            delete(TABLE_SNAPSHOT, "id <> ?", arrayOf(snapshotId))
        }
        activeSnapshotCache = stagedSnapshotCache?.takeIf { it.id == snapshotId }
        stagedSnapshotCache = null
    }

    override fun activeSnapshot(): RouteDatabaseSnapshot? {
        activeSnapshotCache?.let { return it }
        val database = readableDatabase
        val snapshotId = database.rawQuery(
            "SELECT value FROM $TABLE_METADATA WHERE key = ?",
            arrayOf(KEY_ACTIVE_SNAPSHOT)
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return null
        val header = database.rawQuery(
            "SELECT data_day, completed_at FROM $TABLE_SNAPSHOT WHERE id = ?",
            arrayOf(snapshotId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0) to cursor.getLong(1)
        } ?: return null
        val jointRoutes = database.rawQuery(
            "SELECT route, partner FROM $TABLE_JOINT_ROUTE WHERE snapshot_id = ? ORDER BY route, partner",
            arrayOf(snapshotId)
        ).use { cursor ->
            cursor.mapRows {
                JointOperatorRoute(
                    route = getString(0),
                    partner = requireNotNull(BusOperator.fromCode(getString(1)))
                )
            }
        }
        val sourceCaches = database.rawQuery(
            "SELECT source, etag, last_modified, body FROM $TABLE_SOURCE_CACHE " +
                "WHERE snapshot_id = ? ORDER BY source",
            arrayOf(snapshotId)
        ).use { cursor ->
            cursor.mapRows {
                GlobalStaticSource.valueOf(getString(0)) to CachedStaticSource(
                    etag = getStringOrNull(1),
                    lastModified = getStringOrNull(2),
                    body = decompressSourceBody(getBlob(3))
                )
            }.toMap()
        }
        val ctbRoutes = database.rawQuery(
            "SELECT route, direction FROM $TABLE_CTB_ROUTE WHERE snapshot_id = ? ORDER BY route, direction",
            arrayOf(snapshotId)
        ).use { cursor ->
            cursor.mapRows {
                StaticRouteRecord(BusOperator.CTB, getString(0), getString(1), "")
            }
        }
        val variants = database.rawQuery(
            "SELECT operator, route, direction, service_type FROM $TABLE_VARIANT " +
                "WHERE snapshot_id = ? ORDER BY operator, route, direction, service_type",
            arrayOf(snapshotId)
        ).use { cursor ->
            cursor.mapRows {
                val operatorCode = getString(0)
                val route = getString(1)
                val direction = getString(2)
                val serviceType = getString(3)
                StaticRouteVariant(
                    operator = requireNotNull(BusOperator.fromCode(operatorCode)),
                    route = route,
                    direction = direction,
                    serviceType = serviceType,
                    stops = loadVariantStops(
                        database,
                        snapshotId,
                        operatorCode,
                        route,
                        direction,
                        serviceType
                    )
                )
            }
        }
        return RouteDatabaseSnapshot(
            id = snapshotId,
            dataDay = header.first,
            completedAtMillis = header.second,
            jointRoutes = jointRoutes,
            ctbRoutes = ctbRoutes,
            variants = variants,
            sourceCaches = sourceCaches
        ).also { activeSnapshotCache = it }
    }

    fun saveCtbRouteSlice(slice: CtbRouteSlice) {
        saveCtbRouteSlices(listOf(slice))
    }

    override fun saveCtbRouteSlices(slices: List<CtbRouteSlice>) {
        if (slices.isEmpty()) return
        slices.forEach { require(it.route.isNotBlank() && it.direction.isNotBlank()) }
        val routeNames = slices.map { it.route }.toSet()
        require(routeNames.size == 1) { "One atomic slice write must target one route" }
        val database = writableDatabase
        database.transaction {
            slices.forEach { slice ->
                delete(
                    TABLE_CTB_SLICE,
                    "route = ? AND direction = ?",
                    arrayOf(slice.route, slice.direction)
                )
                insertOrThrow(
                    TABLE_CTB_SLICE,
                    null,
                    valuesOf(
                        "route" to slice.route,
                        "direction" to slice.direction,
                        "verified_day" to slice.verifiedDataDay,
                        "fingerprint" to slice.fingerprint
                    )
                )
                slice.stops.sortedBy { it.sequence }.forEach { stop ->
                    insertOrThrow(
                        TABLE_CTB_SLICE_STOP,
                        null,
                        valuesOf(
                            "route" to slice.route,
                            "direction" to slice.direction,
                            "sequence" to stop.sequence,
                            "stop_id" to stop.id,
                            "latitude" to stop.latitude,
                            "longitude" to stop.longitude,
                            "name" to stop.name
                        )
                    )
                }
            }
        }
    }

    override fun loadCtbRouteSlice(route: String, direction: String): CtbRouteSlice? {
        val database = readableDatabase
        val header = database.rawQuery(
            "SELECT verified_day, fingerprint FROM $TABLE_CTB_SLICE WHERE route = ? AND direction = ?",
            arrayOf(route, direction)
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0) to cursor.getString(1)
        } ?: return null
        val stops = database.rawQuery(
            "SELECT stop_id, sequence, latitude, longitude, name FROM $TABLE_CTB_SLICE_STOP " +
                "WHERE route = ? AND direction = ? ORDER BY sequence",
            arrayOf(route, direction)
        ).use { cursor ->
            cursor.mapRows {
                StaticRouteStop(getString(0), getInt(1), getDouble(2), getDouble(3), getString(4))
            }
        }
        return CtbRouteSlice(route, direction, header.first, header.second, stops)
    }

    fun saveMatch(
        route: String,
        direction: String,
        ctbFingerprint: String,
        snapshotId: String,
        operatorFingerprint: String,
        match: CrossOperatorRouteMatch
    ) {
        val winner = match.winner
        val database = writableDatabase
        database.transaction {
            deleteMatch(this, route, direction)
            insertOrThrow(
                TABLE_MATCH,
                null,
                valuesOf(
                    "route" to route,
                    "direction" to direction,
                    "ctb_fingerprint" to ctbFingerprint,
                    "snapshot_id" to snapshotId,
                    "operator_fingerprint" to operatorFingerprint,
                    "status" to match.status.name,
                    "winner_operator" to winner?.operator?.code,
                    "winner_direction" to winner?.direction,
                    "winner_service_type" to winner?.serviceType,
                    "raw_cost" to match.rawCost,
                    "normalized_cost" to match.normalizedCost,
                    "algorithm_version" to match.algorithmVersion,
                    "gap_cost" to match.gapCostMeters,
                    "threshold" to match.thresholdMetersPerStop
                )
            )
            match.stopPairs.forEachIndexed { index, pair ->
                insertOrThrow(
                    TABLE_MATCH_PAIR,
                    null,
                    valuesOf(
                        "route" to route,
                        "direction" to direction,
                        "pair_order" to index,
                        "ctb_stop_id" to pair.ctbStopId,
                        "operator_stop_id" to pair.operatorStopId,
                        "operator_sequence" to pair.operatorSequence,
                        "distance_meters" to pair.distanceMeters
                    )
                )
            }
        }
    }

    fun loadMatch(
        route: String,
        direction: String,
        ctbFingerprint: String,
        snapshotId: String,
        operatorFingerprint: String,
        algorithmVersion: Int,
        gapCostMeters: Double,
        thresholdMetersPerStop: Double
    ): CrossOperatorRouteMatch? {
        val database = readableDatabase
        val row = database.rawQuery(
            "SELECT status, winner_operator, winner_direction, winner_service_type, raw_cost, " +
                "normalized_cost FROM $TABLE_MATCH WHERE route = ? AND direction = ? " +
                "AND ctb_fingerprint = ? AND snapshot_id = ? AND operator_fingerprint = ? " +
                "AND algorithm_version = ? AND gap_cost = ? AND threshold = ?",
            arrayOf(
                route,
                direction,
                ctbFingerprint,
                snapshotId,
                operatorFingerprint,
                algorithmVersion.toString(),
                gapCostMeters.toString(),
                thresholdMetersPerStop.toString()
            )
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else MatchRow(
                status = CrossOperatorMatchStatus.valueOf(cursor.getString(0)),
                winnerOperator = cursor.getStringOrNull(1),
                winnerDirection = cursor.getStringOrNull(2),
                winnerServiceType = cursor.getStringOrNull(3),
                rawCost = cursor.getDouble(4),
                normalizedCost = cursor.getDouble(5)
            )
        } ?: return null
        val pairs = database.rawQuery(
            "SELECT ctb_stop_id, operator_stop_id, operator_sequence, distance_meters " +
                "FROM $TABLE_MATCH_PAIR WHERE route = ? AND direction = ? ORDER BY pair_order",
            arrayOf(route, direction)
        ).use { cursor ->
            cursor.mapRows {
                CrossOperatorStopPair(getString(0), getString(1), getInt(2), getDouble(3))
            }
        }
        val winner = row.winnerOperator?.let { operatorCode ->
            StaticRouteVariant(
                operator = requireNotNull(BusOperator.fromCode(operatorCode)),
                route = route,
                direction = row.winnerDirection.orEmpty(),
                serviceType = row.winnerServiceType.orEmpty(),
                stops = emptyList()
            )
        }
        return CrossOperatorRouteMatch(
            status = row.status,
            winner = winner,
            rawCost = row.rawCost,
            normalizedCost = row.normalizedCost,
            stopPairs = pairs,
            algorithmVersion = algorithmVersion,
            gapCostMeters = gapCostMeters,
            thresholdMetersPerStop = thresholdMetersPerStop
        )
    }

    override fun load(
        route: String,
        direction: String,
        ctbFingerprint: String,
        snapshotId: String,
        operatorFingerprint: String
    ): CrossOperatorRouteMatch? = loadMatch(
        route,
        direction,
        ctbFingerprint,
        snapshotId,
        operatorFingerprint,
        CrossOperatorRouteMatcher.ALGORITHM_VERSION,
        CrossOperatorRouteMatcher.DEFAULT_GAP_COST_METERS,
        CrossOperatorRouteMatcher.DEFAULT_THRESHOLD_METERS_PER_STOP
    )

    override fun save(
        route: String,
        direction: String,
        ctbFingerprint: String,
        snapshotId: String,
        operatorFingerprint: String,
        match: CrossOperatorRouteMatch
    ) = saveMatch(route, direction, ctbFingerprint, snapshotId, operatorFingerprint, match)

    override fun invalidateMatchesForRoute(route: String) {
        writableDatabase.transaction { deleteMatch(this, route, null) }
    }

    fun invalidateAllMatches() {
        writableDatabase.transaction {
            delete(TABLE_MATCH_PAIR, null, null)
            delete(TABLE_MATCH, null, null)
        }
    }

    private fun loadVariantStops(
        database: SQLiteDatabase,
        snapshotId: String,
        operator: String,
        route: String,
        direction: String,
        serviceType: String
    ): List<StaticRouteStop> {
        return database.rawQuery(
            "SELECT stop_id, sequence, latitude, longitude, name FROM $TABLE_VARIANT_STOP " +
                "WHERE snapshot_id = ? AND operator = ? AND route = ? AND direction = ? " +
                "AND service_type = ? ORDER BY sequence",
            arrayOf(snapshotId, operator, route, direction, serviceType)
        ).use { cursor ->
            cursor.mapRows {
                StaticRouteStop(getString(0), getInt(1), getDouble(2), getDouble(3), getString(4))
            }
        }
    }

    private fun deleteMatch(database: SQLiteDatabase, route: String, direction: String?) {
        val selection = if (direction == null) "route = ?" else "route = ? AND direction = ?"
        val args = if (direction == null) arrayOf(route) else arrayOf(route, direction)
        database.delete(TABLE_MATCH_PAIR, selection, args)
        database.delete(TABLE_MATCH, selection, args)
    }

    private fun deleteSnapshotRows(database: SQLiteDatabase, snapshotId: String) {
        database.delete(TABLE_SNAPSHOT, "id = ?", arrayOf(snapshotId))
    }

    private fun rebuild(database: SQLiteDatabase) {
        activeSnapshotCache = null
        stagedSnapshotCache = null
        TABLES_REVERSE_ORDER.forEach { table -> database.execSQL("DROP TABLE IF EXISTS $table") }
        createSchema(database)
    }

    private fun createSchema(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE $TABLE_METADATA (`key` TEXT PRIMARY KEY, value TEXT NOT NULL)")
        database.execSQL(
            "CREATE TABLE $TABLE_SNAPSHOT (id TEXT PRIMARY KEY, data_day TEXT NOT NULL, " +
                "completed_at INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_JOINT_ROUTE (snapshot_id TEXT NOT NULL, route TEXT NOT NULL, " +
                "partner TEXT NOT NULL, PRIMARY KEY(snapshot_id, route, partner), " +
                "FOREIGN KEY(snapshot_id) REFERENCES $TABLE_SNAPSHOT(id) ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_SOURCE_CACHE (snapshot_id TEXT NOT NULL, source TEXT NOT NULL, " +
                "etag TEXT, last_modified TEXT, body BLOB NOT NULL, PRIMARY KEY(snapshot_id, source), " +
                "FOREIGN KEY(snapshot_id) REFERENCES $TABLE_SNAPSHOT(id) ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_CTB_ROUTE (snapshot_id TEXT NOT NULL, route TEXT NOT NULL, " +
                "direction TEXT NOT NULL, PRIMARY KEY(snapshot_id, route, direction), " +
                "FOREIGN KEY(snapshot_id) REFERENCES $TABLE_SNAPSHOT(id) ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_VARIANT (snapshot_id TEXT NOT NULL, operator TEXT NOT NULL, " +
                "route TEXT NOT NULL, direction TEXT NOT NULL, service_type TEXT NOT NULL, " +
                "PRIMARY KEY(snapshot_id, operator, route, direction, service_type), " +
                "FOREIGN KEY(snapshot_id) REFERENCES $TABLE_SNAPSHOT(id) ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_VARIANT_STOP (snapshot_id TEXT NOT NULL, operator TEXT NOT NULL, " +
                "route TEXT NOT NULL, direction TEXT NOT NULL, service_type TEXT NOT NULL, " +
                "sequence INTEGER NOT NULL CHECK(sequence > 0), stop_id TEXT NOT NULL, " +
                "latitude REAL NOT NULL, longitude REAL NOT NULL, name TEXT NOT NULL, " +
                "PRIMARY KEY(snapshot_id, operator, route, direction, service_type, sequence), " +
                "FOREIGN KEY(snapshot_id, operator, route, direction, service_type) REFERENCES " +
                "$TABLE_VARIANT(snapshot_id, operator, route, direction, service_type) ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_MATCH (route TEXT NOT NULL, direction TEXT NOT NULL, " +
                "ctb_fingerprint TEXT NOT NULL, snapshot_id TEXT NOT NULL, operator_fingerprint TEXT NOT NULL, " +
                "status TEXT NOT NULL, winner_operator TEXT, winner_direction TEXT, winner_service_type TEXT, " +
                "raw_cost REAL NOT NULL, normalized_cost REAL NOT NULL, algorithm_version INTEGER NOT NULL, " +
                "gap_cost REAL NOT NULL, threshold REAL NOT NULL, PRIMARY KEY(route, direction))"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_CTB_SLICE (route TEXT NOT NULL, direction TEXT NOT NULL, " +
                "verified_day TEXT NOT NULL, fingerprint TEXT NOT NULL, PRIMARY KEY(route, direction))"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_CTB_SLICE_STOP (route TEXT NOT NULL, direction TEXT NOT NULL, " +
                "sequence INTEGER NOT NULL CHECK(sequence > 0), stop_id TEXT NOT NULL, latitude REAL NOT NULL, " +
                "longitude REAL NOT NULL, name TEXT NOT NULL, PRIMARY KEY(route, direction, sequence), " +
                "FOREIGN KEY(route, direction) REFERENCES $TABLE_CTB_SLICE(route, direction) ON DELETE CASCADE)"
        )
        database.execSQL(
            "CREATE TABLE $TABLE_MATCH_PAIR (route TEXT NOT NULL, direction TEXT NOT NULL, " +
                "pair_order INTEGER NOT NULL, ctb_stop_id TEXT NOT NULL, operator_stop_id TEXT NOT NULL, " +
                "operator_sequence INTEGER NOT NULL, distance_meters REAL NOT NULL, " +
                "PRIMARY KEY(route, direction, pair_order), FOREIGN KEY(route, direction) " +
                "REFERENCES $TABLE_MATCH(route, direction) ON DELETE CASCADE)"
        )
    }

    private data class MatchRow(
        val status: CrossOperatorMatchStatus,
        val winnerOperator: String?,
        val winnerDirection: String?,
        val winnerServiceType: String?,
        val rawCost: Double,
        val normalizedCost: Double
    )

    companion object {
        const val DATABASE_NAME = "cross_operator_routes.db"
        private const val DATABASE_VERSION = 1
        private const val KEY_ACTIVE_SNAPSHOT = "active_snapshot"
        private const val TABLE_METADATA = "metadata"
        private const val TABLE_SNAPSHOT = "snapshot"
        private const val TABLE_JOINT_ROUTE = "joint_route"
        private const val TABLE_SOURCE_CACHE = "source_cache"
        private const val TABLE_CTB_ROUTE = "ctb_route"
        private const val TABLE_VARIANT = "route_variant"
        private const val TABLE_VARIANT_STOP = "route_variant_stop"
        private const val TABLE_MATCH = "route_match"
        private const val TABLE_MATCH_PAIR = "route_match_pair"
        private const val TABLE_CTB_SLICE = "ctb_route_slice"
        private const val TABLE_CTB_SLICE_STOP = "ctb_route_slice_stop"
        private val TABLES_REVERSE_ORDER = listOf(
            TABLE_MATCH_PAIR,
            TABLE_MATCH,
            TABLE_CTB_SLICE_STOP,
            TABLE_CTB_SLICE,
            TABLE_VARIANT_STOP,
            TABLE_VARIANT,
            TABLE_CTB_ROUTE,
            TABLE_SOURCE_CACHE,
            TABLE_JOINT_ROUTE,
            TABLE_SNAPSHOT,
            TABLE_METADATA
        )
    }
}

private inline fun <T> SQLiteDatabase.transaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try {
        val result = block()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}

private fun valuesOf(vararg pairs: Pair<String, Any?>): ContentValues {
    return ContentValues().apply {
        pairs.forEach { (key, value) ->
            when (value) {
                null -> putNull(key)
                is String -> put(key, value)
                is Int -> put(key, value)
                is Long -> put(key, value)
                is Double -> put(key, value)
                is ByteArray -> put(key, value)
                else -> error("Unsupported ContentValues type ${value::class.java.simpleName}")
            }
        }
    }
}

private inline fun <T> Cursor.mapRows(transform: Cursor.() -> T): List<T> {
    val values = mutableListOf<T>()
    while (moveToNext()) values += transform()
    return values
}

private fun Cursor.getStringOrNull(index: Int): String? {
    return if (isNull(index)) null else getString(index)
}

private fun compressSourceBody(body: ByteArray): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).use { gzip -> gzip.write(body) }
    return output.toByteArray()
}

private fun decompressSourceBody(body: ByteArray): ByteArray {
    val isGzip = body.size >= 2 && body[0] == 0x1f.toByte() && body[1] == 0x8b.toByte()
    if (!isGzip) return body
    return GZIPInputStream(ByteArrayInputStream(body)).use { it.readBytes() }
}
