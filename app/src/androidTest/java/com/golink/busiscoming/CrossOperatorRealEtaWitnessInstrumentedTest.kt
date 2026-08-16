package com.golink.busiscoming

import android.graphics.Bitmap
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.local.CrossOperatorRouteDatabase
import com.golink.busiscoming.data.localization.AppLanguage
import com.golink.busiscoming.data.localization.AppLanguageChoice
import com.golink.busiscoming.data.localization.LanguageSnapshot
import com.golink.busiscoming.data.model.BusOperator
import com.golink.busiscoming.data.model.BusRouteOption
import com.golink.busiscoming.data.model.CrossOperatorEtaQuery
import com.golink.busiscoming.data.model.EtaArrival
import com.golink.busiscoming.data.model.FirstLegEtaQuery
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.model.WaitTimeState
import com.golink.busiscoming.data.repository.CitybusBusRouteRepository
import com.golink.busiscoming.data.repository.CitybusFirstLegEtaService
import com.golink.busiscoming.data.repository.CitybusP2pStopMapResolver
import com.golink.busiscoming.data.repository.CitybusStaticDataHttpSource
import com.golink.busiscoming.data.repository.CrossOperatorEtaMerger
import com.golink.busiscoming.data.repository.CrossOperatorGlobalUpdater
import com.golink.busiscoming.data.repository.CrossOperatorMappingRepository
import com.golink.busiscoming.data.repository.CrossOperatorMappingResolution
import com.golink.busiscoming.data.repository.CtbRouteSliceLoader
import com.golink.busiscoming.data.repository.EtaSourceResult
import com.golink.busiscoming.data.repository.GlobalUpdateResult
import com.golink.busiscoming.data.repository.HongKongDataDay
import com.golink.busiscoming.data.repository.HttpGlobalStaticDataFetcher
import com.golink.busiscoming.data.repository.KmbFirstLegEtaSource
import com.golink.busiscoming.data.repository.KmbPacedGlobalStaticDataFetcher
import com.golink.busiscoming.data.repository.P2pFirstLegStopIdentityResolver
import com.golink.busiscoming.data.repository.RetryingGlobalStaticDataFetcher
import com.golink.busiscoming.data.repository.RouteSemanticFingerprint
import com.golink.busiscoming.ui.main.EtaArrivalsBottomSheet
import com.golink.busiscoming.ui.main.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrossOperatorRealEtaWitnessInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun real118P2pAndDynamicJointRouteWitnessUseProductionPipeline() {
        assumeTrue(InstrumentationRegistry.getArguments().getString(ARG_RUN_LIVE) == "true")
        val evidenceDir = File(requireNotNull(context.getExternalFilesDir(null)), "cross-operator-witness")
            .apply { mkdirs() }
        val database = CrossOperatorRouteDatabase(context)
        try {
            val dataDay = HongKongDataDay.forInstant(System.currentTimeMillis())
            val update = CrossOperatorGlobalUpdater(
                database,
                RetryingGlobalStaticDataFetcher(
                    KmbPacedGlobalStaticDataFetcher(HttpGlobalStaticDataFetcher())
                )
            ).update(dataDay)
            val updateSuccess = update as? GlobalUpdateResult.Success
            if (updateSuccess == null) {
                val inconclusive = JSONObject()
                    .put("status", "inconclusive")
                    .put("stage", "global-static-update")
                    .put("requestTimeMillis", System.currentTimeMillis())
                    .put("dataDay", dataDay)
                    .put("reason", update.toString())
                    .toString(2)
                File(evidenceDir, "inconclusive.json").writeText(inconclusive)
                Log.i(EVIDENCE_LOG_TAG, inconclusive)
                assumeTrue("Live global static update unavailable; evidence saved", false)
                return
            }
            val snapshot = requireNotNull(database.activeSnapshot())

            val routes = CitybusBusRouteRepository(
                languageSnapshotProvider = { LANGUAGE },
                // The live runner can execute overnight, when the P2P service correctly returns
                // N118 instead of 118. Use a daytime planning instant to keep the 118 route-map
                // smoke deterministic; ETA requests below still use the real current time.
                clock = ::daytimePlanningInstant,
                requestLogger = {},
                waitTimeResolver = { WaitTimeState.NoArrivals }
            ).searchRoutes(LOK_HIN_TERRACE, CHEUNG_SHA_WAN_TERMINUS)
            val jointRouteNames = snapshot.jointRoutes.map { it.route }.toSet()
            val candidates = routes
                .filter { it.firstLegEtaQuery?.route in jointRouteNames }
                .sortedBy { if (it.firstLegEtaQuery?.route == "118") 0 else 1 }
                .distinctBy { it.firstLegEtaQuery?.let { query ->
                    listOf(query.route, query.routeVariant, query.boardingSeq, query.alightingSeq)
                } }
                .take(MAX_P2P_CANDIDATES)
            val route118 = candidates.firstOrNull { it.firstLegEtaQuery?.route == "118" }
            assertNotNull(
                "Real Citybus P2P query did not return route 118; routes=" +
                    routes.joinToString { route ->
                        "${route.routeName}[${route.firstLegEtaQuery?.routeVariant}]"
                    },
                route118
            )

            val captured = linkedMapOf<String, String>()
            val fetchJson: (URL) -> String = { url ->
                captured.getOrPut(url.toString()) { fetchBounded(url) }
            }
            val stopMapResolver = CitybusP2pStopMapResolver()
            val identityResolver = P2pFirstLegStopIdentityResolver(stopMapResolver)
            val citybus = CitybusFirstLegEtaService(
                etaFetcher = fetchJson,
                stopMapResolver = stopMapResolver
            )
            val mapping = CrossOperatorMappingRepository(
                snapshotStore = database,
                sliceStore = database,
                matchStore = database,
                routeLoader = CtbRouteSliceLoader(
                    CitybusStaticDataHttpSource(),
                    database
                )::loadRoute
            )
            val partner = KmbFirstLegEtaSource(fetcher = fetchJson)

            var witness: Witness? = null
            var route118MappingVerified = false
            val attempts = JSONArray()
            for (route in candidates) {
                val query = route.firstLegEtaQuery ?: continue
                val identity = identityResolver.resolve(query)
                val resolution = identity?.let {
                    mapping.resolve(query, it.boardingCtbStopId, it.alightingCtbStopId)
                }
                if (query.route == "118") {
                    assertNotNull("Real route 118 P2P stop identity was unavailable", identity)
                    assertTrue(
                        "Real route 118 did not pass the cross-operator mapping gate: $resolution",
                        resolution is CrossOperatorMappingResolution.Enabled
                    )
                    route118MappingVerified = true
                }
                if (identity == null || resolution !is CrossOperatorMappingResolution.Enabled) {
                    attempts.put(JSONObject().put("route", query.route).put("status", resolution.toString()))
                    continue
                }
                val citybusResult = citybus.resolveWaitTime(query)
                val partnerResult = partner.query(resolution.query, query.lang)
                val merged = CrossOperatorEtaMerger.merge(citybusResult, partnerResult)
                val operators = (merged as? WaitTimeState.Available)
                    ?.arrivals.orEmpty().map(EtaArrival::operator).toSet()
                attempts.put(
                    JSONObject()
                        .put("route", query.route)
                        .put("ctbStop", identity.boardingCtbStopId)
                        .put("partnerStop", resolution.query.boardingStopId)
                        .put("partnerDirection", resolution.query.direction)
                        .put("serviceType", resolution.query.serviceType)
                        .put("dpRawCost", resolution.match.rawCost)
                        .put("dpNormalizedCost", resolution.match.normalizedCost)
                        .put("operators", JSONArray(operators.map(BusOperator::code)))
                )
                if (BusOperator.CTB in operators && resolution.query.operator in operators) {
                    witness = Witness(route, query, identity.boardingCtbStopId, resolution, merged)
                    break
                }
            }
            assertTrue("Real route 118 mapping smoke was not executed", route118MappingVerified)

            if (witness == null) {
                val inconclusive = JSONObject()
                    .put("status", "inconclusive")
                    .put("requestTimeMillis", System.currentTimeMillis())
                    .put("snapshotId", snapshot.id)
                    .put("dataDay", snapshot.dataDay)
                    .put("attempts", attempts)
                    .toString(2)
                File(evidenceDir, "inconclusive.json").writeText(inconclusive)
                Log.i(EVIDENCE_LOG_TAG, inconclusive)
                assumeTrue("No current CTB plus KMB/LWB dual-arrival witness; evidence saved", false)
                return
            }

            val live = requireNotNull(witness)
            val arrivals = (live.merged as WaitTimeState.Available).arrivals
            val ctbUrl = citybus.buildEtaUrl("CTB", live.ctbBoardingStop, live.query.route).toString()
            val partnerUrl = partner.buildUrl(live.resolution.query).toString()
            val ctbRaw = requireNotNull(captured[ctbUrl])
            val partnerRaw = requireNotNull(captured[partnerUrl])
            val oracle = oracleRows(
                ctbRaw,
                partnerRaw,
                live.query,
                live.ctbBoardingStop,
                live.resolution.query
            )
            assertEquals(
                oracle,
                arrivals.map { OracleRow(it.operator, requireNotNull(it.etaMillis), it.sourceSequence) }
            )

            File(evidenceDir, "citybus-eta.json").writeText(ctbRaw)
            File(evidenceDir, "partner-eta.json").writeText(partnerRaw)
            val manifest = JSONObject()
                .put("status", "live-success")
                .put("requestTimeMillis", System.currentTimeMillis())
                .put("route", live.query.route)
                .put("directionPath", live.query.directionPath)
                .put("ctbStop", live.ctbBoardingStop)
                .put("partnerStop", live.resolution.query.boardingStopId)
                .put("operator", live.resolution.query.operator.code)
                .put("partnerDirection", live.resolution.query.direction)
                .put("serviceType", live.resolution.query.serviceType)
                .put("dpRawCost", live.resolution.match.rawCost)
                .put("dpNormalizedCost", live.resolution.match.normalizedCost)
                .put("snapshotId", snapshot.id)
                .put("dataDay", snapshot.dataDay)
                .put(
                    "ctbFingerprint",
                    database.loadCtbRouteSlice(live.query.route, live.query.directionPath)?.fingerprint
                )
                .put(
                    "partnerFingerprint",
                    snapshot.variants.firstOrNull { variant ->
                        variant.operator == live.resolution.query.operator &&
                            variant.route == live.resolution.query.route &&
                            variant.direction == live.resolution.query.direction &&
                            variant.serviceType == live.resolution.query.serviceType
                    }?.let(RouteSemanticFingerprint::of)
                )
                .put("algorithmVersion", live.resolution.match.algorithmVersion)
                .put("gapCost", live.resolution.match.gapCostMeters)
                .put("threshold", live.resolution.match.thresholdMetersPerStop)
                .put("citybusUrl", redactUrl(ctbUrl))
                .put("partnerUrl", redactUrl(partnerUrl))
                .put("citybusSha256", sha256(ctbRaw))
                .put("partnerSha256", sha256(partnerRaw))
                .put("rows", JSONArray(arrivals.map { arrival ->
                    JSONObject()
                        .put("operator", arrival.operator.code)
                        .put("sourceSequence", arrival.sourceSequence)
                        .put("etaMillis", arrival.etaMillis)
                        .put("arrivalTime", arrival.arrivalTimeText)
                }))
            File(evidenceDir, "manifest.json").writeText(manifest.toString(2))

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var sheet: EtaArrivalsBottomSheet
                scenario.onActivity { activity ->
                    sheet = EtaArrivalsBottomSheet(activity)
                    sheet.show(live.route.copy(waitTimeState = live.merged))
                }
                instrumentation.waitForIdleSync()
                val accessibilityRows = collectAccessibilityDescriptions()
                val expectedRows = arrivals.map(::expectedAccessibilityDescription)
                val rowPositions = expectedRows.map(accessibilityRows::indexOf)
                assertTrue(
                    "ETA accessibility rows differ from the independent oracle: " +
                        "expected=$expectedRows actual=$accessibilityRows",
                    rowPositions.all { it >= 0 } && rowPositions == rowPositions.sorted()
                )
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                FileOutputStream(File(evidenceDir, "ui.png")).use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                dumpAccessibilityHierarchy(File(evidenceDir, "ui-hierarchy.xml"))
                scenario.onActivity { sheet.dispose() }
            }
        } finally {
            database.close()
        }
    }

    private fun oracleRows(
        ctbRaw: String,
        partnerRaw: String,
        query: FirstLegEtaQuery,
        ctbStop: String,
        partnerQuery: CrossOperatorEtaQuery
    ): List<OracleRow> {
        val ctbRecords = records(JSONObject(ctbRaw).getJSONArray("data"))
            .filter { it.string("route") == query.route }
            .filter { it.string("stop") == ctbStop }
            .filter { it.string("dir") == query.bound }
        val strictCtb = ctbRecords.filter { it.int("seq") == query.boardingSeq }.ifEmpty { ctbRecords }
            .mapNotNull { record -> record.toOracle(BusOperator.CTB) }
        val partnerRecords = records(JSONObject(partnerRaw).getJSONArray("data"))
            .filter { it.string("co") == partnerQuery.operator.code }
            .filter { it.string("route") == partnerQuery.route }
            .filter { it.string("stop") == partnerQuery.boardingStopId }
            .filter { it.string("dir") == partnerQuery.direction }
            .filter { it.string("service_type") == partnerQuery.serviceType }
            .mapNotNull { record -> record.toOracle(partnerQuery.operator) }
        return (strictCtb + partnerRecords).sortedWith(
            compareBy<OracleRow> { it.etaMillis }
                .thenBy { it.operator.code }
                .thenBy { it.sourceSequence }
        )
    }

    private fun records(array: JSONArray): List<JSONObject> = buildList {
        for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
    }

    private fun JSONObject.toOracle(operator: BusOperator): OracleRow? {
        val eta = string("eta").takeIf(String::isNotBlank) ?: return null
        val millis = ETA_FORMAT.get()!!.parse(eta)?.time ?: return null
        return OracleRow(operator, millis, optInt("eta_seq", Int.MAX_VALUE))
    }

    private fun JSONObject.string(key: String): String = opt(key)?.toString().orEmpty()
    private fun JSONObject.int(key: String): Int? = opt(key)?.toString()?.toIntOrNull()

    private fun fetchBounded(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            val status = connection.responseCode
            require(status in 200..299) { "HTTP $status for ${redactUrl(url.toString())}" }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText().also { require(it.length <= MAX_ETA_CHARS) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun redactUrl(url: String): String = url.substringBefore('?')

    private fun dumpAccessibilityHierarchy(output: File) {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return
        output.bufferedWriter().use { writer ->
            writer.appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            fun appendNode(node: AccessibilityNodeInfo, depth: Int) {
                val indent = "  ".repeat(depth)
                writer.append(indent)
                    .append("<node class=\"").append(xml(node.className)).append("\"")
                    .append(" text=\"").append(xml(node.text)).append("\"")
                    .append(" content-desc=\"").append(xml(node.contentDescription)).append("\">")
                    .appendLine()
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let { child -> appendNode(child, depth + 1) }
                }
                writer.append(indent).appendLine("</node>")
            }
            appendNode(root, 0)
        }
    }

    private fun collectAccessibilityDescriptions(): List<String> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        return buildList {
            fun appendNode(node: AccessibilityNodeInfo) {
                node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(::add)
                for (index in 0 until node.childCount) {
                    node.getChild(index)?.let(::appendNode)
                }
            }
            appendNode(root)
        }
    }

    private fun expectedAccessibilityDescription(arrival: EtaArrival): String {
        val sequence = context.getString(R.string.eta_arrival_sequence, arrival.sequence)
        val operator = context.getString(
            when (arrival.operator) {
                BusOperator.CTB -> R.string.operator_ctb
                BusOperator.KMB -> R.string.operator_kmb
                BusOperator.LWB -> R.string.operator_lwb
            }
        )
        val minutes = if (arrival.minutes <= 0) {
            context.getString(R.string.eta_due)
        } else {
            context.getString(R.string.minutes_count, arrival.minutes)
        }
        return if (arrival.remark.isNullOrBlank()) {
            context.getString(
                R.string.eta_arrival_row_content_description,
                sequence,
                operator,
                minutes,
                arrival.arrivalTimeText
            )
        } else {
            context.getString(
                R.string.eta_arrival_row_with_remark_content_description,
                sequence,
                operator,
                minutes,
                arrival.arrivalTimeText,
                arrival.remark
            )
        }
    }

    private fun xml(value: CharSequence?): String = value?.toString().orEmpty()
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun daytimePlanningInstant(): Long = Calendar.getInstance(HONG_KONG_TIME_ZONE).run {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private data class OracleRow(
        val operator: BusOperator,
        val etaMillis: Long,
        val sourceSequence: Int
    )

    private data class Witness(
        val route: BusRouteOption,
        val query: FirstLegEtaQuery,
        val ctbBoardingStop: String,
        val resolution: CrossOperatorMappingResolution.Enabled,
        val merged: WaitTimeState
    )

    companion object {
        private const val ARG_RUN_LIVE = "runCrossOperatorLive"
        private const val EVIDENCE_LOG_TAG = "CrossOperatorWitness"
        private const val MAX_P2P_CANDIDATES = 6
        private const val MAX_ETA_CHARS = 2 * 1024 * 1024
        private val HONG_KONG_TIME_ZONE = TimeZone.getTimeZone("Asia/Hong_Kong")
        private val LANGUAGE = LanguageSnapshot.create(
            AppLanguageChoice.TRADITIONAL_CHINESE,
            AppLanguage.TRADITIONAL_CHINESE,
            1L
        )
        private val LOK_HIN_TERRACE = Place("樂軒臺", 22.264980642091, 114.24170198053)
        private val CHEUNG_SHA_WAN_TERMINUS = Place(
            "長沙灣（深旺道）",
            22.331135302091,
            114.14903921053
        )
        private val ETA_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                timeZone = HONG_KONG_TIME_ZONE
            }
        }
    }
}
