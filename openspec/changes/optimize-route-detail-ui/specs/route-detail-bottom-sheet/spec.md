## ADDED Requirements

### Requirement: 摘要以可點擊完整行動鏈連接時間線
系統 SHALL 在摘要第二層按實際方案次序展示起點步行、每個乘車段、每次步行或同站換乘及終點步行，並讓每個分段以穩定目標定位對應時間線內容。

#### Scenario: 顯示完整行動鏈
- **WHEN** 系統有可展示的單段或多段路線摘要
- **THEN** 第二層 SHALL 依次顯示起點步行、乘車段、換乘及終點步行且不得省略中間分段
- **AND** 起點、換乘及終點步行塊 SHALL 使用相同中性灰底及現有等比 `ic_walking_person` 圖標
- **AND** 乘車塊 SHALL 使用對應乘車段色並顯示必要路線號
- **AND** 相鄰塊之間 SHALL 只保留約 2dp 間距且 SHALL NOT 顯示連接箭頭

#### Scenario: 分段按內容緊湊排布
- **WHEN** 摘要行動鏈在一般 360dp、100% 字體顯示
- **THEN** 每塊 SHALL 按內容包裹寬度而非等寬或彈性拉伸
- **AND** 可見底色 SHALL 貼合約 18dp 圖標／路線內容，總可見高度約 22dp，並只保留約 2dp 上下留白
- **AND** 圖標或路線號與小號分段耗時 SHALL 共用底部基線
- **AND** 耗時 SHALL 使用較小字級而不得另佔一行或下沉為獨立角落

#### Scenario: 行動鏈超出可用寬度
- **WHEN** 多乘車段、長路線號或窄屏令行動鏈寬度超出容器
- **THEN** 行動鏈 SHALL 保持單行並可水平捲動
- **AND** 系統 SHALL NOT 換行、壓縮真實圖標、增加大段空隙或裁掉末段

#### Scenario: 緊湊分段保持可操作
- **WHEN** 使用者以觸控或 TalkBack 操作任一摘要分段
- **THEN** 每個分段 SHALL 具有至少 48dp 的有效操作高度及完整本地化描述
- **AND** 透明擴張觸控區 SHALL NOT 與其他可點擊控制重疊或改變實際行動順序
- **AND** TalkBack 焦點順序 SHALL 與畫面中的行程次序一致

#### Scenario: 點擊已載入的摘要分段
- **WHEN** 使用者點擊已有對應時間線 item 的步行、乘車或換乘分段
- **THEN** bottom sheet SHALL 進入全屏可閱讀狀態
- **AND** RecyclerView SHALL 捲動至穩定目標、短暫低強度高亮並把 TalkBack 焦點移至其標題
- **AND** 輔助技術 SHALL 朗讀目標分段而不是只朗讀位置變化

#### Scenario: 點擊仍在載入的摘要分段
- **WHEN** 使用者點擊分段時其對應動態詳情尚未到達
- **THEN** 系統 SHALL 保存綁定目前 page generation、穩定結構 identity 與 detail target id 的 pending target
- **AND** 同一 page 與結構 identity 的目標 item 出現後 SHALL 完成捲動、高亮、聚焦及朗讀
- **AND** 過期 generation 的 callback SHALL NOT 操作目前列表

#### Scenario: pending target 最終不可用
- **WHEN** pending target 所屬資料域最終失敗或新 generation 取代舊請求
- **THEN** 系統 SHALL 清除 pending target
- **AND** 同一頁仍在前台時 SHALL 以目前語言朗讀不可用狀態
- **AND** 其他已成功摘要及時間線內容 SHALL 保持可用

#### Scenario: 巴士分段計劃耗時可判定
- **WHEN** 本次新鮮 Citybus 動態詳情提供某分段可靠的起訖計劃時間
- **THEN** 摘要分段 SHALL 顯示由該時間邊界計算的耗時並正確處理跨午夜
- **AND** 系統 SHALL NOT 從 24 小時結構快取、距離或即時 ETA 推算該耗時

#### Scenario: CSDI 步行分段耗時可判定
- **WHEN** 目前 walking domain 接受某步行段的 CSDI 成功結果及正數 `Total_Time`
- **THEN** 對應摘要步行分段 SHALL 顯示向上取整且至少 1 分鐘的約略耗時
- **AND** 系統 SHALL NOT 以該值重算 Citybus 總耗時、預計到達或巴士段耗時

#### Scenario: 分段耗時不可判定
- **WHEN** 任一時間邊界缺失、不可靠或只存在於過期動態結果
- **THEN** 對應摘要分段 SHALL 保持存在且可點擊
- **AND** 系統 SHALL 隱藏該分段耗時而不顯示零、破折號或估算值

#### Scenario: Citybus fallback 與同站換乘沒有步行耗時
- **WHEN** 步行段仍在 Loading、回退 Citybus 距離、端點不可用，或相鄰巴士為 SameStop
- **THEN** 對應摘要分段 SHALL 保持步行或同站換乘語義
- **AND** 系統 SHALL NOT 顯示步行耗時、零值或以距離推算分鐘

### Requirement: 乘車段資訊去除重複並保留票價
系統 SHALL 在時間線中以無邊框乘車段展示路線、方向及必要單段票價，並 SHALL 把首程即時 ETA 及總乘坐站數集中於摘要第三層。

#### Scenario: 多段路線顯示單段票價
- **WHEN** 路線包含兩個或更多乘車段且某段有可靠單段票價
- **THEN** 該票價 SHALL 顯示於路線號／方向同一行末端
- **AND** 票價 SHALL NOT 另建帶邊框資訊卡或擠壓路線號至不可讀

#### Scenario: 單段路線不重複票價
- **WHEN** 路線只包含一個乘車段
- **THEN** 時間線 SHALL 不重複顯示單段票價
- **AND** 摘要第三層 SHALL 繼續顯示可靠總票價

#### Scenario: 單段票價缺失
- **WHEN** 多段路線的某段沒有可靠票價
- **THEN** 該段 SHALL 隱藏單段票價
- **AND** 系統 SHALL NOT 顯示破折號、零值或估算值

#### Scenario: 首程 ETA 只在摘要顯示
- **WHEN** 首程即時 ETA 為載入中、可用、暫無班次、最近成功值或不可用
- **THEN** 該狀態 SHALL 只在摘要第三層顯示
- **AND** 第一乘車段 SHALL NOT 重複顯示首程 ETA
- **AND** 每個乘車段 SHALL NOT 顯示單段乘坐或途經站數

## MODIFIED Requirements

### Requirement: 途经站默认折叠并可按段展开
系統 SHALL 在全屏路線詳情頁中預設折疊每段巴士的途經站，並允許用戶以乘車段內容區之外的控制行按段獨立展開或收起。

#### Scenario: 預設折疊每段途經站
- **WHEN** 結構化路線詳情載入成功
- **THEN** 每段巴士詳情 SHALL 預設展示路線號、可選方向、上車站和下車站
- **AND** 每段途經站 SHALL 預設折疊
- **AND** 乘車段 SHALL NOT 使用外框或獨立卡片底色包裹內容

#### Scenario: 折疊狀態展示途經站數量
- **WHEN** 某段巴士包含一個或多個途經站且處於折疊狀態
- **THEN** 系統 SHALL 在乘車段內容區之外展示 `N 個途經站` 控制行及向下 Chevron
- **AND** `N` SHALL 等於該段上車站和下車站之間的途經站數量
- **AND** 該控制只表達可展開內容數量，不得作為摘要或乘車段的單段站數指標
- **AND** 控制行 SHALL 提供至少 48dp 觸控範圍

#### Scenario: 展開單段途經站
- **WHEN** 用戶點擊某段的途經站控制行
- **THEN** 系統 SHALL 在主時間線原位展示該段全部途經站、前置圓點及分段實線
- **AND** 展開內容 SHALL 位於無邊框乘車段內容區之外
- **AND** Chevron SHALL 旋轉 180° 表示已展開
- **AND** 其他路線分段的折疊狀態 SHALL 保持不變

#### Scenario: 收起單段途經站
- **WHEN** 用戶點擊已展開分段的控制行
- **THEN** 系統 SHALL 隱藏該段途經站並將 Chevron 恢復為向下
- **AND** 系統 SHALL 繼續展示該段上車站和下車站
- **AND** 系統 SHALL 儘量保持目前列表視口穩定

#### Scenario: 畫面重建時保留展開狀態
- **WHEN** 全屏詳情頁因旋轉或等效 configuration change 重建
- **THEN** 系統 SHALL 恢復各乘車段目前的展開狀態
- **AND** 展開狀態 SHALL NOT 在離開詳情頁後永久保存

#### Scenario: 沒有途經站的分段
- **WHEN** 某段巴士上車站和下車站之間沒有途經站
- **THEN** 系統 SHALL 展示上車站和下車站
- **AND** 系統 SHALL NOT 展示途經站控制行

### Requirement: 路线详情采用分段时间线视觉
系統 SHALL 在半屏及全屏路線詳情中使用輕量縱向時間線展示起點步行、各段巴士、換乘、終點步行與目的地，並以連續線型、端點圓心、文字及分段顏色共同表達語義。

#### Scenario: 每段巴士使用分色連續豎線
- **WHEN** 結構化路線詳情包含一段或多段巴士
- **THEN** 詳情 UI SHALL 為每段巴士展示一條連續粗實線
- **AND** 相鄰路線段 SHALL 使用不同且模式感知的顏色
- **AND** 路線牌 SHALL 使用與該段實線一致的顏色
- **AND** 粗實線 SHALL 只表達路線分段，不表達車輛即時狀態或官方路線色

#### Scenario: 巴士上下車位置不突出節點
- **WHEN** 某段巴士詳情展示成功
- **THEN** 分段色實線 SHALL 連續穿過該段上車、途經及下車內容
- **AND** UI SHALL NOT 在上車或下車位置放大節點、額外空心圓或帶框卡片
- **AND** 上下車角色 SHALL 由站名、位置及本地化語義清楚表達

#### Scenario: 整體起終點使用彩色圓心
- **WHEN** 時間線展示查詢起點及查詢終點
- **THEN** 起點 SHALL 使用白色圓環內綠色圓心
- **AND** 終點 SHALL 使用白色圓環內珊瑚紅圓心
- **AND** 起終點 SHALL 同時以文字及無障礙描述區分

#### Scenario: 時間線步行段使用輕量點線
- **WHEN** 詳情包含起點、異站換乘或終點步行段
- **THEN** 時間線 SHALL 使用中性灰色輕量點線及步行人物圖示展示該段
- **AND** CSDI 成功時 SHALL 共同展示向上取整的距離及約略分鐘
- **AND** 查詢中或回退時 SHALL 顯示對應狀態；Citybus fallback 只可顯示可用距離而不得顯示約略分鐘
- **AND** 該點線 SHALL NOT 改變地圖只沿 CSDI path 使用粗灰開放折角的契約

#### Scenario: 失敗段只展示 Citybus 後備距離
- **WHEN** 某一 CSDI 步行段最終失敗
- **THEN** 詳情 UI SHALL 展示可用 Citybus 分段米數或目前語言的「距離暫不可用」
- **AND** 詳情 UI SHALL NOT 展示該段約略分鐘或暗示具有 CSDI 軌跡

#### Scenario: 同站換乘不顯示步行距離
- **WHEN** 結構化詳情標記兩段巴士為同站換乘
- **THEN** 時間線 SHALL 顯示目前語言的「同站換乘」內容
- **AND** 時間線 SHALL NOT 顯示步行點線、步行人物、步行距離或步行時間

#### Scenario: Citybus 預計時刻使用中性標示
- **WHEN** 起點、上下車站或終點有 Citybus 方案時間
- **THEN** 詳情 UI SHALL 以 `預計 HH:mm` 或目前語言等效文案展示
- **AND** 預計時刻 SHALL 使用中性文字層級
- **AND** 系統 SHALL NOT 將預計時刻標示為即時到站資料

#### Scenario: 節點不只依賴顏色
- **WHEN** 系統展示起點、上車、途經、下車與終點
- **THEN** 不同角色 SHALL 同時以節點、位置或文字標籤區分
- **AND** 關鍵路線結構 SHALL NOT 只依靠顏色辨識

#### Scenario: 不展示本次範圍外操作
- **WHEN** 路線詳情展示成功
- **THEN** 詳情 UI SHALL NOT 展示步行導航、收藏、截圖、分享、關注路線或下車提醒入口
- **AND** 本次變更 SHALL NOT 顯示空白 Google 地圖佔位

### Requirement: 路线详情支持失败重试
系統 SHALL 在全屏路線詳情請求或站點主結構解析失敗時展示可恢復的失敗狀態並允許用戶重試，且 SHALL 保持全屏無標題列的內容結構。

#### Scenario: 詳情請求失敗
- **WHEN** Citybus P2P 詳情請求失敗、超時或站點主結構無法解析
- **THEN** 全屏詳情頁 SHALL 保留已可用路線摘要與 Android 系統返回能力
- **AND** 正文 SHALL 展示「路線詳情暫不可用」及重試入口
- **AND** 頁面 SHALL NOT 為失敗狀態恢復 App Bar、Toolbar、頁面標題或屏內返回按鈕

#### Scenario: 用戶點擊重試
- **WHEN** 用戶在詳情失敗狀態點擊重試
- **THEN** 系統 SHALL 重新發起同一條路線及目前實際語言的 Citybus P2P 詳情請求
- **AND** 頁面 SHALL 重新展示載入狀態
- **AND** 舊請求後續結果 SHALL 被取消或忽略

#### Scenario: 返回失敗頁
- **WHEN** 用戶在詳情失敗狀態使用 Android 系統返回手勢或按鍵
- **THEN** 系統 SHALL 關閉路線詳情頁
- **AND** 來源路線結果列表及排序 SHALL 保持可用

#### Scenario: 部分欄位缺失
- **WHEN** 站點主結構解析成功但一個或多個可選欄位缺失
- **THEN** 系統 SHALL 展示已解析詳情並隱藏缺失值
- **AND** 系統 SHALL NOT 只因可選欄位缺失而展示整頁失敗狀態

### Requirement: 路線詳情摘要展示可判定的完整方案指標
系統 SHALL 在 persistent bottom sheet 的無邊框摘要區依次展示總耗時／預計到達、完整行動鏈，以及乘坐站數／步行距離／總票價／首程即時 ETA，並 SHALL 明確處理站數可靠性、卡片摘要與完整分段的差異。

#### Scenario: 顯示三層路線摘要與可靠乘坐站數
- **WHEN** 系統有可用路線結果摘要
- **THEN** 第一層 SHALL 先以主層級顯示總耗時，並緊接顯示可用的 `預計 HH:mm 到達` 或目前語言等效文案
- **AND** 第二層 SHALL 顯示按實際次序排列的完整可點擊行動鏈
- **AND** 第三層 SHALL 依次顯示乘坐站數、步行距離、總票價及首程即時 ETA 狀態
- **AND** 已驗證站序可用時乘坐站數 SHALL 等於每段途經站加該段下車站的總和
- **AND** 摘要 SHALL NOT 計算任何乘車段的上車站或額外計算換乘端點
- **AND** 摘要 SHALL NOT 使用外框或獨立 MaterialCard 邊框分組

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

#### Scenario: 完整步行分段距離可用
- **WHEN** 起點、所有必要步行換乘與終點分段均取得 CSDI 成功結果
- **THEN** 詳情摘要 SHALL 顯示各段原始 CSDI 距離先相加再向上取整的總米數
- **AND** 摘要 SHALL 使用已確認的步行人物圖示與距離
- **AND** 系統 SHALL NOT 將完整合計回填至路線卡片或改變列表排序

#### Scenario: 完整 CSDI 步行分段仍在取得
- **WHEN** 所有必要非同站步行段尚未全部成功且尚無分段最終失敗
- **THEN** 詳情摘要的步行資訊 SHALL 顯示目前語言的查詢中狀態
- **AND** 其他已取得摘要與分段內容 SHALL 漸進顯示而不被清空

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

#### Scenario: 首程 ETA 集中於摘要第三層
- **WHEN** 首程 ETA 有載入中、可用、暫無班次、最近成功值或技術失敗狀態
- **THEN** 摘要第三層 SHALL 以目前結構化語義顯示該狀態
- **AND** 摘要第一層及第一乘車段 SHALL NOT 重複顯示首程 ETA

#### Scenario: 摘要隨詳情內容捲動
- **WHEN** 用戶把詳情窗展開至半屏或全屏並向下瀏覽時間線
- **THEN** 摘要 SHALL 作為詳情列表首項正常捲出畫面
- **AND** 系統 SHALL NOT 把完整摘要固定在詳情窗頂部而壓縮時間線空間

#### Scenario: 從展開狀態收合至摘要
- **WHEN** 詳情列表未在頂部且用戶把詳情窗收合至摘要態
- **THEN** 系統 SHALL 先恢復列表頂部以完整展示摘要
- **AND** 摘要 SHALL NOT 停留在部分捲出或內部垂直捲動狀態

#### Scenario: 大字體摘要超出普通目標高度
- **WHEN** font scale 1.3 或 2.0 令摘要無法容納於普通 25% 至 30% 目標高度
- **THEN** 摘要態 SHALL 按內容增高且半屏態 SHALL 不低於摘要所需高度
- **AND** 第三層指標 SHALL 自然換行而不得縮字、裁切或省略核心狀態
- **AND** 摘要 SHALL NOT 產生內部垂直捲動
