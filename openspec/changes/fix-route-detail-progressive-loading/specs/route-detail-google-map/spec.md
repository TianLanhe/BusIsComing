## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: 地圖相機與控件尊重使用者探索
系統 SHALL 在香港首幀後最多自動展示一次完整行程，並在使用者開始地圖手勢後保留其鏡頭所有權，除非用戶明確選擇定位、全覽或站點。

#### Scenario: 首次完整路線全覽
- **WHEN** 可靠站點已可用且所有預期幾何分段均到達成功或失敗終態
- **AND** 用戶尚未操作地圖且本頁尚未自動全覽
- **THEN** 地圖 SHALL 平滑調整相機以包含查詢起點、所有可靠乘車段與查詢終點
- **AND** 遠離路線的設備目前位置 SHALL NOT 強制加入初始 bounds
- **AND** 單段幾何失敗 SHALL NOT 讓首次全覽永久等待

#### Scenario: 使用者手勢取得鏡頭所有權
- **WHEN** 用戶在完整路線自動全覽前或後平移、縮放或旋轉地圖
- **THEN** 本次詳情頁 SHALL 將相機視為由用戶控制
- **AND** 晚到的詳情、幾何、ETA 或 bottom sheet 更新 SHALL NOT 再自動 fit 或重置相機

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
- **THEN** 地圖 SHALL 重新顯示目前可用的完整查詢行程

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
