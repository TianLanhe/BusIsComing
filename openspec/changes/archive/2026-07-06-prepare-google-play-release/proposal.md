## Why

App 準備首次上架 Google Play，目前仍存在幾類開發期與實驗期遺留：正式包名仍是 `com.example.busiscoming`，微信小程序實驗入口和 OpenSDK 配置仍保留在上架包，Citybus mobile 請求仍攜帶靜態瀏覽器 header/cookie，路線查詢 debug log 仍可輸出完整 cURL。這些內容會增加上架前審查、隱私與維護風險，也會讓倉庫內目前規格與即將發布的生產行為不一致。

本變更將 App 收斂為 Google Play 首次上架前的正式形態：使用正式 Android package identity，移除微信小程序實驗能力，保留已確認的 AlipayHK／支付寶正式乘車碼入口，將 Citybus mobile HTML 請求改為最小必要請求，並讓路線請求日誌不再輸出可直接復現的完整 URL 或敏感 header。

## What Changes

- 將 Android `applicationId`、`namespace`、Kotlin source/test package、instrumentation package、腳本中的 app id 完整遷移為 `com.golink.busiscoming`。
- 不做舊開發包 `com.example.busiscoming` 的本機資料遷移；新包名視為新的 Android App 身份。
- 移除微信小程序實驗入口、微信 OpenSDK 依賴、`.wxapi.WXEntryActivity`、`com.tencent.mm` package visibility、微信小程序 AppID/userName/診斷代碼與相關測試。
- 保留正式 `乘車碼` 單按鈕與 AlipayHK／支付寶自動 fallback，不保留或展示實驗 bottom sheet。
- 刪除 Citybus mobile HTML 請求中的靜態 `Cookie`、假 Chrome/macOS `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`X-Requested-With`、`Connection`、`Accept-Language` 等顯式 header；4 個接口均使用無顯式 header 的 GET 請求。
- 調整 `logRouteCurl`：debug build 只輸出脫敏摘要，不輸出完整 cURL、完整 URL、完整 query string、headers 或 cookies。
- 同步目前有效 OpenSpec specs 與 docs，讓乘車碼實驗入口、正式乘車碼入口、Citybus header/cookie 行為與代碼保持一致。
- 不改動 `versionCode` / `versionName`。
- 不改動權限聲明、foreground service 類型、exact alarm 策略或 Google Geocoding API key 外部配置。

## Capabilities

### New Capabilities

- `google-play-release-preparation`: 定義 Google Play 上架前正式 package identity、版本不變、舊開發包資料不遷移、權限不在本變更中調整，以及上架前驗證要求。

### Modified Capabilities

- `citybus-route-query-api`: 路線候選請求不再攜帶瀏覽器 header/cookie，並保留以 10 組有效 live 樣本驗證功能不受影響的要求；路線請求 debug log 改為脫敏摘要。
- `place-search-api`: 地點搜尋請求不再攜帶瀏覽器 header/cookie，並保留 10 組有效 live 樣本驗證。
- `citybus-p2p-stop-map`: `showstops2.php` 停站映射請求不再攜帶瀏覽器 header。
- `route-detail-bottom-sheet`: `getp2pstopinroute.php` 路線詳情請求不再攜帶瀏覽器 header。
- `transit-code-experimental-launcher`: 移除現行實驗面板、微信 SDK 候選入口與診斷能力。
- `transit-code-quick-launcher`: 保留正式 AlipayHK／支付寶候選鏈，明確不依賴微信或實驗入口，manifest 只保留正式錢包 package visibility。

## Impact

- 受影響代碼：
  - `app/build.gradle.kts`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/example/busiscoming/**`
  - `app/src/test/java/com/example/busiscoming/**`
  - `app/src/androidTest/java/com/example/busiscoming/**`
  - `gradle/libs.versions.toml`
  - `scripts/generate-demo-screenshots.sh`
  - `docs/transit-code-experimental-launcher-validation.md`
  - 相關 OpenSpec current specs
- 受影響測試：
  - 包名／manifest／instrumentation 契約測試。
  - Google reverse geocoding request identity 測試中的 package 期望。
  - Citybus header/cookie 契約測試與路線 cURL/log 測試。
  - 乘車碼正式入口與 package visibility 測試。
  - 移除或重寫微信實驗入口、微信 SDK launcher、實驗 bottom sheet 相關測試。
- 兼容性與驗收：
  - 首次上架前包名遷移不保留開發期舊包資料；本地測試時新舊包可並存。
  - Google Geocoding Cloud Console API key Android app restriction 由用戶自行配置，不在本 change 中修改或提交。
  - 權限與版本號保持現狀。
  - 實作後必須運行 `./gradlew build`，並對 4 個 Citybus mobile HTML 接口各跑 10 個有效 live 請求驗證刪除 header/cookie 後業務字段不受影響。
