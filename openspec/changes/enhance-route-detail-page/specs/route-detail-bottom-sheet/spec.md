## REMOVED Requirements

### Requirement: 路线卡片可打开详情底部弹层
**Reason**: 長路線、大字體及後續 Google 地圖不適合繼續由 Bottom Sheet 承載；詳情入口改為獨立全屏頁。

**Migration**: 路線卡片點擊改為啟動全屏路線詳情頁，系統返回後恢復來源查詢結果、排序及捲動上下文。

### Requirement: 路線詳情內容優先處理巢狀滾動
**Reason**: 全屏頁使用單一 RecyclerView，不再存在詳情內容與 Bottom Sheet 拖動之間的巢狀捲動競爭。

**Migration**: 長內容、途經站展開及返回頂部均由全屏頁的單一垂直列表處理，不保留下拉收合或拖動關閉行為。

## MODIFIED Requirements

### Requirement: 按需查询 Citybus P2P 路线详情
系統 SHALL 在用戶點擊路線卡片後，使用該路線的 P2P 詳情元數據按需請求 Citybus 路線詳情，且 SHALL NOT 為該請求設置靜態瀏覽器 header。

#### Scenario: 構造詳情請求
- **WHEN** 系統獲得路線詳情查詢元數據 `rawInfo`、`ginfo`、`lid` 和 `lang`
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php`
- **AND** 請求 SHALL 攜帶 `info=<rawInfo>`、`ginfo=<ginfo>`、`lid=<lid>` 和 `l=<lang>`

#### Scenario: 詳情請求不攜帶瀏覽器 header
- **WHEN** 系統發起 `getp2pstopinroute.php` 請求
- **THEN** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 顯式設置 `Cookie`

#### Scenario: 點擊後展示載入狀態
- **WHEN** 用戶進入全屏路線詳情頁且詳情請求尚未完成
- **THEN** 頁面 SHALL 立即展示由路線結果提供的摘要與詳情載入狀態
- **AND** 載入狀態 SHALL NOT 清空來源頁已有路線結果
- **AND** 返回操作 SHALL 保持可用

#### Scenario: 詳情接口不使用公共 API 兜底
- **WHEN** Citybus P2P 詳情請求失敗、超時、返回空內容或解析失敗
- **THEN** 系統 SHALL 展示詳情失敗狀態
- **AND** 系統 SHALL NOT 調用 DATA.GOV.HK route-stop 或 stop 接口重建路線詳情

#### Scenario: 語言或請求 generation 已過期
- **WHEN** 詳情請求執行期間實際語言改變、頁面被銷毀或重試建立新的 request generation
- **THEN** 系統 SHALL 取消舊工作或忽略舊回應
- **AND** 舊回應 SHALL NOT 覆蓋目前頁面的語言或較新結果

#### Scenario: Header 清理後驗證詳情一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `getp2pstopinroute.php` SHALL 使用 10 個有效詳情樣本對比刪除 header 前後的響應
- **AND** 每個樣本 SHALL 返回 HTTP 200
- **AND** 每個樣本 SHALL 包含可解析路線詳情標記
- **AND** 業務簽名或 body hash SHALL 一致

### Requirement: 解析路线详情站点结构
系統 SHALL 將 Citybus P2P 詳情 HTML 解析為結構化路線詳情，包含每段巴士的上下車站、途經站、方向、分段票價、預計時間，以及可辨識的起點／換乘／終點步行段與換乘類型。

#### Scenario: 解析單段路線詳情
- **WHEN** Citybus P2P 詳情 HTML 包含一段巴士路線的站點列表
- **THEN** 系統 SHALL 解析該段 route variant、上車站、下車站和途經站
- **AND** 每個站點 SHALL 儘可能包含站點名稱、站點編號、站序、緯度和經度

#### Scenario: 站點展示名只使用逗號前第一段
- **WHEN** 系統解析到的站點名稱包含逗號
- **THEN** 系統 SHALL 將第一個逗號前的文本作為該站點的展示名
- **AND** 系統 SHALL 在詳情 UI 中展示該展示名
- **AND** 系統 SHALL 在結構化模型中保留完整原始站點名稱

#### Scenario: 路線段展示方向
- **WHEN** 系統解析到某段巴士的可靠方向資訊
- **THEN** 詳情 UI SHALL 在該路線段標題區域展示 `往 XX方向`
- **AND** `XX` SHALL 來自接口返回的方向資訊

#### Scenario: 路線段缺少方向
- **WHEN** 系統未解析到某段巴士的可靠方向資訊
- **THEN** 詳情 UI SHALL 隱藏該路線段的方向文本
- **AND** 系統 SHALL NOT 根據上車站、下車站或路線名自行推斷方向

#### Scenario: 解析多段轉乘路線詳情
- **WHEN** `rawInfo` 包含兩段或更多 bus legs，且詳情 HTML 包含對應站點列表
- **THEN** 系統 SHALL 按 `rawInfo` 中 bus leg 順序生成多個路線詳情分段
- **AND** 每個分段 SHALL 只包含該分段對應 route variant 的站點與方案資料

#### Scenario: 使用站序判定上下車和途經站
- **WHEN** 系統解析某段路線詳情站點
- **THEN** 站序等於該 leg `boardingSeq` 的站點 SHALL 標記為上車站
- **AND** 站序等於該 leg `alightingSeq` 的站點 SHALL 標記為下車站
- **AND** 站序位於上車和下車之間的站點 SHALL 標記為途經站

#### Scenario: 解析分段票價與預計時間
- **WHEN** Citybus 詳情回應包含可與 bus leg 可靠對齊的分段票價、預計上車時間或預計下車時間
- **THEN** 系統 SHALL 把這些欄位保存到對應結構化乘車段
- **AND** 系統 SHALL 將 Citybus 方案時間標記為預計資料而非即時 ETA

#### Scenario: 解析起點與終點步行段
- **WHEN** Citybus 詳情回應包含起點前往首個上車站及末個下車站前往終點的步行資訊
- **THEN** 系統 SHALL 分別建立起點與終點步行段
- **AND** 每個步行段 SHALL 獨立保存可選距離，不得合併後丟失來源

#### Scenario: 解析步行換乘
- **WHEN** Citybus 詳情回應標示需要前往轉車站
- **THEN** 系統 SHALL 建立步行換乘段並保存下一上車站
- **AND** 回應包含換乘距離時系統 SHALL 保存該距離
- **AND** 距離缺失時系統 SHALL 保留步行換乘類型而不推算數字

#### Scenario: 解析同站換乘
- **WHEN** Citybus 詳情回應標示同站換乘
- **THEN** 系統 SHALL 將換乘類型標記為同站換乘
- **AND** 系統 SHALL NOT 為該換乘建立虛假的步行距離

#### Scenario: 可選欄位缺失時保留站點主結構
- **WHEN** 站點結構可解析但部分步行距離、票價、方向或預計時間缺失
- **THEN** 系統 SHALL 返回已解析的站點與其他可靠欄位
- **AND** 系統 SHALL 將缺失欄位保持為空
- **AND** 系統 SHALL NOT 因單一可選欄位缺失而令整頁詳情失敗

#### Scenario: 解析失敗不影響路線列表
- **WHEN** Citybus P2P 詳情 HTML 無法解析為有效站點結構
- **THEN** 系統 SHALL 在全屏詳情頁展示「路線詳情暫不可用」
- **AND** 用戶返回後來源路線結果列表 SHALL 保持可用

### Requirement: 途经站默认折叠并可按段展开
系統 SHALL 在全屏路線詳情頁中預設折疊每段巴士的途經站，並允許用戶以卡片外控制行按段獨立展開或收起。

#### Scenario: 預設折疊每段途經站
- **WHEN** 結構化路線詳情載入成功
- **THEN** 每段巴士詳情 SHALL 預設展示路線號、可選方向、上車站和下車站
- **AND** 每段途經站 SHALL 預設折疊

#### Scenario: 折疊狀態展示途經站數量
- **WHEN** 某段巴士包含一個或多個途經站且處於折疊狀態
- **THEN** 系統 SHALL 在乘車卡片外展示 `N 個途經站` 控制行及向下 Chevron
- **AND** `N` SHALL 等於該段上車站和下車站之間的途經站數量
- **AND** 控制行 SHALL 提供至少 48dp 觸控範圍

#### Scenario: 展開單段途經站
- **WHEN** 用戶點擊某段的途經站控制行
- **THEN** 系統 SHALL 在主時間線原位展示該段全部途經站、前置圓點及分段實線
- **AND** 展開內容 SHALL 位於乘車卡片外
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
系統 SHALL 在全屏路線詳情頁中使用縱向時間線展示起點步行、各段巴士、換乘、終點步行與目的地，並以線型、節點、文字及分段顏色共同表達語義。

#### Scenario: 每段巴士使用分色粗豎線
- **WHEN** 結構化路線詳情包含一段或多段巴士
- **THEN** 詳情 UI SHALL 為每段巴士展示一條粗實線
- **AND** 相鄰路線段 SHALL 使用不同且模式感知的顏色
- **AND** 路線牌 SHALL 使用與該段實線一致的顏色
- **AND** 粗實線 SHALL 只表達路線分段，不表達車輛即時狀態或官方路線色

#### Scenario: 上下車站作為路線段端點
- **WHEN** 某段巴士詳情展示成功
- **THEN** 詳情 UI SHALL 將上車站和下車站展示為該段粗實線的端點
- **AND** 途經站展開後 SHALL 展示在該段上車站和下車站之間

#### Scenario: 步行段使用細虛線
- **WHEN** 詳情包含起點、換乘或終點步行段
- **THEN** 詳情 UI SHALL 使用中性細虛線及步行人物圖示展示該段
- **AND** 可用距離 SHALL 與步行人物圖示共同展示
- **AND** 缺少距離時系統 SHALL 顯示步行語義但 SHALL NOT 顯示推算數字

#### Scenario: 同站換乘不顯示步行距離
- **WHEN** 結構化詳情標記兩段巴士為同站換乘
- **THEN** 詳情 UI SHALL 顯示「同站換乘」節點
- **AND** 詳情 UI SHALL NOT 顯示步行虛線或步行距離

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

### Requirement: 路线详情支持失败重试
系統 SHALL 在全屏路線詳情請求或站點主結構解析失敗時展示可恢復的失敗狀態並允許用戶重試。

#### Scenario: 詳情請求失敗
- **WHEN** Citybus P2P 詳情請求失敗、超時或站點主結構無法解析
- **THEN** 全屏詳情頁 SHALL 保留路線摘要、App Bar 與返回操作
- **AND** 正文 SHALL 展示「路線詳情暫不可用」及重試入口

#### Scenario: 用戶點擊重試
- **WHEN** 用戶在詳情失敗狀態點擊重試
- **THEN** 系統 SHALL 重新發起同一條路線及目前實際語言的 Citybus P2P 詳情請求
- **AND** 頁面 SHALL 重新展示載入狀態
- **AND** 舊請求後續結果 SHALL 被取消或忽略

#### Scenario: 返回失敗頁
- **WHEN** 用戶在詳情失敗狀態使用系統返回或 App Bar 返回
- **THEN** 系統 SHALL 關閉全屏詳情頁
- **AND** 來源路線結果列表及排序 SHALL 保持可用

#### Scenario: 部分欄位缺失
- **WHEN** 站點主結構解析成功但一個或多個可選欄位缺失
- **THEN** 系統 SHALL 展示已解析詳情並隱藏缺失值
- **AND** 系統 SHALL NOT 只因可選欄位缺失而展示整頁失敗狀態

## ADDED Requirements

### Requirement: 路線卡片可開啟全屏詳情頁
系統 SHALL 允許用戶從任一共用路線結果卡片進入獨立全屏路線詳情頁，並在返回時恢復來源查詢上下文。

#### Scenario: 點擊有詳情元數據的路線卡片
- **WHEN** 用戶點擊包含可解析 P2P 詳情元數據的路線結果卡片非 ETA／通知專用區域
- **THEN** 系統 SHALL 開啟獨立全屏路線詳情頁
- **AND** 頁面 SHALL 立即展示路線名、價格、總耗時、卡片步行摘要及可用候車狀態

#### Scenario: 返回來源結果
- **WHEN** 用戶從全屏詳情頁返回
- **THEN** 系統 SHALL 顯示原來源頁的查詢結果、排序與捲動上下文
- **AND** 系統 SHALL NOT 因開啟詳情增加常用行程使用次數或重新執行路線查詢

#### Scenario: 點擊缺少詳情元數據的路線卡片
- **WHEN** 用戶點擊的路線結果缺少可解析 P2P 詳情元數據
- **THEN** 系統 SHALL 開啟全屏詳情頁並展示路線摘要與「路線詳情暫不可用」
- **AND** 系統 SHALL NOT 發起 Citybus 路線詳情請求

#### Scenario: 頁面重建可恢復請求
- **WHEN** 全屏詳情頁因 process recreation 或 configuration change 重建
- **THEN** 系統 SHALL 從可恢復的啟動參數重建摘要與詳情請求
- **AND** 系統 SHALL NOT 依賴來源頁仍保有原 `BusRouteOption` 記憶體物件

### Requirement: 路線詳情摘要展示可判定的完整方案指標
系統 SHALL 在全屏詳情頁頂部展示路線鏈、總耗時、預計到達時間、總票價、總途經站數與步行距離，並明確處理卡片摘要與完整分段的差異。

#### Scenario: 顯示路線摘要
- **WHEN** 系統有可用路線結果摘要
- **THEN** 頁面 SHALL 展示路線鏈、總耗時與總票價
- **AND** 有最終預計到達時間時頁面 SHALL 展示 `預計 HH:mm 到達` 或目前語言等效文案
- **AND** 頁面 SHALL 展示各乘車段途經站數之和且 SHALL NOT 重複計算上下車或換乘端點

#### Scenario: 完整步行分段距離可用
- **WHEN** 起點、所有必要換乘與終點步行段均已識別且距離可用
- **THEN** 詳情摘要 SHALL 顯示這些分段距離之和
- **AND** 摘要 SHALL 使用已確認的步行人物圖示與距離
- **AND** 系統 SHALL NOT 將完整合計回填至路線卡片或改變列表排序

#### Scenario: 完整步行合計不可判定
- **WHEN** 一個或多個必要步行段的距離缺失
- **THEN** 詳情摘要 SHALL 回退顯示路線卡片步行距離
- **AND** 系統 SHALL 保留該值不是已知完整分段合計的狀態
- **AND** 系統 SHALL NOT 以缺失分段為零宣稱完整總量

#### Scenario: 未接入 Google 地圖
- **WHEN** 目前版本尚未提供 Google 地圖模組
- **THEN** 摘要與時間線 SHALL 直接相鄰或以正常間距排列
- **AND** 頁面 SHALL NOT 為未實作地圖保留可見空白區域

### Requirement: 路線詳情支援三語、模式感知與無障礙
系統 SHALL 讓全屏詳情頁及其動態內容在繁體、簡體、英文、淺色、深色及大字體下保持可讀、可操作及可由輔助技術理解。

#### Scenario: 三語與深淺色
- **WHEN** 用戶以任一支援語言及外觀模式開啟詳情頁
- **THEN** App 自有標題、狀態、操作與格式 SHALL 使用目前語言資源
- **AND** 表面、分段色、文字、圖示及描邊 SHALL 使用目前模式對應資源
- **AND** 第三方站名、路線號與方向原文 SHALL NOT 被 App 機器翻譯

#### Scenario: TalkBack 讀取關鍵語義
- **WHEN** TalkBack 聚焦摘要、乘車段、步行段或途經站控制
- **THEN** 系統 SHALL 讀出耗時、距離、預計／即時來源、路線號、站數及展開狀態等完整語義
- **AND** 裝飾實線、虛線及圓點 SHALL NOT 重複朗讀
- **AND** 關鍵狀態 SHALL NOT 只由顏色表達

#### Scenario: 窄屏與大字體
- **WHEN** 詳情頁在約 360dp 寬度或 font scale 1.3／2.0 顯示
- **THEN** 摘要指標 SHALL 可換行或重排
- **AND** 長站名與方向 SHALL 可換行且內容 SHALL 可捲動至終點
- **AND** 核心文字 SHALL NOT 以不可讀縮字或固定高度裁切
