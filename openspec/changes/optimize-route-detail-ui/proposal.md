## Why

目前路線詳情已能漸進載入地圖、Citybus 站點結構、幾何及首程 ETA，但關鍵地圖角色、路線前進方向、摘要行動鏈與半屏時間線仍不易快速辨識，並有重複資訊及全屏空間浪費。這次變更把已確認的視覺與互動結論固化為可驗收契約，同時保留既有資料權威、快取、generation 與局部失敗邊界。

## What Changes

- 以固定比例圖形區分起點、終點、上車、下車、同站換乘及普通途經站；關鍵站名常駐，普通站名依縮放、選中與碰撞避讓顯示。
- 在巴士實線與步行連接上加入嚴格沿有序 geometry 局部切線排列的開放折角；地圖步行段只顯示粗灰折角，同站換乘不偽造步行軌跡。
- 把摘要精簡為「總耗時／預計到達、可點擊完整行動鏈、站數／步行／票價／首程候車」三層，移除邊框，並讓每個分段可跳轉、聚焦及朗讀對應詳情。
- 簡化半屏／全屏時間線：使用連續軌跡，移除乘車段卡片邊框、首段重複 ETA、單段站數及放大的上下車節點；多段方案的單段票價移到路線／方向同一行末端。
- 全屏檔位移除標題、Toolbar 與屏內返回按鈕，內容自安全區及拖動把手後鋪滿；系統返回在所有檔位仍直接退出詳情。
- 維持 Citybus、DATA.GOV.HK、Google Maps 及已接入 CSDI 的請求、解析、24 小時成功／結構快取、single-flight、reducer、generation、相機所有權及局部重試契約；本 change 不新增網絡請求或導航／即時追蹤功能。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `route-detail-google-map`: 修改地圖角色 marker、站名密度與碰撞避讓、巴士／步行方向紋理、同站換乘，以及全屏檔位的地圖與返回入口行為。
- `route-detail-bottom-sheet`: 修改三層摘要、分段跳轉、半屏／全屏時間線、首程 ETA 位置、單段票價、站數及無邊框呈現契約。

## Impact

- 主要影響路線詳情 Activity／persistent bottom sheet、地圖展示模型與 GoogleMap renderer、摘要 formatter、時間線 RecyclerView／adapter，以及三語字串與無障礙描述。
- 需要新增或調整本地 VectorDrawable；下車圖標採用 Lucide `log-out` 並隨 App 保留 ISC 第三方許可告知。現有 `ic_walking_person` 必須按原比例復用。
- 不改變 repository／parser 的外部接口或 domain 欄位權威；巴士摘要分段耗時及單段票價只可使用本次新鮮 Citybus 動態詳情，成功步行段耗時只可使用目前 walking domain 的 CSDI `Total_Time`；Citybus fallback／SameStop 不顯示步行耗時，任何動態值均不能寫入或讀自 24 小時結構快取。
- 方向折角先以 Google Maps SDK 的 polyline stamp/style 做裝置 spike；實測因 SDK 重採樣而形成密集鋸齒，不能保持已確認的尺寸與間距。正式方案改由 renderer 在 camera idle／padding 更新後把同一有序 geometry 投影到屏幕，以固定屏幕間距插值並按每個位置的局部屏幕切線放置扁平折角 marker；不得使用整段 bearing、固定角度或脫離軌跡的手工位置。
- 驗證涵蓋純 Kotlin formatter／碰撞規則、renderer 差量更新與失敗降級、摘要 pending target／generation、三語、360dp／窄屏、大字體、明暗模式、TalkBack，以及單段、多段、同站／異站換乘與反向彎道的裝置視覺驗收。
