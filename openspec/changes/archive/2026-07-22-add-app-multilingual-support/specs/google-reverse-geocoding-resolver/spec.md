## MODIFIED Requirements

### Requirement: 反向地理編碼請求使用固定繁體語言並預留擴展
系統 SHALL 按目前 App 實際語言建立 Google reverse geocoding 請求，並保持香港地區偏好。

#### Scenario: 繁體中文地址請求
- **WHEN** 目前 App 實際語言為繁體中文
- **THEN** 系統 SHALL 使用 `languageCode=zh-Hant`
- **AND** 系統 SHALL 使用 `regionCode=HK`

#### Scenario: 簡體中文地址請求
- **WHEN** 目前 App 實際語言為簡體中文
- **THEN** 系統 SHALL 使用 `languageCode=zh-Hans`
- **AND** 系統 SHALL 使用 `regionCode=HK`

#### Scenario: 英文地址請求
- **WHEN** 目前 App 實際語言為英文
- **THEN** 系統 SHALL 使用 `languageCode=en`
- **AND** 系統 SHALL 使用 `regionCode=HK`

#### Scenario: Google 返回非目標或混合語言地址
- **WHEN** Google reverse geocoding 返回的選中地址不是完整目標語言
- **THEN** 系統 SHALL 展示 Google 返回的選中地址原文
- **AND** 系統 SHALL NOT 使用機器翻譯或手工替換地址片段
- **AND** 系統 SHALL NOT 以另一語言重新請求並保存替代地址

## ADDED Requirements

### Requirement: Google 地址結果受語言版本及 cache key 隔離
系統 SHALL 以座標與 `languageCode` 隔離 Google 地址 cache 及 in-flight 請求，並拒絕語言切換前的晚到結果。

#### Scenario: 相同座標不同語言
- **WHEN** 相同目前位置先後以不同 App 語言解析名稱
- **THEN** 系統 SHALL 使用不同 `languageCode` cache key
- **AND** 系統 SHALL NOT 把舊語言地址作為新語言 cache 命中

#### Scenario: 地址解析期間切換語言
- **WHEN** Google reverse geocoding 尚未完成且用戶切換語言
- **THEN** 舊請求結果 SHALL NOT 更新目前輸入、建立新 `Place` 或寫入新語言 cache

#### Scenario: 目前語言 Google 請求失敗
- **WHEN** 目前語言 Google 請求 timeout、網絡失敗、API 拒絕或沒有可讀地址
- **THEN** 系統 SHALL 使用目前 App 語言回報目前位置名稱解析失敗
- **AND** 系統 SHALL NOT 以其他語言重試或建立 mock `Place`

### Requirement: Google 三語真實請求為完成門檻
系統 SHALL 使用真實 Google Geocoding API v4 驗證三種語言，而非只依賴 mock 或 fixture。

#### Scenario: 相同香港座標三語驗證
- **WHEN** 團隊驗收 Google reverse geocoding 多語言能力
- **THEN** 系統 SHALL 對相同香港座標分別使用 `zh-Hant`、`zh-Hans`、`en` 發出真實請求
- **AND** 每次請求 SHALL 通過 App package、certificate、API key 限制並成功解析
- **AND** 返回地址 SHALL 與請求語言的文字體系及地址含義相符

#### Scenario: 真實驗證環境不可用
- **WHEN** API key、package／certificate 配置或連接 `geocode.googleapis.com` 的網絡不可用
- **THEN** 本 change SHALL NOT 被標記為完成
- **AND** mock 測試成功 SHALL NOT 取代該硬門檻
