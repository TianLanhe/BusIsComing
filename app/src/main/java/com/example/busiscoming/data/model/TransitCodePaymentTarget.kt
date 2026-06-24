package com.example.busiscoming.data.model

enum class TransitCodePaymentProvider(val displayName: String) {
    ALIPAY_HK("AlipayHK"),
    ALIPAY("支付寶")
}

enum class TransitCodePaymentLaunchMethod {
    SCHEME,
    HTTPS
}

data class TransitCodeWalletInstallState(
    val alipayHkInstalled: Boolean,
    val alipayInstalled: Boolean
)

data class TransitCodePaymentTarget(
    val provider: TransitCodePaymentProvider,
    val method: TransitCodePaymentLaunchMethod,
    val title: String,
    val uri: String
)

object TransitCodePaymentTargets {
    const val ALIPAY_HK_PACKAGE_NAME = "hk.alipay.wallet"
    const val ALIPAY_PACKAGE_NAME = "com.eg.android.AlipayGphone"
    const val ALIPAY_HK_SCHEME_URI = "alipayhk://platformapi/startApp?appId=85200098"
    const val ALIPAY_HK_RENDER_URL = "https://render.alipay.hk/p/s/hkwallet/landing/easygo"
    const val ALIPAY_SCHEME_URI = "alipays://platformapi/startapp?appId=200011235"
    const val ALIPAY_RENDER_URL = "https://render.alipay.com/p/s/i?appId=200011235"

    val alipayHkScheme = TransitCodePaymentTarget(
        provider = TransitCodePaymentProvider.ALIPAY_HK,
        method = TransitCodePaymentLaunchMethod.SCHEME,
        title = "AlipayHK Scheme",
        uri = ALIPAY_HK_SCHEME_URI
    )
    val alipayHkHttps = TransitCodePaymentTarget(
        provider = TransitCodePaymentProvider.ALIPAY_HK,
        method = TransitCodePaymentLaunchMethod.HTTPS,
        title = "AlipayHK HTTPS",
        uri = ALIPAY_HK_RENDER_URL
    )
    val alipayScheme = TransitCodePaymentTarget(
        provider = TransitCodePaymentProvider.ALIPAY,
        method = TransitCodePaymentLaunchMethod.SCHEME,
        title = "支付寶 Scheme",
        uri = ALIPAY_SCHEME_URI
    )
    val alipayHttps = TransitCodePaymentTarget(
        provider = TransitCodePaymentProvider.ALIPAY,
        method = TransitCodePaymentLaunchMethod.HTTPS,
        title = "支付寶 HTTPS",
        uri = ALIPAY_RENDER_URL
    )

    fun forInstallState(state: TransitCodeWalletInstallState): List<TransitCodePaymentTarget> {
        return when {
            state.alipayHkInstalled && state.alipayInstalled -> listOf(
                alipayHkScheme,
                alipayHkHttps,
                alipayScheme,
                alipayHttps
            )
            state.alipayHkInstalled -> listOf(alipayHkScheme, alipayHkHttps)
            state.alipayInstalled -> listOf(alipayScheme, alipayHttps)
            else -> listOf(alipayHkHttps)
        }
    }
}
