## 1. 固化資料契約與回歸樣本

- [x] 1.1 盤點 N118 與 N8P → N969 `getp2pstopinroute.php` fixture 中的步行列、換乘標記、`showtimetable1(...)` 分段票價／預計時間欄位及 route variant 對齊規則，將欄位證據記錄到 parser 測試名稱與斷言。
- [x] 1.2 新增同站換乘 fixture，以及步行距離、分段票價或預計時間部分缺失的 fixture，保留第三方原始內容並避免以 mock 取代生產 HTTP 路徑。
- [x] 1.3 為單段、步行換乘、同站換乘與部分缺失樣本先加入失敗的 parser／formatter 測試，覆蓋完整步行 403 米、卡片 378 米保持、總途經站 14 站與缺失欄位降級。

## 2. 擴充詳情領域模型、解析與快取

- [x] 2.1 在 `data/model` 新增可空距離的起點／換乘／終點步行段、換乘類型、分段票價及預計上下車／到達時間模型，並提供完整步行合計與完整性狀態。
- [x] 2.2 擴充 `CitybusRouteDetailParser`，按 `rawInfo` leg 順序及 route variant 解析步行列、前往轉車站／同站換乘、分段票價及預計時間；可選欄位失敗時保留站點主結構。
- [x] 2.3 擴充 `CitybusRouteDetailRepository` 與 `RouteDetailCache` 保存新的結構化欄位，維持 `rawInfo + lang`、1 天期限、失敗不快取及現有無靜態瀏覽器 header 契約。
- [x] 2.4 補齊模型、parser、repository 與 cache 單元測試，確認三種 Citybus 語言隔離、過期快取、部分資料及錯誤回應行為。
- [x] 2.5 以 10 個有效 Citybus 詳情樣本執行可重現 live 請求，記錄 HTTP 200、可解析標記及清除 header 前後的業務簽名／body hash 一致性。

## 3. 完成路線卡片矢量圖示

- [x] 3.1 新增已確認的 18dp 細線鬧鐘 VectorDrawable，以及由四條獨立填充 path 組成的高保真步行人物 VectorDrawable；不得打包低解像度參考圖。
- [x] 3.2 調整 `item_bus_route.xml` 與卡片 formatter／binding，展示價格、鬧鐘＋耗時、步行人物＋卡片距離，並使用模式感知次要文字 tint。
- [x] 3.3 新增或更新卡片 formatter／Adapter 測試，確認 `walkingDistanceMeters`、步行排序、卡片高度與 ETA／通知點擊區保持既有語義。
- [x] 3.4 為卡片輔助指標提供合併 TalkBack 文案，驗證圖示不重複朗讀且三語均能讀出完整耗時與步行單位。

## 4. 建立全屏詳情頁與可恢復啟動契約

- [x] 4.1 定義只包含 primitive／Bundle 欄位的詳情啟動參數，涵蓋路線摘要、`P2pRouteDetailQuery`、`FirstLegEtaQuery` 與目前候車 snapshot，並加入 process recreation round-trip 測試。
- [x] 4.2 新增 `RouteDetailActivity`、Manifest 宣告、固定 App Bar、單一 RecyclerView、WindowInsets、載入／內容／失敗 UI state 及返回操作，不顯示空白地圖佔位。
- [x] 4.3 建立詳情 UI formatter／item model，將摘要、步行段、乘車段、途經站控制、展開站點、換乘及終點映射為具有 stable identity 的扁平列表。
- [x] 4.4 實作背景詳情載入、1 天快取命中、重試 generation、生命週期取消／忽略及部分欄位降級，確保舊語言或舊重試結果不覆蓋目前頁面。
- [x] 4.5 加入 Activity instrumentation 測試，覆蓋進入即顯示摘要、載入成功、缺少詳情元數據、請求失敗、重試、返回與 configuration change。

## 5. 實作摘要與完整行動時間線

- [x] 5.1 實作摘要 item，展示路線鏈、總耗時、預計到達、總票價、途經站總和，以及高保真步行人物＋完整步行合計或卡片回退值。
- [x] 5.2 實作模式感知分段調色板與乘車時間線 item，使相鄰乘車段使用不同粗實線顏色，並讓路線牌、節點與預留地圖色 key 保持一致。
- [x] 5.3 實作起點、換乘及終點步行 item，使用中性細虛線、步行人物與可選距離；同站換乘只顯示文字節點且不顯示虛假距離。
- [x] 5.4 實作乘車卡片，只承載路線號、方向、首程候車狀態、分段票價與站數；預計上下車時間以中性 `預計 HH:mm` 顯示。
- [x] 5.5 實作卡片外 `N 個途經站` 控制行、18dp 矢量 Chevron、180° 旋轉及至少 48dp 觸控區，展開後把全部站名與小圓點插入主時間線。
- [x] 5.6 保存各乘車段獨立展開狀態並以局部 diff 維持視口，加入預設折疊、逐段展開／收起、無途經站及旋轉恢復測試。

## 6. 整合首程即時 ETA

- [x] 6.1 由啟動參數立即顯示來源卡片最新 `WaitTimeState`，把可用值格式化為品牌色 `即時 · 還有 N 分鐘`，並與中性 Citybus 預計時刻並列但不混用。
- [x] 6.2 在存在完整 `FirstLegEtaQuery` 時復用 `CitybusFirstLegEtaService` 背景刷新一次，加入生命週期與 `LanguageSnapshot` generation 檢查，只更新首個乘車段。
- [x] 6.3 覆蓋 `Available`、`NoArrivals`、各類 `Unavailable`、刷新過期與後續乘車段無即時 ETA 的單元／instrumentation 測試。

## 7. 切換入口並移除 Bottom Sheet

- [x] 7.1 將 `MainActivity` 與 `SearchFragment` 的共用卡片詳情入口切換為 `RouteDetailActivity`，保持 ETA 文字欄、通知鈴鐺與卡片其他點擊熱區語義。
- [x] 7.2 驗證從常用與搜尋結果進入／返回後，原查詢結果、排序、置頂分隔、捲動位置及行程使用次數保持不變。
- [x] 7.3 移除不再使用的 `RouteDetailBottomSheet`、Bottom Sheet 關閉／拖動與巢狀捲動資源及測試，保留 repository、parser 與可復用 formatter 邊界。

## 8. 三語、主題與無障礙驗收

- [x] 8.1 為所有新增標題、摘要、預計／即時來源、步行／換乘、站數、展開、載入、錯誤與重試文案提供香港繁體、獨立簡體及自然英文資源，禁止 XML／Kotlin 硬編碼 App 文案。
- [x] 8.2 為分段調色板、表面、文字、虛線、節點及圖示建立淺／深色語意資源，驗證路線牌文字對比且顏色不是唯一資訊通道。
- [x] 8.3 補齊 TalkBack 語意與可點擊狀態，確認摘要、步行、乘車、預計／即時來源及途經站展開狀態可理解，裝飾線與圓點不重複朗讀。
- [x] 8.4 在繁體／簡體／英文 × 淺／深色下驗證 360dp、font scale 1.0／1.3／2.0、長站名、長方向、多段換乘、完整／缺失資料、逐段展開與終點可捲達。

## 9. 最終驗證與交付

- [x] 9.1 執行相關 parser、repository、formatter 與 instrumentation 測試，修正所有回歸並保存可重現失敗證據。
- [x] 9.2 執行 `./gradlew build`，確認 Kotlin 編譯、unit tests、lint 及 debug／release assemble 全部通過。
- [x] 9.3 使用本任務自行啟動的模擬器或實機完成真實 Citybus／DATA.GOV.HK 視覺與互動驗證，記錄未完成項並關閉本任務啟動的模擬器；不得接管其他任務已開啟的 AVD。
- [x] 9.4 檢查 `git status --short`、任務勾選、OpenSpec 規格一致性與提交範圍，依專案 `/opsx-apply` 規則提交已驗證實作。
