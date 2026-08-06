package com.golink.busiscoming.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.golink.busiscoming.data.localization.AppLanguage

enum class RatingExternalAction {
    PRODUCT_PAGE,
    PLAY_APP_SETTINGS,
    APP_SETTINGS,
    OFFICIAL_HELP
}

data class RatingExternalTarget(
    val action: RatingExternalAction,
    val url: String,
    val packageName: String? = null
)

object GooglePlayRatingLinks {
    private const val OFFICIAL_HELP =
        "https://support.google.com/googleplay/answer/190860"

    fun productPage(): String = AppUpdateLinks.PLAY_HTTPS_URL

    fun officialHelp(language: AppLanguage): String = "$OFFICIAL_HELP?hl=${when (language) {
        AppLanguage.TRADITIONAL_CHINESE -> "zh-HK"
        AppLanguage.SIMPLIFIED_CHINESE -> "zh-CN"
        AppLanguage.ENGLISH -> "en"
    }}"
}

class GooglePlayRatingNavigator {
    fun openProductPage(
        context: Context,
        starter: (Context, RatingExternalTarget) -> Unit = ::start
    ): Boolean = open(
        context,
        RatingExternalTarget(
            action = RatingExternalAction.PRODUCT_PAGE,
            url = GooglePlayRatingLinks.productPage(),
            packageName = PLAY_PACKAGE_NAME
        ),
        starter
    )

    fun openPlayAppSettings(
        context: Context,
        starter: (Context, RatingExternalTarget) -> Unit = ::start
    ): Boolean = open(
        context,
        RatingExternalTarget(
            action = RatingExternalAction.PLAY_APP_SETTINGS,
            url = "package:$PLAY_PACKAGE_NAME"
        ),
        starter
    )

    fun openAppSettings(
        context: Context,
        starter: (Context, RatingExternalTarget) -> Unit = ::start
    ): Boolean = open(
        context,
        RatingExternalTarget(
            action = RatingExternalAction.APP_SETTINGS,
            url = "package:${context.packageName}"
        ),
        starter
    )

    fun openOfficialHelp(
        context: Context,
        language: AppLanguage,
        starter: (Context, RatingExternalTarget) -> Unit = ::start
    ): Boolean = open(
        context,
        RatingExternalTarget(
            action = RatingExternalAction.OFFICIAL_HELP,
            url = GooglePlayRatingLinks.officialHelp(language)
        ),
        starter
    )

    private fun open(
        context: Context,
        target: RatingExternalTarget,
        starter: (Context, RatingExternalTarget) -> Unit
    ): Boolean = try {
        starter(context, target)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private companion object {
        fun start(context: Context, target: RatingExternalTarget) {
            val intent = when (target.action) {
                RatingExternalAction.PRODUCT_PAGE -> Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(target.url)
                ).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(target.packageName)
                RatingExternalAction.PLAY_APP_SETTINGS,
                RatingExternalAction.APP_SETTINGS -> Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse(target.url)
                )
                RatingExternalAction.OFFICIAL_HELP -> Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(target.url)
                ).addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
        }
    }
}
