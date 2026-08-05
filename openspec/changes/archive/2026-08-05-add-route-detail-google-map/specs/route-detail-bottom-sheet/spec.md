## MODIFIED Requirements

### Requirement: 路線卡片可開啟全屏詳情頁
系統 SHALL 允許用戶從任一共用路線結果卡片進入以 Google 地圖為背景的獨立全屏路線詳情頁，並 SHALL 在返回時恢復來源查詢上下文。

#### Scenario: 點擊有詳情元數據的路線卡片
- **WHEN** 用戶點擊包含可解析 P2P 詳情元數據的路線結果卡片非 ETA／通知專用區域
- **THEN** 系統 SHALL 立即開啟獨立全屏路線詳情頁
- **AND** 頁面 SHALL 先展示路線名、價格、總耗時、卡片步行摘要及可用候車狀態
- **AND** 頁面 SHALL 在不阻塞進入的情況下分別載入 Google Map、Citybus 詳情及分段幾何

#### Scenario: 返回來源結果
- **WHEN** 用戶在任一詳情窗檔位使用系統返回、返回手勢或頁面返回按鈕
- **THEN** 系統 SHALL 直接關閉詳情頁並顯示原來源頁的查詢結果、排序與捲動上下文
- **AND** 系統 SHALL NOT 先收合詳情窗
- **AND** 系統 SHALL NOT 因開啟詳情增加常用行程使用次數或重新執行路線查詢

#### Scenario: 點擊缺少詳情元數據的路線卡片
- **WHEN** 用戶點擊的路線結果缺少可解析 P2P 詳情元數據
- **THEN** 系統 SHALL 開啟全屏詳情頁並展示路線摘要與「路線詳情暫不可用」
- **AND** 頁面 SHALL 保留可獨立展示的地圖、查詢端點及目前位置
- **AND** 系統 SHALL NOT 發起 Citybus 路線詳情或路線幾何請求

#### Scenario: 頁面重建可恢復請求
- **WHEN** 全屏詳情頁因 process recreation 或 configuration change 重建
- **THEN** 系統 SHALL 從 primitive 啟動參數重建摘要、詳情請求及可用查詢起終點快照
- **AND** 系統 SHALL 恢復詳情窗檔位、已展開乘車段、所選站點及列表位置
- **AND** 系統 SHALL NOT 依賴來源頁仍保有原 `BusRouteOption` 記憶體物件

### Requirement: 路線詳情摘要展示可判定的完整方案指標
系統 SHALL 在 persistent bottom sheet 的摘要區展示路線鏈、總耗時、預計到達時間、總票價、總途經站數、步行距離及可用首程即時 ETA，並 SHALL 明確處理卡片摘要與完整分段的差異。

#### Scenario: 顯示路線摘要
- **WHEN** 系統有可用路線結果摘要
- **THEN** 摘要 SHALL 展示路線鏈、總耗時與總票價
- **AND** 有最終預計到達時間時摘要 SHALL 展示 `預計 HH:mm 到達` 或目前語言等效文案
- **AND** 摘要 SHALL 展示各乘車段途經站數之和且 SHALL NOT 重複計算上下車或換乘端點
- **AND** 有可靠首程即時 ETA 時摘要 SHALL 以緊湊形式展示該狀態

#### Scenario: 完整步行分段距離可用
- **WHEN** 起點、所有必要換乘與終點步行段均已識別且距離可用
- **THEN** 詳情摘要 SHALL 顯示這些分段距離之和
- **AND** 摘要 SHALL 使用已確認的步行人物圖示與距離
- **AND** 系統 SHALL NOT 將完整合計回填至路線卡片或改變列表排序

#### Scenario: 完整步行合計不可判定
- **WHEN** 一個或多個必要步行段的距離缺失
- **THEN** 詳情摘要 SHALL 回退顯示路線卡片步行距離
- **AND** 系統 SHALL 保留該值不是已知完整分段合計的狀態
- **AND** 系統 SHALL NOT 以缺失分段為零宣稱完整總量

#### Scenario: 摘要隨詳情內容捲動
- **WHEN** 用戶把詳情窗展開至半屏或全屏並向下瀏覽時間線
- **THEN** 摘要 SHALL 作為詳情列表首項正常捲出畫面
- **AND** 系統 SHALL NOT 把完整摘要固定在詳情窗頂部而壓縮時間線空間

#### Scenario: 從展開狀態收合至摘要
- **WHEN** 詳情列表未在頂部且用戶把詳情窗收合至摘要態
- **THEN** 系統 SHALL 先恢復列表頂部以完整展示摘要
- **AND** 摘要 SHALL NOT 停留在部分捲出或內部捲動狀態

#### Scenario: 大字體摘要超出普通目標高度
- **WHEN** font scale 1.3 或 2.0 令摘要無法容納於普通 25% 至 30% 目標高度
- **THEN** 摘要態 SHALL 按內容增高且半屏態 SHALL 不低於摘要所需高度
- **AND** 系統 SHALL NOT 縮字、裁切核心文字或讓摘要本身內部捲動

### Requirement: 路線詳情支援三語、模式感知與無障礙
系統 SHALL 讓地圖背景、三段式詳情窗及其動態內容在繁體、簡體、英文、淺色、深色及大字體下保持可讀、可操作及可由輔助技術理解。

#### Scenario: 三語與深淺色
- **WHEN** 用戶以任一支援語言及外觀模式開啟詳情頁
- **THEN** App 自有標題、狀態、圖例、操作與格式 SHALL 使用目前語言資源
- **AND** 表面、分段色、文字、圖示、marker 及線條 SHALL 使用目前模式對應資源並保持對比
- **AND** 第三方站名、路線號與方向原文 SHALL NOT 被 App 機器翻譯

#### Scenario: Google 底圖標籤語言
- **WHEN** Google 底圖道路或 POI 標籤的語言與 App 內目前語言不同
- **THEN** 系統 SHALL 允許第三方底圖標籤沿用 Google／設備語言
- **AND** App 自有 marker 說明、圖例、錯誤與詳情內容 SHALL 繼續使用 App 目前語言

#### Scenario: TalkBack 讀取關鍵語義
- **WHEN** TalkBack 聚焦摘要、乘車段、步行段、地圖 marker、地圖控制或途經站控制
- **THEN** 系統 SHALL 讀出耗時、距離、預計／即時來源、路線號、站點角色、站數、操作及展開狀態等完整語義
- **AND** 裝飾實線、虛線、圓點及重複 marker SHALL NOT 造成重複朗讀
- **AND** 關鍵狀態 SHALL NOT 只由顏色或地圖位置表達

#### Scenario: 詳情窗把手可操作
- **WHEN** 觸控或輔助技術聚焦詳情窗把手
- **THEN** 把手 SHALL 提供至少 48dp 的操作區域及目前檔位語義
- **AND** 點擊 SHALL 讓摘要／半屏進入全屏，或讓全屏回到摘要

#### Scenario: 窄屏與大字體
- **WHEN** 詳情頁在約 360dp 寬度或 font scale 1.3／2.0 顯示
- **THEN** 摘要指標與地圖控制 SHALL 可換行、重排或避讓
- **AND** 長站名與方向 SHALL 可換行且內容 SHALL 可捲動至終點
- **AND** 核心文字 SHALL NOT 以不可讀縮字或固定高度裁切
- **AND** Google 標誌與法律文字 SHALL NOT 被詳情窗、控制或 WindowInsets 遮擋
