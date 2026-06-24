## Why

第一輪乘車碼實驗已確認微信 3 條 scheme 候選不可用，而支付寶 `appId` scheme 與 render HTTPS 已可到達乘車碼入口；現在需要把實驗入口收斂到下一組待驗證方案，避免繼續測試已判定無效或已判定可用的入口。

本次變更要用微信 Android OpenSDK 拉起騰訊乘車碼小程序並輸出可診斷原因，同時加入 AlipayHK 的 scheme 與 HTTPS 候選，為後續正式「單一乘車碼入口 + 自動兜底」策略提供實測依據。

## What Changes

- 將實驗面板中的微信入口由 3 條 URI scheme 改為微信 Android OpenSDK 小程序拉起實驗。
- 使用微信移動應用 AppID `wx0a914d80e5b75bfa`、小程序原始 ID `gh_a2de39e7aeb4`、首輪空 path，分別提供正式版、測試版、預覽版 3 個 `miniprogramType` 實驗入口。
- 接入微信 OpenSDK 依賴、App 註冊與回調入口，並在每次微信 SDK 實驗後於 logcat 和實驗面板記錄診斷資訊。
- 從實驗面板移除支付寶 4 條候選入口；保留已驗證結論供正式改造時使用：支付寶主備候選為 `alipays://platformapi/startapp?appId=200011235` 與 `https://render.alipay.com/p/s/i?appId=200011235`。
- 新增 AlipayHK 實驗分組，提供 `alipayhk://platformapi/startApp?appId=85200098` 與 `https://render.alipay.hk/p/s/hkwallet/landing/easygo` 兩條候選入口。
- 調整底部彈層說明與分組，只展示「微信 SDK」與「AlipayHK」兩組，並顯示最近一次實驗診斷結果。
- 本次只修改實驗入口，不實作正式自動兜底策略、不保存用戶支付偏好、不改動 Citybus 查詢流程。

## Capabilities

### New Capabilities

- `transit-code-experimental-launcher`: 更新進行中的乘車碼實驗入口契約，將候選集合改為微信 OpenSDK 3 種小程序類型與 AlipayHK 2 種跳轉方式，並新增可觀察診斷輸出。

### Modified Capabilities

- 無；`transit-code-experimental-launcher` 尚未 archive 到 `openspec/specs/`，本 change 以同名 capability 提供更新後的完整實驗契約。

## Impact

- 代碼：
  - `app/build.gradle.kts`、`gradle/libs.versions.toml`：新增微信 OpenSDK 依賴，優先使用 Maven Central 最新可用版本 `com.tencent.mm.opensdk:wechat-sdk-android:6.8.34`。
  - `app/src/main/AndroidManifest.xml`：保留微信 package visibility，新增 AlipayHK package visibility，並註冊微信 SDK 回調 Activity。
  - `app/src/main/java/.../data/model/TransitCodeLaunchTarget.kt`：重建實驗候選模型，使同一面板可承載 SDK 類入口與 URI/URL 類入口。
  - `app/src/main/java/.../ui/main/TransitCodeLauncher.kt`、`TransitCodeBottomSheet.kt`：拆分微信 SDK 與 AlipayHK URI/HTTPS 啟動路徑，集中產出診斷結果。
  - `app/src/main/java/.../wxapi/WXEntryActivity.kt`：接收微信 SDK 回調並記錄 `errCode`、`errStr`、`extMsg` 等結果。
  - `docs/transit-code-experimental-launcher-validation.md`：更新真機驗證記錄模板與已確認支付寶結論。
- 外部依賴與平台：
  - 依賴微信 OpenSDK；實際可用性受微信開放平台移動應用 AppID、Android 包名、簽名、Universal Link/能力配置，以及目標小程序可拉起策略影響。
  - AlipayHK 兩條候選仍需真機逐條驗證；HTTPS 中轉可能依賴瀏覽器、系統 App Links 或 AlipayHK 當前版本行為。
- 測試：
  - 單元測試覆蓋候選清單、微信 `miniprogramType` 映射、AlipayHK URI/URL 與診斷結果格式。
  - Instrumentation/UI 測試覆蓋底部彈層分組、入口數量與最近診斷展示。
  - 最終仍需真機驗證微信 SDK 三種 `miniprogramType` 和 AlipayHK 兩條入口是否到達乘車碼頁。
