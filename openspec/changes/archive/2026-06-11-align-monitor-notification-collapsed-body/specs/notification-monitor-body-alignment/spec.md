## ADDED Requirements

### Requirement: 通知欄監控成功 body 在折疊態與展開態一致
系統 SHALL 使用同一份完整監控 body 作為通知欄監控成功刷新時的折疊態 content text 與展開態 BigTextStyle big text。

#### Scenario: 有下一班時使用完整 body
- **WHEN** 監控狀態刷新成功
- **AND** 距離最遲出門門檻還有 2 分鐘
- **AND** 第一班車 5 分鐘到
- **AND** 步行到站時間為 3 分鐘
- **AND** 下一班車 25 分鐘到
- **AND** 最後更新時間為 `12:45`
- **THEN** 監控通知的折疊態 content text SHALL 等於 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`
- **AND** 監控通知的展開態 big text SHALL 等於 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`

#### Scenario: 無下一班時不產生短版摘要
- **WHEN** 監控狀態刷新成功
- **AND** 最遲出門門檻已過 1 分鐘
- **AND** 第一班車 2 分鐘到
- **AND** 步行到站時間為 3 分鐘
- **AND** 系統沒有可用的下一班 ETA
- **AND** 最後更新時間為 `12:45`
- **THEN** 監控通知的折疊態 content text SHALL 等於 `已過 1 分鐘 · 車 2 分鐘到 · 步行 3 分鐘 · 12:45 更新`
- **AND** 監控通知的展開態 big text SHALL 等於 `已過 1 分鐘 · 車 2 分鐘到 · 步行 3 分鐘 · 12:45 更新`
- **AND** 系統 SHALL NOT 生成另一套只包含前三段信息的折疊態摘要

### Requirement: 通知欄監控過渡與失敗 body 在折疊態與展開態一致
系統 SHALL 在初始載入、session 恢復、刷新失敗和資料延遲狀態下，使用同一 body 字串作為折疊態 content text 與展開態 BigTextStyle big text。

#### Scenario: 初始載入 body 一致
- **WHEN** 用戶開始通知欄監控
- **AND** 系統尚未取得第一筆 ETA
- **THEN** 監控通知的折疊態 content text SHALL 等於 `正在取得候車時間...`
- **AND** 監控通知的展開態 big text SHALL 等於 `正在取得候車時間...`

#### Scenario: 刷新失敗時回放完整 body
- **WHEN** 上一次成功通知 body 為 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`
- **AND** 下一次 ETA 刷新失敗且連續失敗次數為 1
- **THEN** 監控通知的折疊態 content text SHALL 等於 `資料延遲 · 剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新 · 更新失敗 1 次`
- **AND** 監控通知的展開態 big text SHALL 等於 `資料延遲 · 剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新 · 更新失敗 1 次`

#### Scenario: 無成功資料的刷新失敗 body 一致
- **WHEN** 監控 session 尚未有任何成功 ETA
- **AND** ETA 刷新失敗
- **THEN** 監控通知的折疊態 content text SHALL 等於 `資料延遲 · 暫無 ETA，1 分鐘後重試`
- **AND** 監控通知的展開態 big text SHALL 等於 `資料延遲 · 暫無 ETA，1 分鐘後重試`

### Requirement: Public notification body 與主通知 body 一致
系統 SHALL 讓鎖屏 public notification 使用與主通知相同的 App 可控 body 文案，不為 public notification 生成另一套短版正文。

#### Scenario: Public notification 使用成功完整 body
- **WHEN** 監控通知刷新成功
- **AND** 主通知 body 為 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`
- **THEN** public notification 的 content text SHALL 等於 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`
- **AND** public notification 的 big text SHALL 等於 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`

### Requirement: 通知欄監控保持標準通知模板
系統 SHALL 保持使用 Android 標準 NotificationCompat 模板和 BigTextStyle 展示監控通知，不為了強制折疊態完整視覺展示而引入 custom notification layout。

#### Scenario: 折疊視覺截斷由系統處理
- **WHEN** 完整 body 超過 Android 折疊通知的可視寬度
- **THEN** 系統 MAY 依 Android 通知 UI 自然截斷視覺顯示
- **AND** App 提供給 content text 的字串 SHALL 仍與 BigTextStyle big text 相同
