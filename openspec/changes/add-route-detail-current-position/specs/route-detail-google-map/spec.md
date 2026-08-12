## MODIFIED Requirements

### Requirement: 目前位置只在詳情前台可選使用
系統 SHALL 在路線詳情頁前台提供 Google 原生藍點及 App 行程位置匹配；已有權限時頁面 SHALL 自動開始取得位置，未授權時 SHALL 由使用者透過可忽略提示決定是否請求，且 SHALL NOT 把設備位置改寫為查詢起點或保存為位置軌跡。

#### Scenario: 已有位置權限
- **WHEN** 詳情頁進入前台且 App 已有可用位置權限
- **THEN** 地圖 SHALL 顯示持續更新的 Google 原生藍點
- **AND** App SHALL 立即請求新鮮位置供摘要及詳細時間線匹配
- **AND** 相機 SHALL 保持由用戶控制，不因持續位置更新進入相機跟隨

#### Scenario: 未授權時進入頁面
- **WHEN** 用戶尚未授予位置權限而開啟詳情頁
- **THEN** 系統 SHALL 顯示一次帶本地化「開啟」action 的 Snackbar
- **AND** 系統 SHALL NOT 在使用者點擊 action 前自動顯示權限對話框
- **AND** 地圖、路線摘要與詳情 SHALL 正常載入

#### Scenario: 點擊未授權提示或位置控件
- **WHEN** 未授權用戶點擊位置 Snackbar 的「開啟」action 或地圖目前位置控件
- **THEN** 系統 SHALL 在平台允許時請求適用的前台位置權限
- **AND** 一般拒絕 SHALL 不在同一詳情頁會話內反覆提示
- **AND** 權限授予後原生藍點與 App 行程位置取得 SHALL 在頁面仍在前台時開始

#### Scenario: 位置權限永久拒絕
- **WHEN** 用戶選擇開啟位置但平台不再允許顯示系統權限對話框
- **THEN** 系統 SHALL 開啟 BusIsComing 的 App 系統設定頁
- **AND** 從設定返回後系統 SHALL 依目前權限重新判定藍點及行程位置能力

#### Scenario: 系統定位服務關閉
- **WHEN** App 已有位置權限但系統定位服務關閉
- **THEN** 系統 SHALL 在單次詳情頁會話內顯示一次說明及系統位置設定入口
- **AND** 地圖、路線與時間線 SHALL 保持可用而不顯示推測位置

#### Scenario: 點擊已有權限的目前位置控件
- **WHEN** 位置權限已授予且用戶點擊目前位置控件
- **THEN** 地圖 SHALL 把設備原生藍點移入可見區域
- **AND** 地圖 SHALL NOT 因此進入持續相機跟隨
- **AND** 該操作 SHALL NOT 重啟、重置或改寫 App 行程位置匹配狀態

#### Scenario: 頁面離開前台
- **WHEN** 詳情頁進入後台或被關閉
- **THEN** 系統 SHALL 停止本頁 App 位置更新並停止向已離開頁面派送 callback
- **AND** 系統 SHALL NOT 申請背景定位或保存使用者軌跡

### Requirement: 地圖、詳情、幾何、定位與 ETA 獨立降級
系統 SHALL 分別管理外部資料、地圖及目前位置狀態，讓單一失敗只影響依賴該項目的內容。

#### Scenario: Google 底圖完全不可用
- **WHEN** 設備缺少可用 Google Play Services、Map 初始化失敗或底圖完全不可用
- **THEN** bottom sheet SHALL 自動進入全屏態
- **AND** 頁面 SHALL 顯示地圖不可用提示並保留完整文字詳情、目前位置摘要／時間線指示及返回
- **AND** Google 原生藍點 SHALL 可獨立降級而不得令 App 行程位置匹配必然失敗

#### Scenario: 單段幾何不可用
- **WHEN** 某一乘車段幾何失敗但站點詳情可用
- **THEN** 地圖 SHALL 保留該段所有可靠站點
- **AND** 地圖 SHALL NOT 補畫該段巴士直線
- **AND** bottom sheet SHALL 保持目前檔位
- **AND** 目前位置 SHALL 只在其他具有可靠幾何或 CSDI path 的分段繼續匹配

#### Scenario: Citybus 詳情不可用
- **WHEN** Citybus 詳情請求或站點主結構解析失敗
- **THEN** 頁面 SHALL 保留啟動摘要、查詢端點、Google 原生藍點與可獨立驗證的路線幾何
- **AND** 時間線 SHALL 顯示詳情錯誤與重試
- **AND** 摘要及詳細目前位置指示 SHALL 隱藏而不得只依地圖幾何猜測站序

#### Scenario: 定位不可用
- **WHEN** 權限、系統定位、位置 fix、精度或可靠匹配單獨失敗
- **THEN** 系統 SHALL 只降級 Google 原生藍點及依賴定位的摘要／詳細位置指示
- **AND** 地圖、路線、時間線與 ETA SHALL 保持可用

#### Scenario: ETA 不可用
- **WHEN** 首程 ETA 單獨失敗
- **THEN** 系統 SHALL 只降級 ETA 區域
- **AND** 地圖、路線、時間線、原生藍點與可靠行程位置指示 SHALL 保持可用

#### Scenario: 重試缺失內容
- **WHEN** 用戶選擇重試且部分資料已成功
- **THEN** 系統 SHALL 只重新載入失敗或過期部分
- **AND** 系統 SHALL 保留仍有效的成功內容、相機、詳情窗檔位及使用者捲動所有權

### Requirement: 地圖詳情不提供導航或即時乘車追蹤
系統 SHALL 把地圖及行程位置指示限制為候選路線規劃與裝置相對位置，並 SHALL NOT 暗示未提供的巴士車輛追蹤、已行經進度或導航能力。

#### Scenario: 顯示裝置相對於規劃行程的位置
- **WHEN** 摘要或詳細時間線依可靠設備位置顯示目前位置指示
- **THEN** 系統 SHALL 只表示裝置靠近某節點、位於相鄰節點之間或處於步行分段的相對位置
- **AND** 地圖 SHALL NOT 顯示巴士車輛即時位置
- **AND** 系統 SHALL NOT 根據設備位置把巴士道路、步行 path 或時間線軸標記為已步行、已乘坐或已完成

#### Scenario: 不提供步行導航或地圖選點
- **WHEN** 用戶查看步行連接或長按地圖
- **THEN** 系統 SHALL NOT 啟動 Google Routes、沿街導航或地圖起終點選擇
- **AND** 目前位置指示 SHALL NOT 提供開始導航、偏航重算或逐步指令

#### Scenario: 不新增參考 App 操作列
- **WHEN** 詳情頁展示成功
- **THEN** 系統 SHALL NOT 新增收藏、截圖、分享、打車、關注路線、開始導航或下車提醒固定操作列
