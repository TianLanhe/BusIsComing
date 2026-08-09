# route-detail-google-map Specification

## Purpose
記錄 Google 地圖路線詳情的漸進載入、完整路線語義、三段式詳情窗、雙向聯動、生命週期、無障礙及獨立降級契約。
## Requirements
### Requirement: 路線詳情使用 Google 地圖背景與漸進載入
系統 SHALL 在獨立路線詳情頁使用全屏 Google 地圖作為背景，並 SHALL 在地圖或 Citybus 詳情完成前立即展示既有路線摘要。

#### Scenario: 點擊路線後立即進入詳情
- **WHEN** 用戶從路線結果卡片開啟詳情
- **THEN** 系統 SHALL 立即開啟獨立詳情頁並展示路線鏈、總耗時、票價、步行摘要及可用首程 ETA
- **AND** 系統 SHALL NOT 等待 Google Map、Citybus 詳情或路線幾何完成才進入頁面

#### Scenario: 地圖與詳情漸進完成
- **WHEN** Google Map、Citybus 詳情與路線幾何以不同順序完成
- **THEN** 系統 SHALL 增量展示每一項可靠內容
- **AND** 較晚完成的項目 SHALL NOT 清空或重建已成功內容

#### Scenario: 缺少詳情元數據
- **WHEN** 路線缺少可解析 P2P 詳情元數據
- **THEN** 頁面 SHALL 保留啟動摘要與 Google Map 可用部分
- **AND** 頁面 SHALL 顯示路線詳情不可用
- **AND** 系統 SHALL NOT 發起 Citybus 詳情或幾何請求

### Requirement: 詳情啟動保存本次查詢起終點快照
系統 SHALL 讓路線詳情使用產生目前結果的成功查詢起終點，而 SHALL NOT 使用尚未重新查詢的新輸入。

#### Scenario: 從常用行程結果開啟
- **WHEN** 用戶從一個已保存行程的目前查詢結果開啟路線詳情
- **THEN** 系統 SHALL 把該次查詢對應的起終點名稱與坐標傳入詳情頁
- **AND** 開啟詳情 SHALL NOT 增加行程使用次數或重新查詢路線

#### Scenario: 從搜尋結果開啟
- **WHEN** 用戶從搜尋 destination 的結果開啟詳情
- **THEN** 系統 SHALL 使用最近一次成功查詢的起終點快照
- **AND** 系統 SHALL NOT 使用成功查詢後已編輯但尚未提交的新輸入

#### Scenario: 啟動快照缺失
- **WHEN** 詳情頁無法取得可靠查詢起點或終點快照
- **THEN** 系統 SHALL 省略缺失端點 marker 與其對應步行連接
- **AND** 巴士站、道路幾何與文字詳情 SHALL 保持可用

#### Scenario: 頁面 process recreation
- **WHEN** 詳情頁在 process recreation 後由 Intent extras 重建
- **THEN** 系統 SHALL 從 primitive 啟動參數恢復路線摘要、P2P 查詢與可用起終點快照
- **AND** 系統 SHALL NOT 依賴來源頁仍持有原物件

### Requirement: 地圖展示完整路線角色與示意步行
系統 SHALL 在地圖上展示所有可靠巴士站、分段巴士道路、查詢起終點、轉乘與成功 CSDI 步行軌跡，並以固定角色圖形、線型、文字、時間線及無障礙描述共同表達角色；頁面 SHALL NOT 顯示常駐路線圖例，亦 SHALL NOT 以端點直線冒充步行路線。

#### Scenario: 展示單段巴士路線
- **WHEN** 單段路線的站點與道路幾何可用
- **THEN** 系統 SHALL 使用與時間線一致的分段色繪製帶對比白色描邊實線
- **AND** 系統 SHALL 展示上車站、所有途經站與下車站
- **AND** 上車點 SHALL 為目前乘車段色實心圓內白色巴士正面圖形
- **AND** 下車點 SHALL 為目前乘車段色空心圓環內等比 `log-out` 圖形
- **AND** 普通途經站 SHALL 使用帶白色隔離邊緣的低強度中性小圓點且不預設顯示全部站名

#### Scenario: 展示查詢起終點
- **WHEN** 查詢起點或查詢終點坐標可用
- **THEN** 起點 SHALL 使用綠色、中心白色圓孔的地圖針
- **AND** 終點 SHALL 使用珊瑚紅、中心白色圓孔的地圖針
- **AND** 起終點角色 SHALL 以形狀及無障礙描述區分，不能只依賴顏色

#### Scenario: 展示多段轉乘路線
- **WHEN** 路線包含兩個或更多乘車段
- **THEN** 系統 SHALL 依乘車段次序使用可辨識的不同分段色
- **AND** marker SHALL 顯示其所屬路線與上車、下車或轉乘角色
- **AND** 顏色 SHALL NOT 是唯一角色資訊

#### Scenario: 展示同站轉乘
- **WHEN** Citybus 詳情把兩段路線標記為同站轉乘
- **THEN** 系統 SHALL 使用單一複合轉乘 marker
- **AND** marker SHALL 使用分別代表前後乘車段色的雙色圓環及中性環形換向箭頭
- **AND** 系統 SHALL NOT 疊放前段下車與後段上車 marker
- **AND** 系統 SHALL NOT 繪製步行紋理或步行距離

#### Scenario: 展示步行轉乘
- **WHEN** Citybus 詳情把兩段路線標記為步行前往轉車站且該 CSDI 分段成功
- **THEN** 系統 SHALL 保留前段下車站與後段上車站兩個角色
- **AND** 系統 SHALL 只沿 CSDI 回應的每個有序子路徑以較粗灰色開放折角表示步行軌跡
- **AND** 系統 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL NOT 以坐標相同為由改寫 Citybus 轉乘類型

#### Scenario: 展示首尾步行
- **WHEN** 起點或終點必要步行段取得 CSDI 成功結果
- **THEN** 系統 SHALL 只沿 CSDI 回應的每個有序子路徑使用較粗灰色開放折角展示首尾步行軌跡
- **AND** 系統 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL 把該紋理描述為規劃預覽而非逐步導航或即時引導

#### Scenario: 步行段查詢中或失敗
- **WHEN** 某步行段仍在查詢、SameStop、最終失敗、回退 Citybus 距離或端點不可可靠確定
- **THEN** 地圖 SHALL 保留該段端點 marker 及其他已成功巴士或步行內容
- **AND** 地圖 SHALL NOT 為該段繪製折角、直線、虛假軌跡或失敗佔位線

#### Scenario: 多個子路徑不補畫連接線
- **WHEN** 一個 CSDI 成功分段包含兩個或更多 `geometry.paths`
- **THEN** 每個子路徑 SHALL 以 `步行分段 + path 次序` 形成穩定渲染身份並獨立繪製
- **AND** 系統 SHALL NOT 在子路徑空隙、首尾 marker 或其他幾何之間補畫直線

#### Scenario: 漸進結果只更新對應軌跡
- **WHEN** CSDI 分段以任意次序成功或有效狀態被替換
- **THEN** renderer SHALL 依穩定子路徑身份只新增、更新或移除對應 path presentation
- **AND** 其他 marker、巴士幾何及成功步行軌跡 SHALL 保持不變

#### Scenario: 地圖不顯示路線圖例
- **WHEN** 地圖區域在摘要、半屏或全屏任一詳情窗檔位可見
- **THEN** 頁面 SHALL NOT 顯示「巴士路線」、「步行連接（示意）」或等效浮動圖例
- **AND** 圖例 SHALL NOT 保留空白容器、觸控目標或無障礙節點
- **AND** 巴士實線、方向折角、marker、時間線及無障礙描述 SHALL 保持可用

#### Scenario: 移除圖例後地圖安全區
- **WHEN** 圖例被移除且 WindowInsets 或 bottom sheet 高度改變
- **THEN** Google Logo、必要法律文字、CSDI 署名、返回、目前位置及全覽控件 SHALL 保持可見且不互相遮擋
- **AND** 頁面 SHALL NOT 新增另一個常駐說明卡取代圖例

### Requirement: 路線詳情使用三段式 persistent bottom sheet
系統 SHALL 讓詳情時間線成為不可隱藏的摘要／半屏／全屏三段式 persistent bottom sheet，並讓地圖與詳情手勢具有可預測優先級。

#### Scenario: 初次進入摘要態
- **WHEN** 詳情頁首次開啟且底圖沒有完全失敗
- **THEN** bottom sheet SHALL 停靠在摘要態
- **AND** 普通字體摘要高度 SHALL 以約 25% 至 30% 為目標並按實際內容自適應
- **AND** 摘要 SHALL 完整展示且 SHALL NOT 以縮字、裁切或內部捲動容納內容

#### Scenario: 大字體摘要自適應
- **WHEN** font scale 1.3 或 2.0 使摘要超出普通目標高度
- **THEN** 摘要態 SHALL 增加高度以容納核心內容
- **AND** 半屏態高度 SHALL 不低於摘要所需高度

#### Scenario: 摘要向上滑直接全屏
- **WHEN** 用戶從摘要內容區向上滑動
- **THEN** bottom sheet SHALL 直接進入全屏態
- **AND** 系統 SHALL NOT 強制在半屏態停留

#### Scenario: 拖動把手進入半屏
- **WHEN** 用戶拖動至少 48dp 可操作把手區並在中間高度放開
- **THEN** bottom sheet SHALL 可吸附至約 55% 的半屏態

#### Scenario: 全屏向下依次收合
- **WHEN** 時間線已位於頂部且用戶從全屏態向下拖動
- **THEN** bottom sheet SHALL 先進入半屏態，再次向下拖動後進入摘要態

#### Scenario: 摘要不可隱藏或拖動關閉
- **WHEN** 用戶在摘要態繼續向下拖動
- **THEN** bottom sheet SHALL 產生阻尼並回到摘要態
- **AND** 系統 SHALL NOT 隱藏詳情窗或退出頁面

#### Scenario: 點擊把手區
- **WHEN** 用戶點擊把手區而非拖動
- **THEN** 摘要態或半屏態 SHALL 進入全屏態
- **AND** 全屏態 SHALL 回到摘要態

#### Scenario: 任意檔位返回
- **WHEN** 用戶使用系統返回、返回手勢或頁面返回按鈕
- **THEN** 系統 SHALL 直接關閉詳情頁並返回原結果上下文
- **AND** 系統 SHALL NOT 先逐段收合 bottom sheet

### Requirement: 返回入口按詳情窗狀態遷移
系統 SHALL 在地圖可見狀態提供單一屏內返回入口，並 SHALL 在全屏詳情狀態把內容空間留給時間線而不新增屏內返回或標題列。

#### Scenario: 摘要或半屏返回入口
- **WHEN** bottom sheet 位於摘要態或半屏態
- **THEN** 返回按鈕 SHALL 以圓形浮動控件顯示在地圖左上安全區域
- **AND** 畫面 SHALL NOT 同時顯示第二個頁面返回入口

#### Scenario: 全屏返回入口
- **WHEN** bottom sheet 進入全屏態
- **THEN** 地圖浮動返回按鈕 SHALL 隨地圖隱藏
- **AND** 詳情窗 SHALL NOT 顯示「路線詳情」標題、Toolbar、App Bar 或任何屏內返回按鈕
- **AND** 內容 SHALL 在狀態列安全區及拖動把手之後直接鋪滿可用高度

#### Scenario: 任一檔位使用系統返回
- **WHEN** 用戶在摘要、半屏或全屏態使用 Android 系統返回手勢或按鍵
- **THEN** 系統 SHALL 直接關閉路線詳情並返回原結果上下文
- **AND** 系統 SHALL NOT 先逐段收合 bottom sheet

### Requirement: 地圖與時間線雙向聯動
系統 SHALL 讓可靠站點 marker 與現有時間線使用穩定對應關係互相選取、定位及展開。

#### Scenario: 摘要態點擊普通途經站
- **WHEN** 用戶在摘要態點擊一個尚未展開的途經站 marker
- **THEN** bottom sheet SHALL 進入半屏態
- **AND** 系統 SHALL 只展開該站所屬乘車段
- **AND** 時間線 SHALL 捲動並高亮該站

#### Scenario: 半屏態點擊站點
- **WHEN** 用戶在半屏態點擊站點 marker
- **THEN** bottom sheet SHALL 保持半屏態
- **AND** 時間線 SHALL 定位並高亮對應站點

#### Scenario: 再次點擊同一 marker
- **WHEN** 用戶再次點擊已選中途經站 marker
- **THEN** 系統 SHALL 保持該段展開
- **AND** 系統 SHALL NOT 把 marker 點擊當作收起操作

#### Scenario: 全屏時間線點擊站點
- **WHEN** 用戶在全屏態點擊時間線站點
- **THEN** bottom sheet SHALL 進入半屏態
- **AND** 地圖 SHALL 把對應 marker 移入目前可見區域並高亮

#### Scenario: 收合回摘要
- **WHEN** bottom sheet 從展開狀態回到摘要態
- **THEN** RecyclerView SHALL 在摘要顯示前回到頂部
- **AND** 摘要 SHALL 完整可見

### Requirement: 地圖相機與控件尊重使用者探索
系統 SHALL 以香港作地圖首幀，在可靠站點結構首次可用時最多自動全覽一次，並在後續幾何、步行軌跡及 bottom sheet 變化中保留使用者鏡頭，除非用戶明確選擇定位、全覽或站點。

#### Scenario: 尚無路線結構時先顯示香港
- **WHEN** Google Map 已建立但可靠站點結構尚未可用
- **THEN** 地圖 SHALL 以預設香港中心及適當城市層級顯示
- **AND** 地圖 SHALL NOT 先定位至 `(0,0)`、非洲或以設備位置取代查詢路線

#### Scenario: 首次完整路線全覽
- **WHEN** 查詢端點及可靠巴士站結構首次可用，且使用者尚未操作地圖
- **THEN** 地圖 SHALL 最多一次調整相機以包含查詢起點、所有可靠站點及查詢終點
- **AND** 遠離路線的設備目前位置 SHALL NOT 強制加入初始 bounds

#### Scenario: 晚到幾何不搶奪相機
- **WHEN** 初始結構全覽已完成後，巴士道路幾何或 CSDI 步行 paths 漸進到達
- **THEN** 地圖 SHALL 增量繪製內容而 SHALL NOT 自動改變 target、zoom、bearing 或 tilt

#### Scenario: 使用者手勢取得鏡頭所有權
- **WHEN** 使用者在可靠站點結構完成前已平移、縮放或以其他手勢操作地圖
- **THEN** 後續站點、巴士幾何及 CSDI paths SHALL NOT 觸發自動全覽
- **AND** 相機所有權 SHALL 保持由使用者控制

#### Scenario: 程式相機動畫不冒充使用者手勢
- **WHEN** 系統因首次全覽、全覽控件、目前位置或站點選擇而移動相機
- **THEN** 系統 SHALL NOT 將該程式移動誤判為使用者手勢
- **AND** 系統 SHALL 正確保存移動後的相機 snapshot

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

### Requirement: 目前位置只在詳情前台可選使用
系統 SHALL 在用戶控制下提供頁面前台目前位置，且 SHALL NOT 把設備位置改寫為查詢起點或保存為位置軌跡。

#### Scenario: 已有位置權限
- **WHEN** 詳情頁進入前台且 App 已有可用位置權限
- **THEN** 地圖 SHALL 顯示持續更新的 Google 原生藍點
- **AND** 相機 SHALL 保持由用戶控制

#### Scenario: 未授權時進入頁面
- **WHEN** 用戶尚未授予位置權限而開啟詳情頁
- **THEN** 系統 SHALL NOT 自動顯示權限對話框
- **AND** 地圖與路線詳情 SHALL 正常載入

#### Scenario: 點擊位置控件時請求
- **WHEN** 未授權用戶點擊目前位置控件
- **THEN** 系統 SHALL 請求適用的位置權限
- **AND** 一般拒絕、永久拒絕或系統定位關閉 SHALL 提供對應說明或設定入口

#### Scenario: 頁面離開前台
- **WHEN** 詳情頁進入後台或被關閉
- **THEN** 系統 SHALL 停止本頁位置更新
- **AND** 系統 SHALL NOT 申請背景定位或保存使用者軌跡

### Requirement: 地圖與詳情狀態可恢復且不跨開啟永久保存
系統 SHALL 在同一次詳情頁生命週期重建時恢復可序列化探索及相機所有權狀態，並在真正退出後讓下一次開啟回到摘要態與香港首幀，再按目前可靠路線執行一次自動全覽。

#### Scenario: configuration change 重建
- **WHEN** 詳情頁因旋轉、主題、語言或等效 configuration change 重建
- **THEN** 系統 SHALL 恢復 bottom sheet 檔位、相機、相機所有權、是否已自動全覽、選中站點、展開乘車段和列表位置
- **AND** GoogleMap、Marker 或 Polyline 實例 SHALL NOT 被直接保存

#### Scenario: MapView 生命週期
- **WHEN** Activity 收到建立、啟動、恢復、暫停、停止、低記憶體、保存狀態或銷毀事件
- **THEN** 系統 SHALL 把對應生命週期轉交 MapView
- **AND** 已銷毀頁面的 callback SHALL NOT 更新 UI

#### Scenario: 真正退出後再次開啟
- **WHEN** 用戶返回結果頁後再次點擊同一路線
- **THEN** 新詳情頁 SHALL 從摘要態與香港預設相機首幀開始
- **AND** 可靠完整路線就緒且用戶尚未操作地圖時 SHALL 執行本次頁面唯一一次自動全覽
- **AND** 前次探索鏡頭、相機所有權與選中站點 SHALL NOT 永久恢復

### Requirement: 地圖能力支援三語、明暗與無障礙降級
系統 SHALL 讓 App 自有地圖內容與三段詳情窗在支援語言、外觀、大字體及輔助操作下保持可讀可用，並允許第三方底圖使用自身語言。

#### Scenario: App 自有內容跟隨目前語言與主題
- **WHEN** 用戶以繁體、簡體或英文及淺色或深色模式開啟地圖詳情
- **THEN** App 自有 marker、圖例、控件、錯誤與無障礙文案 SHALL 使用目前語言資源
- **AND** 地圖配色 SHALL 使用 AppCompat 目前實際明暗模式

#### Scenario: Google 底圖第三方語言
- **WHEN** Google 底圖道路、地區或 POI 標籤沒有跟隨 App 內 locale
- **THEN** 系統 SHALL 允許該第三方文字跟隨設備或 Google 自身語言
- **AND** App 自有內容 SHALL 仍使用 App 目前實際語言

#### Scenario: 拖動區輔助操作
- **WHEN** TalkBack 或其他輔助技術聚焦詳情窗拖動區
- **THEN** 系統 SHALL 讀出目前檔位與可用展開／收合操作
- **AND** 觸控與焦點範圍 SHALL 不小於 48dp
- **AND** 點擊操作 SHALL 讓摘要／半屏進入全屏或讓全屏回到摘要

#### Scenario: 地圖不是唯一資訊來源
- **WHEN** 使用者無法操作或理解地圖
- **THEN** 所有站點、乘車段、轉乘與步行語義 SHALL 仍可從 RecyclerView 時間線取得
- **AND** 裝飾線條、圓點與顏色 SHALL NOT 被重複或當作唯一資訊朗讀

### Requirement: 地圖、詳情、幾何、定位與 ETA 獨立降級
系統 SHALL 分別管理外部資料與地圖狀態，讓單一失敗只影響依賴該項目的內容。

#### Scenario: Google 底圖完全不可用
- **WHEN** 設備缺少可用 Google Play Services、Map 初始化失敗或底圖完全不可用
- **THEN** bottom sheet SHALL 自動進入全屏態
- **AND** 頁面 SHALL 顯示地圖不可用提示並保留完整文字詳情與返回

#### Scenario: 單段幾何不可用
- **WHEN** 某一乘車段幾何失敗但站點詳情可用
- **THEN** 地圖 SHALL 保留該段所有可靠站點
- **AND** 地圖 SHALL NOT 補畫該段巴士直線
- **AND** bottom sheet SHALL 保持目前檔位

#### Scenario: Citybus 詳情不可用
- **WHEN** Citybus 詳情請求或站點主結構解析失敗
- **THEN** 頁面 SHALL 保留啟動摘要、查詢端點、目前位置與可獨立驗證的路線幾何
- **AND** 時間線 SHALL 顯示詳情錯誤與重試

#### Scenario: 定位或 ETA 不可用
- **WHEN** 定位或首程 ETA 單獨失敗
- **THEN** 系統 SHALL 只降級藍點或 ETA 區域
- **AND** 地圖、路線與時間線 SHALL 保持可用

#### Scenario: 重試缺失內容
- **WHEN** 用戶選擇重試且部分資料已成功
- **THEN** 系統 SHALL 只重新載入失敗或過期部分
- **AND** 系統 SHALL 保留仍有效的成功內容

### Requirement: 地圖詳情不提供導航或即時乘車追蹤
系統 SHALL 把本能力限制為行程規劃預覽，並 SHALL NOT 讓地圖暗示未提供的導航或即時追蹤能力。

#### Scenario: 不顯示即時巴士位置或乘車進度
- **WHEN** 地圖展示一條候選路線
- **THEN** 系統 SHALL NOT 顯示巴士車輛即時位置
- **AND** 系統 SHALL NOT 根據設備位置標記已步行或已乘坐路段

#### Scenario: 不提供步行導航或地圖選點
- **WHEN** 用戶查看步行連接或長按地圖
- **THEN** 系統 SHALL NOT 啟動 Google Routes、沿街導航或地圖起終點選擇

#### Scenario: 不新增參考 App 操作列
- **WHEN** 詳情頁展示成功
- **THEN** 系統 SHALL NOT 新增收藏、截圖、分享、打車、關注路線、開始導航或下車提醒固定操作列

### Requirement: 路線詳情地圖首幀位於香港
系統 SHALL 在首次 MapView 建立時預置香港城市級相機，避免在任何路線資料完成前顯示世界預設 `(0, 0)` 附近底圖；同一頁可恢復的有效相機 SHALL 優先於香港預設值。

#### Scenario: 首次開啟且沒有保存相機
- **WHEN** 用戶首次開啟路線詳情且沒有可恢復相機狀態
- **THEN** MapView 首個可見底圖 SHALL 使用香港中心及城市級 zoom
- **AND** 地圖 SHALL NOT 先顯示 `(0, 0)` 附近再等待詳情或幾何修正

#### Scenario: 重建時有保存相機
- **WHEN** 詳情頁因 configuration change 或 process recreation 重建
- **AND** 系統有可恢復的有效 target 與 zoom
- **THEN** 地圖 SHALL 恢復該相機
- **AND** 香港預設相機 SHALL NOT 覆蓋使用者已保存的探索位置

#### Scenario: 查詢端點早於詳情可用
- **WHEN** Map 已就緒且啟動參數包含有效查詢起點或終點
- **THEN** 地圖 SHALL 在不等待 Citybus 詳情或分段幾何的情況下展示對應端點
- **AND** 其他資料域失敗 SHALL NOT 移除已知端點

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

### Requirement: 地圖方向紋理嚴格沿路徑前進方向
系統 SHALL 以綁定同一有序路徑的開放折角顯示巴士及示意步行的前進方向，且每個折角 SHALL 按所在位置的局部切線定向。

#### Scenario: 彎曲巴士 geometry 顯示方向
- **WHEN** 一段巴士 geometry 包含直線、彎道或 S 彎
- **THEN** 地圖 SHALL 在 9dp 白色描邊與 7dp 分段色核心組成的巴士實線上重複白色淺開放折角
- **AND** 折角完整視覺包絡 SHALL 約為 5.5dp、描邊約為 1.2dp、固定屏幕間距約為 36dp，且全部非透明像素 SHALL 保持在 7dp 分段色核心內
- **AND** 每個折角 SHALL 隨其所在位置的局部曲線轉向
- **AND** renderer SHALL 由同一有序 geometry 的屏幕投影按固定屏幕間距取得位置，並按每個位置的局部屏幕切線定向扁平折角 marker
- **AND** 系統 SHALL NOT 使用整段起終點 bearing、固定角度、字體 glyph 或脫離 geometry 的手工位置

#### Scenario: 可見視口保持固定方向密度
- **WHEN** 同一條巴士或步行 path 在目前 zoom 只有部分 geometry 位於地圖安全視口
- **THEN** 系統 SHALL 只對安全視口與少量 overscan 相交的有序屏幕線段按固定間距生成方向折角
- **AND** 屏外 geometry 長度 SHALL NOT 消耗可見折角數量上限或放大目前視口內的間距
- **AND** 異常 marker 上限 SHALL 在視口裁切後套用且 SHALL NOT 稀釋正常可見密度

#### Scenario: 拐角只放置可完整貼合的折角
- **WHEN** 候選位置鄰近急彎、S 彎轉折或局部線段短於完整方向折角所需窗口
- **THEN** 系統 SHALL 使用候選位置前後的局部切線窗口判定完整 glyph 是否能沿軌跡容納
- **AND** 系統 SHALL 把候選移至鄰近安全線段或省略該折角
- **AND** 巴士折角 SHALL NOT 越出分段色核心，方向折角 SHALL NOT 以任一單邊線段角度漂離拐角

#### Scenario: 有序 geometry 反轉
- **WHEN** 測試或上游資料把同一條 geometry 的點序反轉
- **THEN** 所有方向折角 SHALL 同步反轉並繼續貼合路徑
- **AND** 折角 SHALL NOT 保留原方向或漂離拐角

#### Scenario: 地圖步行軌跡顯示方向
- **WHEN** 起點步行、異站換乘步行或終點步行取得包含一個或更多有序 `geometry.paths` 的 CSDI 成功結果
- **THEN** 地圖 SHALL 只以約 9dp、描邊約 2.4dp、固定屏幕間距約 14dp 的灰色開放折角沿每個 CSDI 有序子路徑顯示前進方向
- **AND** 每個折角 SHALL 按所在位置的局部切線定向，子路徑空隙及端點之間 SHALL NOT 補畫直線
- **AND** 地圖 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL 把該軌跡描述為規劃預覽而非逐步導航或即時引導

#### Scenario: 步行軌跡查詢中或回退
- **WHEN** 步行段仍在查詢、SameStop、端點不可靠、CSDI 最終失敗或使用 Citybus 距離回退
- **THEN** 地圖 SHALL 保留可靠端點 marker 及其他已成功內容
- **AND** 地圖 SHALL NOT 為該段建立開放折角、直線、虛假軌跡或失敗佔位線

#### Scenario: 相機及增量更新保持紋理貼合
- **WHEN** 相機縮放／平移完成、bottom sheet 到達穩定檔位或漸進資料更新改變有效 path
- **THEN** renderer SHALL 在 camera idle 或穩定檔位 padding 提交後，重新由同一有序 geometry 的目前屏幕投影排布及定向折角
- **AND** renderer SHALL 復用既有方向 marker，只更新位置、旋轉與圖標並按數量差額增刪
- **AND** 增量 renderer SHALL NOT 全量移除再新增未改變的折角，亦 SHALL NOT 造成折角相對路徑漂移、跳角或反向

#### Scenario: 方向折角無法可靠建立
- **WHEN** 目前 projection 不可用或某段 geometry 不能產生可靠的局部屏幕切線
- **THEN** 系統 SHALL 保留可靠巴士實線或省略該步行折角並記錄安全診斷
- **AND** 系統 SHALL NOT 回退為整段 bearing、固定角度或脫離 geometry 的圖標

### Requirement: 地圖站名按角色優先級動態避讓
系統 SHALL 以目前相機 projection、可見地圖範圍及固定角色優先級放置站名，讓關鍵站名可辨識而普通途經站不形成文字牆。

#### Scenario: 關鍵站名選擇可讀位置
- **WHEN** 地圖顯示查詢起點、查詢終點、上車、下車或同站換乘 marker
- **THEN** 系統 SHALL 依右、左、上、下候選位置評估視口、bottom sheet、系統 inset、目前可見 CSDI 署名、路徑、marker 及既有標籤碰撞
- **AND** 系統 SHALL 優先顯示關鍵站名並選擇無碰撞或衝突最少的位置
- **AND** 標籤 SHALL 與 marker 保持可辨識間距及可讀 halo

#### Scenario: 普通途經站按空間顯示
- **WHEN** 普通途經站位於目前地圖可見區域
- **THEN** 系統 SHALL 只在縮放及碰撞條件允許時顯示其站名
- **AND** 普通站名 SHALL 為起終點、上下車、換乘及已選普通站名讓位
- **AND** 放大或選中普通站後使用者 SHALL 可取得完整站名

#### Scenario: 相機停止後更新標籤
- **WHEN** 相機完成一次平移、縮放或程式化定位
- **THEN** 系統 SHALL 在 camera idle 後重新評估標籤
- **AND** 舊候選位置仍有效時 SHALL 保持原側，避免標籤反覆跳動
- **AND** 系統 SHALL NOT 在每個 camera move frame 重算文字碰撞

#### Scenario: 長站名及語言切換
- **WHEN** Citybus 站名在目前 `LanguageSnapshot` 為長英文、繁體或簡體文本
- **THEN** 地圖標籤 SHALL 使用該原文、單行限寬及省略呈現
- **AND** 完整名稱 SHALL 保留於 marker 互動、時間線及無障礙描述
- **AND** App SHALL NOT 自行翻譯或因語言切換改寫第三方站名

### Requirement: 詳情窗拖動期間避免逐幀重建地圖內容
系統 SHALL 在 persistent bottom sheet 連續拖動期間以輕量過渡保持地圖可讀，並只在安全檔位預留或最終停靠時提交昂貴的 padding 與 overlay 重排。

#### Scenario: 開始拖動詳情窗
- **WHEN** bottom sheet 從摘要、半屏或全屏穩定檔位進入拖動
- **THEN** 地圖 SHALL 保留巴士實線、方向折角、站點 marker、目前相機及選中狀態
- **AND** 站名 SHALL 淡出或暫時隱藏而 SHALL NOT 被逐幀移除及重建

#### Scenario: 連續拖動 frame
- **WHEN** 使用者持續拖動 bottom sheet 並產生多個 slide 回呼
- **THEN** CSDI 署名 SHALL 以不觸發重新 layout 的位置變換跟隨詳情窗
- **AND** 向上拖動 SHALL 最多預留一次下一檔位安全 padding，向下拖動 SHALL 暫時保留起始較大的安全 padding
- **AND** 系統 SHALL NOT 在每個 slide frame 重建方向 marker、站名 bitmap、站名 marker 或 CSDI layout
- **AND** 系統 SHALL NOT 因拖動重置相機 target、zoom 或使用者選中內容

#### Scenario: 詳情窗完成停靠
- **WHEN** bottom sheet 最終到達摘要、半屏或全屏穩定檔位
- **THEN** 系統 SHALL 提交一次精確地圖 padding 及 CSDI 正式位置
- **AND** 系統 SHALL 只按最終 projection 重排一次可見方向及站名並恢復標籤可見性
- **AND** 被取消或過期的過渡 callback SHALL NOT 重排目前頁面
