# 實驗性乘車碼入口歷史記錄

本文件保留 `refine-transit-code-experimental-launcher` 期間的第三方跳轉實驗結論。該實驗入口已在 Google Play 上架前清理中廢棄；當前生產 App 不再包含微信 OpenSDK、`.wxapi.WXEntryActivity`、微信 package visibility、實驗 bottom sheet 或微信診斷代碼。

當前生產 `乘車碼` 入口只使用正式 `TransitCodePaymentLauncher`，按安裝狀態自動嘗試 AlipayHK／支付寶候選鏈：

| 安裝狀態 | 候選鏈 |
| --- | --- |
| 只安裝 AlipayHK | `alipayhk://platformapi/startApp?appId=85200098` → `https://render.alipay.hk/p/s/hkwallet/landing/easygo` |
| 只安裝支付寶 | `alipays://platformapi/startapp?appId=200011235` → `https://render.alipay.com/p/s/i?appId=200011235` |
| 同時安裝 AlipayHK 與支付寶 | AlipayHK scheme → AlipayHK HTTPS → 支付寶 scheme → 支付寶 HTTPS |
| 兩者都未安裝 | AlipayHK HTTPS |

## 當前倉庫狀態

| 項目 | 狀態 |
| --- | --- |
| 微信 OpenSDK dependency | 已移除 |
| `com.tencent.mm` package query | 已移除 |
| `.wxapi.WXEntryActivity` | 已移除 |
| 實驗 bottom sheet | 已移除 |
| 微信 AppID / userName / callback diagnostic | 已移除 |
| 主頁 `乘車碼` 點擊 | 走正式 `TransitCodePaymentLauncher` |
| 當前 instrumentation 覆蓋 | `com.golink.busiscoming.TransitCodePaymentLauncherInstrumentedTest` 驗證主頁點擊不打開實驗面板且不改變結果列表 |

## 歷史支付寶結論

| 入口名稱 | URI / URL | 歷史結論 | 當前用途 |
| --- | --- | --- | --- |
| 支付寶 appId scheme | `alipays://platformapi/startapp?appId=200011235` | 已確認可行 | 正式支付寶 scheme 候選 |
| 支付寶 render HTTPS | `https://render.alipay.com/p/s/i?appId=200011235` | 已確認可行 | 正式支付寶 HTTPS 兜底 |
| 支付寶 saId | `alipays://platformapi/startapp?saId=200011235` | 未納入主備方案 | 不使用 |
| 支付寶 ds 包裝 | `https://ds.alipay.com/?scheme=alipays%3A%2F%2Fplatformapi%2Fstartapp%3FappId%3D200011235` | 未納入主備方案 | 不使用 |

## 歷史微信 SDK 實驗

| 入口名稱 | AppID | userName | path | miniprogramType | 當前狀態 |
| --- | --- | --- | --- | --- | --- |
| 微信 SDK 正式版 | `wx0a914d80e5b75bfa` | `gh_a2de39e7aeb4` | 空 | `MINIPTOGRAM_TYPE_RELEASE` / `0` | 已廢棄，不在當前 App 中提供 |
| 微信 SDK 測試版 | `wx0a914d80e5b75bfa` | `gh_a2de39e7aeb4` | 空 | `MINIPROGRAM_TYPE_TEST` / `1` | 已廢棄，不在當前 App 中提供 |
| 微信 SDK 預覽版 | `wx0a914d80e5b75bfa` | `gh_a2de39e7aeb4` | 空 | `MINIPROGRAM_TYPE_PREVIEW` / `2` | 已廢棄，不在當前 App 中提供 |

微信實驗資料只作歷史排查參考，不應再作為當前 manifest、依賴、測試或生產入口配置依據。
