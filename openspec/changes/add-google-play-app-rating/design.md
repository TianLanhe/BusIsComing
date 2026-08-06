## Context

`SettingsFragment` 的「應用評分」目前只顯示 `unsupported_rate_app`。專案已有更新功能使用的 `AndroidPlayPackageProbe` 與 `AppUpdateExternalActions.openPlayListing()`：前者只回傳布林值，會把 Play 停用、缺失與 Intent 不可解析合併為同一結果；後者在 Play market Intent 失敗後會再嘗試無 package 限定的 HTTPS 商品頁。這兩項既有行為都不足以實現本 change 已確認的評分契約。

Google Play 商品頁只代表把控制權交給 Play；App 無法知道使用者是否看到評分介面或提交評分。因此功能必須以「打開商品詳情頁」為成功邊界，不能顯示已評分、不能記錄評分完成，也不能把 BusIsComing 的初始安裝來源當成能否評分的判斷條件。

`add-app-update-check` 正在修改 `app-settings-support` 的同一個「暫不支援入口」Requirement，且其 Play 更新行為仍需要目前的網站 fallback。為避免評分契約意外改變更新流程，本 change 只共享低層 Play package 探測資訊，不直接改寫更新 action；實作及歸檔以該 change 先完成同步／歸檔為前置條件。

## Goals / Non-Goals

**Goals:**

- 從設定頁以一次點擊打開官方 Google Play App 內的 BusIsComing 商品詳情頁。
- 明確區分 Play 可用、停用、缺失與不可解析，為每種狀態提供可恢復且本地化的操作。
- 讓 package 探測、Intent 建立與啟動結果可由純狀態及注入點測試，避免判斷散落在 Fragment。
- 保留既有更新檢查、查詢、行程資料及監控行為。

**Non-Goals:**

- 不使用 Play In-App Review，也不判斷其 quota、介面展示或提交結果。
- 不追蹤評分完成、不在返回 App 後顯示感謝或自動再次打開商品頁。
- 不以瀏覽器商品頁、其他商店或第三方 APK 作為 Google Play 缺失／失敗時的 fallback。
- 不協助安裝 Google Play APK，不申請安裝或全 package 查詢權限。

## Decisions

### 1. 用結構化可用性分類取代評分流程中的布林探測

新增 `PlayStoreAvailabilityDetector`（或等價可測元件），輸出以下互斥狀態：

- `AVAILABLE`：`com.android.vending` 已安裝且啟用，並可解析限定該 package 的 BusIsComing 商品 Intent。
- `DISABLED`：package 存在，但目前停用。
- `MISSING`：`PackageManager` 明確找不到 package。
- `UNUSABLE`：package 已啟用但商品 Intent 無法解析，或探測因可捕獲的 package／security runtime 問題無法建立可信可用結果。

現有 `PlayPackageProbe.isPlayAvailable()` 可改為對結構化 detector 的相容投影，讓更新功能繼續取得布林結果；評分流程直接使用完整狀態。選擇共享 detector 而不是在 `SettingsFragment` 重新查 package，可避免更新與評分對「可用 Play」形成兩套判斷。被否決方案是直接復用現有布林 probe，因為它無法提供已停用與未安裝的不同恢復路徑。

### 2. 評分 action 只啟動 package 限定的官方商品 Intent

新增 `GooglePlayRatingNavigator`，建立 `ACTION_VIEW`、BusIsComing 官方 HTTPS 商品 URL、`CATEGORY_BROWSABLE` 並 `setPackage("com.android.vending")` 的 Intent；商品 ID 取目前正式 `applicationId`，與更新功能的 Play link 常量保持單一來源。

不沿用 `AppUpdateExternalActions.openPlayListing()` 的無 package HTTPS fallback，因為該 fallback 是更新流程既有的恢復策略，與本 change「始終在 Google Play App 打開」的產品決策相反。亦不使用 In-App Review：它不能可靠回報介面是否展示或使用者是否提交，且不適合作為明確「應用評分」按鈕的可驗證結果。

### 3. 由狀態矩陣決定唯一後續操作

`SettingsFragment` 只請求 action、按結果顯示 Material 對話框或錯誤，不直接執行 package 判斷：

| 狀態 | 提示 | 主要操作 | 取消／返回 |
|---|---|---|---|
| `AVAILABLE` | 不先顯示提示 | 打開 Play 商品詳情 | 返回設定頁，不自動續辦 |
| `DISABLED` | Google Play 已停用 | 「前往啟用」打開 `com.android.vending` 系統應用詳情 | 留在設定頁 |
| `MISSING` | 裝置未找到 Google Play，並說明只提供官方協助 | 「查看安裝說明」打開 Google 官方說明 | 留在設定頁 |
| `UNUSABLE` | 目前無法使用 Google Play | 「應用設定」打開 BusIsComing 系統應用詳情 | 留在設定頁 |
| 啟動 action 失敗 | 顯示目前語言的無法開啟提示 | 無自動 fallback | 留在設定頁並可再次點擊 |

官方說明固定使用 `https://support.google.com/googleplay/answer/190860`，依 App 語言附加 `hl=zh-HK`、`hl=zh-CN` 或 `hl=en`；該 Google Help 頁本身說明 Play 通常預載、可能被隱藏／停用，以及仍缺失時應聯絡製造商或電訊商，因此不把它誤寫成可下載 APK 的頁面。系統應用詳情使用 `ACTION_APPLICATION_DETAILS_SETTINGS` 與明確 package URI；若恢復 Intent 本身不可解析或啟動失敗，顯示通用無法開啟提示，不再串接其他 destination。

### 4. 返回 App 不保存 pending 自動續辦

評分流程不註冊「返回後自動打開 Play」的 Activity Result pending 狀態。從 Play、系統設定或官方說明返回時只重新呈現設定頁；即使 detector 已變成 `AVAILABLE`，仍須由使用者再次點擊「應用評分」。這避免從外部頁返回即被第二次跳轉，也讓每次外部導航都由明確手勢觸發。

### 5. 本地化、可存取性與驗證分層

新增繁體、獨立簡體與自然英文資源，涵蓋三種不可用對話框、操作按鈕及啟動失敗。對話框標題、正文、按鈕與設定列須能由 TalkBack 按自然順序讀取；不使用品牌圖示冒充狀態，也不宣稱使用者已完成評分。

純 JVM 測試使用 fake package reader／resolver 覆蓋四種 detector 狀態，使用注入 starter 覆蓋所有 Intent 目標與例外。Fragment instrumentation 驗證每個狀態只顯示對應分支、返回不自動續辦、旋轉不重複啟動，並檢查三語、明暗主題與 TalkBack。真實 Play 驗證只確認商品詳情頁可打開，不能把人工觀察推廣為「評分已提交」。

## Risks / Trade-offs

- [部分 OEM 對停用 package 或 Intent resolver 回報不一致] → detector 將不可信例外收斂為 `UNUSABLE`，所有啟動再捕獲 `ActivityNotFoundException`、`SecurityException` 及已知可恢復 runtime 例外。
- [Google Help URL 或語言參數日後變更] → URL 集中於單一 link provider，測試只驗證官方 HTTPS host、文章 ID 與語言 mapping，發佈前以只讀方式核對可達性。
- [共享 Play detector 可能影響更新渠道判斷] → 保留 `isPlayAvailable()` 相容介面與既有語義，新增狀態只供評分使用；運行更新功能既有測試，禁止順手改變網站 fallback。
- [使用者可能期待點擊後直接看到評分框] → 商品頁是唯一可保證的官方 destination；文案只承諾「前往 Google Play 評分」，不承諾 Play 介面內的具體控件。

## Migration Plan

1. 先完成、同步並歸檔 `add-app-update-check`，確認其 `app-settings-support` delta 已成為主規格。
2. 擴展 Play 可用性探測並保留更新功能的相容投影，再加入獨立評分 navigator 與 UI 狀態矩陣。
3. 移除設定列的 `unsupported_rate_app` 行為，但不刪除或改寫檢查更新流程。
4. 若需回滾，只恢復評分列的暫不支援 action；新 detector 的相容投影可保留，不涉及使用者資料遷移。

## Open Questions

無；商品 destination、不可用矩陣、返回行為與非目標均已確認。
