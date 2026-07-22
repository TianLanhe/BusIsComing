## ADDED Requirements

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

## MODIFIED Requirements

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
