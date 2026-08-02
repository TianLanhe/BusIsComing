# Google Play 更新資格與錯誤語義修正設計

## 背景

App 已把 `FORCE_WEBSITE_UPDATE_CHECK` 預設值改為 `false`，正常構建會呼叫 Google Play Core 檢查更新。真機聯調時，本機安裝 `versionCode=10`，Google Play 已提供 `versionCode=11`，但設定頁顯示「目前已是最新版本」。

排查確認真機上的 v10 是由 ADB 安裝的 debuggable／test-only APK，installer 為空，並使用 Android Debug 憑證；它不符合 Google Play in-app updates 對安裝取得狀態與簽名的測試前提。裝置保存的更新狀態同時證明 Play 優先接線已生效，並非舊網站強制開關仍在作用。

現有流程還存在獨立的產品錯誤：Play 回傳 `ERROR_APP_NOT_OWNED` 後，協調器會讀取網站 metadata；只要網站版本不高於本機版本，就把結果保存為可靠的 `PLAY / UP_TO_DATE`。網站 metadata 可以證明「有較高版本」，但不能證明目前帳號、軌道、地區及裝置在 Play 沒有較高版本，因此這個結果不應顯示為「目前已是最新版本」。

Google Play v11 已在目標地區完成發佈，網站亦已上線同版本的 Play app signing key 簽署 APK。線上 metadata 與實際 APK 已核對為：application ID `com.golink.busiscoming`、`versionCode=11`、`versionName=1.0`、APK 大小 `6094814` bytes，簽名憑證 SHA-256 為 `33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58`。

## 目標

- 讓正式非 Debug 構建以 Google Play Core 對目前帳號與裝置的資格結果為主要權威。
- 防止 Debug 聯調包把無效 Play 結果顯示為「目前已是最新版本」。
- 修正 `ERROR_APP_NOT_OWNED` 的網站補充證據語義：網站只可證明有較高版本，不能證明 Play 已是最新。
- 在手動檢查無法驗證時提供清晰、可操作的 Play 恢復路徑；自動檢查保持靜默。
- 保留既有可靠更新快照、小紅點、節流、稍後提醒及略過狀態。
- 以不含個人資料的結構化本機日誌改善 Internal App Sharing 與真機排查能力。
- 刪除已完成歷史任務的 `FORCE_WEBSITE_UPDATE_CHECK` 開關及平行接線，避免正常行為再次被本機配置切換。

## 非目標

- 不在 App runtime 硬編碼 Play app signing 憑證指紋；正式簽名仍由發佈流程驗證。
- 不按 installer 是否為 Play 單獨判斷 Play 更新資格；正式網站 APK 可由非 Play 安裝，但仍應在有 Play 時優先嘗試 Play。
- 不為 `versionCode` 已遞增但 `versionName` 相同增加特殊 UI 規則；真實後續版本會同步遞增 `versionName`。
- 不新增遠端遙測、帳號識別或裝置識別。
- 不重構與更新檢查無關的 UI、網絡或資料層。

## 核心決策

### 1. 刪除網站強制開關

刪除 `FORCE_WEBSITE_UPDATE_CHECK` BuildConfig 欄位、`DisabledPlayUpdateSource`、coordinator 的 `forceWebsiteOnly` 分支及相關契約測試。App runtime 始終建立真實 `GooglePlayUpdateSource`。

刪除開關不等於刪除網站渠道。正式非 Debug 構建仍按原有渠道規則運作：裝置沒有可用 Play，且初始渠道不是 Play 時，使用網站 metadata；初始為 Play 的安裝在 Play 不可用時顯示受控錯誤。

### 2. 以可注入資格值隔離 Debug 構建

`AppUpdateRuntime` 根據構建是否 debuggable，向 coordinator 傳入可測試的 Play 檢查資格值。coordinator 不直接依賴 Android `BuildConfig`，以保持 JVM 測試能力。

Debug 構建發起更新檢查時：

- 不探測 Play package。
- 不呼叫 Play Core。
- 不呼叫網站 metadata。
- 不寫入可靠版本快照或更新小紅點。
- 回傳獨立失敗類型 `PLAY_DEBUG_BUILD_UNSUPPORTED`。
- 自動檢查仍保存嘗試時間，使 24 小時節流生效。

Internal App Sharing 使用非 debuggable 發佈構建，因此不受此限制。

### 3. 固定正式構建的 Play 優先資料流

非 Debug 構建的檢查順序如下：

1. 保存首次安裝渠道及本次嘗試時間。
2. 檢查官方 Play package 是否可用。
3. 沒有 Play 時：
   - 初始 Play 安裝回傳 `PLAY_UNAVAILABLE`。
   - 正式網站安裝或未知非 Play 安裝檢查網站 metadata。
4. 有 Play 時呼叫 Play Core：
   - `UPDATE_AVAILABLE`：保存 Play 更新快照。
   - `UPDATE_NOT_AVAILABLE`：保存 Play 權威的最新快照。
   - 暫時失敗：保留原有可靠快照，不降級到網站。
   - `ERROR_APP_NOT_OWNED`：進入網站補充證據流程。

single-flight、自動檢查 24 小時節流及目前提醒策略維持不變。

### 4. 網站在 AppNotOwned 流程中只提供正向證據

Play 回傳 `ERROR_APP_NOT_OWNED` 時，網站結果按以下矩陣處理：

| 網站結果 | 最終結果 | 操作渠道 |
| --- | --- | --- |
| `versionCode` 高於本機 | 保存可靠的更新可用快照 | Play |
| `versionCode` 等於或低於本機 | `PLAY_APP_NOT_OWNED` 失敗 | Play |
| 網絡失敗 | `PLAY_APP_NOT_OWNED` 失敗 | Play |
| metadata 無效 | `PLAY_APP_NOT_OWNED` 失敗 | Play |

網站發現較高版本時只負責證明版本存在，不提供安裝流程。因為 Play Core 沒有提供可用 `AppUpdateInfo`，用戶選擇更新時直接開啟 Play 詳情頁，不嘗試網站 APK。

### 5. 可靠快照與失敗狀態分離

失敗不得清除或覆寫已有可靠快照：

- 已可靠發現較高版本時，後續失敗繼續保留版本摘要、小紅點、defer 及 skip 狀態。
- 已有可靠 `UP_TO_DATE` 時，手動失敗在目前進程顯示失敗，不再把舊快照呈現為本次「目前已是最新版本」；可靠快照本身仍保留。
- 自動失敗不彈出 UI，也不覆蓋設定頁既有可靠摘要。
- 只有 App 完成升級，或後續可靠檢查確認目前版本已不低於可用版本，才清除更新狀態。

## 使用者介面

### 手動檢查

`PLAY_DEBUG_BUILD_UNSUPPORTED` 顯示「目前偵錯版本無法驗證 Google Play 更新」的等價三語文案；`PLAY_APP_NOT_OWNED` 顯示「暫時無法透過 Google Play 確認更新」的等價三語文案。

兩種狀態均使用可操作提示框，而非只有短暫 Toast：

- 「取消」：關閉提示並保留目前狀態。
- 「前往 Google Play」：先嘗試 `market://details?id=com.golink.busiscoming`，無處理程式時改開 Google Play HTTPS 詳情頁。

設定行仍可再次點擊重試。手動檢查失敗且沒有既有更新快照時，設定行在目前進程顯示暫時無法檢查，不顯示小紅點。

### 自動檢查

自動檢查遇到 Debug 不支援、`PLAY_APP_NOT_OWNED`、Play 暫時失敗或網站失敗時均保持靜默：不顯示 Dialog、Toast 或新小紅點。自動嘗試時間照常保存，避免每次啟動重複請求。

### 多語言與無障礙

新增或修改的設定摘要、Dialog 標題、內文、按鈕及 content description 同時提供香港繁體、獨立審校的簡體與自然英文。Dialog 沿用現有可換行／垂直 action 佈局，避免英文及大字體下擁擠。

## 診斷設計

新增小型可注入的更新診斷介面。Android 實作以統一 `AppUpdate` tag 寫入本機 logcat，fake 實作用於 JVM 測試。

允許記錄：

- Play `updateAvailability`。
- `availableVersionCode`。
- `InstallException.errorCode`。
- 初始安裝渠道。
- 最終渠道決策與失敗類型。

不得記錄 Google 帳號、使用者資料、裝置識別碼、位置或完整外部響應。原始 Play error 不額外長期寫入 SharedPreferences，也不新增遠端上報。

## 測試策略

### JVM 測試

- Debug 資格短路後，Play package probe、Play source 及網站 source 均不被呼叫。
- Debug 手動與自動檢查回傳正確失敗，且自動檢查仍受 24 小時節流。
- `AppNotOwned + 網站更高` 產生 Play 渠道更新，操作回退到 Play 詳情頁。
- `AppNotOwned + 網站相等／較低／網絡失敗／非法 metadata` 均產生 `PLAY_APP_NOT_OWNED`，且不保存 `UP_TO_DATE`。
- 失敗保留已有可靠更新、小紅點、defer 及 skip。
- 手動失敗不顯示舊「目前已是最新版本」；自動失敗不覆蓋可靠摘要。
- 結構化診斷事件包含預期欄位，且不包含額外識別資料。
- 刪除開關後，runtime 接線固定使用真實 Play source。

### UI 與契約測試

- 三語資源 key、格式參數及無障礙 content description 完整。
- Debug 不支援與 AppNotOwned 使用可操作 Dialog。
- market Intent 失敗時使用 HTTPS Play 詳情頁。
- 已有更新後檢查失敗仍保留摘要與小紅點。

### 真實裝置驗收

使用 Google Play Internal App Sharing：

1. 透過 v10 分享連結安裝支援 in-app updates 的基線版本。
2. 開啟 v11 分享連結但不安裝。
3. 回到 v10 App，手動檢查並確認識別 v11。
4. 啟動 flexible update，驗證取消／返回後 App 仍可使用且可再次發起。
5. 完成下載，確認出現「重新啟動並安裝」。
6. 確認後完成 v11 安裝。
7. 重新啟動並確認舊快照、小紅點、defer 及 skip 已清理。
8. 驗證 flexible flow 無法啟動時的 Play 詳情頁恢復路徑。

真實 `ERROR_APP_NOT_OWNED` 不作硬門檻；該分支由確定性 JVM 測試覆蓋。如可取得從未在 Play 獲取 App 的額外測試帳號，可補充真機證據，但不阻塞交付。

## 文件與技術債同步

- 更新現有 `openspec/changes/add-app-update-check` 的 proposal、design、specs 與 tasks，不建立重複 change。
- 更新 `docs/app-update-check.md`，刪除網站強制開關說明並記錄新的 AppNotOwned 證據規則。
- 更新 `docs/technical-debt.md` TD-002；只有完整 Internal App Sharing flexible flow 通過並留下版本、軌道、帳號資格與日期證據後才關閉。
- 刪除所有把 `FORCE_WEBSITE_UPDATE_CHECK` 當作目前行為或恢復手段的契約與說明。

## 遷移與提交邊界

目前工作樹把 `versionCode` 從 10 降到 9 的未提交改動只用於先前無效的 ADB 聯調。實作時恢復分支原本的 `versionCode=10`，不把降版本改動納入提交。v10 真實基線由 Internal App Sharing／Play 正確簽名製品提供。

實作完成後先運行針對性測試，再運行 `./gradlew build`。Android 程式、三語資源、OpenSpec、更新文檔與測試以清晰提交粒度提交；不提交 APK、AAB 或其他構建產物。

## 完成條件

- Debug 構建不再呼叫 Play 或網站，也不再顯示假「目前已是最新版本」。
- AppNotOwned 的網站相等、較低或失敗結果不再產生可靠 `UP_TO_DATE`。
- 網站更高時仍能發現更新，並把操作導向 Play。
- 手動不可驗證狀態具清晰三語提示與 Play 操作；自動失敗保持靜默。
- 可靠更新狀態在後續失敗時不丟失。
- `FORCE_WEBSITE_UPDATE_CHECK` 及其平行接線已完全刪除。
- 自動化測試與 `./gradlew build` 通過。
- IAS v10 → v11 flexible update 完整流程通過。
- 線上 v11 metadata 與實際 APK 的版本、大小、application ID 及 Play app signing 憑證再次核對一致。
