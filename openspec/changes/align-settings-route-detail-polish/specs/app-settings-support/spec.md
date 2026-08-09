## MODIFIED Requirements

### Requirement: 分享應用使用系統分享面板
系統 SHALL 允許用戶從設定頁分享 BusIsComing，並使用目前 App 語言的一句精簡核心價值文案、Google Play 商品頁及對應語言的官方網站下載頁。

#### Scenario: 成功打開分享面板
- **WHEN** 用戶在設定頁點擊 `分享應用`
- **THEN** 系統 SHALL 打開系統分享面板
- **AND** 分享內容 SHALL 使用目前 App 語言說明 BusIsComing 可比較 Citybus 路線與實時到站時間，以協助掌握出發時機
- **AND** 分享內容 SHALL 先列出 `https://play.google.com/store/apps/details?id=com.golink.busiscoming`
- **AND** 官方網站下載 URL SHALL 對繁體、簡體、英文分別使用 `https://www.busiscoming.com/zh-hant/#download`、`https://www.busiscoming.com/zh-hans/#download`、`https://www.busiscoming.com/en/#download`
- **AND** 分享內容 SHALL NOT 宣稱支援 Citybus 以外的巴士營運商

#### Scenario: 無法分享應用
- **WHEN** 用戶點擊 `分享應用`
- **AND** 系統沒有可處理分享 Intent 的 App 或分享面板打開失敗
- **THEN** 系統 SHALL 以目前 App 語言顯示無法分享提示
- **AND** 系統 SHALL 保持停留在設定頁

### Requirement: 關於我們展示 App 基本資訊
系統 SHALL 在設定頁內提供關於我們二級頁，以目前 App 語言展示 App 基本資訊、精簡產品介紹與對應官方網站首頁入口。

#### Scenario: 打開關於我們頁
- **WHEN** 用戶在設定頁點擊 `關於我們`
- **THEN** 系統 SHALL 打開使用目前 App 語言標題的二級頁
- **AND** 頁面 SHALL 顯示 App 名稱 `BusIsComing`
- **AND** 頁面 SHALL 顯示目前 App 版本
- **AND** 頁面簡介 SHALL 先表達 App 為香港巴士通勤而設，並明確說明目前可比較 Citybus 路線與實時到站時間
- **AND** 頁面簡介 SHALL 以第二段補充常用行程、地圖詳情及通知欄監察
- **AND** 頁面簡介 SHALL NOT 宣稱支援 Citybus 以外的巴士營運商
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
