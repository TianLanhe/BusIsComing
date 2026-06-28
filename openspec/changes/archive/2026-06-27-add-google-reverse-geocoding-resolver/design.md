## Context

目前位置起點流程已具備定位權限處理、系統定位開關引導、3 秒定位超時、30 秒定位 snapshot cache、同時定位請求合併、generation 過期保護，以及自動／手動失敗 UX。名稱解析仍由 `MockPlaceNameResolver` 同步返回固定 `目前位置附近`，因此新增路線與臨時查詢雖使用真實 GPS 座標，畫面與保存資料仍缺少真實地址。

Google Geocoding API v4 reverse geocoding 文檔定義了 `https://geocode.googleapis.com/v4/geocode/location/{lat},{lng}` GET 形式，支持 API key header、`languageCode`、`regionCode`、`types`、`granularity` 以及 `X-Goog-FieldMask`。本 change 使用 v4 response 欄位，例如 `formattedAddress`、`types`、`addressComponents.longText`，不兼容 legacy v3 欄位。

## Goals / Non-Goals

**Goals:**

- 用 Google reverse geocoding 產生目前位置 `Place.name`，並保留當次目前位置 snapshot 的原始 GPS latitude／longitude。
- 維持純新增頁與臨時查詢的既有自動目前位置起點行為，並讓複製／編輯頁靜默預熱位置與地址名稱但不覆蓋既有起點。
- 使用單次 Google 請求與客戶端地址選取策略，優先 `street_address`，不拼接多個 results。
- 以 `GOOGLE_GEOCODING_API_KEY` 從 ignored 本機設定或環境注入 key，避免提交密鑰。
- 生產預設 `languageCode=zh-Hant`、`regionCode=HK`，並在 resolver 層預留 language provider。
- 新增 Google 地址名稱進程內 cache，與既有定位 snapshot cache 分離，並合併同 key in-flight 請求。
- 當 Google 地址實際顯示在起點輸入框上下文時，顯示 `地址由 Google Maps 提供` attribution。
- 以 JVM 單測覆蓋 API 請求、解析、cache、錯誤和 UI contract；以 opt-in 模擬器 connected test 驗證真實 API 主路徑。

**Non-Goals:**

- 不導入完整 App 多語言文案改造，也不提供用戶手動切換地址語言的 UI。
- 不使用 Android `Geocoder`、香港政府 geocoding、Google Places SDK、離線地址庫或機器翻譯。
- 不把 Google reverse geocoding 接入 Citybus 地點搜尋、終點搜尋、候選排序、路線結果或通用地圖搜尋。
- 不改變 Citybus 查詢語言、保存資料 schema、最近路線選擇、候選距離展示、焦點／鍵盤／候選搜尋等既有交互。
- 不把 attribution 來源寫入 `Place`、`RouteConfig` 或 SQLite，也不在管理列表、主頁卡片、保存彈窗或路線結果中展示地點來源。
- 不做持久化地址 cache、批量 reverse geocoding、背景掃描既有路線或自動重試。
- 不在源碼、OpenSpec、測試 fixture、日誌或提交中保存實際 Google API key。

## Decisions

### Decision 1: 客戶端直連 Google v4，作為階段性方案

做法：App 直接以 HTTPS GET 調用 Google Geocoding API v4 reverse geocoding，API key 通過 ignored `local.properties` 或環境變數 `GOOGLE_GEOCODING_API_KEY` 注入到 `BuildConfig.GOOGLE_GEOCODING_API_KEY`。請求除 `X-Goog-Api-Key` 和 `X-Goog-FieldMask` 外，還帶 `X-Android-Package` 與 `X-Android-Cert`，用於 Android app-restricted key 的 REST 直連身份驗證。缺少 key、package name 或簽名憑證 SHA-1 時 resolver 不發網路請求，直接返回名稱解析失敗，debug log 只記錄失敗類別。

原因：App 目前沒有後端；本 change 目標是替換 current-place mock resolver。客戶端直連能最小化架構變更並支援本地模擬器驗收。

替代方案：先建立後端 proxy。否決原因是會新增服務端部署、鑑權、監控和成本模型，超出本期 resolver 替換範圍。需要正式大規模分發或集中控管密鑰時再另開 change。

### Decision 2: 使用 `HttpURLConnection`、小型背景 executor 和可注入 fetcher

做法：新增 `GoogleReverseGeocodingPlaceNameResolver` 或等效類，沿用專案現有 callback/thread 風格，不引入 coroutine。網路請求在小型背景 executor 上執行，callback 回主線程後由 Activity/BottomSheet 依 generation 狀態更新 UI。resolver 提供 fake fetcher／clock／language provider 便於單測。

原因：專案現有 Citybus repository 已採用 `HttpURLConnection` 和 fetcher 注入。引入 Retrofit、OkHttp、coroutine 或 Google SDK 會增加依賴與審查面。

替代方案：在 Activity 內直接開 thread 呼叫 Google。否決原因是會把 HTTP、JSON 解析、cache 與錯誤分類散落到 UI 層。

### Decision 3: 使用 `org.json` 解析 v4 嵌套 response

做法：Google parser 使用 `org.json.JSONObject`／`JSONArray` 解析 `results[]`、`types[]`、`addressComponents[]`。JVM 單測新增 `testImplementation` 的 `org.json:json`，避免本地測試依賴 Android stub。Citybus 既有輕量 JSON 解析不在本 change 中重寫。

原因：Google response 是嵌套 JSON，正則解析會脆弱且難覆蓋 `types` 和 fallback。`org.json` 對本場景足夠，且不需要新增生產 runtime 大依賴。

替代方案：Moshi 或 kotlinx.serialization。否決原因是對單一小型 response parser 偏重，並會擴大 Gradle 配置。

### Decision 4: 固定繁體請求語言，保留多語言擴展點

做法：生產預設 `languageCode=zh-Hant`，固定 `regionCode=HK`。resolver 接收 `languageCodeProvider` 或等效配置，單測覆蓋 `en` 等非預設語言的 request，但 UI 不新增語言設定，也不跟隨系統 locale 自動切換。

原因：目前 App 文案與香港場景以繁體中文為主。直接跟隨系統 locale 可能讓繁體 App 中出現英文地址，短期體驗不穩。保留 provider 能在未來多語言改造時接入 App locale。

替代方案：立即跟隨系統 locale。否決原因是多語言需要 App 文案、Citybus 語言和 Google 地址語言一起設計。

### Decision 5: 單次請求，不用 `types`／`granularity` 過濾

做法：Google 請求不加 `types=street_address` 或 `granularity`。FieldMask 僅請求 `results.formattedAddress,results.types,results.addressComponents.longText,results.addressComponents.types`。經緯度以 `Locale.US` 格式化到 6 位小數放入 v4 path；API key 使用 `X-Goog-Api-Key` header，FieldMask 使用 `X-Goog-FieldMask` header，Android 身份使用 `X-Android-Package` 與 `X-Android-Cert` header。

原因：Google `types`／`granularity` 是過濾，不是排序偏好；若服務端過濾後為空，還要二次請求才能 fallback，增加延遲、成本與失敗面。單次請求後客戶端選取可同時滿足 `street_address` 優先與可靠 fallback。

替代方案：請求加 `types=street_address`，失敗再不帶 types 重試。否決原因是多一次真實 API 成本與 5 秒整體超時衝突。

### Decision 6: 客戶端地址選取規則明確化

做法：parser 不拼接多個 results。地址選取順序為：第一輪取第一個 `types` 包含 `street_address` 且 `formattedAddress` 非空、非 plus code 的 result；第二輪取第一個 `formattedAddress` 非空、非 plus code，且 types 不全屬於過粗類別的 result；第三輪取第一個非空、非 plus code 的 `formattedAddress`；最後才從單一 result 的 `addressComponents.longText` 依 response 順序去重後組合最多 4 段。過粗類別為 `country`、`administrative_area_level_1`、`administrative_area_level_2`、`locality`、`political`，只有 types 全部落在該集合時才視為過粗。

原因：Google 通常按精確度排序，但用戶明確希望優先 `street_address`。同時避免只顯示「香港」這類過粗地址；若服務真的只返回粗粒度地址，第三輪仍可兜底而不是直接失敗。

替代方案：永遠取第一個 result 或拼接多個 `formattedAddress`。否決原因是第一個 result 不一定是 street address；拼接會產生冗長且重複的地址。

### Decision 7: 保持 `Place` 模型不變，attribution 走 UI 狀態

做法：`Place` 仍只有 `name`、`latitude`、`longitude`。Google 成功結果通過 `CurrentPlaceSelectionResult.Success` 或等效 metadata 攜帶 `PlaceAttribution.GOOGLE_MAPS` 給當前輸入 UI。Route edit 和 temporary sheet 在起點輸入框下方新增獨立小字 `地址由 Google Maps 提供`，不使用 `TextInputLayout.helperText` 承載 attribution。

原因：attribution 描述當前輸入框內容來源，不是 `Place` 持久化資料。獨立小字避免和「沒有匹配地點」「暫時無法取得目前位置」等 helper/error 相互覆蓋。

替代方案：擴展 `Place.source` 並遷移 SQLite。否決原因是用戶已確認除輸入框上下文外不展示地點來源，本期不需要持久化來源。

### Decision 8: 兩層 cache 分離，Google 地址 cache 支持 in-flight 合併

做法：保留 `CurrentLocationCoordinator` 既有 30 秒 location snapshot cache。新增 Google 地址名稱 cache，key 使用 `round(latitude * 10000)`、`round(longitude * 10000)` 和 `languageCode`，value 只保存 `addressName` 與 `cachedAtMillis`，TTL 10 分鐘，只缓存成功結果，不持久化，不缓存失敗。同 key cache miss 且已有 in-flight Google 請求時，後續 callback 加入等待列表，避免複製／編輯頁預熱與用戶點擊重複打 API。

原因：位置 cache 與地址 cache 生命週期和語義不同。4 位小數約 10 米量級，能降低定位抖動導致的 cache miss，又不至於把相鄰街道大範圍混用。in-flight 合併能覆蓋預熱尚未完成時用戶立即點擊定位按鈕的高概率場景。

替代方案：不做地址 cache 或把地址 cache 放入 location coordinator。否決原因是前者會增加重複 API 調用；後者會混淆「定位」與「地址解析」責任。

### Decision 9: 複製／編輯頁靜默預熱位置與地址名稱

做法：複製／編輯頁打開時，若已具備前台定位權限且系統定位已開啟，取得或復用目前位置 snapshot，更新候選距離，並調 Google resolver 預熱地址名稱 cache。預熱不請求權限、不跳系統設定、不填入起點、不顯示 attribution、不顯示 helper/toast；每個頁面會話最多自動預熱一次。純新增頁和臨時查詢不做額外靜默預熱，因為它們本身會自動填目前位置起點。

原因：複製／編輯頁不能自動覆蓋已有起點，但用戶可能隨後點擊定位按鈕。靜默預熱能降低等待時間，同時不改變既有可見行為。

替代方案：複製／編輯頁只預取位置、不預熱 Google 地址。否決原因是用戶明確希望提前取得地址名稱但不填入。

### Decision 10: 超時、遲到結果和失敗 UX 沿用目前位置流程

做法：定位階段最多 3 秒，Google 名稱解析階段整體最多 3 秒，整體「使用我的位置」流程最多 5 秒。不能只依賴 connect/read timeout 保證解析階段上限；UI 層或 coordinator 層需要 finished/generation guard。UI 超時後回調失敗並丟棄遲到 UI 更新；resolver 遲到成功仍可寫入地址 cache，供下次點擊命中。HTTP 非 2xx、API error、空結果、無有效地址、key 缺失、JSON 解析錯誤、IOException 和 timeout 均返回失敗，不回退 `目前位置附近`。

原因：用戶感知上「取得目前位置」包含定位與地址解析，應保持單一失敗體驗。遲到結果不能在失敗提示後突然覆蓋輸入，但成功 cache 仍有價值。

替代方案：UI 顯示 Google 技術錯誤或自動重試。否決原因是普通用戶不可操作，且重試會增加成本並衝突 5 秒上限。

## Risks / Trade-offs

- [客戶端 API key 可被 APK 提取] → 本期接受客戶端直連作為階段性方案；key 不提交到 repo，缺失時不請求。正式大規模分發或需要集中控管時再設計後端 proxy。
- [Google API 配額、帳單或網路不可用導致解析失敗] → 保持既有失敗 UX，不保存假地址、不回退 mock；debug log 僅記錄失敗類別。
- [自動目前位置會把 GPS 坐標傳送給 Google] → 僅在既有會自動使用目前位置的入口和用戶點擊定位按鈕時發生；本期不新增隱私彈窗，正式分發前補隱私披露。
- [保存後列表仍顯示 Google 地址但不顯示 attribution] → 用戶已確認來源只在輸入框上下文展示；本期不做持久化來源欄位，正式分發合規完整性後續單獨處理。
- [Google 回傳語言或地址格式不穩定] → 固定 `zh-Hant` + `regionCode=HK`，展示選中 result 原文，不做翻譯或業務縮短；UI 以既有截斷處理長地址。
- [真實 API 驗收受環境影響] → 預設 build 不跑真實 API；opt-in connected test 一旦執行，缺 key、無模擬器或不能注入定位即明確失敗。

## Migration Plan

1. 在 ignored `local.properties` 或環境中配置 `GOOGLE_GEOCODING_API_KEY`，Gradle 生成 BuildConfig 欄位。
2. 用固定香港坐標做一次不提交 response/key 的 curl smoke test，帶上 Google 與 Android 身份 headers，校驗 v4 endpoint、FieldMask 和 response 欄位；若 key 未授權當前 debug 簽名，該 smoke check 保持未完成並在交付中說明。
3. 新增 Google resolver、request config、`org.json` parser、地址 cache、in-flight 合併、debug log 分類和測試注入點。
4. 將 `RouteEditActivity` 與 `MainActivity` 的 mock 解析替換為非同步 Google resolver，並補充複製／編輯頁靜默預熱與起點 attribution 小字。
5. 更新目前位置相關 contract/policy 測試，新增 resolver/parser/cache 單元測試。
6. 運行 `./gradlew testDebugUnitTest`、`./gradlew build`，並在可用模擬器上運行 opt-in 真實 API connected 驗收。

## Open Questions

- 無阻塞問題。API key 已由用戶提供，實作時只寫入 ignored 本機配置，不提交到 repo。
