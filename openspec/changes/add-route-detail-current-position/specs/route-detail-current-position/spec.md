## ADDED Requirements

### Requirement: 路線詳情以前台限定方式取得目前位置
系統 SHALL 在路線詳情頁進入前台時準備取得裝置目前位置，並 SHALL 在頁面離開前台後停止本頁持續位置更新。

#### Scenario: 已授權時首次開啟詳情
- **WHEN** 路線詳情頁首次進入前台且已有可用前台位置權限
- **THEN** 系統 SHALL 立即請求新鮮位置 fix
- **AND** 系統 SHALL 以約 `10 秒` 目標間隔及 `20 米` 最小位移的省電參數繼續取得位置
- **AND** 使用者 SHALL NOT 需要先展開詳情窗或點擊地圖目前位置控件

#### Scenario: 未授權時首次開啟詳情
- **WHEN** 路線詳情頁首次進入前台且尚無可用位置權限
- **THEN** 系統 SHALL 在不阻塞地圖、摘要及詳情載入的情況下顯示一次帶「開啟」action 的本地化 Snackbar
- **AND** 系統 SHALL NOT 在使用者操作 action 前自動顯示系統權限對話框

#### Scenario: 未授權使用者選擇開啟
- **WHEN** 未授權使用者點擊 Snackbar 的「開啟」action
- **THEN** 系統 SHALL 在平台仍允許請求時發起適用的前台位置權限請求
- **AND** 權限授予後系統 SHALL 在頁面仍在前台及 generation 有效時立即開始取得位置

#### Scenario: 位置權限已永久拒絕
- **WHEN** 使用者點擊「開啟」但平台不再允許顯示位置權限對話框
- **THEN** 系統 SHALL 開啟 BusIsComing 的 App 系統設定頁
- **AND** 從設定返回後系統 SHALL 依目前實際權限及頁面前台狀態重新判定是否開始定位

#### Scenario: 系統定位服務關閉
- **WHEN** App 已有位置權限但系統定位服務不可用
- **THEN** 系統 SHALL 在單次詳情頁會話內顯示一次本地化 Snackbar 及系統位置設定入口
- **AND** 路線摘要、地圖與文字時間線 SHALL 保持可用

#### Scenario: 初次 fix 暫時不可用
- **WHEN** 定位已啟動但約 `10 秒` 仍沒有首個位置 fix
- **THEN** 系統 SHALL 在單次詳情頁會話內提示一次目前暫時無法取得位置
- **AND** 系統 SHALL 在頁面仍在前台時繼續等待後續 fix

#### Scenario: 頁面離開前台
- **WHEN** 路線詳情頁進入後台、被關閉或目前 generation 被銷毀
- **THEN** 系統 SHALL 停止或取消本頁持續位置 callback
- **AND** 過期 callback SHALL NOT 更新後續頁面或目前 UI

### Requirement: 系統建立具局部可靠性的完整行程軸
系統 SHALL 按實際方案次序建立由查詢起點、步行、各乘車段有序站點、換乘及查詢終點組成的結構化行程軸，並 SHALL 讓單一不可用分段只失去自身匹配能力。

#### Scenario: 完整單段或多段路線資料可用
- **WHEN** 詳情提供已通過完整性門禁的站序、各乘車段已驗證 Citybus 幾何及成功 CSDI 步行 paths
- **THEN** 行程軸 SHALL 依次包含查詢起點、首段步行、每段巴士相鄰站點邊、每次換乘、終段步行及查詢終點
- **AND** 每個節點及邊 SHALL 具有綁定目前結構 identity 的穩定身份

#### Scenario: 巴士站點可單調投影到道路幾何
- **WHEN** 一段完整有序站點可依站序唯一且單調地投影到同段已驗證 Citybus 道路幾何
- **THEN** 系統 SHALL 以相鄰站點投影位置切分可匹配巴士邊
- **AND** 系統 SHALL 保留原站序及道路幾何方向

#### Scenario: 巴士幾何無法可靠切分
- **WHEN** 關鍵站點投影距離過大、投影次序倒退，或環線、交叉及平行道路令投影不可可靠裁決
- **THEN** 系統 SHALL 將該乘車段全部巴士邊標記為不可匹配
- **AND** 系統 SHALL NOT 使用站點直線、最近道路猜測、時間或 ETA 補建候選邊

#### Scenario: 步行分段有多個成功子路徑
- **WHEN** 一個成功 CSDI 步行分段包含兩個或更多有序子 paths
- **THEN** 系統 SHALL 保留每個實際子 path 的次序與累計長度
- **AND** 系統 SHALL NOT 在子 path 空隙或端點之間補畫或建立可匹配直線

#### Scenario: 同站換乘
- **WHEN** Citybus 詳情把相鄰乘車段標記為同站換乘
- **THEN** 行程軸 SHALL 使用一個代表前段下車及後段上車的複合換乘節點
- **AND** 系統 SHALL NOT 為該換乘虛構步行距離或零長 path

#### Scenario: 分段資料局部失敗
- **WHEN** 某乘車段幾何或某步行分段 path 不可用而其他分段仍可靠
- **THEN** 系統 SHALL 只把失敗分段的邊標記為不可匹配
- **AND** 其他可靠節點及邊 SHALL 繼續可供目前位置匹配

#### Scenario: 行程資料以任意次序漸進到達
- **WHEN** 位置 fix、站點詳情、巴士幾何及 CSDI paths 以任意次序完成或更新
- **THEN** 系統 SHALL 只使用目前 generation 及相同結構 identity 的最新可靠資料重建行程軸並重算位置
- **AND** ETA 或其他不改變靜態站序與幾何的刷新 SHALL NOT 重建行程軸

### Requirement: 目前位置只在通過明確可靠性門禁後匹配
系統 SHALL 先驗證位置新鮮度、精度、離軸距離及候選可辨識性，再輸出靠近節點、相鄰節點之間、步行進度或不可靠狀態。

#### Scenario: 位置輸入通過基本門禁
- **WHEN** fix 年齡不超過 `20 秒`、accuracy 存在且不超過約 `75 米`
- **AND** 最近可靠候選距離不超過 `max(30 米, accuracy)`
- **THEN** 系統 SHALL 繼續評估該候選的唯一性及行程軸語義

#### Scenario: 非相鄰候選無法可靠區分
- **WHEN** 最近候選相對於不屬於同一本地相鄰節點／邊關係的次近候選，未領先至少 `max(20 米, accuracy / 2)`
- **THEN** 系統 SHALL 將本次位置視為不可靠
- **AND** 系統 SHALL NOT 強制選擇其中一個候選

#### Scenario: 唯一站點候選
- **WHEN** 一個站點或查詢端點在門禁內成為唯一可靠節點候選
- **THEN** 系統 SHALL 輸出靠近該節點的結構化狀態及其穩定 identity

#### Scenario: 相鄰站點共同競爭
- **WHEN** 兩個候選節點屬於同一條可靠相鄰邊且不能只判定其中一個節點
- **THEN** 系統 SHALL 在該邊實際幾何上評估位置
- **AND** 巴士邊 SHALL 輸出位於這兩個相鄰站點之間的狀態

#### Scenario: 位置位於可靠步行 path
- **WHEN** 位置可唯一投影到成功 CSDI 步行分段的實際子 path
- **THEN** 系統 SHALL 以先前子 paths 累計長度加目前投影里程計算該步行分段的 `[0, 1]` 進度
- **AND** 系統 SHALL NOT 把 path 空隙計入可匹配距離

#### Scenario: 位置門禁失敗
- **WHEN** fix 過期、accuracy 缺失或過大、位置離可靠行程軸過遠、所屬邊不可匹配或候選歧義
- **THEN** 系統 SHALL 輸出不可靠狀態並立即隱藏摘要及詳細位置指示
- **AND** 系統 SHALL NOT 保留灰色、淡化或其他看似目前位置的舊指示

### Requirement: 目前位置轉移須穩定且允許雙向移動
系統 SHALL 允許首次 fix 命中行程任意可靠位置及後續正向或反向移動，並使用本地滯回與跨區確認抑制定位噪聲。

#### Scenario: 首次可靠匹配位於行程中段
- **WHEN** 詳情頁取得的第一個可靠 fix 位於任一中間站點、巴士邊、換乘或步行分段
- **THEN** 系統 SHALL 直接接受該位置
- **AND** 系統 SHALL NOT 假設使用者必須先經過查詢起點或前序分段

#### Scenario: 使用者沿行程正向或反向移動
- **WHEN** 連續可靠 fixes 從目前區域移到相鄰節點或邊
- **THEN** 系統 SHALL 以相同規則接受正向及反向轉移
- **AND** 系統 SHALL NOT 因方向與規劃次序相反而隱藏可靠結果

#### Scenario: 節點與相鄰邊邊界輕微抖動
- **WHEN** 連續 fixes 在目前節點與其相鄰邊約 `15 米` 本地滯回範圍內往返
- **THEN** 系統 SHALL 保持已確認區域，直到新候選明確越過滯回邊界

#### Scenario: 候選跨越非相鄰區域
- **WHEN** 新鮮 fix 指向與目前已確認區域不相鄰的遠端可靠區域
- **THEN** 系統 SHALL 等待連續兩個新鮮 fixes 指向同一新區域後才確認轉移
- **AND** 第一個跳動 fix SHALL NOT 移動或顯示舊位置指示

#### Scenario: 等待跨區確認時出現歧義
- **WHEN** 第二個 fix 未確認同一新區域、變為低精度或候選歧義
- **THEN** 系統 SHALL 清除待確認轉移並保持指示隱藏

### Requirement: 目前位置狀態不改變規劃與動態資料語義
系統 SHALL 只把裝置目前位置表示為相對於候選規劃行程的獨立狀態，並 SHALL NOT 將其解釋為車輛位置、導航進度或路線完成度。

#### Scenario: 顯示可靠行程位置
- **WHEN** 摘要或詳細時間線顯示目前位置指示
- **THEN** 已行經巴士實線、步行虛線及地圖路徑 SHALL 保持原樣
- **AND** 系統 SHALL NOT 把任何路段標記為已步行、已乘坐或已完成

#### Scenario: 動態 ETA 刷新
- **WHEN** 首程 ETA 或 Citybus 動態詳情刷新而靜態站序、幾何與 CSDI paths 未改變
- **THEN** 目前位置匹配與行程軸 SHALL 保持穩定
- **AND** 定位狀態 SHALL NOT 改寫 ETA、計劃耗時、預計到達或路線排序

#### Scenario: 位置只在記憶體中使用
- **WHEN** 系統取得位置 fix 或確認行程位置
- **THEN** 系統 SHALL 只在目前前台詳情頁記憶體中使用該資料
- **AND** 系統 SHALL NOT 寫入資料庫、偏好、檔案、分析事件或包含原始坐標的常規日誌
- **AND** 系統 SHALL NOT 改寫查詢起點或終點

### Requirement: 目前位置提供去重且非操作性的無障礙狀態
系統 SHALL 以本地化狀態公告補充視覺位置指示，並 SHALL 避免把裝飾指示器建立為重複或可操作的焦點。

#### Scenario: 首次可靠位置靠近節點
- **WHEN** 本頁首次確認靠近一個站點或查詢端點
- **THEN** 輔助技術 SHALL 以 polite announcement 朗讀目前語言的「目前位置，靠近〈名稱〉」等效語義
- **AND** 視覺指示器 SHALL NOT 成為獨立按鈕或重複焦點

#### Scenario: 確認位於相鄰節點之間
- **WHEN** 已確認目標改為兩個相鄰站點或步行端點之間
- **THEN** 輔助技術 SHALL 朗讀目前語言的「目前位置，〈A〉與〈B〉之間」等效語義
- **AND** 第三方站名 SHALL 保持原文

#### Scenario: 相同區域持續更新
- **WHEN** 多個位置 fixes 維持同一已確認節點或相鄰區域
- **THEN** 系統 SHALL 更新必要視覺坐標而不重複公告相同狀態

#### Scenario: 位置變為不可靠
- **WHEN** 目前位置變為不可靠並隱藏指示器
- **THEN** 系統 SHALL NOT 朗讀推測位置或重複失敗公告
- **AND** 權限、系統定位及首 fix timeout 的可恢復提示 SHALL 依各自一次性規則處理
