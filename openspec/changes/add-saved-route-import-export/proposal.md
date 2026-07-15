## Why

BusIsComing 的常用路線目前只能保存在單一裝置，使用者無法把已設定的起終點路線交給另一位使用者匯入，只能逐條重新建立。現在設定頁與本機路線資料模型都已穩定，適合加入具版本、預覽及原子寫入保護的檔案交換能力，同時避免把使用統計或整個 SQLite 資料庫暴露出去。

## What Changes

- 在 `設定` 頁新增 `路線資料` 分組與單一 `匯入與匯出常用路線` item，打開專用二級頁。
- 支援將全部常用路線匯出為未加密、版本化 JSON 內容的 `.bicroutes` 檔案；使用系統保存位置選擇器，不直接開啟分享面板。
- 每次匯出前提示檔案包含全部路線地點及精確座標；沒有常用路線時禁止匯出。
- 支援從系統文件選擇器讀取 `.bicroutes`，完整校驗後顯示檔名、路線名稱清單、重複數及合併／取代影響。
- 匯入提供 `合併匯入` 與 `取代現有路線`；完全重複路線跳過，取代必須二次確認刪除及匯入數量。
- 所有匯入使用單一 SQLite transaction；任何解析、校驗或寫入失敗均不留下部分資料，取代失敗必須還原原有路線。
- 檔案不包含 SQLite id、使用次數、最近使用時間或本機時間戳；合併保留既有統計，新匯入路線從 0 開始，取代後全部路線從 0 開始。
- 檔案限制為最多 2 MiB、1 至 500 條路線；0 條路線、未知版本、非法欄位或任一非法路線均整份拒絕。
- 修正早期有效規格仍允許三要素完全重複路線、但目前 App 與較新規格已禁止重複的矛盾；不改變目前手動新增或編輯的實際行為。
- 本期不提供部分路線匯出、直接分享、密碼或加密、雲端／帳號同步、SQLite 備份，也不匯入查詢結果、ETA、監控 session 或定位狀態。

## Capabilities

### New Capabilities

- `saved-route-transfer`: 定義設定入口、專用頁、`.bicroutes` 版本 1 協議、全部匯出、匯入預覽、合併／取代、重複與使用統計語義、原子性、限制、隱私、錯誤處理及狀態恢復。

### Modified Capabilities

- `route-config-management`: 移除早期 MVP「允許完全重複路線配置」要求，使有效規格與目前禁止三要素完全重複的 App 行為及 `route-management-actions` 規格一致。

## Impact

- 受影響 UI：`ui/settings/SettingsActivity`、`activity_settings.xml`、設定相關字串／主題，並新增路線傳輸 Activity、版面與 Manifest 註冊。
- 受影響資料層：新增純 Kotlin 路線傳輸模型及 codec；擴充 `RouteConfigRepository` 的批量合併／取代 transaction，不變更 SQLite schema。
- Android 系統整合：使用 Storage Access Framework 的 `CreateDocument`／`OpenDocument`；不新增外部儲存權限，不要求永久 URI 存取權。
- 相容性：現有常用路線不會自動變更；只有使用者確認匯入後才寫入。未知格式／版本拒絕匯入；版本 1 只接受明確欄位。
- 外部系統：不修改 Citybus、DATA.GOV.HK、Google Geocoding、ETA、通知監控或任何網路接口，也不新增第三方服務。
- 驗證：新增 codec／匯入規劃 JVM 測試、Repository transaction instrumentation 測試、設定入口與匯入匯出流程 UI／contract 測試；檢查取消、失敗回滾、旋轉、大字體、長名稱、TalkBack 語義及 Manifest 無新增儲存權限，最終執行 `./gradlew build` 與 OpenSpec strict validation。
