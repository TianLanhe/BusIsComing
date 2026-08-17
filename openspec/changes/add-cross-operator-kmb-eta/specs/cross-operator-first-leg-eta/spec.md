## ADDED Requirements

### Requirement: 依匹配 winner 查詢 KMB 或 LWB 首程 ETA
系統 SHALL 以已通過 P2P 上落車門禁的 winner stop ID、route 及 service type 查詢 KMB Data API，並以響應中的 `co` 保留九巴或龍運真實身份。

#### Scenario: 查詢有效 winner 的上車站
- **WHEN** 首程具有有效的 KMB／LWB winner 及已映射 boarding stop
- **THEN** 系統 SHALL 請求 `/v1/transport/kmb/eta/{stop_id}/{route}/{service_type}`
- **AND** 上車站身份 SHALL 由請求 URL 中的 `stop_id` 與已映射 boarding stop 一致來保證
- **AND** 系統 SHALL 只接受 `co`、route、dir、service type 與 winner 一致且 ETA 可解析的記錄

#### Scenario: ETA 響應記錄不包含 stop 欄位
- **WHEN** KMB Data API 以請求 URL 中的 `stop_id` 返回該站 ETA
- **AND** 響應內的 ETA 記錄不包含 `stop` 欄位
- **THEN** 系統 SHALL 依 URL 綁定的站點身份繼續解析有效記錄
- **AND** 系統 SHALL NOT 因響應缺少 `stop` 而丟棄該站全部 KMB／LWB ETA

#### Scenario: 響應記錄由龍運營運
- **WHEN** 有效 ETA 記錄的 `co` 為 `LWB`
- **THEN** 系統 SHALL 把該班次的 operator 保存為 LWB
- **AND** 系統 SHALL NOT 因 endpoint 路徑包含 `/kmb/` 而把該班次標記為 KMB

#### Scenario: 響應營運商或變體不一致
- **WHEN** ETA 記錄的 `co` 未知或 route、dir、service type 任一欄位不符 winner
- **THEN** 系統 SHALL 忽略該記錄
- **AND** 系統 SHALL NOT 猜測其營運商或路線變體

### Requirement: 合併全部有效跨營運商首程班次
系統 SHALL 把 CTB 與適用的 KMB／LWB 有效 arrivals 合併為同一結構化首程 ETA 結果，保留每筆 operator、來源內容及時間戳，且不限制為前三班。

#### Scenario: 兩個營運商均返回班次
- **WHEN** CTB 與 KMB／LWB 均返回一筆或以上有效 ETA
- **THEN** 系統 SHALL 返回雙方全部有效班次
- **AND** 系統 SHALL 按絕對 ETA 時間升序排列
- **AND** 系統 SHALL 在完全相同 ETA 時以 operator code 及來源 sequence 穩定排序
- **AND** 系統 SHALL 在合併後重新編排展示班序

#### Scenario: 不同營運商返回相同到站時間
- **WHEN** CTB 與 KMB／LWB 各有一筆 ETA 時間相同
- **THEN** 系統 SHALL 保留兩筆班次
- **AND** 系統 SHALL NOT 跨營運商去重

#### Scenario: 合併班次超過三筆
- **WHEN** 全部來源合計返回超過三筆有效 ETA
- **THEN** 聚合結果 SHALL 保留排序後的全部有效班次

#### Scenario: 保留每筆來源語義
- **WHEN** 任一有效 ETA 包含目的地、備註、來源 sequence 或來源 timestamp
- **THEN** 聚合結果 SHALL 保留可用原始語義及結構化 operator
- **AND** 自有 UI 文案 SHALL 以查詢開始時的 App 語言快照格式化

### Requirement: 跨營運商 ETA 支援漸進結果與部分失敗
系統 SHALL 在映射或第二營運商 ETA 尚未完成時先交付可用 Citybus 結果，並以結構化狀態區分有班次、確定空結果、技術失敗及未啟用跨營運商。

#### Scenario: 首次匹配尚未完成
- **WHEN** Citybus ETA 已返回
- **AND** CTB route slice、DP 或 KMB／LWB ETA 尚未完成
- **THEN** 系統 SHALL 先交付 Citybus 結果
- **AND** 系統 SHALL 在同一有效 query generation 內漸進加入後到的跨營運商班次

#### Scenario: 一個適用來源失敗但另一來源有班次
- **WHEN** 任一適用來源返回一筆或以上有效 arrival
- **AND** 另一適用來源技術失敗或返回空結果
- **THEN** 系統 SHALL 返回包含已知班次的 `Available`
- **AND** 系統 SHALL 保留各來源成功、空或故障診斷

#### Scenario: 所有適用來源成功但均無班次
- **WHEN** 所有適用來源均成功完成
- **AND** 所有來源均沒有有效 arrival
- **THEN** 系統 SHALL 返回 `NoArrivals`

#### Scenario: 沒有班次且至少一個適用來源失敗
- **WHEN** 目前沒有任何有效 arrival
- **AND** 至少一個適用來源技術失敗
- **THEN** 系統 SHALL 返回 `Unavailable`
- **AND** 系統 SHALL NOT 把未知結果表達為暫無車輛

#### Scenario: 跨營運商路徑未啟用
- **WHEN** GTFS 不適用、匹配為 `NO_MATCH` 或 P2P 上落車門禁失敗
- **THEN** 系統 SHALL 保持既有 Citybus ETA 結果語義
- **AND** 系統 SHALL 保留結構化未啟用原因供診斷

#### Scenario: 舊 generation 或舊 snapshot 結果晚到
- **WHEN** ETA 結果的 route result identity、query generation、語言版本或 snapshot identity 已過期
- **THEN** 系統 SHALL 忽略該結果
- **AND** 系統 SHALL NOT 覆蓋目前路線或監控狀態

### Requirement: 所有首程 ETA consumer 使用一致的聚合結果
系統 SHALL 讓路線結果、全屏詳情、前台自動刷新及通知監控使用同一跨營運商首程 ETA 契約，避免各 consumer 各自產生不同映射或排序。

#### Scenario: 路線結果與詳情收到相同來源資料
- **WHEN** 路線結果與全屏詳情查詢相同首程及同一有效資料版本
- **THEN** 兩者 SHALL 使用相同的有效 arrival 集合與排序

#### Scenario: 自動刷新或監控判斷下一班
- **WHEN** 自動刷新或前台監控需要第一及第二班 ETA
- **THEN** 系統 SHALL 使用合併排序後的第一及第二班
- **AND** 現有刷新、通知與停止策略 SHALL 以該聚合結果執行

#### Scenario: 非詳情 consumer 展示營運商
- **WHEN** 路線卡、通知或 TTS 使用合併後 ETA
- **THEN** 它們 SHALL NOT 在本版本新增營運商標籤或播報
- **AND** ETA 詳情 SHALL 仍可取得每筆結構化 operator

### Requirement: 真實雙營運商見證可被核對及重放
系統 SHALL 提供明確 opt-in 的真實 App 驗證，動態尋找同一首程 CTB 與 KMB／LWB 均有 ETA 的見證，並以獨立 oracle 核對展示結果。

#### Scenario: 真實查詢找到雙營運商見證
- **WHEN** opt-in instrumentation 在受限候選與時間窗口內找到雙方均有至少一筆 ETA 的首程
- **THEN** 系統 SHALL 以同一次 repository 結果展示真實 ETA 詳情
- **AND** 證據 SHALL 記錄路線、方向、兩方 stop ID、co、service type、DP cost、資料版本、請求時間、原始響應、UI hierarchy 及截圖
- **AND** 獨立 oracle SHALL 核對 UI 的全部行數、operator、排序與絕對到站時間

#### Scenario: 受限窗口沒有雙營運商班次
- **WHEN** 真實驗證在受限窗口內沒有找到雙方均有 ETA 的見證
- **THEN** 驗證 SHALL 報告 skipped 或 inconclusive 及未取得實時雙營運商證據
- **AND** 系統 SHALL NOT 把單方班次或沒有班次報告為實證成功

#### Scenario: 保存真實響應為 fixture
- **WHEN** 已取得可核對的真實雙營運商見證
- **THEN** 系統 SHALL 保存帶來源時間與內容 hash 的去敏 fixture 及固定 clock
- **AND** 日常測試 SHALL 可重放相同行數、排序與營運商展示
- **AND** fixture 通過 SHALL NOT 被描述為取代至少一次 live 見證
