## ADDED Requirements

### Requirement: 摘要行動鏈展示目前位置 pin
系統 SHALL 在路線詳情摘要的完整行動鏈上方，以隨內容捲動的藍色 pin 表達目前可靠位置所屬分段及段內進度，並 SHALL NOT 混合巴士站序與步行距離形成虛假全程比例。

#### Scenario: 摘要顯示固定比例 pin
- **WHEN** 系統已可靠匹配目前位置且對應摘要分段可用
- **THEN** 摘要 SHALL 在行動鏈內容層上方顯示固定 `18dp × 22dp` 的藍色地圖 pin
- **AND** pin SHALL 使用完整水滴輪廓及尖銳末端，尖端 SHALL 精確落在段內進度錨點
- **AND** pin SHALL 保持固定比例而不得被父容器拉伸、壓扁或以直線代替尖尾

#### Scenario: 巴士段內按站序顯示進度
- **WHEN** 目前位置可靠匹配到具有 `E` 個相鄰站點邊的巴士段
- **THEN** 第 `i` 個站點的摘要進度 SHALL 為 `i / E`
- **AND** 第 `i` 與第 `i+1` 個站點之間的摘要進度 SHALL 為 `(i + 0.5) / E`
- **AND** 系統 SHALL NOT 以巴士道路距離、時間或 ETA 改寫該站序比例

#### Scenario: 步行段內按實際 path 距離顯示進度
- **WHEN** 目前位置可靠匹配到成功 CSDI 步行分段
- **THEN** 摘要 pin SHALL 使用該分段實際有序子 paths 的累計距離比例
- **AND** 系統 SHALL NOT 把子 path 空隙、Citybus fallback 距離或其他乘車段站數混入該比例

#### Scenario: 同站換乘及行程端點
- **WHEN** 目前位置可靠匹配同站換乘複合節點、查詢起點或查詢終點
- **THEN** 同站換乘 pin SHALL 對齊對應換乘塊中心
- **AND** 查詢起點及終點 SHALL 分別對齊首段及末段行動鏈的端點

#### Scenario: pin 跟隨摘要內容水平捲動
- **WHEN** 行動鏈超出可用寬度且系統首次可靠匹配或確認目標分段改變
- **THEN** 摘要 SHALL 在使用者尚未手動水平捲動時把 pin 所在分段移入可見區
- **AND** pin SHALL 與行動鏈內容共用坐標並隨內容捲動

#### Scenario: 使用者取得摘要水平捲動所有權
- **WHEN** 使用者手動水平捲動摘要行動鏈
- **THEN** 系統 SHALL 在本次詳情頁會話內停止自動搶奪摘要水平視口
- **AND** pin SHALL 繼續在正確內容坐標更新，即使其暫時捲出畫面

#### Scenario: 摘要 pin 不攔截既有操作
- **WHEN** 使用者點擊或以輔助技術操作 pin 下方的摘要分段
- **THEN** 原摘要分段點擊、捲動、焦點順序及至少 `48dp` 有效操作高度 SHALL 保持可用
- **AND** pin SHALL NOT 建立獨立可點擊區、焦點或重複朗讀

#### Scenario: 目前位置不可可靠表示
- **WHEN** 位置不可靠、目標分段不可匹配或摘要結構 identity 已過期
- **THEN** 摘要 SHALL 隱藏 pin
- **AND** 摘要其他耗時、行動鏈、站數、步行距離、票價及 ETA SHALL 保持可用

### Requirement: 詳細時間線跟隨可靠目前位置
系統 SHALL 在半屏或全屏詳細時間線顯示目前位置指示器，並 SHALL 只使用已完成 layout 的可見節點與軸段 anchor 定位，不估算隱藏或未建立的列表幾何。

#### Scenario: 指示器對齊站點
- **WHEN** 目前位置可靠匹配一個目前可見的時間線節點
- **THEN** 指示器藍色圓環的幾何圓心 SHALL 與路線軸及該節點圓心精確重合
- **AND** 系統 SHALL NOT 以左側 padding 或偏移補償製造視覺對齊

#### Scenario: 指示器對齊兩站之間
- **WHEN** 目前位置可靠匹配兩個相鄰且目前可見的巴士站點之間
- **THEN** 指示器圓心 SHALL 固定對齊這兩個站點軸段的視覺中點
- **AND** 系統 SHALL NOT 按道路距離比例、時間、ETA 或列表內容高度內插其他位置

#### Scenario: 指示器對齊步行進度
- **WHEN** 目前位置可靠匹配一段目前可見且可定位的步行軸
- **THEN** 指示器圓心 SHALL 按可靠 CSDI path 累計距離比例對齊該步行軸段
- **AND** 步行虛線及其他時間線內容 SHALL 保持原有語義

#### Scenario: 詳情展開後首次自動定位
- **WHEN** 使用者把詳情窗展開至可閱讀詳細時間線的檔位且已有可靠目前位置
- **THEN** 系統 SHALL 在必要的自動展開及 RecyclerView layout 完成後把指示器捲入可見區一次
- **AND** 自動定位 SHALL NOT 改變地圖相機所有權或摘要水平捲動所有權

#### Scenario: 可靠目標在跟隨期間改變
- **WHEN** 使用者尚未手動縱向捲動且已確認目前位置移到另一可見區域
- **THEN** 系統 SHALL 讓詳細列表繼續把新指示器位置保持在可見範圍
- **AND** 相同位置 fix SHALL NOT 重複觸發無效捲動

#### Scenario: 使用者取得詳細列表捲動所有權
- **WHEN** 使用者手動縱向捲動詳細列表
- **THEN** 系統 SHALL 停止目前詳細檔位的自動跟隨
- **AND** 同一頁重新進入全屏檔位時系統 SHALL 恢復詳細自動跟隨並把目前可靠指示器移入可見區

#### Scenario: 目標 anchor 被收合或捲出
- **WHEN** 目前位置依賴已收合途經站，或必要節點／軸段 anchor 未同時可見
- **THEN** 詳細時間線 SHALL 隱藏位置指示器而不得以估算行高補畫
- **AND** 摘要 pin SHALL 在其資料仍可靠時繼續更新

#### Scenario: 過期列表幾何
- **WHEN** RecyclerView anchor 綁定舊 page generation、舊結構 identity 或已回收 child
- **THEN** overlay SHALL 忽略該 anchor
- **AND** 過期幾何 SHALL NOT 移動目前指示器或令列表跳動

## MODIFIED Requirements

### Requirement: 途经站默认折叠并可按段展开
系統 SHALL 在全屏路線詳情頁中預設折疊每段巴士的途經站，並允許用戶以乘車段內容區之外的控制行按段獨立展開或收起；可靠目前位置首次命中某乘車段時，系統 SHALL 在尊重使用者手動收合的前提下只自動展開該段一次。

#### Scenario: 預設折疊每段途經站
- **WHEN** 結構化路線詳情載入成功且尚無可靠目前位置命中乘車段
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

#### Scenario: 首次可靠位置命中乘車段
- **WHEN** 目前位置首次可靠匹配到一個具有途經站且尚未自動展開的乘車段
- **THEN** 系統 SHALL 只自動展開該乘車段一次
- **AND** 其他乘車段的折疊狀態 SHALL 保持不變
- **AND** bottom sheet 目前檔位 SHALL NOT 因此被強制改變

#### Scenario: 可靠位置命中步行分段
- **WHEN** 目前位置可靠匹配到起點、換乘或終點步行分段
- **THEN** 系統 SHALL NOT 自動展開任何無關乘車段

#### Scenario: 使用者手動收合自動展開分段
- **WHEN** 使用者手動收合一個曾因目前位置而自動展開的乘車段
- **THEN** 系統 SHALL 在本次詳情頁會話內尊重收合狀態並不再強制展開該段
- **AND** 依賴已隱藏途經站的詳細位置指示 SHALL 隱藏
- **AND** 摘要 pin SHALL 在位置仍可靠時繼續更新

#### Scenario: 目前位置進入另一乘車段
- **WHEN** 已確認目前位置從一個乘車段移到另一個尚未自動展開的乘車段
- **THEN** 新乘車段 SHALL 可自動展開一次
- **AND** 前一乘車段 SHALL 保持使用者或既有狀態而不被自動收合

### Requirement: 路线详情采用分段时间线视觉
系統 SHALL 在半屏及全屏路線詳情中使用輕量縱向時間線展示起點步行、各段巴士、換乘、終點步行與目的地，並以連續線型、清晰節點、文字及分段顏色共同表達語義；目前位置指示 SHALL 疊加於軸線而不改變原路線狀態。

#### Scenario: 每段巴士使用分色粗豎線
- **WHEN** 結構化路線詳情包含一段或多段巴士
- **THEN** 詳情 UI SHALL 為每段巴士展示約 `10dp`、圓角端點的連續粗實線
- **AND** 相鄰路線段 SHALL 使用不同且模式感知的顏色
- **AND** 路線牌 SHALL 使用與該段實線一致的顏色
- **AND** 粗實線 SHALL 只表達路線分段，不表達車輛即時狀態、已乘坐進度或官方路線色

#### Scenario: 普通途經站使用清晰節點
- **WHEN** 某段巴士的途經站處於展開狀態
- **THEN** 每個普通途經站 SHALL 在軸中心展示約 `10dp` 的高對比薄荷綠圓點及約 `2dp` 白色隔離邊界
- **AND** 節點邊界 SHALL 在淺色及深色模式保持清楚，不得使用模糊、低對比灰色代替

#### Scenario: 上下車站作為路線段端點
- **WHEN** 某段巴士詳情展示成功
- **THEN** 分段色實線 SHALL 連續穿過該段上車、途經及下車內容
- **AND** 上下車端點 SHALL 在軸中心使用約 `16dp` 白色底、約 `3dp` 深化分段色外框及約 `4dp` 角色色圓心
- **AND** UI SHALL NOT 疊加第二個端點節點、帶框卡片或與目前位置混淆的額外空心圓
- **AND** 上下車角色 SHALL 由節點、站名、位置及本地化語義共同清楚表達

#### Scenario: 整體起終點使用彩色圓心
- **WHEN** 時間線展示查詢起點及查詢終點
- **THEN** 起點 SHALL 使用白色圓環內綠色圓心
- **AND** 終點 SHALL 使用白色圓環內珊瑚紅圓心
- **AND** 起終點 SHALL 同時以文字及無障礙描述區分

#### Scenario: 步行段使用細虛線
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

#### Scenario: 顯示詳細目前位置指示器
- **WHEN** 目前位置可可靠映射到可見時間線 anchor
- **THEN** UI SHALL 顯示由約 `38dp` 柔和 halo、`26dp` 白色承托圓、外徑 `20dp` 藍色圓環及白色圓心構成的指示器
- **AND** 圓環右側 SHALL 在圓環後方使用基部及突出量約 `8dp` 的纖細單尖尾巴
- **AND** 尖尾 SHALL NOT 使用粗箭頭、雙尖、直線末端或方向語義
- **AND** 藍色圓環幾何圓心 SHALL 精確對齊路線軸中心

#### Scenario: 不展示本次範圍外操作
- **WHEN** 路線詳情展示成功
- **THEN** 詳情 UI SHALL NOT 展示步行導航、收藏、截圖、分享、關注路線或下車提醒入口
- **AND** 本次變更 SHALL NOT 顯示空白 Google 地圖佔位
