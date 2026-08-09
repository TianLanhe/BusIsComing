## MODIFIED Requirements

### Requirement: 路線詳情辨識並展示分段步行完整性
系統 SHALL 分別規劃與展示起點、每次步行換乘及終點步行段，讓各段 CSDI 距離、約略時間與失敗回退獨立漸進更新；系統 SHALL NOT 從總距離反推未知分段。

#### Scenario: 單段路線完整距離
- **WHEN** 單段路線的起點至上車站及下車站至終點兩個必要 CSDI 請求均成功
- **THEN** 時間線 SHALL 在對應首尾步行段分別展示各自向上取整的米數及約略分鐘
- **AND** 詳情 SHALL 將兩段標記為 CSDI 成功

#### Scenario: 多段路線完整距離
- **WHEN** 多段路線的起點、每次步行換乘及終點必要 CSDI 請求均成功
- **THEN** 時間線 SHALL 依實際方案次序展示每段距離及約略分鐘
- **AND** 非同站步行段數量 SHALL 等於乘車段數加一

#### Scenario: 同站換乘
- **WHEN** Citybus 詳情標記相鄰乘車段為同站換乘
- **THEN** 時間線 SHALL 顯示目前語言的「同站換乘」
- **AND** 系統 SHALL NOT 顯示 `0 米`、`0 分鐘`、未知步行距離或步行點線
- **AND** 同站換乘 SHALL NOT 被計入必要步行段數量或 CSDI 合計

#### Scenario: 分段仍在查詢
- **WHEN** 一個必要步行段尚未從成功 cache 取得結果且外部 flight 尚未最終完成
- **THEN** 時間線 SHALL 保留該段步行語義並展示目前語言的查詢中狀態
- **AND** 其他已成功或已回退的分段 SHALL 保持可見

#### Scenario: 只有部分距離
- **WHEN** 一個必要 CSDI 分段最終失敗而對應 Citybus 分段距離可用
- **THEN** 時間線 SHALL 在該段展示 Citybus 米數
- **AND** 該段 SHALL NOT 展示 CSDI 約略分鐘或步行軌跡
- **AND** 其他 CSDI 成功分段的距離、時間及軌跡 SHALL 保持可用

#### Scenario: CSDI 與 Citybus 分段距離皆不可用
- **WHEN** 一個必要 CSDI 分段最終失敗且對應 Citybus 分段距離亦缺失
- **THEN** 時間線 SHALL 保留步行語義並顯示目前語言的「距離暫不可用」
- **AND** 系統 SHALL NOT 以零值、總距離差額或直線距離補出該段

#### Scenario: 三語兼容解析
- **WHEN** 繁體、簡體或英文 Citybus 詳情以 `showtimetable1(...)` 或對應 HTML 標籤提供轉乘類型及後備步行距離
- **THEN** 系統 SHALL 解析相同的步行段次序、SameStop 語義及可用後備米數
- **AND** parser SHALL NOT 只依賴繁體 `步行距離(約)` 文本

#### Scenario: session 缺失形態
- **WHEN** 站點仍可解析，但 timetable payload 為空且所有應有步行距離與轉乘欄位同時為空
- **THEN** 系統 SHALL 將 Citybus 詳情結果分類為 session 缺失並觸發一次受控恢復
- **AND** 系統 SHALL NOT 把該形態誤判為合法的全零、SameStop 或永久未知距離

### Requirement: 路线详情采用分段时间线视觉
系統 SHALL 在半屏及全屏路線詳情中使用輕量縱向時間線展示起點步行、各段巴士、換乘、終點步行與目的地，並以連續線型、端點圓心、文字及分段顏色共同表達語義。

#### Scenario: 每段巴士使用分色粗豎線
- **WHEN** 結構化路線詳情包含一段或多段巴士
- **THEN** 詳情 UI SHALL 為每段巴士展示一條粗實線
- **AND** 相鄰路線段 SHALL 使用不同且模式感知的顏色
- **AND** 路線牌 SHALL 使用與該段實線一致的顏色
- **AND** 粗實線 SHALL 只表達路線分段，不表達車輛即時狀態或官方路線色

#### Scenario: 上下車站作為路線段端點
- **WHEN** 某段巴士詳情展示成功
- **THEN** 分段色實線 SHALL 連續穿過該段上車、途經及下車內容
- **AND** UI SHALL NOT 在上車或下車位置放大節點、額外空心圓或帶框卡片
- **AND** 上下車角色 SHALL 由站名、位置及本地化語義清楚表達

#### Scenario: 步行段使用細虛線
- **WHEN** 詳情包含起點、步行換乘或終點步行段
- **THEN** 詳情 UI SHALL 使用中性灰色輕量點線及步行人物圖示展示該段
- **AND** CSDI 成功時 SHALL 共同展示向上取整的距離與約略分鐘
- **AND** 查詢中或回退時 SHALL 顯示對應狀態而 SHALL NOT 顯示推算數字
- **AND** 該點線 SHALL NOT 改變地圖只沿 CSDI path 使用粗灰開放折角的契約

#### Scenario: 失敗段只展示 Citybus 後備距離
- **WHEN** 某一 CSDI 步行段最終失敗
- **THEN** 詳情 UI SHALL 展示可用 Citybus 分段米數或「距離暫不可用」
- **AND** 詳情 UI SHALL NOT 展示該段約略分鐘或暗示具有 CSDI 軌跡

#### Scenario: 同站換乘不顯示步行距離
- **WHEN** 結構化詳情標記兩段巴士為同站換乘
- **THEN** 詳情 UI SHALL 顯示「同站換乘」節點
- **AND** 詳情 UI SHALL NOT 顯示步行點線、步行人物、步行距離或步行時間

#### Scenario: Citybus 預計時刻使用中性標示
- **WHEN** 起點、上下車站或終點有 Citybus 方案時間
- **THEN** 詳情 UI SHALL 以 `預計 HH:mm` 或目前語言等效文案展示
- **AND** 預計時刻 SHALL 使用中性文字層級
- **AND** 系統 SHALL NOT 將預計時刻標示為即時到站資料

#### Scenario: 節點不只依賴顏色
- **WHEN** 系統展示起點、上車、途經、下車與終點
- **THEN** 不同角色 SHALL 同時以節點大小、形狀、位置或文字標籤區分
- **AND** 關鍵路線結構 SHALL NOT 只依靠顏色辨識

#### Scenario: 不展示本次範圍外操作
- **WHEN** 路線詳情展示成功
- **THEN** 詳情 UI SHALL NOT 展示步行導航、收藏、截圖、分享、關注路線或下車提醒入口
- **AND** 本次變更 SHALL NOT 顯示空白 Google 地圖佔位

### Requirement: 路線詳情摘要展示可判定的完整方案指標
系統 SHALL 在 persistent bottom sheet 的摘要區展示路線鏈、Citybus 總耗時、Citybus 預計到達、總票價、總途經站數、漸進步行距離及可用首程即時 ETA，並 SHALL 明確區分 CSDI 完整合計與完整 Citybus 回退。

#### Scenario: 顯示路線摘要
- **WHEN** 系統有可用路線結果摘要
- **THEN** 摘要 SHALL 展示路線鏈、Citybus 總耗時與總票價
- **AND** 有 Citybus 最終預計到達時間時摘要 SHALL 展示 `預計 HH:mm 到達` 或目前語言等效文案
- **AND** 摘要 SHALL 展示各乘車段途經站數之和且 SHALL NOT 重複計算上下車或換乘端點
- **AND** 有可靠首程即時 ETA 時摘要 SHALL 以緊湊形式展示該狀態

#### Scenario: 相鄰上下車乘車段
- **WHEN** 一個已驗證乘車段的上車站與下車站相鄰且沒有途經站
- **THEN** 該段 SHALL 為摘要乘坐站數貢獻 1 站
- **AND** 兩個相鄰上下車乘車段 SHALL 合計為 2 站，即使兩段為同站換乘

#### Scenario: 可靠站序仍在載入
- **WHEN** 頁面尚未取得已驗證站序或未過期結構快取
- **THEN** 摘要 SHALL 使用目前語言展示站數載入狀態
- **AND** 摘要 SHALL NOT 使用 plan 差值、空集合或預設整數顯示 `0 站`

#### Scenario: 站序最終不可用
- **WHEN** 詳情請求、受控恢復或站序完整性驗證最終失敗
- **AND** 頁面沒有可用的已驗證結構快取
- **THEN** 摘要 SHALL 使用目前語言展示站數暫時無法載入
- **AND** 摘要 SHALL NOT 把失敗或未知狀態格式化為 `0 站`

#### Scenario: 完整 CSDI 步行分段仍在取得
- **WHEN** 所有必要非同站步行段尚未全部成功且尚無分段最終失敗
- **THEN** 詳情摘要的步行信息 SHALL 顯示目前語言的查詢中狀態
- **AND** 其他已取得的摘要與分段內容 SHALL 漸進顯示而不被清空

#### Scenario: 完整步行分段距離可用
- **WHEN** 起點、所有必要步行換乘與終點分段均取得 CSDI 成功結果
- **THEN** 詳情摘要 SHALL 顯示各段原始距離相加後再向上取整的總米數
- **AND** 摘要 SHALL 使用已確認的步行人物圖示與距離
- **AND** 系統 SHALL NOT 將完整合計回填至路線卡片原始 Citybus 欄位或改變 result identity

#### Scenario: 完整步行合計不可判定
- **WHEN** 任一必要 CSDI 步行段最終失敗、端點來源衝突或不可可靠確定
- **THEN** 詳情摘要 SHALL 立即完整回退顯示 `ppsearch_p3.php` 路線卡片 Citybus 總步行距離
- **AND** 摘要 SHALL NOT 加上來源或「約」字樣
- **AND** 系統 SHALL NOT 混合 CSDI 成功段與 Citybus 分段距離宣稱新的完整總量
- **AND** 其他 CSDI 成功分段 SHALL 繼續在時間線與地圖中保留

#### Scenario: 成功快取使摘要首幀完整
- **WHEN** 路線組合與全部必要 CSDI 分段均命中有效成功快取
- **THEN** 詳情摘要 SHALL 首次展示時直接顯示 CSDI 總距離
- **AND** 摘要 SHALL NOT 先閃現查詢中或 Citybus 回退數值

#### Scenario: 摘要隨詳情內容捲動
- **WHEN** 用戶把詳情窗展開至半屏或全屏並向下瀏覽時間線
- **THEN** 摘要 SHALL 作為詳情列表首項正常捲出畫面
- **AND** 系統 SHALL NOT 把完整摘要固定在詳情窗頂部而壓縮時間線空間

#### Scenario: 從展開狀態收合至摘要
- **WHEN** 詳情列表未在頂部且用戶把詳情窗收合至摘要態
- **THEN** 系統 SHALL 先恢復列表頂部以完整展示摘要
- **AND** 摘要 SHALL NOT 停留在部分捲出或內部捲動狀態

#### Scenario: 大字體摘要超出普通目標高度
- **WHEN** font scale 1.3 或 2.0 令摘要無法容納於普通 25% 至 30% 目標高度
- **THEN** 摘要態 SHALL 按內容增高且半屏態 SHALL 不低於摘要所需高度
- **AND** 系統 SHALL NOT 縮字、裁切核心文字或讓摘要本身內部捲動

## ADDED Requirements

### Requirement: 路線詳情保留 Citybus 時間權威
系統 SHALL 保留 Citybus 總耗時、預計到達、各巴士乘車段計劃時間與首程 ETA，並 SHALL 只把 CSDI 時間用作各成功步行段的約略時間。

#### Scenario: CSDI 步行時間不加入總耗時
- **WHEN** 一個或多個 CSDI 步行段取得原始時間
- **THEN** 詳情 SHALL 在對應步行段展示向上取整且至少 1 分鐘的約略時間
- **AND** 系統 SHALL NOT 以該時間重算 Citybus 總耗時或預計到達

#### Scenario: 巴士段時間不被步行更新改寫
- **WHEN** 步行分段由查詢中更新為成功或回退
- **THEN** 各巴士段 Citybus 計劃時間與首程 ETA SHALL 保持不變
- **AND** 詳情 SHALL NOT 宣稱 Citybus 時間與 CSDI 固定步速已形成內部一致的端到端時間模型
