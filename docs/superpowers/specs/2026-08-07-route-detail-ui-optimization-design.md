# 路線詳情地圖、摘要與時間線 UI 優化設計

日期：2026-08-07
狀態：已確認

## 背景

目前路線詳情已具備 Google 地圖、摘要／半屏／全屏三檔 persistent bottom sheet、Citybus 站點時間線、分段道路 geometry、首程 ETA 與漸進載入。功能骨架完整，但地圖角色、方向提示、摘要資訊層級與時間線視覺仍不夠容易掃讀：

- 地圖上的起點、終點、上車、下車、同站換乘及普通站點主要依賴簡單幾何形狀，角色辨識成本高；
- 乘車與步行軌跡缺乏穩定、嚴格沿 geometry 局部方向排列的方向紋理；
- 普通站名完全不顯示，使用者難以把地圖位置與實際站點對上，但全部常駐又會造成多語言文字擁擠；
- 摘要卡片層級偏重，沒有形成可點擊的完整行動鏈；
- 半屏時間線仍有過多卡片、端點與重複資訊；
- 全屏態仍保留標題及返回按鈕，減少可用內容高度。

本設計在已完成的資料、並發、快取、相機與三檔 bottom sheet 基線上，只重做路線詳情的展示模型與 UI。它不改變 Citybus、DATA.GOV.HK 或 Google Maps 的資料權威邊界。

## 與既有設計的關係

本設計補充並在下列衝突範圍內覆蓋既有設計：

- `2026-08-02-route-detail-page-design.md`；
- `2026-08-03-route-detail-google-map-design.md`；
- `2026-08-06-route-detail-progressive-loading-design.md`。

衝突時以本設計為準的項目包括：

1. 地圖 marker 的確切角色圖形、站名常駐／避讓規則及方向紋理；
2. 地圖步行連接由灰色虛線改為只顯示粗灰色開放折角；
3. 摘要由帶邊框卡片改為三行無邊框資訊，並加入可點擊完整行動鏈；
4. 首程 ETA 不再出現在第一乘車段，只保留於摘要第三行；
5. 乘車段移除邊框與單段站數，單段票價改放於路線／方向同一行末端；
6. 全屏態移除詳情標題與屏內返回按鈕；
7. 地圖目的地使用經使用者確認的珊瑚紅地圖針，視為目的地角色色，不是錯誤狀態。

其餘站數公式、可靠結構門禁、進程快取、single-flight、reducer、generation、局部重試、相機所有權、定位、ETA 刷新與 MapView 生命週期契約保持不變。

實作前應建立獨立 OpenSpec change，不得把本次 UI 優化靜默併入其他尚未歸檔的 change。

## 目標

- 讓所有關鍵地圖角色在不讀文字時也能區分，並在三語環境維持一致語義。
- 讓巴士與步行軌跡以不漂移的方向紋理嚴格顯示前進方向。
- 在地圖上提供足夠站名，同時避免長英文、窄屏及密集站點造成文字牆。
- 把摘要精簡為「總時間 → 可點擊行動鏈 → 核心總計」三行。
- 讓半屏及全屏時間線更連續、輕量，去除重複 ETA、單段站數與卡片邊框。
- 全屏時最大化內容空間，保留既有手勢、系統返回與漸進載入可靠性。
- 保持現有 XML／AppCompat／Material 架構，使用純 Kotlin 展示模型承載規則。

## 非目標

- 不修改 Citybus、DATA.GOV.HK、Google Maps 的請求接口或資料來源。
- 不接入 Google Routes API，不提供真實沿街步行導航。
- 不新增巴士即時位置、乘車進度、下車提醒或導航操作列。
- 不修改行程保存、匯入匯出、路線排序、通知監控或 TTS 流程。
- 不替換地圖提供商，不引入 Advanced Marker 所需的新 Map ID 或其他雲端配置。
- 不重寫 progressive loading reducer、domain cache 或 single-flight 架構。
- 不為缺失 geometry、時間、距離、票價或顏色創造看似可靠的資料。

## 已確認的整體視覺語法

### 地圖角色

所有角色使用固定比例 VectorDrawable 或由 VectorDrawable 等比產生的 BitmapDescriptor。不得使用 Emoji、文字字形、拉伸位圖或遠端圖標。

| 角色 | 已確認圖形 | 顏色規則 |
|---|---|---|
| 查詢起點 | 地圖針，中心白色圓孔 | 綠色角色色 |
| 上車點 | 實心圓內白色巴士正面 | 目前乘車段顏色 |
| 下車點 | 空心圓環內 `log-out` 圖形 | 圓環及圖形使用目前乘車段顏色 |
| 同站換乘 | 雙色圓環內環形換向箭頭 | 半環使用前一乘車段色，另一半使用後一乘車段色 |
| 普通途經站 | 低強度小型中性圓點 | 中性灰，白色隔離邊緣 |
| 查詢終點 | 地圖針，中心白色圓孔 | 珊瑚紅目的地角色色 |

上車巴士圖形是 App 自有固定矢量資源。下車使用 Lucide `log-out` 官方 SVG 的原始比例，來源為 [`lucide-icons/lucide`](https://github.com/lucide-icons/lucide)，許可證為 ISC。實作需要：

- 把 SVG 路徑本地轉為 VectorDrawable，不載入網絡資產；
- 保留原始 viewBox 比例，禁止非等比縮放；
- 在隨 App 分發的第三方許可告知中保留 Lucide 版權及 ISC 許可文字。

同站換乘只有一個複合 marker，不疊放上一段下車與下一段上車 marker，也不繪製步行連接。不同站換乘則保留上一下車、下一上車與其間步行連接。

### 地圖站名

起點、終點、上車、下車及換乘是關鍵角色，站名始終嘗試顯示。普通途經站只在縮放、可見空間及碰撞條件允許時顯示；放大或選中普通站後保證可取得完整名稱。

站名位置不固定於單一方向。renderer 在 camera idle 後使用目前 projection 估算文字邊界，依序評估：

1. 右側；
2. 左側；
3. 上方；
4. 下方。

每個候選按以下衝突評分：

- 是否超出可見地圖範圍或被 bottom sheet／系統 inset 遮擋；
- 是否壓住 marker、巴士／步行軌跡或既有關鍵標籤；
- 是否與其他普通站名碰撞；
- 是否位於地圖邊緣外側。

優先級為：起點／終點、上車／下車／換乘、選中普通站、其他普通站。關鍵站名之間無完全無碰撞位置時，選擇衝突最少的位置並使用地圖背景可讀的文字 halo；普通站名必須為關鍵站名讓位。舊位置仍有效時保持舊位置，避免平移或縮放後文字反覆跳邊。

標籤與 marker 保持約 6dp 視覺間距，單行限寬及省略；完整名稱保留在 marker 互動、時間線及無障礙描述中。Citybus 站名使用目前 `LanguageSnapshot` 對應原文，不自行翻譯或在語言切換時改寫資料。

### 地圖軌跡與方向紋理

巴士段維持帶對比白色描邊的分段色實線。實線上稀疏重複稍粗的白色開放折角，沒有箭桿、實心三角或字體 glyph。

起點步行、不同站換乘步行與終點步行不再繪製灰色虛線或灰色底線；只以一組較粗的灰色開放折角沿有序連接排列。同站換乘不繪製任何步行紋理。

方向精度是不可降級的硬約束：

- 折角必須綁定與軌跡相同的有序 polyline geometry；
- 每個折角的旋轉由其所在位置的局部切線決定；
- 不得使用整段起終點 bearing、獨立 Marker、手工角度或固定方向 bitmap；
- 彎道中的每個折角都要隨局部曲線轉向；
- geometry 順序反轉時，全部折角必須同步反轉；
- 相機縮放、padding 更新及增量 renderer 不得讓折角相對軌跡漂移。

Google Maps renderer 優先以 `StrokeStyle`／[`StampStyle`](https://developers.google.com/maps/documentation/android-sdk/reference/com/google/android/libraries/maps/model/StampStyle)／`SpriteStyle` 把透明背景的開放折角 stamp 綁在 polyline 上，由 SDK 沿線排布與定向。步行使用透明承載 stroke 與灰色折角 stamp；巴士使用彩色 stroke 與白色折角 stamp。不得以手動旋轉 Marker 作為正式回退。

正式串接頁面前，先以固定 S 彎及反向 geometry 做最小裝置實驗，驗證目前 Maps SDK 版本能同時滿足：透明步行承載 stroke、只顯示開放折角、穩定間距及局部切線定向。若 SDK 無法達成，應停止實作並回到設計評估其他 polyline 內建樣式；不得在完整頁面中偷偷改用手工 Marker。

若執行期某段 stamp 無法正確建立，保留可靠巴士實線或省略步行方向紋理並記錄安全診斷；不顯示方向錯誤的折角。實作驗收若仍存在漂移，視為功能未完成，不能以「接近」通過。

## 路線摘要

摘要移除 MaterialCard 邊框，直接使用 bottom sheet 背景。內容固定為三個層級：

### 第一行：總耗時與預計到達

- 先以主層級顯示總耗時；
- 緊接中性次要文字顯示 Citybus 預計到達時間；
- 不把首程即時 ETA 放入第一行。

範例：

```text
全程 49 分鐘   預計 01:21 到達
```

### 第二行：可點擊完整行動鏈

順序必須完整保留：

```text
起點步行 → 第一乘車段 → 換乘步行／同站換乘 → 後續乘車段 → 終點步行
```

視覺規則：

- 起點、換乘及終點步行全部使用相同中性灰底；
- 步行使用現有真實 `ic_walking_person` VectorDrawable，不重畫、不變形；
- 乘車段使用目前分段色，只顯示必要路線號；
- 不在段塊之間顯示箭頭；
- 每段按內容包裹寬度，相鄰段只留約 2dp；
- 可見底色直接貼合約 18dp 圖標／路線內容，總高度約 22dp，上下各只保留約 2dp；
- 路線號或步行人物與小號耗時共用底部基線；
- 耗時字級小於路線號，不另開一行或下沉至獨立角落；
- 內容超過可用寬度時維持單行水平捲動，不換行、不拉伸為等寬、不壓縮圖標；
- 可見高度雖為約 22dp，透明外層／TouchDelegate 的互動高度至少 48dp；擴張範圍不得與另一個可點擊控制重疊或改變 TalkBack 的實際行動順序。

每段擁有穩定 `detailTargetId`。點擊後：

1. bottom sheet 進入可閱讀的全屏狀態；
2. RecyclerView 捲動到對應步行或乘車段；
3. 目標段短暫使用低強度背景高亮；
4. TalkBack 焦點移至目標標題並朗讀；
5. 若詳情尚未到達，保存 pending target，在相同 generation 的 item 出現後完成捲動；
6. 若該資料域最終失敗，清除 pending target 並朗讀本地化不可用狀態。

每段耗時只由本次新鮮 Citybus 計劃時間邊界計算，不能從 24 小時結構快取取得。formatter 需要處理跨午夜；任一邊界缺失或不可靠時，段塊仍存在並可點擊，但不顯示耗時，不以距離估算。

### 第三行：核心總計

依序顯示：

1. 總乘坐站數；
2. 總步行距離；
3. 總票價；
4. 首程候車時間／結構化 ETA 狀態。

總乘坐站數沿用 `2026-08-06-route-detail-progressive-loading-design.md`：

```text
rideStopCount = Σ（leg.viaStops.size + 1）
```

並保留 `Loading`、`Available(count)`、`Unavailable` 三種狀態，不回退為誤導性的 `0`。常規 360dp／100% 字體目標為單行；窄屏或大字體允許自然換行，不能縮小文字或省略核心狀態。

首程 ETA 只在第三行顯示，不再重複進入第一乘車段。ETA 暫無班次、刷新中、最近成功值、技術失敗等狀態繼續沿用現有結構化語義。

## 半屏與全屏時間線

### 縱向軌跡

- 整體起點使用白環內綠色圓心；
- 整體終點使用白環內珊瑚紅圓心；
- 步行段使用輕量中性灰點線；
- 巴士段使用分段色連續實線；
- 巴士實線在上車與下車位置不放大節點或額外空心圓；
- 普通站名可沿巴士段自然列出，但不加入新的巢狀卡片；
- 同站換乘以文字及下一乘車段開始表達，不偽造步行距離。

這裡的灰色點線只屬於垂直時間線，不等同地圖步行軌跡；地圖仍使用已確認的粗灰色開放折角。

### 乘車段內容

每段移除外框與卡片底色，由縱向軌跡、留白和文字層級分組。保留：

- 上車站及計劃時間；
- 路線號；
- 方向；
- 中途站名；
- 下車站及計劃時間；
- 多段路線時的單段票價。

移除：

- 第一乘車段的首程即時 ETA；
- 單段乘坐／途經站數；
- 乘車段外邊框。

多段路線的單段票價放在「路線號／方向」同一行末端。單段路線不重複顯示單段票價，因摘要第三行已有總票價。單段票價缺失時整體隱藏，不顯示破折號或估算值。

### Bottom sheet 檔位與返回

- 摘要態與半屏態保留地圖左上角既有懸浮返回按鈕；
- 全屏態隱藏地圖與懸浮返回按鈕；
- 全屏態不顯示「路線詳情」標題、Toolbar 或任何屏內返回按鈕；
- 全屏內容在狀態列安全區與拖動把手之後直接鋪滿；
- 向下拖動可回到半屏；
- Android 系統返回手勢／按鍵在任何檔位直接退出路線詳情，不先逐檔收合。

## 展示模型與元件責任

### Repository／domain model

repository 與 parser 繼續負責外部資料及可靠性：

- 保留站序、角色、坐標、路線方向、計劃時間、票價、步行段及同站／不同站換乘語義；
- 保持結構完整性門禁、快取分域、single-flight、generation 與局部失敗規則；
- 不讓 Google Maps SDK、View 或本地化展示文字進入資料層；
- 不新增任何只為視覺效果而發出的網絡請求。

24 小時 `RouteStructureCache` 仍不得保存易變計劃時間或單段票價。摘要分段耗時及單段票價只在新鮮 dynamic detail 到達後原位補入；刷新或失敗不能清空已驗證結構。

### `RouteMapPresentationBuilder`

輸出純 Kotlin 地圖展示模型：

- stable marker id、角色、坐標、目前語言站名；
- 前後乘車段顏色與 label priority；
- 有序 geometry、路徑種類及方向 stamp 種類；
- marker 與時間線 stable target 的對應；
- 選中／高亮狀態。

展示模型只描述語義及優先級，不直接包含 Google `Marker`、`Polyline`、`BitmapDescriptor` 或螢幕像素座標。

### `GoogleRouteMapRenderer`

負責：

- 把純展示模型差量套用到 GoogleMap；
- 產生及快取固定比例 marker bitmap；
- 以 camera projection 計算標籤候選、碰撞及穩定位置；
- 使用 polyline stamp/style span 繪製嚴格沿局部切線的方向折角；
- 以 stable id 復用 marker、label 與 polyline，避免 ETA 或其他 slice 更新造成閃爍；
- 在 camera idle 後才重排 label，不觸發資料重新取得或 camera reset。

### `RouteDetailUiFormatter`

統一輸出：

- 三行摘要；
- `RouteSummarySegmentPresentation`：stable id、detail target、種類、路線號、顏色及可空耗時；
- `RouteDetailLegPresentation`：站點、方向、計劃時間、可空票價，不含首程 ETA 及單段站數；
- 跨午夜時間計算及缺失值回退；
- 三語資源所需參數，不硬編碼可見文案。

### `RouteDetailAdapter`／`RouteTimelineRailView`

Adapter 只渲染展示模型及發出語義 callback，不重新計算票價、時間、站數或 map role。Timeline rail view 只繪製整體起終點、灰色步行點線及連續分段色巴士實線，不持有 repository 或異步工作。

### `RouteDetailActivity`

Activity 只協調：

- 現有 reducer state 與 renderer／adapter；
- bottom sheet 檔位及全屏 Toolbar／返回可見性；
- 摘要 segment click 的 pending target、展開、捲動、高亮與焦點；
- MapView、WindowInsets 與既有生命週期。

不得把 label collision、時間計算、站數公式、外部 parser 或 bitmap 幾何生成散落在 Activity。

## 失敗與降級

| 情況 | 行為 |
|---|---|
| 某段 geometry 缺失或未通過端點驗證 | 不繪製該段軌跡及 stamp；保留時間線與其他可靠線段，不以站點直線冒充道路 |
| stamp 建立失敗或無法正確貼合 | 巴士保留實線、步行省略方向紋理；記錄安全診斷，不顯示手工旋轉的錯誤折角 |
| 個別站點坐標非法 | 只省略對應地圖點；時間線及完整站名仍可使用 |
| 關鍵站名無完全無碰撞位置 | 隱藏普通站名，關鍵站名選最少衝突位置並加可讀 halo |
| 分段計劃時間缺失 | 保留可點擊段塊，隱藏耗時，不按距離估算 |
| 單段票價缺失 | 隱藏該票價，不顯示佔位符；總票價可用時仍保留 |
| 路線色缺失 | 使用現有確定性回退色，不隨機產生 |
| 同站換乘一側顏色缺失 | 使用可用段色及現有回退色組成雙色環 |
| 詳情尚未載入時點擊摘要段 | 保存同 generation pending target；item 到達後完成捲動 |
| 目標詳情最終失敗 | 清除 pending target，保留摘要並朗讀本地化不可用狀態 |

局部 UI 失敗不得切換整頁為單一 Error，也不得清除 Map、ETA、可靠結構、其他 geometry 或使用者已選中狀態。

## 多語言與無障礙

- App 自有文案、content description、載入／缺失狀態及 TalkBack announcement 同時提供香港繁體、獨立簡體與自然英文；
- Citybus 站名、路線號、方向及使用者地點名稱保持目前語言來源原文；
- marker 以形狀、填充／空心、圖形及文字共同區分，不只依賴顏色；
- marker content description 至少包含角色、完整站名及相關路線號；
- 地圖不是唯一資訊來源，所有角色及站名必須在時間線完整可讀；
- 摘要段塊至少提供 48dp 互動範圍，TalkBack 按實際行動順序遍歷；
- 捲動到目標後把無障礙焦點移至目標標題；
- font scale 2.0 不縮字，第三行及詳情允許自然換行；
- 站名地圖標籤可單行省略，但不能讓省略文字成為唯一可取得名稱；
- 淺色／深色模式均需驗證 marker 白色隔離邊緣、路線文字及灰色折角對比度。

## 狀態與資料流

```text
Citybus detail／geometry／ETA events
                │
                ▼
RouteDetailPageReducer（現有 generation／品質單調契約）
                │
                ├── RouteMapPresentationBuilder
                │       └── GoogleRouteMapRenderer
                │
                └── RouteDetailUiFormatter
                        ├── RouteDetailAdapter
                        └── RouteTimelineRailView

摘要 segment click
        └── Activity 保存 stable target → 展開 full → scroll／highlight／focus
```

ETA、detail dynamic 補充、geometry 或 camera 更新只重繪受影響 slice。未變的 marker、label、polyline、RecyclerView stable item、展開狀態、list position 及選中狀態不得被重建或重置。

## 驗證

### 純邏輯測試

- 地圖角色：單段、多段、同站換乘、不同站換乘、起終點快照缺失；
- 同站換乘正確合併角色並取得前後段顏色；
- label priority、候選順序、邊界懲罰、碰撞評分及舊位置穩定策略；
- 摘要行動鏈包含起點步行、所有乘車段、所有不同站換乘步行及終點步行；
- summary segment stable target 在局部 dynamic detail 更新後不改變；
- 計劃時間完整、缺失、跨午夜、逆序／不可靠時的可空耗時；
- 單段與多段票價展示規則；
- 站數 `Loading`／`Available`／`Unavailable` 與既有正式公式；
- pending target 在成功、失敗、generation 改變及退出後的處理；
- stale event 不改變已顯示 UI，局部重試不清除其他 slice。

### 地圖方向驗收

以固定合成 geometry 覆蓋直線、S 彎、急彎及反向路徑：

- 巴士白色折角中心落在彩色軌跡上；
- 步行粗灰色折角沿連接 geometry 排列，沒有灰色底線或虛線；
- 每個折角朝向和所在位置局部切線一致；
- geometry 反轉時所有折角同步反轉；
- camera zoom、bottom sheet padding、增量更新後不漂移；
- 不出現箭桿、實心三角、字體 glyph 或手工角度 fallback。

任何可見漂移均為驗收失敗。

### UI／Instrumentation 驗收

- 360dp 窄屏上的 22dp 貼合內容摘要段塊、2dp 段間距及水平捲動；
- 點擊每個摘要段精確展開、捲動、高亮及 TalkBack 聚焦；
- 半屏起終點圓心、灰色步行點線、連續巴士實線及無端點放大節點；
- 多段路線票價位於路線／方向行末，單段路線不重複顯示；
- 第一乘車段沒有首程 ETA，所有乘車段沒有單段站數及外邊框；
- 摘要／半屏保留懸浮返回，全屏沒有標題及屏內返回；
- 全屏下拉回半屏，系統返回直接退出；
- 詳情、geometry、ETA、票價及時間的局部缺失／重試不清空可靠內容；
- 香港繁體、簡體、英文；淺色、深色；font scale 1.0、1.3、2.0；長站名；
- TalkBack 閱讀順序、48dp 觸控目標、target announcement 及隱藏地圖標籤的時間線替代。

### 構建與裝置

- 定向單元／UI 測試先行；
- 實作完成後執行 `./gradlew build`；
- 使用屆時實作任務自行啟動且符合 360dp、Google Maps、目標 API、語言、主題與 font scale 的 AVD；
- 不操作、停止或重啟任務開始前已運行的模擬器；
- 驗證完成後關閉本任務啟動的全部模擬器；
- 最少對單段、多段、同站換乘、不同站步行換乘各做一次真實 Citybus／Maps 抽查，並以非敏感 fixture 保留可重現回歸。

## 完成標準

- 使用者可在地圖上不依賴顏色或中文「上／下」字辨識所有關鍵角色；
- 關鍵站名常駐且普通站名不形成文字牆，鏡頭停止後標籤位置穩定；
- 巴士及步行折角在所有彎道與反向 geometry 上嚴格沿局部切線，沒有漂移；
- 摘要無外框並形成可點擊完整行動鏈，貼合內容高度不再保留頂部多餘空白；
- 第三行清楚呈現站數、步行、總票價與候車狀態；
- 半屏時間線只突出整體起終點，巴士實線不放大上下車端點；
- 乘車段無外框、首程 ETA 與單段站數不重複，票價位置一致；
- 全屏內容沒有標題及屏內返回按鈕，手勢與系統返回契約正確；
- 所有缺失與局部失敗只降級受影響內容，不偽造資料、不清除可靠內容；
- 三語、明暗模式、窄屏、大字體、TalkBack、完整 build 及任務自有裝置驗證全部通過；
- 既有漸進載入、可靠快取、站數、相機、ETA、定位、路線結果、排序及通知監控沒有無關回歸。
