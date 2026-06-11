## Why

目前監控通知的折疊態 content text 只展示較短摘要，而展開態 BigTextStyle 會展示更完整的步行、下一班與更新時間。用戶在通知欄折疊狀態下看到的 body 與展開後 body 不一致，容易誤判通知內容是否缺漏。

本變更要求 App 提供給 Android 標準通知模板的折疊態 body 與展開態 body 使用同一份完整文案來源，讓折疊、展開和鎖屏 public notification 的內容契約保持一致。

## What Changes

- 監控通知刷新成功時，折疊態 `contentText` SHALL 使用與展開態 `BigTextStyle.bigText` 相同的完整監控 body 文案。
- 完整監控 body 保留既有排序：`剩餘/已過 N 分鐘 · 車 X 分鐘到 · 步行 M 分鐘 · 下一班 Y 分鐘 · HH:mm 更新`。
- 沒有下一班 ETA 時，完整監控 body 仍展示剩餘/已過、首班車、步行與更新時間，不插入空白佔位。
- 監控通知刷新失敗、初始載入、恢復 session 和 public notification 也 SHALL 使用相同 body 文案傳入折疊態與展開態。
- 保留既有通知標題 `<首程巴士號> · <監控狀態>`、`刷新`/`停止` action、通知本體點擊打開 App、標準 `BigTextStyle` 模板。
- 不引入 custom notification layout；Android 系統在折疊版面上仍 MAY 依螢幕寬度、系統版本或通知樣式自然截斷顯示，但 App 提供的 body 字串不得主動縮短成另一套摘要。

## Capabilities

### New Capabilities

- `notification-monitor-body-alignment`: 定義通知欄監控折疊態、展開態、public notification 和異常狀態 body 文案使用同一完整內容來源。

### Modified Capabilities

- 無。

## Impact

- 影響通知 formatter：需要讓監控通知成功文案輸出單一完整 body，或讓折疊態 formatter 委派給展開態 formatter。
- 影響通知構建：`BusMonitorService` 應把同一 body 傳給 `.setContentText(...)`、`BigTextStyle().bigText(...)` 和 public notification。
- 影響 session 持久化：`lastSuccessfulNotificationText` 應保存完整 body，供刷新失敗時折疊態與展開態一致回退。
- 影響單元/契約測試：需要覆蓋成功、有/無下一班、刷新失敗、初始載入或恢復通知的折疊 body 與展開 body 一致。
- 不改變 ETA 查詢、刷新調度、通知 channel、通知權限、語音播報或結果卡片 UI。
