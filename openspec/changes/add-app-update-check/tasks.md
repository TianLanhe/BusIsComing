## 1. 外部契約與工程準備

- [x] 1.1 在網站倉庫及已部署 endpoint 確認 `GET /api/downloads/android/latest/metadata` 返回 `platform`、`status`、`versionName`、`versionCode`、`fileName`、`sizeBytes`、`lastUpdated`、`downloadUrl` 與 `Cache-Control: no-store`；確認公開 DTO 按設計不含 `applicationId`，且目前 `downloadUrl` 為 `/api/downloads/android/latest`，不得在 App 內硬編碼最新版本。
- [x] 1.2 在 `gradle/libs.versions.toml` 與 `app/build.gradle.kts` 引入官方 Google Play In-App Updates 依賴，並以 Gradle dependency report 確認版本解析成功且沒有重複套用 Kotlin Android plugin。
- [x] 1.3 在 `app/src/main/AndroidManifest.xml` 只加入查詢 `com.android.vending` 所需的有限 package visibility；以 manifest 測試確認沒有新增 `QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES` 或 APK 安裝元件。

## 2. 更新模型、策略與本機狀態

- [x] 2.1 在 `data/model` 新增檢查觸發來源、初始安裝渠道、更新渠道、可靠快照、檢查結果與受控錯誤模型，明確表達 `NEVER_CHECKED`、`UP_TO_DATE`、`UPDATE_AVAILABLE`、`PLAY_UNAVAILABLE` 及已保留快照的失敗狀態。
- [x] 2.2 新增可注入 clock 的 `UpdatePolicy`，集中定義 24 小時自動檢查、3 天首次提醒及 3 天稍後提醒；以 JVM 測試覆蓋未滿／剛好／超過門檻、香港日期零時及系統時間回撥。
- [x] 2.3 新增 SharedPreferences 型 `UpdateStateStore`，保存首次安裝渠道、檢查嘗試、可靠快照、首次發現時間、defer 與 skip versionCode，並以 round-trip 與損壞值測試確認可安全回到預設狀態。
- [x] 2.4 實作啟動同步規則：目前 `versionCode` 不低於快照版本時清除更新快照、defer、skip 與小紅點；發現更高版本時重置舊版本的 defer／skip，並加入 JVM 回歸測試。
- [x] 2.5 實作提醒決策純邏輯，覆蓋 Play staleness、Play 首次觀察時間、網站 `lastUpdated`、3 天門檻、稍後、略過、點擊更新後延遲及手動檢查不受抑制的完整矩陣測試。
- [x] 2.6 補強可注入 clock 的確定性時間矩陣：24 小時節流、網站 72 小時提醒及 defer 在前一毫秒／剛好／後一毫秒的行為、時鐘回撥、同版 skip／defer 與較高 versionCode 重置；測試不得依賴真實等待。

## 3. 渠道探測與資料來源

- [x] 3.1 新增可注入的安裝來源與 package probe：API 30+ 使用 `getInstallSourceInfo()`、API 25–29 使用 `getInstallerPackageName()`，首次保存 `PLAY`、`NON_PLAY` 或 `UNKNOWN_NON_PLAY`，並以單元測試覆蓋 null、例外及 Play 後續被停用。
- [x] 3.2 新增 `PlayUpdateSource` 封裝 `AppUpdateManager`，把可用／無更新、`ERROR_APP_NOT_OWNED`、暫時錯誤、允許的更新類型、staleness 與 install status 映射成領域結果；以 fake 測試確認 UI 層不依賴 Play Core 型別。
- [x] 3.3 修正 `WebsiteUpdateSource` 以已部署響應為 runtime 契約：不要求 `applicationId`，接受精確相對 `/api/downloads/android/latest` 或等價官方 HTTPS 絕對 URL，拒絕其他 path／host／scheme／port／query／fragment；以實際響應同形 fixture 覆蓋合法、有更新、無更新、缺欄位、錯誤來源、惡意下載 URL、錯誤日期與 HTTP 失敗。
- [x] 3.4 實作 `UpdateChannelResolver` 並以表格化測試覆蓋：Play 可更新、Play 無更新、Play 暫時失敗、App not owned、有 Play 的網站安裝、無 Play 的非 Play 安裝、無 Play 的 Play 初始安裝及未知 installer；確認 Play 暫時失敗不降級網站。
- [x] 3.5 在網站渠道更新動作中只按目前 App 語言產生 `/zh-hant/#download`、`/zh-hans/#download`、`/en/#download` 白名單頁面 Intent，不使用 metadata 的 `downloadUrl`；以測試確認不直接下載或安裝 APK。
- [x] 3.6 Google Play 上架後刪除本機網站強制 BuildConfig、停用 Play source 與強制網站 coordinator 平行接線；正常 runtime 固定建立 Play source，無 Play 非 Play 安裝的網站渠道保持不變。
- [x] 3.7 把 debuggable 構建短路為 `PLAY_DEBUG_BUILD_UNSUPPORTED`，不呼叫 installer／Play package／Play source／網站 source，手動提供 Play 恢復提示，自動失敗保留 24 小時節流。
- [x] 3.8 把 `ERROR_APP_NOT_OWNED` 的網站 metadata 限制為正向證據：只有較高版本形成 Play 渠道更新，相等、較低、網絡失敗或非法資料均回傳 `PLAY_APP_NOT_OWNED`。

## 4. 檢查協調與 Android 生命週期

- [x] 4.1 新增 `AppUpdateCoordinator`，串接 store、policy、resolver、Play／網站 source，讓自動檢查在請求前記錄 attempt、最多每 24 小時一次，手動檢查繞過節流且兩者都不阻塞主線程。
- [x] 4.2 為 coordinator 實作 single-flight／generation 作廢與多觀察者交付，確保重疊檢查不重複請求、Fragment 銷毀後不接收 UI callback、舊結果不覆蓋新快照，並加入並發 JVM 測試。
- [x] 4.3 實作可靠快照合併：成功結果更新快照，失敗只更新 attempt outcome；既有更新快照、小紅點及版本資訊在後續失敗、查看、稍後或略過後仍保留，可靠無更新才清除。
- [x] 4.4 在 `ui/main/MainActivity.kt` 於首個主要畫面完成後觸發到期的靜默檢查，並只在 resumed、可安全展示且提醒條件成立時交付不可取消 Dialog；背景完成時先持久化並在返回前台恢復交付。
- [x] 4.5 接入 flexible update 啟動、install state listener 與生命週期清理；只有用戶按「前往更新」才啟動 Play UI，`DOWNLOADED` 後持續顯示「重新啟動並安裝」，確認後呼叫 `completeUpdate()`，返回前台可恢復待完成狀態。
- [x] 4.6 實作 Play flexible 不可用／啟動失敗的兜底：依序嘗試明確 package 的 `market://details` 與 Play HTTPS 詳情頁；兩者失敗只顯示 Play 錯誤，不切換網站。
- [x] 4.7 新增不含個人資料的結構化 `AppUpdate` 本機診斷，記錄 Play availability／versionCode／errorCode、初始渠道、渠道決策與失敗類型。

## 5. 設定頁、提醒介面與三語文案

- [x] 5.1 修改 `app/src/main/res/layout/fragment_settings.xml`，為「檢查更新」加入可本地化狀態摘要、標題旁無數字小紅點及獨立 content description，保持 48dp 觸控目標並讓資訊不只依賴顏色。
- [x] 5.2 修改 `ui/main/SettingsFragment.kt`，移除 `unsupported_check_update` Toast，觀察可靠快照並展示尚未檢查、檢查中、最新、有更新、稍後、略過、無快照失敗及保留快照失敗；點擊列發起不受 24 小時、3 天、defer 或 skip 限制的手動檢查。
- [x] 5.3 實作不可按返回鍵或外部取消的三操作更新 Dialog，按鈕為「前往更新／稍後提醒／略過此版本」；稍後與前往更新均寫入同版本 3 天 defer，略過只記錄目前 available versionCode。
- [x] 5.4 在 `values/strings.xml`、`values-b+zh+Hans/strings.xml`、`values-en/strings.xml` 提供自然香港繁體、獨立簡體與英文的狀態、Dialog、錯誤、Play 下載完成及無障礙文案，並以字串契約測試確認無 XML／Kotlin 硬編碼可見文字。
- [x] 5.5 更新 `AppSettingsSupportContractTest` 及相關 layout／short-text contract，斷言應用評分仍顯示不支援 Toast，而檢查更新已連接新能力、小紅點、摘要與三語資源。
- [x] 5.6 新增 Debug 不支援與 AppNotOwned 的三語可操作 Dialog；手動失敗不顯示舊「已是最新」，自動失敗不覆蓋可靠摘要。

## 6. 自動化驗證

- [x] 6.1 新增 coordinator 端到端 JVM 測試，以 fake Play、網站、package probe、store 及 clock 驗證所有渠道分流、24 小時節流、手動繞過、3 天提醒、defer、skip、新版本重置及失敗保留快照。
- [x] 6.2 新增 instrumentation 測試驗證設定頁各狀態與小紅點、三按鈕 Dialog 不可由返回鍵／外部取消、旋轉或 recreation 後恢復，以及 flexible 已下載提示的前台恢復。
- [x] 6.3 運行更新相關 JVM 測試及 `./gradlew build`，確認 Kotlin 編譯、unit tests、lint 與 debug／release assemble 全部通過，並記錄任何環境限制。
- [x] 6.4 新增網站渠道整合回歸測試，把已部署 metadata 同形 JSON 經 parser、versionCode 判斷及 3 天 policy 串接，覆蓋無 `applicationId`、相對 `downloadUrl` 與邊界時間。
- [x] 6.5 重新運行更新相關 JVM 測試及 `./gradlew build`，確認本次契約修正未破壞 Kotlin 編譯、unit tests、lint 與 debug／release assemble。
- [x] 6.6 以 JVM 與 UI 契約測試覆蓋 Debug 短路、AppNotOwned 網站矩陣、歷史快照保留、手動／自動摘要及 Play 詳情頁兜底。

## 7. 裝置與發布鏈驗收

- [x] 7.1 在無可用 Google Play 的 API 25 與 API 30+ 模擬器驗證：非 Play 安裝走三語網站頁、Play 初始安裝只顯示 Play 暫不可用，且 App 不請求未知來源安裝權限。
- [ ] 7.2 使用 Google Play internal test／Internal App Sharing、已擁有 App 的帳號及較高 `versionCode` 真實驗證資格判斷、flexible 下載、取消／返回、下載完成、`completeUpdate()` 與升級後清除小紅點；mock 結果不得取代此門檻。
- [x] 7.3 人工驗證繁體／簡體／英文 × 淺／深色、360dp、font scale 1.0／1.3／2.0 與 TalkBack，確認設定摘要、小紅點、三個 Dialog 操作及下載完成提示不裁切且朗讀完整。
- [x] 7.4 已按固定順序驗證網站 v11 正式包：metadata 為 `versionCode=11`、`versionName=1.0`、`sizeBytes=6094814`，下載 APK 的 application ID 為 `com.golink.busiscoming`，Play app signing SHA-256 為 `33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58`，且響應使用 `Cache-Control: no-store`。
- [x] 7.5 以確定性測試驗證 `ERROR_APP_NOT_OWNED` 網站矩陣：只有較高版本形成 Play 渠道可靠更新，相等、較低、網絡或非法資料均保留 AppNotOwned；網站 v11 APK、metadata 與發佈鏈已完成驗證，真實帳號重現 AppNotOwned 屬可選補充證據，不取代 7.2 的 IAS flexible flow 門檻。
- [x] 7.6 對已部署 metadata endpoint 做只讀線上契約核對，確認 HTTP 200、`Cache-Control: no-store`、無 `applicationId`、相對 `downloadUrl=/api/downloads/android/latest`、正整數版本／大小與合法日期；線上檢查不加入一般單元測試以避免網絡造成 CI 不穩定。

## 8. 文件與提交

- [x] 8.1 更新 `README.md` 或對應 `docs/`，記錄雙渠道權威規則、本機 24 小時／3 天策略、網站 metadata 契約、三語入口及 Play signed universal APK 發佈流程，不把 upload key 當正式網站簽名。
- [x] 8.2 在 `docs/technical-debt.md` 記錄網站強制模式已刪除、目前 Debug 行為、v11 發佈鏈證據、IAS 剩餘門檻與關閉條件；同步更新本 change 與更新檢查說明。
- [x] 8.3 更新 `docs/app-update-check.md`，使 runtime metadata 欄位、無 `applicationId` 理由、相對 `downloadUrl` 白名單與線上驗證證據和實作一致。
- [x] 8.4 核對本 change 的 requirement／scenario 均有實作或驗證證據，同步勾選完成任務；執行 `git status --short` 與 staged diff 檢查，避開既有無關改動後依專案規則建立單一清晰 conventional commit。
