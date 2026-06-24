## 1. 候選模型與依賴

- [x] 1.1 在 Gradle version catalog 與 App 模組中新增微信 OpenSDK 依賴 `com.tencent.mm.opensdk:wechat-sdk-android:6.8.34`，確認不引入額外 Kotlin Android 插件配置。
- [x] 1.2 重構 `TransitCodeLaunchTarget`，支援微信 SDK 小程序與 URI/URL 兩種啟動類型，保留 provider、標題、說明和可測試的參數欄位。
- [x] 1.3 將候選清單更新為微信 SDK 正式版、測試版、預覽版與 AlipayHK Scheme、AlipayHK HTTPS 共 5 條，移除上一輪微信 scheme 與支付寶實驗候選。
- [x] 1.4 新增或更新失敗提示文案，覆蓋微信 SDK、AlipayHK 與通用非預期錯誤，文案指向診斷結果。

## 2. 微信 SDK 啟動與回調

- [x] 2.1 在 `AndroidManifest.xml` 保留 `com.tencent.mm` 查詢聲明，新增 AlipayHK package query，並註冊 `.wxapi.WXEntryActivity`。
- [x] 2.2 實作微信 SDK 啟動路徑，完成 `IWXAPI` 建立、`registerApp`、安裝與 SDK 支援檢查、`WXLaunchMiniProgram.Req` 建立與 `sendReq` 結果回傳。
- [x] 2.3 實作 `.wxapi.WXEntryActivity`，將微信 intent 交給 SDK `handleIntent`，並記錄 `errCode`、`errStr`、`extMsg` 與 transaction 等可取得欄位。
- [x] 2.4 確保微信 SDK 三個入口分別使用 AppID `wx0a914d80e5b75bfa`、`userName` `gh_a2de39e7aeb4`、空 path，以及 `miniprogramType` 0、1、2。

## 3. AlipayHK 啟動與診斷

- [x] 3.1 實作 AlipayHK `ACTION_VIEW` 啟動路徑，分別處理 `alipayhk://platformapi/startApp?appId=85200098` 與 `https://render.alipay.hk/p/s/hkwallet/landing/easygo`。
- [x] 3.2 在啟動前後記錄 resolve 結果、`startActivity` 結果、`ActivityNotFoundException`、`SecurityException` 與其他異常 class/message。
- [x] 3.3 確保每次點擊只嘗試當前候選，不自動 fallback 到另一條 AlipayHK、微信 SDK 或支付寶入口。

## 4. 實驗面板 UI

- [x] 4.1 更新 `TransitCodeBottomSheet` 標題說明，將分組改為「微信 SDK」與「AlipayHK」，並移除「支付寶」分組。
- [x] 4.2 在底部彈層加入最近一次診斷摘要區，能顯示入口名稱、狀態、關鍵參數與錯誤摘要。
- [x] 4.3 在用戶返回 App 或微信 SDK 回調更新後刷新最近診斷摘要，避免需要重開面板才能看到結果。
- [x] 4.4 保持主頁「乘車碼」入口、彈層開關、滾動、觸控目標和既有巴士查詢狀態隔離。

## 5. 測試與文件

- [x] 5.1 更新候選清單單元測試，驗證 5 條入口、微信 `miniprogramType` 0/1/2、AppID、`userName`、空 path 與 AlipayHK URI/URL。
- [x] 5.2 新增或更新啟動器單元測試，覆蓋微信 SDK 成功送出、未安裝/不支援、`registerApp` 失敗、`sendReq` 失敗、AlipayHK resolve/start 成功與異常診斷。
- [x] 5.3 更新 instrumentation/UI 測試，驗證點擊主頁「乘車碼」可看到微信 SDK 3 條、AlipayHK 2 條、最近診斷區，且不再顯示支付寶入口。
- [x] 5.4 更新 `docs/transit-code-experimental-launcher-validation.md`，記錄支付寶已確認可行的 `appId` scheme 與 render HTTPS，並新增微信 SDK / AlipayHK 真機驗證表。
- [x] 5.5 執行 `./gradlew build`，修正編譯、單元測試、lint、assemble 或 instrumentation 編譯問題。
- [x] 5.6 若有可用 Android 真機，逐條驗證微信 SDK 正式版、測試版、預覽版與 AlipayHK Scheme、HTTPS，記錄設備、系統版本、App 版本、到達頁與診斷摘要；無設備時明確記錄未完成人工驗證。
