# landsd-pedestrian-routing Specification

## Purpose
TBD - created by archiving change integrate-landsd-pedestrian-routing. Update Purpose after archive.
## Requirements
### Requirement: 以固定契約查詢地政總署步行路線
系統 SHALL 透過地政總署 3D Pedestrian Route Search `route/solve` 端點查詢每一個必要步行段，並 SHALL 以不受 App 語言影響的固定參數取得米制 WGS84 路線。

#### Scenario: 建立固定步行請求
- **WHEN** 系統為一個具有有效 WGS84 起終點的必要步行段建立請求
- **THEN** `stops` SHALL 只包含依序命名為 `Start` 與 `End` 的兩個 feature
- **AND** 每個端點 SHALL 以 `x=longitude`、`y=latitude` 傳遞且 SHALL NOT 猜測或補入 z 值
- **AND** 請求 SHALL 固定傳遞 `travelMode=3`、`directionsLengthUnits=esriNAUMeters`、`directionsLanguage=en`、`outSR=4326`、`f=json`、`returnZ=true` 及 `directionStyleName=NA Campus`

#### Scenario: 語言切換不改變外部請求
- **WHEN** 相同步行段在繁體、簡體或英文 App 語言下被查詢
- **THEN** 系統 SHALL 使用相同端點、固定參數、request key 及已成功結果
- **AND** 系統 SHALL NOT 只因 App 語言改變而重新請求地政總署

### Requirement: 嚴格驗證距離時間與全部子路徑
系統 SHALL 只接受同時具有有效正數距離、有效正數時間及完整步行幾何的 CSDI 路線，並 SHALL 將全部有效 `geometry.paths` 保持為互相獨立的有序子路徑。

#### Scenario: 解析有效回應
- **WHEN** `routes.features[0]` 包含有限正數 `Total_Length`、有限正數 `Total_Time` 及非空 `geometry.paths`
- **AND** 每個 path 至少包含兩個有效 WGS84 點
- **THEN** 系統 SHALL 返回原始 Double 米數、原始 Double 分鐘及全部子路徑
- **AND** 系統 SHALL 只讀每個坐標的前兩項並忽略回應中的 z 或 m 值

#### Scenario: 子路徑保持分離
- **WHEN** 有效回應包含兩個或更多 `geometry.paths`
- **THEN** 系統 SHALL 保留上游次序及子路徑邊界
- **AND** 系統 SHALL NOT 以直線把相鄰子路徑首尾補接為一條幾何

#### Scenario: 必要欄位或幾何無效
- **WHEN** 距離、時間或 paths 缺失、非有限、非正數或含無效 WGS84 點
- **THEN** 系統 SHALL 將整個分段分類為無效回應
- **AND** 系統 SHALL NOT 發布部分距離、部分時間或部分軌跡

#### Scenario: 軌跡端點偏差超限
- **WHEN** 第一個 path 起點距請求起點超過 30 米，或最後一個 path 終點距請求終點超過 30 米
- **THEN** 系統 SHALL 拒絕整個分段
- **AND** 系統 SHALL NOT 以該距離、時間或軌跡更新 UI

### Requirement: 以 Citybus 語義與可靠坐標規劃步行分段
系統 SHALL 先按 Citybus 方案建立起點、轉乘及終點步行段，再以查詢端點與可靠巴士站坐標提交可確定的分段；Citybus 詳情與站點坐標載入 SHALL 互相並發而非串行等待。

#### Scenario: 規劃首尾步行段
- **WHEN** 查詢起點、首段上車站、末段下車站及查詢終點具有可靠坐標
- **THEN** 系統 SHALL 分別建立 `origin` 與 `destination` 有向步行段
- **AND** 任一首尾分段 SHALL 在自身端點可用後立即參與查詢，而不等待不相關的轉乘資料

#### Scenario: 規劃步行轉乘
- **WHEN** Citybus 詳情明確把相鄰乘車段標記為步行轉乘
- **THEN** 系統 SHALL 建立具有穩定次序識別的 `transfer:<index>` 有向步行段
- **AND** 即使兩端坐標很接近，系統亦 SHALL 提交該步行段

#### Scenario: 跳過同站轉乘
- **WHEN** Citybus 詳情明確把相鄰乘車段標記為同站轉乘
- **THEN** 系統 SHALL 將該位置標記為 `SameStop`
- **AND** 系統 SHALL NOT 發起 CSDI 請求、建立 0 米或 0 分鐘結果，或生成步行軌跡

#### Scenario: 站點坐標主來源與嚴格後備
- **WHEN** `showstops2.php` 提供目前乘車段端點的有效坐標
- **THEN** 系統 SHALL 以該坐標作主來源
- **WHEN** 主來源缺失而 Citybus 詳情坐標的 `routeVariant + sequence + stopId` 全部匹配同一端點
- **THEN** 系統 SHALL 允許使用該詳情坐標作後備
- **AND** 系統 SHALL NOT 只按站名、sequence、距離接近或另一 route variant 對齊端點

#### Scenario: 兩個 Citybus 坐標來源衝突
- **WHEN** 主來源與嚴格匹配後備來源皆可用且兩者直線距離超過 30 米
- **THEN** 系統 SHALL 將涉及該端點的步行段分類為來源衝突並回退
- **AND** 系統 SHALL NOT 平均坐標或任選一個來源發起請求

### Requirement: 保留 Citybus 原值並以分段狀態派生展示
系統 SHALL 保留 Citybus 原始總步行距離作身份、去重、監控與兜底資料，並 SHALL 以獨立的 Loading、CSDI 成功、Citybus 回退及 SameStop 狀態表達新的分段結果。

#### Scenario: CSDI 成功不改寫 Citybus 原值
- **WHEN** 一個或多個 CSDI 分段成功
- **THEN** 系統 SHALL 將原始距離、時間與 paths 寫入獨立步行狀態
- **AND** 系統 SHALL NOT 改寫路線結果既有 Citybus 總步行距離或由其形成的 result identity

#### Scenario: 總距離向上取整
- **WHEN** 所有必要非同站分段均取得 CSDI 成功結果
- **THEN** 系統 SHALL 先累加各段原始 Double 米數，再對總和向上取整至整數米
- **AND** 系統 SHALL NOT 先對每段取整後再計算總距離

#### Scenario: 分段距離與時間向上取整
- **WHEN** 詳情展示一個 CSDI 成功步行段
- **THEN** 系統 SHALL 將該段原始距離向上取整至整數米
- **AND** 系統 SHALL 將正數原始時間向上取整至整數分鐘且最少展示 1 分鐘
- **AND** 展示 SHALL 將該分鐘標示為約略時間

#### Scenario: 亂序或重複事件重算狀態
- **WHEN** 多個分段 callback 以亂序、重複或重試完成的次序到達
- **THEN** 系統 SHALL 以 stable segment id 原子替換對應狀態並從完整狀態表重新派生摘要
- **AND** 系統 SHALL NOT 以 callback 內增量累加造成重複距離或遺失已成功內容

### Requirement: 以進程級 single-flight 控制全局負載
系統 SHALL 讓卡片、詳情及不同候選路線共用同一進程級 CSDI runtime，對相同有向端點只保留一個 flight，並 SHALL 將全 App 同時執行的 CSDI 請求限制為 5 個。

#### Scenario: 建立有向 request key
- **WHEN** 系統建立分段 request key
- **THEN** key SHALL 包含起點及終點經緯度各自保留 6 位小數的有向次序與 `travelMode=3`
- **AND** key SHALL NOT 包含 App 語言
- **AND** 反向端點 SHALL 形成不同 key

#### Scenario: 相同端點合併 flight
- **WHEN** 多張卡片或詳情同時訂閱相同 request key
- **THEN** 系統 SHALL 讓所有訂閱者加入同一個排隊中或執行中的 flight
- **AND** 系統 SHALL 對外只發出一次對應嘗試序列

#### Scenario: 全局並發上限
- **WHEN** 超過 5 個不同 request key 同時等待執行
- **THEN** 任一時刻執行中的 CSDI 網絡嘗試 SHALL NOT 超過 5 個
- **AND** 重試 SHALL 繼續受相同全局上限約束

#### Scenario: 詳情提升排隊優先級
- **WHEN** 詳情訂閱一個尚未開始的普通優先級 flight 或建立新 flight
- **THEN** 系統 SHALL 令該 flight 以詳情優先級在有界隊列中等待
- **AND** 系統 SHALL NOT 搶占或中斷已執行的其他 flight

### Requirement: 成功結果快取一天且失敗可重新嘗試
系統 SHALL 在 App 進程記憶體內分別快取成功分段與穩定路線組合 24 小時，並 SHALL NOT 快取失敗、來源衝突、無效回應或已格式化 UI 文字。

#### Scenario: 命中分段成功快取
- **WHEN** 24 小時內再次訂閱相同 request key
- **THEN** 系統 SHALL 立即返回原始 CSDI 距離、時間及 paths
- **AND** 系統 SHALL NOT 建立新的外部 flight

#### Scenario: 重建已快取路線組合
- **WHEN** 相同查詢端點 context 與 plan fingerprint 命中有序 segment key、角色及 SameStop 組合
- **THEN** 系統 SHALL 從各分段成功快取重建可用卡片與詳情狀態
- **AND** 組合快取 SHALL NOT 複製或另存一份軌跡結果

#### Scenario: 部分命中只查缺失段
- **WHEN** 路線組合命中但只有部分必要分段仍在成功 TTL 內
- **THEN** 系統 SHALL 立即發布已有成功分段並只為缺失 key 建立或加入 flight

#### Scenario: 失敗後手動刷新或重新進入
- **WHEN** 某分段先前最終失敗，且使用者明確觸發手動路線刷新或重新進入詳情
- **THEN** 系統 SHALL 重用仍有效的成功 cache 並允許該手勢繞過一次目前失敗退避以重新嘗試該分段
- **AND** 先前失敗 SHALL NOT 被當作成功結果返回或阻止這一次新 flight

#### Scenario: 自動刷新遵守失敗退避
- **WHEN** `AUTOMATIC` 路線刷新在同一有向 request key 的失敗退避到期前建立新結果
- **THEN** 系統 SHALL 保持該段 Citybus 回退而不建立新的 CSDI flight
- **AND** 初次退避 SHALL 為 5 分鐘，連續失敗 SHALL 指數增加且最長不超過 30 分鐘
- **AND** CSDI 成功 SHALL 立即清除該 key 的失敗退避資格

#### Scenario: 成功快取過期
- **WHEN** 成功分段或路線組合保存超過 24 小時
- **THEN** 系統 SHALL 原子移除過期項並按目前資料重新規劃或請求

### Requirement: 超時重試取消與過期結果均有明確邊界
系統 SHALL 讓每個 flight 可取消，為每次網絡嘗試設定 8 秒總時限，只對瞬時錯誤在約 300 毫秒後自動重試一次，並 SHALL 由仍有效訂閱及 generation 控制結果派送。

#### Scenario: 瞬時錯誤重試一次
- **WHEN** 第一次嘗試遇到連線或網絡異常、timeout 或 HTTP 5xx
- **THEN** 系統 SHALL 在約 300 毫秒後於同一 flight 自動重試一次
- **AND** 第二次嘗試完成後 SHALL 產生最終成功或失敗而不再自動重試

#### Scenario: 永久或內容錯誤不重試
- **WHEN** 嘗試收到 HTTP 4xx、無可用路線、必要欄位缺失或無效回應
- **THEN** 系統 SHALL 立即把該 flight 完成為最終失敗
- **AND** 系統 SHALL NOT 自動重試該回應

#### Scenario: 個別訂閱者離開
- **WHEN** 一個 consumer 取消但同一 flight 仍有其他有效 consumer
- **THEN** 系統 SHALL 只移除該 consumer 並讓 flight 繼續
- **AND** 已取消 consumer SHALL NOT 收到後續 callback

#### Scenario: 最後訂閱者離開
- **WHEN** 排隊中或執行中的 flight 失去最後一個 consumer
- **THEN** 系統 SHALL 從隊列移除尚未執行工作，或中止在途 HTTP
- **AND** 系統 SHALL NOT 因已取消 flight 更新卡片、詳情或 cache

#### Scenario: 配置變更保留邏輯訂閱
- **WHEN** 搜尋或詳情因旋轉、主題或語言 configuration change 重建，但邏輯查詢會話仍存在
- **THEN** 系統 SHALL 由邏輯會話繼續持有訂閱與原始狀態
- **AND** 新 UI observer SHALL 從目前 snapshot 重新格式化，而不因舊 View 銷毀取消及重請相同 flight

#### Scenario: 過期 callback 不覆蓋新狀態
- **WHEN** callback 的 query generation、page generation、walking domain generation、result identity 或 segment id 不再匹配目前 consumer
- **THEN** 系統 SHALL 忽略該 callback
- **AND** 已成功的新查詢或新頁面內容 SHALL 保持不變

#### Scenario: 基礎結果完成後步行仍可漸進
- **WHEN** 自動刷新已因基礎路線回應完成 cycle，而目前 query generation 的 CSDI flight 稍後完成
- **THEN** 有效 callback SHALL 仍可更新對應 result id 與 segment id 的步行狀態
- **AND** 該 callback SHALL NOT 延長已完成 cycle、重設排程或更新另一個 query generation

### Requirement: 步行診斷不得記錄可重建行程資料
系統 SHALL 只以匿名 flight 識別與分類事件診斷 CSDI runtime，並 SHALL NOT 在 release 分析或日誌中保存可重建使用者行程的請求及回應資料。

#### Scenario: 記錄匿名並發診斷
- **WHEN** debug 診斷記錄 cache、queue、single-flight、priority、permit、attempt、retry、訂閱或失敗事件
- **THEN** 系統 SHALL 使用進程內匿名 flight id 與分類值
- **AND** 系統 SHALL NOT 記錄完整 URL、stops JSON、任何精度坐標、地點名稱、stop id、session、使用者自訂名稱或返回軌跡點

#### Scenario: 生產環境不新增線上軌跡分析
- **WHEN** App 以 release 配置執行 CSDI 查詢
- **THEN** 系統 SHALL NOT 因本能力新增包含步行端點、路徑或行程身份的線上分析事件
