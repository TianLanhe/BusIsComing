## ADDED Requirements

### Requirement: 搜尋頁輸入器提供清晰欄位焦點與輔助狀態
系統 SHALL 在搜尋頁移除頁面大標題，保留短功能說明，並讓起終點輸入、欄位級輔助文案與右側交換控制保持清晰而互不重疊。

#### Scenario: 搜尋頁頂部保持精簡
- **WHEN** 用戶打開搜尋頁
- **THEN** 系統 SHALL NOT 顯示「搜尋／搜索／Search」頁面大標題
- **AND** 系統 SHALL 在輸入器上方保留簡短功能說明

#### Scenario: 輸入文字與工具保持間距
- **WHEN** 用戶在起點或終點輸入框輸入
- **THEN** 文字 SHALL 使用 16sp 及 16dp 起始內距
- **AND** 欄位末端 SHALL 保留 52dp 工具空間
- **AND** 文字、光標、定位／清除工具 SHALL NOT 互相重疊

#### Scenario: 焦點與光標在深淺色可辨識
- **WHEN** 任一搜尋輸入框獲得焦點
- **THEN** 約 2dp 光標與焦點外框 SHALL 使用高對比主題強調色
- **AND** 淺色與深色模式 SHALL 均可清楚辨識目前輸入欄位

#### Scenario: 輔助文案歸屬目前欄位
- **WHEN** 欄位為空並取得焦點、正在輸入、沒有候選或發生錯誤
- **THEN** 對應輔助文案 SHALL 顯示在該輸入框下方
- **AND** 另一欄位 SHALL NOT 顯示該狀態文案
- **AND** 有效地點被選中後 SHALL 隱藏一般操作提示

#### Scenario: 交換控制保持獨立可用
- **WHEN** 搜尋頁顯示兩個輸入框
- **THEN** 交換按鈕 SHALL 位於兩個輸入框右側並垂直置中
- **AND** 交換按鈕 SHALL 保持至少 48dp 觸控範圍

### Requirement: 搜尋頁首次非阻塞填入目前位置地址
系統 SHALL 在每個主畫面實例首次進入搜尋頁且沒有可恢復起點時，非阻塞執行定位與 Google Reverse Geocoding，並只在整個流程成功後以具體地址名稱和原始經緯度建立起點。

#### Scenario: 符合條件時自動填入具體地址
- **WHEN** 用戶在一個主畫面實例首次進入搜尋頁
- **AND** 搜尋頁沒有已選起點、使用者起點文字或已提交查詢
- **THEN** 系統 SHALL 在背景取得手機位置並使用目前語言執行 Google Reverse Geocoding
- **AND** 成功後 SHALL 填入具體地址名稱、原始經緯度及必要 attribution
- **AND** 系統 SHALL NOT 使用「我的位置」特殊占位值

#### Scenario: 定位期間輸入保持可用
- **WHEN** 自動定位或 Geocoding 正在進行
- **THEN** 起點與終點輸入 SHALL 保持可編輯
- **AND** 只有起點定位工具 SHALL 以小型進度表示等待
- **AND** 搜尋按鈕可用性 SHALL 只由兩端是否為有效選中地點決定

#### Scenario: 終點操作不取消起點定位
- **WHEN** 自動定位進行中且用戶輸入或選擇終點
- **THEN** 起點自動定位 SHALL 繼續
- **AND** 有效成功回調 SHALL 可填入起點而不改變已選終點

#### Scenario: 起點操作使舊回調失效
- **WHEN** 自動定位進行中且用戶輸入或選擇起點，或交換起終點
- **THEN** 系統 SHALL 立即停止顯示起點等待狀態
- **AND** 已發出的舊定位或 Geocoding 回調 SHALL NOT 覆蓋目前輸入與選擇

#### Scenario: 頁面或語言狀態使舊回調失效
- **WHEN** 自動定位進行中且搜尋頁離開可見狀態或 App 實際語言改變
- **THEN** 舊回調 SHALL NOT 更新搜尋頁
- **AND** 後續新請求 SHALL 使用新的語言 snapshot

#### Scenario: 自動流程失敗不建立半完成地點
- **WHEN** 權限、定位服務、逾時、定位或 Reverse Geocoding 任一步失敗
- **THEN** 起點 SHALL 保持空白或保留使用者目前內容且可編輯
- **AND** 系統 SHALL NOT 建立只有座標或沒有具體名稱的選中起點
- **AND** 起點輔助文案 SHALL 提示手動選擇或點擊定位工具重試
- **AND** 系統 SHALL NOT 因自動失敗彈出 Toast 或強制跳轉設定

#### Scenario: 手動重試沿用既有恢復流程
- **WHEN** 用戶點擊起點定位工具手動重試
- **THEN** 系統 SHALL 沿用新增行程既有權限、定位設定、timeout、cache、Geocoding 與失敗提示流程

#### Scenario: 恢復狀態不被再次覆蓋
- **WHEN** 搜尋頁因畫面重建恢復了起點、使用者文字或已提交查詢
- **THEN** 系統 SHALL NOT 再次自動定位並覆蓋該狀態
