# route-search-destination Specification

## Purpose
定義搜尋頂層頁的一次性起終點查詢、目前位置、結果摘要、儲存常用行程與既有路線結果能力，讓搜尋不依賴主頁的臨時查詢彈層。

## Requirements

### Requirement: 搜尋頁承接一次性起終點查詢
系統 SHALL 在「搜尋」destination 提供表單優先的一次性路線查詢流程，讓用戶不需建立常用路線即可查詢 Citybus 候選路線。

#### Scenario: 輸入有效起終點並查詢
- **WHEN** 用戶在搜尋頁從 Citybus 候選中選擇有效起點與終點並點擊 `查詢路線`
- **THEN** 系統 SHALL 使用該起終點發起既有 Citybus 路線查詢
- **AND** 系統 SHALL 不自動建立常用路線

#### Scenario: 沒有常用路線時使用搜尋
- **WHEN** 用戶尚未保存任何常用路線並切換至搜尋頁
- **THEN** 系統 SHALL 允許用戶完成一次性起終點查詢
- **AND** 系統 SHALL 不要求先新增常用路線

#### Scenario: 起終點無效時阻止查詢
- **WHEN** 用戶未從候選列表確認起點或終點，或兩地點完全相同，並嘗試查詢
- **THEN** 系統 SHALL 顯示既有輸入校驗錯誤
- **AND** 系統 SHALL 不發起 Citybus 路線請求

### Requirement: 搜尋頁提供目前位置與交換操作
系統 SHALL 在搜尋頁提供目前位置作為起點的選擇方式，並提供交換已選起點與終點的圖示操作。

#### Scenario: 使用目前位置作為起點
- **WHEN** 用戶在搜尋頁觸發目前位置入口且既有定位流程成功返回可用地點
- **THEN** 系統 SHALL 將該地點設為搜尋起點
- **AND** 系統 SHALL 沿用既有 Google 反向地理編碼 attribution 與定位降級語義

#### Scenario: 交換已確認地點
- **WHEN** 搜尋頁起點與終點均為已確認的 Citybus 地點且用戶點擊交換圖示
- **THEN** 系統 SHALL 交換兩個已選地點及輸入內容
- **AND** 系統 SHALL 不重新搜尋、保存或查詢路線

### Requirement: 搜尋結果提供摘要、編輯與保存為常用
系統 SHALL 在一次性查詢開始後，以搜尋摘要承載目前起終點與操作，取代舊主頁的臨時查詢上下文條。

#### Scenario: 顯示搜尋摘要
- **WHEN** 用戶以有效起終點發起一次性查詢且系統進入載入、成功、失敗或無結果狀態
- **THEN** 搜尋頁 SHALL 顯示該次查詢的 `起點 → 終點` 摘要
- **AND** 摘要 SHALL 提供編輯與 `存為常用` 操作

#### Scenario: 編輯目前搜尋
- **WHEN** 用戶點擊搜尋摘要中的編輯操作
- **THEN** 系統 SHALL 返回帶有該次起終點的搜尋表單
- **AND** 系統 SHALL 不以主頁 bottom sheet 顯示臨時查詢

#### Scenario: 保存後保持搜尋上下文
- **WHEN** 用戶從搜尋摘要使用有效起終點保存常用路線並保存成功
- **THEN** 系統 SHALL 刷新常用路線資料
- **AND** 系統 SHALL 保持在搜尋 destination 並保留目前結果與排序

### Requirement: 搜尋頁使用與常用一致的結果能力
系統 SHALL 在搜尋結果中使用既有路線卡、ETA、路線詳情、通知監控、排序與下拉刷新能力，且不得因查詢來源不同改變 Citybus 結果的業務含義。

#### Scenario: 搜尋結果顯示既有路線資訊
- **WHEN** 一次性查詢返回一筆或多筆候選路線
- **THEN** 系統 SHALL 顯示既有路線號、站點預覽、票價、耗時、步行距離與候車資訊
- **AND** 用戶 SHALL 可開啟 ETA、路線詳情及適用的監控入口

#### Scenario: 搜尋失敗或無結果
- **WHEN** 一次性查詢失敗或沒有可用候選路線
- **THEN** 搜尋頁 SHALL 顯示與既有結果區一致的失敗或無結果狀態
- **AND** 系統 SHALL 保留搜尋摘要與起終點，讓用戶可以重試或編輯

### Requirement: 第一階段不顯示地圖功能
系統 SHALL 在本 change 的搜尋頁維持表單與結果列表流程，且不得新增 Google 地圖、地圖選點、站序連線或地圖載入占位。

#### Scenario: 搜尋結果顯示
- **WHEN** 一次性查詢返回結果
- **THEN** 系統 SHALL 顯示搜尋摘要、排序與路線結果
- **AND** 系統 SHALL 不要求 Google Maps SDK、地圖 API key 或地圖網路服務才可使用查詢結果
