package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageQueryRecreationContractTest {
    private val main = File(
        "src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt"
    ).readText()
    private val search = File(
        "src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt"
    ).readText()
    private val placeInput = File(
        "src/main/java/com/golink/busiscoming/ui/common/PlaceInputController.kt"
    ).readText()

    @Test
    fun frequentOwnerRestoresQueryContextWithoutRecordingUsage() {
        assertTrue(main.contains("STATE_ACTIVE_QUERY_ROUTE_ID"))
        assertTrue(main.contains("restoreFrequentQueryIfNeeded"))
        assertTrue(main.contains("recordUsage = false"))
        assertTrue(main.contains("STATE_FREQUENT_SORT_FIELD"))
        assertTrue(main.contains("STATE_FREQUENT_SCROLL_POSITION"))
    }

    @Test
    fun searchOwnerRestoresSubmittedQueryButPreservesUnsubmittedInput() {
        assertTrue(search.contains("STATE_HAS_SUBMITTED_QUERY"))
        assertTrue(search.contains("STATE_ORIGIN_INPUT"))
        assertTrue(search.contains("STATE_DESTINATION_INPUT"))
        assertTrue(search.contains("restoreSubmittedQueryIfNeeded"))
        assertTrue(placeInput.contains("fun restoreInputText"))
    }
}
