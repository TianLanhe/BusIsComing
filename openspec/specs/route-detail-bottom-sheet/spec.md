# route-detail-bottom-sheet Specification

## Purpose
TBD - created by archiving change add-route-detail-bottom-sheet. Update Purpose after archive.
## Requirements
### Requirement: 按需查询 Citybus P2P 路线详情
系統 SHALL 在用戶點擊路線卡片後，使用候選的 P2P 詳情元數據及與其 `lid` 匹配的短期 Citybus session 按需請求路線詳情，且 SHALL NOT 為該請求設置靜態瀏覽器 header 或無關 Cookie。

#### Scenario: 構造詳情請求
- **WHEN** 系統獲得 `rawInfo`、`ginfo`、`lid`、`lang` 與可解析的匹配 session reference
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php`
- **AND** 請求 SHALL 攜帶 `info=<rawInfo>`、`ginfo=<ginfo>`、`lid=<lid>` 和 `l=<lang>`
- **AND** 請求 SHALL 只增加 `Cookie: PHPSESSID=<matching session value>`

#### Scenario: 詳情請求不攜帶瀏覽器 header 或無關 Cookie
- **WHEN** 系統發起 `getp2pstopinroute.php` 請求
- **THEN** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 發送 ad、consent、tracking、未知或其他模式的 Cookie
- **AND** session Cookie SHALL NOT 被轉送至 `getlinep2p.php`、Google、DATA.GOV.HK 或其他 host

#### Scenario: 點擊後展示載入狀態
- **WHEN** 用戶進入全屏路線詳情頁且詳情請求尚未完成
- **THEN** 頁面 SHALL 立即展示由路線結果提供的摘要與詳情載入狀態
- **AND** 載入狀態 SHALL NOT 清空來源頁已有路線結果
- **AND** 返回操作 SHALL 保持可用

#### Scenario: 詳情 session 可恢復
- **WHEN** 匹配 session 缺失、過期或上游回應呈現 session-missing 形態
- **THEN** 系統 SHALL 依 `citybus-route-query-api` 的受控恢復契約重建一次候選關聯並重試詳情
- **AND** 頁面 SHALL 保留啟動摘要及已成功載入的地圖內容

#### Scenario: 詳情接口不使用公共 API 兜底
- **WHEN** Citybus P2P 詳情請求、session 恢復或站點主結構解析最終失敗
- **THEN** 系統 SHALL 展示詳情失敗或部分資料狀態
- **AND** 系統 SHALL NOT 調用 DATA.GOV.HK route-stop 或 stop 接口重建路線詳情

#### Scenario: 語言或請求 generation 已過期
- **WHEN** 詳情請求執行期間實際語言改變、頁面被銷毀或重試建立新的 request generation
- **THEN** 系統 SHALL 取消舊工作或忽略舊回應
- **AND** 舊回應 SHALL NOT 覆蓋目前頁面的語言、session 關聯或較新結果

#### Scenario: 最小 session 詳情 live 驗證
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `getp2pstopinroute.php` SHALL 覆蓋繁體、簡體、英文及單段、多段有效樣本
- **AND** 每個樣本 SHALL 只使用匹配的 `PHPSESSID` 而不使用瀏覽器 header／無關 Cookie
- **AND** 每個樣本 SHALL 返回可解析站點、乘車段與預期數量的步行距離
- **AND** 驗證記錄 SHALL NOT 保存原始 `PHPSESSID`

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

### Requirement: 路線詳情辨識並展示分段步行完整性
系統 SHALL 分別解析與展示起點、每次步行換乘及終點步行距離，並區分完整、部分及 session 缺失結果；系統 SHALL NOT 從總距離反推未知分段。

#### Scenario: 單段路線完整距離
- **WHEN** Citybus 詳情為單段路線提供起點至上車站及下車站至終點兩個距離
- **THEN** 時間線 SHALL 在對應首尾步行段分別展示準確米數
- **AND** 詳情 SHALL 將兩段標記為完整

#### Scenario: 多段路線完整距離
- **WHEN** Citybus 詳情為多段路線提供起點、每次步行換乘及終點距離
- **THEN** 時間線 SHALL 依實際方案次序展示每個距離
- **AND** 距離數量 SHALL 等於乘車段數加一

#### Scenario: 同站換乘
- **WHEN** Citybus 詳情標記相鄰乘車段為同站換乘
- **THEN** 時間線 SHALL 顯示目前語言的「同站換乘」
- **AND** 系統 SHALL NOT 顯示 `0 米`、未知步行距離或步行虛線
- **AND** 同站換乘 SHALL NOT 被計入必要步行段數量

#### Scenario: 只有部分距離
- **WHEN** 站點與轉乘類型可解析但一個或多個必要步行距離缺失
- **THEN** 時間線 SHALL 顯示所有已知分段米數
- **AND** 未知段 SHALL 保留步行語義但不顯示推算數字
- **AND** 系統 SHALL 將整體標記為部分完整

#### Scenario: 三語兼容解析
- **WHEN** 繁體、簡體或英文詳情以 `showtimetable1(...)` 或對應 HTML 標籤提供步行距離
- **THEN** 系統 SHALL 解析相同的數值與分段次序
- **AND** parser SHALL NOT 只依賴繁體 `步行距離(約)` 文本

#### Scenario: session 缺失形態
- **WHEN** 站點仍可解析，但 timetable payload 為空且所有應有步行距離欄位同時為空
- **THEN** 系統 SHALL 將結果分類為 session 缺失並觸發一次受控恢復
- **AND** 系統 SHALL NOT 把該形態誤判為合法的全零或永久未知距離

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

### Requirement: 路线详情成功结果缓存 1 天
系統 SHALL 按資料語義分開快取成功解析的站點結構與步行距離，讓相同穩定查詢上下文可跨不同 session 重用一天；系統 SHALL NOT 把時間敏感資料或 session 身分併入同一長期快取。

#### Scenario: 快取成功站點結構
- **WHEN** 系統成功解析某個乘車方案在目前語言的站點、方向及穩定乘車段資料
- **THEN** 系統 SHALL 以 `plan fingerprint + lang` 在 App 進程內快取該結構 1 天
- **AND** cache key SHALL NOT 包含 `PHPSESSID`、session reference 或 `lid`

#### Scenario: 快取完整分段步行距離
- **WHEN** 系統對某一穩定起點、終點及乘車方案取得完整起點／轉乘／終點步行距離
- **THEN** 系統 SHALL 以穩定起點、終點及 plan fingerprint 在 App 進程內快取純數值距離 1 天
- **AND** 新 session 查詢相同上下文 SHALL 可命中該快取
- **AND** 不同起點或終點 SHALL NOT 串用首尾步行距離

#### Scenario: 地點穩定識別
- **WHEN** 起點或終點有可用 provider identifier
- **THEN** 步行 cache key SHALL 優先使用該 identifier
- **AND** identifier 缺失時 SHALL 使用送入 P2P 查詢的規範化坐標

#### Scenario: 計劃時間與 ETA 不進入一天快取
- **WHEN** 詳情包含計劃上車、下車、到達時間或 DATA.GOV.HK 即時 ETA
- **THEN** 系統 SHALL 使用本次查詢資料或各自既有短期刷新策略
- **AND** 系統 SHALL NOT 將這些時間敏感欄位作為一天詳情快取的一部分重用

#### Scenario: session 缺失空資料不快取
- **WHEN** 站點可解析但 `showtimetable1(...)` 與所有必要步行欄位因 session 缺失而為空
- **THEN** 系統 SHALL NOT 把空距離保存為成功步行快取
- **AND** 空距離 SHALL NOT 覆蓋已有完整步行快取

#### Scenario: 部分或失敗結果不污染完整快取
- **WHEN** 詳情只包含部分距離、請求失敗、回應為空或解析失敗
- **THEN** 系統 SHALL 只保存已通過其資料域完整性要求的內容
- **AND** 未知欄位 SHALL NOT 以零值寫入
- **AND** 後續恢復或重試 SHALL 可取得並覆蓋為新的完整資料

#### Scenario: 快取過期
- **WHEN** 某一結構或步行快取保存時間超過 1 天
- **THEN** 系統 SHALL 依目前有效 session 重新請求或恢復 Citybus 詳情
- **AND** 新的成功解析結果 SHALL 替換同資料域的舊快取

### Requirement: 路線卡片可開啟全屏詳情頁
系統 SHALL 允許用戶從任一共用路線結果卡片進入以 Google 地圖為背景的獨立全屏路線詳情頁，並 SHALL 在返回時恢復來源查詢上下文。

#### Scenario: 點擊有詳情元數據的路線卡片
- **WHEN** 用戶點擊包含可解析 P2P 詳情元數據的路線結果卡片非 ETA／通知專用區域
- **THEN** 系統 SHALL 立即開啟獨立全屏路線詳情頁
- **AND** 頁面 SHALL 先展示路線名、價格、總耗時、卡片步行摘要及可用候車狀態
- **AND** 頁面 SHALL 在不阻塞進入的情況下分別載入 Google Map、Citybus 詳情及分段幾何

#### Scenario: 返回來源結果
- **WHEN** 用戶在任一詳情窗檔位使用系統返回、返回手勢或頁面返回按鈕
- **THEN** 系統 SHALL 直接關閉詳情頁並顯示原來源頁的查詢結果、排序與捲動上下文
- **AND** 系統 SHALL NOT 先收合詳情窗
- **AND** 系統 SHALL NOT 因開啟詳情增加常用行程使用次數或重新執行路線查詢

#### Scenario: 點擊缺少詳情元數據的路線卡片
- **WHEN** 用戶點擊的路線結果缺少可解析 P2P 詳情元數據
- **THEN** 系統 SHALL 開啟全屏詳情頁並展示路線摘要與「路線詳情暫不可用」
- **AND** 頁面 SHALL 保留可獨立展示的地圖、查詢端點及目前位置
- **AND** 系統 SHALL NOT 發起 Citybus 路線詳情或路線幾何請求

#### Scenario: 頁面重建可恢復請求
- **WHEN** 全屏詳情頁因 process recreation 或 configuration change 重建
- **THEN** 系統 SHALL 從 primitive 啟動參數重建摘要、詳情請求及可用查詢起終點快照
- **AND** 系統 SHALL 恢復詳情窗檔位、已展開乘車段、所選站點及列表位置
- **AND** 系統 SHALL NOT 依賴來源頁仍保有原 `BusRouteOption` 記憶體物件

### Requirement: 路線詳情摘要展示可判定的完整方案指標
系統 SHALL 在 persistent bottom sheet 的摘要區展示路線鏈、總耗時、預計到達時間、總票價、總途經站數、步行距離及可用首程即時 ETA，並 SHALL 明確處理卡片摘要與完整分段的差異。

#### Scenario: 顯示路線摘要
- **WHEN** 系統有可用路線結果摘要
- **THEN** 摘要 SHALL 展示路線鏈、總耗時與總票價
- **AND** 有最終預計到達時間時摘要 SHALL 展示 `預計 HH:mm 到達` 或目前語言等效文案
- **AND** 摘要 SHALL 展示各乘車段途經站數之和且 SHALL NOT 重複計算上下車或換乘端點
- **AND** 有可靠首程即時 ETA 時摘要 SHALL 以緊湊形式展示該狀態

#### Scenario: 完整步行分段距離可用
- **WHEN** 起點、所有必要步行換乘與終點距離均已識別且完整
- **THEN** 詳情摘要 SHALL 顯示這些分段距離之和
- **AND** 摘要 SHALL 使用已確認的步行人物圖示與距離
- **AND** 系統 SHALL NOT 將完整合計回填至路線卡片或改變列表排序

#### Scenario: 完整步行合計不可判定
- **WHEN** 一個或多個必要步行段的距離缺失
- **THEN** 詳情摘要 SHALL 回退顯示 `ppsearch_p3.php` 路線卡片步行距離
- **AND** 摘要 SHALL 顯示目前語言的部分距離來源說明
- **AND** 系統 SHALL NOT 以缺失分段為零宣稱完整總量

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

### Requirement: 路線詳情支援三語、模式感知與無障礙
系統 SHALL 讓地圖背景、三段式詳情窗及其動態內容在繁體、簡體、英文、淺色、深色及大字體下保持可讀、可操作及可由輔助技術理解。

#### Scenario: 三語與深淺色
- **WHEN** 用戶以任一支援語言及外觀模式開啟詳情頁
- **THEN** App 自有標題、狀態、圖例、操作與格式 SHALL 使用目前語言資源
- **AND** 表面、分段色、文字、圖示、marker 及線條 SHALL 使用目前模式對應資源並保持對比
- **AND** 第三方站名、路線號與方向原文 SHALL NOT 被 App 機器翻譯

#### Scenario: Google 底圖標籤語言
- **WHEN** Google 底圖道路或 POI 標籤的語言與 App 內目前語言不同
- **THEN** 系統 SHALL 允許第三方底圖標籤沿用 Google／設備語言
- **AND** App 自有 marker 說明、圖例、錯誤與詳情內容 SHALL 繼續使用 App 目前語言

#### Scenario: TalkBack 讀取關鍵語義
- **WHEN** TalkBack 聚焦摘要、乘車段、步行段、地圖 marker、地圖控制或途經站控制
- **THEN** 系統 SHALL 讀出耗時、距離、預計／即時來源、路線號、站點角色、站數、操作及展開狀態等完整語義
- **AND** 裝飾實線、虛線、圓點及重複 marker SHALL NOT 造成重複朗讀
- **AND** 關鍵狀態 SHALL NOT 只由顏色或地圖位置表達

#### Scenario: 詳情窗把手可操作
- **WHEN** 觸控或輔助技術聚焦詳情窗把手
- **THEN** 把手 SHALL 提供至少 48dp 的操作區域及目前檔位語義
- **AND** 點擊 SHALL 讓摘要／半屏進入全屏，或讓全屏回到摘要

#### Scenario: 窄屏與大字體
- **WHEN** 詳情頁在約 360dp 寬度或 font scale 1.3／2.0 顯示
- **THEN** 摘要指標與地圖控制 SHALL 可換行、重排或避讓
- **AND** 長站名與方向 SHALL 可換行且內容 SHALL 可捲動至終點
- **AND** 核心文字 SHALL NOT 以不可讀縮字或固定高度裁切
- **AND** Google 標誌與法律文字 SHALL NOT 被詳情窗、控制或 WindowInsets 遮擋
