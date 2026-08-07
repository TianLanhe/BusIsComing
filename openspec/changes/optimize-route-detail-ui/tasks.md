## 1. 基線與地圖方向折角門檻

- [x] 1.1 在 apply 開始時核對 active `fix-route-detail-progressive-loading` 與 `integrate-landsd-pedestrian-routing` 的實現、測試及 delta specs；本次不要求先歸檔，重疊 requirement 必須保留可靠站數、CSDI 成功／回退、generation、single-flight 及分域快取契約。
- [x] 1.2 定義方向紋理驗證設備畫像，確認沒有佔用目前已運行 AVD，只啟動本任務自有且符合 API、Google Maps、約 360dp、直向、語言、主題及 font scale 條件的設備。
- [x] 1.3 以 `play-services-maps 20.0.0` 的 `StrokeStyle`／`StyleSpan`／`SpriteStyle` 建立最小 instrumentation spike，固定渲染直線、急彎、S 彎及反向有序 geometry；記錄 SDK 重採樣令折角形成密集鋸齒、不能保持尺寸與間距的失敗結果。
- [x] 1.4 在停止依賴 stamp 的正式串接並修訂 design/spec 後，以屏幕投影固定間距插值及逐位置局部切線實作扁平折角 marker；裝置核對反向 geometry 全部反轉、步行只留下粗灰折角、縮放／padding 後重排且拐角不漂移，保存不含敏感資料的可復核畫面。
- [x] 1.5 禁止整段 bearing、固定角度及脫離 geometry 的手工位置；projection／局部切線不可可靠取得時保留巴士實線或省略步行折角，並為每段 marker 數量設上限。

## 2. 純展示模型與格式化規則

- [x] 2.1 先為地圖角色、雙色同站換乘、巴士／步行 stamp 種類、有序點列、標籤優先級及 stable target 補充失敗測試，再擴展 `RouteMapPresentation`／`RouteMapPresentationBuilder` 使測試通過且不引入 Google Maps SDK 類型。
- [x] 2.2 為起點、終點、上車、下車、途經及同站／異站換乘的角色、前後段色、marker 合併與步行有無補充單段及多段 JVM 測試。
- [x] 2.3 新增純 Kotlin 標籤候選／碰撞策略及測試，覆蓋右、左、上、下候選、角色優先級、viewport／bottom sheet／CSDI 署名／路徑／marker／標籤碰撞、最低衝突回退、普通站隱藏及舊側穩定。
- [x] 2.4 先為摘要完整行動鏈、stable `detailTargetId`、單段／多段／同站／異站換乘次序及缺失動態資料補充失敗測試，再擴展 `RouteDetailUiFormatter` 的純展示 model。
- [x] 2.5 實作按資料域計算摘要耗時的純函數：巴士只依本次新鮮 Citybus 計劃時間邊界，步行只依目前 walking domain 的 CSDI `Total_Time` 向上取整；測試正常、跨午夜、缺失、非法、Citybus fallback、SameStop 與過期值，並禁止從距離、ETA 或 24 小時結構快取估算。
- [x] 2.6 保留 `RideStopCountState.Loading／Available／Unavailable`、`Σ(viaStops.size + 1)`、完整／部分步行合計及結構化首程 ETA，補充回歸測試證明 formatter 不把未知值顯示為零。
- [x] 2.7 確認 `RouteStructureCache` 及其他 24 小時 cache 沒有新增計劃時間、分段票價、摘要耗時或 ETA 欄位，並以 cache round-trip／domain cache 測試鎖定資料邊界。

## 3. 固定比例圖標、色彩與第三方告知

- [x] 3.1 新增或調整起點綠色地圖針、終點珊瑚紅地圖針、上車實心圓白色巴士、普通中性站點及開放折角 VectorDrawable，確認 viewport、path、intrinsic ratio 及明暗模式資源不變形。
- [x] 3.2 從 Lucide 官方 `log-out` SVG 本地等比轉換下車 VectorDrawable，保持原 viewBox 比例並以目前乘車段色渲染；不得載入遠端資產或使用字體 glyph。
- [x] 3.3 實作前後乘車段各佔半環、內含中性環形換向箭頭的同站換乘資產／組合繪製，覆蓋不同色 slot、選中狀態及深淺模式。
- [x] 3.4 把 Lucide 版權及 ISC 許可文字加入 App 隨附第三方告知，加入資源／告知回歸檢查並確認未誤加入其他遠端或商標資產。
- [x] 3.5 核對摘要繼續直接使用現有 `ic_walking_person`，在所有 density 下保持等比 18dp 視覺尺寸且沒有預覽占位或拉伸複本。

## 4. Google 地圖 renderer

- [x] 4.1 擴展 `RouteMapMarkerIconFactory`／renderer bitmap cache，以角色、色 slot、前後段色、模式、選中狀態及 density bucket 產生固定比例 marker，並測試相同 key 復用與狀態改變失效。
- [x] 4.2 以 camera-idle 屏幕投影、固定屏幕間距與逐位置局部切線實作巴士白描邊＋分段色實線＋白色開放折角，確保同一有序 geometry、stable id 差量更新及反向定向。
- [x] 4.3 只對 CSDI 成功分段的每個有序 path 使用粗灰開放折角 marker；移除步行底線、端點直線與舊 `Dash／Dot／Gap` 地圖 pattern，Loading／失敗／Citybus fallback／SameStop 不得建立步行折角或補線。
- [x] 4.4 為 projection／局部切線不可用加入安全降級：巴士保留可靠實線、步行省略折角；測試不得建立整段 bearing、固定角度或脫離 geometry 的方向圖標。
- [x] 4.5 在 `GoogleRouteMapRenderer` 加入 camera-idle 標籤 overlay／等效標籤管理，使用目前 projection 與 2.3 的純策略計算位置，關鍵站常駐嘗試、普通站按空間顯示，並保持完整 marker 無障礙名稱。
- [x] 4.6 把 bottom sheet 高度、WindowInsets、Google attribution、目前可見 CSDI 署名安全區及地圖 padding 納入標籤可見矩形；測試 camera move 不逐幀重算、camera idle／padding 更新才重排且舊側有效時不跳動。
- [x] 4.7 保留 marker／時間線雙向聯動、選中途經站展開、相機所有權、全覽、目前位置、MapView 生命週期及地圖／geometry 獨立降級，補充差量更新與過期 generation 回歸測試。

## 5. 三層摘要與分段跳轉

- [x] 5.1 把摘要改為無 MaterialCard 邊框的三層內容：總耗時後接可用預計到達、完整行動鏈、乘坐站數／步行距離／總票價／首程 ETA，保持摘要作為 RecyclerView 首項正常捲出。
- [x] 5.2 實作單行可水平捲動的行動鏈：所有步行塊同一灰底、乘車塊使用分段色、內容包裹寬度、相鄰約 2dp、無連接箭頭、無等寬拉伸、無多餘空白。
- [x] 5.3 把可見塊控制為約 22dp 高、圖標／路線內容約 18dp、上下各約 2dp，讓路線號／步行圖標與小號耗時共用底部基線；以 layout/instrumentation 測試鎖定高度、間距、baseline 及圖標比例。
- [x] 5.4 為每個可見分段建立至少 48dp 有效操作高度且水平方向互不重疊的透明 TouchDelegate／獨立語義節點；保持內容包裹寬度，以可見塊邊界或相鄰中點裁切並測試觸控座標及 TalkBack 行程順序。
- [x] 5.5 實作摘要分段點擊：進入 FULL、定位對應 stable item、短暫低強度高亮、移動 TalkBack 焦點並朗讀本地化標題。
- [x] 5.6 實作綁定 page generation＋穩定結構 identity 的 pending target；同一結構 item 到達後完成一次跳轉，動態詳情／ETA／walking refresh 不得誤清除，結構最終失敗、新頁／新結構、離頁或銷毀時才清除並按需要朗讀不可用。
- [x] 5.7 在窄屏、大字體及長路線號下驗證行動鏈只水平捲動而不換行／壓縮圖標，第三層則可自然換行且不裁切、縮字或形成內部垂直捲動。

## 6. 半屏／全屏時間線與頁面 chrome

- [x] 6.1 調整 `RouteTimelineRailView`，讓整體起點為白環綠心、終點為白環珊瑚紅心、步行為中性輕量點線、巴士為分段色連續實線；上車與下車位置不得繪製放大節點或額外空心圓。
- [x] 6.2 移除乘車段外框及獨立卡片底色，以 rail、留白與文字層級分組，保留上下車站、計劃時間、路線、方向、途經站展開及下車內容。
- [x] 6.3 從第一乘車段移除首程 ETA，從所有乘車段移除單段乘坐／途經站數；途經站 toggle 的 `N` 只表示可展開項目數，加入 adapter 回歸測試。
- [x] 6.4 多段路線把可靠單段票價放在路線／方向同一行末端；單段路線及票價缺失時隱藏，測試不得顯示破折號、零值或估算值。
- [x] 6.5 修改 `activity_route_detail.xml` 與 `RouteDetailActivity`：FULL 移除 Toolbar、頁面標題及屏內返回占位，內容在 status bar safe inset 與 drag handle 後鋪滿；SUMMARY／HALF 保留地圖左上 floating back。
- [x] 6.6 保留 FULL 向下拖至 HALF、三檔 handle 互動與所有檔位系統返回直接 finish；失敗狀態也不得恢復 App Bar 或屏內返回，補充 state policy／instrumentation 測試。
- [x] 6.7 同步香港繁體、獨立簡體及自然英文的摘要、分段、不可用朗讀、content description 與無障礙文案，保持 Citybus 站名、路線號及方向原文不翻譯。

## 7. 自動化、構建與裝置驗收

- [x] 7.1 運行所有新增及受影響 JVM 測試，至少覆蓋 presentation、formatter、跨午夜耗時、站數／ETA 狀態、cache 邊界、碰撞策略、sheet policy、renderer identity 及過期 generation。
- [x] 7.2 運行受影響 instrumentation 測試，覆蓋 22dp 可見／48dp 有效操作高度、水平觸控不重疊、水平捲動、分段跳轉／高亮／焦點／朗讀、時間線 rail、票價／ETA 去重與全屏 chrome。
- [x] 7.3 運行 `./gradlew build`，修復本 change 引入的編譯、lint 或測試失敗；無法運行時記錄具體原因及未覆蓋風險，不得宣稱完整驗證。
- [ ] 7.4 使用本任務自有 AVD 驗證單段、多段、同站換乘、異站步行換乘、缺失 geometry／時間／票價與局部失敗；逐段核對 marker 角色、站名避讓、摘要次序及時間線連續性。
- [x] 7.5 在固定直線、急彎、S 彎、反向 geometry、不同 zoom 與 bottom sheet padding 下再次做最終方向驗收；任何折角未嚴格貼合局部軌跡或拐角漂移均視為未完成。
- [x] 7.6 完成繁體／簡體／英文、淺／深色、約 360dp／窄屏、font scale 1.0／1.3／2.0 與 TalkBack 矩陣，確認第三方名稱原文、Google attribution、安全區、48dp 操作及列表終點可達。
- [x] 7.7 檢查 OpenSpec scenarios 與驗證證據逐項可追溯，執行 `openspec validate optimize-route-detail-ui --strict`，核對 `git status --short` 與提交範圍後按 `/opsx-apply` 規則提交。
- [x] 7.8 關閉本任務啟動的全部 AVD，確認未操作、重啟或關閉任務開始前已運行的任何模擬器。
