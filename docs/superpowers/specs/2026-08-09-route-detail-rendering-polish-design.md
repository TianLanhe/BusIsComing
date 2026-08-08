# 路線詳情渲染與設定介面精修設計

## 背景

`integrate-landsd-pedestrian-routing`、`optimize-route-detail-ui` 與 `add-route-auto-refresh` 已完成首次實作，但裝置驗收發現四類需要在歸檔前修正的問題：路線詳情 persistent bottom sheet 拖動卡頓、地圖方向紋理過大且在彎道越出巴士色線、CSDI 署名視覺重量過高，以及自動刷新設定的五個常駐按鈕過大。後續視覺確認又增加兩項精修：壓縮把手上下空白，以及只放大摘要第二行的可點擊行程階段。

這些問題均屬現有能力的實作與驗收修正，不新增資料來源、網絡請求、使用者資料或導航能力。三個原 change 尚未歸檔，因此本輪直接重新打開並修訂原 change，不另建會與未歸檔 ADDED requirements 重疊的修復 change。

## 已確認結論

- bottom sheet 拖動期間停止重建地圖方向 marker 與站名 marker；CSDI 署名只以 view translation 跟隨，最終停靠後再精確重排一次。
- 巴士方向使用能完整收在 7dp 彩色核心內的白色淺折角；步行只使用較密的灰色開放折角，不加灰色底線、虛線、點線或端點補線。
- 方向紋理只按可見視口及少量 overscan 取樣；屏外幾何不得稀釋目前視口的紋理密度。
- CSDI 可見署名約為 `116×29dp`，官方標誌為 `15dp`，保留原雙行內容及至少 `48dp` 有效操作範圍。
- 自動刷新改為標準設定行，右側顯示目前值；點擊整行打開 Material 單選對話框。
- bottom sheet 把手可見容器改為 `28dp`，`36×4dp` 橫線保持居中，透明操作範圍仍至少 `48dp`。
- 摘要第一行總耗時／預計到達及第三行統計保持現狀；只把第二行階段塊由 `22dp` 放大為 `30dp`。

## 目標

- 消除 bottom sheet 連續拖動期間由 App 主動製造的地圖 marker churn、bitmap 重建與 requestLayout。
- 在不同縮放比例、彎道、S 彎與反向幾何上保持方向紋理貼合、清楚且不越出巴士色線。
- 保持 CSDI 法律署名內容、來源入口及碰撞安全區，同時降低其地圖視覺重量。
- 讓自動刷新設定與現有外觀／語言設定列一致，並在三語、大字體與 TalkBack 下保持自然。
- 在不放大標題及第三行統計的前提下，提高摘要行程階段的辨識與點擊便利性。
- 保留現有資料權威、generation、漸進載入、相機所有權、自動刷新狀態機及局部失敗邊界。

## 非目標

- 不重寫 Google Maps renderer、不切換 Canvas overlay 或第三方地圖 SDK。
- 不改變 Citybus、DATA.GOV.HK、CSDI 或 Google 的接口、解析、cache 或請求頻率。
- 不重新設計地圖 marker 角色、站名文案、時間線結構或摘要跳轉語義。
- 不增加逐步步行導航、即時車輛追蹤、背景刷新或自訂刷新秒數。
- 不歸檔任何 active change；由使用者完成實機驗收後再決定歸檔。

## 性能診斷

### 目前熱路徑

`RouteDetailActivity` 的 `BottomSheetCallback.onSlide()` 在每個拖動回呼呼叫 `updateMapPadding()`。該方法又會：

1. `post` 到 sheet 重新讀取可見高度；
2. 每次重設 CSDI 署名的 `bottomMargin` 與 `layoutParams`，觸發 layout；
3. 再次 `post` 後呼叫 renderer 的 `updatePadding()`；
4. renderer 無條件呼叫 `GoogleMap.setPadding()`、`relayoutDirectionArrows()` 與 `relayoutLabels()`；
5. `relayoutDirectionArrows()` 先移除每條線的全部箭頭 marker，再全部新增；
6. `relayoutLabels()` 重新建立文字 bitmap，移除並新增 label marker。

因此卡頓的主因不是 MapView 單純存在，而是 App 在每個手指移動 frame 同時造成主線程 marker 管理、View layout、文字 bitmap 及地圖 GL／RenderThread 工作。

### 裝置證據

在本任務新啟動的 API 36、360dp、Google Play AVD 上，以同一 synthetic route 比較 idle 與連續拖動：idle 的 8 秒 simpleperf 樣本為 17，拖動樣本為 18,758；拖動期間 GL-Map、RenderThread 與 App main thread 均成為主要熱點，first-party `GoogleRouteMapRenderer.relayoutDirectionArrows` 亦出現在樣本中。這組資料用於定位熱路徑，不作跨機型的固定毫秒門檻。

## 設計一：拖動期間使用兩階段地圖更新

### 拖動開始

Bottom sheet 進入 `DRAGGING` 或等效過渡狀態時，Activity 只做一次：

- 把目前 label marker 的 alpha 淡出至零，但不移除 marker 或 bitmap；
- 記錄已提交的 detent、padding、署名基準位置與拖動方向；
- 保留巴士 polyline、方向 marker、站點 marker、選中狀態及相機；
- 把下一個安全 detent 的地圖 padding 作為候選，而不是逐 frame 使用 sheet 的實際像素高度。

### 拖動進行中

`onSlide()` 只允許執行便宜、無配置的 view property 更新及必要的 detent 預測：

- CSDI 署名使用 `translationY` 跟隨 sheet 上緣，不修改 margin 或 `layoutParams`；
- 向上拖動時，首次跨入下一檔方向便預留下一 detent 的安全 padding；
- 向下拖動時保留起始較大的 padding，避免 Google attribution、站名或署名短暫落入即將被 sheet 覆蓋的區域；
- 同一候選 padding 未改變時不重複呼叫 `GoogleMap.setPadding()`；
- 不重排方向紋理、不重建站名、不建立文字 bitmap，也不重設相機。

此策略接受拖動過程中地圖可用區可能暫時比實際露出區略保守，以換取穩定 frame time 和零 marker churn。

### 最終停靠

Bottom sheet 到達 `SUMMARY`、`HALF` 或 `FULL` 後：

1. 計算並提交一次精確 padding；
2. 清除 CSDI translation，按最終 detent 設定一次正式 margin；
3. 由目前相機 projection 重排一次可見方向紋理；
4. 由最終安全矩形重排一次站名並淡入；
5. 保持相機 target、zoom、使用者選中項、列表位置與展開狀態。

若過渡被取消或直接跳到另一 detent，仍只以最後的 stable state 完成一次精確提交。地圖不可用、Activity 已銷毀或 projection 失敗時安全跳過，不補畫替代幾何。

## 設計二：方向紋理按可見幾何與安全切線生成

### 視覺尺寸

巴士線保持 `9dp` 對比白色描邊與 `7dp` 分段色核心。白色方向折角採較扁、較淺的開放 Chevron，完整視覺包絡寬約 `5.5dp`、stroke 約 `1.2dp`，中心沿核心線，固定屏幕間距約 `36dp`。折角的全部非透明像素必須留在彩色核心寬度內，不能依賴白色外描邊掩蓋越界。

成功 CSDI 步行軌跡只使用灰色開放折角，視覺寬約 `9dp`、stroke 約 `2.4dp`、固定屏幕間距約 `14dp`。它沒有灰色實線、虛線或點線底圖，因此密度需要足以讓使用者看出 path 的彎曲走向，但仍不得在子路徑空隙補線。

### 可見視口取樣

renderer 先把每個有序 path 投影至屏幕，再只保留與「目前地圖安全矩形＋少量 overscan」相交的 screen segments。取樣間距只由上述固定 dp 值決定；屏外長路線不參與 `totalLength / cap`，因此放大地圖時不會因整條投影線變長而讓可見箭頭變稀。

安全上限只在完成 viewport clipping 後限制異常資料造成的 marker 數量，且不得反向放大正常 spacing。多個 CSDI `geometry.paths` 各自保留 path 邊界與順序，不能跨 path 累加或補接。

### 局部方向與拐角安全

每個候選位置以局部屏幕切線計算旋轉，點序反轉時全部折角同步反轉。為避免折角在拐角外飄：

- 取樣點前後須有足以容納完整 glyph 的同一局部切線窗口；
- 若窗口跨越角度變化過大的 vertex，候選移到鄰近可容納的直線區間；
- 沒有安全區間時省略該 glyph，不以整段 bearing、固定角度或任一單邊 segment 冒充；
- 巴士 glyph 的像素包絡另受 7dp 核心寬度測試約束。

### Marker 復用

每條 rendered line 維護方向 marker pool。camera idle、最終 padding 或有效 path 改變時，按目前 placement 次序復用既有 marker，只更新 position、rotation、icon、visibility 與 z-index；只新增不足數量、移除超額數量。方向 icon 依 density、kind 與 palette cache，不在每次重排重建 bitmap。

相機手勢進行中不重新取樣；既有 marker 隨 Google Map 正常移動，camera idle 後才按新 zoom／viewport 重新分布。

## 設計三：CSDI 署名縮小但保持法律與無障礙責任

CSDI 可見署名採已確認的緊湊 B 方案：整體約 `116×29dp`，官方地政總署標誌 `15dp`，保留目前兩行內容與三語資源。背景使用輕微半透明的語義 surface、較小圓角，不使用 1dp 卡片描邊、厚重陰影或與主要操作同級的視覺重量。

可見署名文字允許隨系統字體縮放，但最高約以 font scale `1.3` 排版，避免 font scale `2.0` 時在地圖上擴張成主要資訊卡；點擊後的完整來源、版權及免責對話框仍正常遵循系統字體。署名本身的有效觸控區以 `TouchDelegate` 或等效透明 hit target 擴展至至少 `48dp`，不放大可見背景。

只有實際顯示至少一條 CSDI path 時才顯示。碰撞模型使用可見署名的實際矩形加必要安全 margin，不以 48dp 透明 hit target 造成過大的地圖禁區。它仍須避讓 Google Logo、法律文字、返回、定位、全覽與 bottom sheet；拖動期間按設計一使用 translation，停靠後再提交正式位置。

## 設計四：摘要與把手只調整必要尺寸

### 把手

`routeDetailSheetHandle` 的可見版面高度由 `48dp` 改為 `28dp`，`36×4dp` 橫線保持水平及垂直居中，因此上下各約 `12dp` 空白。點擊切換 detent、BottomSheetBehavior 拖動及三個狀態的 content description 保持不變。

把手的有效點擊範圍由 sheet content 在垂直方向透明擴展至至少 `48dp`；擴展區只覆蓋下方不可點擊的標題空間，不能遮擋摘要階段或其他 action。TalkBack 仍保留單一把手節點，不新增重複的隱形節點。

### 摘要第二行

只把完整行動鏈的可見階段塊由 `22dp` 放大為 `30dp`：

- 現有 `ic_walking_person` 由 `18dp` 等比放大為 `24dp`，不得以預覽圖、emoji 或非等比複本替換；
- 巴士路線號約 `17sp`、小號耗時約 `12sp`；
- 路線／圖標與耗時繼續共用底部 baseline，耗時位於內容右下但不另佔一行；
- 每段仍按內容包裹寬度，相鄰 gap 保持約 `2dp`；
- 超寬時保持單行水平捲動，不改為等寬、換行或壓縮圖標；
- 透明 TouchDelegate 仍把每段有效操作高度擴展至至少 `48dp`，水平方向不得與相鄰段重疊。

第一行「總耗時 · 預計到達」完整保持目前 `21sp`、字重、文字次序與 spacing。第三行總站數、步行距離、總票價及首程候車狀態亦保持目前 `13sp` 與排版。摘要內容上／下 padding 除配合新階段高度的必要 layout 外不額外增加；把手縮小 20dp、階段增加 8dp，因此摘要態整體仍比目前減少約 12dp 的非必要高度。

## 設計五：自動刷新使用標準設定行與單選對話框

偏好分組仍依序顯示外觀主題、語言、自動刷新。`settingsAutoRefreshRow` 改為與前兩列相同的可點擊標準設定行：左側顯示 `自動刷新`，右側使用 `SettingsRowValueText` 顯示目前的 `關閉`、`1 分鐘`、`2 分鐘`、`5 分鐘` 或 `10 分鐘`。頁面不再常駐五個 48dp MaterialButton，也不因大型字體形成兩至三行按鈕矩陣。

點擊整行打開 `MaterialAlertDialogBuilder.setSingleChoiceItems()`：

- 初始選中項由同一 `RouteAutoRefreshSettingsStore` 讀取；
- 點擊不同項立即保存、完成首次 notice、更新設定行目前值並關閉對話框；
- 點擊目前項仍完成首次 notice 並關閉，但不改變已保存值，也不製造可見 reload；
- 設定 store 與目前 controller 的既有通知／到期重算語義保持不變；
- 不顯示成功 Toast、額外確認或另一層頁面。

Settings deep link 的 `focusAutoRefreshSelector()` 改為捲動並聚焦整個設定行，而不是搜尋已選中的 MaterialButton。標準 dialog radio 語義負責朗讀各選項及 checked state；設定行 content description／value 共同表達目前值。三語、明暗模式及 font scale `1.0／1.3／2.0` 使用同一資訊結構，長文案可自然換行但不能互相覆蓋。

## 資料與狀態流

本輪不新增 domain model 或 repository 請求。主要狀態流如下：

1. `RouteMapPresentation` 仍提供有序 bus／walking paths、marker 與 stable id。
2. renderer 在 render、camera idle 或最終 detent padding 到達時，從目前 presentation 產生 viewport-local placements。
3. bottom sheet 過渡 policy 只決定拖動開始、候選安全 padding、CSDI translation 與最終提交；它不改變 presentation、相機或 reducer generation。
4. 摘要仍由 `RouteDetailUiFormatter` 生成相同 segment model；Adapter 只調整視覺尺寸。
5. 自動刷新設定仍由單一 `RouteAutoRefreshSettingsStore` 持久化，所有可見 owner 依既有 controller policy 重新計算 due。

Citybus 動態更新、ETA、CSDI walking、geometry、站名及摘要 pending target 的 generation 邊界不變。自動刷新不能因設定 UI 改變而重請 CSDI、重置相機、替換結構或清除已選 interaction。

## 失敗與降級

- projection、局部切線或拐角安全判定失敗：保留可靠巴士 polyline，省略對應 glyph；步行不補假軌跡。
- 拖動途中 Activity 銷毀或 Map 不可用：取消未提交的最終重排，不在新頁面執行舊 callback。
- CSDI 署名尚未量測：先使用安全 padding，量測完成且頁面 generation 有效時再提交一次最終 reserved rect。
- 自動刷新設定保存失敗若未來 store 引入可失敗介面，保持舊值並留在對話框或顯示本地化錯誤；目前 SharedPreferences 同步介面沿用既有行為。
- 大型字體令摘要總高度超過普通 summary detent：繼續沿用內容驅動的 sheet metrics，不縮字、不裁切，也不讓摘要內部垂直捲動。

## OpenSpec 修訂方式

不建立新 change。重新打開下列 change 並增加未完成任務：

### `optimize-route-detail-ui`

- 在 `route-detail-google-map` delta 中補充 viewport-local spacing、glyph 包絡不得越出核心、拐角安全窗口、marker reuse 與拖動期間不重排。
- 在 `route-detail-bottom-sheet` delta 中把約 `22dp／18dp` 修改為 `30dp／24dp`，明確第一、三行保持不變，並加入 `28dp` 可見把手及 `48dp` 有效操作範圍。
- design 加入兩階段 padding／label fade／CSDI translation 架構及性能診斷。
- tasks 新增先測試後實作、性能採樣、視覺矩陣與 build 驗證；原已完成任務保留勾選。

### `integrate-landsd-pedestrian-routing`

- 在可見署名 requirement 增加 `116×29dp`、`15dp` logo、輕量 surface、可見層 font scale 上限與 `48dp` hit target。
- design／tasks 增加署名尺寸、碰撞矩形、拖動 translation 及三語大字體驗證。

### `add-route-auto-refresh`

- 以標準設定行＋單選對話框取代「行內 segmented selector／不得打開 radio dialog」的舊合同。
- 保留五個值、立即保存、重選目前值完成 notice、三語、至少 `48dp` 操作與全 App 共用偏好。
- 更新 Settings deep focus、instrumentation 與 screenshot matrix，不改 controller、notice 或 runtime refresh 契約。

三個 change 在新增任務完成前重新呈現為未完成；本輪不歸檔任何 change。

## 測試與驗收

### 純 JVM／策略測試

- 方向 placement：水平、垂直、折線、急彎、S 彎、反向點序、短 path、重複點、多子路徑及 projection 失敗。
- 至少兩個 zoom 對應的投影尺度；可見 spacing 保持固定，屏外超長幾何不改變可見密度。
- 巴士 icon 的全部非透明像素留在 7dp 核心包絡；急彎窗口不足時省略而非越界。
- viewport clipping＋overscan、異常安全 cap 及 marker pool 增／減／復用。
- bottom sheet transition policy：連續多個 slide event 不要求 arrow／label relayout；向上只提交下一 detent 安全 padding，向下保留較大 padding，最終只提交一次精確重排。

### Instrumentation／View 測試

- 把手可見高度 `28dp`、有效操作範圍至少 `48dp`、三 detent content description 及不遮擋摘要 action。
- 摘要第一行 `21sp` 與第三行 `13sp` 不變；第二行塊 `30dp`、真實步行 icon `24dp`、baseline、2dp gap、content-wrap、水平捲動及 48dp 操作高度。
- CSDI `116×29dp` 級可見尺寸、15dp logo、兩行三語、無厚重 stroke/elevation、完整 dialog、TalkBack、Google attribution 及控件避讓。
- 自動刷新設定行目前值、dialog 五項與 checked state、不同值及重選目前值、重建持久化、Settings deep focus。
- 三語 × 明暗 × 360dp × font scale `1.0／1.3／2.0`，避免裁切、重疊、錯誤縮字或多餘按鈕矩陣。

### 真實地圖與性能驗收

只啟動本任務未運行且符合 API、360dp、Google Play、語言、主題與字體畫像的 AVD：

- 使用可復現 synthetic route 檢查直線、彎道、反向、多段及 CSDI 多 path；
- 在不同縮放比例確認巴士白色折角不越出色線、步行折角能描述細節且不補線；
- 連續拖動摘要↔半屏↔全屏，採樣確認 first-party 熱路徑不再逐 frame 移除／新增方向及 label markers；
- 拖動期間確認標籤淡出、路線保持、署名跟手且 Google attribution 不被遮擋；停靠後只出現一次最終重排；
- 完成後關閉本任務啟動的全部模擬器。

最終運行受影響 JVM／instrumentation 測試、OpenSpec strict validation 及 `./gradlew build`。若真實服務、Google Play 或裝置環境阻止某層驗證，交付時明確列出未驗證項，不以自動測試冒充真實地圖視覺驗收。

## 風險與緩解

- **拖動時保守 padding 令地圖可用區短暫偏小**：只在過渡期間接受，換取不遮擋及穩定 frame time；停靠後立即精確恢復。
- **標籤淡入造成閃爍**：只有進入拖動時淡出一次、stable detent 後淡入一次；不用每 frame alpha 切換。
- **過密步行 marker 增加 GPU 負載**：只在 viewport＋overscan 生成，camera move 不重排，異常 cap 在 clipping 後才生效。
- **30dp 階段塊重新增加摘要高度**：把手同時減少 20dp，總體仍比目前緊湊；第一、三行不放大。
- **CSDI 小字在複雜底圖上難讀**：保留半透明 surface、完整點擊說明及 TalkBack；不把可見署名當主要閱讀入口。
- **對話框比行內按鈕多一次點擊**：換取設定頁更輕、更一致及大字體可讀；間隔是低頻偏好，不是高頻操作。

## 完成條件

- 三個 active change 的相關 proposal／design／spec／tasks 與本設計一致，strict validation 通過。
- 所有新增任務完成，原能力的資料與生命週期測試無回歸。
- 拖動不再逐 frame 重建方向及站名 marker，CSDI 不再逐 frame requestLayout。
- 多種 zoom／彎道／反向資料的方向紋理通過自動與人工視覺驗收。
- CSDI 署名、自動刷新設定、28dp 把手及 30dp 階段塊通過三語、主題、大字體與 TalkBack 驗收。
- `./gradlew build` 成功；任何未能執行的真實環境驗證均被如實記錄。
