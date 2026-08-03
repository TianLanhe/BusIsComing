## Context

本 change 已把設定頁「檢查更新」接入更新領域模型、網站 metadata source、Play In-App Updates 與本機狀態；`AppSupportActions` 依 App 實際語言組合官方網站路徑並安全開啟 URL。這次修訂聚焦於讓 Android 契約與已部署網站的真實響應一致，並補強長時間門檻的自動化驗證。

App 將同時由 Google Play 與官方網站提供下載，但兩個渠道不能用同一個全局版本判斷取代彼此：Play 會按帳號擁有權、軌道、地區、裝置與灰度判斷可用版本；網站只提供一個全局 APK。另一方面，只要 application ID、簽名及 versionCode 相容，Play app signing key 簽署的 universal APK 可作網站正式包。

設計確認時，網站候選 APK 的 SHA-256 簽名憑證為 upload key `AC:B1:8B:84:F0:67:E9:CE:4D:AD:EA:D5:B2:97:7C:1E:F4:06:2E:3D:DE:39:52:A6:E3:CC:36:8B:D5:D7:43:69`，而 Play app signing key 為 `33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58`。正式網站 APK 仍須在 App 上架後改用 Play signed universal APK，runtime metadata 是否包含 `applicationId` 不改變這項發佈門檻。

網站 `feat/010-website-analytics` 建立的 `GET /api/downloads/android/latest/metadata` 已部署。它是供網站首頁與 App 共用的公開下載資訊 DTO，刻意只回傳平台、狀態、版本、檔名、大小、更新日期與下載路徑，不公開 `applicationId`；目前 `downloadUrl` 為相對路徑 `/api/downloads/android/latest`。Android 不應要求網站新增與版本判斷無關的包名回聲，而應以固定官方 endpoint、嚴格欄位及下載路徑白名單建立 runtime 信任邊界。

## Goals / Non-Goals

**Goals:**

- 以 Google Play 對目前用戶的資格結果作首要更新權威，無 Play 的非 Play 安裝才使用網站。
- 提供非阻塞 24 小時自動檢查、可隨時執行的手動檢查及可靠本地快照。
- 以 3 天首次提醒、3 天稍後提醒及 versionCode 級略過提供可控且不重複騷擾的提示。
- 在設定頁以狀態摘要與小紅點持續表達更新，並支援三語、深淺色、大字體及 TalkBack。
- 在 Play 允許時完成 flexible update 下載與安裝確認生命週期；網站渠道只開啟三語下載頁。
- 建立網站 APK 與 Play app signing key、versionCode 及 100% 發佈順序的可驗證發佈門檻。

**Non-Goals:**

- 不直接下載、驗證或安裝 APK，不申請 `REQUEST_INSTALL_PACKAGES` 或 `QUERY_ALL_PACKAGES`。
- 不提供 immediate 或強制更新，不阻止用戶繼續使用 App。
- 不以網站版本覆蓋 Play 灰度或裝置資格，不因 Play 暫時失敗切換網站。
- 不引入遠端提醒策略、帳號、分析識別或 SQLite migration。
- 不修改 Citybus、DATA.GOV.HK、Google 地址、行程資料、通知監控或排序行為。

## Decisions

### 0. 正常構建固定使用 Play 優先策略

Google Play 上架後刪除本機網站強制開關；正常構建固定使用 Play 優先策略。網站渠道只保留給目前沒有可用官方 Play 的非 Play／未知非 Play 安裝，`ERROR_APP_NOT_OWNED` 只把網站較高版本當作正向證據。

Debug 構建無法代表 Google Play 的正式交付與帳號擁有權，因此更新協調器在任何 installer、Play package、Play Core 或網站請求前短路。手動檢查提供前往 Google Play 的受控提示，自動檢查保持靜默並保存 24 小時嘗試節流，兩者都不寫入可靠的最新或更新可用快照。

被否決方案：繼續保留本機網站強制模式會讓正式行為存在無需的平行接線，亦可能再次以網站全局版本取代 Play 資格判斷。

### 1. Play 能力優先，初始安裝渠道只限制網站兜底

更新協調器先呼叫 Play Core，並以 `AppUpdateInfo` 的更新狀態作目前用戶的權威結果。`UPDATE_AVAILABLE` 與 `UPDATE_NOT_AVAILABLE` 都不再查網站；`ERROR_APP_NOT_OWNED` 保持 Play 操作渠道，但可在網站已遵守「Play 100% 後才發佈」的前提下讀 metadata 判斷是否有較高版本。只有網站 `versionCode` 較高時才形成可靠更新快照；版本相等、較低、請求失敗或 metadata 無效都保留 `PLAY_APP_NOT_OWNED`，不得宣稱目前已是最新。Play 暫時失敗但 `com.android.vending` 可用時保留 Play 渠道。

系統首次使用更新能力時保存 `initialInstallChannel`：API 30 或以上讀 `getInstallSourceInfo()`，API 25–29 讀 `getInstallerPackageName()`。目前有 Play 時不論初始渠道都走 Play；只有沒有 Play且初始渠道不是 Play時才走網站。初始為 Play 的安裝即使 Play 日後被停用，也只顯示 Play 暫不可用。

被否決方案：只按 installer 分流會讓網站安裝在有 Play 時仍走網站；所有裝置只查網站則會忽略 Play 灰度與資格；Play 失敗即改網站會造成不合規的跨渠道更新。

### 2. 以協調器、資料來源及偏好存取維持分層

新增以下責任邊界，名稱可在實作時依既有 package 慣例微調，但不得把長流程放回 Fragment：

- `AppUpdateCoordinator`：自動／手動入口、single-flight、渠道解析、可靠快照、提醒決策與前台交付。
- `PlayUpdateSource`：封裝 `AppUpdateManager`、Play 狀態、flexible flow 及安裝狀態監聽。
- `WebsiteUpdateSource`：透過 HTTPS 讀取及驗證 metadata，不下載 APK。
- `UpdateChannelResolver`：結合 Play 結果、`com.android.vending` 可用性與初始渠道產生 `PLAY`、`WEBSITE` 或 `PLAY_UNAVAILABLE`。
- `UpdateStateStore`：以 SharedPreferences 保存節流、快照、首次發現、defer 及 skip。
- `UpdatePolicy`：集中保存 24 小時、3 天、3 天本地常量。

`MainActivity` 只在首個主要畫面完成後觸發 `checkIfDue(AUTOMATIC)` 並作為安全 Dialog／flexible flow host；`SettingsFragment` 只渲染快照及發起 `check(MANUAL)`。HTTP、JSON、Play error mapping 與持久化不進入 UI 類別。

被否決方案：直接在 `SettingsFragment` 串接 Play、HTTP 與 SharedPreferences 會讓冷啟動、手動檢查、Activity 重建及測試注入互相耦合；新增 SQLite 則超出小型偏好狀態需要。

### 3. 分離嘗試狀態、可靠快照與提醒狀態

SharedPreferences 保存：

- 嘗試：`lastAutoAttemptAt`、`lastAttemptAt`、受控 outcome、`lastSuccessfulCheckAt`。
- 初始渠道：固定的 `PLAY`、`NON_PLAY` 或 `UNKNOWN_NON_PLAY`。
- 快照：`NEVER_CHECKED`／`UP_TO_DATE`／`UPDATE_AVAILABLE`、渠道、installed／available version、`availableSinceAt`、`firstSeenAt`。
- 提醒：`deferredVersionCode`、`deferredUntil`、`skippedVersionCode`。

自動檢查在發請求前先記錄 `lastAutoAttemptAt`，因此失敗亦不會在每次冷啟動重試；失敗只更新 attempt outcome，不刪可靠快照。App 啟動時如果目前 versionCode 已不低於快照版本，先同步清理小紅點、defer 及 skip。

被否決方案：只保存最後檢查時間無法區分「本次失敗」與「上次可靠有更新」；失敗時清空快照會造成小紅點閃爍及誤報最新。

### 4. 以渠道權威時間判斷三天門檻

Play 更新使用 `clientVersionStalenessDays()`；如果為 null，使用本機首次觀察到該 versionCode 的時間。網站把必填 `lastUpdated` 解析為香港時區當日零時，並以滿 `3 × 24` 小時判斷門檻；剛好滿 72 小時即到期。自動提醒條件為：較高 versionCode、已滿 3 天、未 skip、defer 到期、Activity 可安全展示。

點擊「稍後提醒」把同版本延後 3 天；「略過此版本」只抑制該 versionCode 的自動 Dialog；「前往更新」亦先寫 3 天 defer，避免跳到商店或網站後未完成安裝而次日再提示。手動檢查無視 24 小時、3 天、defer 及 skip，惟不自行清除 skip。

Dialog 設為不可 cancel，確保返回鍵與點擊外部不被誤解為「稍後」或「略過」。三個操作必須以可換行或垂直佈局容納三語大字體。

被否決方案：每日提醒會在更新頻繁階段造成騷擾；把 Dialog 關閉視為永久略過缺乏明確同意；遠端調整天數增加服務依賴，已決定使用本地常量。

### 5. 設定頁小紅點由可靠 versionCode 差異推導

設定列新增副標題狀態與標題右側無數字小紅點。只有可靠快照為 `UPDATE_AVAILABLE` 且 availableVersionCode 大於目前版本時顯示。發現更新即顯示，不等待 3 天；查看、defer 或 skip 都不清除；成功升級或可靠無更新結果才清除。失敗但已有可靠更新時保留紅點，手動失敗另顯示可重試提示。

小紅點使用 Material error 語意色，但文字摘要與 content description 同時表達更新，避免只靠顏色。設定列覆蓋尚未檢查、檢查中、最新、有更新、稍後、略過、無快照失敗及保留快照失敗。

被否決方案：點擊即消除會把「有更新」誤當未讀；只顯示紅點不符合無障礙；每次進設定頁重新請求會破壞 24 小時與可靠快照模型。

### 6. Play 更新只由用戶啟動 flexible flow

引入 Google Play In-App Updates 依賴。自動檢查永遠不啟動 Play UI；用戶按「前往更新」且 flexible 被允許時，才由 resumed Activity 啟動 flow。`InstallStatus.DOWNLOADED` 後顯示持續可操作的「重新啟動並安裝」，用戶確認才呼叫 `completeUpdate()`；Activity 返回前台時重查並恢復提示。

flexible 不允許或 flow 無法啟動時，先用明確 package 的 `market://details?id=com.golink.busiscoming`，再以 Play HTTPS 詳情頁兜底。已判定 Play 渠道後，即使兩者打開失敗也只顯示錯誤，不改網站。

被否決方案：immediate／強制更新與用戶可控性不符；直接打開 Play 頁失去 flexible 體驗；自動啟動更新 UI 會打斷冷啟動。

### 7. 網站只提供白名單 metadata 與三語頁面入口

網站資料來源固定讀 `https://www.busiscoming.com/api/downloads/android/latest/metadata`，要求 `Cache-Control: no-store` 且 DTO 至少包含：

```text
platform
status
versionName
versionCode
fileName
sizeBytes
lastUpdated
downloadUrl
```

App 僅由固定 HTTPS 官方 endpoint 取得資料，驗證響應最終 URL 沒有 user info、非預期 port、query 或 fragment，並驗證 `platform=android`、`status=available`、正整數 versionCode、可展示 versionName、正整數 sizeBytes、ISO 日期及必要欄位。`applicationId` 不屬於 runtime DTO；APK 的 application ID 與簽名由發佈管線從實際 APK 驗證。

`downloadUrl` 只接受精確相對路徑 `/api/downloads/android/latest`，或完全等價的 `https://www.busiscoming.com/api/downloads/android/latest`；其他相對路徑、scheme-relative URL、非官方 host、HTTP、非預期 port、query 或 fragment 均視為無效。版本只比較 versionCode，且 `downloadUrl` 僅作契約一致性驗證，不作 App Intent 目標。更新操作依目前語言固定開啟 `/zh-hant/#download`、`/zh-hans/#download` 或 `/en/#download`，讓用戶在網站再次確認下載。

被否決方案：App 直接打 `/api/downloads/android/latest` 會在進入瀏覽器後立即下載；接受服務端任意 URL 會擴大跳轉風險；在 App 內下載與調起 installer 需要不適合本產品的高風險權限。

### 8. 網站發佈以 Play app signing key 與 100% 發佈為門檻

發佈順序固定為：上傳 AAB → Play 目標地區 100% → 從 Play Console 下載 signed universal APK → 用 `apksigner`／package metadata 驗證 app signing certificate、application ID、versionCode、versionName → 從 APK 產生網站 size／SHA-256／metadata → 驗證下載響應後公開。

網站不得公開目前以 upload key 簽署的候選 APK，也不得在 Play 灰度期間提前公開較高網站版本。這確保 `ERROR_APP_NOT_OWNED` 用戶被導向 Play 時確實能取得網站已知版本。

被否決方案：本地以 upload key 簽 APK 無法覆蓋 Play 交付版本；網站先上線會讓 Play 優先策略把用戶導向尚未可用的版本。

### 9. 併發、生命週期與測試注入

協調器維持單一有效檢查 generation；重疊手動操作附著到進行中的有效請求，或使舊 callback 作廢。只有 resumed Activity 能顯示 Dialog 或啟動 Play flow；背景完成結果先持久化，恢復前台再交付。Fragment 銷毀後不再接收 UI callback，但 App 級檢查可完成。

時間來源、Play source、網站 source、package probe 及 state store 應可注入 fake。JVM 測試使用固定 epoch，不實際等待，至少覆蓋 24 小時自動節流與 72 小時首次／稍後提醒在「前一毫秒、剛好、後一毫秒」的結果、系統時間回撥、同版 skip／defer、新版本重置、錯誤矩陣與 generation；網站 source 另以已部署響應同形 fixture 覆蓋無 `applicationId`、相對 `downloadUrl` 及惡意 URL 邊界。instrumentation 驗證設定頁與 Dialog；真實 internal test 驗證 Play 資格、簽名與 flexible 流程，mock 不代替最後門檻。

被否決方案：以實際系統時間和 Play singleton 寫死會讓邊界測試不穩定；只做 instrumentation 無法完整覆蓋狀態矩陣。

## Risks / Trade-offs

- [網站公開 DTO 與 Android parser 漂移] → 以網站實際響應同形 fixture、已部署 endpoint 只讀核對及固定欄位白名單回歸；`applicationId` 不作 runtime 必填欄位。
- [相對 `downloadUrl` 被誤當非法或任意 URL 被誤信任] → 只接受精確官方相對路徑或其等價官方 HTTPS 絕對 URL，拒絕 query、fragment、其他 host／path／scheme，且永不直接用作 Intent。
- [Play Core 在 sideload／帳號未擁有時可能無法提供版本] → `ERROR_APP_NOT_OWNED` 只用已遵守 Play 100% 門檻的網站 metadata 判斷是否顯示更新，操作仍導向 Play。
- [Package installer 可能為 null 或隨更新改變] → 首次保存渠道；有 Play 時始終 Play；無 Play 且未知時只歸為未知非 Play，不把 installer 當安全憑證。
- [Play 暫時錯誤造成網站錯誤降級] → resolver 必須同時判斷 Play error 與官方 package 可用性，暫時錯誤只保留可靠快照。
- [使用者點更新但不完成] → 點擊前先 defer 3 天；更新完成後以目前 versionCode 同步清理。
- [三個 Dialog action 在英文或大字體下擁擠] → 使用可換行／垂直 action 佈局，按 360dp 及 font scale 2.0 驗證，不縮字。
- [系統時間回撥破壞節流] → 負間隔按未到期處理，持久化 epoch 時間並以注入 clock 做邊界測試。
- [網站 APK 簽名或 metadata 人工失配] → 只使用 Play signed universal APK，從實際包提取 metadata 並在發佈前以腳本驗證。
- [沒有遠端 kill switch] → 更新檢查失敗預設 fail-safe 且不阻塞 App；如 Play 版本有嚴重問題，使用 Play halt／新版本回復，網站保留上一個已驗證包直到新版本 100%。
- [真實 Play 資格與 flexible flow 尚未驗收] → 正常構建固定進入 Play 優先流程，以 TD-002 及未完成的 Internal App Sharing 任務追蹤，取得 v10 → v11 flexible flow 真實證據後才關閉技術債。

## Migration Plan

1. 確認網站已部署 metadata endpoint、真實 DTO 不含 `applicationId`、`downloadUrl` 可為固定相對路徑，並保持 `Cache-Control: no-store`。
2. 在 Android 工程以真實 DTO 契約修正網站 parser，並維持既有 Play 依賴、有限 package visibility、更新模型／policy／store／source／coordinator。
3. 接入 `MainActivity` 冷啟動與 `SettingsFragment` 手動入口、小紅點、三語 Dialog、flexible 完成提示及 instrumentation。
4. 刪除本機網站強制模式，以 Play internal test／Internal App Sharing 及已擁有 App 的帳號驗證較高 versionCode flexible flow；Debug 構建只驗證受控失敗，不作 Play 資格證據。
5. 運行 `./gradlew build`，並完成三語×深淺色×360dp×font scale 1.0／1.3／2.0 與 TalkBack 人工驗收。
6. 發佈 AAB 並完成目標地區 100%；下載 Play signed universal APK，驗證 app signing certificate 後才替換網站 APK 與 metadata。
7. 如需回滾，Play 使用 halt／修復版本；網站不公開未完成 100% 或未驗證的新包。已安裝 App 的檢查故障保持靜默，不影響行程查詢與本機資料。

## Open Questions

無未裁決產品行為。網站 metadata、signed universal APK 與簽名發佈鏈已可進行只讀驗證；Play internal test／Internal App Sharing 的 v10 → v11 真實 flexible flow 仍屬關閉 TD-002 的驗收條件，而非待決設計選項。
