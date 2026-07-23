package com.golink.busiscoming.data.update

import com.golink.busiscoming.data.localization.AppLanguage

object AppUpdateLinks {
    private const val WEBSITE_ORIGIN = "https://www.busiscoming.com"
    const val PLAY_HTTPS_URL =
        "https://play.google.com/store/apps/details?id=com.golink.busiscoming"
    const val PLAY_MARKET_URL = "market://details?id=com.golink.busiscoming"

    fun websiteDownloadPage(language: AppLanguage): String = when (language) {
        AppLanguage.TRADITIONAL_CHINESE -> "$WEBSITE_ORIGIN/zh-hant/#download"
        AppLanguage.SIMPLIFIED_CHINESE -> "$WEBSITE_ORIGIN/zh-hans/#download"
        AppLanguage.ENGLISH -> "$WEBSITE_ORIGIN/en/#download"
    }
}

