package com.example.busiscoming

import com.example.busiscoming.data.model.TransitCodeLaunchTargets
import com.example.busiscoming.data.model.TransitCodeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitCodeLaunchTargetTest {
    @Test
    fun definesThreeWechatAndFourAlipayTargets() {
        assertEquals(7, TransitCodeLaunchTargets.all.size)
        assertEquals(3, TransitCodeLaunchTargets.forProvider(TransitCodeProvider.WECHAT).size)
        assertEquals(4, TransitCodeLaunchTargets.forProvider(TransitCodeProvider.ALIPAY).size)

        assertEquals(
            listOf("微信 jumpWxa", "微信明文 Scheme", "微信首頁 path"),
            TransitCodeLaunchTargets.forProvider(TransitCodeProvider.WECHAT).map { it.title }
        )
        assertEquals(
            listOf("支付寶 appId", "支付寶 saId", "支付寶 H5 render", "支付寶 ds 包裝"),
            TransitCodeLaunchTargets.forProvider(TransitCodeProvider.ALIPAY).map { it.title }
        )
    }

    @Test
    fun buildsExpectedWechatUris() {
        val uris = TransitCodeLaunchTargets.forProvider(TransitCodeProvider.WECHAT).map { it.uri }

        assertEquals(
            "weixin://app/wxbe05102357855fc7/jumpWxa/?userName=gh_a2de39e7aeb4",
            uris[0]
        )
        assertEquals(
            "weixin://dl/business/?appid=wxbe05102357855fc7&env_version=release",
            uris[1]
        )
        assertEquals(
            "weixin://dl/business/?appid=wxbe05102357855fc7&path=pages/index/index&env_version=release",
            uris[2]
        )
    }

    @Test
    fun buildsExpectedAlipayUrisAndEncodedDsWrapper() {
        val uris = TransitCodeLaunchTargets.forProvider(TransitCodeProvider.ALIPAY).map { it.uri }

        assertEquals("alipays://platformapi/startapp?appId=200011235", uris[0])
        assertEquals("alipays://platformapi/startapp?saId=200011235", uris[1])
        assertEquals("https://render.alipay.com/p/s/i?appId=200011235", uris[2])
        assertEquals(
            "https://ds.alipay.com/?scheme=alipays%3A%2F%2Fplatformapi%2Fstartapp%3FappId%3D200011235",
            uris[3]
        )
        assertTrue(uris[3].contains("scheme=alipays%3A%2F%2F"))
    }
}
