## ADDED Requirements

### Requirement: 主頁提供實驗性乘車碼入口
系統 SHALL 在主查詢頁提供一個低干擾的「乘車碼」入口，用於打開實驗性第三方乘車碼跳轉面板。

#### Scenario: 主頁顯示乘車碼入口
- **WHEN** 用戶打開主查詢頁
- **THEN** 系統 SHALL 在主頁頂部標題區顯示「乘車碼」入口
- **AND** 系統 SHALL 保留既有「巴士查詢」標題和「管理路線」入口
- **AND** 系統 SHALL NOT 自動發起任何微信、支付寶或 Citybus 以外的第三方跳轉

#### Scenario: 點擊入口打開實驗面板
- **WHEN** 用戶點擊主頁「乘車碼」入口
- **THEN** 系統 SHALL 顯示底部彈層
- **AND** 彈層 SHALL 明確表達這些入口屬於實驗性乘車碼入口
- **AND** 系統 SHALL NOT 改變目前選中的常用路線、臨時查詢上下文、排序狀態或已展示的路線結果

### Requirement: 實驗面板列出微信與支付寶候選入口
系統 SHALL 在底部彈層中按平台分組展示 7 條固定候選入口，讓用戶能分別嘗試每條 URI 或 URL。

#### Scenario: 顯示微信候選入口
- **WHEN** 系統顯示實驗性乘車碼入口底部彈層
- **THEN** 彈層 SHALL 顯示「微信」分組
- **AND** 「微信」分組 SHALL 包含「微信 jumpWxa」入口，其 URI 為 `weixin://app/wxbe05102357855fc7/jumpWxa/?userName=gh_a2de39e7aeb4`
- **AND** 「微信」分組 SHALL 包含「微信明文 Scheme」入口，其 URI 為 `weixin://dl/business/?appid=wxbe05102357855fc7&env_version=release`
- **AND** 「微信」分組 SHALL 包含「微信首頁 path」入口，其 URI 為 `weixin://dl/business/?appid=wxbe05102357855fc7&path=pages/index/index&env_version=release`

#### Scenario: 顯示支付寶候選入口
- **WHEN** 系統顯示實驗性乘車碼入口底部彈層
- **THEN** 彈層 SHALL 顯示「支付寶」分組
- **AND** 「支付寶」分組 SHALL 包含「支付寶 appId」入口，其 URI 為 `alipays://platformapi/startapp?appId=200011235`
- **AND** 「支付寶」分組 SHALL 包含「支付寶 saId」入口，其 URI 為 `alipays://platformapi/startapp?saId=200011235`
- **AND** 「支付寶」分組 SHALL 包含「支付寶 H5 render」入口，其 URL 為 `https://render.alipay.com/p/s/i?appId=200011235`
- **AND** 「支付寶」分組 SHALL 包含「支付寶 ds 包裝」入口，其 URL 為 `https://ds.alipay.com/?scheme=alipays%3A%2F%2Fplatformapi%2Fstartapp%3FappId%3D200011235`

### Requirement: 候選入口獨立嘗試跳轉
系統 SHALL 在用戶點擊某一候選入口時只嘗試該入口自身的 URI 或 URL，不自動嘗試其他候選入口。

#### Scenario: 點擊微信候選入口
- **WHEN** 用戶在底部彈層點擊任一微信候選入口
- **THEN** 系統 SHALL 使用該入口自身 URI 建立 `ACTION_VIEW` intent 並嘗試交給 Android 系統處理
- **AND** 系統 SHALL NOT 自動嘗試其他微信或支付寶候選入口

#### Scenario: 點擊支付寶候選入口
- **WHEN** 用戶在底部彈層點擊任一支付寶候選入口
- **THEN** 系統 SHALL 使用該入口自身 URI 或 URL 建立 `ACTION_VIEW` intent 並嘗試交給 Android 系統處理
- **AND** 系統 SHALL NOT 自動嘗試其他支付寶或微信候選入口

#### Scenario: 外部系統接受跳轉
- **WHEN** 用戶點擊候選入口且 Android 系統成功接受對應 intent
- **THEN** 系統 SHALL 將後續流程交給微信、支付寶、瀏覽器或系統中轉頁
- **AND** App SHALL NOT 顯示錯誤 toast
- **AND** App SHALL NOT 假設已成功到達乘車碼頁

### Requirement: 跳轉失敗時保持穩定並提示
系統 SHALL 在候選入口無法打開時保持 App 穩定，並提供可理解的 toast 提示。

#### Scenario: 微信入口無法打開
- **WHEN** 用戶點擊微信候選入口且系統無法處理對應 intent
- **THEN** App SHALL NOT 崩潰
- **AND** 系統 SHALL 顯示 `未能開啟微信乘車碼入口，請嘗試其他入口或手動打開微信。`
- **AND** 底部彈層 SHALL 保持可見或可立即返回以便用戶嘗試其他入口

#### Scenario: 支付寶入口無法打開
- **WHEN** 用戶點擊支付寶候選入口且系統無法處理對應 intent
- **THEN** App SHALL NOT 崩潰
- **AND** 系統 SHALL 顯示 `未能開啟支付寶乘車碼入口，請嘗試其他入口或手動打開支付寶。`
- **AND** 底部彈層 SHALL 保持可見或可立即返回以便用戶嘗試其他入口

#### Scenario: 非預期啟動異常
- **WHEN** 用戶點擊任一候選入口且啟動流程遇到無法歸類的非預期異常
- **THEN** App SHALL NOT 崩潰
- **AND** 系統 SHALL 顯示 `未能開啟此實驗入口。`

### Requirement: 實驗入口不保存偏好且不影響巴士查詢
系統 SHALL 將乘車碼實驗入口與既有巴士查詢狀態隔離。

#### Scenario: 使用乘車碼入口後不保存支付偏好
- **WHEN** 用戶打開底部彈層並點擊任一候選入口
- **THEN** 系統 SHALL NOT 將該支付平台或候選入口保存為用戶偏好
- **AND** 系統 SHALL NOT 建立新的本機資料表或修改既有路線配置資料

#### Scenario: 使用乘車碼入口後不改變查詢結果
- **WHEN** 用戶從主頁打開或關閉乘車碼實驗面板
- **THEN** 系統 SHALL 保留既有常用路線、臨時查詢上下文、查詢結果、排序狀態與更新時間
- **AND** 系統 SHALL NOT 發起 Citybus 路線查詢、ETA 查詢或通知監控操作

### Requirement: 系統聲明微信與支付寶 package visibility
系統 SHALL 在 Android manifest 中聲明微信與支付寶 package visibility，以支援 Android 11+ 的外部 App 可見性限制。

#### Scenario: Manifest 包含微信與支付寶查詢聲明
- **WHEN** App 在 Android 11 或以上系統運行
- **THEN** manifest SHALL 包含 `com.tencent.mm` 的 package query 聲明
- **AND** manifest SHALL 包含 `com.eg.android.AlipayGphone` 的 package query 聲明
- **AND** manifest SHALL NOT 建立重複的 `<queries>` 節點
