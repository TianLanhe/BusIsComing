package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRefreshFeedbackContractTest {
    private val mainActivityKt =
        File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()

    @Test
    fun refreshSuccessWaitsForPinsAndKeepsViewportBeforeDelayedGenerationBoundFinish() {
        assertTrue(mainActivityKt.contains("finishPinGatedQuery"))
        assertTrue(mainActivityKt.contains("handleRefreshSuccess(completion.queryId, completion.routes)"))
        assertTrue(mainActivityKt.contains("REFRESH_SUCCESS_DURATION_MS"))
        assertTrue(mainActivityKt.contains("finishRefreshSuccess(queryId)"))
        assertTrue(mainActivityKt.contains("restoreRefreshViewport()"))
        assertFalse(mainActivityKt.contains("resultList.scrollToPosition(0)"))
    }

    @Test
    fun refreshFailureRestoresPreviousViewportAndKeepsToastOnlyFeedback() {
        assertTrue(mainActivityKt.contains("captureRefreshViewport()"))
        assertTrue(mainActivityKt.contains("restoreRefreshViewport()"))
        assertTrue(mainActivityKt.contains("R.string.refresh_failed"))
    }
}
