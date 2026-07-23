package com.golink.busiscoming

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.TransitCodeEntryPoint
import com.golink.busiscoming.ui.main.TransitCodePaymentLaunchAction
import com.golink.busiscoming.ui.main.TransitCodePaymentLaunchOutcome
import com.golink.busiscoming.ui.main.TransitCodeShortcutActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitCodeShortcutActivityInstrumentedTest {
    @After
    fun resetDependencies() {
        TransitCodeShortcutActivity.resetTestDependencies()
    }

    @Test
    fun shortcutRelayLaunchesPaymentOnceAndFinishesWithoutCreatingMainActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        val launchCount = AtomicInteger(0)
        val mainCreated = AtomicBoolean(false)
        val relayDestroyed = CountDownLatch(1)
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity) mainCreated.set(true)
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (activity is TransitCodeShortcutActivity) relayDestroyed.countDown()
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        TransitCodeShortcutActivity.paymentLauncherFactory = {
            object : TransitCodePaymentLaunchAction {
                override fun launchTransitCode(): TransitCodePaymentLaunchOutcome {
                    launchCount.incrementAndGet()
                    return TransitCodePaymentLaunchOutcome(
                        started = true,
                        startedTarget = null,
                        attempts = emptyList(),
                        shouldShowFailureToast = false
                    )
                }
            }
        }

        try {
            context.startActivity(TransitCodeEntryPoint.createShortcutIntent(context))

            assertTrue(relayDestroyed.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertEquals(1, launchCount.get())
            assertFalse(mainCreated.get())
            assertNoRelayTask(context.getSystemService(ActivityManager::class.java))
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    @Test
    fun failedShortcutRelayStillFinishesWithoutLeavingAnAppTask() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        val relayDestroyed = CountDownLatch(1)
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityDestroyed(activity: Activity) {
                if (activity is TransitCodeShortcutActivity) relayDestroyed.countDown()
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        }
        application.registerActivityLifecycleCallbacks(callbacks)
        TransitCodeShortcutActivity.paymentLauncherFactory = {
            object : TransitCodePaymentLaunchAction {
                override fun launchTransitCode(): TransitCodePaymentLaunchOutcome {
                    return TransitCodePaymentLaunchOutcome(
                        started = false,
                        startedTarget = null,
                        attempts = emptyList(),
                        shouldShowFailureToast = true
                    )
                }
            }
        }

        try {
            context.startActivity(TransitCodeEntryPoint.createShortcutIntent(context))

            assertTrue(relayDestroyed.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertNoRelayTask(context.getSystemService(ActivityManager::class.java))
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    private fun assertNoRelayTask(activityManager: ActivityManager) {
        val relayClassName = TransitCodeShortcutActivity::class.java.name
        assertTrue(
            activityManager.appTasks.mapNotNull { appTask ->
                runCatching { appTask.taskInfo }.getOrNull()
            }.none { taskInfo ->
                taskInfo.baseActivity?.className == relayClassName ||
                    taskInfo.topActivity?.className == relayClassName
            }
        )
    }
}
