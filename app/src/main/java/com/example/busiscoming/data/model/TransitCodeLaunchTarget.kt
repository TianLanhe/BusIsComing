package com.example.busiscoming.data.model

enum class TransitCodeProvider(val displayName: String) {
    WECHAT_SDK("微信 SDK"),
    ALIPAY_HK("AlipayHK")
}

enum class TransitCodeLaunchType {
    WECHAT_MINI_PROGRAM,
    VIEW_URI
}

data class WechatMiniProgramParams(
    val appId: String,
    val userName: String,
    val path: String,
    val miniprogramTypeName: String,
    val miniprogramType: Int
)

data class TransitCodeLaunchTarget(
    val provider: TransitCodeProvider,
    val title: String,
    val description: String,
    val launchType: TransitCodeLaunchType,
    val uri: String? = null,
    val wechatMiniProgramParams: WechatMiniProgramParams? = null
)

object TransitCodeLaunchTargets {
    const val WECHAT_APP_ID = "wx0a914d80e5b75bfa"
    const val WECHAT_MINI_PROGRAM_USER_NAME = "gh_a2de39e7aeb4"
    const val WECHAT_MINI_PROGRAM_PATH = ""
    const val WECHAT_MINIPROGRAM_TYPE_RELEASE_NAME = "MINIPTOGRAM_TYPE_RELEASE"
    const val WECHAT_MINIPROGRAM_TYPE_TEST_NAME = "MINIPROGRAM_TYPE_TEST"
    const val WECHAT_MINIPROGRAM_TYPE_PREVIEW_NAME = "MINIPROGRAM_TYPE_PREVIEW"
    const val WECHAT_MINIPROGRAM_TYPE_RELEASE = 0
    const val WECHAT_MINIPROGRAM_TYPE_TEST = 1
    const val WECHAT_MINIPROGRAM_TYPE_PREVIEW = 2
    const val ALIPAY_HK_SCHEME_URI = "alipayhk://platformapi/startApp?appId=85200098"
    const val ALIPAY_HK_RENDER_URL = "https://render.alipay.hk/p/s/hkwallet/landing/easygo"

    val all: List<TransitCodeLaunchTarget> = listOf(
        wechatMiniProgramTarget(
            title = "微信 SDK 正式版",
            description = "用正式版 miniprogramType 拉起騰訊乘車碼小程序。",
            miniprogramTypeName = WECHAT_MINIPROGRAM_TYPE_RELEASE_NAME,
            miniprogramType = WECHAT_MINIPROGRAM_TYPE_RELEASE
        ),
        wechatMiniProgramTarget(
            title = "微信 SDK 測試版",
            description = "用測試版 miniprogramType 拉起騰訊乘車碼小程序。",
            miniprogramTypeName = WECHAT_MINIPROGRAM_TYPE_TEST_NAME,
            miniprogramType = WECHAT_MINIPROGRAM_TYPE_TEST
        ),
        wechatMiniProgramTarget(
            title = "微信 SDK 預覽版",
            description = "用預覽版 miniprogramType 拉起騰訊乘車碼小程序。",
            miniprogramTypeName = WECHAT_MINIPROGRAM_TYPE_PREVIEW_NAME,
            miniprogramType = WECHAT_MINIPROGRAM_TYPE_PREVIEW
        ),
        viewUriTarget(
            title = "AlipayHK Scheme",
            description = "嘗試通過 AlipayHK scheme 打開 EasyGo 入口。",
            uri = ALIPAY_HK_SCHEME_URI
        ),
        viewUriTarget(
            title = "AlipayHK HTTPS",
            description = "嘗試通過 AlipayHK HTTPS 中轉頁打開 EasyGo。",
            uri = ALIPAY_HK_RENDER_URL
        )
    )

    fun forProvider(provider: TransitCodeProvider): List<TransitCodeLaunchTarget> {
        return all.filter { it.provider == provider }
    }

    private fun wechatMiniProgramTarget(
        title: String,
        description: String,
        miniprogramTypeName: String,
        miniprogramType: Int
    ): TransitCodeLaunchTarget {
        return TransitCodeLaunchTarget(
            provider = TransitCodeProvider.WECHAT_SDK,
            title = title,
            description = description,
            launchType = TransitCodeLaunchType.WECHAT_MINI_PROGRAM,
            wechatMiniProgramParams = WechatMiniProgramParams(
                appId = WECHAT_APP_ID,
                userName = WECHAT_MINI_PROGRAM_USER_NAME,
                path = WECHAT_MINI_PROGRAM_PATH,
                miniprogramTypeName = miniprogramTypeName,
                miniprogramType = miniprogramType
            )
        )
    }

    private fun viewUriTarget(
        title: String,
        description: String,
        uri: String
    ): TransitCodeLaunchTarget {
        return TransitCodeLaunchTarget(
            provider = TransitCodeProvider.ALIPAY_HK,
            title = title,
            description = description,
            launchType = TransitCodeLaunchType.VIEW_URI,
            uri = uri
        )
    }
}
