## Context

基線為 `c8143fe`。`MainActivity.sortBy` 與 `SearchFragment.sortBy` 直接提交新列表；`BusRouteAdapter` 使用以 stable id 判斷身份的 DiffUtil。部分漸進更新、置頂及自動刷新另以 `RouteListViewportAnchor` 查找原卡片的新位置，因此「同一卡片仍露在頂部」會導致列表序號跟著改變。

使用者已確認：手動及背景重排一律保持第一可見項序號與露出偏移，不跟隨原卡片，也不要求累積像素或滑塊百分比固定。生效 `bus-route-results-sorting` 的漸進步行規則及 `route-query-results-layout` 的自動刷新規則目前明確要求 stable-id 錨點，本 change 必須完整取代這兩處契約；`route-auto-refresh` 的查詢、時間與排程契約保持不變。

版面與動效沿用 `docs/ui-style-guide.md`；資料交付與刷新上下文參考 `docs/journey-query-workflow.md`。本 change 不改 runtime 文案。

## Goals / Non-Goals

**Goals:**
- 常用與搜尋結果在同一查詢下所有列表更新使用一致的序號／偏移視窗策略。
- 保留頂欄狀態、使用者新手勢、資料身份及已開啟的 ETA／詳情互動。
- 正確處理連續 diff、列表長度／項目高度變化、空結果及頁面銷毀。

**Non-Goals:**
- 不修改排序比較器、排序方向預設值、置頂身份／token 或資料來源。
- 不把同一視窗策略套到候選地點列表、詳情時間線、新查詢或主動置頂展示。
- 不改變手動／自動刷新動畫、排程、通知、權限或資料格式。

## Decisions

### 1. 以列表序號及內容座標表達視窗

快照保存第一可見 adapter 項目序號，以及該項目在 RecyclerView 內容頂部的 decorated offset。常用頁的置頂分隔行也是實際列表項，因此納入序號；快照不保存作為捲動依據的 route id。例：第 8 項上緣位於內容頂部上方 20px，提交後仍讓新第 8 項維持相同相對位置。

偏移以扣除 padding、insets 及當前手動刷新浮層佔位後的一致內容座標量度及恢復；不能直接把舊 `view.top` 當作任何 padding 狀態下通用的 offset。只改列表位置，不展開／收起 AppBar，不重新聚焦排序按鈕。

長度不足時把序號截在最後有效項；新卡片高度短於原遮擋量時，把遮擋量限制至該項仍至少露出一個像素；再由 layout manager 對列表底部可捲範圍作最小修正。空列表清除快照且不發出捲動指令，之後同一查詢恢復非空結果時從有效起始位置顯示，不復活過期位置。

否決 stable-id 錨點，因其正是要改變的行為；否決累積像素／百分比，因變高卡片與分隔行會使其不等於使用者確認的「第幾項及露出位置」。

### 2. 共用提交協調，保留資料身份

在既有 `RouteListViewportController` 附近建立窄範圍的視窗快照／恢復 policy 與提交協調，讓兩個頁面的排序、漸進更新及同查詢刷新入口使用同一行為。UI owner 仍負責投影資料及生命週期；repository 和 sorter 不處理 RecyclerView。

沿用 stable id／DiffUtil 以維持卡片更新、置頂與互動身份，不改用全量 `notifyDataSetChanged`。在新列表首次 layout 前安排序號 offset，避免先跟隨原卡片跳動、再於下一幀補捲回去；用真實 RecyclerView 驗證這個時序。既有卡片差異動畫可保留，但不得驅動整個視窗或在動畫結束後再次恢復舊位置。

新增明確的「保持序號位置」策略，與既有「展示置頂頂部」及置頂操作本身的身份錨點策略分開。只替換本 change 覆蓋的更新入口，不全域改寫 `positionOf` 等仍供置頂操作使用的 helper。

### 3. 以最新使用者位置和提交世代為準

視窗快照在即將提交可接受的新資料時擷取，而非在 HTTP 刷新開始時擷取。請求期間的使用者捲動因此自然成為提交基準。

非同步 diff 存在時，由單一協調器追蹤 query/view generation、提交序號及使用者捲動世代。只有最新仍有效的提交能恢復；commit callback 被新提交取代時不得遺失共用狀態。計算 diff 期間若使用者繼續滑動，提交時採用最後有效 layout 對應的最新視窗，或使舊恢復失效；不得拿請求起點快照強行停止手勢。內部恢復不應被誤認作新手勢。

頁面離開、View 銷毀、切換查詢、主動置頂或明確導航會取消本輪 pending restore。舊 `post`、動畫完成通知及 callback 都需再次核對 owner／generation。

### 4. 已開啟內容仍按路線身份管理

位置錨點改為序號，不代表點擊的路線改成「目前第幾張」。ETA sheet、詳情參數、監控啟動及選擇繼續綁定原路線身份；排序後不得把已開啟互動悄悄切到新佔該序號的路線。原路線消失時沿用該互動原有失效規則。

### 5. 驗證策略

- 純 policy：正負偏移、最後項、分隔行、高度縮小、空列表及 owner／提交／手勢世代。
- Instrumentation：兩頁中段部分露出，逐一切換五個欄位及升降序；在 ETA、walking、預覽、手動／自動刷新後比對實際第一可見序號、decorated offset 和 AppBar offset，而非只驗證 helper 回傳值。
- 競態：連續切換排序、diff 被取代、刷新期間滑動、diff 期間滑動、新查詢、Tab 切換與 View 重建；驗證最後可見幾何且無延後拉回。
- 回歸：置頂／取消置頂、ETA sheet／詳情身份、手動刷新浮層、font scale 2.0、長卡片、三語、深淺色、系統停用動畫與 TalkBack。
- 裝置畫像：API 36、360dp 級手機直向、Google Play 系統映像，以相同 query fixture 隔離外部變動；字體 1.0／2.0，動畫開／關。只使用本任務持有且符合畫像的 AVD，驗證後關閉。

## Risks / Trade-offs

- [不同高度使某些舊 offset 無法成立] → 接受最小有效範圍修正；驗收不要求 scrollbar 百分比或累積像素一致。
- [AsyncListDiffer 捨棄較舊 commit callback] → 協調器保留最新世代，驗證取代和手勢競態，避免每個入口各自 `post`。
- [新增策略誤傷置頂或卡片身份] → 分開提交意圖並保留原身份模型，以操作與視窗雙重斷言回歸。

## Migration Plan

無儲存或 API 遷移。按兩頁依次接入共用策略並驗證所有更新入口；完成 `./gradlew build` 與裝置測試後才標記實作完成。回退只需還原本 change 的提交協調及 delta，不變更任何使用者資料。後續歸檔依倉庫規則同步兩個受影響主 spec 及文件。
