# 技術債清單

本文件只記錄已由代碼或外部證據確認、但經工程決策延期的問題。每項必須描述目前影響、延期邊界、推薦方向與可驗證的關閉條件；正式處理時另建 OpenSpec change，不在已完成 change 中追加實現任務。

## TD-001：簡體路線卡站名可能顯示繁體

- **狀態**：已確認，主動延期
- **首次記錄**：2026-07-18
- **最近核對**：2026-08-04
- **影響範圍**：簡體中文下，路線卡上車站與下車站名稱
- **目前影響**：路線身份、stop id、ETA 與查詢仍正確，但卡片可能展示 Citybus `showstops2` 返回的繁體站名，與 App 實際語言不一致。

### 已驗證根因與現況

- App 已按簡體向 Citybus mobile 傳遞 `l=2`；對既有 8X、N118 等 P2P 樣本，`showstops2` 的 `l=2` 仍返回與繁體相同的站名。
- 生產路線卡以 P2P rawInfo 對齊 `showstops2`，直接展示其 `displayName`。這個來源對路線分支與 sequence 最可靠，但不能保證簡體名稱。
- DATA.GOV.HK `GET /v2/transport/citybus/stop/{stopId}` 可返回 `name_tc`、`name_sc`、`name_en`。
- 倉庫已有 `CitybusStopNameResolver` 原型，可按 stop id 讀上述 endpoint；它沒有接入任何生產調用點，目前按 `(company, stopId, lang)` 快取單一名稱，未實現跨卡片 in-flight 去重、並發限制、完整三語 record cache 或失敗語義，因此不能視為本技術債已解決。

### 延期邊界

- 不以機器繁簡轉換取代官方 `name_sc`，也不猜測未公開的 Citybus mobile 語言參數。
- 不改用公開 `route-stop` 站序取代 P2P `showstops2`；後者仍是路線分支、leg、sequence 與 stop id 的身份來源。
- DATA.GOV.HK 名稱補齊失敗時保留 Citybus 原名，不能隱藏整條站點資訊，也不能阻塞 ETA。

### 推薦方向

1. 由 `showstops2` 確定上落車 stop id，再只為需要補齊的顯示名稱查詢 DATA.GOV.HK stop endpoint。
2. 以 stop id 快取包含三語欄位與 `data_timestamp` 的完整站點 record，不按語言重複保存單一字串。
3. 對跨卡片 stop id 去重、合併相同 in-flight 請求並限制並發；名稱補齊與 ETA 並行。
4. 簡體按 `name_sc → name_tc → name_en` 單欄位回退並保留實際欄位語言；整體請求失敗時暫時展示 Citybus 原名。
5. 成功 record 使用有限 TTL；HTTP 失敗、空 `data`、解析失敗或 fallback 顯示不得作為簡體成功結果長期快取。

### 關閉條件

- 真實 P2P 樣本保持正確路線分支、上落車 sequence 與 stop id；簡體優先展示真實 `name_sc`。
- 相同 stop id 的請求可去重，並有明確並發上限；語言切換不產生跨語言 cache 污染或重複網絡風暴。
- timeout、非 2xx、HTTP 200 空 `data`、解析錯誤及欄位缺失均有回歸測試，失敗時路線卡仍可用。
- 名稱補齊不延遲 ETA 交付；完成真實裝置三語與網絡降級驗證。
- 未接線或不符合最終 cache／failure 契約的原型被接線重構或刪除，不留下第二套漂移實現。

## TD-002：Google Play flexible update 真實裝置驗收

- **狀態**：實現與確定性測試已完成，等待真實 IAS flexible flow
- **首次記錄**：2026-07-24
- **最近核對**：2026-08-04
- **影響範圍**：App 自動／手動更新、Play flexible update 與發布驗收
- **目前影響**：正常 Release 已使用 Play 優先分流，Debug 不訪問 provider；但在已擁有 App 的真實 Play 帳號與裝置上，端到端 flexible update 尚未形成關閉證據。目前 `versionCode=12`，不能再把歷史 v10→v11 寫成待測固定版本對。

### 已驗證狀態

- 自動 24 小時節流、手動繞過、同一 in-flight 工作、初始安裝來源、Play 可用性與 provider 結果矩陣都有確定性測試。
- Play 有更新時，網站只在 versionCode 精確一致時補 versionName；不一致或失敗仍保留通用可靠更新，不以 versionCode 冒充 versionName。
- Play 無更新不訪問網站；`ERROR_APP_NOT_OWNED` 只有網站 versionCode 較高時形成 Play 渠道可靠更新；臨時 Play 失敗不改走網站。
- 2026-08-03 曾驗證網站 v11 metadata、signed universal APK、application ID 與 Play app signing key 一致。這是歷史發布鏈證據，不代表目前 App 或網站版本，也不替代 flexible flow。
- OpenSpec `add-app-update-check` 目前只餘任務 7.2 未完成。

### 延期邊界

- fake source、單元測試與 instrumentation 可驗證決策、狀態和 UI，但不能替代 Google Play 的帳號擁有權、軌道資格、下載及安裝流程。
- 不以 Debug APK、直接 adb 安裝或網站 APK 取代 Internal App Sharing／internal test 的 Play 交付證據。
- 真實重現 `ERROR_APP_NOT_OWNED` 只是可選補充；關閉硬門檻是可正常擁有 App 的帳號完成 flexible update。

### 剩餘驗收

1. 在同一 Internal App Sharing 或 internal test 渠道準備 Play-signed 的較低 versionCode 與較高 versionCode。
2. 用目標帳號從 Play 安裝較低版本，記錄裝置、軌道、版本與帳號擁有狀態。
3. 開放較高版本，從 App 內檢查啟動 flexible update。
4. 驗證取消／返回、重新進入、下載完成、前台恢復、`completeUpdate()`、升級及小紅點／已下載狀態清理。
5. 把證據記入該 OpenSpec change／archive，勾選任務 7.2，再關閉本條目。

### 關閉條件

- 同一渠道的較低→較高版本，在合適真實裝置與已擁有 App 的帳號上完成資格判斷和 flexible update。
- 取消／返回、下載完成、重新啟動安裝及升級後狀態清理均有真實證據。
- `openspec/changes/add-app-update-check/tasks.md` 的 7.2 已完成，且對應 change 可以歸檔。

## TD-003：Android 備份與裝置轉移策略尚未定義

- **狀態**：已確認，待產品與資料治理決策
- **記錄日期**：2026-08-04
- **影響範圍**：常用行程、長期置頂、偏好、權限提示狀態、監控 session、更新狀態與快捷方式提示狀態
- **目前影響**：Manifest 設定 `android:allowBackup="true"`，並引用 `data_extraction_rules.xml` 與 `backup_rules.xml`；兩份文件仍是 Android Studio 範本，沒有任何明確 include／exclude。實際 cloud backup 與 device transfer 範圍因平台版本與預設規則而異，沒有形成可審核的產品契約。

### 風險

- SQLite 中的常用行程與長期置頂可能是用戶希望遷移的資料，但包含自訂名稱、地點與精確座標，需要明確隱私選擇。
- 語言與外觀適合恢復；位置權限提示、更新嘗試／skip、監控 session、TTS／快捷方式運行狀態可能不應跨裝置或跨安裝恢復。
- 未定義策略會令 Android 12+ data extraction 與舊版 Auto Backup 行為難以保持一致，也無法可靠測試升級、重裝與裝置轉移。
- `.bicroutes` 已提供用戶可控的行程匯入匯出，但它不會自動決定系統備份應開啟、關閉或只包含部分資料。

### 推薦方向

1. 先按資料類型分類：用戶內容、可重建偏好、裝置／安裝綁定狀態、短期 session、cache／臨時狀態。
2. 對 cloud backup 與 device transfer 分別決定 include／exclude；同時維護 API 31+ `data-extraction-rules` 和舊版 `full-backup-content`。
3. 如允許備份精確地點，需在私隱政策與產品說明中明確；如不允許，保留 `.bicroutes` 作用戶主動遷移路徑。
4. 為 SQLite schema 版本、SharedPreferences 缺鍵／舊鍵與被排除的 session 設計恢復測試，不假設整個 app data 可以原樣還原。

### 關閉條件

- 已書面決定每個 SQLite／SharedPreferences 文件在 cloud backup、device transfer 及本機匯出中的歸屬與理由。
- 兩套 Android backup rule 明確且語義一致，不再保留範本 TODO。
- 至少驗證一個支援的舊 API 路徑與一個 API 31+ 路徑：應恢復資料正確恢復，被排除的 session／安裝狀態不復活，升級不破壞 SQLite。
- README／私隱說明及相關 OpenSpec 與最終產品決策一致。
