## MODIFIED Requirements

### Requirement: 路線詳情使用 Google 地圖背景與漸進載入
系統 SHALL 在所需 GMS 能力可用時沿用獨立路線詳情頁的全屏 Google 地圖及既有漸進載入；GMS 明確不可用時 SHALL 直接展示全屏文字詳情。兩種狀態 SHALL 均在外部資料完成前立即展示既有路線摘要。

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

#### Scenario: 無 GMS 時直接閱讀文字詳情
- **WHEN** 用戶在 GMS 明確不可用的裝置開啟路線詳情
- **THEN** 系統 SHALL 直接展示全屏文字摘要、可靠站點、轉乘、步行、ETA 及返回，隱藏僅供地圖使用的控件
- **AND** 系統 SHALL NOT 先建立 Google 地圖、顯示必須安裝 Google 的阻擋 UI，或要求用戶先拉起詳情窗
- **AND** 原有資料來源與各自失敗／重試語義 SHALL 保持不變

#### Scenario: 正常 GMS 路線詳情
- **WHEN** GMS 與 Google Play 正常可用，且用戶開啟詳情或操作地圖
- **THEN** 系統 SHALL 保留目前 Google renderer、方向圖層、相機、三段詳情窗、站點互動及權限行為
- **AND** 本次無 GMS 相容改動 SHALL NOT 新增文字降級、後備定位訂閱或可見版面跳動

### Requirement: 目前位置只在詳情前台可選使用
系統 SHALL 在用戶控制下提供頁面前台目前位置與獨立的 App 行程位置匹配；地圖可用時 SHALL 沿用目前 Google 路徑的 App 自有方向標記與精度範圍，無 GMS 時 SHALL 只保留可由系統位置可靠支援的文字位置指示。系統 SHALL NOT 把設備位置改寫為查詢起點、保存為位置軌跡或在背景持續訂閱位置與方向。

#### Scenario: 已有位置權限
- **WHEN** 詳情頁進入前台且 App 已有可用位置權限並開啟系統定位
- **THEN** 地圖可用時 SHALL 沿用既有持續前台位置及設備方向更新；App 行程位置 SHALL 獨立取得合格位置供文字匹配
- **AND** 地圖可用時 SHALL 以 App 自有方向標記與精度範圍呈現可用結果
- **AND** 相機 SHALL 保持由用戶控制

#### Scenario: 未授權時進入頁面
- **WHEN** 用戶尚未授予位置權限而開啟詳情頁
- **THEN** 系統 SHALL 沿用既有一次性可忽略位置 Snackbar，且在用戶選擇開啟前 SHALL NOT 自動顯示權限對話框
- **AND** 系統 SHALL NOT 啟動位置或方向訂閱
- **AND** 可用的地圖與路線詳情 SHALL 沿用原有載入行為；無 GMS 時 SHALL 直接載入文字詳情

#### Scenario: 點擊位置控件時請求
- **WHEN** 未授權用戶點擊目前位置控件或既有 Snackbar 的開啟 action
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

#### Scenario: 無地圖時文字位置獨立運作
- **WHEN** GMS 明確不可用，但系統位置、權限及可靠路線軸可用
- **THEN** 文字摘要與時間線 SHALL 使用系統位置沿用既有行程位置匹配與可靠性規則
- **AND** 系統 SHALL NOT 因地圖缺失強制關閉文字位置能力，亦不得啟動地圖高精度方向訂閱

#### Scenario: 權限與系統設定恢復
- **WHEN** 用戶從 App 權限或系統定位設定返回
- **THEN** 系統 SHALL 沿用既有權限拒絕、永久拒絕及系統定位開關處理，按目前能力恢復前台工作
- **AND** 正常 GMS 路徑 SHALL 不改變提示時機、相機跟隨政策或文字匹配狀態

### Requirement: 地圖與詳情狀態可恢復且不跨開啟永久保存
系統 SHALL 在同一次詳情頁生命週期重建時恢復可序列化探索及相機所有權狀態，並在真正退出後讓下一次有可用地圖的開啟回到摘要態與香港首幀，再按目前可靠路線執行一次自動全覽；無 GMS 時 SHALL 使用全屏文字詳情，保留同次頁面的文字閱讀與選擇狀態。

#### Scenario: configuration change 重建
- **WHEN** 詳情頁因旋轉、主題、語言或等效 configuration change 重建
- **THEN** 系統 SHALL 恢復 bottom sheet 檔位、相機、相機所有權、是否已自動全覽、選中站點、展開乘車段和列表位置
- **AND** GoogleMap、Marker 或 Polyline 實例 SHALL NOT 被直接保存

#### Scenario: MapView 生命週期
- **WHEN** Activity 收到建立、啟動、恢復、暫停、停止、低記憶體、保存狀態或銷毀事件
- **THEN** 系統 SHALL 只向已在所需 GMS 能力可用時成功建立的 MapView 轉交對應生命週期；無 GMS 時 SHALL 不建立或呼叫 MapView
- **AND** 已銷毀頁面的 callback SHALL NOT 更新 UI

#### Scenario: 真正退出後再次開啟
- **WHEN** 用戶返回結果頁後再次點擊同一路線
- **THEN** 有可用地圖的新詳情頁 SHALL 從摘要態與香港預設相機首幀開始；無 GMS 時 SHALL 從全屏文字詳情開始
- **AND** 地圖可用、可靠完整路線就緒且用戶尚未操作地圖時 SHALL 執行本次頁面唯一一次自動全覽
- **AND** 前次探索鏡頭、相機所有權與選中站點 SHALL NOT 永久恢復

#### Scenario: 無 GMS 的配置重建與能力恢復
- **WHEN** 無 GMS 文字詳情因配置改變而重建，或從設定返回時能力已改變
- **THEN** 系統 SHALL 重新確認相關能力並保留本次查詢、展開段、選擇及有效文字列表位置
- **AND** 舊世代的地圖與定位 callback SHALL NOT 更新新頁面
- **AND** GMS 恢復可用後 SHALL 能使用 Google 地圖路徑；能力未變時 SHALL NOT 額外重置成功內容

### Requirement: 地圖、詳情、幾何、定位與 ETA 獨立降級
系統 SHALL 分別管理外部資料、地圖及目前位置狀態，讓單一失敗只影響依賴該項目的內容。

#### Scenario: Google 底圖完全不可用
- **WHEN** 設備缺少可用 Google Play Services、Map 初始化失敗或底圖完全不可用
- **THEN** GMS 明確不可用時 SHALL 直接進入全屏文字詳情；既有地圖路徑載入失敗時 bottom sheet SHALL 自動進入全屏態
- **AND** 頁面 SHALL 顯示地圖不可用提示並保留完整文字詳情、目前位置摘要／時間線指示及返回
- **AND** App 自有目前位置圖層 SHALL 可獨立降級而不得令 App 行程位置匹配必然失敗

#### Scenario: 單段幾何不可用
- **WHEN** 某一乘車段幾何失敗但站點詳情可用
- **THEN** 地圖 SHALL 保留該段所有可靠站點
- **AND** 地圖 SHALL NOT 補畫該段巴士直線
- **AND** bottom sheet SHALL 保持目前檔位
- **AND** 目前位置 SHALL 只在其他具有可靠幾何或 CSDI path 的分段繼續匹配

#### Scenario: Citybus 詳情不可用
- **WHEN** Citybus 詳情請求或站點主結構解析失敗
- **THEN** 頁面 SHALL 保留啟動摘要、查詢端點、App 自有目前位置圖層與可獨立驗證的路線幾何
- **AND** 時間線 SHALL 顯示詳情錯誤與重試
- **AND** 摘要及詳細目前位置指示 SHALL 隱藏而不得只依地圖幾何猜測站序

#### Scenario: 定位不可用
- **WHEN** 權限、系統定位、位置 fix、精度或可靠匹配單獨失敗
- **THEN** 系統 SHALL 只降級 App 自有目前位置圖層及依賴定位的摘要／詳細位置指示
- **AND** 地圖、路線、時間線與 ETA SHALL 保持可用

#### Scenario: ETA 不可用
- **WHEN** 首程 ETA 單獨失敗
- **THEN** 系統 SHALL 只降級 ETA 區域
- **AND** 地圖、路線、時間線、目前位置圖層與可靠行程位置指示 SHALL 保持可用

#### Scenario: 重試缺失內容
- **WHEN** 用戶選擇重試且部分資料已成功
- **THEN** 系統 SHALL 只重新載入失敗或過期部分
- **AND** 系統 SHALL 保留仍有效的成功內容、相機、詳情窗檔位及使用者捲動所有權

#### Scenario: 底圖失敗不改判全部 Google 能力
- **WHEN** GMS 仍可用而只有底圖網絡或地圖資料載入失敗
- **THEN** 系統 SHALL 維持既有地圖失敗回饋及其他可靠內容
- **AND** 系統 SHALL NOT 只因底圖失敗把既有 Fused 文字位置改為系統定位後備，或改變 Google Play 更新路徑
