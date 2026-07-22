# top-level-module-navigation Specification

## Purpose
定義常用、搜尋、設定三個固定頂層 destination 的底部導航、狀態保留、無障礙與次級頁返回行為，讓主要任務在同一宿主內可預期切換。

## Requirements

### Requirement: App 提供三個固定頂層 destination
系統 SHALL 在主 App 介面底部提供「常用」「搜尋」「設定」三個固定 destination，並以圖示和繁體中文文字共同表達目前選中項目。

#### Scenario: 冷啟動進入常用
- **WHEN** 用戶從 launcher 冷啟動 App
- **THEN** 系統 SHALL 顯示「常用」destination
- **AND** 底部導航 SHALL 將「常用」標識為目前選中項目

#### Scenario: 切換頂層 destination
- **WHEN** 用戶點擊「搜尋」或「設定」底部導航項目
- **THEN** 系統 SHALL 在同一個頂層宿主中顯示對應 destination
- **AND** 系統 SHALL 不以次級返回頁或 modal 代替該 destination

#### Scenario: 頂層導航具可達性
- **WHEN** 用戶以觸控或無障礙服務訪問底部導航
- **THEN** 每個導航項目 SHALL 有可理解的繁體中文名稱與選中狀態
- **AND** 每個項目的可觸控區域 SHALL 不小於 48dp

### Requirement: 頂層 destination 保留各自狀態
系統 SHALL 在用戶於「常用」「搜尋」「設定」之間切換時，保留每個 destination 已建立的可見狀態，而不以切換動作清空其他 destination。

#### Scenario: 常用與搜尋切換
- **WHEN** 用戶已在常用頁選中路線或取得查詢結果後切換到搜尋，再返回常用
- **THEN** 系統 SHALL 保留常用頁的選中路線、排序、結果與列表位置
- **AND** 系統 SHALL 不重新發起常用路線查詢

#### Scenario: 搜尋頁切換後返回
- **WHEN** 用戶已在搜尋頁輸入起終點、展開候選或取得結果後切換到其他 destination，再返回搜尋
- **THEN** 系統 SHALL 保留尚未提交的輸入或最近一次有效查詢的起終點、結果、排序與列表位置
- **AND** 系統 SHALL 不因 destination 切換將有效搜尋自動保存為常用路線

#### Scenario: 宿主重建後恢復頂層頁
- **WHEN** Activity 因旋轉或系統重建而恢復
- **THEN** 系統 SHALL 恢復先前選中的頂層 destination
- **AND** 系統 SHALL 恢復可安全保存的選中路線、起終點與排序狀態

### Requirement: 次級頁維持返回導航
系統 SHALL 將路線管理、路線新增或編輯、關於及其他既有次級內容視為頂層 destination 之外的頁面。

#### Scenario: 從常用進入路線管理
- **WHEN** 用戶從常用頁開啟路線管理或路線編輯
- **THEN** 系統 SHALL 進入既有次級頁並提供返回行為
- **AND** 次級頁 SHALL 不與底部導航並列為另一個頂層 destination

#### Scenario: 從設定進入關於
- **WHEN** 用戶在設定頁開啟關於內容
- **THEN** 系統 SHALL 開啟既有關於頁
- **AND** 用戶返回後 SHALL 回到設定 destination

### Requirement: 設定作為頂層頁面呈現
系統 SHALL 在「設定」destination 顯示既有設定、支援與關於入口，且不得將設定本身呈現為需要返回上一頁的次級頁。

#### Scenario: 開啟設定 destination
- **WHEN** 用戶透過底部導航進入「設定」
- **THEN** 系統 SHALL 顯示設定標題、版本、偏好、支援與關於內容
- **AND** 系統 SHALL 不顯示「返回上一頁」按鈕或 ActionBar home 作為設定頁的主要入口

#### Scenario: 常用頁不再顯示設定快捷入口
- **WHEN** 用戶查看常用頁的頂部操作區
- **THEN** 系統 SHALL 保留乘車碼與適用的常用路線管理操作
- **AND** 系統 SHALL 不顯示前往設定的右上角快捷按鈕
