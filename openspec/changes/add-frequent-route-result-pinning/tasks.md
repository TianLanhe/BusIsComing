## 1. 嚴格路線身份與置頂領域模型

- [x] 1.1 在 `app/src/test` 新增版本化路線指紋測試，覆蓋有序乘車段字段、`v1|` 前綴、長度編碼、語言與動態字段不變性、身份字段差異、缺失字段及重複指紋拒絕。
- [x] 1.2 在 `data/model` 實作嚴格路線指紋 formatter，從 `P2pRoutePlan.legs` 編碼 company、公開路線號、variant、bound、direction path、上下車 sequence，且不使用 `resultId`、`rawInfo` 或展示字段。
- [x] 1.3 新增 `PinLevel`、置頂 token／快照及路線清單 item 模型，明確表達未置頂、本次、長期、不可置頂、路線卡及普通排序分隔列。
- [x] 1.4 新增 `RoutePinSessionState` 單元測試，覆蓋三態轉換、直接取消、撤銷完整恢復、升級保留 token、LIFO、無數量上限、未匹配保留及每行程隔離。
- [x] 1.5 實作 `RoutePinSessionState` 與嚴格遞增 token 產生器，使用 `max(now, maxKnown + 1)` 並保存每 fingerprint mutation generation。

## 2. 純列表投影與既有排序整合

- [x] 2.1 新增 `PinnedRouteProjector` 單元測試，覆蓋置頂 token 降序、只排序未置頂、候車更新、取消回到目前排序位置、可選分隔列、全部置頂、空結果及結果總數不重複。
- [x] 2.2 實作純 `PinnedRouteProjector`，把查詢結果投影為置頂卡、可選 `UnpinnedDividerItem` 及既有 `BusRouteSorter` 排序後的普通卡。
- [x] 2.3 調整 `RouteQueryState` 以保存及更新原始 `BusRouteOption` 結果、排序與查詢狀態，將置頂組合留給投影器，並補充 ETA／站點預覽增量更新回歸測試。
- [x] 2.4 補充常用與搜尋排序回歸測試，確認搜尋仍排序全部結果、常用只排序未置頂，且 `BusRouteSorter` 不承擔置頂邏輯。

## 3. SQLite v4 與長期置頂 repository

- [x] 3.1 在 instrumentation 測試先建立 v3 fixture，驗證升級後既有行程、使用次數及最近使用時間保持不變，並驗證新安裝 v4 schema。
- [x] 3.2 將 `RouteConfigDbHelper` 升至 database version 4，在 `onConfigure` 啟用 foreign keys，建立 `route_result_pins` 複合主鍵、`pinned_at` 及 `(route_config_id, pinned_at DESC)` 索引，且 v3→v4 只新增結構。
- [x] 3.3 新增 schema／repository instrumentation 測試，覆蓋唯一鍵、載入降序、insert-if-absent 不重寫 token、單條刪除、計數、按行程清除、外鍵啟用及刪除行程級聯。
- [x] 3.4 實作 `PinnedRouteRepository` 的 load、insert-if-absent、delete、count 與 clear API，確保 SQLite 工作可由專用單線程 executor 調用而不散落到 Activity 或 Adapter。
- [x] 3.5 補充 v1／v2 舊資料庫升至 v4 的回歸測試，確認既有歷史遷移後同樣具備 pin table／index 且不新增破壞性重建。

## 4. 查詢協調、生命週期與失敗降級

- [x] 4.1 為常用行程查詢新增協調測試，覆蓋 Citybus 結果與 pin load 並行、首次列表等待兩者、pin 讀取失敗降級、空結果保留偏好及查詢失敗不修改偏好。
- [x] 4.2 在 `MainActivity` 接入專用 pin executor、行程 id／query generation 檢查及並行載入，只有 pin load 完成或明確失敗後才提交首個非空常用結果列表。
- [x] 4.3 將投影器接入常用頁的初次結果、排序、ETA、站點預覽、下拉刷新及同一行程重查路徑；搜尋頁保持無置頂投影。
- [x] 4.4 實作本次置頂 saved-instance 序列化與恢復，只保存 fingerprint／level／token 等輕量狀態，不保存完整路線結果。
- [x] 4.5 補充 Activity／session 生命週期測試，驗證切到搜尋／設定後返回、語言／主題／旋轉／有效 task process recreation 保留本次置頂，而切換行程、修改起終點、刪除行程及無 saved state 全新啟動會清除。
- [x] 4.6 實作 dormant 長期置頂處理，確認未匹配時不建立卡片、空位、計數或錯誤，日後相同版本完整指紋出現時按原 token 恢復。

## 5. RecyclerView 差異列表與橫滑互動

- [x] 5.1 為 `BusRouteAdapter` 新增 item identity／DiffUtil 回歸測試，覆蓋路線卡、分隔列、ETA／站點內容更新、跨區 move 及搜尋頁純卡片列表。
- [x] 5.2 將 `BusRouteAdapter` 遷移為支援 `RouteCardItem` 與 `UnpinnedDividerItem` 的 `ListAdapter`，沿用 `BusRouteCardBinder` 及既有詳情、ETA、鈴鐺 callback。
- [x] 5.3 新增滑動 policy 測試，覆蓋未置頂右滑、本次右滑、任一置頂左滑、普通左滑禁止、長期右滑回彈、約 40% 門檻、無 fling 捷徑及單次門檻震動。
- [x] 5.4 在常用頁結果清單接入 `ItemTouchHelper`，實作水平主導 1:1 跟手、180–240ms 回彈、系統動畫關閉降級及不可唯一辨識卡片回彈提示。
- [x] 5.5 以命中測試排除候車文字可點擊區及鈴鐺 48dp 觸控區，並加入 instrumentation 測試確認 ETA、監控與卡片詳情入口不被橫滑搶佔。
- [x] 5.6 實作置頂／取消後的視口錨點保存與恢復，加入長列表測試確認深處置頂或取消不自動捲頂，且 DiffUtil move 保持第一個可見 item 及 offset。
- [x] 5.7 確認 `UnpinnedDividerItem` 不可點擊、不可滑動且非 sticky，並測試只有置頂與普通區域同時非空才出現。

## 6. 樂觀寫入、Snackbar 與競態恢復

- [x] 6.1 新增 pin mutation coordinator 單元測試，覆蓋升級寫入成功／失敗、取消刪除成功／失敗、刪除完成前撤銷、快速相反操作、舊 generation callback 及最後意圖勝出。
- [x] 6.2 實作本次升級長期的樂觀 UI：先顯示長期樣式再寫入，失敗時恢復本次 level、原 token／位置並提示。
- [x] 6.3 實作長期取消的樂觀 UI：先移回普通區再刪除，失敗時恢復長期 level、原 token／位置，撤銷時按序補回資料庫記錄。
- [x] 6.4 實作三個主要 Snackbar 流程：首次本次置頂附「長期置頂」、長期置頂成功包含行程名稱、取消附「撤銷」；長期右滑只回彈而不顯示 Snackbar。
- [x] 6.5 實作 pin read／write／delete 失敗的單次非阻塞提示，確認失敗不清除其他 fingerprint、行程或既有查詢結果。

## 7. 卡片視覺、分隔列、本地化與無障礙

- [x] 7.1 在既有 `item_bus_route`／binder 與 theme 資源中加入本次置頂語意描邊，確保不改變卡片尺寸、內容行或右側候車／鈴鐺約束。
- [x] 7.2 加入長期置頂左側短書籤 drawable／view（約 10dp × 25dp、位於既有約 14dp 左 inset），並驗證明暗色對比及不與核心內容重疊。
- [x] 7.3 建立普通排序分隔列 layout／binder，按未置頂數量、目前排序字段與方向生成完整句子，全部置頂、無置頂或空結果時隱藏。
- [x] 7.4 在繁體 `values`、簡體 `values-zh-rCN` 與英文 `values-en` 新增等價的 Snackbar、錯誤、分隔列、置頂狀態及無障礙 action 資源，禁止 Kotlin／XML 硬編碼 App 文案。
- [x] 7.5 為普通、本次及長期卡片加入 TalkBack 狀態與自訂 action，讓裝飾性書籤不重複朗讀，並讓分隔列朗讀完整排序句子。
- [x] 7.6 補充資源 key／placeholder parity 與卡片版面 contract 測試，覆蓋三語長文案、角標、分隔列、ETA 區及 48dp 鈴鐺觸控區。

## 8. 行程編輯、刪除、複製與傳輸一致性

- [x] 8.1 新增 repository instrumentation 測試，覆蓋只改名保留 pins、修改起終點與清除 pins 同一 transaction、任一步驟失敗完整回滾及其他行程不受影響。
- [x] 8.2 擴充 `RouteConfigRepository` 的行程更新 API，使起終點修改可在同一 transaction 更新行程並清除長期置頂，而只改名不觸碰 pin table。
- [x] 8.3 在 `RouteEditActivity` 保存前比較原行程快照；當起點或終點改變且有 N 條長期置頂時顯示數量確認，取消時不寫入，確認成功後通知目前 task 清除本次置頂。
- [x] 8.4 補充刪除行程測試及 UI 協調，確認 SQLite 級聯刪除長期置頂、session 清除本次置頂，且取消刪除保持全部資料。
- [x] 8.5 補充複製與合併匯入測試，確認新行程使用新 id 且沒有 pins，完全重複而跳過的既有行程保留原 pins。
- [x] 8.6 補充取代匯入 transaction 測試，確認成功時清理被取代行程的 pins、新行程無 pins，失敗時行程、統計及長期置頂一併回滾且本次 session 不被誤清除。
- [x] 8.7 更新 `RouteTransferCodec` 回歸測試，明確驗證 `.bicroutes` v1 不包含 pin、fingerprint 或 token，並繼續接受現行舊版 v1 文件。

## 9. 綜合驗證與文件收尾

- [x] 9.1 運行全部相關 JVM 單元測試，包含 fingerprint、session、projector、sort、mutation coordinator、adapter policy 及 transfer codec。
- [x] 9.2 運行相關 Android instrumentation 測試，包含 v3→v4 遷移、外鍵級聯、transaction 回滾、ItemTouchHelper 命中區、長列表視口、Activity recreation 及匯入取代。
- [x] 9.3 在模擬器或實機逐一驗證普通→本次→長期、反向直接取消、Snackbar 升級／撤銷、長期右滑回彈、排序／ETA／刷新、未匹配恢復及失敗降級。
- [x] 9.4 依本地化矩陣驗證繁體／簡體／英文 × 淺／深色、約 360dp、font scale 1.0／1.3／2.0、TalkBack、自訂 action、角標及分隔列；記錄任何未能完成的實機驗證。
- [x] 9.5 更新 `docs/overview-design.md`、`docs/specification.md` 與 `docs/implementation-plan.md` 的現行置頂行為、資料表、生命週期及驗證說明，不改寫歷史 change artifacts。
- [x] 9.6 運行 `./gradlew build`，修復與本 change 有關的 compile、unit test、lint 及 assemble 問題。
- [x] 9.7 依專案規則核對 `tasks.md` 勾選、`git status --short`、驗證結果及提交範圍，完成 `/opsx-apply` 後以簡潔英文 conventional commit 自動提交。
