## MODIFIED Requirements

### Requirement: 正式入口與實驗入口及巴士查詢狀態隔離
系統 SHALL 將正式乘車碼入口作為單按鈕自動拉起流程，並 SHALL NOT 保留或依賴已廢棄的微信／AlipayHK 實驗面板；正式入口 SHALL 與巴士查詢狀態隔離。

#### Scenario: 正式入口不打開實驗面板
- **WHEN** 用戶點擊主頁正式 `乘車碼` 入口
- **THEN** 系統 SHALL 直接執行正式乘車碼候選鏈
- **AND** 系統 SHALL NOT 顯示 `實驗性乘車碼入口` 底部彈層
- **AND** 系統 SHALL NOT 顯示微信 SDK、AlipayHK 實驗候選列表或診斷摘要
- **AND** 系統 SHALL NOT 在上架包中保留可直接打開該實驗面板的 current UI 或測試入口

#### Scenario: 正式入口不嘗試微信
- **WHEN** 用戶點擊主頁正式 `乘車碼` 入口
- **THEN** 系統 SHALL NOT 嘗試微信 OpenSDK、小程序 `userName` 或任何微信 scheme 候選
- **AND** 系統 SHALL NOT 因微信 `has_no_permission` 或其他微信錯誤阻塞 AlipayHK／支付寶流程
- **AND** 系統 SHALL NOT 引入微信 OpenSDK runtime dependency

#### Scenario: 使用乘車碼入口後不保存支付偏好
- **WHEN** 用戶使用正式 `乘車碼` 入口並成功或失敗返回 App
- **THEN** 系統 SHALL NOT 寫入任何 AlipayHK／支付寶偏好
- **AND** 系統 SHALL NOT 新增、修改或刪除任何常用路線資料

#### Scenario: 使用乘車碼入口後不改變巴士查詢狀態
- **WHEN** 用戶在主頁已有選中路線、臨時查詢上下文、排序選擇或查詢結果時點擊正式 `乘車碼`
- **THEN** 系統 SHALL 保留既有巴士查詢狀態
- **AND** 系統 SHALL NOT 自動發起 Citybus 查詢、刷新結果或清空結果列表

### Requirement: 系統聲明正式錢包 package visibility
系統 SHALL 在 Android 11+ package visibility 限制下能檢測 AlipayHK 與支付寶安裝狀態，以支援正式候選鏈決策，且 SHALL NOT 為已移除的微信實驗能力聲明 package visibility。

#### Scenario: Manifest 包含正式錢包查詢聲明
- **WHEN** App 在 Android 11+ 裝置上運行
- **THEN** manifest SHALL 包含 `hk.alipay.wallet` package query 聲明
- **AND** manifest SHALL 包含 `com.eg.android.AlipayGphone` package query 聲明
- **AND** manifest SHALL NOT 包含 `com.tencent.mm` package query 聲明
- **AND** manifest SHALL NOT 註冊 `.wxapi.WXEntryActivity`

#### Scenario: package 檢測結果不可用時降級為未安裝
- **WHEN** 系統查詢 AlipayHK 或支付寶 package 狀態時發生可捕獲異常或無法確認已安裝
- **THEN** 系統 SHALL 將該錢包視為未安裝來組裝候選鏈
- **AND** 系統 SHALL NOT 因 package 查詢失敗而讓 `乘車碼` 點擊崩潰
