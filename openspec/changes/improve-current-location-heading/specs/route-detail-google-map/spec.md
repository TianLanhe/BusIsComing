## ADDED Requirements

### Requirement: 目前位置方向反映手機頂部朝向
系統 SHALL 以 App 自有實心方向標記顯示手機頂部相對地圖北方的朝向，並 SHALL NOT 以設備行進方向、固定北向或 Google 原生靜止圓點冒充手機朝向。

#### Scenario: 靜止時旋轉手機
- **WHEN** 詳情頁位於前台、目前位置有效，且設備方向供應者在使用者原地旋轉手機時持續回報有效 heading
- **THEN** 實心方向箭頭 SHALL 持續顯示並按手機頂部朝向旋轉
- **AND** 箭頭是否顯示 SHALL NOT 取決於設備是否移動

#### Scenario: 位置先於首個方向到達
- **WHEN** 目前位置已到達但尚未收到首個有效設備 heading
- **THEN** 地圖 SHALL 顯示可用的位置精度範圍
- **AND** 系統 SHALL NOT 偽造朝北箭頭、行進方向箭頭或以圓點冒充方向

#### Scenario: 方向先於位置到達
- **WHEN** 有效設備 heading 已到達但尚未取得有效目前位置
- **THEN** 系統 SHALL 保留最新有效方向供後續位置使用
- **AND** 地圖 SHALL NOT 在未知位置繪製方向標記

#### Scenario: 忽略過期方向事件
- **WHEN** 系統在較新方向事件後收到時間戳較舊或屬於已停止 generation 的事件
- **THEN** 地圖 SHALL 保持較新的方向與生命週期狀態
- **AND** 過期事件 SHALL NOT 重建或旋轉目前位置標記

#### Scenario: 完全未知的方向可信度
- **WHEN** 已有有效 heading 且方向供應者明確回報保守誤差完全未知
- **THEN** 方向箭頭 SHALL 繼續由設備 heading 驅動
- **AND** 系統 SHALL 在該連續低可信區間最多提示一次轉動手機以校準方向
- **AND** 系統 SHALL NOT 改用行進方向或固定方向兜底

#### Scenario: 方向可信度恢復
- **WHEN** 方向供應者在低可信區間後重新回報可信誤差
- **THEN** 系統 SHALL 自動清除校準提示狀態
- **AND** 下一個獨立低可信區間 SHALL 可再次提示一次

#### Scenario: 方向供應者失敗
- **WHEN** 前台方向訂閱無法啟動或中途失敗
- **THEN** 系統 SHALL 呈現可恢復的方向不可用狀態
- **AND** 目前位置 SHALL NOT 被保存為軌跡或改用錯誤語義的方向來源

#### Scenario: 首個方向事件未到
- **WHEN** 前台方向訂閱已啟動，但活性期限內沒有收到首個有效設備 heading
- **THEN** 系統 SHALL 呈現可恢復的方向不可用狀態且 SHALL NOT 繪製方向箭頭
- **AND** 首個有效設備 heading 稍後到達時 SHALL 自動恢復方向箭頭

#### Scenario: 方向更新流停止
- **WHEN** 已顯示有效方向，但方向供應者超過活性期限沒有回報新事件或顯式失敗
- **THEN** 系統 SHALL 清除過期方向箭頭並呈現可恢復的方向不可用狀態
- **AND** 下一個時間較新的有效設備 heading 到達時 SHALL 自動恢復方向箭頭

### Requirement: App 自有目前位置保留位置精度語義
系統 SHALL 以連續前台位置更新驅動目前位置方向標記，並在上游提供水平精度時顯示對應精度範圍。

#### Scenario: 新位置與水平精度到達
- **WHEN** 詳情頁前台收到時間較新的有效位置及水平精度
- **THEN** 系統 SHALL 更新方向標記位置與精度範圍
- **AND** 相機 SHALL 保持由使用者控制

#### Scenario: 較舊位置晚到
- **WHEN** 系統在較新位置後收到時間戳較舊或屬於已停止 generation 的位置
- **THEN** 系統 SHALL 忽略較舊位置
- **AND** 方向標記、精度範圍及相機 SHALL 保持較新狀態

#### Scenario: 前台位置暫時不可用
- **WHEN** 位置訂閱已啟動但尚未取得有效位置或位置來源暫時失敗
- **THEN** 路線地圖與詳情 SHALL 繼續可用
- **AND** 系統 SHALL NOT 以保存位置、查詢端點或任意預設座標冒充目前位置

## MODIFIED Requirements

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
- **THEN** 地圖 SHALL 把 App 自有目前位置方向標記移入可見區域
- **AND** 地圖 SHALL NOT 因此進入持續相機跟隨

#### Scenario: 精簡地圖控件
- **WHEN** 路線詳情地圖顯示成功
- **THEN** 地圖 SHALL 支援平移與縮放並停用旋轉和傾斜
- **AND** 頁面 SHALL NOT 提供交通、衛星、地圖類型、回饋、縮放加減或 Google 公交圖層控件

#### Scenario: Google attribution 不被遮擋
- **WHEN** bottom sheet、CSDI 署名或 WindowInsets 改變地圖可見區域
- **THEN** Google Logo 與必要法律文字 SHALL 保持可見且不可被詳情窗、署名或控件遮擋

### Requirement: 目前位置只在詳情前台可選使用
系統 SHALL 在用戶控制下提供頁面前台目前位置與設備朝向，且 SHALL NOT 把設備位置改寫為查詢起點、保存為位置軌跡或在背景持續訂閱位置與方向。

#### Scenario: 已有位置權限
- **WHEN** 詳情頁進入前台且 App 已有可用位置權限並開啟系統定位
- **THEN** 地圖 SHALL 啟動持續前台位置及設備方向更新
- **AND** 地圖 SHALL 以 App 自有方向標記與精度範圍呈現可用結果
- **AND** 相機 SHALL 保持由用戶控制

#### Scenario: 未授權時進入頁面
- **WHEN** 用戶尚未授予位置權限而開啟詳情頁
- **THEN** 系統 SHALL NOT 自動顯示權限對話框
- **AND** 系統 SHALL NOT 啟動位置或方向訂閱
- **AND** 地圖與路線詳情 SHALL 正常載入

#### Scenario: 點擊位置控件時請求
- **WHEN** 未授權用戶點擊目前位置控件
- **THEN** 系統 SHALL 請求適用的位置權限
- **AND** 一般拒絕、永久拒絕或系統定位關閉 SHALL 提供對應說明或設定入口

#### Scenario: 點擊目前位置只居中一次
- **WHEN** 用戶點擊目前位置控件且已有新鮮目前位置
- **THEN** 地圖 SHALL 把目前位置移入可見區域
- **AND** 地圖 SHALL NOT 因此進入持續相機跟隨

#### Scenario: 頁面離開前台
- **WHEN** 詳情頁進入後台或被關閉
- **THEN** 系統 SHALL 停止本頁位置與方向更新並移除 App 自有目前位置圖層
- **AND** 已停止 generation 的晚到 callback SHALL NOT 恢復目前位置圖層
- **AND** 系統 SHALL NOT 申請背景定位或保存使用者軌跡

#### Scenario: resumed 頁面中關閉或重新開啟系統定位
- **WHEN** 使用者在詳情頁保持 resumed 時關閉系統定位
- **THEN** 系統 SHALL 立即停止本頁位置與方向更新並移除目前位置圖層
- **AND** 使用者重新開啟系統定位後，系統 SHALL 在權限與地圖仍可用時重新啟動更新

#### Scenario: 地圖不可用
- **WHEN** Google 地圖明確不可用或載入逾時進入不可用狀態
- **THEN** 系統 SHALL 不啟動或立即停止本頁高精度位置與方向更新
- **AND** 晚到地圖 callback SHALL NOT 在頁面 paused 時重新啟動更新

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
- **THEN** 系統 SHALL 只降級 App 自有目前位置圖層或 ETA 區域
- **AND** 地圖、路線與時間線 SHALL 保持可用

#### Scenario: 重試缺失內容
- **WHEN** 用戶選擇重試且部分資料已成功
- **THEN** 系統 SHALL 只重新載入失敗或過期部分
- **AND** 系統 SHALL 保留仍有效的成功內容
