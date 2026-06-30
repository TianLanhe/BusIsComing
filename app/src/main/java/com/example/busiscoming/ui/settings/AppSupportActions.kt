package com.example.busiscoming.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.busiscoming.R

object AppSupportActions {
    const val websiteUrl = "https://www.busiscoming.com"
    const val privacyPolicyUrl = "https://www.busiscoming.com/zh-hant/privacy/"
    const val feedbackEmail = "hezhenyu966@gmail.com"
    const val feedbackSubject = "BusIsComing 問題反饋"
    const val shareText =
        "搭巴士前想快一點知道哪條路線合適？BusIsComing 可以保存常用路線，快速比較候車時間、票價和耗時。https://www.busiscoming.com"

    fun shareApp(context: Context) {
        val sendIntent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, shareText)
        val chooser = Intent.createChooser(sendIntent, context.getString(R.string.settings_share_app))
        startOrToast(context, chooser, R.string.share_app_failed)
    }

    fun sendFeedback(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO)
            .setData(Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(feedbackEmail))
            .putExtra(Intent.EXTRA_SUBJECT, feedbackSubject)
            .putExtra(
                Intent.EXTRA_TEXT,
                feedbackBody(
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
        openUrl(context, privacyPolicyUrl, R.string.privacy_policy_failed)
    }

    fun openWebsite(context: Context) {
        openUrl(context, websiteUrl, R.string.website_failed)
    }

    fun feedbackBody(
        appVersion: String,
        androidVersion: String,
        deviceModel: String
    ): String {
        return """
            App 版本：$appVersion
            Android 版本：$androidVersion
            設備型號：$deviceModel

            問題描述：
        """.trimIndent()
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
