package com.golink.busiscoming.ui.main

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.golink.busiscoming.R

class TransitCodeShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!TransitCodeEntryPoint.isLaunchAction(intent?.action)) {
            finish()
            return
        }

        val launcher = paymentLauncherFactory?.invoke(this)
            ?: TransitCodePaymentLauncher.forActivity(this)
        val outcome = launcher.launchTransitCode()
        if (outcome.shouldShowFailureToast) {
            Toast.makeText(this, R.string.transit_code_launch_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        @Volatile
        internal var paymentLauncherFactory:
            ((Activity) -> TransitCodePaymentLaunchAction)? = null

        internal fun resetTestDependencies() {
            paymentLauncherFactory = null
        }
    }
}
