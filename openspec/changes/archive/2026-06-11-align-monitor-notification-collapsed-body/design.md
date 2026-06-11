## Context

`polish-route-card-monitor-notification-ui` 已將監控通知調整為 `<首程巴士號> · <監控狀態>` 標題，並把正文分成折疊態短摘要與展開態完整摘要。目前成功刷新時的折疊態 `contentText` 由 `successText(...)` 產生，只包含 `剩餘/已過`、首班車與步行或下一班；展開態 `BigTextStyle.bigText(...)` 由 `bigText(...)` 產生，額外包含步行、下一班與更新時間。

這種分工讓折疊通知更短，但也導致用戶看到的 body 內容在折疊與展開間不一致。新的產品要求是：App 提供給 Android 通知模板的折疊態 body 必須與展開態 body 對齊，內容同源，系統版面自然截斷不屬於 App 主動縮短文案。

## Goals / Non-Goals

**Goals:**

- 成功刷新時，`contentText` 與 `BigTextStyle.bigText` 使用完全相同的完整監控 body。
- 完整 body 一次性包含 `剩餘/已過`、首班車、步行、可用時的下一班、最後更新時間。
- 初始載入、session 恢復、刷新失敗和 public notification 的折疊與展開 body 也保持一致。
- `lastSuccessfulNotificationText` 保存完整 body，讓刷新失敗 fallback 仍能回放與展開態一致的內容。
- 保留既有標題、action、通知 channel、語音和刷新策略。

**Non-Goals:**

- 不引入 custom `RemoteViews` 或自定義通知版面。
- 不承諾 Android 折疊通知在所有系統版本、螢幕寬度和字體設定下完整顯示整串 body。
- 不重新調整通知標題、通知 action、鎖屏可見性或提醒優先級。
- 不改變監控狀態判斷、ETA 查詢、停止條件或語音播報內容。

## Decisions

### 1. 使用單一完整 body formatter 作為通知正文來源

實作應新增或重命名一個語義清楚的 formatter，例如 `bodyText(...)` 或 `fullBodyText(...)`，輸出完整監控正文：

```text
剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新
```

若沒有下一班 ETA，formatter 使用 `listOfNotNull` 或等效方式移除下一班片段，而不是輸出空白或多餘分隔符。

`successText(...)` 若仍保留，應委派給完整 body formatter，避免折疊態和展開態有兩套拼接規則。這比在 service 裡手動把兩個 formatter 的輸出拼到一致更穩，因為測試可以直接覆蓋 formatter 契約。

### 2. 通知構建層只接收一份 body

`BusMonitorService` 在成功刷新後應生成一次 `notificationBody`，並把同一值傳給：

- `.setContentText(notificationBody)`
- `NotificationCompat.BigTextStyle().bigText(notificationBody)`
- `buildPublicNotification(..., text = notificationBody, bigText = notificationBody)`

`buildNotification(...)` 可以保留 `text` 和 `bigText` 參數以降低改動範圍，但呼叫點必須傳入同一 body。若實作選擇收斂函式簽名，應保持 existing tests 可清楚驗證折疊與展開一致。

### 3. 異常與過渡狀態也沿用同一 body 契約

初始載入或 session 恢復時，若只能展示 `正在取得候車時間...`，則折疊與展開都使用該字串。

刷新失敗時，fallback body 仍由 `failureText(...)` 產生一次，並同時作為 content text 和 big text。當存在 `lastSuccessfulNotificationText` 時，它應是完整 body，例如：

```text
資料延遲 · 剩餘 2 分鐘 · 車 5 分鐘到 · 步行 3 分鐘 · 下一班 25 分鐘 · 12:45 更新 · 更新失敗 1 次
```

### 4. 驗證重點放在字串契約，不依賴系統通知截圖判斷

Android 標準通知折疊態可能在視覺上截斷長字串，因此本變更的自動測試應驗證 App 傳給通知 builder 的字串契約：

- formatter 成功 body 包含完整片段與更新時間；
- 有下一班和無下一班兩條路徑都不產生多餘分隔符；
- service 構建通知時折疊 body 與展開 body 同源；
- public notification 使用同一 body；
- `lastSuccessfulNotificationText` 保存完整 body，刷新失敗時折疊與展開一致。

模擬器或真機驗證可輔助確認通知仍能展示、展開後內容完整、action 不變，但不應把不同 Android 版本的折疊截斷方式作為功能通過條件。

## Risks / Trade-offs

- [Risk] 折疊態 body 變長後，部分 Android 通知 UI 只顯示前半段。→ Mitigation：標題已承載核心狀態，展開態可看完整內容；本變更只保證 App 不再主動提供短版 body。
- [Risk] `lastSuccessfulNotificationText` 改為完整 body 後，失敗 fallback 更長。→ Mitigation：失敗文案保留 `資料延遲` 前綴，長內容由標準通知模板自然處理。
- [Risk] 本 change 與 `polish-route-card-monitor-notification-ui` 都涉及 notification formatter。→ Mitigation：實作時以當前工作區最新 formatter 為準，保留其標題與 action 決策，只收斂 body 來源。
- [Risk] 若測試只做源碼字串檢查，可能漏掉實際呼叫點分叉。→ Mitigation：優先新增 formatter 單元測試，並用契約測試覆蓋 service 呼叫同一 body 的約束。
