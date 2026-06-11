# notification-monitor-title Specification

## Purpose
TBD - created by archiving change polish-route-card-monitor-notification-ui. Update Purpose after archive.
## Requirements
### Requirement: 通知欄監控以狀態作為折疊態標題
系統 SHALL 使用 `<首程巴士號> · <監控狀態>` 作為通知欄監控通知的 content title，讓用戶在折疊態即可直接判斷是否需要出門。

#### Scenario: 準備出門標題
- **WHEN** 被監控結果的首程巴士號為 `82X`
- **AND** 監控狀態為 `準備出門`
- **THEN** 監控通知的 content title SHALL 顯示為 `82X · 準備出門`
- **AND** content title SHALL NOT 顯示 `通知欄監控`

#### Scenario: 立即出門標題
- **WHEN** 被監控結果的首程巴士號為 `82X`
- **AND** 監控狀態為 `立即出門`
- **THEN** 監控通知的 content title SHALL 顯示為 `82X · 立即出門`
- **AND** content title SHALL NOT 顯示 `通知欄監控`

#### Scenario: 快遲到了標題
- **WHEN** 被監控結果的首程巴士號為 `82X`
- **AND** 監控狀態為 `快遲到了`
- **THEN** 監控通知的 content title SHALL 顯示為 `82X · 快遲到了`
- **AND** content title SHALL NOT 顯示 `通知欄監控`

#### Scenario: 多段路線使用首程巴士號
- **WHEN** 用戶從多段路線結果啟動通知欄監控
- **AND** 該結果路線為 `82X → 116`
- **AND** 首程巴士號為 `82X`
- **THEN** 監控通知的 content title SHALL 使用 `82X` 作為巴士號部分
- **AND** 系統 SHALL NOT 使用完整轉乘路線 `82X → 116` 作為巴士號部分

#### Scenario: 不修改系統通知外層標籤
- **WHEN** Android 系統通知 UI 額外展示 App 名稱或通知渠道名稱
- **THEN** 系統 MAY 繼續展示既有 App 名稱或渠道名稱
- **AND** 本需求 SHALL 僅約束 App 可控的 notification content title

### Requirement: 通知欄監控折疊態正文展示出門倒計時
系統 SHALL 在折疊態 content text 中優先展示相對最遲出門門檻的 `剩餘` 或 `已過` 時間，再展示第一班車到站時間和必要的步行或下一班信息。

#### Scenario: 準備出門折疊正文
- **WHEN** 監控狀態為 `準備出門`
- **AND** 距離最遲出門門檻還有 4 分鐘
- **AND** 第一班車 7 分鐘到
- **AND** 步行到站時間為 3 分鐘
- **THEN** 監控通知的折疊態 content text SHALL 顯示為 `剩餘 4 分鐘 · 車 7 分鐘到 · 步行 3 分鐘`

#### Scenario: 立即出門折疊正文
- **WHEN** 監控狀態為 `立即出門`
- **AND** 距離最遲出門門檻還有 1 分鐘
- **AND** 第一班車 4 分鐘到
- **AND** 步行到站時間為 3 分鐘
- **THEN** 監控通知的折疊態 content text SHALL 顯示為 `剩餘 1 分鐘 · 車 4 分鐘到 · 步行 3 分鐘`

#### Scenario: 快遲到了折疊正文
- **WHEN** 監控狀態為 `快遲到了`
- **AND** 最遲出門門檻已過 1 分鐘
- **AND** 第一班車 2 分鐘到
- **AND** 下一班車 25 分鐘到
- **THEN** 監控通知的折疊態 content text SHALL 顯示為 `已過 1 分鐘 · 車 2 分鐘到 · 下一班 25 分鐘`
- **AND** 折疊態 SHALL 優先展示下一班信息作為備選

#### Scenario: 缺少下一班時保留步行信息
- **WHEN** 監控狀態為 `快遲到了`
- **AND** 系統沒有可用的下一班 ETA
- **THEN** 監控通知的折疊態 content text SHALL 使用步行時間作為第三個信息，例如 `已過 1 分鐘 · 車 2 分鐘到 · 步行 3 分鐘`

### Requirement: 通知欄監控展開態展示完整監控摘要
系統 SHALL 使用標準通知模板與 `BigTextStyle` 展示完整監控摘要，不引入 custom notification layout。

#### Scenario: 展開態完整摘要
- **WHEN** 監控通知可展示展開態
- **AND** 距離最遲出門門檻還有 2 分鐘
- **AND** 第一班車 5 分鐘到
- **AND** 步行到站時間為 3 分鐘
- **AND** 下一班車 25 分鐘到
- **AND** 最後更新時間為 `12:45`
- **THEN** 監控通知的 big text SHALL 顯示為 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`

#### Scenario: 展開態快遲到了摘要
- **WHEN** 監控狀態為 `快遲到了`
- **AND** 最遲出門門檻已過 1 分鐘
- **AND** 第一班車 2 分鐘到
- **AND** 步行到站時間為 3 分鐘
- **AND** 下一班車 25 分鐘到
- **AND** 最後更新時間為 `12:45`
- **THEN** 監控通知的 big text SHALL 顯示為 `已過 1 分鐘 · 車 2 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`

### Requirement: 通知欄監控只保留必要 action
系統 SHALL 在通知欄監控通知上只提供 `刷新` 和 `停止` 兩個 action，並保留通知本體點擊打開 App 的能力。

#### Scenario: 通知 action 列表
- **WHEN** 系統展示通知欄監控通知
- **THEN** 通知 action SHALL 包含 `刷新`
- **AND** 通知 action SHALL 包含 `停止`
- **AND** 通知 action SHALL NOT 包含 `打開 App`

#### Scenario: 點擊通知本體打開 App
- **WHEN** 用戶點擊通知本體
- **THEN** 系統 SHALL 打開 App 或回到 App 既有主界面
- **AND** 系統 SHALL NOT 依賴 `打開 App` action 才能進入 App

