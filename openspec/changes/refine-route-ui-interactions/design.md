## Context

目前 `MainActivity` 協調常用頁查詢、底部導航及乘車碼入口，`SearchFragment` 獨立持有搜尋輸入與查詢狀態，兩頁共用 `BusRouteAdapter`、`BusRouteCardBinder` 與 `RouteResultSortButton`。常用頁目前把多個控制區固定在結果列表上方；搜尋頁則保留重複的起終點結果上下文。`item_bus_route.xml` 以單一多行 `TextView` 顯示站點預覽，底部導航選中時改變圖示量度，這些做法都會壓縮結果空間或造成版面跳動。

目前位置流程已由 `CurrentLocationCoordinator`、`GoogleReverseGeocodingPlaceNameResolver`、`LanguageSnapshot` 與 `MainActivity.resolveCurrentPlace` 提供完整的定位、Geocoding、timeout、cache 和語言一致性。`TransitCodePaymentLauncher` 已封裝 AlipayHK／支付寶候選鏈，`BusMonitorService` 已提供重新整理與停止通知 action。本次設計復用這些邊界，不增加外部服務或資料遷移。

## Goals / Non-Goals

**Goals:**

- 讓常用頁形成單一垂直捲動體系，僅固定排序與結果摘要。
- 在不改變站名區既有位置與總寬度的前提下，穩定展示單行起終點。
- 讓底部導航以固定量度持續表達選中狀態。
- 精簡搜尋輸入與結果層級，並以不可阻塞、可作廢的流程首次填入目前位置地址。
- 把乘車碼移出常用頁，改由系統快捷方式、設定管理入口及候車通知提供。
- 維持三語、深淺色、窄屏、大字體與無障礙契約。

**Non-Goals:**

- 不改變 Citybus、DATA.GOV.HK 或 Google Geocoding 接口與解析規則。
- 不改變 SQLite schema、已保存行程、排序語義、ETA 或通知監控 session。
- 不重設路線詳情／ETA 彈層、新增／編輯行程或乘車碼 provider 候選鏈。
- 不使用站名縮字、跑馬燈、整列橫向捲動或自行翻譯第三方站名。

## Decisions

### 1. 常用頁使用 AppBar 與可捲動結果形成單一捲動體系

`fragment_frequent_routes.xml` 會以 `CoordinatorLayout`、`AppBarLayout` 及帶 scrolling behavior 的 `SwipeRefreshLayout/RecyclerView` 組合畫面。常用行程標題、快捷卡和查詢按鈕位於帶 scroll flags 的 AppBar 區段；排序與結果摘要位於不帶 scroll flags 的區段，因此前者隨內容離場，後者吸頂。首次空狀態仍由頁面狀態切換控制，不偽裝成列表資料。

選擇此方案是因為它沿用 Android 巢狀捲動與 Insets 行為，並讓刷新手勢、列表回收和固定控制各自保持原有責任。否決把所有標題塞進 RecyclerView 多類型 item 的方案，因為會把頁面控制偽裝成查詢資料並擴大 adapter 狀態；亦否決以兩個獨立 ScrollView 手動同步，因為容易產生手勢競爭。

### 2. 站點預覽由專用 ViewGroup 與純寬度策略分配

在 `busRouteTextColumn` 原有站名位置內，以起點 `TextView`、固定箭頭和終點 `TextView` 取代單一 `busStopPreviewText`。新增純 Kotlin 寬度分配策略：內容總寬足夠時使用自然寬度；不足且僅一端較短時完整保留短端；兩端都長時把單端比例限制在 32% 至 68%。專用 `ViewGroup` 在量度階段套用策略，兩端維持 `maxLines=1` 與尾部省略。

純策略便於 JVM 測試，專用容器則能在取得實際可用像素後決策。否決固定 50/50，因為會無謂截斷短站名；否決在 binder 依字元數配置 weight，因為字元數不能可靠代表三語字形寬度；否決全卡寬站名，因為會改變現有 ETA 區與卡片層級。

### 3. 底部導航選中態不改變 item 量度

三個 destination 保持等寬，圖示固定 24dp。選中背景使用 64×32dp 膠囊，只包圍圖示；標籤容器預留 13sp Bold 所需空間，未選中使用 12sp Regular。狀態切換只做約 150ms 的顏色／透明度動畫，最終 drawable、文字樣式與語義色由目前 destination 持續驅動。

否決選中時把圖示放大至 28dp，因為會重新量度並擠壓標籤；否決只播放瞬時動畫，因為動畫結束後無法辨識目前頁面。

### 4. 搜尋保存行為由查詢快照推導

搜尋輸入區移除大標題與結果起終點上下文條；保存按鈕放入左側輸入欄，在兩個輸入框下方。`SearchFragment` 保存成功查詢時記錄起點、終點與 query generation 快照，僅當結果非空且目前選中地點仍等於快照時顯示按鈕。編輯、重新選擇、交換、發起新查詢、失敗或空結果立即清除資格。

此方案使保存入口與「這組起終點已查到可用路線」建立可測契約。否決只依 adapter 是否非空顯示，因為舊結果可能與新輸入不一致；否決在每張路線卡放保存，因為保存的是行程而非單一路線。

### 5. 首次目前位置沿用既有服務但使用獨立 generation

搜尋頁每個 `MainActivity` 實例最多自動嘗試一次；僅在沒有恢復起點、使用者文字或已提交查詢時啟動。定位和 Reverse Geocoding 仍由 `MainActivity.resolveCurrentPlace` 在背景執行，`SearchFragment` 只控制起點工具列的小型進度與 UI generation。使用者編輯／選擇起點、交換、離開頁面或語言 snapshot 改變時使舊 generation 作廢；終點操作不使起點定位作廢。

自動失敗只更新起點欄位的輔助文案，不彈 Toast 或跳設定；手動定位仍沿用既有恢復流程。成功回調必須同時通過 generation、生命週期與語言 snapshot 檢查，才可一次性提交地址名稱、原始經緯度和 attribution。否決「我的位置」特殊值與保存時才 Geocoding，因為會延後失敗並造成保存資料語義不明；否決停用輸入器，因為定位不是完成搜尋的必要阻塞步驟。

### 6. 所有乘車碼入口匯入同一明確 Intent action

定義單一 App 內 `OPEN_TRANSIT_CODE` action，由 `MainActivity` 在 `onCreate/onNewIntent` 消費並調用既有 `TransitCodePaymentLaunchAction`。靜態 App Shortcut、設定頁 pinned shortcut 與 `BusMonitorService` 通知 action 都建立相同 explicit intent。設定頁只負責透過 `ShortcutManagerCompat` 請求使用者固定 shortcut；通知 action 不停止或重建監控服務。

此方案只保留一份 provider 回退與錯誤提示。否決三個入口各自啟動 URI，因為容易讓候選順序、package visibility 和失敗處理漂移；否決在常用頁保留次級入口或 coachmark，因為仍會佔用核心結果空間。

### 7. 驗證分為純邏輯、契約及裝置層

寬度分配、搜尋結果資格與 generation 判斷以 JVM 單元測試覆蓋；XML、Manifest、shortcut metadata、通知 action 和多語言資源以 contract 測試覆蓋；既有 repository/parser 測試確認查詢行為未變。最終執行 `./gradlew build`，並在可用裝置上驗證三語、深淺色、360dp 與 font scale 1.0／1.3／2.0。

## Risks / Trade-offs

- [AppBar 與 SwipeRefreshLayout 產生巢狀手勢或 Insets 問題] → 使用 Material scrolling behavior，保留單一垂直捲動 owner，並以長列表與下拉刷新驗證。
- [站名可用寬度極小時兩端仍難以辨識] → 固定箭頭並以 32/68 邊界公平分配，完整名稱保留在 contentDescription 與詳情彈層。
- [大字體令標題列或底部導航高度增加] → 允許容器增高但不縮小核心文字，維持固定圖示和 48dp 觸控目標。
- [延遲定位結果覆蓋使用者輸入] → 起點互動、交換、頁面離開與語言變更均遞增 generation；回調提交前再次驗證。
- [Shortcut 在不同 launcher 上支援不一致] → 靜態 shortcut 維持可用；設定列先檢查 pinned shortcut 支援並提供本地化降級提示。
- [通知第三個 action 在窄裝置被系統收合] → 使用短標籤及既有圖示尺寸；系統即使收合 action 亦不影響監控核心功能。

## Migration Plan

1. 先加入共用站名寬度策略、乘車碼 intent action 與相容資源，再切換各入口。
2. 重構常用與搜尋 XML，同步更新 Fragment／Activity 綁定，避免保留失效 view id。
3. 更新三語資源、Manifest shortcut metadata、通知 action 與測試。
4. 執行 OpenSpec 驗證、完整 Gradle build 及可用裝置驗收。

本次沒有資料庫或偏好遷移。回退時可還原 UI 與 shortcut metadata；既有行程、查詢資料和監控 session 不受影響。

## Open Questions

無。產品行為、入口層級、失敗處理及驗收邊界均已確認。
