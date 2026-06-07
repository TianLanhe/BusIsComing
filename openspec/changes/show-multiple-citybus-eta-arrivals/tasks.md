## 1. ETA 資料模型與格式化

- [x] 1.1 新增或調整 ETA arrival 資料模型，保存班次序號、候車分鐘數、ETA 時間戳、`HH:mm` 到達時刻、目的地、備註和資料時間。
- [x] 1.2 將 `WaitTimeState.Available` 擴展為可承載多筆 arrivals，並保留第 1 班候車分鐘數作為主展示和排序比較值。
- [x] 1.3 更新既有 `toDisplayText`、排序器和 formatter，使單班、多班、查詢中與暫無車輛狀態保持兼容。
- [x] 1.4 新增 `即將到站`、`等候 X 分鐘`、`下一班 X 分鐘`、`第N班`、`更新 HH:mm` 等展示格式 helper。

## 2. Citybus ETA 解析與補全

- [x] 2.1 更新 `CitybusFirstLegEtaService`，從同一次 ETA response 中保留最多 3 筆匹配班次。
- [x] 2.2 保持現有 P2P stop map stopId 推導，不恢復運行時 `route-stop` fallback。
- [x] 2.3 保持 strict match 優先、route + stop + dir fallback 的既有匹配語義。
- [x] 2.4 按 `eta_seq` 升序排列班次；`eta_seq` 缺失或不可解析時使用 ETA 時間升序兜底。
- [x] 2.5 解析並保留 `generated_timestamp`、`data_timestamp`、`dest_tc` 和非空 `rmk_tc`。
- [x] 2.6 確認 ETA 請求數量不增加，仍由現有同首程 request key 去重和並發限制控制。
- [x] 2.7 更新 Repository ETA 補全回調，使每條 routeId 可接收多班 `WaitTimeState`。
- [x] 2.8 保持舊查詢 generation 的 ETA 更新取消或忽略規則。

## 3. 路線卡片候車摘要

- [x] 3.1 調整 `item_bus_route.xml` 候車區，使其支持主候車狀態和可選的下一班摘要兩行。
- [x] 3.2 在卡片有第 2 班 ETA 時展示 `下一班 X 分鐘 ›`；只有 1 班、查詢中或暫無車輛時隱藏該行。
- [x] 3.3 將第 1 班 `0 分鐘` 顯示為 `即將到站`，第 2 班 `0 分鐘` 顯示為 `下一班 即將到站 ›`。
- [x] 3.4 將候車區拆成獨立點擊目標；2 班及以上時點擊打開 ETA 班次面板，卡片其他區域仍打開路線詳情。
- [x] 3.5 補充可訪問性描述，讓候車區可點擊時清楚表達可查看首程候車班次。
- [x] 3.6 檢查窄屏與字體放大下卡片文字不重疊，下一班摘要不得擠壓主候車狀態。

## 4. ETA 班次底部面板

- [x] 4.1 新增輕量 `BottomSheetDialog` 或等效元件展示首程 ETA 班次。
- [x] 4.2 面板標題展示 `首程 <路線> 候車時間`，副標題優先展示 `<上車站> 往 <dest_tc>`。
- [x] 4.3 `dest_tc` 缺失時使用卡片站點預覽中的下車站名；缺少站點預覽時仍允許面板展示班次列表。
- [x] 4.4 面板最多展示第 1-3 班，每班包含 `第N班`、候車分鐘數和具體到達時刻。
- [x] 4.5 非空 `rmk_tc` 僅在面板對應班次下方以次級文字展示，卡片不展示備註。
- [x] 4.6 面板展示 `更新 HH:mm`，來源優先使用 `generated_timestamp`，其次 `data_timestamp`。
- [x] 4.7 不新增自動輪詢或定時刷新；若面板打開期間有效後台 ETA 更新到達，面板同步刷新。

## 5. 排序與狀態一致性

- [x] 5.1 更新候車時間排序，使其只使用第 1 班候車分鐘數。
- [x] 5.2 確認第 2 班和第 3 班變化不會單獨改變排序。
- [x] 5.3 保持默認查詢結果仍按總耗時升序，不因 ETA 補全改變順序。
- [x] 5.4 保持查詢中和暫無車輛排序位置在可用候車時間之後。

## 6. 測試與驗證

- [x] 6.1 補充 ETA service 單元測試：解析 1/2/3 班、`eta_seq` 排序、ETA 時間兜底排序、備註和更新時間。
- [x] 6.2 補充 fallback 單元測試：strict 缺失時取 route + stop + dir 的多班資料，且不使用 route/stop/dir 不一致記錄。
- [x] 6.3 更新排序器和 formatter 單元測試，覆蓋 `即將到站`、`下一班`、暫無車輛和只按第 1 班排序。
- [ ] 6.4 補充卡片 adapter 或 layout 測試，確認候車區可點擊條件、卡片其他區域點擊路線詳情不受影響。
- [ ] 6.5 補充 ETA 班次面板測試或可驗證 helper 測試，覆蓋標題、副標題、班次、備註和更新時間。
- [x] 6.6 運行 `./gradlew testDebugUnitTest`。
- [x] 6.7 運行 `./gradlew build`。
- [ ] 6.8 使用模擬器或真機驗證主頁查詢結果：卡片下一班摘要、候車區點擊面板、卡片其他區域打開路線詳情、無車狀態正常。
- [x] 6.9 運行 `openspec validate show-multiple-citybus-eta-arrivals --strict`。
