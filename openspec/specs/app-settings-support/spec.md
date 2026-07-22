# app-settings-support Specification

## Purpose
TBD - created by archiving change add-settings-and-support-entrypoints. Update Purpose after archive.
## Requirements

### Requirement: 主頁可進入 App 設定頁
系統 SHALL 在主頁提供克制且清楚的 App 設定入口，讓用戶可從普通主頁、首次引導頁或臨時查詢狀態進入設定頁。

#### Scenario: 普通主頁顯示設定入口
- **WHEN** 用戶打開主頁且主頁顯示常用路線區塊
- **THEN** 系統 SHALL 在主頁右上角顯示設定圖示入口
- **AND** 設定入口 SHALL 使用白底、淡色描邊、深青綠圖示的圓形或等效克制樣式
- **AND** 設定入口 SHALL 提供至少 44dp 的觸控區
- **AND** 設定入口 SHALL 提供無障礙描述 `設定`

#### Scenario: 首次引導頁顯示設定入口
- **WHEN** 用戶打開主頁且系統顯示首次引導頁
- **THEN** 系統 SHALL 仍在主頁右上角顯示設定圖示入口
- **AND** 系統 SHALL NOT 要求用戶先新增常用路線才能查看設定、關於或隱私政策

#### Scenario: 從主頁打開設定頁
- **WHEN** 用戶點擊主頁設定入口
- **THEN** 系統 SHALL 打開標題為 `設定` 的獨立設定頁
- **AND** 系統 SHALL 保留主頁既有已選路線、臨時查詢上下文、排序狀態與查詢結果

### Requirement: 設定頁按 App 資訊、偏好、支援與關於分組
系統 SHALL 使用頂層設定 destination 承載 App 級低頻入口，並以清楚分組避免常用與搜尋頁平鋪功能按鈕。

#### Scenario: 顯示設定頁基本結構
- **WHEN** 用戶打開設定頁
- **THEN** 系統 SHALL 顯示頁面標題 `設定`
- **AND** 系統 SHALL 顯示 App 名稱 `BusIsComing`
- **AND** 系統 SHALL 顯示目前 App 版本
- **AND** 系統 SHALL 顯示 `偏好`、`路線資料`、`支援`、`關於` 分組

#### Scenario: 偏好分組
- **WHEN** 用戶查看設定頁 `偏好` 分組
- **THEN** 系統 SHALL 依序顯示 `外觀主題` 與 `語言` 入口
- **AND** `外觀主題` SHALL 位於 `語言` 之前

#### Scenario: 路線資料分組
- **WHEN** 用戶查看設定頁 `路線資料` 分組
- **THEN** 系統 SHALL 顯示 `匯入與匯出常用路線` 入口

#### Scenario: 支援分組
- **WHEN** 用戶查看設定頁 `支援` 分組
- **THEN** 系統 SHALL 依序顯示 `分享應用`、`問題反饋`、`應用評分`、`檢查更新` 入口

#### Scenario: 關於分組
- **WHEN** 用戶查看設定頁 `關於` 分組
- **THEN** 系統 SHALL 依序顯示 `關於我們`、`隱私政策` 入口

#### Scenario: 設定是頂層 destination
- **WHEN** 用戶透過底部導航打開設定頁
- **THEN** 系統 SHALL 保持底部導航可見並標識設定為目前 destination
- **AND** 系統 SHALL NOT 顯示左上返回入口或把設定呈現為獨立次級頁

#### Scenario: 返回主頁
- **WHEN** 用戶在設定頁點擊左上返回入口或按系統返回
- **THEN** 系統 SHALL 關閉設定頁並回到主頁

### Requirement: 暫不支援入口提供明確 Toast
系統 SHALL 保留應用評分與檢查更新入口為可點擊狀態，但本期僅提供目前語言的暫不支援提示。

#### Scenario: 點擊應用評分入口
- **WHEN** 用戶在設定頁點擊 `應用評分`
- **THEN** 系統 SHALL 以目前 App 語言顯示暫不支援提示
- **AND** 系統 SHALL NOT 打開商店頁或 Play In-App Review

#### Scenario: 點擊檢查更新入口
- **WHEN** 用戶在設定頁點擊 `檢查更新`
- **THEN** 系統 SHALL 以目前 App 語言顯示暫不支援提示
- **AND** 系統 SHALL NOT 發起網路更新檢查、Play In-App Updates 或自建更新流程

#### Scenario: 點擊語言入口
- **WHEN** 用戶在設定頁點擊 `語言`
- **THEN** 系統 SHALL 顯示 Toast `暫不支援語言切換`
- **AND** 系統 SHALL NOT 改變 App locale、Citybus 查詢語言或 Google 地址語言

### Requirement: 分享應用使用系統分享面板
系統 SHALL 允許用戶從設定頁分享 BusIsComing，並使用目前 App 語言的自然分享文案與對應官方網站首頁 URL。

#### Scenario: 成功打開分享面板
- **WHEN** 用戶在設定頁點擊 `分享應用`
- **THEN** 系統 SHALL 打開系統分享面板
- **AND** 分享內容 SHALL 使用目前 App 語言介紹 BusIsComing 的常用路線、候車及比較能力
- **AND** 分享 URL SHALL 對繁體、簡體、英文分別使用 `https://www.busiscoming.com/zh-hant/`、`https://www.busiscoming.com/zh-hans/`、`https://www.busiscoming.com/en/`

#### Scenario: 無法分享應用
- **WHEN** 用戶點擊 `分享應用`
- **AND** 系統沒有可處理分享 Intent 的 App 或分享面板打開失敗
- **THEN** 系統 SHALL 以目前 App 語言顯示無法分享提示
- **AND** 系統 SHALL 保持停留在設定頁

### Requirement: 問題反饋使用郵件 Intent
系統 SHALL 允許用戶從設定頁透過郵件提交問題反饋，並以目前 App 語言預填主旨、說明與基本診斷資訊。

#### Scenario: 成功打開問題反饋郵件
- **WHEN** 用戶在設定頁點擊 `問題反饋`
- **THEN** 系統 SHALL 打開郵件撰寫 Intent
- **AND** 收件人 SHALL 為 `hezhenyu966@gmail.com`
- **AND** 主題與問題描述提示 SHALL 使用目前 App 語言
- **AND** 正文 SHALL 預填 App 版本、Android 版本與設備型號
- **AND** 品牌、版本與設備值 SHALL 保持原值而不翻譯

#### Scenario: 無法打開問題反饋
- **WHEN** 用戶點擊 `問題反饋`
- **AND** 系統沒有可處理郵件 Intent 的 App 或郵件撰寫打開失敗
- **THEN** 系統 SHALL 以目前 App 語言顯示無法開啟提示
- **AND** 系統 SHALL 保持停留在設定頁

### Requirement: 關於我們展示 App 基本資訊
系統 SHALL 在設定頁內提供關於我們二級頁，以目前 App 語言展示 App 基本資訊與對應官方網站首頁入口。

#### Scenario: 打開關於我們頁
- **WHEN** 用戶在設定頁點擊 `關於我們`
- **THEN** 系統 SHALL 打開使用目前 App 語言標題的二級頁
- **AND** 頁面 SHALL 顯示 App 名稱 `BusIsComing`
- **AND** 頁面 SHALL 顯示目前 App 版本
- **AND** 頁面簡介 SHALL 使用目前 App 語言
- **AND** 頁面 SHALL 顯示對應語言官方網站首頁入口

#### Scenario: 從關於我們頁返回設定
- **WHEN** 用戶在關於我們頁點擊左上返回入口或按系統返回
- **THEN** 系統 SHALL 返回設定頁

#### Scenario: 打開官網
- **WHEN** 用戶在關於我們頁點擊官網入口
- **THEN** 系統 SHALL 使用外部瀏覽器或等效系統能力打開目前語言的官方網站首頁

#### Scenario: 官網打開失敗
- **WHEN** 用戶點擊官網入口
- **AND** 系統無法打開該 URL
- **THEN** 系統 SHALL 以目前 App 語言顯示無法開啟網站提示
- **AND** 系統 SHALL 保持停留在關於我們頁

### Requirement: 隱私政策打開線上 URL
系統 SHALL 讓用戶從設定頁打開與目前 App 語言一致的線上私隱政策。

#### Scenario: 成功打開繁體私隱政策
- **WHEN** 目前 App 語言為繁體中文且用戶點擊私隱政策
- **THEN** 系統 SHALL 打開 `https://www.busiscoming.com/zh-hant/privacy/`

#### Scenario: 成功打開簡體私隱政策
- **WHEN** 目前 App 語言為簡體中文且用戶點擊私隱政策
- **THEN** 系統 SHALL 打開 `https://www.busiscoming.com/zh-hans/privacy/`

#### Scenario: 成功打開英文私隱政策
- **WHEN** 目前 App 語言為英文且用戶點擊私隱政策
- **THEN** 系統 SHALL 打開 `https://www.busiscoming.com/en/privacy/`

#### Scenario: 私隱政策打開失敗
- **WHEN** 系統無法打開目前語言的私隱政策 URL
- **THEN** 系統 SHALL 以目前 App 語言顯示無法開啟提示
- **AND** 系統 SHALL 保持停留在設定頁

#### Scenario: 成功打開隱私政策
- **WHEN** 用戶在設定頁點擊 `隱私政策`
- **THEN** 系統 SHALL 使用外部瀏覽器或等效系統能力打開 `https://www.busiscoming.com/zh-hant/privacy/`

#### Scenario: 隱私政策打開失敗
- **WHEN** 用戶點擊 `隱私政策`
- **AND** 系統無法打開該 URL
- **THEN** 系統 SHALL 顯示 Toast `暫時無法開啟隱私政策`
- **AND** 系統 SHALL 保持停留在設定頁

### Requirement: 設定頁可選擇 App 語言
系統 SHALL 在設定頁讓用戶透過單選對話框選擇跟隨系統、繁體中文、簡體中文或英文。

#### Scenario: 語言 item 顯示目前選擇
- **WHEN** 用戶查看設定頁偏好分組
- **THEN** `語言` item SHALL 顯示目前選擇作為副標題
- **AND** 跟隨系統時副標題 SHALL 同時顯示目前實際語言
- **AND** `語言` item SHALL 位於 `外觀主題` item 之後

#### Scenario: 打開語言單選對話框
- **WHEN** 用戶點擊設定頁 `語言`
- **THEN** 系統 SHALL 顯示單選對話框
- **AND** 明確語言選項 SHALL 固定顯示為 `繁體中文`、`简体中文`、`English`
- **AND** `跟隨系統` SHALL 使用目前 App 語言顯示
- **AND** 目前選擇 SHALL 處於選中狀態

#### Scenario: 選擇後立即套用
- **WHEN** 用戶選擇任一不同語言選項
- **THEN** 系統 SHALL 立即保存並套用該選擇
- **AND** 對話框 SHALL NOT 要求額外儲存按鈕
- **AND** 設定頁重建後 SHALL 保持在設定 destination 並展示新語言
- **AND** 已保存外觀模式 SHALL 保持不變

### Requirement: 設定頁提供外觀主題單選入口

系統 SHALL 在 `外觀主題` 設定列顯示已保存模式，並 SHALL 使用可存取的 Material 單選對話框讓用戶切換三種外觀模式。

#### Scenario: 設定列顯示目前值
- **WHEN** 用戶打開設定頁
- **THEN** `外觀主題` 列 SHALL 顯示目前保存值 `跟隨系統`、`淺色模式` 或 `深色模式`
- **AND** 整列 SHALL 提供至少 48dp 的觸控高度
- **AND** 輔助技術 SHALL 能讀取設定名稱及目前值

#### Scenario: 打開外觀主題單選對話框
- **WHEN** 用戶點擊 `外觀主題`
- **THEN** 系統 SHALL 顯示標題為 `外觀主題` 的 Material 單選對話框
- **AND** 對話框 SHALL 依序顯示 `跟隨系統`、`淺色模式`、`深色模式`
- **AND** 對話框 SHALL 選中目前已保存模式
- **AND** 輔助技術 SHALL 能讀取每個選項及其選中狀態

#### Scenario: 選擇新的外觀模式
- **WHEN** 用戶在對話框點擊與目前不同的模式
- **THEN** 系統 SHALL 保存並立即套用該模式
- **AND** 對話框 SHALL 關閉
- **AND** 設定列摘要 SHALL 在 Activity 重建後顯示新模式
- **AND** 系統 SHALL 保持設定 destination 與已保存 App 語言選擇不變
- **AND** 系統 SHALL NOT 顯示成功 Toast 或要求額外確認

#### Scenario: 重新選擇目前模式
- **WHEN** 用戶在對話框點擊目前已保存模式
- **THEN** 系統 SHALL 關閉對話框並保持目前模式
- **AND** 系統 SHALL NOT 顯示成功 Toast 或執行可見的無效重載

### Requirement: 設定頁管理乘車碼桌面快捷方式
系統 SHALL 在設定頁提供「乘車碼快捷方式」入口，並透過 Android pinned shortcut 能力由用戶確認把乘車碼添加至桌面。

#### Scenario: 顯示快捷方式設定列
- **WHEN** 用戶查看設定頁
- **THEN** 系統 SHALL 顯示「乘車碼快捷方式」設定列
- **AND** 設定列 SHALL 以「從桌面一按開啟」說明其用途
- **AND** 設定列 SHALL NOT 直接啟動乘車碼

#### Scenario: 請求添加桌面快捷方式
- **WHEN** 裝置支援 pinned shortcut 且用戶點擊設定列
- **THEN** 系統 SHALL 顯示由系統 launcher 管理的添加確認
- **AND** 用戶確認後建立的快捷方式 SHALL 直接進入正式乘車碼啟動鏈

#### Scenario: 裝置不支援固定快捷方式
- **WHEN** 裝置 launcher 不支援 pinned shortcut
- **AND** 用戶點擊設定列
- **THEN** 系統 SHALL 顯示本地化的不可用提示
- **AND** 系統 SHALL 保持停留在設定頁且不得崩潰

#### Scenario: 重複請求快捷方式
- **WHEN** 用戶已添加乘車碼快捷方式並再次點擊設定列
- **THEN** 系統 SHALL 可再次交由 launcher 處理請求或顯示已添加狀態
- **AND** 系統 SHALL NOT 在常用頁增加持續宣傳入口

### Requirement: 設定頁準確回饋乘車碼桌面快捷方式狀態
系統 SHALL 在設定頁的乘車碼快捷方式入口展示目前 pinned shortcut 狀態，並對請求接受、固定成功、已存在、不支援及失敗提供可理解且不誤導的三語回饋。

#### Scenario: 快捷方式已固定
- **WHEN** 用戶打開或返回設定頁且乘車碼 pinned shortcut 已存在
- **THEN** 設定列 SHALL 顯示已新增狀態
- **AND** 用戶再次點擊時系統 SHALL 提示 `已新增至主畫面`
- **AND** 系統 SHALL NOT 重複發出 pinned shortcut 請求

#### Scenario: 系統接受固定請求
- **WHEN** 用戶點擊尚未固定且 launcher 支援的乘車碼快捷方式設定列
- **AND** 系統接受 pinned shortcut 請求
- **THEN** 系統 SHALL 提示用戶在系統視窗確認新增
- **AND** 系統 SHALL NOT 在收到成功 callback 前宣告已新增

#### Scenario: 固定快捷方式成功
- **WHEN** launcher 完成固定並回傳成功 callback
- **THEN** 設定頁 SHALL 顯示新增成功回饋
- **AND** 設定列 SHALL 刷新為已新增狀態

#### Scenario: 用戶取消系統確認
- **WHEN** 系統已接受請求但用戶取消 launcher 確認
- **THEN** 系統 SHALL 保持設定列為未新增狀態
- **AND** 系統 SHALL NOT 誤報成功或失敗
- **AND** 用戶 SHALL 能夠再次點擊重試

#### Scenario: launcher 不支援固定快捷方式
- **WHEN** 目前 launcher 不支援 App 內 pinned shortcut
- **THEN** 系統 SHALL 指引用戶長按 BusIsComing 圖示並把靜態 `乘車碼` 快捷項拖到主畫面
- **AND** 系統 SHALL NOT 要求不存在的 Android 運行時權限

#### Scenario: 固定請求失敗
- **WHEN** pinned shortcut API 返回 false 或發生可捕獲例外
- **THEN** 系統 SHALL 顯示可重試的失敗回饋
- **AND** 設定列 SHALL 保持未新增狀態及可點擊

#### Scenario: 返回設定頁重新檢查
- **WHEN** 設定頁進入 `onResume`，包括從 launcher 確認畫面返回或用戶從桌面移除 shortcut 後返回
- **THEN** 系統 SHALL 重新查詢 pinned shortcut 狀態
- **AND** 設定列 SHALL 反映目前實際可檢測狀態

#### Scenario: 多語與深淺色顯示
- **WHEN** App 使用繁體中文、簡體中文或英文，以及淺色或深色模式
- **THEN** 請求確認、成功、已存在、不支援及失敗回饋 SHALL 使用對應語言資源及語意色
- **AND** 狀態文字 SHALL NOT 與設定列圖示或其他內容重疊
