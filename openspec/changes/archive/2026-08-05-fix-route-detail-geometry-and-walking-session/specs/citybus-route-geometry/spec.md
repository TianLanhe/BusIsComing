## MODIFIED Requirements

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
