## MODIFIED Requirements

### Requirement: 地圖展示完整路線角色與示意步行
系統 SHALL 在地圖上展示所有可靠巴士站、分段巴士道路、查詢起終點、轉乘與成功 CSDI 步行軌跡，並以形狀、線型、文字、時間線及無障礙描述共同表達角色；頁面 SHALL NOT 顯示常駐路線圖例，亦 SHALL NOT 以端點直線冒充步行路線。

#### Scenario: 展示單段巴士路線
- **WHEN** 單段路線的站點與道路幾何可用
- **THEN** 系統 SHALL 使用與時間線一致的分段色繪製帶對比描邊實線
- **AND** 系統 SHALL 展示上車站、所有途經站與下車站
- **AND** 普通途經站 SHALL 使用低強度小圓點且不預設顯示全部站名

#### Scenario: 展示多段轉乘路線
- **WHEN** 路線包含兩個或更多乘車段
- **THEN** 系統 SHALL 依乘車段次序使用可辨識的不同分段色
- **AND** marker SHALL 顯示其所屬路線與上車、下車或轉乘角色
- **AND** 顏色 SHALL NOT 是唯一角色資訊

#### Scenario: 展示同站轉乘
- **WHEN** Citybus 詳情把兩段路線標記為同站轉乘
- **THEN** 系統 SHALL 使用單一複合轉乘 marker
- **AND** 系統 SHALL NOT 繪製步行紋理、步行距離或 CSDI 軌跡

#### Scenario: 展示成功步行轉乘軌跡
- **WHEN** Citybus 詳情把兩段路線標記為步行轉乘且該 CSDI 分段成功
- **THEN** 系統 SHALL 保留前段下車站與後段上車站兩個角色
- **AND** 系統 SHALL 把同一有序子路徑投影到屏幕，以固定屏幕間距及較粗灰色開放折角依序繪製 CSDI 回應的每個獨立子路徑
- **AND** 每個折角 SHALL 按所在位置的局部屏幕切線定向，地圖 SHALL NOT 另畫灰色實線、點線或虛線底圖
- **AND** 系統 SHALL NOT 以坐標相同為由改寫 Citybus 轉乘類型

#### Scenario: 展示成功首尾步行軌跡
- **WHEN** 起點或終點必要步行段取得 CSDI 成功結果
- **THEN** 系統 SHALL 以綁定同一有序子路徑的較粗灰色開放折角依序繪製該分段的每個獨立子路徑
- **AND** 地圖 SHALL NOT 另畫灰色實線、點線或虛線底圖
- **AND** 系統 SHALL 把軌跡描述為規劃預覽而非逐步導航或即時引導

#### Scenario: 多個子路徑不補畫連接線
- **WHEN** 一個 CSDI 成功分段包含兩個或更多 geometry paths
- **THEN** 每個子路徑 SHALL 以 `步行分段 + path 次序` 形成穩定渲染身份並獨立繪製
- **AND** 系統 SHALL NOT 在子路徑空隙、首尾 marker 或其他幾何之間補畫直線

#### Scenario: 步行段查詢中或失敗
- **WHEN** 某步行段仍在查詢、最終失敗、回退 Citybus 距離或端點不可可靠確定
- **THEN** 地圖 SHALL 保留該段端點 marker 及其他已成功巴士或步行內容
- **AND** 地圖 SHALL NOT 為該段繪製直線、虛假軌跡或失敗佔位線

#### Scenario: 漸進結果只更新對應軌跡
- **WHEN** CSDI 分段以任意次序成功或有效狀態被替換
- **THEN** renderer SHALL 依穩定子路徑身份只新增、更新或移除對應 path presentation
- **AND** 其他 marker、巴士幾何及成功步行軌跡 SHALL 保持不變

#### Scenario: 地圖不顯示路線圖例
- **WHEN** 地圖區域在摘要、半屏或全屏任一詳情窗檔位可見
- **THEN** 頁面 SHALL NOT 顯示「巴士路線」、「步行連接」或等效浮動圖例
- **AND** 圖例 SHALL NOT 保留空白容器、觸控目標或無障礙節點
- **AND** 巴士實線、CSDI 步行開放折角、marker、時間線及無障礙描述 SHALL 保持可用

#### Scenario: 移除圖例後地圖安全區
- **WHEN** 圖例被移除且 WindowInsets、資料署名或 bottom sheet 高度改變
- **THEN** Google Logo、必要法律文字、CSDI 署名、返回、目前位置及全覽控件 SHALL 保持可見且不互相遮擋
- **AND** 頁面 SHALL NOT 新增另一個常駐路線說明卡取代圖例

### Requirement: 地圖相機與控件尊重使用者探索
系統 SHALL 以香港作地圖首幀，在可靠站點結構首次可用時最多自動全覽一次，並在後續幾何、步行軌跡及 bottom sheet 變化中保留使用者鏡頭，除非用戶明確選擇定位、全覽或站點。

#### Scenario: 尚無路線結構時先顯示香港
- **WHEN** Google Map 已建立但可靠站點結構尚未可用
- **THEN** 地圖 SHALL 以預設香港中心及適當城市層級顯示
- **AND** 地圖 SHALL NOT 先定位至 `(0,0)`、非洲或以設備位置取代查詢路線

#### Scenario: 可靠結構首次到達時自動全覽
- **WHEN** 查詢端點及可靠巴士站結構首次可用，且使用者尚未操作地圖
- **THEN** 地圖 SHALL 最多一次調整相機以包含查詢起點、所有可靠站點及查詢終點
- **AND** 遠離路線的設備目前位置 SHALL NOT 強制加入初始 bounds

#### Scenario: 晚到幾何不搶奪相機
- **WHEN** 初始結構全覽已完成後，巴士道路幾何或 CSDI 步行 paths 漸進到達
- **THEN** 地圖 SHALL 增量繪製內容而 SHALL NOT 自動改變 target、zoom、bearing 或 tilt

#### Scenario: 使用者先操作地圖
- **WHEN** 使用者在可靠站點結構完成前已平移、縮放或以其他手勢操作地圖
- **THEN** 後續站點、巴士幾何及 CSDI paths SHALL NOT 觸發自動全覽
- **AND** 相機所有權 SHALL 保持由使用者控制

#### Scenario: bottom sheet 改變高度
- **WHEN** bottom sheet 在三個檔位之間移動
- **THEN** 系統 SHALL 更新 Google Map padding 與可用視口
- **AND** 系統 SHALL NOT 重置使用者已調整的 zoom、bearing 或 target

#### Scenario: 點擊全覽路線
- **WHEN** 用戶點擊全覽路線控件
- **THEN** 地圖 SHALL 以目前全部查詢端點、可靠站點、巴士幾何及成功 CSDI paths 重新顯示完整查詢行程

#### Scenario: 點擊目前位置
- **WHEN** 位置權限已授予且用戶點擊目前位置控件
- **THEN** 地圖 SHALL 把設備藍點移入可見區域
- **AND** 地圖 SHALL NOT 因此進入持續相機跟隨

#### Scenario: 精簡地圖控件
- **WHEN** 路線詳情地圖顯示成功
- **THEN** 地圖 SHALL 支援平移與縮放並停用旋轉和傾斜
- **AND** 頁面 SHALL NOT 提供交通、衛星、地圖類型、回饋、縮放加減或 Google 公交圖層控件

#### Scenario: Google attribution 不被遮擋
- **WHEN** bottom sheet、CSDI 署名或 WindowInsets 改變地圖可見區域
- **THEN** Google Logo 與必要法律文字 SHALL 保持可見且不可被詳情窗、署名或控件遮擋

## ADDED Requirements

### Requirement: 實際展示地政總署資料時提供可見署名
系統 SHALL 在地圖實際顯示至少一條 CSDI 步行 path 時展示官方地政總署標誌、約 116×29dp 的精簡雙行來源／版權及可開啟的完整說明，並 SHALL 在沒有 CSDI path 時隱藏該署名。

#### Scenario: 第一條 CSDI path 顯示署名
- **WHEN** 地圖開始實際顯示第一條 CSDI 步行 path
- **THEN** 地圖安全區 SHALL 顯示約 116×29dp 的精簡署名背景及約 15dp 的官方地政總署標誌
- **AND** 繁體 SHALL 顯示 `步行：地政總署 · CSDI` 與 `© 香港特區政府` 兩行精簡文字
- **AND** 簡體及英文 SHALL 使用各自審校的等效資源
- **AND** 署名 SHALL 使用低對比淺色半透明 surface，且 SHALL NOT 加入粗描邊或厚重陰影

#### Scenario: 開啟完整來源與免責說明
- **WHEN** 用戶啟用 CSDI 精簡署名
- **THEN** 系統 SHALL 提供可開啟的完整資料來源、版權及免責說明
- **AND** 該操作 SHALL 具有目前語言的可理解標籤及 TalkBack 語義

#### Scenario: 精簡外觀仍保持有效觸控區
- **WHEN** CSDI 精簡署名可見
- **THEN** 可見背景 SHALL 保持約 116×29dp，而整個署名操作 SHALL 提供至少 48dp 高的有效觸控區
- **AND** 有效觸控區 SHALL NOT 迫使可見背景、標誌或文字同步放大

#### Scenario: 大型字體保持地圖比例
- **WHEN** 系統字體比例高於約 1.3
- **THEN** 地圖上的精簡署名文字 SHALL 以約 1.3 倍為可見放大上限，並保持兩行內容不被裁切
- **AND** 點擊後的完整來源、版權及免責說明 SHALL 繼續跟隨正常系統字體比例

#### Scenario: 沒有實際 CSDI path
- **WHEN** 所有步行段仍在查詢、SameStop、失敗、回退或因端點不可靠而沒有 CSDI path
- **THEN** 地圖 SHALL 隱藏地政總署標誌及精簡署名
- **AND** Google 自身 Logo 與法律文字 SHALL 繼續按其契約顯示

#### Scenario: 署名避讓地圖與詳情控件
- **WHEN** WindowInsets 或 bottom sheet 高度令地圖安全區改變
- **THEN** CSDI 署名 SHALL 與 Google Logo、法律文字、返回、目前位置、全覽控件及詳情窗互相避讓
- **AND** 署名 SHALL NOT 被裁切、遮擋或成為常駐路線圖例
- **AND** 站名碰撞策略 SHALL 把目前可見署名矩形加必要安全邊距視為不可覆蓋的保留區域，而 SHALL NOT 使用放大的觸控矩形作視覺碰撞範圍

#### Scenario: 詳情窗拖動期間署名平滑避讓
- **WHEN** 用戶連續拖動 bottom sheet 且 CSDI 署名可見
- **THEN** 署名 SHALL 以平移跟隨詳情窗安全邊界，而 SHALL NOT 在每個 motion frame 重設 layout 尺寸或 margin
- **AND** bottom sheet 停在穩定 detent 後，系統 SHALL 才套用精確署名 layout margin
