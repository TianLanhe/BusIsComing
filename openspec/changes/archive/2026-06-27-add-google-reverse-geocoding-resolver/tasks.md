## 1. Build Configuration and Smoke Check

- [x] 1.1 在 `app/build.gradle.kts` 啟用 BuildConfig，從 ignored `local.properties` 或環境變數讀取 `GOOGLE_GEOCODING_API_KEY`，缺失時生成空字串。
- [x] 1.2 在本機 `local.properties` 以 `GOOGLE_GEOCODING_API_KEY=<key>` 配置用戶提供的 key，保留既有 `sdk.dir` 並確認該檔案不會被 git 提交。
- [x] 1.3 使用固定香港坐標執行一次不提交 key/response 的 Google Geocoding API v4 curl smoke check，帶上 `X-Goog-*` 與 `X-Android-*` headers，確認 endpoint、FieldMask 與 v4 response 欄位可用。（已透過系統代理重驗，Google 回傳 HTTP 200，且 response 包含 `results[]` 與 `formattedAddress` 欄位）

## 2. Google Resolver Core

- [x] 2.1 將目前位置名稱解析改為非同步 resolver 或新增等效非同步契約，避免 Google 網路請求阻塞主線程。
- [x] 2.2 新增 Google reverse geocoding request config，生產預設 `languageCode=zh-Hant`、`regionCode=HK`，並預留可注入 language provider。
- [x] 2.3 實作 v4 HTTP 請求：6 位小數座標 path、`X-Goog-Api-Key`、`X-Goog-FieldMask`、`X-Android-Package`、`X-Android-Cert`、不在 URL 放 key、不使用 `types`/`granularity` 過濾。
- [x] 2.4 使用 `org.json` 實作 v4 response parser，按 `street_address` 優先、非過粗 result、第一個可用 `formattedAddress`、單一 result components fallback 的順序選取地址。
- [x] 2.5 排除 plus code 作為地址名稱，並在無可讀地址時返回解析失敗而不是 mock fallback。
- [x] 2.6 確保成功結果建立的 `Place` 使用選中地址名稱與當次目前位置 snapshot 原始 GPS 座標，不使用 Google result 座標或 cache key 座標。
- [x] 2.7 將 key 缺失、Android 身份 header 缺失、HTTP/API 錯誤、空結果、無有效地址、網路錯誤、JSON 錯誤和超時全部轉換為名稱解析失敗。
- [x] 2.8 加入克制 debug log 分類，覆蓋 key missing、403/429/5xx、empty results、malformed JSON、timeout、network error，且不記錄 key、完整 response body、高精度坐標或帶 key URL。

## 3. Cache, Concurrency, and Timeout

- [x] 3.1 保留 `CurrentLocationCoordinator` 既有 30 秒定位 snapshot cache 和 pending 定位請求合併，不把 Google 地址 cache 混入定位 coordinator。
- [x] 3.2 新增 Google 地址名稱進程內 cache，key 使用 `round(latitude * 10000)`、`round(longitude * 10000)` 和 `languageCode`，TTL 10 分鐘，只缓存成功地址名稱。
- [x] 3.3 cache 命中時不發 Google 請求，並使用當次目前位置 snapshot 原始 GPS 座標建立 `Place`。
- [x] 3.4 實作同 key in-flight 合併，讓靜默預熱和用戶點擊定位按鈕可共用同一個 Google 請求。
- [x] 3.5 實作名稱解析階段整體 3 秒上限，以及「使用我的位置」整體 5 秒 guard；UI 超時後丟棄遲到更新，但 resolver 遲到成功仍可寫入 cache。
- [x] 3.6 確認不做自動重試、不做持久化 cache、不缓存失敗結果。

## 4. Current Place Flow Integration

- [x] 4.1 將 `RouteEditActivity` 純新增頁的自動目前位置起點從 `MockPlaceNameResolver` 切換到 Google resolver，保持既有焦點、鍵盤、候選搜尋和自動查詢行為。
- [x] 4.2 將 `MainActivity`／臨時查詢目前位置起點從 `MockPlaceNameResolver` 切換到 Google resolver，保持 `CurrentPlaceSelectionResult` 成功／失敗語義與既有 helper/toast。
- [x] 4.3 在複製與編輯路線頁面加入靜默預熱：已有定位權限且定位開啟時取得/復用 snapshot、更新候選距離、預熱 Google 地址 cache；不得填入、提示、跳設定或顯示 attribution。
- [x] 4.4 確認所有自動與手動目前位置流程在成功前不覆蓋既有輸入，並在用戶編輯、清空、選擇其他起點或頁面關閉後丟棄遲到 UI 更新。
- [x] 4.5 移除生產路徑對固定 mock 名稱 `目前位置附近` 的依賴，必要時僅保留測試替身。

## 5. Attribution UI

- [x] 5.1 在新增/編輯/複製路線頁起點輸入框下方加入獨立 attribution 小字視圖，文字為 `地址由 Google Maps 提供`，不使用 `TextInputLayout.helperText`。
- [x] 5.2 在臨時查詢起點輸入框下方、起點 loading/candidate list 上方加入同等 attribution 小字視圖。
- [x] 5.3 在 Google resolver 成功或 cache 命中並實際填入起點時顯示 attribution；靜默預熱不顯示。
- [x] 5.4 在用戶手動編輯、清空起點、選擇 Citybus 候選、Google 解析失敗或起點不再由 Google 填入時隱藏 attribution。
- [x] 5.5 確認管理路線、主頁卡片、路線結果、保存臨時查詢彈窗和其他非輸入上下文不展示地點來源，且 attribution 不進入 `Place.name` 或保存資料。

## 6. Automated Tests

- [x] 6.1 新增 Google resolver request 單元測試，覆蓋 v4 URL、6 位小數座標、Google/Android headers、FieldMask、`languageCode`、`regionCode`、key 缺失或 Android 身份缺失不請求。
- [x] 6.2 新增 parser 單元測試，覆蓋 `street_address` 優先、非過粗 result、第一個可用地址、components fallback、plus code 排除、空 results、API error 和 malformed JSON。
- [x] 6.3 新增 cache/concurrency 單元測試，覆蓋 E4 cache key、10 分鐘 TTL、cache hit 不請求、失敗不缓存、同 key in-flight 合併、cache hit 使用當次 snapshot 座標。
- [x] 6.4 更新 `LocationFeaturePolicyTest`，確認目前位置 `Place` 保留原始 GPS 座標並使用 resolver 返回的真實地址名稱。
- [x] 6.5 更新 `LocationFeatureContractTest`，確認主流程不再依賴 `MockPlaceNameResolver.resolve`，並保留定位設定、權限、失敗文案、預熱與候選距離契約。
- [x] 6.6 補充 UI contract 測試，覆蓋 attribution 独立小字、成功顯示、手動編輯/Citybus 候選後隱藏、失敗 helper/toast 不被 attribution 覆蓋。

## 7. Emulator Acceptance

- [x] 7.1 新增 opt-in `androidTest`/connected 驗收測試，使用新增路線頁起點「使用我的位置」入口驗證真實 Google API 主路徑。
- [x] 7.2 在驗收前使用 Android `cmd location` test provider 注入香港中環固定坐標，並透過測試授予前台定位權限。
- [x] 7.3 驗收斷言起點地址非空、不是 `目前位置附近`、不是 plus code、不是 attribution 文案，且起點 attribution 小字可見；不斷言完整地址字串。
- [x] 7.4 確保真實 API connected 驗收不併入預設 `./gradlew build`；一旦執行該 opt-in 驗收，缺 key、無可用模擬器、無法注入定位或無網路 SHALL fail 並給出清晰原因。

## 8. Verification and Commit

- [x] 8.1 運行 `./gradlew testDebugUnitTest`，確認所有本地單元測試通過。
- [x] 8.2 運行 `./gradlew build`，確認編譯、lint、單測與 assemble 通過。
- [x] 8.3 若當前環境有可用 Android 模擬器，運行 opt-in 真實 Google API connected 驗收；若無可用環境，保持該任務未勾選並在交付中說明原因。（已在 `emulator-5554` 執行 opt-in connected 驗收並通過）
- [x] 8.4 運行 `openspec validate add-google-reverse-geocoding-resolver --strict`，確認 proposal、design、specs、tasks 可通過驗證。
- [x] 8.5 實作完成後更新本 `tasks.md` 勾選狀態，檢查 `git status --short` 和 staged 範圍。
- [x] 8.6 依專案規則提交本 change 的實作與 OpenSpec 更新，commit message 使用簡潔英文 conventional commit。
