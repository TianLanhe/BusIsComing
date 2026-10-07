## 1. 視窗模型與提交協調

- [ ] 1.1 盤點 `MainActivity`／`SearchFragment` 的排序、ETA、walking、預覽、手動／自動刷新與置頂提交入口，記錄各自提交意圖；保留新查詢與置頂導航語義。
- [ ] 1.2 為序號、decorated offset、分隔行、結果縮短、短卡片及空列表建立純 policy 回歸測試，對應 `bus-route-results-sorting` 的位置與邊界 requirements。
- [ ] 1.3 在 `RouteListViewportController` 附近實作可共用的快照及邊界解析，統一 padding／insets／刷新浮層的座標系，不把路線身份用作此次恢復依據。
- [ ] 1.4 實作查詢／View／提交／使用者捲動世代的協調與取消，覆蓋 diff callback 被取代、晚到 post 及動畫完成後不得拉回的情況。

## 2. 接入兩個結果頁

- [ ] 2.1 常用頁接入排序及所有同查詢內容／刷新更新；保留 `BusRouteAdapter` 的 DiffUtil 身份、置頂 token 與主動置頂展示。
- [ ] 2.2 搜尋頁接入相同策略，移除相應背景重排對原 stable id 的視窗追蹤；維持表單折疊、頂欄及刷新 eligibility。
- [ ] 2.3 統一手動／自動刷新在接受提交時擷取最新位置；保留原浮層、成功勾號、靜默摘要、錯誤、空結果及恢復行為。
- [ ] 2.4 驗證 ETA sheet、詳情及監控啟動仍綁定原路線身份，返回、配置重建與新查詢不使用過期視窗快照。

## 3. 行為與裝置驗證

- [ ] 3.1 加入真實 RecyclerView instrumentation：兩頁中段部分露出時逐一切換五個排序欄位及升降序，斷言第一可見序號、偏移及 AppBar offset；不能只有 helper 單元測試。
- [ ] 3.2 驗證 ETA／walking／預覽、手動及自動刷新重排、數量減少、空結果恢復、不同卡片高度及置頂分隔行，對應兩份 delta 的全部更新及邊界 scenarios。
- [ ] 3.3 驗證請求／diff 期間滑動、連續排序與提交取代、Tab 切換、View 銷毀／重建、置頂及已開啟互動；以最終 layout 和動畫結束後斷言防止延後跳動。
- [ ] 3.4 使用符合 design 畫像且本任務持有的 AVD 驗證三語、明暗、font scale 1.0／2.0、長卡片、TalkBack 及系統動畫開／關；記錄畫像、結果並關閉本任務啟動的全部 AVD。
- [ ] 3.5 執行定向測試及 `./gradlew build`；核對實作、兩份 delta、任務勾選及差異範圍，按 AGENTS.md 提交實作。未完成裝置項不得以 build 成功代替。
