## ADDED Requirements

### Requirement: 已保存起終點配置使用行程術語
系統 SHALL 在 App 自有用戶可見內容中，把由用戶命名並保存的起點與終點配置稱為「常用行程」或上下文清楚時的「行程」，並 SHALL NOT 把該配置描述為一條已確定的巴士路線。

#### Scenario: 常用頁展示已保存行程
- **WHEN** 常用頁存在一個或多個已保存起終點配置
- **THEN** 系統 SHALL 以「常用行程」標示該區塊及相關完整列表
- **AND** 每張卡片 SHALL 保留用戶自訂行程名稱及起點到終點摘要

#### Scenario: 沒有已保存行程
- **WHEN** 用戶打開常用頁且沒有任何已保存起終點配置
- **THEN** 首次引導、空狀態及建立入口 SHALL 使用「行程」描述可建立的保存配置
- **AND** 臨時查詢入口 SHALL 繼續表達一次性查詢而非已保存行程

#### Scenario: 新增編輯複製及刪除行程
- **WHEN** 用戶新增、編輯、複製或刪除已保存起終點配置
- **THEN** 頁面標題、欄位、操作、確認訊息、成功／失敗提示及無障礙描述 SHALL 使用「行程」
- **AND** 名稱欄位 SHALL 稱為「行程名稱」或該語言自然的等效文案

#### Scenario: 臨時查詢保存為常用行程
- **WHEN** 用戶從搜尋表單、臨時查詢彈層或查詢結果上下文保存目前起點和終點
- **THEN** 保存入口、名稱輸入、重複提示及成功訊息 SHALL 表達「保存為常用行程」
- **AND** 系統 SHALL NOT 暗示保存了目前選中的某一條查詢路線

#### Scenario: 匯入匯出已保存配置
- **WHEN** 用戶在設定或傳輸頁查看、匯入、匯出、合併或取代已保存起終點配置
- **THEN** 分組、標題、說明、數量、預覽、私隱警告、確認及結果文案 SHALL 使用「行程」
- **AND** `.bicroutes` 副檔名及系統文件選擇器中的真實檔名 SHALL 保持不變

### Requirement: 查詢返回的乘車方案保留路線術語
系統 SHALL 把 Citybus 點到點查詢返回的一個直達或換乘乘車方案稱為「路線」，並 SHALL NOT 因保存配置改稱行程而把查詢結果改稱「行程」。

#### Scenario: 發起路線查詢
- **WHEN** 用戶以已保存行程或臨時起點和終點發起查詢
- **THEN** 查詢操作、載入狀態、空結果、失敗狀態及結果摘要 SHALL 使用「路線」描述待查詢或已返回的乘車方案

#### Scenario: 展示直達路線
- **WHEN** 查詢返回 `118` 或 `8X` 等直達乘車方案
- **THEN** 系統 SHALL 把該結果展示為一條路線
- **AND** 路線號、票價、耗時、步行距離、ETA、站點及第三方原文 SHALL 保持既有展示語義

#### Scenario: 展示換乘路線
- **WHEN** 查詢返回 `85 → 106` 等包含多段巴士服務的乘車方案
- **THEN** 系統 SHALL 把完整組合展示為一條路線
- **AND** 系統在需要描述其結構時 SHALL 把 `85` 和 `106` 分別稱為乘車段或該語言自然的等效詞

#### Scenario: 查看路線詳情及監控
- **WHEN** 用戶查看路線卡、路線詳情、ETA 或為查詢結果啟動通知欄監控
- **THEN** 系統 SHALL 繼續使用「路線」描述被查看或監控的乘車方案
- **AND** 如同時展示所屬已保存配置，該配置 SHALL 使用「行程」描述

### Requirement: 三語使用一致的行程與路線映射
系統 SHALL 在繁體中文、簡體中文及英文中維持相同概念邊界，且每種語言 SHALL 使用自然、經獨立審校的 runtime 文案。

#### Scenario: 繁體中文術語
- **WHEN** App 實際語言為繁體中文
- **THEN** 已保存起終點配置 SHALL 使用「常用行程／行程」
- **AND** 查詢乘車方案 SHALL 使用「路線」
- **AND** 單段巴士服務在需要時 SHALL 使用「乘車段」

#### Scenario: 簡體中文術語
- **WHEN** App 實際語言為簡體中文
- **THEN** 已保存起終點配置 SHALL 使用「常用行程／行程」
- **AND** 查詢乘車方案 SHALL 使用「路线」
- **AND** 單段巴士服務在需要時 SHALL 使用「乘车段」

#### Scenario: 英文術語
- **WHEN** App 實際語言為英文
- **THEN** 已保存起終點配置 SHALL 使用 `Regular journey / journey`
- **AND** 查詢乘車方案 SHALL 使用 `Route`
- **AND** 單段巴士服務在需要時 SHALL 使用 `Leg`

#### Scenario: 語言切換後重新顯示界面
- **WHEN** 用戶在包含已保存行程及查詢路線的有效上下文中切換 App 語言
- **THEN** 重建後的 App 自有文案 SHALL 按新實際語言使用對應的行程與路線術語
- **AND** 用戶自訂名稱、已保存地點及第三方動態原文 SHALL 遵循既有本地化與回退規則

### Requirement: 用戶資料與第三方原文不因術語調整而改寫
系統 SHALL 只調整 App 自有文案，並 SHALL NOT 翻譯、改寫或遷移用戶保存資料、匯入資料、Citybus 路線號、站名、目的地、備註或其他第三方原文。

#### Scenario: 展示既有自訂行程名稱
- **WHEN** 用戶已保存名稱為「上班」「118」或其他任意原文的配置
- **THEN** 系統 SHALL 原樣展示該自訂名稱
- **AND** 系統 SHALL 只在周邊 App 標籤和操作中使用新的行程術語

#### Scenario: 語言切換不改寫保存資料
- **WHEN** 用戶切換 App 語言
- **THEN** 系統 SHALL NOT 改寫 SQLite 或匯入資料中的名稱與地點
- **AND** 系統 SHALL NOT 把用戶名稱中的「路線／路线／route」自動替換為行程術語

#### Scenario: 第三方路線資料保持原文
- **WHEN** Citybus 或 DATA.GOV.HK 返回路線號、站名、目的地或備註
- **THEN** 系統 SHALL 按既有語言 mapping 和回退規則展示官方原文
- **AND** 本 change SHALL NOT 對第三方內容執行術語替換

### Requirement: 術語調整保持內部及資料兼容
系統 SHALL 在只改變 App 自有文案的前提下保留既有功能、持久化及交換契約，並 SHALL NOT 要求用戶遷移或重新建立現有資料。

#### Scenario: 升級後讀取既有保存配置
- **WHEN** 已有保存配置的用戶升級至包含新術語的版本
- **THEN** 系統 SHALL 原樣讀取所有既有名稱、起點、終點、使用次數及最近使用時間
- **AND** 系統 SHALL 只以新的行程文案包裝和操作該資料

#### Scenario: 匯入既有 bicroutes 文件
- **WHEN** 用戶匯入符合現行協議的既有 `.bicroutes` 文件
- **THEN** 系統 SHALL 沿用既有校驗、預覽、合併及取代行為
- **AND** 文件 schema、版本及欄位 SHALL NOT 因界面改稱行程而改變

#### Scenario: 使用行程查詢路線
- **WHEN** 用戶選擇已保存行程並發起查詢
- **THEN** 系統 SHALL 沿用既有起終點、使用統計、Citybus 請求、結果排序、ETA 及刷新行為
- **AND** 術語調整 SHALL NOT 改變任何 callback、cache、語言 snapshot 或過期結果處理

#### Scenario: 內部歷史命名保持可用
- **WHEN** App 編譯及執行本 change
- **THEN** 既有 Kotlin API、resource key、SQLite schema 及內部檔名 SHALL 保持兼容
- **AND** 系統 SHALL NOT 因文案調整新增資料遷移或協議轉換

### Requirement: 術語在無障礙及可伸縮版面中完整可用
系統 SHALL 讓受影響的可見文字和 content description 在三語、淺／深色、窄屏及大字體下保持語義一致、可讀和可操作。

#### Scenario: 無障礙服務讀取行程操作
- **WHEN** 無障礙服務聚焦常用行程區塊、管理入口、行程卡或新增／編輯／刪除操作
- **THEN** content description SHALL 使用行程術語並清楚表達操作對象
- **AND** 查詢結果卡及路線詳情的 content description SHALL 繼續使用路線術語

#### Scenario: 窄屏與大字體展示新文案
- **WHEN** 用戶在約 360dp 寬度或 font scale 1.3／2.0 下查看受影響畫面
- **THEN** 行程標題、按鈕、Dialog、錯誤提示及匯入匯出摘要 SHALL 保持可理解且核心文字不得裁切
- **AND** 系統 SHALL NOT 以縮小字體取代可伸縮、換行或滾動版面

#### Scenario: 深淺色不改變術語可讀性
- **WHEN** 用戶在淺色或深色模式查看受影響文案
- **THEN** 新的行程文案 SHALL 沿用既有語意色與對比規則
- **AND** 文案調整 SHALL NOT 改變畫面層級、觸控目標或焦點順序

### Requirement: 項目文件沉澱權威術語規則
項目 SHALL 在現行文件中定義行程、路線與乘車段的三語映射和使用邊界，讓後續 App 文案與 OpenSpec change 可採用同一準則。

#### Scenario: 本地化指南提供權威術語表
- **WHEN** 開發者查閱 `docs/localization-guidelines.md`
- **THEN** 文件 SHALL 把已保存起終點配置定義為行程、查詢乘車方案定義為路線、單段巴士服務定義為乘車段
- **AND** 文件 SHALL 說明用戶資料與第三方原文不得自動改寫

#### Scenario: 項目級 agent 規則記錄概念邊界
- **WHEN** 後續 coding agent 查閱 `AGENTS.md`
- **THEN** 文件 SHALL 提供行程與路線的簡潔長期規則並指向詳細本地化指南
- **AND** 文件 SHALL 說明內部 `RouteConfig` 等歷史命名保持不變，不得直接推導 runtime 文案

#### Scenario: 現行產品說明使用新術語
- **WHEN** 用戶或開發者閱讀 `README.md` 及描述目前產品行為的現行說明
- **THEN** 文件 SHALL 使用行程描述保存配置，並使用路線描述查詢結果
- **AND** 已完成或歷史 OpenSpec change artifacts SHALL NOT 因本 change 被批量重寫
