## Why

目前「我的位置」起點已經能取得 GPS 座標，但名稱解析仍使用固定 mock 文案 `目前位置附近`，保存路線和臨時查詢時無法呈現真實地址。Google Geocoding API 已完成註冊與配置，現在可以把既有 mock resolver 替換為真實 reverse geocoding，讓用戶在目前位置起點看到可理解的香港地址名稱。

## What Changes

- 新增 Google Geocoding API v4 reverse geocoding resolver，使用目前 GPS 緯度／經度取得真實地址名稱。
- 只在「我的位置」起點流程使用 Google resolver：純新增頁自動起點、臨時查詢自動起點、起點定位按鈕，以及複製／編輯頁靜默預熱。
- 地址選取使用單次 API 請求：客戶端優先選 `types` 包含 `street_address` 的 result；沒有時選非過粗 result；再沒有時選第一個可用 `formattedAddress`；最後才使用單一 result 的 `addressComponents` 降級；不拼接多個 results。
- 保持 `Place.latitude`／`Place.longitude` 使用當次目前位置 snapshot 的原始 GPS 座標，不使用 Google result 座標，也不擴展 `Place` 或 SQLite schema。
- API key 透過 ignored `local.properties` 或環境變數 `GOOGLE_GEOCODING_API_KEY` 注入到 `BuildConfig`；不把實際 key 寫入可提交源碼、OpenSpec、測試 fixture 或日誌。
- 生產預設 `languageCode=zh-Hant`、`regionCode=HK`，並在 resolver 層預留 language provider 以便未來多語言改造。
- 新增 Google 地址名稱的進程內 cache，與既有 30 秒定位 snapshot cache 分開；複製／編輯頁可靜默預熱位置與地址名稱，但不填入、不提示、不顯示 attribution。
- Google 地址實際填入起點輸入框時，在輸入框上下文顯示獨立小字 `地址由 Google Maps 提供`；其他列表、卡片、保存彈窗和路線結果不顯示地點來源。
- 保持既有定位權限、定位開關、超時、過期結果保護、焦點、鍵盤、候選搜尋、保存與查詢行為；反向地理編碼失敗時走既有「暫時無法取得目前位置」失敗路徑。
- 不改動 Citybus 地點搜尋、路線排序、最近路線自動選中、候選距離展示、離線地址解析或乘車碼能力。

## Capabilities

### New Capabilities
- `google-reverse-geocoding-resolver`: 定義目前位置名稱解析如何調用 Google Geocoding API v4、處理 key／語言／區域、選取地址名稱、cache／in-flight 合併、超時、日誌與失敗。

### Modified Capabilities
- `route-place-selection`: 將目前位置起點的 mock 名稱解析改為真實 Google reverse geocoding，並補充純新增、臨時查詢、複製／編輯預熱、attribution、失敗與過期結果規則。

## Impact

- 受影響代碼主要在 `app/src/main/java/com/example/busiscoming/data/location/`、`ui/edit/RouteEditActivity.kt`、`ui/main/MainActivity.kt`、`ui/main/TemporaryRouteBottomSheet.kt`、Gradle BuildConfig 配置與 `androidTest`。
- 需要新增 Google Geocoding API v4 請求、`org.json` 結構化解析、resolver 注入、地址名稱 cache、in-flight 合併、attribution UI 狀態和本機 key 讀取；現有 `INTERNET` 權限可繼續使用。
- 外部 API 依賴 Google Geocoding API v4 reverse geocoding：`https://geocode.googleapis.com/v4/geocode/location/{lat},{lng}`，使用 `X-Goog-Api-Key`、`X-Goog-FieldMask`、`languageCode`、`regionCode`；不兼容 v3 response 欄位。
- 需要更新目前位置相關單元測試，新增 resolver/parser/cache 單元測試，並新增 opt-in 模擬器 connected 驗收測試；預設 `./gradlew build` 不打真實 Google API。
- 用戶位置會在目前位置起點解析時傳送給 Google Geocoding API；本期不新增隱私彈窗，正式分發前需補齊隱私披露。
