package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRefreshFeedbackContractTest {
    private val mainActivityKt =
        File("src/main/java/com/example/busiscoming/ui/main/MainActivity.kt").readText()

    @Test
    fun refreshSuccessUsesInitialRoutesAndDelayedGenerationBoundFinish() {
        assertTrue(mainActivityKt.contains("handleRefreshSuccess(queryId, routes)"))
        assertTrue(mainActivityKt.contains("REFRESH_SUCCESS_DURATION_MS"))
        assertTrue(mainActivityKt.contains("finishRefreshSuccess(queryId)"))
        assertTrue(mainActivityKt.contains("resultList.scrollToPosition(0)"))
    }

    @Test
    fun refreshFailureRestoresPreviousViewportAndKeepsToastOnlyFeedback() {
        assertTrue(mainActivityKt.contains("captureRefreshViewport()"))
        assertTrue(mainActivityKt.contains("restoreRefreshViewport()"))
        assertTrue(mainActivityKt.contains("刷新失敗，請稍後重試"))
    }
}
