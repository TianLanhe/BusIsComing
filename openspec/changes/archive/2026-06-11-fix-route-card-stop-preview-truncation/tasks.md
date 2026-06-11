## 1. 卡片頂部布局

- [x] 1.1 將 `item_bus_route.xml` 頂部容器從固定右邊距策略調整為 `ConstraintLayout` 或等效內容感知約束。
- [x] 1.2 保留 `busRouteNameText`、`busStopPreviewText`、`busWaitArea`、`busArrivalText`、`busNextArrivalText` 和 `busMonitorButton` 既有 id，避免破壞 adapter 綁定與點擊事件。
- [x] 1.3 讓站點預覽不再固定扣除整個右側候車資訊塊寬度；有下一班摘要時約束到下一班摘要左側或使用獨立行空間，無下一班摘要時可延伸到卡片右側。
- [x] 1.4 保持候車主標題在正常字體下完整展示 `等候 NN 分鐘`。
- [x] 1.5 保持下一班摘要在正常字體下完整展示 `下一班 NN 分鐘 ›`。
- [x] 1.6 保持通知鈴鐺不小於 `48dp` 觸控區、透明低權重樣式和原有 content description。

## 2. 站點預覽降級策略

- [x] 2.1 調整站點預覽 TextView 的寬度、行數、ellipsize 或約束策略，確保 `興華邨興翠樓 → 漁灣邨` 在常見手機寬度與正常字體下完整展示。
- [x] 2.2 當站點預覽仍超出可用空間時，優先允許最多兩行展示或增加卡片高度，而不是只截斷終點。
- [x] 2.3 極端長站名才允許省略，且省略後仍需保留起點與終點兩端語義可辨識。
- [x] 2.4 確認站點預覽隱藏時，卡片高度和候車區布局不出現多餘空白或跳動。

## 3. 測試

- [x] 3.1 更新 `RouteCardLayoutContractTest`，確認不再使用 `layout_marginEnd="180dp"` 這類固定扣除整個候車區的站點預覽約束。
- [x] 3.2 新增或更新 layout contract 測試，覆蓋 `興華邨興翠樓 → 漁灣邨` 回歸文案和站點預覽可使用更寬區域或兩行展示。
- [x] 3.3 保留候車區 contract 測試，確認 `busMonitorButton` 仍為 `48dp`，候車主標題和下一班摘要仍有完整展示約束。
- [x] 3.4 保留點擊事件 contract 測試，確認卡片點擊、候車區點擊與鈴鐺點擊互不干擾。

## 4. 驗證

- [x] 4.1 運行 `./gradlew testDebugUnitTest`。
- [x] 4.2 運行 `./gradlew build`。
- [x] 4.3 使用模擬器或真機截圖驗證真實結果卡片站點預覽完整展示，並以 contract 測試覆蓋 `興華邨興翠樓 → 漁灣邨` 回歸文案。
- [x] 4.4 使用模擬器或真機驗證右側 `等候 NN 分鐘`、`下一班 NN 分鐘 ›` 和鈴鐺未因本修復被截斷、重疊或縮小。
- [x] 4.5 使用大字體或窄屏配置驗證站點預覽可讀降級，且不與路線、候車、底部價格/耗時/步行文字重疊。
- [x] 4.6 運行 `openspec validate fix-route-card-stop-preview-truncation --strict`。
