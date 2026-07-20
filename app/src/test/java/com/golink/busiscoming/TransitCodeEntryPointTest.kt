package com.golink.busiscoming

import com.golink.busiscoming.ui.main.TransitCodeEntryPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitCodeEntryPointTest {
    @Test
    fun `all formal entry points use one explicit app action`() {
        assertTrue(TransitCodeEntryPoint.isLaunchAction(TransitCodeEntryPoint.ACTION_OPEN_TRANSIT_CODE))
        assertFalse(TransitCodeEntryPoint.isLaunchAction(null))
        assertFalse(TransitCodeEntryPoint.isLaunchAction("android.intent.action.MAIN"))
    }
}
