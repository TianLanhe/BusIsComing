## 1. Formatter 與文案來源

- [x] 1.1 在 `BusMonitorNotificationFormatter` 新增或重命名單一完整 body formatter，輸出 `剩餘/已過 · 車 · 步行 · 下一班 · HH:mm 更新`。
- [x] 1.2 讓成功刷新時的折疊態 formatter 不再生成短版摘要；若保留 `successText(...)`，需委派到完整 body formatter 或移除分叉呼叫。
- [x] 1.3 覆蓋無下一班 ETA 的完整 body，確認不產生空白片段或多餘分隔符。
- [x] 1.4 確認 `lastSuccessfulNotificationText` 保存完整 body，供刷新失敗 fallback 使用。

## 2. Notification 構建與 public notification

- [x] 2.1 更新 `BusMonitorService` 成功刷新流程，只生成一次 `notificationBody`，並同時傳給 `text` 與 `bigText`。
- [x] 2.2 更新初始載入、session 恢復和刷新失敗流程，確保折疊態 content text 與展開態 big text 使用同一字串。
- [x] 2.3 更新 `buildPublicNotification(...)` 呼叫，確保 public notification 的 content text 與 big text 與主通知一致。
- [x] 2.4 保留既有 notification title、`刷新`/`停止` action、content intent、channel、visibility、priority 和 silent 設定。

## 3. 測試

- [x] 3.1 更新 `BusMonitorModelsTest` 或新增 formatter 測試，覆蓋有下一班時完整 body 等於 `剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新`。
- [x] 3.2 新增無下一班 formatter 測試，覆蓋 `已過 1 分鐘 · 車 2 分鐘到 · 步行 3 分鐘 · 12:45 更新` 且無多餘分隔符。
- [x] 3.3 更新刷新失敗測試，覆蓋 fallback 使用完整 `lastSuccessfulNotificationText` 並讓折疊態與展開態一致。
- [x] 3.4 更新 `BusMonitorNotificationContractTest` 或等效契約測試，確認 `BusMonitorService` 對成功、失敗、初始載入和 public notification 傳入同一 body。

## 4. 驗證

- [x] 4.1 運行 `./gradlew testDebugUnitTest`。
- [x] 4.2 運行 `./gradlew build`。
- [x] 4.3 運行 `openspec validate align-monitor-notification-collapsed-body --strict`。
- [x] 4.4 使用模擬器或真機啟動通知欄監控，展開通知確認完整 body 可見，折疊通知沒有 App 主動生成的短版摘要。
