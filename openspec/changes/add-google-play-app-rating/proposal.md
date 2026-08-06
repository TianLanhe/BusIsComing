## Why

設定頁目前已有「應用評分」入口，但只會顯示暫不支援提示，無法把願意評分的使用者帶到 BusIsComing 的 Google Play 商品詳情頁。需要提供明確、可恢復且不誤導的商店入口，同時正確處理 Google Play 已停用、缺失或無法啟動的裝置狀態。

## What Changes

- 把設定頁「應用評分」改為始終嘗試使用官方 Google Play App 打開 BusIsComing 商品詳情頁；不使用 Play In-App Review，也不追蹤或推測使用者是否完成評分。
- 在啟動前區分 Google Play 可用、已安裝但停用、未安裝，以及已啟用但商品 Intent 無法解析／啟動等狀態，為每個狀態提供目前語言的明確回饋與恢復操作。
- Google Play 已停用時引導至其系統應用詳情頁；未安裝時只提供 Google 官方安裝／恢復說明；其他不可用狀態引導至 BusIsComing 的系統應用詳情頁。
- 從系統設定或安裝說明返回後不自動打開商品詳情頁；使用者須再次點擊「應用評分」。
- 不在 Google Play 缺失或失敗時靜默降級到瀏覽器商品頁、不導向第三方 APK、不新增安裝權限，並保留既有「檢查更新」行為不變。

## Capabilities

### New Capabilities

- `google-play-app-rating`: 定義 Google Play 商品詳情頁導向、Play 可用性分類、各失敗／恢復分支、返回行為及評分隱私邊界。

### Modified Capabilities

- `app-settings-support`: 移除「應用評分僅顯示暫不支援提示」的既有要求，改由 `google-play-app-rating` 提供實際商店導向與狀態回饋，同時保留檢查更新能力的既有契約。

## Impact

- **前置基線**：`add-app-update-check` 亦修改 `app-settings-support` 的同一既有 Requirement；本 change 直接以其目前已實作代碼、測試及 active delta 為基線，本次不要求先完成真實 Play 人工驗證、同步或歸檔。評分實作不得改變更新功能的 Play／網站權威與 fallback。
- **Android 代碼**：影響 `SettingsFragment`、設定支援 action、Google Play package probe／外部 Intent 導向及相應狀態 model；UI 只負責觸發與呈現，package／Intent 判斷集中於可測試元件。
- **Manifest 與依賴**：沿用 `com.android.vending` package visibility；不新增 Play Core In-App Review、`QUERY_ALL_PACKAGES`、`REQUEST_INSTALL_PACKAGES` 或第三方商店依賴。
- **外部系統**：依賴 Google Play package、BusIsComing 商品 ID、Android 系統應用詳情頁與 Google 官方說明 URL；Intent 不可解析或啟動例外均須留在設定頁並可恢復。
- **相容性與驗證**：不修改 SQLite、已保存行程、查詢、監控或更新狀態；需覆蓋 Play 啟用／停用／缺失／不可解析／啟動失敗、返回不自動續辦、三語文案及可存取對話框，且不得把「已打開商品頁」宣稱為「已完成評分」。
