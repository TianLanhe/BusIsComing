package com.example.busiscoming.data.model

import java.net.URLEncoder

enum class TransitCodeProvider(val displayName: String) {
    WECHAT("微信"),
    ALIPAY("支付寶")
}

data class TransitCodeLaunchTarget(
    val provider: TransitCodeProvider,
    val title: String,
    val description: String,
    val uri: String
)

object TransitCodeLaunchTargets {
    const val WECHAT_JUMP_WXA_URI =
        "weixin://app/wxbe05102357855fc7/jumpWxa/?userName=gh_a2de39e7aeb4"
    const val WECHAT_BUSINESS_URI =
        "weixin://dl/business/?appid=wxbe05102357855fc7&env_version=release"
    const val WECHAT_BUSINESS_HOME_PATH_URI =
        "weixin://dl/business/?appid=wxbe05102357855fc7&path=pages/index/index&env_version=release"
    const val ALIPAY_APP_ID_URI =
        "alipays://platformapi/startapp?appId=200011235"
    const val ALIPAY_SA_ID_URI =
        "alipays://platformapi/startapp?saId=200011235"
    const val ALIPAY_H5_RENDER_URL =
        "https://render.alipay.com/p/s/i?appId=200011235"
    const val ALIPAY_DS_BASE_URL =
        "https://ds.alipay.com/?scheme="

    val all: List<TransitCodeLaunchTarget> = listOf(
        TransitCodeLaunchTarget(
            provider = TransitCodeProvider.WECHAT,
            title = "微信 jumpWxa",
            description = "嘗試直接打開騰訊乘車碼小程序。",
            uri = WECHAT_JUMP_WXA_URI
        ),
        TransitCodeLaunchTarget(
            provider = TransitCodeProvider.WECHAT,
            title = "微信明文 Scheme",
            description = "嘗試通過微信 business scheme 打開入口。",
            uri = WECHAT_BUSINESS_URI
        ),
        TransitCodeLaunchTarget(
            provider = TransitCodeProvider.WECHAT,
            title = "微信首頁 path",
            description = "嘗試指定小程序首頁 path。",
            uri = WECHAT_BUSINESS_HOME_PATH_URI
        ),
        TransitCodeLaunchTarget(
            provider = TransitCodeProvider.ALIPAY,
            title = "支付寶 appId",
            description = "嘗試以 appId 直接打開乘車碼入口。",
            uri = ALIPAY_APP_ID_URI
        ),
        TransitCodeLaunchTarget(
            provider = TransitCodeProvider.ALIPAY,
            title = "支付寶 saId",
            description = "嘗試以 saId 打開支付寶服務入口。",
            uri = ALIPAY_SA_ID_URI
        ),
        TransitCodeLaunchTarget(
            provider = TransitCodeProvider.ALIPAY,
            title = "支付寶 H5 render",
            description = "嘗試通過支付寶 H5 中轉頁打開。",
            uri = ALIPAY_H5_RENDER_URL
        ),
        TransitCodeLaunchTarget(
            provider = TransitCodeProvider.ALIPAY,
            title = "支付寶 ds 包裝",
            description = "嘗試通過 ds.alipay.com 包裝 scheme。",
            uri = buildAlipayDsWrapperUrl(ALIPAY_APP_ID_URI)
        )
    )

    fun forProvider(provider: TransitCodeProvider): List<TransitCodeLaunchTarget> {
        return all.filter { it.provider == provider }
    }

    fun buildAlipayDsWrapperUrl(innerScheme: String): String {
        return ALIPAY_DS_BASE_URL + URLEncoder.encode(innerScheme, "UTF-8")
    }
}
