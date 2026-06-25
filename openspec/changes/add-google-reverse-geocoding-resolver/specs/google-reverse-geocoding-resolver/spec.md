## ADDED Requirements

### Requirement: 使用 Google Geocoding API v4 解析目前位置名稱
系統 SHALL 使用 Google Geocoding API v4 reverse geocoding 將目前 GPS 位置解析為可展示和保存的地址名稱。

#### Scenario: 以 v4 endpoint 和 header 發出反向地理編碼請求
- **WHEN** 系統需要解析目前位置名稱
- **AND** 目前位置 snapshot 包含緯度與經度
- **AND** Google geocoding API key 可用
- **THEN** 系統 SHALL 發出 HTTPS GET 請求到 `https://geocode.googleapis.com/v4/geocode/location/{latitude},{longitude}`
- **AND** URL path 中的緯度與經度 SHALL 使用目前位置 snapshot 的原始座標並格式化到 6 位小數
- **AND** 請求 SHALL 透過 `X-Goog-Api-Key` header 傳遞 API key
- **AND** 請求 SHALL 透過 `X-Goog-FieldMask` header 傳遞 `results.formattedAddress,results.types,results.addressComponents.longText,results.addressComponents.types`
- **AND** 請求 SHALL 透過 `X-Android-Package` header 傳遞 App package name
- **AND** 請求 SHALL 透過 `X-Android-Cert` header 傳遞 App 簽名憑證 SHA-1 的無分隔符十六進位值
- **AND** 系統 SHALL NOT 將 API key 放入 URL query
- **AND** 系統 SHALL NOT 請求 Google result 的 `location` 或 `plusCode` 欄位

#### Scenario: 不使用 types 或 granularity 過濾請求
- **WHEN** 系統建立 Google reverse geocoding 請求
- **THEN** 系統 SHALL NOT 在請求中加入 `types=street_address`
- **AND** 系統 SHALL NOT 在請求中加入 `granularity`
- **AND** 系統 SHALL 使用單次請求結果在客戶端完成地址選取

#### Scenario: API key 不可用時不發出網路請求
- **WHEN** 系統需要解析目前位置名稱
- **AND** Google geocoding API key 為空白或缺失
- **THEN** 系統 SHALL 將名稱解析視為失敗
- **AND** 系統 SHALL NOT 發出 Google Geocoding API 請求
- **AND** 系統 SHALL NOT 使用固定 mock 名稱替代真實地址

#### Scenario: Android 身份 header 不可用時不發出網路請求
- **WHEN** 系統需要解析目前位置名稱
- **AND** App package name 或 App 簽名憑證 SHA-1 無法取得
- **THEN** 系統 SHALL 將名稱解析視為失敗
- **AND** 系統 SHALL NOT 發出 Google Geocoding API 請求

### Requirement: 反向地理編碼請求使用固定繁體語言並預留擴展
系統 SHALL 在 Google reverse geocoding 請求中使用香港繁體中文偏好，並保留未來多語言擴展點。

#### Scenario: 生產預設使用繁體中文地址
- **WHEN** 系統建立生產 Google reverse geocoding 請求
- **THEN** 系統 SHALL 使用 `languageCode=zh-Hant`
- **AND** 系統 SHALL 使用 `regionCode=HK`

#### Scenario: 測試或未來多語言可注入 languageCode
- **WHEN** resolver 由測試或未來多語言配置提供非預設 languageCode
- **THEN** 系統 SHALL 使用被提供的 BCP-47 language code 建立請求
- **AND** 系統 SHALL NOT 因 languageCode 改變而改變 Citybus 查詢語言或解析邏輯

#### Scenario: Google 返回非繁體或混合語言地址
- **WHEN** Google reverse geocoding 返回的選中地址不是完整繁體中文
- **THEN** 系統 SHALL 展示 Google 返回的選中地址原文
- **AND** 系統 SHALL NOT 使用機器翻譯或手工替換地址片段

### Requirement: 客戶端選取單一可讀地址名稱
系統 SHALL 從 Google reverse geocoding `results[]` 中選取單一可讀地址名稱，並 SHALL NOT 拼接多個 results 的地址。

#### Scenario: 優先選取 street_address
- **WHEN** Google reverse geocoding 回應包含至少一個 result
- **AND** 存在 `types` 包含 `street_address` 的 result
- **AND** 該 result 的 `formattedAddress` 非空且不是 plus code
- **THEN** 系統 SHALL 使用按 Google 返回順序遇到的第一個此類 `formattedAddress` 作為地址名稱

#### Scenario: 沒有 street_address 時選取非過粗 result
- **WHEN** Google reverse geocoding 回應沒有可用的 `street_address` 地址
- **AND** 存在 `formattedAddress` 非空且不是 plus code 的 result
- **AND** 該 result 的 `types` 不全屬於過粗類別
- **THEN** 系統 SHALL 使用按 Google 返回順序遇到的第一個此類 `formattedAddress` 作為地址名稱
- **AND** 過粗類別 SHALL 包含 `country`、`administrative_area_level_1`、`administrative_area_level_2`、`locality`、`political`

#### Scenario: 只有過粗 result 時兜底第一個可用地址
- **WHEN** Google reverse geocoding 回應沒有可用的 `street_address`
- **AND** 回應沒有非過粗的可用 `formattedAddress`
- **AND** 存在非空且不是 plus code 的 `formattedAddress`
- **THEN** 系統 SHALL 使用按 Google 返回順序遇到的第一個此類 `formattedAddress` 作為地址名稱

#### Scenario: formattedAddress 不可用時使用單一 result 的 addressComponents
- **WHEN** Google reverse geocoding 回應沒有任何可用 `formattedAddress`
- **AND** 存在包含可用 `addressComponents.longText` 的 result
- **THEN** 系統 SHALL 從單一 result 的 `addressComponents` 依返回順序取非空 `longText`
- **AND** 系統 SHALL 去除前後空白與重複片段
- **AND** 系統 SHALL 最多組合 4 個片段作為地址名稱
- **AND** 系統 SHALL NOT 跨多個 results 組合地址名稱

#### Scenario: plus code 不作為地址名稱
- **WHEN** Google reverse geocoding result 的 `formattedAddress` 疑似 plus code
- **THEN** 系統 SHALL NOT 將該 `formattedAddress` 作為地址名稱
- **AND** 若所有 results 只能產生 plus code，系統 SHALL 將名稱解析視為失敗

#### Scenario: 無可讀地址時解析失敗
- **WHEN** Google reverse geocoding 回應沒有 result
- **OR** 所有 results 都無法產生非空、非 plus code 的地址名稱
- **THEN** 系統 SHALL 將名稱解析視為失敗
- **AND** 系統 SHALL NOT 建立使用 mock 名稱的 `Place`

### Requirement: Google 地址結果保留目前位置原始 GPS 座標
系統 SHALL 使用解析出的地址名稱和當次目前位置 snapshot 的原始 GPS 座標建立目前位置 `Place`。

#### Scenario: 成功解析後建立 Place
- **WHEN** Google reverse geocoding 成功選取地址名稱
- **THEN** 系統 SHALL 使用該地址名稱作為 `Place.name`
- **AND** `Place.latitude` SHALL 等於當次目前位置 snapshot 的原始緯度
- **AND** `Place.longitude` SHALL 等於當次目前位置 snapshot 的原始經度
- **AND** 系統 SHALL NOT 使用 Google result 座標替換目前位置座標

#### Scenario: cache 命中時仍使用當次 snapshot 座標
- **WHEN** Google 地址名稱 cache 命中
- **AND** 系統需要建立目前位置 `Place`
- **THEN** 系統 SHALL 使用 cache 中的地址名稱作為 `Place.name`
- **AND** `Place.latitude` SHALL 使用當次目前位置 snapshot 的原始緯度
- **AND** `Place.longitude` SHALL 使用當次目前位置 snapshot 的原始經度
- **AND** 系統 SHALL NOT 使用 cache key 的四位小數座標作為 `Place` 座標

### Requirement: Google 地址名稱使用進程內 cache 與 in-flight 合併
系統 SHALL 對 Google reverse geocoding 成功地址名稱進行短期進程內 cache，並合併同一 cache key 的進行中請求。

#### Scenario: 成功地址名稱寫入 cache
- **WHEN** Google reverse geocoding 成功解析地址名稱
- **THEN** 系統 SHALL 以緯度四位小數整數鍵、經度四位小數整數鍵和 `languageCode` 作為 cache key
- **AND** 系統 SHALL 將地址名稱與 cache 時間寫入進程內 cache
- **AND** cache TTL SHALL 為 10 分鐘
- **AND** 系統 SHALL NOT 將該 cache 持久化到 SQLite、檔案或 SharedPreferences

#### Scenario: cache 命中時不發出 Google 請求
- **WHEN** 系統需要解析目前位置名稱
- **AND** 以目前 snapshot 與 `languageCode` 計算出的 cache key 命中未過期成功地址
- **THEN** 系統 SHALL 使用該地址名稱返回成功
- **AND** 系統 SHALL NOT 發出 Google Geocoding API 請求

#### Scenario: 失敗結果不寫入 cache
- **WHEN** Google reverse geocoding 名稱解析失敗
- **THEN** 系統 SHALL NOT 寫入成功地址 cache
- **AND** 下一次用戶明確觸發目前位置起點時系統 SHALL 可重新嘗試解析

#### Scenario: 同 key in-flight 請求合併
- **WHEN** 系統需要解析目前位置名稱
- **AND** cache 未命中
- **AND** 相同 cache key 已有進行中的 Google 請求
- **THEN** 系統 SHALL 將新的等待者加入該 in-flight 請求
- **AND** 系統 SHALL NOT 為相同 cache key 發出第二個並行 Google 請求
- **AND** in-flight 請求完成後 SHALL 通知所有仍有效的等待者

### Requirement: 反向地理編碼失敗、超時與日誌可控
系統 SHALL 將 Google reverse geocoding 的技術性失敗轉換為目前位置名稱解析失敗，並限制敏感資訊出現在日誌中。

#### Scenario: HTTP 或 API 錯誤
- **WHEN** Google reverse geocoding 請求返回非 2xx HTTP 狀態
- **OR** 回應表示 `INVALID_ARGUMENT`、`PERMISSION_DENIED`、配額、帳單或其他 API 錯誤
- **THEN** 系統 SHALL 將名稱解析視為失敗
- **AND** 系統 SHALL NOT 顯示 Google 技術錯誤給普通用戶

#### Scenario: 網路或解析錯誤
- **WHEN** Google reverse geocoding 請求發生網路 IOException
- **OR** 回應 JSON 無法解析
- **THEN** 系統 SHALL 將名稱解析視為失敗
- **AND** 系統 SHALL NOT 讓例外中斷目前畫面

#### Scenario: 名稱解析階段有整體超時限制
- **WHEN** 系統正在調用 Google reverse geocoding 解析目前位置名稱
- **THEN** 名稱解析階段 SHALL 最多等待 3 秒
- **AND** connect timeout 和 read timeout SHALL 均不超過 3 秒
- **AND** 超時 SHALL 被轉換為名稱解析失敗

#### Scenario: 失敗日誌不包含敏感資訊
- **WHEN** 系統記錄 Google reverse geocoding 失敗原因
- **THEN** 日誌 SHALL 只記錄失敗類別
- **AND** 日誌 SHALL NOT 包含 API key
- **AND** 日誌 SHALL NOT 包含完整 Google response body
- **AND** 日誌 SHALL NOT 包含高精度 GPS 座標或帶 key 的完整 URL

#### Scenario: 成功不記錄地址或座標
- **WHEN** Google reverse geocoding 成功解析地址名稱
- **THEN** 系統 SHALL NOT 因成功路徑在 debug log 中記錄地址名稱
- **AND** 系統 SHALL NOT 因成功路徑在 debug log 中記錄目前位置座標
