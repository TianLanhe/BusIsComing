# 實作與驗證記錄

## 基線與入口盤點

基線 `13005a2`，工作樹開始時乾淨。既有 unit suite 通過。實作前新增真實兩頁 RecyclerView 測試，排序後預期序號 8，搜尋實際 14、常用實際 0，確認捕獲原缺陷。

- 常用 `displayResults`：首次展示、五種排序、ETA、預覽與手動刷新。
- 常用 `renderProjectedResultsPreservingViewport`：自動刷新、漸進快照；置頂持久化通知需保留身份語義。
- 搜尋 `applyProgressiveRouteSnapshot`、`displayAutomaticResults`、`updateRoute`、`sortBy`：同查詢更新。
- 搜尋 `displayInitialResults`：首次結果與同查詢手動刷新；移除後者強制到頂。
- 新查詢的空列表、頁面離開／銷毀及主動置頂需使 pending 恢復失效。
- 既有主 spec／docs 的 stable-id 視窗契約由本 change delta 明確取代；不在 apply 階段提前歸檔或改寫主 spec。

## 裝置

本任務起始 `adb devices` 為空。啟動 `BIC_Main_API36_1_Play_360`，序號 `emulator-5580`，API 36.1／Google Play／1080px @480dpi（360dp）／直向。驗證後已關閉。

## 實作與驗證對應

| 契約群組 | 證據 |
| --- | --- |
| 五種排序／兩方向／部分露出／AppBar | `RouteListPositionInstrumentedTest` 在兩頁逐一斷言序號 8、offset -20 與 AppBar offset |
| ETA／walking／預覽及自動刷新 | 兩頁真實 adapter 的 `progressiveAndAutomaticUpdatesKeepBothListPositions`；既有 sorter／RouteQueryState 單測保留排序與身份政策 |
| 手動刷新與請求中滑動／失敗 | 請求開始在 5，期間改到 12／-25；成功與失敗均保持最後位置，包含浮層 padding 移除 |
| 短卡／分隔行／縮短／清空 | `RouteListPositionTest` 及 `RouteViewportBoundaryInstrumentedTest`；新項短於原遮擋量時至少露出一個像素，尾端由 LayoutManager 作必要邊界修正 |
| 隱藏列表的新查詢 | 空列表未經 layout、下一查詢立即抵達的案例先失敗（位置 33），修正為首批新內容接受時重設到 0 |
| diff 取代／最新使用者捲動／導航 | replaced diff、cancelled owner、ordinary update 不取消 pin 導航的真實 RecyclerView 測試；晚到動畫後再次斷言 |
| 暫時離開與資料一致性 | owner 取消位置恢復而仍接受最新資料；新查詢／銷毀才取消 pending diff |
| 路線互動與既有置頂 | ETA／詳情／監控仍由 binder 捕捉 route、依 resultId 更新；核心搜尋→ETA→詳情→監控設定→儲存裝置回歸；既有 pin lifecycle 與 mutation／fingerprint 單測 |
| Tab／重建／晚到輸入 | AOSP 上 `SearchDestinationInstrumentedTest` 與 `TopLevelNavigationInstrumentedTest` 相關案例通過 |

一般資料提交在 accepted diff callback 擷取當下舊 layout 的序號及 decorated offset，並在下一次繪製前完成 layout；不再建立追蹤 stable id 的延後恢復。資料 diff 世代、owner／導航世代與使用者手勢世代分開。主動置頂仍沿用原 reveal／identity intent，但過期 owner、空列表和新手勢可撤銷晚到導航。

測試發現來源字串契約仍要求舊 `captureRefreshViewport` helper 存在；已以實際手動刷新裝置測試替代，沒有用修改 helper 名稱規避行為驗收。矩陣測試的自動起點查詢曾在注入結果後完成並清空未選擇起終點的測試列表；已用既有測試注入點隔離該獨立流程，生產來源不變。

## 工程檢查

- 最終 `./gradlew build`：1 分 38 秒通過；840 unit tests，0 failure／0 error／1 既有 skipped。
- `openspec validate preserve-route-list-viewport-position --strict`：通過。
- strict validator 要求 MODIFIED requirement 保留原 scenario 名稱；已保留原名稱並寫入新位置語義，沒有恢復舊 stable-id 行為。
- 新增／相關測試使用真實 RecyclerView、DiffUtil、LayoutManager 及兩頁入口，fixtures 僅存在測試。
- 最終 `RouteListPositionInstrumentedTest`＋`RouteViewportBoundaryInstrumentedTest`：12 項全部通過，沒有跳過；包含三語 × 明暗的 1.0 字體矩陣。2.0 字體矩陣另跑通過。兩輪分別使用系統動畫開／關。
- 無障礙：置頂卡片 semantics／custom actions 與詳情 accessibility node 聚焦／操作共 2 項通過。這是自動化無障礙驗收，未宣稱真人聆聽 TalkBack 聲音。
- 僅修改本次位置政策與必要的取消邊界；排序算法、route identity、DiffUtil 與監控參數未改。
- 本任務啟動的 API 25、AOSP 及 Google Play AVD 全部已關閉；最終 `adb devices` 為空。
