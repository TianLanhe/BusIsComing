## Why

常用行程與臨時查詢的結果目前只在首次查詢或使用者手動下拉時更新，路線詳情也只有首程 ETA 具備既有週期刷新，使用者停留頁面時可能持續看到過期的路線時間、票價及候車資訊。需要一套前台可見、可設定且不打擾操作的自動刷新契約，讓動態資訊保持新鮮而不引入背景耗電或重置頁面狀態。

## What Changes

- 新增全 App 共用的「自動刷新」偏好，預設每 1 分鐘，提供關閉、1、2、5、10 分鐘五個即時生效選項；設定頁以與外觀主題、語言一致的標準設定行顯示目前值，點擊後在 Material 單選對話框選擇。
- 常用行程與臨時查詢首次成功顯示結果後啟動前台定時刷新；沿用原查詢、排序、置頂及座標快照，不更新行程使用次數，也不因臨時查詢重新取得目前位置。
- 路線詳情頁每個週期並發刷新 Citybus 動態詳情與首程 ETA；完整解析及驗證 Citybus 詳情回應後只歸併動態時間／票價，保持可靠結構、幾何、地圖相機、底部面板及選中／展開狀態。
- 結果自動刷新與 CSDI 步行漸進資料共用同一 query generation、結果 projection 及視口錨點；基礎路線回應仍可結束刷新 cycle，後續有效 CSDI callback 不延長 cycle，失敗 key 按 walking runtime 退避而不每分鐘重請。
- 自動刷新只在目前 destination 可見且 App 位於前台時運行；離開、鎖屏、編輯查詢或關閉設定時暫停／取消，返回後按是否到期立即刷新或等待剩餘時間，全程不使用 `Service`、`AlarmManager` 或 `WorkManager`。
- 自動刷新與手動／首次查詢互斥，不固定追趕牆鐘週期；每次嘗試完成後才安排下一次，失敗後等待完整間隔，並以 generation 忽略過期 callback，避免頁面切換造成並發或快速重試。
- 第一次成功顯示常用／臨時查詢結果時立即展示一次性的 5 秒自動刷新說明橫幅；橫幅嚴格採用已確認的無圖示、無關閉鍵、兩行短文案、右側「設定」、綠色語義表面與倒數線樣式，位於查詢上下文與結果摘要之間且不遮擋內容。
- 日常自動刷新只在結果摘要或詳情對應區域顯示輕量「正在更新」狀態；成功靜默更新內容與最後成功時間，自動失敗不顯示警告並保留最近成功內容／時間，手動刷新既有成功與失敗回饋保持不變。

## Capabilities

### New Capabilities

- `route-auto-refresh`: 定義全域偏好、前台排程狀態機、常用／臨時查詢與詳情刷新語義、首次提示橫幅、資料歸併、失敗恢復、生命週期及互動狀態保持。

### Modified Capabilities

- `app-settings-support`: 在偏好分組新增「自動刷新」標準設定行、目前值及 Material 單選對話框，定義順序、選項、即時保存、首次提示完成、deep focus、響應式與無障礙契約。
- `route-query-results-layout`: 在既有查詢結果摘要、更新時間與手動下拉刷新契約上新增自動刷新狀態、首次橫幅位置及自動更新時的列表視口保持行為。

## Impact

- **前置基線**：詳情自動刷新直接沿用 active `fix-route-detail-progressive-loading` 已實作的 reducer、domain generation、可靠結構快取及互動狀態保持；本次迭代不要求先同步或歸檔該 change。由於能力仍未進入主規格，詳情定時刷新契約由本 change 的 `route-auto-refresh` 新能力承載，不建立虛假的 MODIFIED delta。
- **Android 代碼**：影響設定偏好、`MainActivity` 常用結果、`SearchFragment` 臨時結果、`RouteDetailActivity`、查詢／詳情 coordinator、結果摘要及 AppBar XML；新增可注入 clock／scheduler 的純前台排程 controller 與持久化 notice 狀態。
- **資料與外部接口**：結果刷新重跑原 Citybus 點到點查詢；詳情每週期請求完整 Citybus 詳情回應並並發請求 DATA.GOV.HK ETA，但不重新請求幾何。所有外部結果須按頁面、query／domain generation 及 stable key 驗證。
- **生命週期與相容性**：偏好與一次性提示狀態使用可清除的本機持久化資料，預設值對新裝及升級皆為 1 分鐘；不修改 SQLite、已保存行程或 `.bicroutes`，不新增背景執行、通知或權限。
- **UI 與驗證**：新增三語、明暗主題、360dp、字體比例 1.0／1.3／2.0、TalkBack、標準設定行、單選對話框、deep focus、停用動畫及頁面可操作性驗收；需以 fake clock 覆蓋間隔邊界、前後台／頁面切換、停用與改間隔、並發／失敗／過期 callback，並用任務自有裝置完成真實 Citybus／ETA 至少兩個週期的前台驗證。
