package com.example.busiscoming

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.busiscoming.data.model.FirstLegEtaQuery
import com.example.busiscoming.data.model.WaitTimeState
import com.example.busiscoming.data.repository.CitybusFirstLegEtaService
import com.example.busiscoming.data.repository.CitybusP2pStopMapResolver
import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CitybusFirstLegEtaServiceInstrumentedTest {
    @Test
    fun resolvesWaitTimeOnAndroidRegexRuntime() {
        val service = CitybusFirstLegEtaService(
            clock = { millis("2026-06-04T18:32:00+08:00") },
            etaFetcher = {
                """
                {
                  "data": [
                    {"co":"CTB","route":"788","dir":"O","seq":6,"stop":"001344","eta":"2026-06-04T18:35:35+08:00","eta_seq":1}
                  ]
                }
                """.trimIndent()
            },
            stopMapResolver = CitybusP2pStopMapResolver(
                stopMapFetcher = { _, _ ->
                    """
                    <iframe onload="addstoponmap('001344',114.20000000000,22.300000000000,'S','6','6 - 測試站, 測試道','788-MAF-1','O','N','114.20000000000','22.300000000000');"></iframe>
                    """.trimIndent()
                }
            )
        )

        val waitTimeState = service.resolveWaitTime(
            FirstLegEtaQuery(
                company = "CTB",
                routeVariant = "788-MAF-1",
                route = "788",
                boardingSeq = 6,
                alightingSeq = 10,
                bound = "O",
                directionPath = "outbound",
                rawInfo = "1|*|CTB||788-MAF-1||6||10||O|*|",
                lang = "0"
            )
        )

        assertEquals(WaitTimeState.Available(4), waitTimeState)
    }

    private fun millis(value: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(value)!!.time
    }
}
