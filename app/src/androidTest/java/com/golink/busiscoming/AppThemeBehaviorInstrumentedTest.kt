package com.golink.busiscoming

import android.Manifest
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.golink.busiscoming.data.local.AppThemePreferenceStore
import com.golink.busiscoming.data.model.AppThemeMode
import com.golink.busiscoming.ui.main.MainActivity
import java.io.FileInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppThemeBehaviorInstrumentedTest {
    private lateinit var context: Context
    private lateinit var originalMode: AppThemeMode
    private lateinit var originalSystemNightMode: String

    @get:Rule
    val locationPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @Before
    fun rememberAppearanceState() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        originalMode = AppThemePreferenceStore(context).getMode()
        originalSystemNightMode = runShell("cmd uimode night")
            .substringAfter(":", "auto")
            .trim()
            .ifBlank { "auto" }
    }

    @After
    fun restoreAppearanceState() {
        runShell("cmd uimode night $originalSystemNightMode")
        AppThemePreferenceStore(context).setMode(originalMode)
        AppCompatDelegate.setDefaultNightMode(originalMode.nightMode)
    }

    @Test
    fun followSystemTracksAvailableNightModeAndFixedModesOverrideIt() {
        assertTheme(AppThemeMode.SYSTEM, systemNight = false, expectedNight = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTheme(AppThemeMode.SYSTEM, systemNight = true, expectedNight = true)
        }
        assertTheme(AppThemeMode.LIGHT, systemNight = true, expectedNight = false)
        assertTheme(AppThemeMode.DARK, systemNight = false, expectedNight = true)
    }

    private fun assertTheme(
        mode: AppThemeMode,
        systemNight: Boolean,
        expectedNight: Boolean
    ) {
        runShell("cmd uimode night ${if (systemNight) "yes" else "no"}")
        AppThemePreferenceStore(context).setMode(mode)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val actualNightMode = activity.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                assertEquals(
                    "mode=$mode systemNight=$systemNight expectedNight=$expectedNight",
                    if (expectedNight) {
                        Configuration.UI_MODE_NIGHT_YES
                    } else {
                        Configuration.UI_MODE_NIGHT_NO
                    },
                    actualNightMode
                )
                assertEquals(mode, AppThemePreferenceStore(activity).getMode())
            }
        }
    }

    private fun runShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return FileInputStream(descriptor.fileDescriptor).use { input ->
            input.readBytes().decodeToString()
        }
    }
}
