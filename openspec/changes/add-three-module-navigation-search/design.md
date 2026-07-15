## Context

目前 `MainActivity` 直接持有常用路線、臨時查詢、結果列表、排序、刷新、ETA、路線詳情、通知監控、乘車碼、定位與設定入口。`TemporaryRouteBottomSheet` 以程式化 bottom sheet 承載一次性查詢，並透過既有 `PlaceInputController`、`RouteConfigRepository` 和 `TemporaryRouteSaveDialog` 完成候選地點、目前位置與保存流程；`SettingsActivity` 則是帶返回按鈕的獨立 Activity。

這個 change 實作已確認設計的第一階段：重組為「常用」「搜尋」「設定」三個頂層 destination，遷移一次性查詢並保留既有 Citybus 查詢能力。Google 地圖、路線幾何與詳情地圖屬於下一個獨立 change，不在本次範圍。

## Goals / Non-Goals

**Goals:**

- 以固定底部導航建立清楚的常用、搜尋、設定責任邊界。
- 讓常用路線快速查詢流程、資料庫資料、ETA、詳情、排序、刷新與監控行為保持可用。
- 將臨時查詢移至獨立搜尋頁，重用 Citybus 候選地點、目前位置、校驗、保存與結果展示能力。
- 在 destination 切換、旋轉及非同步回應延遲時保護使用者已選路線與查詢狀態。
- 讓次級 Activity 維持現有返回語義，避免把路線管理與編輯一起重構。

**Non-Goals:**

- 不接入 Google Maps SDK、Google Places、Google Routes 或任意地圖功能。
- 不改動 Citybus、DATA.GOV.HK、ETA、站點對齊、排序規則、SQLite schema 或保存格式。
- 不改變通知監控、乘車碼、支援、關於和隱私政策的業務行為。
- 不引入 Navigation Component、Compose、單獨 Gradle feature module 或全域架構重寫。

## Decisions

### 1. `MainActivity` 成為固定 destination 宿主

`MainActivity` 改為承載 `BottomNavigationView` 與一個 Fragment 容器，建立並保留三個頂層 Fragment：常用路線、搜尋與設定。切換採用 show/hide 或等效非破壞性切換，避免重新建立已存在的列表、輸入與選中狀態；選中 tab 以 `onSaveInstanceState` 恢復。

選擇 Fragment 而不是三個頂層 Activity，因為底部導航、狀態保留與返回規則需要在同一宿主內一致處理。單純在既有 `MainActivity` 內切換多組 `View` 會把已經集中的查詢、設定與導航責任繼續堆在同一個 Activity；Navigation Component 對只有三個固定 destination 的首版增加了不必要的導覽圖與 back stack 複雜度。

### 2. 頂層畫面按既有責任拆分，次級頁不搬遷

- 常用路線 Fragment 承接現有常用路線選擇、快捷卡、完整列表、管理入口、乘車碼及常用路線結果。
- 搜尋 Fragment 承接原臨時起終點選擇、目前位置、交換、查詢、結果、排序、刷新、ETA、詳情、監控和存為常用。
- 設定 Fragment 承接 `SettingsActivity` 的頂層內容，移除頂層返回按鈕。
- `RouteManageActivity`、`RouteEditActivity`、`AboutActivity` 保持為次級 Activity；從頂層 Fragment 進入後維持既有返回行為與資料刷新。

直接保留 `SettingsActivity` 並從底部 tab 啟動會造成底部導航消失及返回語義錯置，因此不採用。為了避免重複維護設定行為，設定行的點擊處理改為可由 Fragment 使用的既有支援 action，而非複製一套邏輯。

### 3. 將查詢協調從畫面生命週期中抽離，但不改 repository 契約

從 `MainActivity` 提取可被常用與搜尋流程共用的查詢協調元件。它持有 `BusRouteRepository`、`RouteDetailRepository`、單一查詢 executor、主線程回呼與 generation，對 UI 發出基礎結果、ETA／站點預覽增量更新、失敗與刷新結果。各 Fragment 保有自己的 `RouteQueryState`，包括起終點或常用路線、結果、排序、更新時間、查詢中與刷新狀態；Fragment 只訂閱及渲染自己的 state。

每次新查詢、手動切換路線、離開 Fragment 或銷毀宿主都必須使舊 generation 失效。遲到回呼只可更新仍有效、仍屬於原 owner 的 state。這保留既有「先展示基礎結果、再補齊 ETA／站點」契約，且不讓 HTTP、HTML／JSON 解析或 executor 編排進入 Fragment、Adapter 或設定頁。

不複製兩套查詢流程；也不改變 `CitybusBusRouteRepository`、`CitybusRouteDetailRepository` 的 HTTP 端點、參數、解析或快取行為。

### 4. 搜尋頁重用地點輸入與保存能力

搜尋 Fragment 使用 XML 表單承載起點、終點、交換按鈕、目前位置入口、候選列表和查詢按鈕。它重用 `PlaceInputController`、Citybus 地點搜尋、目前位置協調、反向地理編碼、`RouteConfigValidator` 和 `TemporaryRouteSaveDialog`；`TemporaryRouteBottomSheet` 在所有呼叫點遷移完成後移除。

搜尋結果以起點至終點摘要、編輯與存為常用操作取代舊的「臨時」上下文條。保存成功後刷新常用路線資料但停留在搜尋頁，避免中斷結果比較。候選地點、目前位置、相同起終點、名稱重複與保存校驗維持既有語義。

選擇獨立表單而非常駐在常用頁，因為一次性輸入與日常快捷選擇是不同任務；不加入近期搜尋、收藏地點或地圖選點，避免擴張本次資料模型與 UI 範圍。

### 5. 設定是頂層頁，不是 modal 或返回頁

設定 Fragment 顯示既有版本、偏好、支援與關於項目，但不顯示「返回上一頁」或 ActionBar home。它仍以既有 action 執行分享、回饋、隱私政策、Toast 和 About Activity；進入 About 後返回設定 tab。這讓設定在底部導航中是可重複抵達的頂層目的地，而非從常用頁 push 出去的次級頁。

### 6. 視覺與狀態基線保持現有通勤工具風格

頂層頁使用既有 XML、AppCompat、Material Components、淺色根背景和 8dp 以下卡片圓角。底部導航以圖示與「常用」「搜尋」「設定」文字共同表達選中狀態；圖示按鈕具繁體中文內容描述與至少 48dp 觸控區。

常用結果繼續使用既有路線卡與刷新回饋。搜尋頁在初始、查詢中、無結果、失敗、成功與刷新時提供與常用流程一致的可見狀態；搜尋頁不顯示空白地圖或地圖占位內容。小螢幕、字體縮放、WindowInsets 與巢狀捲動需保持文字不重疊和列表可操作。

## Risks / Trade-offs

- [現有 `MainActivity` 集中超過千行 UI 與查詢程式] → 先抽取 owner 無關的查詢協調與 state，再逐一遷移常用、搜尋、設定，避免一次重寫所有結果卡與底部彈層。
- [兩個 destination 同時收到遲到回呼而交叉覆蓋] → generation 必須綁定查詢 owner；Fragment 停止或 query 被替換後使舊 generation 失效。
- [地點候選列表與列表下拉刷新產生巢狀手勢衝突] → 候選列表展開時由輸入控制器獨占輸入區，結果刷新只在有效結果列表頂部啟用。
- [從搜尋保存常用後常用頁顯示舊資料] → 保存成功後通知常用路線 state 重新載入，但不切換目前 tab。
- [設定 Fragment 與既有 Activity 同時存在造成入口不一致] → 將 launcher 入口及主頁設定按鈕全部遷移到頂層 tab；若保留 Activity 僅可作為不對外暴露的相容包裝，不能成為一般導航入口。
- [導航重構意外改變 Citybus 回歸行為] → 保留 repository、fixture、查詢參數與 parser；對同一組起終點比較遷移前後的基礎結果、排序與 ETA 補齊行為。

## Migration Plan

1. 新增 Fragment 依賴與底部導航宿主，但保留既有資料庫、repository、service、Activity 與 XML 資源直到對應 destination 已接管。
2. 先遷移常用路線流程並驗證常用結果、ETA、監控、乘車碼、管理與定位；再遷移搜尋表單與其結果流程；最後遷移設定內容。
3. 所有頂層入口已改用 bottom navigation、搜尋保存與刷新回歸通過後，刪除主頁臨時入口、上下文條和 `TemporaryRouteBottomSheet`。
4. App 更新不涉及 SQLite schema 或保存資料變更；既有常用路線與監控 session 保持可讀。若發布後需回退，回退到前一個 App 版本即可繼續讀取相同本機資料。
5. 此 change 完成後才建立地圖展示 change；地圖 change 不得重新改寫本 change 已穩定的搜尋輸入、Citybus 查詢或底部導航契約。

## Open Questions

無。Google 地圖的 API key、渲染、站序連線、詳情彈層地圖與地圖失敗降級已明確隔離到後續 change。
