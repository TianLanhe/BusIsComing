## Why

目前 App 只查詢 Citybus 首程 ETA；對城巴與九巴／龍運聯營的路線，使用者會看不到另一營運商的即將到站班次。兩家公司使用不同 stop ID，不能直接共用 Citybus P2P 上車站，因此需要可更新、可失效且不猜測站點的跨營運商路線與站點映射，才能安全聚合真實 ETA。

## What Changes

- 每個香港資料日於 App 首次進入前台後，背景檢查 GTFS 聯營路線清單、KMB 全量 route／route-stop／stop 及 CTB 全量 route；以原子本機快照保存最近一次完整成功資料，失敗時繼續使用舊快照。
- 對 GTFS 標記為 `KMB+CTB` 或 `LWB+CTB` 的首程路線懶載入 CTB 雙向 route-stop 與 stop，使用經緯度距離及雙邊動態規劃，在同路線號全部 KMB `bound + service_type` 變體中選擇最低成本映射；採用 `G=100m`、正規化門禁 `T=46`，不使用站名、方向名稱或站對硬門禁。
- 按路線語義指紋與算法版本緩存成功映射及確定性無匹配結果；靜態資料更新時只令受影響路線失效，並拒絕舊快照上的過期計算寫回新快照。
- 映射有效時並行查詢 Citybus 與 KMB Data API 首程 ETA，依 `co` 保留 CTB／KMB／LWB 真實營運商來源，合併全部有效班次、按實際 ETA 排序且不跨營運商去重；單方失敗仍保留另一方結果，首次未完成映射時先展示 Citybus 結果。
- ETA 詳情 Bottom Sheet 展示完整可滾動班次列表，並以文字膠囊標示每班由城巴、九巴或龍運營運；路線卡片、通知與 TTS 第一版不增加營運商標籤。
- 在設定頁新增「路線資料庫更新檢查」，顯示五項全局資料最近一次完整成功檢查時間，並允許用戶手動加入或觸發單一更新任務；手動檢查不預先計算 DP 或批量載入 CTB 站點。
- 增加真實雙營運商 ETA 見證測試：在 App instrumentation 中以真實資料動態尋找同站同路線兩方均有 ETA 的樣本，保存可核對證據並轉為可重放 fixture；當上游當時沒有雙方班次時明確標記未取得證據，不誤報成功。
- 新增跨營運商映射長期文件，並在 `docs/technical-debt.md` 登記把靜態資料更新、DP 計算及映射快取遷移至服務端的技術債與關閉條件。
- 非目標包括：改用公開 CTB route-stop 取代 P2P stop map 的運行時上車站身份、在路線卡片展示營運商、加入新路線規劃來源、或在本版本實作服務端。

## Capabilities

### New Capabilities

- `cross-operator-static-route-data`: 定義五項全局資料的每日原子更新、CTB 路線站點懶載入、stale-while-revalidate、資料日及持久化失敗邊界。
- `cross-operator-route-stop-matching`: 定義 GTFS 聯營閘門、CTB↔KMB 雙邊 DP、門禁、站點對應、語義指紋、緩存與失效規則。
- `cross-operator-first-leg-eta`: 定義 KMB／LWB 首程 ETA 查詢、Citybus／KMB／LWB 全量班次合併、部分失敗及各 ETA consumer 的一致結果。

### Modified Capabilities

- `citybus-eta-arrivals-sheet`: 將最多三班的 Citybus 面板改為完整合併列表，加入每班營運商膠囊、可滾動高度及同步更新規則。
- `app-settings-support`: 在路線資料分組加入全局路線資料最近同步時間、手動更新、進行中與失敗回復行為。

## Impact

- **資料與存儲**：新增版本化 SQLite 靜態資料快照、CTB 懶載入站點資料、路線匹配緩存、更新狀態與 schema migration；不得破壞既有行程、置頂、偏好或匯入資料。
- **網絡來源**：新增 GTFS `routes.txt`、KMB Data API route／route-stop／stop／ETA 及 DATA.GOV.HK Citybus route／route-stop／stop；Citybus P2P stop map 仍是運行時 CTB 上車站身份的唯一來源。
- **架構**：影響 ETA model、repository／parser、App 前台更新協調、路線查詢漸進更新、全屏詳情、自動刷新、監控服務、設定頁及 ETA Bottom Sheet；網絡、SQLite、DP 與長流程不得進入 UI。
- **生命週期與失敗**：更新與懶載入均需 single-flight、取消／generation、防過期寫回、退避、部分失敗及 stale-while-revalidate；查詢不得等待每日更新或 DP 完成才展示 Citybus 核心結果。
- **本地化與 UI**：新增香港繁體、獨立簡體及自然英文的設定、狀態、城巴／九巴／龍運營運商與無障礙文案；Bottom Sheet 需在深淺色、360dp 及 font scale 1.0／1.3／2.0 下展示全部班次且可操作。
- **驗證風險**：真實雙營運商 ETA 受服務時段和即時班次影響；fixture 不能取代至少一次真實 App 見證，沒有取得見證時不得宣稱實證完成。
- **文件與後續**：新增跨營運商映射文件並更新架構、Citybus ETA、本地化及技術債文件；服務端遷移保留為明確非本版本範圍。
