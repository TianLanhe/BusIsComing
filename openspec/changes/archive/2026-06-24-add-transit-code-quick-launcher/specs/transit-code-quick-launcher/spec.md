## ADDED Requirements

### Requirement: 正式乘車碼入口按安裝狀態組裝候選鏈
系統 SHALL 在用戶使用正式 `乘車碼` 入口時，根據本機 AlipayHK 與支付寶安裝狀態自動組裝固定候選鏈，並優先使用 AlipayHK。

#### Scenario: 只安裝 AlipayHK
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統明確檢測到只安裝了 AlipayHK
- **THEN** 系統 SHALL 先嘗試 `alipayhk://platformapi/startApp?appId=85200098`
- **AND** 若該候選本地啟動失敗，系統 SHALL 再嘗試 `https://render.alipay.hk/p/s/hkwallet/landing/easygo`
- **AND** 系統 SHALL NOT 嘗試支付寶候選入口

#### Scenario: 只安裝支付寶
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統明確檢測到只安裝了支付寶
- **THEN** 系統 SHALL 先嘗試 `alipays://platformapi/startapp?appId=200011235`
- **AND** 若該候選本地啟動失敗，系統 SHALL 再嘗試 `https://render.alipay.com/p/s/i?appId=200011235`
- **AND** 系統 SHALL NOT 嘗試 AlipayHK 候選入口

#### Scenario: 同時安裝 AlipayHK 與支付寶
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統檢測到 AlipayHK 與支付寶都已安裝
- **THEN** 系統 SHALL 依序嘗試 `alipayhk://platformapi/startApp?appId=85200098`、`https://render.alipay.hk/p/s/hkwallet/landing/easygo`、`alipays://platformapi/startapp?appId=200011235`、`https://render.alipay.com/p/s/i?appId=200011235`
- **AND** 系統 SHALL 僅在 AlipayHK 兩個候選都本地啟動失敗後才降級嘗試支付寶候選

#### Scenario: AlipayHK 與支付寶都未安裝
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統檢測到 AlipayHK 與支付寶都未安裝
- **THEN** 系統 SHALL 直接嘗試 `https://render.alipay.hk/p/s/hkwallet/landing/easygo`
- **AND** 系統 SHALL NOT 嘗試任何 scheme 候選
- **AND** 系統 SHALL NOT 顯示平台選擇器

### Requirement: 本地啟動失敗才觸發自動兜底
系統 SHALL 只在當前候選發生本地啟動失敗時嘗試下一個候選；一旦 Android 接受啟動請求，系統 SHALL 停止候選遍歷。

#### Scenario: scheme 沒有可處理 Activity
- **WHEN** 系統嘗試當前 scheme 候選且 Android 找不到可處理 Activity
- **THEN** 系統 SHALL 記錄該候選本地啟動失敗
- **AND** 若候選鏈仍有下一項，系統 SHALL 自動嘗試下一項

#### Scenario: startActivity 發生安全或啟動異常
- **WHEN** 系統嘗試當前候選且啟動流程發生 `SecurityException`、`ActivityNotFoundException` 或其他可捕獲啟動異常
- **THEN** 系統 SHALL 將該候選視為本地啟動失敗
- **AND** 若候選鏈仍有下一項，系統 SHALL 自動嘗試下一項

#### Scenario: 候選被 Android 成功接受
- **WHEN** 系統嘗試任一候選且 Android 成功接受啟動請求
- **THEN** 系統 SHALL 停止嘗試候選鏈中後續項目
- **AND** 系統 SHALL NOT 因無法判斷外部錢包內部頁面而繼續兜底
- **AND** 系統 SHALL NOT 顯示失敗 toast

#### Scenario: 所有候選均本地啟動失敗
- **WHEN** 系統已嘗試當前安裝狀態對應候選鏈中的所有候選且全部本地啟動失敗
- **THEN** 系統 SHALL 停止候選遍歷
- **AND** 系統 SHALL 顯示 `未能開啟乘車碼，請確認已安裝 AlipayHK 或支付寶。`

### Requirement: 正式入口與實驗入口及巴士查詢狀態隔離
系統 SHALL 將正式乘車碼入口作為單按鈕自動拉起流程，並與既有乘車碼實驗面板及巴士查詢狀態隔離。

#### Scenario: 正式入口不打開實驗面板
- **WHEN** 用戶點擊主頁正式 `乘車碼` 入口
- **THEN** 系統 SHALL 直接執行正式乘車碼候選鏈
- **AND** 系統 SHALL NOT 顯示 `實驗性乘車碼入口` 底部彈層
- **AND** 系統 SHALL NOT 顯示微信 SDK、AlipayHK 實驗候選列表或診斷摘要

#### Scenario: 正式入口不嘗試微信
- **WHEN** 用戶點擊主頁正式 `乘車碼` 入口
- **THEN** 系統 SHALL NOT 嘗試微信 OpenSDK、小程序 `userName` 或任何微信 scheme 候選
- **AND** 系統 SHALL NOT 因微信 `has_no_permission` 或其他微信錯誤阻塞 AlipayHK／支付寶流程

#### Scenario: 使用乘車碼入口後不保存支付偏好
- **WHEN** 用戶使用正式 `乘車碼` 入口並成功或失敗返回 App
- **THEN** 系統 SHALL NOT 寫入任何 AlipayHK／支付寶偏好
- **AND** 系統 SHALL NOT 新增、修改或刪除任何常用路線資料

#### Scenario: 使用乘車碼入口後不改變巴士查詢狀態
- **WHEN** 用戶在主頁已有選中路線、臨時查詢上下文、排序選擇或查詢結果時點擊正式 `乘車碼`
- **THEN** 系統 SHALL 保留既有巴士查詢狀態
- **AND** 系統 SHALL NOT 自動發起 Citybus 查詢、刷新結果或清空結果列表

### Requirement: 系統聲明正式錢包 package visibility
系統 SHALL 在 Android 11+ package visibility 限制下能檢測 AlipayHK 與支付寶安裝狀態，以支援正式候選鏈決策。

#### Scenario: Manifest 包含正式錢包查詢聲明
- **WHEN** App 在 Android 11+ 裝置上運行
- **THEN** manifest SHALL 包含 `hk.alipay.wallet` package query 聲明
- **AND** manifest SHALL 包含 `com.eg.android.AlipayGphone` package query 聲明

#### Scenario: package 檢測結果不可用時降級為未安裝
- **WHEN** 系統查詢 AlipayHK 或支付寶 package 狀態時發生可捕獲異常或無法確認已安裝
- **THEN** 系統 SHALL 將該錢包視為未安裝來組裝候選鏈
- **AND** 系統 SHALL NOT 因 package 查詢失敗而讓 `乘車碼` 點擊崩潰
