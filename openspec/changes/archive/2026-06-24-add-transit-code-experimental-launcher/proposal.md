## Why

用戶在搭乘內地公交或地鐵前，可能需要快速打開微信/支付寶「乘車碼」，目前只能離開 App 後手動尋找第三方入口。新增一個實驗性快捷入口，可以在不改動 Citybus 查詢主流程的前提下，驗證多種微信/支付寶 URI/URL 跳轉方式是否可用。

## What Changes

- 在主查詢頁頂部標題區新增低干擾「乘車碼」入口。
- 點擊「乘車碼」後顯示底部彈層，按微信與支付寶分組列出 7 條實驗性跳轉候選。
- 每條候選入口獨立嘗試自身 URI/URL，不做自動 fallback，以便真機驗證時能明確記錄哪條入口可用。
- 跳轉失敗時 App 保持穩定，保留底部彈層並顯示對應 toast。
- 增加 Android 11+ package visibility 配置，預留微信與支付寶 App 可見性檢查能力。
- 不接入微信 OpenSDK、支付寶 SDK，不保存支付偏好，不承諾第一版一定能直達乘車碼頁。

## Capabilities

### New Capabilities
- `transit-code-experimental-launcher`: 從主頁打開實驗性乘車碼跳轉面板，讓用戶可分別嘗試微信與支付寶候選入口，並在跳轉失敗時獲得清楚提示。

### Modified Capabilities
- 無。

## Impact

- 受影響代碼：
  - `app/src/main/res/layout/activity_main.xml`：新增主頁「乘車碼」入口。
  - `app/src/main/java/com/example/busiscoming/ui/main/MainActivity.kt`：綁定入口、顯示底部彈層、處理入口點擊與錯誤提示。
  - 可新增輕量模型/啟動器類，用於集中管理 7 條 URI/URL、`Intent.ACTION_VIEW` 啟動與異常轉換。
  - `app/src/main/AndroidManifest.xml`：合併微信與支付寶 package visibility 查詢。
- 不影響 Citybus mobile、DATA.GOV.HK ETA、路線管理、本機 SQLite schema、通知欄監控或既有路線結果排序與刷新邏輯。
- UI 應遵循 `docs/ui-style-guide.md` 與既有 `app-ui-style-system` 風格；主頁入口需保持低干擾，不破壞現有查詢第一屏。
- 兼容性風險集中在第三方 App 跳轉：
  - 微信 `jumpWxa` 屬非官方候選；微信明文 Scheme 需要目標小程序允許對應能力，且 `pages/index/index` 只是基於官方小程序首頁慣例的實驗 path。
  - 支付寶 `appId`、`saId`、H5 render 與 `ds.alipay.com` 包裝入口均需真機實測。
- 測試與驗證：
  - 自動化測試覆蓋 URI/URL 構造、支付寶 ds 包裝 URL encode、底部彈層展示與啟動失敗處理。
  - 外部 App 實際跳轉結果需人工真機驗證，並記錄入口名稱、設備/Android 版本、微信/支付寶版本、結果、到達頁面與備註。
