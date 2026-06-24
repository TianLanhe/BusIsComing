# 實驗性乘車碼入口真機驗證記錄

本記錄用於 `add-transit-code-experimental-launcher`。每條入口需在可安裝對應 App 的 Android 真機上單獨點擊驗證；App 第一版不自動 fallback，也不判斷是否已到達乘車碼頁。

## 驗證環境

| 項目 | 記錄 |
| --- | --- |
| 設備型號 | Pixel_8 AVD |
| Android 版本 | Android 17 / SDK 37 |
| BusIsComing 版本 / commit | 待真機驗證前填寫 |
| 微信版本 | 模擬器未安裝，待真機驗證 |
| 支付寶版本 | 模擬器未安裝，待真機驗證 |
| 驗證日期 | 2026-06-24（自動化與模擬器基礎驗證）；真機驗證待補 |
| 驗證人 | Codex（自動化與模擬器基礎驗證）；真機驗證待補 |

## 微信候選入口

| 入口名稱 | URI | 結果 | 到達頁面 | 備註 |
| --- | --- | --- | --- | --- |
| 微信 jumpWxa | `weixin://app/wxbe05102357855fc7/jumpWxa/?userName=gh_a2de39e7aeb4` | 待真機驗證 | 待真機驗證 | 模擬器未安裝微信 |
| 微信明文 Scheme | `weixin://dl/business/?appid=wxbe05102357855fc7&env_version=release` | 待真機驗證 | 待真機驗證 | 模擬器未安裝微信 |
| 微信首頁 path | `weixin://dl/business/?appid=wxbe05102357855fc7&path=pages/index/index&env_version=release` | 待真機驗證 | 待真機驗證 | 模擬器未安裝微信；`pages/index/index` 尚未確認為騰訊乘車碼 path |

## 支付寶候選入口

| 入口名稱 | URI / URL | 結果 | 到達頁面 | 備註 |
| --- | --- | --- | --- | --- |
| 支付寶 appId | `alipays://platformapi/startapp?appId=200011235` | 待真機驗證 | 待真機驗證 | 模擬器未安裝支付寶 |
| 支付寶 saId | `alipays://platformapi/startapp?saId=200011235` | 待真機驗證 | 待真機驗證 | 模擬器未安裝支付寶 |
| 支付寶 H5 render | `https://render.alipay.com/p/s/i?appId=200011235` | 待真機驗證 | 待真機驗證 | 模擬器未安裝支付寶 |
| 支付寶 ds 包裝 | `https://ds.alipay.com/?scheme=alipays%3A%2F%2Fplatformapi%2Fstartapp%3FappId%3D200011235` | 待真機驗證 | 待真機驗證 | 模擬器未安裝支付寶 |

## 佈局檢查

| 場景 | 結果 | 備註 |
| --- | --- | --- |
| 主頁頂部「乘車碼」入口在常見手機寬度下不遮擋「巴士查詢」和「管理路線」 | 待真機補充 | 已由 XML 約束與模擬器 UI 測試覆蓋入口存在；多寬度仍待人工檢查 |
| 底部彈層可完整滾動查看 7 條入口 | 已通過自動化 | `TransitCodeBottomSheetInstrumentedTest#bottomSheetListsThreeWechatAndFourAlipayTargets` |
| 系統字體放大後標題、分組與入口說明不互相遮擋 | 待真機補充 |  |
| TalkBack / 無障礙讀取可聚焦主頁入口與各候選入口 | 待真機補充 |  |
