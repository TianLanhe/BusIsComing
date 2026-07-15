## Context

目前工程仍以 `com.example.busiscoming` 作為 `applicationId`、Gradle `namespace`、Kotlin package、測試 package 與腳本 app id。Google reverse geocoding resolver 會根據運行時 `context.packageName` 和簽名 SHA-1 生成 `X-Android-Package` / `X-Android-Cert` header，因此包名完整遷移後，代碼與測試需要同步到新身份；Google Cloud Console API key restriction 由用戶自行配置，不寫入倉庫。

乘車碼功能已分成兩套：正式入口 `TransitCodePaymentLauncher` 只使用 AlipayHK／支付寶候選鏈；實驗入口 `TransitCodeBottomSheet`、`TransitCodeLauncher`、微信 OpenSDK client 與 `.wxapi.WXEntryActivity` 仍保留在倉庫中，但不再是生產入口需要的能力。當前 `transit-code-experimental-launcher` spec 仍要求保留微信 SDK 三個候選入口、診斷面板和 `com.tencent.mm` package visibility，這和上架前清理方向衝突。

Citybus mobile HTML 請求目前帶有從瀏覽器 cURL 複製來的靜態 header/cookie。已完成的 live 驗證顯示，在 `ppsearch_p3.php`、`bsearch_p3.php`、`showstops2.php`、`getp2pstopinroute.php` 四個接口上移除顯式 header/cookie 不影響狀態碼、解析標記或 App 使用的業務字段。`ppsearch_p3.php` 完整 body hash 會因 `shareinfo` 動態時間戳自然不同，但路線卡片語義與 `showroutep2p(...)` 參數一致。

`logRouteCurl` 目前輸出完整 cURL；即使刪除 cookie，完整 URL 仍包含起終點坐標與查詢時間。Google Play 上架前應避免在 debug log 中輸出可直接復現用戶行程的完整查詢。

## Goals / Non-Goals

**Goals:**

- 完整遷移 Android package identity 到 `com.golink.busiscoming`，覆蓋 Gradle、source/test package、目錄、腳本和測試期望。
- 移除微信小程序實驗能力與配置，保留正式 AlipayHK／支付寶乘車碼入口。
- 刪除 Citybus mobile HTML 請求中的所有顯式瀏覽器 header/cookie，並以單測和 live 驗證保護功能不受影響。
- 將路線請求日誌收斂為 debug-only 脫敏摘要。
- 同步目前有效 specs 與 docs，讓倉庫知識不再描述已移除的微信實驗能力。

**Non-Goals:**

- 不遷移 `com.example.busiscoming` 舊開發包的本機 SQLite 或 SharedPreferences 資料。
- 不修改 `versionCode = 1` 或 `versionName = "1.0"`。
- 不修改權限聲明、`SCHEDULE_EXACT_ALARM`、foreground service 類型、wake lock 或通知監控調度策略。
- 不提交或改動 Google Geocoding API key，也不把 Cloud Console Android app restriction 作為本 change 任務。
- 不重寫 archived OpenSpec change 歷史；只同步目前有效 specs、docs 與仍被測試或腳本使用的內容。
- 不改變正式乘車碼 AlipayHK／支付寶候選 URI、URL、fallback 順序或失敗提示。

## Decisions

### 1. 包名做完整遷移，而不是只改 `applicationId`

`applicationId` 是安裝與 Google Play 識別身份；`namespace` 決定生成的 `R` / `BuildConfig` package；Kotlin package、測試 package、instrumentation runner 參數、腳本 app id 也共同構成可維護性邊界。首次上架前應一次性完整遷移到 `com.golink.busiscoming`，避免長期混用 `com.example.busiscoming`。

實作時應同步：

- `app/build.gradle.kts` 中的 `namespace` 與 `applicationId`。
- `app/src/main/java`、`app/src/test/java`、`app/src/androidTest/java` 下 package 宣告、import 與目錄。
- manifest 相對組件名會跟隨新 package；仍需檢查產物 manifest。
- scripts、instrumentation class name、`run-as` / `dumpsys` / `pm grant` 等仍被倉庫腳本使用的舊 package 字串。
- 測試中 Google reverse geocoding request identity 的 package 期望。

替代方案是只改 `applicationId`。這能讓 Play 包名變更較小，但會造成運行時 package、生成類 package、源碼 package、測試與腳本長期分裂；上架前不是值得保留的複雜度。

### 2. 不做舊開發包資料遷移

從 `com.example.busiscoming` 到 `com.golink.busiscoming` 對 Android 而言是新 App 身份。舊 debug 包資料不會自動出現在新包下，也不應為開發期資料引入導入／導出機制。

替代方案是設計資料遷移橋接。這會新增不必要的資料暴露和上架風險，且首次上架前沒有生產用戶需要保留舊包資料。

### 3. 刪除整個微信實驗能力，保留正式 AlipayHK／支付寶入口

本變更應移除微信 OpenSDK 依賴、微信 package visibility、`.wxapi.WXEntryActivity`、微信小程序參數、微信診斷 logger，以及不再需要的實驗 bottom sheet。正式 `乘車碼` 入口繼續使用 `TransitCodePaymentLauncher` 和 `TransitCodePaymentTargets` 的 AlipayHK／支付寶 fallback。

保留實驗 bottom sheet 但只刪微信分組的替代方案不採用。當前用戶目標是 Play 上架前收斂生產包，保留不可從生產入口到達的實驗 UI 會讓規格、測試和文檔繼續背負實驗心智。

### 4. Citybus mobile HTML 請求使用無顯式 header 的 GET

4 個 Citybus mobile HTML 接口都應移除顯式 header/cookie：

- `ppsearch_p3.php`
- `bsearch_p3.php`
- `showstops2.php`
- `getp2pstopinroute.php`

既有 live 驗證已覆蓋每接口 10 個有效 paired samples。實作後仍需重跑同等驗證，以確認代碼改動後真實路徑沒有漏設 header 或破壞解析。`ppsearch_p3.php` 驗收使用語義簽名而不是完整 body hash，因為響應包含上游動態 `shareinfo` 時間戳。

保留 `Accept` 或 `User-Agent` 的替代方案暫不採用。已驗證上游不需要這些 header；最小請求更能避免靜態瀏覽器偽裝和 cookie 風險。若未來 Citybus 增加防盜鏈或 user-agent 限制，再以最小可證明必要 header 重新評估，不回退到靜態 cookies。

### 5. `logRouteCurl` 改為 debug-only 脫敏摘要

路線查詢日誌不應輸出完整 cURL、完整 URL、完整 query string、headers 或 cookies。保留 debug 摘要只用於定位查詢模式與請求是否被觸發，例如 endpoint、searchMode、header count、坐標是否存在或粗粒度坐標。release build 不應輸出該日誌。

完全刪除日誌也可行，但 debug-only 脫敏摘要仍有助於排查查詢流程；只要測試明確禁止完整 URL、`slat/slon/elat/elon` 原始值、`Cookie` 和 `curl`，隱私風險可控。

### 6. 版本、權限與外部 API key 不在本變更內改動

`versionCode = 1`、`versionName = "1.0"` 適合作為首次上架版本，不需要因包名與清理任務調整。權限聲明已不包含電池最佳化豁免，本次不修改 `SCHEDULE_EXACT_ALARM`、foreground service、wake lock 或定位權限。Google Geocoding API key 的 Android app restriction 由用戶在 Google Cloud Console 另行配置。

實作任務應避免把這些非目標變成順手重構；驗證時只檢查沒有被誤改。

## Risks / Trade-offs

- [Risk] 全量 package 遷移容易漏掉測試、腳本或 generated import。→ Mitigation：使用全倉庫 `rg "com.example.busiscoming"` 作為任務驗收，區分歷史 archive 可保留與當前代碼/腳本不可保留。
- [Risk] manifest 相對 Activity 名稱在包名變更後指向錯誤路徑。→ Mitigation：跑 `./gradlew build`，並用 APK/manifest 或 instrumentation 驗證運行時 package 與 launcher Activity。
- [Risk] 移除微信實驗代碼時誤刪正式乘車碼 fallback。→ Mitigation：保留並加強 `TransitCodePaymentLauncherTest`，確認 AlipayHK／支付寶候選鏈、package visibility 和主頁正式入口仍可用。
- [Risk] Citybus 上游將來改變 header 要求。→ Mitigation：本次保留 10 組 live 驗證結果；若未來失效，只按失效接口加入最小必要 header，不恢復靜態 cookies。
- [Risk] debug log 脫敏不足仍泄露坐標。→ Mitigation：測試斷言不包含完整 URL、原始 `slat/slon/elat/elon` query、`Cookie`、`User-Agent` 或 `curl`。
- [Risk] 文檔或 current spec 保留微信實驗敘述，讓後續 agent 誤判。→ Mitigation：同步 current specs 與 `docs/transit-code-experimental-launcher-validation.md`，archive 歷史不批量重寫。

## Migration Plan

1. 先更新 OpenSpec specs/tasks 中的驗收口徑，確保實作範圍明確。
2. 完整遷移 package identity 與目錄，跑編譯修正 import、manifest、測試和腳本引用。
3. 移除微信實驗能力與依賴，保留正式乘車碼 payment launcher。
4. 刪除 Citybus mobile HTML 請求顯式 header/cookie，調整單測。
5. 改造 `logRouteCurl` 為 debug-only 脫敏摘要並補測試。
6. 同步 current specs、docs 和仍被使用的腳本/測試。
7. 運行 `./gradlew build`。
8. 使用真實網路對 4 個 Citybus mobile HTML 接口各跑 10 個有效請求，確認刪除 header/cookie 後業務字段不受影響。

## Open Questions

無。已確認：

- 包名做完整遷移。
- 刪除微信實驗能力與配置，保留正式 AlipayHK／支付寶乘車碼入口。
- 同步 current specs/docs，不批量改寫 archive 歷史。
- `logRouteCurl` 保留 debug 脫敏摘要。
- Citybus live 驗證每接口 10 個有效請求，作為本次實作後驗收，不提交外網驗證腳本。
- 權限保持現狀，不納入任務。
- Google Geocoding API key 外部配置由用戶自行處理。
- 版本號保持 `1.0` / `1`。
- 不做舊開發包資料遷移。
