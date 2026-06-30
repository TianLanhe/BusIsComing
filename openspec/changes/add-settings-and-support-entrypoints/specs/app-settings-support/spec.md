## ADDED Requirements

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
系統 SHALL 使用獨立設定頁承載 App 級低頻入口，並以清楚分組避免首頁平鋪功能按鈕。

#### Scenario: 顯示設定頁基本結構
- **WHEN** 用戶打開設定頁
- **THEN** 系統 SHALL 顯示頁面標題 `設定`
- **AND** 系統 SHALL 顯示 App 名稱 `BusIsComing`
- **AND** 系統 SHALL 顯示目前 App 版本
- **AND** 系統 SHALL 顯示 `偏好`、`支援`、`關於` 分組

#### Scenario: 偏好分組
- **WHEN** 用戶查看設定頁 `偏好` 分組
- **THEN** 系統 SHALL 顯示 `語言` 入口

#### Scenario: 支援分組
- **WHEN** 用戶查看設定頁 `支援` 分組
- **THEN** 系統 SHALL 依序顯示 `分享應用`、`問題反饋`、`應用評分`、`檢查更新` 入口

#### Scenario: 關於分組
- **WHEN** 用戶查看設定頁 `關於` 分組
- **THEN** 系統 SHALL 依序顯示 `關於我們`、`隱私政策` 入口

#### Scenario: 返回主頁
- **WHEN** 用戶在設定頁點擊左上返回入口或按系統返回
- **THEN** 系統 SHALL 關閉設定頁並回到主頁

### Requirement: 暫不支援入口提供明確 Toast
系統 SHALL 保留語言、應用評分與檢查更新入口為可點擊狀態，但本期僅提供暫不支援提示。

#### Scenario: 點擊語言入口
- **WHEN** 用戶在設定頁點擊 `語言`
- **THEN** 系統 SHALL 顯示 Toast `暫不支援語言切換`
- **AND** 系統 SHALL NOT 改變 App locale、Citybus 查詢語言或 Google 地址語言

#### Scenario: 點擊應用評分入口
- **WHEN** 用戶在設定頁點擊 `應用評分`
- **THEN** 系統 SHALL 顯示 Toast `暫不支援應用評分`
- **AND** 系統 SHALL NOT 打開商店頁或 Play In-App Review

#### Scenario: 點擊檢查更新入口
- **WHEN** 用戶在設定頁點擊 `檢查更新`
- **THEN** 系統 SHALL 顯示 Toast `暫不支援檢查更新`
- **AND** 系統 SHALL NOT 發起網路更新檢查、Play In-App Updates 或自建更新流程

### Requirement: 分享應用使用系統分享面板
系統 SHALL 允許用戶從設定頁分享 BusIsComing，並使用固定分享文案與官網 URL。

#### Scenario: 成功打開分享面板
- **WHEN** 用戶在設定頁點擊 `分享應用`
- **THEN** 系統 SHALL 打開系統分享面板
- **AND** 分享內容 SHALL 為 `搭巴士前想快一點知道哪條路線合適？BusIsComing 可以保存常用路線，快速比較候車時間、票價和耗時。https://www.busiscoming.com`

#### Scenario: 無法分享應用
- **WHEN** 用戶點擊 `分享應用`
- **AND** 系統沒有可處理分享 Intent 的 App 或分享面板打開失敗
- **THEN** 系統 SHALL 顯示 Toast `暫時無法分享應用`
- **AND** 系統 SHALL 保持停留在設定頁

### Requirement: 問題反饋使用郵件 Intent
系統 SHALL 允許用戶從設定頁透過郵件提交問題反饋，並預填基本診斷資訊。

#### Scenario: 成功打開問題反饋郵件
- **WHEN** 用戶在設定頁點擊 `問題反饋`
- **THEN** 系統 SHALL 打開郵件撰寫 Intent
- **AND** 收件人 SHALL 為 `hezhenyu966@gmail.com`
- **AND** 主題 SHALL 為 `BusIsComing 問題反饋`
- **AND** 正文 SHALL 預填 App 版本、Android 版本、設備型號與問題描述提示

#### Scenario: 無法打開問題反饋
- **WHEN** 用戶點擊 `問題反饋`
- **AND** 系統沒有可處理郵件 Intent 的 App 或郵件撰寫打開失敗
- **THEN** 系統 SHALL 顯示 Toast `暫時無法開啟問題反饋`
- **AND** 系統 SHALL 保持停留在設定頁

### Requirement: 關於我們展示 App 基本資訊
系統 SHALL 在設定頁內提供關於我們二級頁，展示 App 基本資訊與官網入口。

#### Scenario: 打開關於我們頁
- **WHEN** 用戶在設定頁點擊 `關於我們`
- **THEN** 系統 SHALL 打開標題為 `關於我們` 的二級頁
- **AND** 頁面 SHALL 顯示 App 名稱 `BusIsComing`
- **AND** 頁面 SHALL 顯示目前 App 版本
- **AND** 頁面 SHALL 顯示簡介 `保存常用路線，出門前快速比較巴士方案。`
- **AND** 頁面 SHALL 顯示官網入口 `https://www.busiscoming.com`

#### Scenario: 從關於我們頁返回設定
- **WHEN** 用戶在關於我們頁點擊左上返回入口或按系統返回
- **THEN** 系統 SHALL 返回設定頁

#### Scenario: 打開官網
- **WHEN** 用戶在關於我們頁點擊官網入口
- **THEN** 系統 SHALL 使用外部瀏覽器或等效系統能力打開 `https://www.busiscoming.com`

#### Scenario: 官網打開失敗
- **WHEN** 用戶點擊官網入口
- **AND** 系統無法打開該 URL
- **THEN** 系統 SHALL 顯示 Toast `暫時無法開啟網站`
- **AND** 系統 SHALL 保持停留在關於我們頁

### Requirement: 隱私政策打開線上 URL
系統 SHALL 讓用戶從設定頁打開線上隱私政策，並使用固定繁體 URL 作為本期來源。

#### Scenario: 成功打開隱私政策
- **WHEN** 用戶在設定頁點擊 `隱私政策`
- **THEN** 系統 SHALL 使用外部瀏覽器或等效系統能力打開 `https://www.busiscoming.com/zh-hant/privacy/`

#### Scenario: 隱私政策打開失敗
- **WHEN** 用戶點擊 `隱私政策`
- **AND** 系統無法打開該 URL
- **THEN** 系統 SHALL 顯示 Toast `暫時無法開啟隱私政策`
- **AND** 系統 SHALL 保持停留在設定頁
