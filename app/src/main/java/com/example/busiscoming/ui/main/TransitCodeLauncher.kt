package com.example.busiscoming.ui.main

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.busiscoming.data.model.TransitCodeLaunchTarget
import com.example.busiscoming.data.model.TransitCodeLaunchType
import com.example.busiscoming.data.model.TransitCodeLaunchTargets
import com.example.busiscoming.data.model.TransitCodeProvider
import com.example.busiscoming.data.model.WechatMiniProgramParams
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

private const val TRANSIT_CODE_LOG_TAG = "TransitCodeLauncher"
private const val WECHAT_PACKAGE_NAME = "com.tencent.mm"

class TransitCodeLauncher(
    private val viewUriLauncher: ViewUriLauncher,
    private val wechatMiniProgramClient: WechatMiniProgramClient,
    private val diagnostics: TransitCodeDiagnosticSink = TransitCodeDiagnostics,
    private val logger: TransitCodeDiagnosticLogger = AndroidTransitCodeDiagnosticLogger,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    fun launch(target: TransitCodeLaunchTarget): TransitCodeLaunchResult {
        return when (target.launchType) {
            TransitCodeLaunchType.WECHAT_MINI_PROGRAM -> launchWechatMiniProgram(target)
            TransitCodeLaunchType.VIEW_URI -> launchViewUri(target)
        }
    }

    private fun launchWechatMiniProgram(target: TransitCodeLaunchTarget): TransitCodeLaunchResult {
        val params = target.wechatMiniProgramParams
        if (params == null) {
            val diagnostic = unexpectedDiagnostic(
                target = target,
                summary = "缺少微信小程序參數。",
                details = listOf("launchType=${target.launchType}")
            )
            publish(diagnostic)
            return TransitCodeLaunchResult.UNEXPECTED_ERROR
        }

        val report = try {
            wechatMiniProgramClient.launch(params)
        } catch (error: Throwable) {
            WechatMiniProgramLaunchReport(
                appPackageName = null,
                appSignatureSha256 = null,
                wechatPackageVisible = false,
                registerAppResult = false,
                isWXAppInstalled = false,
                wxAppSupportApi = null,
                isWXAppSupportApi = false,
                sendReqResult = false,
                exceptionClass = error::class.java.name,
                exceptionMessage = error.message
            )
        }
        val status = when {
            report.exceptionClass != null -> TransitCodeLaunchStatus.ERROR
            !report.registerAppResult -> TransitCodeLaunchStatus.UNAVAILABLE
            !report.isWXAppInstalled -> TransitCodeLaunchStatus.UNAVAILABLE
            !report.isWXAppSupportApi -> TransitCodeLaunchStatus.UNSUPPORTED
            !report.sendReqResult -> TransitCodeLaunchStatus.REQUEST_REJECTED
            else -> TransitCodeLaunchStatus.REQUEST_SENT
        }
        val summary = when (status) {
            TransitCodeLaunchStatus.REQUEST_SENT -> "微信 SDK 請求已送出。"
            TransitCodeLaunchStatus.UNAVAILABLE -> "微信 SDK 請求未送出，請檢查微信安裝、AppID、包名或簽名配置。"
            TransitCodeLaunchStatus.UNSUPPORTED -> "目前微信版本不支援 OpenSDK 小程序拉起。"
            TransitCodeLaunchStatus.REQUEST_REJECTED -> "微信 SDK sendReq 返回 false。"
            TransitCodeLaunchStatus.ERROR -> "微信 SDK 啟動時發生異常。"
            TransitCodeLaunchStatus.CALLBACK -> "收到微信 SDK 回調。"
        }
        val details = mutableListOf(
            "appId=${params.appId}",
            "userName=${params.userName}",
            "path=${params.path.ifEmpty { "(空)" }}",
            "miniprogramType=${params.miniprogramTypeName}/${params.miniprogramType}",
            "appPackageName=${report.appPackageName ?: "(未知)"}",
            "appSignatureSha256=${report.appSignatureSha256 ?: "(不可取得)"}",
            "wechatPackageVisible=${report.wechatPackageVisible}",
            "isWXAppInstalled=${report.isWXAppInstalled}",
            "wxAppSupportApi=${report.wxAppSupportApi ?: "(未知)"}",
            "isWXAppSupportApi=${report.isWXAppSupportApi}",
            "registerApp=${report.registerAppResult}",
            "sendReq=${report.sendReqResult}"
        )
        report.exceptionClass?.let { details.add("exceptionClass=$it") }
        report.exceptionMessage?.let { details.add("exceptionMessage=$it") }
        val diagnostic = TransitCodeDiagnosticResult(
            targetTitle = target.title,
            providerName = target.provider.displayName,
            status = status,
            summary = summary,
            details = details,
            timestampMillis = clock()
        )
        publish(diagnostic)
        return if (status == TransitCodeLaunchStatus.REQUEST_SENT) {
            TransitCodeLaunchResult.SUCCESS
        } else if (status == TransitCodeLaunchStatus.ERROR) {
            TransitCodeLaunchResult.UNEXPECTED_ERROR
        } else {
            TransitCodeLaunchResult.UNAVAILABLE
        }
    }

    private fun launchViewUri(target: TransitCodeLaunchTarget): TransitCodeLaunchResult {
        val uri = target.uri
        if (uri == null) {
            val diagnostic = unexpectedDiagnostic(
                target = target,
                summary = "缺少 URI 或 URL。",
                details = listOf("launchType=${target.launchType}")
            )
            publish(diagnostic)
            return TransitCodeLaunchResult.UNEXPECTED_ERROR
        }

        val resolvedActivity = viewUriLauncher.resolveActivity(uri)
        val details = mutableListOf(
            "uri=$uri",
            "resolveActivity=${resolvedActivity ?: "(無)"}"
        )
        return try {
            viewUriLauncher.start(uri)
            details.add("startActivity=true")
            val diagnostic = TransitCodeDiagnosticResult(
                targetTitle = target.title,
                providerName = target.provider.displayName,
                status = TransitCodeLaunchStatus.REQUEST_SENT,
                summary = "已送出 AlipayHK 跳轉請求。",
                details = details,
                timestampMillis = clock()
            )
            publish(diagnostic)
            TransitCodeLaunchResult.SUCCESS
        } catch (error: ActivityNotFoundException) {
            details.add("startActivity=false")
            details.add("exceptionClass=${error::class.java.name}")
            error.message?.let { details.add("exceptionMessage=$it") }
            val diagnostic = TransitCodeDiagnosticResult(
                targetTitle = target.title,
                providerName = target.provider.displayName,
                status = TransitCodeLaunchStatus.UNAVAILABLE,
                summary = "系統沒有可處理此 AlipayHK 入口的 Activity。",
                details = details,
                timestampMillis = clock()
            )
            publish(diagnostic)
            TransitCodeLaunchResult.UNAVAILABLE
        } catch (error: SecurityException) {
            details.add("startActivity=false")
            details.add("exceptionClass=${error::class.java.name}")
            error.message?.let { details.add("exceptionMessage=$it") }
            val diagnostic = TransitCodeDiagnosticResult(
                targetTitle = target.title,
                providerName = target.provider.displayName,
                status = TransitCodeLaunchStatus.UNAVAILABLE,
                summary = "系統安全限制阻止啟動此 AlipayHK 入口。",
                details = details,
                timestampMillis = clock()
            )
            publish(diagnostic)
            TransitCodeLaunchResult.UNAVAILABLE
        } catch (error: Throwable) {
            details.add("startActivity=false")
            details.add("exceptionClass=${error::class.java.name}")
            error.message?.let { details.add("exceptionMessage=$it") }
            val diagnostic = unexpectedDiagnostic(
                target = target,
                summary = "啟動此 AlipayHK 入口時發生非預期異常。",
                details = details
            )
            publish(diagnostic)
            TransitCodeLaunchResult.UNEXPECTED_ERROR
        }
    }

    private fun unexpectedDiagnostic(
        target: TransitCodeLaunchTarget,
        summary: String,
        details: List<String>
    ): TransitCodeDiagnosticResult {
        return TransitCodeDiagnosticResult(
            targetTitle = target.title,
            providerName = target.provider.displayName,
            status = TransitCodeLaunchStatus.ERROR,
            summary = summary,
            details = details,
            timestampMillis = clock()
        )
    }

    private fun publish(diagnostic: TransitCodeDiagnosticResult) {
        logger.log(diagnostic)
        diagnostics.publish(diagnostic)
    }

    companion object {
        fun forActivity(activity: Activity): TransitCodeLauncher {
            return TransitCodeLauncher(
                viewUriLauncher = AndroidViewUriLauncher(activity),
                wechatMiniProgramClient = AndroidWechatMiniProgramClient(activity)
            )
        }
    }
}

interface ViewUriLauncher {
    fun resolveActivity(uri: String): String?
    fun start(uri: String)
}

private class AndroidViewUriLauncher(
    private val activity: Activity
) : ViewUriLauncher {
    override fun resolveActivity(uri: String): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        val resolved = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.let { "${it.packageName}/${it.name}" }
    }

    override fun start(uri: String) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }
}

interface WechatMiniProgramClient {
    fun launch(params: WechatMiniProgramParams): WechatMiniProgramLaunchReport
}

data class WechatMiniProgramLaunchReport(
    val appPackageName: String?,
    val appSignatureSha256: String?,
    val wechatPackageVisible: Boolean,
    val registerAppResult: Boolean,
    val isWXAppInstalled: Boolean,
    val wxAppSupportApi: Int?,
    val isWXAppSupportApi: Boolean,
    val sendReqResult: Boolean,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null
)

private class AndroidWechatMiniProgramClient(
    context: Context
) : WechatMiniProgramClient {
    private val appContext = context.applicationContext

    override fun launch(params: WechatMiniProgramParams): WechatMiniProgramLaunchReport {
        val packageManager = appContext.packageManager
        val appPackageName = appContext.packageName
        val appSignatureSha256 = appSignatureSha256(packageManager, appPackageName)
        val wechatPackageVisible = isPackageVisible(packageManager, WECHAT_PACKAGE_NAME)
        val api = WXAPIFactory.createWXAPI(appContext, params.appId, true)
        val registerAppResult = api.registerApp(params.appId)
        val installed = api.isWXAppInstalled()
        val supportApi = api.wxAppSupportAPI
        val sdkSupported = supportApi > 0
        val sendReqResult = if (registerAppResult && installed && sdkSupported) {
            val request = WXLaunchMiniProgram.Req().apply {
                userName = params.userName
                if (params.path.isNotEmpty()) {
                    path = params.path
                }
                miniprogramType = params.miniprogramType
            }
            api.sendReq(request)
        } else {
            false
        }
        return WechatMiniProgramLaunchReport(
            appPackageName = appPackageName,
            appSignatureSha256 = appSignatureSha256,
            wechatPackageVisible = wechatPackageVisible,
            registerAppResult = registerAppResult,
            isWXAppInstalled = installed,
            wxAppSupportApi = supportApi,
            isWXAppSupportApi = sdkSupported,
            sendReqResult = sendReqResult
        )
    }

    private fun isPackageVisible(packageManager: PackageManager, packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun appSignatureSha256(packageManager: PackageManager, packageName: String): String? {
        val signatures = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }
        } catch (_: Throwable) {
            null
        }
        return signatures?.firstOrNull()?.sha256()
    }

    private fun Signature.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.joinToString(":") { byte -> "%02X".format(Locale.US, byte) }
    }
}

enum class TransitCodeLaunchResult {
    SUCCESS,
    UNAVAILABLE,
    UNEXPECTED_ERROR
}

enum class TransitCodeLaunchStatus(val displayName: String) {
    REQUEST_SENT("已送出"),
    UNAVAILABLE("不可用"),
    UNSUPPORTED("不支援"),
    REQUEST_REJECTED("請求被拒"),
    CALLBACK("收到回調"),
    ERROR("異常")
}

data class TransitCodeDiagnosticResult(
    val targetTitle: String,
    val providerName: String,
    val status: TransitCodeLaunchStatus,
    val summary: String,
    val details: List<String>,
    val timestampMillis: Long
) {
    fun toLogMessage(): String {
        return buildString {
            append("target=").append(targetTitle)
            append(", provider=").append(providerName)
            append(", status=").append(status.name)
            append(", timestampMillis=").append(timestampMillis)
            append(", summary=").append(summary)
            details.forEach { detail ->
                append(", ").append(detail)
            }
        }
    }

    fun toDisplayLines(maxDetails: Int = 6): List<String> {
        return buildList {
            add("最近診斷")
            add("$targetTitle · ${status.displayName}")
            add(summary)
            add("時間: $timestampMillis")
            details.take(maxDetails).forEach { add(it) }
            if (details.size > maxDetails) {
                add("另有 ${details.size - maxDetails} 條詳情，請查看 logcat。")
            }
        }
    }
}

interface TransitCodeDiagnosticSink {
    fun publish(result: TransitCodeDiagnosticResult)
}

object TransitCodeDiagnostics : TransitCodeDiagnosticSink {
    private val observers = CopyOnWriteArrayList<(TransitCodeDiagnosticResult) -> Unit>()
    @Volatile
    private var latestResult: TransitCodeDiagnosticResult? = null

    override fun publish(result: TransitCodeDiagnosticResult) {
        latestResult = result
        observers.forEach { observer -> observer(result) }
    }

    fun latest(): TransitCodeDiagnosticResult? {
        return latestResult
    }

    fun addObserver(observer: (TransitCodeDiagnosticResult) -> Unit) {
        observers.add(observer)
        latestResult?.let(observer)
    }

    fun removeObserver(observer: (TransitCodeDiagnosticResult) -> Unit) {
        observers.remove(observer)
    }
}

interface TransitCodeDiagnosticLogger {
    fun log(result: TransitCodeDiagnosticResult)
}

object AndroidTransitCodeDiagnosticLogger : TransitCodeDiagnosticLogger {
    override fun log(result: TransitCodeDiagnosticResult) {
        Log.i(TRANSIT_CODE_LOG_TAG, result.toLogMessage())
    }
}

fun publishWechatCallbackDiagnostic(
    errCode: Int?,
    errStr: String?,
    extMsg: String?,
    transaction: String?,
    handled: Boolean,
    clock: () -> Long = { System.currentTimeMillis() },
    logger: TransitCodeDiagnosticLogger = AndroidTransitCodeDiagnosticLogger
) {
    val latestWechat = TransitCodeDiagnostics.latest()?.takeIf {
        it.providerName == TransitCodeProvider.WECHAT_SDK.displayName
    }
    val details = listOf(
        "handleIntent=$handled",
        "errCode=${errCode ?: "(未知)"}",
        "errStr=${errStr ?: "(無)"}",
        "extMsg=${extMsg ?: "(無)"}",
        "transaction=${transaction ?: "(無)"}"
    )
    val diagnostic = TransitCodeDiagnosticResult(
        targetTitle = latestWechat?.targetTitle ?: "微信 SDK 回調",
        providerName = TransitCodeProvider.WECHAT_SDK.displayName,
        status = TransitCodeLaunchStatus.CALLBACK,
        summary = "收到微信 SDK 回調。",
        details = details,
        timestampMillis = clock()
    )
    logger.log(diagnostic)
    TransitCodeDiagnostics.publish(diagnostic)
}
