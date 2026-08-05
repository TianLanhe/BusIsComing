## ADDED Requirements

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
系統 SHALL 在地圖上展示所有可靠巴士站、分段巴士道路、查詢起終點、轉乘與示意步行，並以形狀、線型、文字和顏色共同表達角色。

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
- **AND** 系統 SHALL NOT 繪製步行虛線或步行距離

#### Scenario: 展示步行轉乘
- **WHEN** Citybus 詳情把兩段路線標記為步行前往轉車站
- **THEN** 系統 SHALL 保留前段下車站與後段上車站兩個角色
- **AND** 系統 SHALL 以灰色虛線表示「步行連接（示意）」
- **AND** 系統 SHALL NOT 以坐標相同為由改寫 Citybus 轉乘類型

#### Scenario: 展示首尾步行
- **WHEN** 查詢起點、首段上車站、末段下車站與查詢終點坐標可用
- **THEN** 系統 SHALL 使用灰色示意虛線展示首尾步行連接
- **AND** 系統 SHALL NOT 把該虛線描述為真實沿街導航

#### Scenario: 常駐地圖圖例
- **WHEN** 地圖區域可見
- **THEN** 系統 SHALL 顯示「彩色實線＝巴士路線」及「灰色虛線＝步行連接（示意）」的緊湊圖例
- **AND** 圖例 SHALL NOT 遮擋 Google 標誌、法律文字或核心地圖控件

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
系統 SHALL 在地圖可見與全屏詳情狀態間保持單一、持續可用的返回入口。

#### Scenario: 摘要或半屏返回入口
- **WHEN** bottom sheet 位於摘要態或半屏態
- **THEN** 返回按鈕 SHALL 以圓形浮動控件顯示在地圖左上安全區域

#### Scenario: 全屏返回入口
- **WHEN** bottom sheet 進入全屏態
- **THEN** 返回按鈕 SHALL 遷入詳情窗固定標題列
- **AND** 標題列 SHALL 顯示目前語言的「路線詳情」
- **AND** 畫面 SHALL NOT 同時顯示地圖浮動返回與標題列返回

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
系統 SHALL 首次展示完整行程並在後續 bottom sheet 變化中保留使用者鏡頭，除非用戶明確選擇定位、全覽或站點。

#### Scenario: 首次完整路線全覽
- **WHEN** 完整路線的可靠站點或幾何首次可用
- **THEN** 地圖 SHALL 調整相機以包含查詢起點、所有乘車段與查詢終點
- **AND** 遠離路線的設備目前位置 SHALL NOT 強制加入初始 bounds

#### Scenario: bottom sheet 改變高度
- **WHEN** bottom sheet 在三個檔位之間移動
- **THEN** 系統 SHALL 更新 Google Map padding 與可用視口
- **AND** 系統 SHALL NOT 重置使用者已調整的 zoom、bearing 或 target

#### Scenario: 點擊全覽路線
- **WHEN** 用戶點擊全覽路線控件
- **THEN** 地圖 SHALL 重新顯示完整查詢行程

#### Scenario: 點擊目前位置
- **WHEN** 位置權限已授予且用戶點擊目前位置控件
- **THEN** 地圖 SHALL 把設備藍點移入可見區域
- **AND** 地圖 SHALL NOT 因此進入持續相機跟隨

#### Scenario: 精簡地圖控件
- **WHEN** 路線詳情地圖顯示成功
- **THEN** 地圖 SHALL 支援平移與縮放並停用旋轉和傾斜
- **AND** 頁面 SHALL NOT 提供交通、衛星、地圖類型、回饋、縮放加減或 Google 公交圖層控件

#### Scenario: Google attribution 不被遮擋
- **WHEN** bottom sheet 或 WindowInsets 改變地圖可見區域
- **THEN** Google Logo 與必要法律文字 SHALL 保持可見且不可被詳情窗、圖例或控件遮擋

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
系統 SHALL 在同一次詳情頁生命週期重建時恢復可序列化探索狀態，並在真正退出後讓下一次開啟回到初始狀態。

#### Scenario: configuration change 重建
- **WHEN** 詳情頁因旋轉、主題、語言或等效 configuration change 重建
- **THEN** 系統 SHALL 恢復 bottom sheet 檔位、相機、選中站點、展開乘車段和列表位置
- **AND** GoogleMap、Marker 或 Polyline 實例 SHALL NOT 被直接保存

#### Scenario: MapView 生命週期
- **WHEN** Activity 收到建立、啟動、恢復、暫停、停止、低記憶體、保存狀態或銷毀事件
- **THEN** 系統 SHALL 把對應生命週期轉交 MapView
- **AND** 已銷毀頁面的 callback SHALL NOT 更新 UI

#### Scenario: 真正退出後再次開啟
- **WHEN** 用戶返回結果頁後再次點擊同一路線
- **THEN** 新詳情頁 SHALL 從摘要態與完整路線相機開始
- **AND** 前次探索鏡頭與選中站點 SHALL NOT 永久恢復

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
