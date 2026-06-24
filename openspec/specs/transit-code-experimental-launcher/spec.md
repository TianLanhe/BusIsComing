# transit-code-experimental-launcher Specification

## Purpose
保留乘車碼實驗面板與診斷能力，方便開發與真機排查第三方跳轉入口；正式主頁 `乘車碼` 入口已由 `transit-code-quick-launcher` 接管，普通用戶不再從主頁進入實驗面板。

## Requirements
### Requirement: 實驗面板作為內部診斷能力保留
系統 SHALL 保留實驗性乘車碼底部彈層能力，用於直接測試第三方候選入口與查看最近診斷；主頁正式入口 SHALL NOT 依賴或打開該實驗面板。

#### Scenario: 主頁正式入口不打開實驗面板
- **WHEN** 用戶點擊主頁 `乘車碼` 入口
- **THEN** 系統 SHALL 執行正式乘車碼拉起流程
- **AND** 系統 SHALL NOT 顯示 `實驗性乘車碼入口` 底部彈層

#### Scenario: 實驗面板直接展示診斷文案
- **WHEN** 開發或測試流程直接顯示乘車碼實驗面板
- **THEN** 彈層 SHALL 顯示 `實驗性乘車碼入口`
- **AND** 彈層 SHALL 顯示最近一次診斷區域

### Requirement: 實驗面板列出微信 SDK 與 AlipayHK 候選入口
系統 SHALL 在底部彈層中按平台分組展示 5 條固定候選入口，讓測試者能分別嘗試微信 OpenSDK 與 AlipayHK 跳轉方式。

#### Scenario: 顯示微信 SDK 候選入口
- **WHEN** 系統顯示實驗性乘車碼入口底部彈層
- **THEN** 彈層 SHALL 顯示 `微信 SDK` 分組
- **AND** `微信 SDK` 分組 SHALL 包含 `微信 SDK 正式版` 入口，其 AppID 為 `wx0a914d80e5b75bfa`，`userName` 為 `gh_a2de39e7aeb4`，path 為空，`miniprogramType` 為 `MINIPTOGRAM_TYPE_RELEASE` / `0`
- **AND** `微信 SDK` 分組 SHALL 包含 `微信 SDK 測試版` 入口，其 AppID 為 `wx0a914d80e5b75bfa`，`userName` 為 `gh_a2de39e7aeb4`，path 為空，`miniprogramType` 為 `MINIPROGRAM_TYPE_TEST` / `1`
- **AND** `微信 SDK` 分組 SHALL 包含 `微信 SDK 預覽版` 入口，其 AppID 為 `wx0a914d80e5b75bfa`，`userName` 為 `gh_a2de39e7aeb4`，path 為空，`miniprogramType` 為 `MINIPROGRAM_TYPE_PREVIEW` / `2`
- **AND** 彈層 SHALL NOT 顯示上一輪 `weixin://` scheme 候選入口

#### Scenario: 顯示 AlipayHK 候選入口
- **WHEN** 系統顯示實驗性乘車碼入口底部彈層
- **THEN** 彈層 SHALL 顯示 `AlipayHK` 分組
- **AND** `AlipayHK` 分組 SHALL 包含 `AlipayHK Scheme` 入口，其 URI 為 `alipayhk://platformapi/startApp?appId=85200098`
- **AND** `AlipayHK` 分組 SHALL 包含 `AlipayHK HTTPS` 入口，其 URL 為 `https://render.alipay.hk/p/s/hkwallet/landing/easygo`

#### Scenario: 不再顯示支付寶實驗入口
- **WHEN** 系統顯示實驗性乘車碼入口底部彈層
- **THEN** 彈層 SHALL NOT 顯示 `支付寶` 分組
- **AND** 彈層 SHALL NOT 顯示 `alipays://platformapi/startapp?appId=200011235`
- **AND** 彈層 SHALL NOT 顯示 `https://render.alipay.com/p/s/i?appId=200011235`

### Requirement: 候選入口獨立嘗試跳轉
系統 SHALL 在測試者點擊某一候選入口時只嘗試該入口自身的 SDK 請求、URI 或 URL，不自動嘗試其他候選入口。

#### Scenario: 點擊微信 SDK 候選入口
- **WHEN** 測試者在底部彈層點擊任一微信 SDK 候選入口
- **THEN** 系統 SHALL 使用該入口的 `miniprogramType` 建立 `WXLaunchMiniProgram.Req`
- **AND** 系統 SHALL 嘗試通過微信 OpenSDK 將請求送給微信
- **AND** 系統 SHALL NOT 自動嘗試其他微信 SDK、AlipayHK 或支付寶候選入口

#### Scenario: 點擊 AlipayHK 候選入口
- **WHEN** 測試者在底部彈層點擊任一 AlipayHK 候選入口
- **THEN** 系統 SHALL 使用該入口自身 URI 或 URL 建立 `ACTION_VIEW` intent 並嘗試交給 Android 系統處理
- **AND** 系統 SHALL NOT 自動嘗試其他 AlipayHK、微信 SDK 或支付寶候選入口

#### Scenario: 外部系統接受跳轉
- **WHEN** 測試者點擊候選入口且 Android、微信 SDK 或外部系統成功接受對應請求
- **THEN** 系統 SHALL 將後續流程交給微信、AlipayHK、瀏覽器或系統中轉頁
- **AND** App SHALL NOT 顯示錯誤 toast
- **AND** App SHALL NOT 假設已成功到達乘車碼頁

### Requirement: 實驗啟動診斷可見
系統 SHALL 在每次實驗入口點擊後輸出可觀察診斷，協助判斷本地安裝狀態、SDK 能力、啟動結果與微信回調結果。

#### Scenario: 微信 SDK 啟動診斷
- **WHEN** 測試者點擊任一微信 SDK 候選入口
- **THEN** 系統 SHALL 在 logcat 輸出該入口的 AppID、`userName`、path、`miniprogramType` 名稱和值
- **AND** 系統 SHALL 在 logcat 輸出微信 package 是否可見、微信是否已安裝、SDK 是否支援、`registerApp` 結果與 `sendReq` 結果
- **AND** 系統 SHALL 在實驗面板顯示最近一次微信 SDK 診斷摘要

#### Scenario: 微信 SDK 回調診斷
- **WHEN** 微信 SDK 回調 App
- **THEN** 系統 SHALL 記錄回調 `errCode`
- **AND** 系統 SHALL 記錄可取得的 `errStr` 與 `extMsg`
- **AND** 系統 SHALL 更新 logcat 與實驗面板中的最近一次診斷結果

#### Scenario: AlipayHK 啟動診斷
- **WHEN** 測試者點擊任一 AlipayHK 候選入口
- **THEN** 系統 SHALL 在 logcat 輸出該入口的 URI 或 URL
- **AND** 系統 SHALL 在 logcat 輸出 Android 是否找到可處理該 intent 的 Activity、`startActivity` 是否成功以及本地異常 class/message
- **AND** 系統 SHALL 在實驗面板顯示最近一次 AlipayHK 診斷摘要

### Requirement: 跳轉失敗時保持穩定並提示
系統 SHALL 在候選入口無法打開時保持 App 穩定，並提供可理解的提示與診斷。

#### Scenario: 微信 SDK 入口無法送出
- **WHEN** 測試者點擊微信 SDK 候選入口且本地預檢、`registerApp` 或 `sendReq` 判定無法送出請求
- **THEN** App SHALL NOT 崩潰
- **AND** 系統 SHALL 顯示 `未能開啟微信乘車碼入口，請查看診斷結果。`
- **AND** 底部彈層 SHALL 保持可見或可立即返回以便測試者查看診斷並嘗試其他入口

#### Scenario: AlipayHK 入口無法打開
- **WHEN** 測試者點擊 AlipayHK 候選入口且系統無法處理對應 intent
- **THEN** App SHALL NOT 崩潰
- **AND** 系統 SHALL 顯示 `未能開啟 AlipayHK 乘車碼入口，請查看診斷結果。`
- **AND** 底部彈層 SHALL 保持可見或可立即返回以便測試者查看診斷並嘗試其他入口

#### Scenario: 非預期啟動異常
- **WHEN** 測試者點擊任一候選入口且啟動流程遇到無法歸類的非預期異常
- **THEN** App SHALL NOT 崩潰
- **AND** 系統 SHALL 顯示 `未能開啟此實驗入口，請查看診斷結果。`
- **AND** 系統 SHALL 在診斷中記錄異常 class 與 message

### Requirement: 實驗入口不保存偏好且不影響巴士查詢
系統 SHALL 將乘車碼實驗能力與既有巴士查詢狀態隔離。

#### Scenario: 使用乘車碼實驗入口後不保存支付偏好
- **WHEN** 測試者打開底部彈層並點擊任一候選入口
- **THEN** 系統 SHALL NOT 將該支付平台或候選入口保存為用戶偏好
- **AND** 系統 SHALL NOT 建立新的本機資料表或修改既有路線配置資料

#### Scenario: 使用乘車碼實驗入口後不改變查詢結果
- **WHEN** 測試者打開或關閉乘車碼實驗面板
- **THEN** 系統 SHALL 保留既有常用路線、臨時查詢上下文、查詢結果、排序狀態與更新時間
- **AND** 系統 SHALL NOT 發起 Citybus 路線查詢、ETA 查詢或通知監控操作

### Requirement: 系統聲明微信與 AlipayHK package visibility
系統 SHALL 在 Android manifest 中聲明微信與 AlipayHK package visibility，以支援 Android 11+ 的外部 App 可見性限制。

#### Scenario: Manifest 包含微信與 AlipayHK 查詢聲明
- **WHEN** App 在 Android 11 或以上系統運行
- **THEN** manifest SHALL 包含 `com.tencent.mm` 的 package query 聲明
- **AND** manifest SHALL 包含 AlipayHK package query 聲明
- **AND** manifest SHALL NOT 建立重複的 `<queries>` 節點

#### Scenario: Manifest 包含微信 SDK 回調 Activity
- **WHEN** App 安裝在 Android 裝置上
- **THEN** manifest SHALL 註冊微信 OpenSDK 可回調的 `.wxapi.WXEntryActivity`
- **AND** 該 Activity SHALL 能接收微信 SDK 回調並交給 SDK handleIntent 流程處理
