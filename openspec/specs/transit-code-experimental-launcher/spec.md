# transit-code-experimental-launcher Specification

## Purpose
乘車碼實驗入口、微信 OpenSDK 接入與診斷面板已作為上架前清理項移除。當前 App 的生產乘車碼能力由 `transit-code-quick-launcher` 規格覆蓋，保留此規格用於明確「實驗入口不可再作為 current 能力」。

## Requirements
### Requirement: 實驗乘車碼入口已移除
系統 SHALL NOT 在當前生產 App 中保留乘車碼實驗底部彈層、微信 SDK 候選入口或微信回調診斷能力。

#### Scenario: 主頁乘車碼入口不打開實驗面板
- **WHEN** 用戶點擊主頁 `乘車碼` 入口
- **THEN** 系統 SHALL 執行正式 AlipayHK／支付寶乘車碼候選鏈
- **AND** 系統 SHALL NOT 顯示 `實驗性乘車碼入口` 底部彈層
- **AND** 系統 SHALL NOT 顯示微信 SDK 候選、AlipayHK 實驗候選或診斷摘要

#### Scenario: 當前構建不包含微信實驗代碼
- **WHEN** 開發者檢查當前 App source 與 manifest
- **THEN** 系統 SHALL NOT 包含微信 OpenSDK launcher、微信 callback diagnostic 或 `.wxapi.WXEntryActivity`
- **AND** manifest SHALL NOT 聲明 `com.tencent.mm` package visibility
- **AND** Gradle dependencies SHALL NOT 引入 `wechat-sdk-android`

#### Scenario: 歷史實驗記錄不代表當前能力
- **WHEN** 開發者查看歷史實驗文檔或 OpenSpec archive
- **THEN** 系統 SHALL 將微信 SDK 實驗資料視為歷史記錄
- **AND** 系統 SHALL NOT 依據歷史實驗記錄恢復當前生產入口、manifest 或依賴配置
