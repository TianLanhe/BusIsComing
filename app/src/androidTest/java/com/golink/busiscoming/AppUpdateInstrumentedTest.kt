package com.golink.busiscoming

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.InitialInstallChannel
import com.golink.busiscoming.data.model.UpdateChannel
import com.golink.busiscoming.data.model.UpdateSnapshot
import com.golink.busiscoming.data.model.UpdateSnapshotState
import com.golink.busiscoming.data.update.AppUpdateRuntime
import com.golink.busiscoming.data.update.AndroidPlayPackageProbe
import com.golink.busiscoming.data.update.SharedPreferencesUpdateStateStore
import com.golink.busiscoming.data.update.UpdateChannelDecision
import com.golink.busiscoming.data.update.UpdateChannelResolver
import com.golink.busiscoming.data.update.UpdatePolicy
import com.golink.busiscoming.data.update.UpdateStoredState
import com.golink.busiscoming.ui.main.MainActivity
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateInstrumentedTest {
    @Test
    fun noPlayDeviceRoutesOnlyNonPlayInstallsToWebsiteWithoutInstallPermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertFalse(AndroidPlayPackageProbe(context).isPlayAvailable())
        assertEquals(
            UpdateChannelDecision.WEBSITE,
            UpdateChannelResolver.resolve(false, InitialInstallChannel.NON_PLAY, null)
        )
        assertEquals(
            UpdateChannelDecision.PLAY_UNAVAILABLE,
            UpdateChannelResolver.resolve(false, InitialInstallChannel.PLAY, null)
        )
        val requestedPermissions = context.packageManager
            .getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
        assertFalse(requestedPermissions.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES))
    }

    @Test
    fun settingsStartsWithoutDotAndExplainsHowToCheck() {
        seed(UpdateStoredState.initial(versionCode()).copy(
            initialInstallChannel = InitialInstallChannel.NON_PLAY,
            lastAutoAttemptAt = System.currentTimeMillis()
        ))

        ActivityScenario.launch(MainActivity::class.java).use {
            openUpdateSetting()
            onView(withId(R.id.settingsUpdateSummary)).check(
                matches(withText(R.string.update_status_never_checked))
            )
            onView(withId(R.id.settingsUpdateDot)).check(
                matches(withEffectiveVisibility(GONE))
            )
        }
    }

    @Test
    fun reliableAvailableSnapshotShowsDeferredSummaryAndRedDot() {
        val now = System.currentTimeMillis()
        seed(UpdateStoredState(
            initialInstallChannel = InitialInstallChannel.NON_PLAY,
            lastAutoAttemptAt = now,
            snapshot = availableSnapshot(now - 4L * UpdatePolicy.DAY_MILLIS),
            deferredVersionCode = 8L,
            deferredUntil = now + UpdatePolicy.DEFER_INTERVAL_MILLIS
        ))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openUpdateSetting()
            var expected = ""
            scenario.onActivity {
                expected = it.getString(R.string.update_status_available_deferred, "1.2")
            }
            onView(withId(R.id.settingsUpdateSummary)).check(matches(withText(expected)))
            onView(withId(R.id.settingsUpdateDot)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun updateDialogSurvivesRecreationRejectsBackAndLaterDefersNextPrompt() {
        val now = System.currentTimeMillis()
        seed(UpdateStoredState(
            initialInstallChannel = InitialInstallChannel.NON_PLAY,
            lastAutoAttemptAt = now,
            snapshot = availableSnapshot(now - 4L * UpdatePolicy.DAY_MILLIS)
        ))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertUpdateDialogActions()
            scenario.recreate()
            assertUpdateDialogActions()

            pressBack()
            assertUpdateDialogActions()

            onView(withText(R.string.update_action_later)).perform(click())
            onView(withText(R.string.update_prompt_title)).check(doesNotExist())

            scenario.recreate()
            onView(withText(R.string.update_prompt_title)).check(doesNotExist())
        }
    }

    @Test
    fun downloadedFlexibleUpdateRestoresPersistentInstallAction() {
        val now = System.currentTimeMillis()
        seed(UpdateStoredState(
            initialInstallChannel = InitialInstallChannel.PLAY,
            lastAutoAttemptAt = now,
            snapshot = UpdateSnapshot.upToDate(
                installedVersionCode = versionCode(),
                channel = UpdateChannel.PLAY,
                checkedAt = now
            ),
            playUpdateDownloaded = true
        ))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText(R.string.update_downloaded_message)).check(matches(isDisplayed()))
            onView(withText(R.string.update_downloaded_action)).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText(R.string.update_downloaded_action)).check(matches(isDisplayed()))
        }
    }

    private fun assertUpdateDialogActions() {
        onView(withText(R.string.update_prompt_title)).check(matches(isDisplayed()))
        onView(withText(R.string.update_action_update)).check(matches(isDisplayed()))
        onView(withText(R.string.update_action_later)).check(matches(isDisplayed()))
        onView(withText(R.string.update_action_skip)).check(matches(isDisplayed()))
    }

    private fun openUpdateSetting() {
        onView(withId(R.id.navigation_settings)).perform(click())
        onView(withId(R.id.settingsUpdateRow)).perform(scrollTo())
    }

    private fun seed(state: UpdateStoredState) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        SharedPreferencesUpdateStateStore(
            instrumentation.targetContext,
            versionCode()
        ).save(state)
        instrumentation.runOnMainSync {
            AppUpdateRuntime.coordinator.reloadPersistedState()
        }
        instrumentation.waitForIdleSync()
    }

    private fun availableSnapshot(availableSinceAt: Long) = UpdateSnapshot(
        state = UpdateSnapshotState.UPDATE_AVAILABLE,
        channel = UpdateChannel.WEBSITE,
        installedVersionCode = versionCode(),
        availableVersionCode = 8L,
        availableVersionName = "1.2",
        availableSinceAt = availableSinceAt,
        firstSeenAt = availableSinceAt,
        checkedAt = System.currentTimeMillis(),
        flexibleAllowed = false
    )

    private fun versionCode(): Long = BuildConfig.VERSION_CODE.toLong()
}
