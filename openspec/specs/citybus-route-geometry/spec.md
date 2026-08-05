# citybus-route-geometry Specification

## Purpose
記錄 Citybus P2P 道路幾何的最小請求、解析校正、端點驗證、分段並發、快取及失敗降級契約。

## Requirements

### Requirement: 按乘車段查詢 Citybus P2P 路線幾何
系統 SHALL 使用 P2P 乘車段的 route variant、上車站序與下車站序查詢 Citybus 道路幾何，且 SHALL NOT 為該請求附加 session 或靜態瀏覽器資料。

#### Scenario: 構造最小幾何請求
- **WHEN** 系統獲得一段 `routeVariant`、`boardingSeq` 和 `alightingSeq`
- **AND** `boardingSeq <= alightingSeq`
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/getlinep2p.php`
- **AND** 請求 SHALL 只使用業務參數 `rdv=<routeVariant>`、`start=<boardingSeq>` 和 `dest=<alightingSeq>`

#### Scenario: 幾何請求不攜帶 session 或瀏覽器 header
- **WHEN** 系統發起 `getlinep2p.php` 請求
- **THEN** 系統 SHALL NOT 顯式設置 `Cookie`、`ssid`、`sysid`、時間戳、`User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`

#### Scenario: 站序無效時不發起請求
- **WHEN** `routeVariant` 為空或 `boardingSeq > alightingSeq`
- **THEN** 系統 SHALL 將該乘車段幾何標記為不可用
- **AND** 系統 SHALL NOT 發起 `getlinep2p.php` 請求

### Requirement: 解析與驗證 Citybus 路線幾何
系統 SHALL 將 `getlinep2p.php` 回應解析為有序坐標點，並拒絕不能可靠代表該乘車段的內容。

#### Scenario: 解析有效幾何行
- **WHEN** 回應包含一行或多行 `pointId,latitude,longitude`
- **THEN** 系統 SHALL 依原始行序保存 point id、緯度和經度
- **AND** 系統 SHALL 保留至少兩個有效點後才把該回應視為可繪製幾何

#### Scenario: 忽略單一 malformed 行
- **WHEN** 回應同時包含有效行與缺少欄位、非數字或非法坐標的 malformed 行
- **THEN** 系統 SHALL 忽略 malformed 行
- **AND** 剩餘有效點少於兩個時整段幾何 SHALL 視為不可用

#### Scenario: 空回應或無有效坐標
- **WHEN** 服務返回 HTTP 成功但 body 為空或沒有至少兩個有效坐標
- **THEN** 系統 SHALL 將該乘車段幾何視為失敗
- **AND** 系統 SHALL NOT 把 HTTP 200 本身視為成功幾何

#### Scenario: 幾何端點與站點明顯不一致
- **WHEN** 路線詳情已提供可靠上下車站坐標
- **AND** 幾何首尾與對應上下車站超出可接受距離
- **THEN** 系統 SHALL 拒絕該段幾何
- **AND** 系統 SHALL 保留站點資料供地圖與時間線降級展示

### Requirement: Citybus 舊底圖幾何須對齊 Google Maps 坐標
系統 SHALL 在 Citybus 幾何 repository 邊界把 `getlinep2p.php` 的舊底圖路線坐標轉換為 Google Maps 使用的 WGS84 坐標，且 SHALL NOT 把同一校正套用到其他資料來源。

#### Scenario: 校正每個有效路線點
- **WHEN** parser 成功產生 `getlinep2p.php` 的有序原始路線點
- **THEN** repository SHALL 對每點套用 `latitude + 0.0001935197` 與 `longitude - 0.0000697374`
- **AND** 系統 SHALL 保留 point id、原始次序與點數

#### Scenario: 以校正後坐標驗證及快取
- **WHEN** repository 需要驗證幾何端點或保存成功快取
- **THEN** 系統 SHALL 使用校正後的 WGS84 路線幾何
- **AND** cache hit SHALL 回傳同一校正後結果而不重複位移

#### Scenario: 校正範圍只限 Citybus 路線幾何
- **WHEN** 地圖同時展示 Citybus 站點、查詢起終點、設備目前位置或 Google 資料
- **THEN** 系統 SHALL 保持這些坐標原值
- **AND** Google renderer SHALL NOT 對所有地圖內容套用 Citybus provider-specific 位移

### Requirement: 分段並發與進程內快取路線幾何
系統 SHALL 按乘車段獨立且 single-flight 載入幾何、限制並發，並對成功結果使用一天進程內快取；同一頁的詳情到達 SHALL NOT 取消並重發相同 geometry key。

#### Scenario: 多段路線並發載入
- **WHEN** 一條候選路線包含多個可查詢乘車段
- **THEN** 系統 SHALL 允許各段幾何獨立完成與展示
- **AND** 同一時間進行中的幾何 HTTP 請求 SHALL 不超過 3 個

#### Scenario: 冷快取同一 key 只發起一次
- **WHEN** 詳情頁首次開啟且某一 `routeVariant + boardingSeq + alightingSeq` 尚無快取
- **THEN** 該頁 SHALL 只提交一次該 key 的冷載入
- **AND** Citybus 詳情稍後返回 SHALL 只補做端點校驗
- **AND** 系統 SHALL NOT 因加入站點端點而取消及重發相同 key

#### Scenario: 多個 consumer 共享 in-flight candidate
- **WHEN** 多個 consumer 在同一 geometry key 仍載入時提出請求
- **THEN** 系統 SHALL 共享只負責 HTTP、解析、坐標校正及結構驗證的同一工作
- **AND** 每個 consumer SHALL 以自己的可靠端點獨立校驗結果
- **AND** 第一個 consumer 的端點參數 SHALL NOT 決定其他 consumer 的共享 future

#### Scenario: 單一 consumer 離開
- **WHEN** 一個等待 geometry 的頁面被銷毀但同一共享工作仍有其他有效 consumer
- **THEN** 系統 SHALL 停止向已銷毀頁面派送 callback
- **AND** 系統 SHALL NOT 中斷其他 consumer 仍需要的共享 HTTP 工作

#### Scenario: 成功結果快取一天
- **WHEN** 系統成功解析並接受某個 `routeVariant + boardingSeq + alightingSeq` 的校正後幾何
- **THEN** 系統 SHALL 在 App 進程內快取該結果 1 天
- **AND** cache key SHALL NOT 包含語言、Citybus session 或詳情 generation

#### Scenario: 命中未過期快取
- **WHEN** 1 天內再次請求相同 `routeVariant + boardingSeq + alightingSeq`
- **THEN** 系統 SHALL 使用快取 candidate 並對目前可用端點重新校驗
- **AND** 端點校驗通過時系統 SHALL NOT 重複發起對應 `getlinep2p.php` 請求

#### Scenario: 端點校驗失敗
- **WHEN** normalized candidate 與目前可靠上下車站端點明顯不一致
- **THEN** 系統 SHALL 拒絕該頁的 geometry 並移除對應成功 cache candidate
- **AND** 系統 SHALL NOT 讓未校驗 owner 留下可反覆命中的錯誤結果

#### Scenario: 失敗結果不快取
- **WHEN** 幾何請求失敗、回應為空、有效點不足或驗證失敗
- **THEN** 系統 SHALL NOT 快取該失敗結果
- **AND** 後續自動或手動重試 SHALL 可重新發起請求

### Requirement: 幾何失敗不得偽造巴士道路
系統 SHALL 讓單一乘車段幾何失敗只影響該段道路線條，對可恢復失敗自動重試一次，且 SHALL NOT 使用站點直線冒充巴士道路。

#### Scenario: 可恢復失敗自動重試
- **WHEN** 某段幾何發生傳輸錯誤、timeout、空回應或有效點不足
- **AND** 詳情頁仍在前台且 request generation 有效
- **THEN** 系統 SHALL 經短 backoff 自動重試該段一次
- **AND** 頁面 SHALL 保留已展示的站點及其他成功路段

#### Scenario: 不可恢復錯誤不循環
- **WHEN** geometry key 無效、回應明確違反坐標契約或端點校驗失敗
- **THEN** 系統 SHALL 將該段標記為失敗
- **AND** 系統 SHALL NOT 自動重試或進入循環請求

#### Scenario: 自動重試成功
- **WHEN** 某段首次可恢復失敗且自動重試返回有效幾何
- **THEN** 地圖 SHALL 在不離開目前頁面的情況下加入該巴士道路線
- **AND** 用戶 SHALL NOT 需要返回結果頁再重新進入

#### Scenario: 單段最終失敗
- **WHEN** 多段路線中的其中一段在自動重試後仍不可用
- **THEN** 系統 SHALL 繼續展示其他成功路線段
- **AND** 系統 SHALL 繼續展示失敗分段的可靠站點
- **AND** 系統 SHALL NOT 在該段站點之間補畫巴士直線

#### Scenario: 手動重試失敗分段
- **WHEN** 用戶重試缺失內容且只有部分幾何分段失敗
- **THEN** 系統 SHALL 只重新載入失敗或已過期分段
- **AND** 系統 SHALL 保留仍有效的成功幾何與站點

#### Scenario: 頁面離開後停止重試
- **WHEN** 自動重試尚未執行或請求仍在進行而頁面被銷毀或 generation 已過期
- **THEN** 系統 SHALL 取消排程或忽略舊 callback
- **AND** 舊結果 SHALL NOT 更新新的詳情頁

#### Scenario: 保存可復現回歸證據
- **WHEN** 本能力進行實作驗證
- **THEN** 專案 SHALL 保存 `780-CEF-1`、`104-KET-1`、`N118-TOS-1` 及至少一個多段轉乘樣本的原始回應或等價 fixture
- **AND** 測試 SHALL 確定性覆蓋冷快取、詳情與幾何兩種完成順序、首次失敗後自動恢復及部分段永久失敗
- **AND** live 驗證 SHALL 確認不帶 session 的最小幾何請求仍可重複解析
- **AND** 真實 Google 地圖驗證 SHALL 以高縮放畫面抽查校正後道路幾何與道路中心線的相對位置
