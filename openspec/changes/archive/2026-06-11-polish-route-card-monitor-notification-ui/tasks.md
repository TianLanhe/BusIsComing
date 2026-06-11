## 1. 卡片候車區布局

- [x] 1.1 調整 `item_bus_route.xml` 右側候車資訊塊寬度，使正常字體下完整展示 `等候 NN 分鐘`。
- [x] 1.2 確認下一班摘要可完整展示 `下一班 NN 分鐘 ›`，不截斷 `分鐘` 或箭頭。
- [x] 1.3 保留通知鈴鐺不小於 48dp 的觸控區和低權重視覺樣式。
- [x] 1.4 調整左側路線與站點預覽的可用寬度或 margin，使卡片不超出屏幕且左側文字仍可單行省略。
- [x] 1.5 實作極窄屏或超大字體降級策略：優先完整展示主候車狀態，必要時隱藏或整行省略下一班摘要。

## 2. 通知折疊態與展開態文案

- [x] 2.1 新增或調整通知 formatter，輸出 `<首程巴士號> · <監控狀態>` 作為 content title。
- [x] 2.2 多段路線監控標題只使用首程巴士號，不使用完整轉乘路線作為巴士號部分。
- [x] 2.3 新增或調整折疊態 content text formatter，按 `剩餘/已過 → 車 X 分鐘到 → 步行/下一班` 排序。
- [x] 2.4 在 `準備出門` 和 `立即出門` 狀態下，折疊態正文展示 `剩餘 N 分鐘 · 車 X 分鐘到 · 步行 M 分鐘`。
- [x] 2.5 在 `快遲到了` 狀態下，折疊態正文優先展示 `已過 N 分鐘 · 車 X 分鐘到 · 下一班 Y 分鐘`；缺少下一班時 fallback 到步行時間。
- [x] 2.6 新增或調整展開態 big text formatter，展示 `剩餘/已過 N 分鐘 · 車 X 分鐘到 · 步行 M 分鐘 · 下一班 Y 分鐘 · HH:mm 更新`。
- [x] 2.7 更新 `BusMonitorService` 的前台啟動、刷新成功、刷新失敗和 public notification，統一使用 formatter 生成的 content title、content text 和 big text。
- [x] 2.8 移除通知 action 中的 `打開 App`，只保留 `刷新` 和 `停止`；保留通知本體 content intent 打開 App。
- [x] 2.9 確認 notification channel 名稱和系統外層 App 名稱不在本變更中調整。

## 3. 測試

- [x] 3.1 更新或新增卡片 layout contract 測試，覆蓋候車資訊塊寬度、鈴鐺 48dp 觸控區和文字不截斷約束。
- [x] 3.2 新增 formatter 單元測試，覆蓋 `82X · 準備出門`、`82X · 立即出門`、`82X · 快遲到了` 和多段路線只取首程巴士號。
- [x] 3.3 新增 formatter 單元測試，覆蓋折疊態 `剩餘 N 分鐘`、`已過 N 分鐘`、`快遲到了` 下一班優先和缺少下一班 fallback。
- [x] 3.4 新增 formatter 單元測試，覆蓋展開態 big text 的完整排序和 `HH:mm 更新` 文案。
- [x] 3.5 更新 `BusMonitorService` 或通知構建相關測試，確認 content title 不再包含 `通知欄監控`，且 action 只包含 `刷新` 和 `停止`。

## 4. 驗證

- [x] 4.1 運行 `./gradlew testDebugUnitTest`。
- [x] 4.2 運行 `./gradlew build`。
- [x] 4.3 使用模擬器或真機截圖驗證卡片候車主副標題兩位數分鐘完整展示。
- [x] 4.4 使用模擬器或真機驗證監控通知折疊態標題為 `<首程巴士號> · <監控狀態>`。
- [x] 4.5 使用模擬器或真機驗證折疊態正文、展開態 big text 和通知 action 符合本提案排序。
- [x] 4.6 運行 `openspec validate polish-route-card-monitor-notification-ui --strict`。
