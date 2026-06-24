package com.example.busiscoming.wxapi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.example.busiscoming.data.model.TransitCodeLaunchTargets
import com.example.busiscoming.ui.main.AndroidTransitCodeDiagnosticLogger
import com.example.busiscoming.ui.main.TransitCodeDiagnosticResult
import com.example.busiscoming.ui.main.TransitCodeDiagnostics
import com.example.busiscoming.ui.main.TransitCodeLaunchStatus
import com.example.busiscoming.ui.main.publishWechatCallbackDiagnostic
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory

class WXEntryActivity : Activity(), IWXAPIEventHandler {
    private lateinit var wxApi: IWXAPI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wxApi = WXAPIFactory.createWXAPI(this, TransitCodeLaunchTargets.WECHAT_APP_ID, true)
        handleWechatIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWechatIntent(intent)
    }

    override fun onReq(req: BaseReq?) {
        val diagnostic = TransitCodeDiagnosticResult(
            targetTitle = "微信 SDK 請求",
            providerName = "微信 SDK",
            status = TransitCodeLaunchStatus.CALLBACK,
            summary = "收到微信 SDK onReq。",
            details = listOf(
                "type=${req?.type ?: "(未知)"}",
                "transaction=${req?.transaction ?: "(無)"}"
            ),
            timestampMillis = System.currentTimeMillis()
        )
        AndroidTransitCodeDiagnosticLogger.log(diagnostic)
        TransitCodeDiagnostics.publish(diagnostic)
        finish()
    }

    override fun onResp(resp: BaseResp?) {
        publishWechatCallbackDiagnostic(
            errCode = resp?.errCode,
            errStr = resp?.errStr,
            extMsg = resp?.fieldValue("extMsg"),
            transaction = resp?.transaction,
            handled = true
        )
        finish()
    }

    private fun handleWechatIntent(intent: Intent?) {
        val handled = try {
            intent != null && wxApi.handleIntent(intent, this)
        } catch (error: Throwable) {
            val diagnostic = TransitCodeDiagnosticResult(
                targetTitle = "微信 SDK 回調",
                providerName = "微信 SDK",
                status = TransitCodeLaunchStatus.ERROR,
                summary = "處理微信 SDK 回調時發生異常。",
                details = listOf(
                    "exceptionClass=${error::class.java.name}",
                    "exceptionMessage=${error.message ?: "(無)"}"
                ),
                timestampMillis = System.currentTimeMillis()
            )
            AndroidTransitCodeDiagnosticLogger.log(diagnostic)
            TransitCodeDiagnostics.publish(diagnostic)
            finish()
            return
        }
        if (!handled) {
            publishWechatCallbackDiagnostic(
                errCode = null,
                errStr = null,
                extMsg = null,
                transaction = null,
                handled = false
            )
            finish()
        } else {
            finish()
        }
    }

    private fun Any.fieldValue(name: String): String? {
        return try {
            javaClass.getField(name).get(this)?.toString()
        } catch (_: Throwable) {
            null
        }
    }
}
