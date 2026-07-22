## ADDED Requirements

### Requirement: 正式乘車碼由系統快捷與候車情境提供
系統 SHALL 完全移除常用頁乘車碼入口，並由長按 App 圖示的靜態 shortcut、用戶確認的桌面 pinned shortcut及候車監控通知 action 提供正式入口。

#### Scenario: 長按 App 圖示顯示靜態入口
- **WHEN** Android launcher 支援 App shortcuts 且用戶長按 BusIsComing 圖示
- **THEN** 系統 SHALL 顯示本地化的「乘車碼」shortcut
- **AND** 點擊後 SHALL 直接進入正式乘車碼候選鏈

#### Scenario: 桌面快捷方式直接開啟
- **WHEN** 用戶已從設定頁固定乘車碼 shortcut 並點擊桌面圖示
- **THEN** 系統 SHALL 直接進入與靜態 shortcut 相同的正式乘車碼候選鏈
- **AND** 系統 SHALL NOT 要求先切換至常用頁

#### Scenario: 常用頁不顯示乘車碼
- **WHEN** 用戶查看常用頁的一般、首次空狀態或結果狀態
- **THEN** 系統 SHALL NOT 顯示乘車碼按鈕、懸浮入口、固定工具區或 coachmark

### Requirement: 正式入口與實驗入口及巴士查詢狀態隔離
系統 SHALL 將所有正式乘車碼入口匯入同一單按鈕自動拉起流程，並 SHALL NOT 保留或依賴已廢棄的微信／AlipayHK 實驗面板；正式入口 SHALL 與巴士查詢及候車監控狀態隔離。

#### Scenario: 正式入口不打開實驗面板
- **WHEN** 用戶使用靜態 shortcut、pinned shortcut 或通知乘車碼 action
- **THEN** 系統 SHALL 直接執行正式乘車碼候選鏈
- **AND** 系統 SHALL NOT 顯示實驗性乘車碼底部彈層、候選列表或診斷摘要

#### Scenario: 正式入口不嘗試微信
- **WHEN** 用戶使用任何正式乘車碼入口
- **THEN** 系統 SHALL NOT 嘗試微信 OpenSDK、小程序或微信 scheme
- **AND** 系統 SHALL NOT 引入微信 OpenSDK runtime dependency

#### Scenario: 使用乘車碼後不保存支付偏好
- **WHEN** 用戶使用正式乘車碼入口並成功或失敗返回 App
- **THEN** 系統 SHALL NOT 寫入任何 AlipayHK／支付寶偏好
- **AND** 系統 SHALL NOT 新增、修改或刪除任何常用行程資料

#### Scenario: 使用乘車碼不改變巴士狀態
- **WHEN** 用戶已有選中行程、搜尋上下文、排序、查詢結果或運行中的監控時使用正式乘車碼入口
- **THEN** 系統 SHALL 保留既有巴士查詢狀態與監控 session
- **AND** 系統 SHALL NOT 自動查詢、刷新、清空結果或停止監控
