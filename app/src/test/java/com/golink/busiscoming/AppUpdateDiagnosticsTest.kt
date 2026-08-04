package com.golink.busiscoming

import com.golink.busiscoming.data.update.AppUpdateDiagnosticEvent
import com.golink.busiscoming.data.update.AppUpdateDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppUpdateDiagnosticsTest {
    @Test
    fun playDiagnosticsExposeOnlyStatusVersionAndErrorCode() {
        val events = mutableListOf<AppUpdateDiagnosticEvent>()
        val diagnostics = AppUpdateDiagnostics(events::add)

        diagnostics.record(AppUpdateDiagnosticEvent.PlaySuccess(2, 11))
        diagnostics.record(AppUpdateDiagnosticEvent.PlayFailure(-10))

        assertEquals(
            listOf(
                AppUpdateDiagnosticEvent.PlaySuccess(2, 11),
                AppUpdateDiagnosticEvent.PlayFailure(-10)
            ),
            events
        )
        assertFalse(events.joinToString().contains("account", ignoreCase = true))
        assertFalse(events.joinToString().contains("device", ignoreCase = true))
    }
}
