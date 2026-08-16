## ADDED Requirements

### Requirement: GTFS 只為已知聯營路線開啟跨營運商匹配
系統 SHALL 只在 active GTFS 快照把相同公開路線號標記為 `KMB+CTB` 或 `LWB+CTB` 時啟用 CTB 對 KMB／LWB 的路線與站點匹配。

#### Scenario: 九巴與城巴聯營路線
- **WHEN** GTFS 中相同公開路線號的 `agency_id` 為 `KMB+CTB`
- **THEN** 系統 SHALL 允許把該路線的 CTB 方向與全部 KMB 變體進行匹配

#### Scenario: 龍運與城巴聯營路線
- **WHEN** GTFS 中相同公開路線號的 `agency_id` 為 `LWB+CTB`
- **THEN** 系統 SHALL 允許把該路線的 CTB 方向與全部 LWB 變體進行匹配

#### Scenario: 路線未通過聯營門禁
- **WHEN** GTFS 沒有把該公開路線號標記為上述任一聯營 agency
- **THEN** 系統 SHALL NOT 懶載入該路線的 CTB route-stop 以執行 DP
- **AND** 首程 ETA SHALL 維持 Citybus-only 行為

### Requirement: 以座標全候選雙邊 DP 選擇營運商路線變體
系統 SHALL 對同路線號所有符合聯營營運商的 `co + bound + service_type` 變體執行雙邊序列 DP，並僅以站點經緯度距離及跳站成本決定 winner。

#### Scenario: 建立全部候選變體
- **WHEN** 一個有效 CTB `route + direction` 站序準備匹配
- **THEN** 系統 SHALL 列舉 active snapshot 中相同 route 的全部目標營運商 `co + bound + service_type` 站序
- **AND** 系統 SHALL NOT 以方向名稱、首末站、站名、route long name 或距離包圍盒預篩選候選

#### Scenario: 計算一個候選的 DP cost
- **WHEN** CTB 站序長度為 m 且候選站序長度為 n
- **THEN** 對角步驟 cost SHALL 為兩站 Haversine 米數
- **AND** 任一側跳站步驟 cost SHALL 為 `G=100m`
- **AND** 候選正規化 cost SHALL 為完整 DP raw cost 除以 `max(m,n)`
- **AND** 任一站對距離 SHALL NOT 因局部硬門禁被禁止參與最優路徑

#### Scenario: 選擇穩定 winner
- **WHEN** 全部候選完成計算
- **THEN** 系統 SHALL 依正規化 cost、raw cost、co、bound、數值化 service type 及原 service type 字串依序穩定排序
- **AND** 系統 SHALL 選擇排序後最低 cost 的單一候選

#### Scenario: winner 通過路線門禁
- **WHEN** 最低候選的正規化 cost 小於或等於 `T=46`
- **THEN** 系統 SHALL 接受該候選為匹配路線
- **AND** 系統 SHALL NOT 要求其領先第二名固定差值
- **AND** 系統 SHALL NOT 產生高、中、低等站點或路線置信度分類

#### Scenario: winner 未通過路線門禁
- **WHEN** 最低候選的正規化 cost 大於 `T=46`
- **THEN** 系統 SHALL 把該完整輸入計算記為確定性 `NO_MATCH`
- **AND** 系統 SHALL NOT 使用最近候選建立跨營運商 ETA query

### Requirement: DP 回溯建立保守的站點映射
系統 SHALL 只以 winner DP 最優路徑中的對角步驟建立 CTB stop ID 到同一 KMB／LWB 變體 stop ID 的映射。

#### Scenario: 回溯遇到對角步驟
- **WHEN** DP 最優路徑由 CTB stop 與 KMB／LWB stop 的對角步驟前進
- **THEN** 系統 SHALL 為該站對保存映射及 winner 中的 sequence

#### Scenario: 回溯遇到插入或刪除步驟
- **WHEN** DP 最優路徑在任一側以跳站步驟前進
- **THEN** 系統 SHALL NOT 為該 gap 猜測或產生站點映射

#### Scenario: P2P 上落車完整映射且順序有效
- **WHEN** Citybus P2P stop map 提供首程 CTB boarding 與 alighting stop ID
- **AND** 兩站均在同一 winner 中具有唯一映射
- **AND** winner 的 boarding sequence 小於 alighting sequence
- **THEN** 系統 SHALL 允許以該 winner 的 co、bound、service type 及兩個 stop 建立跨營運商首程 ETA query

#### Scenario: P2P 映射不完整或順序無效
- **WHEN** 上落車任一站沒有對角映射、對應不唯一或 winner 中的順序相反
- **THEN** 系統 SHALL NOT 查詢該 KMB／LWB 變體的首程 ETA
- **AND** 系統 SHALL NOT 使用同名站、最近站、另一方向或另一 service type 補足映射

### Requirement: 匹配 cache 依語義輸入精準失效
系統 SHALL 以參與 DP 的路線站序語義、算法版本、`G` 與 `T` 保存可重現的 `MATCHED` 或確定性 `NO_MATCH`，並防止過期計算寫回。

#### Scenario: 只有非語義 metadata 改變
- **WHEN** 站名、generated timestamp、原始行順序或其他不參與 DP 的欄位改變
- **AND** co、route、direction／bound、service type、seq、stop ID 及座標均未改變
- **THEN** 系統 SHALL 復用原匹配 cache

#### Scenario: 一條路線的 DP 語義改變
- **WHEN** CTB route slice 或 KMB／LWB 候選中任一參與 DP 的站序欄位改變
- **THEN** 系統 SHALL 只令受影響路線的匹配 cache 失效
- **AND** 其他語義未變路線 SHALL 保持可復用

#### Scenario: 算法常數或版本改變
- **WHEN** 算法版本、`G` 或 `T` 改變
- **THEN** 系統 SHALL 令全部舊匹配 cache 失效

#### Scenario: 暫時性失敗阻止匹配
- **WHEN** 網絡、解析、取消、資料庫故障或不完整輸入阻止 DP 得出完整結果
- **THEN** 系統 SHALL NOT 把該情況緩存為 `NO_MATCH`

#### Scenario: DP 完成前輸入版本已改變
- **WHEN** DP 完成時 active snapshot ID 或 CTB route slice fingerprint 已不同於開始計算時的值
- **THEN** 系統 SHALL 丟棄舊計算結果而不得寫入新版本 cache
- **AND** 仍有有效 consumer 時系統 SHALL 最多以新輸入自動重算一次

