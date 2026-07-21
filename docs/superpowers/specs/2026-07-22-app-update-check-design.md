# App 檢查更新設計

日期：2026-07-22
狀態：用戶已確認

## 背景

BusIsComing 目前在設定頁已有「檢查更新」入口，但點擊後只顯示尚未支援的提示。App 有兩個預定下載來源：Google Play 與 BusIsComing 官方網站。網站倉庫已提供穩定 APK 下載入口，`feat/010-website-analytics` 分支亦已規劃 APK metadata API、版本展示與下載統計，但相關 metadata handler 仍屬待實作範圍。

本設計建立一套渠道感知的更新檢查流程：只要裝置具備可用的官方 Google Play，就以 Google Play 作為更新權威並盡量使用 flexible in-app update；只有 App 來自網站／非 Play 安裝且裝置沒有可用 Google Play 時，才查詢官方網站版本並在瀏覽器開啟三語下載頁。自動檢查、設定頁狀態、提醒延後和略過版本使用同一份本地狀態。

## 目標

- App 冷啟動時最多每 24 小時靜默檢查一次更新。
- 設定頁可隨時手動檢查，並展示最近一次可靠結果。
- 只要存在較新版本，設定頁「檢查更新」標題旁顯示小紅點。
- 更新已對用戶可用至少 3 天後，才首次自動彈窗提醒。
- 用戶可「前往更新」「稍後提醒」或「略過此版本」。
- Google Play 可用時始終引導至 Play，並優先使用 flexible in-app update。
- 只有 App 來自網站／非 Play 安裝且沒有可用 Google Play 時才使用網站 metadata，並在瀏覽器開啟目前語言的下載頁。
- Google Play 與網站 APK 使用相同 application ID、應用簽名憑證及 versionCode 體系。
- 所有新增界面、狀態、錯誤及無障礙文案覆蓋香港繁體、獨立簡體與自然英文。

## 非目標

- 不由 App 直接下載、驗證或安裝 APK。
- 不申請 `REQUEST_INSTALL_PACKAGES`、`QUERY_ALL_PACKAGES` 或其他高風險權限。
- 不建立強制更新、阻止使用 App 或 immediate in-app update 流程。
- 不因 Play 暫時失敗而改用網站，亦不繞過 Play 的灰度、地區、帳號或裝置資格。
- 不建立遠端提醒策略；24 小時、3 天與 3 天均為 App 本地配置。
- 不記錄或上傳行程、位置、裝置識別資料或其他用戶資料。
- 不在本設計階段實作 App 或網站代碼。

## 已確認產品決策

1. 自動檢查在冷啟動後執行，最多每 24 小時一次；設定頁手動檢查不受此限制。
2. 首次自動提醒延遲為更新可用後 3 天。
3. 「稍後提醒」把同一版本的下一次自動提醒延後 3 天。
4. 更新彈窗只有「前往更新」「稍後提醒」「略過此版本」三個按鈕，不能以返回鍵或點擊外部關閉。
5. 「略過此版本」只抑制該 versionCode 的自動彈窗；不隱藏更新、不移除小紅點，也不影響手動檢查。
6. 用戶點擊「前往更新」後，即使最終沒有安裝，也先為該版本設定 3 天暫緩，避免次日再次彈窗。
7. 小紅點表示目前仍有可安裝更新，不是未讀狀態；點擊、稍後或略過都不會清除。
8. 有可用官方 Google Play 時，不論 App 最初從哪裡安裝，更新操作都只走 Google Play。
9. App 來自網站／非 Play 安裝且沒有可用 Google Play 時，網站更新操作開啟三語網站的 `#download` 區域，不直接開始 APK 下載。
10. 網站 APK 只在相同版本已完成 Google Play 目標地區 100% 發佈後上線。

## 簽名與發佈基線

### 設計時現況

設計時已用 `apksigner verify --print-certs` 驗證網站現有 APK：

- 網站現有 APK SHA-256 憑證：`AC:B1:8B:84:F0:67:E9:CE:4D:AD:EA:D5:B2:97:7C:1E:F4:06:2E:3D:DE:39:52:A6:E3:CC:36:8B:D5:D7:43:69`
- Google Play 上傳密鑰憑證：與上述網站 APK 相同。
- Google Play 應用簽名密鑰憑證：`33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58`

因此網站現有 APK 使用的是 upload key，不是 Google Play 實際交付 APK 的 app signing key。兩者不能互相覆蓋安裝。由於網站尚未正式上線，不需要處理存量網站用戶遷移；網站首個正式 APK 直接改用 Play 簽署版本。

### 統一簽名方式

網站不得再發佈由本地 upload key 直接簽署的 APK。每次發佈流程為：

1. 以正確 upload key 簽署 AAB 並上傳 Google Play。
2. 完成目標地區 100% 發佈。
3. 從 Play Console 的 App Bundle Explorer 下載由 app signing key 簽署的 signed universal APK。
4. 驗證 APK 的 application ID、versionCode、versionName 與應用簽名憑證。
5. 以該 APK 更新網站受管下載檔案。
6. 從實際 APK 自動提取版本、大小與 SHA-256，更新網站 metadata；禁止人工複製舊值。
7. 驗證 metadata、APK bytes、下載響應與網站展示一致後才對外發佈。

Android 接受跨來源更新至少要求 application ID、簽名憑證及 versionCode 相容。Google 官方亦支援下載 Play 簽署的 universal APK 用於網站等其他渠道：

- [Android App 更新與跨商店規則](https://developer.android.com/google/play/app-updates)
- [從 Play Console 下載 signed universal APK](https://support.google.com/googleplay/android-developer/answer/9844279?hl=en)

## 方案比較

### 方案一：按最初安裝來源分流

Play 安裝走 Play，網站安裝走網站。這個方案概念簡單，但 Android 回傳的 installer 可能是瀏覽器、系統安裝器、下載管理器或 `null`，且更新後 installer 記錄可能改變。它亦不符合「裝置有 Play 就只使用 Play」的產品決策，因此不採用。

### 方案二：統一以網站 metadata 判斷版本

所有裝置先查網站版本，再依 Play 是否存在決定跳轉位置。這能提供一致的版本名稱與日期，但網站無法表達 Play 對特定帳號、軌道、地區和裝置的資格，可能提示一個用戶在 Play 尚不可取得的版本，因此不採用。

### 方案三：Play 能力優先，網站只作無 Play 兜底

App 先讓 Play Core 判斷當前用戶與裝置的更新資格；只有明確沒有可用官方 Play 時才查網站。這能尊重 Play 的灰度和資格判斷，又能讓沒有 Play 的網站用戶保持更新，為本設計採用方案。

## 渠道判斷

### 安裝來源不改變 Play 優先級

App 首次執行更新能力時讀取並持久化初始安裝渠道：

- Android 11 或以上：`PackageManager.getInstallSourceInfo(packageName).installingPackageName`
- Android 10 或以下：`PackageManager.getInstallerPackageName(packageName)`

`com.android.vending` 代表 Google Play；網站安裝則可能顯示瀏覽器、系統安裝器或 `null`。由於產品正式渠道只有 Play 與官方網站，非 Play installer 可歸為 `NON_PLAY`；沒有 Play 且 installer 為 `null` 時歸為 `UNKNOWN_NON_PLAY`，保留給網站兜底。初始渠道一經保存，不因後續跨渠道更新改寫。

安裝來源不決定 Play 優先級：只要目前有可用官方 Play，不論初始渠道為何都使用 Play。它只作為沒有 Play 時的第二道網站兜底門檻：

- 初始渠道為 `PLAY`：即使日後 Play 被停用或移除，也不改走網站；設定頁顯示 Play 暫不可用。
- 初始渠道為 `NON_PLAY` 或 `UNKNOWN_NON_PLAY`，且目前沒有可用官方 Play：允許網站 metadata 與三語下載頁。

### Google Play 能力判斷

1. 優先呼叫 Play Core `AppUpdateManager.appUpdateInfo`。
2. 如果 Play 返回更新可用，使用 Play 回傳的 per-user 結果。
3. 如果 Play 返回沒有更新，對當前帳號、裝置和軌道視為最新，不再查網站版本。
4. 如果返回 `ERROR_APP_NOT_OWNED`，表示官方 Play 存在但目前帳號尚未從 Play 取得 App；此時可以查網站 metadata 判斷是否有較高版本，但所有更新操作仍只開啟 Play。
5. 如果 Play Core 暫時失敗，但 `com.android.vending` 已安裝、啟用且能處理 Play 詳情頁，仍保持 Play 渠道，不得改用網站。
6. 只有返回 `ERROR_PLAY_STORE_NOT_FOUND`、再次確認沒有可用官方 Play，且初始渠道不是 `PLAY` 時，才選擇網站渠道。

Android 11 或以上會限制 package visibility，因此 Manifest 只聲明：

```xml
<queries>
    <package android:name="com.android.vending" />
</queries>
```

這不是權限，不需要亦不得申請 `QUERY_ALL_PACKAGES`。

參考：

- [InstallSourceInfo](https://developer.android.com/reference/android/content/pm/InstallSourceInfo)
- [Play InstallErrorCode](https://developer.android.com/reference/com/google/android/play/core/install/model/InstallErrorCode)
- [Android package visibility](https://developer.android.com/training/package-visibility/declaring)

## 渠道狀態矩陣

| 狀態 | 版本判斷 | 「前往更新」行為 |
| --- | --- | --- |
| Play 有更新且允許 flexible | Play `AppUpdateInfo` | 啟動 flexible in-app update |
| Play 有更新但不允許 flexible | Play `AppUpdateInfo` | 開啟 Play 詳情頁 |
| Play 沒有更新 | 對當前用戶視為最新 | 不提供更新操作 |
| `ERROR_APP_NOT_OWNED` | 查網站 metadata；網站只在 Play 100% 後發佈 | 開啟 Play 詳情頁 |
| Play 暫時失敗但官方 Play 可用 | 保留上次可靠結果 | 手動時可開啟 Play 詳情頁；不走網站 |
| 官方 Play 不存在／不是官方版本；初始渠道為非 Play | 網站 metadata | 開啟目前語言網站的 `#download` |
| 官方 Play 不存在／被停用；初始渠道為 Play | 保留上次可靠結果 | 顯示 Play 暫不可用，不開啟網站 |
| 無網絡 | 保留上次可靠結果 | 自動靜默；手動顯示失敗 |

## 技術組件

### AppUpdateCoordinator

統一編排自動與手動檢查、single-flight、渠道選擇、結果持久化、提醒決策及前台彈窗。Activity 或 Fragment 不自行拼接 Play、HTTP 與本地狀態。

### PlayUpdateSource

封裝 `AppUpdateManager`、`AppUpdateInfo`、錯誤碼、flexible update 啟動與下載完成狀態。對上層回傳結構化領域結果，不把 Play Core 類型散落到設定頁。

### WebsiteUpdateSource

只在渠道 resolver 明確選擇網站後，讀取官方 metadata。它不下載 APK，亦不把服務端任意 URL 直接交給 Intent。

### UpdateChannelResolver

結合 Play Core 結果、`com.android.vending` 可用狀態、有限 package visibility 與持久化初始安裝渠道，產生 `PLAY`、`WEBSITE` 或 `PLAY_UNAVAILABLE`。目前有 Play 時永遠優先 Play；初始安裝渠道只限制沒有 Play 時能否使用網站。

### UpdateStateStore

以 `SharedPreferences` 保存最近一次嘗試、最近一次成功快照、目前可用版本、首次發現時間、稍後提醒和略過版本。不需要 SQLite schema migration。

### UpdatePolicy

本地集中配置：

```text
AUTO_CHECK_INTERVAL_HOURS = 24
FIRST_REMINDER_DELAY_DAYS = 3
REMIND_LATER_DELAY_DAYS = 3
```

這些值不由網站遠端覆蓋。日後調整只修改集中常量並隨 App 發佈。

## 調用入口與生命週期

```text
MainActivity
→ 首個主要畫面已完成展示
→ AppUpdateCoordinator.checkIfDue(AUTOMATIC)

SettingsFragment
→ 讀取本地快照並顯示狀態／小紅點
→ 用戶點擊後 AppUpdateCoordinator.check(MANUAL)
```

- 自動檢查不得阻塞 App 冷啟動或首屏互動。
- 同一進程內自動與手動請求共享 single-flight；重複請求附著到同一結果。
- 自動彈窗只在前台 Activity 處於 resumed 狀態時展示；背景或 state 已保存時暫存到恢復前台。
- 離開設定頁不取消 App 級檢查，但已銷毀的 Fragment 不得接收舊 UI callback。
- 每次自動檢查在發起前先寫入 `lastAutoAttemptAt`，網絡失敗亦受 24 小時節流；手動檢查始終可繞過此節流。

## 本地狀態模型

### 最近一次嘗試

- `lastAutoAttemptAt`：上次自動嘗試時間，用於 24 小時節流。
- `lastAttemptAt`：最近一次自動或手動嘗試時間。
- `lastAttemptOutcome`：`SUCCESS` 或受控失敗類別，用於設定頁狀態。
- `lastSuccessfulCheckAt`：最近一次可靠版本判斷時間。
- `initialInstallChannel`：首次判斷後固定為 `PLAY`、`NON_PLAY` 或 `UNKNOWN_NON_PLAY`，只用於無 Play 時的網站兜底資格。

### 最近一次可靠快照

- `status`：`NEVER_CHECKED`、`UP_TO_DATE` 或 `UPDATE_AVAILABLE`。
- `channel`：`PLAY` 或 `WEBSITE`。
- `installedVersionCode`、`installedVersionName`。
- `availableVersionCode`、`availableVersionName`，無更新時為空。
- `availableSinceAt`：更新開始對用戶可用的時間；無權威時間時由 `firstSeenAt` 兜底。
- `firstSeenAt`：本機首次觀察到該 versionCode 的時間。

### 提醒狀態

- `deferredVersionCode`、`deferredUntil`：某版本的稍後提醒期限。
- `skippedVersionCode`：已選擇略過自動提醒的版本。

失敗不得刪除可靠快照；因此一次暫時性失敗不會讓已知更新或小紅點消失。App 升級後若 `currentVersionCode >= availableVersionCode`，啟動時先同步清理舊更新、稍後和略過狀態，不等待網絡。

## 更新可用時間

### Google Play

Play 返回更新時，使用 `clientVersionStalenessDays()`，它表示 Google Play 在該用戶裝置上知道更新可用的天數，能尊重灰度與帳號資格。可把檢查時間減去該天數，得到本地 `availableSinceAt`。

如果 Play 返回更新但 staleness 為 `null`，以本機第一次觀察到該 versionCode 的 `firstSeenAt` 作保守兜底；不得用網站全局發佈日取代 Play 用戶資格時間。

### 網站

網站渠道使用 metadata 的 `lastUpdated` 作為 APK 發佈日期，按香港時區計算完整日數。日期缺失或非法時不得猜測舊值，使用本機 `firstSeenAt` 兜底或把 metadata 判為失敗，具體由契約是否把該欄位定為必填決定；本設計要求它為必填。

參考：[AppUpdateInfo](https://developer.android.com/reference/com/google/android/play/core/appupdate/AppUpdateInfo)

## 自動檢查與提醒狀態流

### 自動檢查

1. App 冷啟動並完成主界面展示。
2. 如果距 `lastAutoAttemptAt` 少於 24 小時，不發請求，設定頁繼續使用快照。
3. 達到 24 小時時，寫入本次嘗試時間並進行渠道感知檢查。
4. 檢查失敗時保持靜默，保留快照。
5. 成功且無更新時保存 `UP_TO_DATE`，清理已失效的提醒狀態與小紅點。
6. 成功且有更新時保存版本快照，立即更新設定頁及小紅點，再獨立判斷是否彈窗。

### 自動彈窗條件

以下條件必須全部成立：

```text
availableVersionCode > currentVersionCode
更新已對該用戶可用至少 3 天
skippedVersionCode != availableVersionCode
沒有同版本 deferredUntil，或目前時間已達 deferredUntil
前台 Activity 處於可安全展示 Dialog 的狀態
```

新 versionCode 不受舊版本的稍後與略過狀態影響。如果新版本被首次發現時已可用超過 3 天，可以立即提醒。

### 三個按鈕

- **前往更新**：先寫入同版本 `deferredUntil = now + 3 days`，再啟動當前渠道更新操作。
- **稍後提醒**：寫入同版本 `deferredUntil = now + 3 days`，不啟動更新。
- **略過此版本**：寫入 `skippedVersionCode`，並清除同版本 defer。

Dialog 設為不可取消；返回鍵與點擊外部都不關閉，使用者必須明確選擇三個操作之一。

### 手動檢查

- 永遠繞過 24 小時節流。
- 正在檢查時禁止同一入口重複提交，但可附著到進行中的自動檢查。
- 有更新時立即展示更新操作，不受首次 3 天、稍後或略過狀態限制。
- 手動檢查本身不自動清除略過狀態；只有用戶選擇或 versionCode 改變時才修改提醒狀態。
- 已是最新版本時明確告知；失敗時顯示可重試錯誤。

## Google Play 更新流程

- 自動檢查只判斷更新，不自動啟動 Play UI。
- 用戶點擊「前往更新」且 `AppUpdateType.FLEXIBLE` 允許時啟動 flexible flow。
- flexible 不允許或無法啟動時，開啟 `market://details?id=com.golink.busiscoming`。
- `market://` 無法處理時，以 Google Play HTTPS 詳情頁兜底；如果官方 Play 原本被判為可用但此刻開啟失敗，顯示受控錯誤，不改走網站。
- flexible 下載期間允許繼續使用 App。
- `InstallStatus.DOWNLOADED` 時顯示持續可見且可操作的「重新啟動並安裝」，由用戶確認後呼叫 `completeUpdate()`。
- App 回到前台時重新查詢 Play 狀態，恢復已下載但未完成的安裝提示。
- 本功能不使用 immediate update，也不阻止用戶繼續使用 App。

Play Core 是 App 與 Google Play 的 runtime 接口，更新是否可用、可用 versionCode、staleness 與 flexible 是否允許均以其回傳為準：

- [In-app updates](https://developer.android.com/guide/playcore/in-app-updates)
- [Kotlin／Java flexible update](https://developer.android.com/guide/playcore/in-app-updates/kotlin-java)

## 網站更新流程

網站渠道只在初始安裝渠道不是 `PLAY`，且目前沒有可用官方 Play 時啟用：

1. 讀取 `GET https://www.busiscoming.com/api/downloads/android/latest/metadata`。
2. 驗證 HTTPS、官方 host、平台、可用狀態、application ID、versionCode、versionName 與 `lastUpdated`。
3. 只以整數 versionCode 判斷是否更新；versionName 僅供展示。
4. 用戶點擊「前往更新」後，依 App 實際語言開啟：
   - `https://www.busiscoming.com/zh-hant/#download`
   - `https://www.busiscoming.com/zh-hans/#download`
   - `https://www.busiscoming.com/en/#download`
5. 網站頁面展示版本與大小，由用戶再次明確點擊才開始 APK 下載。

App 不採信 metadata 中的任意外部 URL，不直接啟動 APK endpoint。下載頁 host 與語言 path 由 App 固定的官方網站基址和既有 `LanguageSnapshot`／`AppLanguageRepository` 映射組合。

## 網站契約依賴

`BusIsComingWebsite` 的 `feat/010-website-analytics` 已規劃 metadata operation，但設計時仍有兩個阻塞 App 網站渠道正式驗收的事實：

1. 實際後端路由目前只註冊 `/api/downloads/android/latest`，metadata handler 尚未落地。
2. 該分支的 `backend/downloads/android/current.json` 仍為 `versionCode = 1`，而 App 設計時已為 `versionCode = 4`、`versionName = 1.1`。

網站實作需要讓 metadata 白名單 DTO 至少提供：

```text
platform
status
applicationId
versionName
versionCode
fileName
sizeBytes
lastUpdated
downloadUrl
```

現有 `feat/010-website-analytics` 的 metadata sample 尚未包含 `applicationId`，實作前需同步調整網站 feature contract、shared OpenAPI、測試和 handler。App 不使用 `downloadUrl` 直接下載，但網站本身仍需要該欄位維持下載頁契約。

metadata 成功與失敗均使用 `Cache-Control: no-store`。metadata 失敗不得影響網站原有穩定下載入口，但 App 端應把本次網站版本檢查視為失敗。

## 設定頁展示

### 小紅點

小紅點放在「檢查更新」標題右側，使用主題 error 語義色、不顯示數字，且不改變列高度或文字基線。顯示條件為：

```text
reliableSnapshot.status == UPDATE_AVAILABLE
availableVersionCode > currentVersionCode
```

- 不需要等待 3 天，只要可靠檢查發現更新便立即顯示。
- 點擊檢查、稍後提醒或略過此版本都不會清除。
- 成功升級或後續可靠檢查確認無更新時清除。
- 暫時性檢查失敗時保留。
- App 升級後啟動時可用本地 versionCode 立即清除。

小紅點不是唯一資訊來源；列摘要與 TalkBack 必須同時表達「有新版本」。

### 列摘要狀態

- 尚未檢查：提示點擊檢查新版本。
- 正在檢查：顯示進度狀態並避免重複點擊。
- 已是最新：顯示目前已是最新版本。
- 有更新：顯示新版本名稱。
- 已稍後：顯示新版本名稱與稍後提醒狀態。
- 已略過：顯示新版本名稱與已略過自動提醒狀態。
- 失敗且無可靠快照：顯示檢查失敗、可點擊重試。
- 失敗但有可靠快照：保留可靠版本與小紅點；手動操作另顯示本次失敗提示。

## 錯誤與降級

- **自動檢查失敗**：靜默，不 Toast、不彈窗、不清空快照。
- **手動檢查失敗**：顯示「暫時無法檢查更新，請稍後重試」。
- **Play 暫時失敗**：保持 Play 渠道，不以網站結果冒充 Play 資格。
- **Play 不存在或不是官方版本**：只有初始安裝渠道不是 Play 時才允許網站渠道；Play 初始安裝不因 Play 後來被停用而切換網站。
- **網站 timeout／非法 JSON／欄位缺失／包名不符**：檢查失敗，不顯示虛假更新。
- **flexible 不允許**：開啟 Play 詳情頁。
- **flexible 下載失敗或取消**：保留目前 App，不破壞本地資料；已寫入的 3 天暫緩繼續生效。
- **Play 撤回版本**：下一次成功判斷無更新時清理紅點與舊快照。
- **Activity 在背景**：延遲提示到 resumed，禁止背景彈窗。
- **系統時間回撥**：負間隔按尚未到期處理，不能造成連續檢查或重複提醒。
- **App 更新完成**：以目前 versionCode 清除不再適用的快照、defer 和 skip。

## Play 政策邊界

Android 8 或以上由 App 調起 package installer 需要 `REQUEST_INSTALL_PACKAGES`。Google Play 只允許安裝套件屬於核心功能的有限類型 App 使用該權限；公交查詢 App 不符合。Google Play 亦明確禁止從 Play 分發的 App 使用 Play 以外的方式更新自身。

因此本設計的網站路徑只開啟普通 HTTPS 下載頁，由瀏覽器與用戶接續處理，不在 App 內下載或安裝 APK：

- [Google Play 自更新限制](https://support.google.com/googleplay/android-developer/answer/16559646)
- [`REQUEST_INSTALL_PACKAGES` 政策](https://support.google.com/googleplay/android-developer/answer/12085295?hl=en)

## 多語言與無障礙

建議三語基線：

| 語義 | 香港繁體 | 簡體中文 | English |
| --- | --- | --- | --- |
| 設定入口 | 檢查更新 | 检查更新 | Check for updates |
| 更新狀態 | 有新版本可用 | 有新版本可用 | Update available |
| 主要操作 | 前往更新 | 前往更新 | Update now |
| 延後 | 稍後提醒 | 稍后提醒 | Remind me later |
| 略過 | 略過此版本 | 忽略此版本 | Skip this version |
| 完成 flexible | 重新啟動並安裝 | 重新启动并安装 | Restart and install |
| 無更新 | 已是最新版本 | 已是最新版本 | You’re up to date |
| 失敗 | 暫時無法檢查更新 | 暂时无法检查更新 | Couldn’t check for updates |

- App runtime 文案只放 Android resources，不在 Kotlin 或 XML 硬編碼。
- 香港繁體使用香港產品語境；簡體獨立審校；英文保持自然克制。
- 小紅點同時提供本地化 content description／狀態摘要，不依賴顏色傳達更新。
- Dialog 三個按鈕在 360dp、font scale 1.0／1.3／2.0 下不得裁切；必要時使用適合長文案的垂直操作佈局，而不是縮字。
- 淺色與深色均使用 Material 語義色，保持足夠對比。

## 安全與私隱

- 網站 metadata 只接受 HTTPS 與 `www.busiscoming.com`。
- App 不採信第三方 host、任意 scheme 或服務端提供的外部跳轉。
- metadata 只包含公開發佈資料；不提交行程、地點、位置、匿名 visitor ID、完整裝置資料或帳號資料。
- Play Core 的資料處理由 Google Play 機制負責，App 需在 Data safety 審核中按實際 SDK 行為準確申報。
- 錯誤日誌只記錄渠道、受控錯誤類別與版本代碼，不記錄用戶帳號、完整 Intent、Cookie 或第三方 response body。

## 驗證方案

### 單元測試

- 24 小時邊界：未到、恰好到達、超過、系統時間回撥。
- 首次 3 天提醒：未到、恰好到達、超過。
- 稍後 3 天、略過同版本、新 versionCode 使舊狀態失效。
- 點擊「前往更新」先寫入 3 天暫緩。
- 手動檢查繞過節流、略過和延後，但不自動清除略過。
- Play 更新可用／無更新／not owned／暫時失敗／Play 不存在的渠道矩陣。
- Play staleness 為 `null` 時使用 firstSeenAt。
- 網站 metadata application ID、versionCode、日期、狀態與 host 校驗。
- 檢查失敗保留可靠快照與小紅點。
- App 升級後同步清理舊狀態。
- single-flight 與舊 callback 不覆蓋新狀態。

### UI／instrumentation

- 設定頁尚未檢查、檢查中、最新、有更新、稍後、略過與失敗狀態。
- 小紅點顯示、持續與清除規則。
- 三按鈕 Dialog 不可用返回鍵或點擊外部關閉。
- 自動檢查不阻塞首屏，背景 Activity 不彈 Dialog。
- flexible 下載完成後顯示「重新啟動並安裝」。
- Play 詳情頁 market Intent 與 HTTPS fallback。
- 無 Play 時三語 URL 均準確落到 `#download`。
- 香港繁體、簡體、英文 × 淺色、深色 × 360dp × font scale 1.0／1.3／2.0。
- TalkBack 能朗讀更新狀態與小紅點語義。

### 真實渠道驗證

- 使用 Play internal test track 或 Internal App Sharing 測試 flexible update。
- 測試帳號必須已從 Play 取得 App，更新包具有相同 application ID、簽名和更高 versionCode。
- 在有官方 Play 的實體裝置驗證 flexible、Play 詳情頁與 not-owned 行為。
- 在沒有 Play 的模擬器驗證網站 metadata 與三語下載頁。
- 以 `apksigner` 驗證網站 APK 憑證等於 Play app signing key，而不是 upload key。
- 驗證網站只在 Play 目標地區 100% 發佈後公開相同版本。

Google 官方測試條件：[Test in-app updates](https://developer.android.com/guide/playcore/in-app-updates/test)

### 工程驗證

實作完成後運行：

```bash
./gradlew build
```

如果交付環境沒有符合條件的 Play 裝置或帳號，必須明確記錄真實 flexible 流程尚未完成，不得以 mock 或單元測試冒充渠道驗收。

## 相容性與遷移

- 不修改 SQLite schema、已保存行程、匯入匯出格式或通知監控 session。
- 網站尚未正式上線，因此不需要處理 upload-key 網站 APK 用戶的卸載遷移。
- Google Play 現有用戶繼續由 Play app signing key 更新。
- `RouteConfig`、Citybus 查詢、ETA、Google 地址與其他現有功能不受影響。
- 更新狀態只存於輕量 preferences；清除 App 資料後重新建立狀態即可，不屬於需備份的用戶內容。

## 後續實作前置條件

1. 網站 `feat/010-website-analytics` 的 metadata API 或等價主幹實作已部署。
2. metadata contract 已加入並驗證 `applicationId`。
3. Google Play 目標版本完成 100% 發佈。
4. 網站 APK 已替換為 Play app signing key 簽署的 signed universal APK。
5. 網站 metadata 已由實際 APK 自動生成，且 versionCode 不落後於 App 的正式網站版本。
6. Google Play internal test 帳號與裝置可用於 flexible update 真實驗證。
