package com.example.busiscoming.ui.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.busiscoming.data.model.TransitCodePaymentTarget
import com.example.busiscoming.data.model.TransitCodePaymentTargets
import com.example.busiscoming.data.model.TransitCodeWalletInstallState

private const val TRANSIT_CODE_PAYMENT_LOG_TAG = "TransitCodePaymentLauncher"

data class TransitCodePaymentLaunchAttempt(
    val target: TransitCodePaymentTarget,
    val started: Boolean,
    val errorClass: String? = null,
    val errorMessage: String? = null
)

data class TransitCodePaymentLaunchOutcome(
    val started: Boolean,
    val startedTarget: TransitCodePaymentTarget?,
    val attempts: List<TransitCodePaymentLaunchAttempt>,
    val shouldShowFailureToast: Boolean
)

interface TransitCodePaymentPackageDetector {
    fun isPackageInstalled(packageName: String): Boolean
}

interface TransitCodePaymentUriStarter {
    fun start(uri: String)
}

interface TransitCodePaymentLaunchLogger {
    fun log(message: String)
}

interface TransitCodePaymentLaunchAction {
    fun launchTransitCode(): TransitCodePaymentLaunchOutcome
}

class TransitCodePaymentLauncher(
    private val packageDetector: TransitCodePaymentPackageDetector,
    private val uriStarter: TransitCodePaymentUriStarter,
    private val logger: TransitCodePaymentLaunchLogger = AndroidTransitCodePaymentLaunchLogger
) : TransitCodePaymentLaunchAction {
    override fun launchTransitCode(): TransitCodePaymentLaunchOutcome {
        val installState = TransitCodeWalletInstallState(
            alipayHkInstalled = isPackageInstalled(TransitCodePaymentTargets.ALIPAY_HK_PACKAGE_NAME),
            alipayInstalled = isPackageInstalled(TransitCodePaymentTargets.ALIPAY_PACKAGE_NAME)
        )
        val targets = TransitCodePaymentTargets.forInstallState(installState)
        val attempts = mutableListOf<TransitCodePaymentLaunchAttempt>()
        logger.log(
            "installState alipayHk=${installState.alipayHkInstalled}, " +
                "alipay=${installState.alipayInstalled}, targets=${targets.joinToString { it.title }}"
        )

        targets.forEach { target ->
            val attempt = launchTarget(target)
            attempts += attempt
            if (attempt.started) {
                return TransitCodePaymentLaunchOutcome(
                    started = true,
                    startedTarget = target,
                    attempts = attempts,
                    shouldShowFailureToast = false
                )
            }
        }

        return TransitCodePaymentLaunchOutcome(
            started = false,
            startedTarget = null,
            attempts = attempts,
            shouldShowFailureToast = true
        )
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageDetector.isPackageInstalled(packageName)
        } catch (error: Throwable) {
            logger.log(
                "package=$packageName installed=false, " +
                    "exceptionClass=${error::class.java.name}, exceptionMessage=${error.message ?: "(無)"}"
            )
            false
        }
    }

    private fun launchTarget(target: TransitCodePaymentTarget): TransitCodePaymentLaunchAttempt {
        return try {
            uriStarter.start(target.uri)
            TransitCodePaymentLaunchAttempt(target = target, started = true).also {
                logger.log("target=${target.title}, uri=${target.uri}, startActivity=true")
            }
        } catch (error: ActivityNotFoundException) {
            failedAttempt(target, error)
        } catch (error: SecurityException) {
            failedAttempt(target, error)
        } catch (error: Throwable) {
            failedAttempt(target, error)
        }
    }

    private fun failedAttempt(
        target: TransitCodePaymentTarget,
        error: Throwable
    ): TransitCodePaymentLaunchAttempt {
        return TransitCodePaymentLaunchAttempt(
            target = target,
            started = false,
            errorClass = error::class.java.name,
            errorMessage = error.message
        ).also {
            logger.log(
                "target=${target.title}, uri=${target.uri}, startActivity=false, " +
                    "exceptionClass=${it.errorClass}, exceptionMessage=${it.errorMessage ?: "(無)"}"
            )
        }
    }

    companion object {
        fun forActivity(activity: Activity): TransitCodePaymentLauncher {
            return TransitCodePaymentLauncher(
                packageDetector = AndroidTransitCodePaymentPackageDetector(activity),
                uriStarter = AndroidTransitCodePaymentUriStarter(activity)
            )
        }
    }
}

private class AndroidTransitCodePaymentPackageDetector(
    context: Context
) : TransitCodePaymentPackageDetector {
    private val packageManager = context.applicationContext.packageManager

    override fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}

private class AndroidTransitCodePaymentUriStarter(
    private val activity: Activity
) : TransitCodePaymentUriStarter {
    override fun start(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        activity.startActivity(intent)
    }
}

object AndroidTransitCodePaymentLaunchLogger : TransitCodePaymentLaunchLogger {
    override fun log(message: String) {
        Log.i(TRANSIT_CODE_PAYMENT_LOG_TAG, message)
    }
}
