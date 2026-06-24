package com.example.busiscoming

import android.content.ActivityNotFoundException
import com.example.busiscoming.data.model.TransitCodeLaunchTargets
import com.example.busiscoming.ui.main.TransitCodeLaunchResult
import com.example.busiscoming.ui.main.TransitCodeLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TransitCodeLauncherTest {
    private val target = TransitCodeLaunchTargets.all.first()

    @Test
    fun launchReturnsSuccessAfterStarterAcceptsUri() {
        var launchedUri: String? = null
        val result = TransitCodeLauncher { uri -> launchedUri = uri }.launch(target)

        assertEquals(TransitCodeLaunchResult.SUCCESS, result)
        assertEquals(target.uri, launchedUri)
    }

    @Test
    fun launchMapsActivityNotFoundToUnavailable() {
        val result = TransitCodeLauncher {
            throw ActivityNotFoundException("missing")
        }.launch(target)

        assertEquals(TransitCodeLaunchResult.UNAVAILABLE, result)
    }

    @Test
    fun launchMapsSecurityExceptionToUnavailable() {
        val result = TransitCodeLauncher {
            throw SecurityException("blocked")
        }.launch(target)

        assertEquals(TransitCodeLaunchResult.UNAVAILABLE, result)
    }

    @Test
    fun launchMapsUnexpectedExceptionToUnexpectedError() {
        val exception = IllegalStateException("unexpected")
        var thrown: Throwable? = null
        val result = TransitCodeLauncher {
            thrown = exception
            throw exception
        }.launch(target)

        assertEquals(TransitCodeLaunchResult.UNEXPECTED_ERROR, result)
        assertSame(exception, thrown)
    }
}
