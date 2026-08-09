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

## TD-004：路線詳情各資料源的時間口徑尚未統一

- **狀態**：已確認，主動延期
- **記錄日期**：2026-08-07
- **影響範圍**：路線詳情摘要、乘車段計劃時間、CSDI 步行分段時間與通知監控
- **目前影響**：路線總耗時及巴士段計劃時間沿用 Citybus 的公交規劃結果；地政總署 CSDI 步行分段則按服務固定 1 m/s 的 `Total_Time` 顯示約略分鐘。兩者可能採用不同步速、轉乘與等候假設，因此分段顯示值不保證可直接相加還原 Citybus 總耗時。通知監控的個人步速仍是獨立使用者設定，不會回寫路線詳情或 CSDI 查詢。

### 延期邊界

- 本次只改善步行距離、約略時間及真實軌跡，不以距離自行估算或改寫 Citybus 總耗時、預計到達、巴士段計劃時間及首程 ETA。
- 不以通知監控的個人步速修改第三方規劃結果，也不把 CSDI 固定步速宣稱為使用者的實際步行時間。
- 任一 CSDI 分段失敗時維持 Citybus 距離回退，不以部分成功分段重算完整路線時間。

### 推薦方向

1. 先定義全 App 可解釋的步速來源與優先級，區分路線比較、詳情展示及個人監控用途。
2. 引入時間依賴的公交計劃資料，明確處理候車、班次、跨午夜及轉乘銜接。
3. 在所有必要資料可靠時重算轉乘與完整行動鏈；局部資料失敗時保留來源邊界，不混合成看似精確的總時間。
4. 在 UI 清楚標示計劃時間、約略步行時間及即時 ETA 的來源與精度，保持三語及無障礙等價。

### 關閉條件

- 統一步速、時間依賴公交與轉乘重算已有獨立 OpenSpec 契約，包含跨午夜、缺班次、部分失敗及個人步速邊界。
- 摘要、各步行段、各乘車段及監控時間可追溯至明確資料源，且不會把不同口徑無標示地相加。
- 純單元測試覆蓋計算與回退，真實香港樣本覆蓋單段、多段、步行換乘及同站換乘，三語 UI 清楚標示來源與約略性。
