package com.golink.busiscoming.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.golink.busiscoming.R
import com.golink.busiscoming.data.localization.AppLanguage

object AppUpdateExternalActions {
    data class ExternalUpdateTarget(
        val url: String,
        val packageName: String? = null
    )

    fun openPlayListing(
        context: Context,
        starter: (Context, ExternalUpdateTarget) -> Unit = { target, destination ->
            target.startActivity(destination.toIntent())
        },
        toaster: (Context, Int) -> Unit = { target, message ->
            Toast.makeText(target, message, Toast.LENGTH_SHORT).show()
        }
    ): Boolean {
        val marketTarget = ExternalUpdateTarget(
            url = AppUpdateLinks.PLAY_MARKET_URL,
            packageName = PLAY_PACKAGE_NAME
        )
        if (tryStart(context, marketTarget, starter)) return true
        if (tryStart(context, ExternalUpdateTarget(AppUpdateLinks.PLAY_HTTPS_URL), starter)) {
            return true
        }
        toaster(context, R.string.update_open_failed)
        return false
    }

    fun openWebsiteDownloadPage(
        context: Context,
        language: AppLanguage,
        starter: (Context, ExternalUpdateTarget) -> Unit = { target, destination ->
            target.startActivity(destination.toIntent())
        },
        toaster: (Context, Int) -> Unit = { target, message ->
            Toast.makeText(target, message, Toast.LENGTH_SHORT).show()
        }
    ): Boolean {
        val destination = ExternalUpdateTarget(AppUpdateLinks.websiteDownloadPage(language))
        if (tryStart(context, destination, starter)) return true
        toaster(context, R.string.update_website_failed)
        return false
    }

    private fun ExternalUpdateTarget.toIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        packageName?.let(::setPackage)
    }

    private fun tryStart(
        context: Context,
        destination: ExternalUpdateTarget,
        starter: (Context, ExternalUpdateTarget) -> Unit
    ): Boolean = try {
        starter(context, destination)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
