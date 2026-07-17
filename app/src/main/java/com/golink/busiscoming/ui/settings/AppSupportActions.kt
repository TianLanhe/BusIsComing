package com.golink.busiscoming.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.golink.busiscoming.R
import com.golink.busiscoming.data.local.AppLanguageRepository

object AppSupportActions {
    const val websiteBaseUrl = "https://www.busiscoming.com"
    const val feedbackEmail = "hezhenyu966@gmail.com"

    fun websiteUrl(context: Context): String =
        websiteBaseUrl + AppLanguageRepository(context).snapshot().websitePath

    fun privacyPolicyUrl(context: Context): String =
        websiteBaseUrl + AppLanguageRepository(context).snapshot().privacyPath

    fun shareText(context: Context): String =
        context.getString(R.string.share_copy, websiteUrl(context))

    fun shareApp(context: Context) {
        val sendIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, shareText(context))
        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.settings_share_app))
        startOrToast(context, chooser, R.string.share_app_failed)
    }

    fun sendFeedback(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO)
            .setData(Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(feedbackEmail))
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.feedback_subject))
            .putExtra(
                Intent.EXTRA_TEXT,
                feedbackBody(
                    context = context,
                    appVersion = context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .versionName
                        .orEmpty(),
                    androidVersion = Build.VERSION.RELEASE.orEmpty(),
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                )
            )
        startOrToast(context, intent, R.string.feedback_failed)
    }

    fun openPrivacyPolicy(context: Context) {
        openUrl(context, privacyPolicyUrl(context), R.string.privacy_policy_failed)
    }

    fun openWebsite(context: Context) {
        openUrl(context, websiteUrl(context), R.string.website_failed)
    }

    fun feedbackBody(
        context: Context,
        appVersion: String,
        androidVersion: String,
        deviceModel: String
    ): String {
        return context.getString(
            R.string.feedback_body,
            appVersion,
            androidVersion,
            deviceModel
        )
    }

    private fun openUrl(context: Context, url: String, failureMessageRes: Int) {
        startOrToast(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)), failureMessageRes)
    }

    internal fun startOrToast(
        context: Context,
        intent: Intent,
        failureMessageRes: Int,
        starter: (Context, Intent) -> Unit = { targetContext, targetIntent ->
            targetContext.startActivity(targetIntent)
        },
        toaster: (Context, Int) -> Unit = { targetContext, messageRes ->
            Toast.makeText(targetContext, messageRes, Toast.LENGTH_SHORT).show()
        }
    ): Boolean {
        try {
            starter(context, intent)
            return true
        } catch (_: ActivityNotFoundException) {
            toaster(context, failureMessageRes)
            return false
        }
    }
}
