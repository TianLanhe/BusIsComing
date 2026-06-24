package com.example.busiscoming

import com.example.busiscoming.data.model.TransitCodeLaunchTargets
import com.example.busiscoming.data.model.TransitCodeLaunchType
import com.example.busiscoming.data.model.TransitCodeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransitCodeLaunchTargetTest {
    @Test
    fun definesThreeWechatSdkAndTwoAlipayHkTargets() {
        assertEquals(5, TransitCodeLaunchTargets.all.size)
        assertEquals(3, TransitCodeLaunchTargets.forProvider(TransitCodeProvider.WECHAT_SDK).size)
        assertEquals(2, TransitCodeLaunchTargets.forProvider(TransitCodeProvider.ALIPAY_HK).size)

        assertEquals(
            listOf("微信 SDK 正式版", "微信 SDK 測試版", "微信 SDK 預覽版"),
            TransitCodeLaunchTargets.forProvider(TransitCodeProvider.WECHAT_SDK).map { it.title }
        )
        assertEquals(
            listOf("AlipayHK Scheme", "AlipayHK HTTPS"),
            TransitCodeLaunchTargets.forProvider(TransitCodeProvider.ALIPAY_HK).map { it.title }
        )
    }

    @Test
    fun buildsExpectedWechatMiniProgramParams() {
        val targets = TransitCodeLaunchTargets.forProvider(TransitCodeProvider.WECHAT_SDK)
        val params = targets.map { target ->
            assertEquals(TransitCodeLaunchType.WECHAT_MINI_PROGRAM, target.launchType)
            assertNull(target.uri)
            assertNotNull(target.wechatMiniProgramParams)
            target.wechatMiniProgramParams!!
        }

        assertEquals(listOf(0, 1, 2), params.map { it.miniprogramType })
        assertEquals(
            listOf(
                "MINIPTOGRAM_TYPE_RELEASE",
                "MINIPROGRAM_TYPE_TEST",
                "MINIPROGRAM_TYPE_PREVIEW"
            ),
            params.map { it.miniprogramTypeName }
        )
        params.forEach {
            assertEquals("wx0a914d80e5b75bfa", it.appId)
            assertEquals("gh_a2de39e7aeb4", it.userName)
            assertEquals("", it.path)
        }
    }

    @Test
    fun buildsExpectedAlipayHkUris() {
        val targets = TransitCodeLaunchTargets.forProvider(TransitCodeProvider.ALIPAY_HK)
        val uris = targets.map { target ->
            assertEquals(TransitCodeLaunchType.VIEW_URI, target.launchType)
            assertNull(target.wechatMiniProgramParams)
            target.uri
        }

        assertEquals("alipayhk://platformapi/startApp?appId=85200098", uris[0])
        assertEquals("https://render.alipay.hk/p/s/hkwallet/landing/easygo", uris[1])
    }
}
