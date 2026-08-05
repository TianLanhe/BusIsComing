## Why

設定頁目前雖有「檢查更新」入口，但只會顯示暫不支援提示，使用者無法得知 Google Play 或官方網站是否已有新版本。App 即將同時由 Google Play 與官方網站提供下載，現在需要在網站正式上線前建立符合 Play 政策、簽名相容且不誤導灰度用戶的渠道感知更新流程。

## What Changes

- 新增冷啟動靜默檢查與設定頁手動檢查；自動檢查最多每 24 小時一次，失敗時保持靜默並保留最近可靠結果。
- 目標渠道策略是：只要裝置有可用的官方 Google Play，不論 App 最初從何處安裝，都由 Play 判斷目前帳號、軌道、地區與裝置的更新資格，並在允許時使用 flexible in-app update。
- Google Play 上架後刪除本機網站強制開關；正常構建固定使用 Play 優先策略。網站渠道只保留給目前沒有可用官方 Play 的非 Play／未知非 Play 安裝，`ERROR_APP_NOT_OWNED` 只把網站較高版本當作正向證據。
- 只有 App 初始為非 Play 安裝且目前沒有可用官方 Play 時，才查詢 `https://www.busiscoming.com/api/downloads/android/latest/metadata`，並把更新操作導向目前語言網站的 `#download` 區域。
- 網站 metadata 沿用已部署首頁下載資訊契約，不要求回傳 `applicationId`；Android 端固定請求官方 endpoint，驗證平台、版本、日期與下載路徑等必要欄位，並接受精確相對路徑 `/api/downloads/android/latest` 或其等價官方 HTTPS 絕對 URL。
- Play Core 只提供可用 `versionCode`；Play 已確認更新時，網站 metadata 只在 `versionCode` 精確一致時補充真實 `versionName` 供 UI 以 `v1.2` 形式展示，不一致或請求失敗時使用不含版本數字的通用更新摘要，禁止把 `versionCode` 冒充 `versionName`。
- 新增本地更新狀態、首次發現時間、稍後提醒與略過 versionCode；更新可用滿 3 天後才自動提醒，「稍後提醒」延後 3 天，「略過此版本」只抑制同一 versionCode 的自動彈窗。
- 把設定頁「檢查更新」由暫不支援入口改為可操作狀態，展示最近檢查結果；發現較新版本時立即顯示小紅點，且不因查看、稍後或略過而清除。
- 新增三語、深淺色、窄屏、大字體與無障礙狀態；更新 Dialog 只提供「前往更新／稍後提醒／略過此版本」三個明確操作，且不可由返回鍵或點擊外部關閉。
- 建立網站 APK 發佈門檻：先完成相同版本的 Google Play 目標地區 100% 發佈，再從 Play Console 取得 app signing key 簽署的 signed universal APK，驗證 metadata 與實際 APK 後才上線網站。
- 不在 App 內下載或安裝 APK，不申請 `REQUEST_INSTALL_PACKAGES`、`QUERY_ALL_PACKAGES`，不新增強制更新或 immediate update。

## Capabilities

### New Capabilities

- `app-update-check`: 定義 Play 優先與網站兜底的渠道選擇、自動／手動檢查、本地狀態、提醒與略過、設定頁狀態、小紅點、flexible update、網站 metadata 契約及發佈驗證。

### Modified Capabilities

- `app-settings-support`: 移除「檢查更新僅顯示暫不支援 Toast」的既有要求，保留應用評分暫不支援，並讓檢查更新入口交由新更新能力提供實際行為。

## Impact

- **Android 代碼**：影響 `ui/main` 的 `MainActivity`、`SettingsFragment` 與設定頁 XML；新增更新領域結果、渠道 resolver、Play／網站資料來源、協調器與 SharedPreferences 狀態存取，避免把 HTTP、Play Core 或長流程狀態散落到 Fragment。
- **依賴與 Manifest**：新增 Google Play In-App Updates 依賴；Manifest 僅以 `<queries>` 聲明 `com.android.vending` 可見性，不增加高風險安裝或全量 package 查詢權限。
- **外部系統**：依賴 Google Play Core、Play 商店詳情頁、網站 metadata endpoint 與三語首頁 `#download`。網站 endpoint 必須提供並驗證 `platform`、`status`、`versionCode`、`versionName`、`lastUpdated` 與固定下載路徑等白名單欄位；`applicationId` 不屬於 App 執行時 metadata 契約，APK 身分改由發佈流程驗證。
- **發佈流程**：網站 APK 必須改用 Play app signing key 簽署的 signed universal APK；目前以 upload key 簽署的網站候選包不得公開。網站版本只可在 Play 目標地區完成 100% 發佈後上線。
- **相容性**：不修改 SQLite、已保存行程、匯入匯出格式、Citybus／DATA.GOV.HK／Google 查詢或通知監控；更新偏好可清除並重建，不屬於需遷移的用戶內容。
- **驗證**：需要以網站實際響應形狀覆蓋無 `applicationId` 與相對 `downloadUrl` 的契約測試，使用可注入 clock 覆蓋 24 小時／3 天／稍後提醒的前一毫秒、剛好與後一毫秒、時鐘回撥及新版本重置；另需設定頁 instrumentation、三語×深淺色×360dp×大字體與 TalkBack 驗收、無 Play 模擬器網站流程，以及具有相同 application ID／簽名和更高 versionCode 的 Google Play internal test 真實 flexible update 驗證。Android 實作完成後運行 `./gradlew build`，並對已部署 metadata 做只讀線上契約核對。
