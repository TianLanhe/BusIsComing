## ADDED Requirements

### Requirement: 應用評分打開官方 Google Play 商品詳情頁
系統 SHALL 在官方 Google Play 可用時，因應用評分入口的一次明確使用者操作打開 BusIsComing 的 Google Play 商品詳情頁。

#### Scenario: Google Play 可用
- **WHEN** 用戶在設定頁點擊 `應用評分`
- **AND** 官方 Google Play 已安裝、啟用且可處理 BusIsComing 商品詳情 Intent
- **THEN** 系統 SHALL 使用 `com.android.vending` 打開 BusIsComing 的官方商品詳情頁
- **AND** 系統 SHALL NOT 先顯示額外確認步驟

#### Scenario: App 不是由 Google Play 安裝
- **WHEN** BusIsComing 的初始安裝來源不是 Google Play 或無法確定
- **AND** 目前官方 Google Play 可用
- **THEN** 系統 SHALL 仍打開同一官方商品詳情頁
- **AND** 系統 SHALL 讓 Google Play 決定目前帳號是否可評分

#### Scenario: 不使用 In-App Review
- **WHEN** 用戶點擊 `應用評分`
- **THEN** 系統 SHALL NOT 啟動 Play In-App Review
- **AND** 系統 SHALL NOT 以 quota、Task 成功或其他不可觀察訊號判斷評分介面已展示

#### Scenario: 不追蹤評分提交
- **WHEN** 用戶從 Google Play 返回 BusIsComing
- **THEN** 系統 SHALL NOT 記錄、推測或顯示使用者已完成評分
- **AND** 系統 SHALL NOT 顯示依賴評分完成狀態的感謝或獎勵

### Requirement: Google Play 不可用時提供對應恢復路徑
系統 SHALL 區分 Google Play 已停用、未安裝及已啟用但不可使用，並 SHALL 只展示與目前可信狀態相符的本地化恢復操作。

#### Scenario: Google Play 已安裝但停用
- **WHEN** 用戶點擊 `應用評分`
- **AND** 系統確認 `com.android.vending` 已安裝但停用
- **THEN** 系統 SHALL 顯示 Google Play 已停用的 Material 對話框
- **AND** 對話框 SHALL 提供前往 Google Play 系統應用詳情頁的 `前往啟用` 操作
- **AND** 系統 SHALL NOT 打開商品頁或瀏覽器 fallback

#### Scenario: Google Play 未安裝
- **WHEN** 用戶點擊 `應用評分`
- **AND** 系統確認裝置找不到 `com.android.vending`
- **THEN** 系統 SHALL 顯示未找到 Google Play 的 Material 對話框
- **AND** 對話框 SHALL 提供打開 Google 官方 Play 尋找／恢復說明的操作
- **AND** 系統 SHALL NOT 導向第三方商店、第三方 APK 或瀏覽器商品頁

#### Scenario: Google Play 已啟用但商品 Intent 不可用
- **WHEN** 用戶點擊 `應用評分`
- **AND** `com.android.vending` 已啟用但無法解析 BusIsComing 商品詳情 Intent，或 package 探測無法取得可信可用結果
- **THEN** 系統 SHALL 顯示目前無法使用 Google Play 的 Material 對話框
- **AND** 對話框 SHALL 提供打開 BusIsComing 系統應用詳情頁的 `應用設定` 操作
- **AND** 系統 SHALL NOT 嘗試無 package 限定的商品 URL

#### Scenario: 取消不可用提示
- **WHEN** 用戶取消任一 Google Play 不可用對話框或按系統返回
- **THEN** 系統 SHALL 保持停留在設定頁
- **AND** 系統 SHALL NOT 發起任何外部導航

### Requirement: 外部導航失敗可恢復且不自動續辦
系統 SHALL 捕獲商品頁、系統設定及官方說明的可恢復啟動失敗，並 SHALL 只在新的明確使用者操作後再次嘗試外部導航。

#### Scenario: Google Play 商品頁啟動失敗
- **WHEN** 系統已判定 Google Play 可用但啟動商品詳情頁時失敗
- **THEN** 系統 SHALL 保持停留在設定頁並顯示目前語言的無法開啟提示
- **AND** 系統 SHALL NOT 改用瀏覽器或其他商店

#### Scenario: 恢復 destination 啟動失敗
- **WHEN** 用戶選擇前往啟用、查看官方說明或應用設定
- **AND** 對應外部 destination 無法解析或啟動
- **THEN** 系統 SHALL 保持或返回設定頁並顯示目前語言的無法開啟提示
- **AND** 系統 SHALL NOT 串接其他 fallback destination

#### Scenario: 從外部 destination 返回
- **WHEN** 用戶由 Google Play、系統應用詳情頁或 Google 官方說明返回 BusIsComing
- **THEN** 系統 SHALL 只恢復設定頁
- **AND** 系統 SHALL NOT 自動打開商品詳情頁、再次顯示恢復對話框或重複啟動外部 Intent
- **AND** 用戶可再次點擊 `應用評分` 重新檢查目前狀態

### Requirement: 評分提示具備三語與無障礙契約
系統 SHALL 以目前 App 語言呈現評分不可用與啟動失敗回饋，並 SHALL 讓輔助技術讀取提示、狀態及可執行操作而不產生誤導。

#### Scenario: 顯示評分狀態提示
- **WHEN** 系統顯示 Google Play 停用、缺失、不可用或啟動失敗回饋
- **THEN** 標題、正文與操作 SHALL 同時具備香港繁體、獨立簡體及自然英文資源
- **AND** TalkBack SHALL 能按自然順序讀取狀態與按鈕
- **AND** 文案 SHALL NOT 宣稱 Google Play 已安裝、評分介面已展示或評分已提交，除非對應事實可被系統直接確認
