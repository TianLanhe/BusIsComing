# BusIsComing 概要設計

## 產品與架構

BusIsComing 以已保存的常用行程為入口，向 Citybus 查詢點到點巴士路線，並漸進補齊 ETA、站點預覽與路線詳情。App 使用 XML、Material Components、RecyclerView、輕量 Repository 分層及 SQLiteOpenHelper；外部查詢資料不寫入本機資料庫。

常用頁的主要資料流為：

```text
MainActivity
├─ RouteQueryCoordinator → CitybusBusRouteRepository
└─ pinExecutor → PinnedRouteRepository
          ↓ 兩者完成或置頂讀取明確失敗
RouteQueryState.rawResults + RoutePinSessionState
          ↓ PinnedRouteProjector
BusRouteAdapter(ListAdapter)
```

搜尋頁沿用相同卡片 Adapter 與 Binder，但只提交普通路線卡，不建立置頂區域，亦不附加置頂手勢。

## 常用頁路線置頂

每個可嚴格辨識的路線結果，在一個已保存行程內具有未置頂、本次置頂或長期置頂三種狀態。右滑依次由未置頂升為本次、由本次升為長期；本次或長期置頂向左滑均直接取消。長期置頂再次右滑只平滑回彈。

本次與長期置頂共用單一 `pinnedAt` token，按 token 降序展示，因此後置頂在最上方；由本次升級為長期時保留原 token。普通排序只作用於未置頂區域。兩個區域同時存在時，中間插入一個隨列表捲動的排序分隔列。

`RouteQueryState` 保存原始結果及既有排序狀態，`PinnedRouteProjector` 每次根據原始結果、目前排序及 `RoutePinSessionState` 產生純列表投影。ETA、站點預覽、排序、刷新與同一行程重查均走同一投影路徑，避免把置頂順序混入 `BusRouteSorter`。

## 身份與生命週期

長期置頂使用 `v1|` 版本化嚴格指紋。指紋按乘車段順序編碼公司、公開路線號、variant、bound、direction path 及上下車 sequence；語言、名稱、價格、耗時、步行、ETA、站點預覽、`rawInfo` 及 `resultId` 均不參與身份。必要字段缺失或同一結果集出現重複指紋時，卡片保持可見但不可置頂。

本次置頂只保存在目前 Android task 的 Activity saved state，內容只有行程 id、指紋與 token。它在排序、刷新、切到搜尋／設定、旋轉及同一 task 重建時保留；切換行程、修改起終點、刪除行程或全新啟動時清除。未匹配的長期指紋保留在 SQLite，不建立空白卡或預留位置。

## 本機資料

資料庫版本為 v4。`route_result_pins` 以 `(route_config_id, route_fingerprint)` 為複合主鍵，保存 `pinned_at`，並建立 `(route_config_id, pinned_at DESC)` 索引。外鍵在 `onConfigure` 啟用，刪除行程時由 `ON DELETE CASCADE` 清除長期置頂。

修改行程起終點時，行程更新與清除該行程長期置頂在同一 transaction 內完成；只改名稱不觸碰置頂。複製與匯入新增的行程使用新 id 且沒有置頂；取代匯入的刪除及級聯亦屬同一 transaction。`.bicroutes` v1 不包含任何置頂狀態。

置頂讀取開始時會記錄每個指紋的 mutation generation；讀取完成後，只以資料庫結果取代期間未被操作的指紋，避免較慢的讀取覆蓋使用者剛完成的置頂或取消。由本次置頂升級為長期置頂時，寫入 transaction 亦會核對操作開始時的起終點快照；若行程已在佇列等待期間被修改，舊作用域的寫入會被忽略，避免已清除的置頂被延遲工作重新建立。

## 互動與可用性

常用結果使用 `ListAdapter` 與 DiffUtil 表達跨區移動及內容更新。滑動採有限 pull-to-action：觸發距離為卡片邊緣 16dp 留白加目前語言動作文字的實際量測寬度，最大可見位移只再增加 8dp，不使用 fling 捷徑或 swipe-to-dismiss。卡片在門檻前 1:1 跟手，單次跨門檻只震動一次；釋放時一律先以約 210ms 回到零位移，再決定是否執行動作。滑動底層保持透明，只露出頁面既有背景與主題主要文字色的動作文字。ETA 文字區與 48dp 鈴鐺區不啟動置頂滑動。

首次把普通卡設為本次置頂後，同一穩定卡片移至 LIFO 置頂區第一項，清單隨後顯示結果頂部，讓用戶看到新置頂卡片及原有內容向下推移。本次升級長期保留 token、位置及視口；取消、排序、刷新、ETA 與站點預覽更新則按穩定 item identity 恢復第一個可見項目與像素偏移。回彈完成、`clearView` 與卡片重綁均保證水平位移歸零，避免同一 identity 升級後沿用已滑出畫面的 ViewHolder。

本次置頂使用語意強調描邊；長期置頂另加左側 10dp × 25dp 短書籤。TalkBack 透過狀態描述及自訂 action 提供本次置頂、長期置頂及取消置頂能力。所有可見文案均提供香港繁體、簡體與英文。
