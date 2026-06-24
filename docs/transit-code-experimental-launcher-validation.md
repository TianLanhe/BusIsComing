# 實驗性乘車碼入口真機驗證記錄

本記錄用於 `refine-transit-code-experimental-launcher`。每條入口需在可安裝對應 App 的 Android 真機上單獨點擊驗證；本輪仍為實驗面板，不自動 fallback，也不由 App 判斷是否已到達乘車碼頁。

## 已確認支付寶結論

支付寶實驗入口已從面板移除，正式改造時暫定使用下列主備候選：

| 入口名稱 | URI / URL | 結論 | 備註 |
| --- | --- | --- | --- |
| 支付寶 appId scheme | `alipays://platformapi/startapp?appId=200011235` | 已確認可行 | 後續正式單一入口的支付寶 scheme 方案 |
| 支付寶 render HTTPS | `https://render.alipay.com/p/s/i?appId=200011235` | 已確認可行 | 後續正式單一入口的支付寶 HTTPS 兜底方案 |
| 支付寶 saId | `alipays://platformapi/startapp?saId=200011235` | 本輪不再測試 | 不納入目前已確認主備方案 |
| 支付寶 ds 包裝 | `https://ds.alipay.com/?scheme=alipays%3A%2F%2Fplatformapi%2Fstartapp%3FappId%3D200011235` | 本輪不再測試 | 不納入目前已確認主備方案 |

## 驗證環境

| 項目 | 記錄 |
| --- | --- |
| 設備型號 | 待真機驗證前填寫 |
| Android 版本 | 待真機驗證前填寫 |
| BusIsComing 版本 / commit | 待真機驗證前填寫 |
| 微信版本 | 待真機驗證前填寫 |
| AlipayHK 版本 | 待真機驗證前填寫 |
| 驗證日期 | 待真機驗證前填寫 |
| 驗證人 | 待真機驗證前填寫 |

## 本次自動化驗證記錄

| 項目 | 結果 |
| --- | --- |
| 自動化設備 | `sdk_gphone16k_x86_64` AVD |
| Android 版本 | Android 17 / SDK 37 |
| 外部 App 安裝狀態 | 未安裝 `com.tencent.mm`、`hk.alipay.wallet`、`com.eg.android.AlipayGphone` |
| Build 驗證 | `./gradlew build` 通過 |
| 乘車碼 UI 驗證 | `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.busiscoming.TransitCodeBottomSheetInstrumentedTest` 通過 |
| 真機外部 App 跳轉 | 未完成；當前只連接 AVD，無可安裝微信 / AlipayHK 的 Android 真機 |

## 微信 SDK 候選入口

| 入口名稱 | AppID | userName | path | miniprogramType | 結果 | 到達頁面 | 診斷摘要 | 備註 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 微信 SDK 正式版 | `wx0a914d80e5b75bfa` | `gh_a2de39e7aeb4` | 空 | `MINIPTOGRAM_TYPE_RELEASE` / `0` | 待真機驗證 | 待真機驗證 | 待補 | 若失敗，記錄 `registerApp`、`sendReq`、`errCode`、`errStr`、`extMsg` |
| 微信 SDK 測試版 | `wx0a914d80e5b75bfa` | `gh_a2de39e7aeb4` | 空 | `MINIPROGRAM_TYPE_TEST` / `1` | 待真機驗證 | 待真機驗證 | 待補 | 若失敗，記錄 `registerApp`、`sendReq`、`errCode`、`errStr`、`extMsg` |
| 微信 SDK 預覽版 | `wx0a914d80e5b75bfa` | `gh_a2de39e7aeb4` | 空 | `MINIPROGRAM_TYPE_PREVIEW` / `2` | 待真機驗證 | 待真機驗證 | 待補 | 若失敗，記錄 `registerApp`、`sendReq`、`errCode`、`errStr`、`extMsg` |

## AlipayHK 候選入口

| 入口名稱 | URI / URL | 結果 | 到達頁面 | 診斷摘要 | 備註 |
| --- | --- | --- | --- | --- | --- |
| AlipayHK Scheme | `alipayhk://platformapi/startApp?appId=85200098` | 待真機驗證 | 待真機驗證 | 待補 | 記錄是否直接打開 AlipayHK EasyGo / 乘車碼 |
| AlipayHK HTTPS | `https://render.alipay.hk/p/s/hkwallet/landing/easygo` | 待真機驗證 | 待真機驗證 | 待補 | 記錄是否經瀏覽器或系統中轉，再進入 AlipayHK |

## 佈局與診斷檢查

| 場景 | 結果 | 備註 |
| --- | --- | --- |
| 主頁頂部「乘車碼」入口在常見手機寬度下不遮擋「巴士查詢」和「管理路線」 | 待真機補充 | 已由 XML 約束與 UI 測試覆蓋入口存在；多寬度仍待人工檢查 |
| 底部彈層可完整滾動查看微信 SDK 3 條與 AlipayHK 2 條入口 | 已由自動化覆蓋 | `TransitCodeBottomSheetInstrumentedTest#bottomSheetListsWechatSdkAndAlipayHkTargets` |
| 底部彈層顯示最近診斷區 | 已由自動化覆蓋 | 點擊入口後應更新入口名稱、狀態、關鍵參數與錯誤摘要 |
| 系統字體放大後標題、分組、入口說明與診斷摘要不互相遮擋 | 待真機補充 |  |
| TalkBack / 無障礙讀取可聚焦主頁入口、各候選入口與診斷摘要 | 待真機補充 |  |
