## ADDED Requirements

### Requirement: 每個香港資料日原子檢查全局路線資料
系統 SHALL 以香港資料日管理 GTFS 聯營路線清單、KMB route、KMB route-stop、KMB stop 及 CTB route 五項全局資料，並只在五項來源全部成功驗證後發布新的可讀快照。

#### Scenario: 尚無可用快照時首次進入前台
- **WHEN** App 尚無完整成功的全局路線資料快照
- **AND** 用戶令 App 進入前台
- **THEN** 系統 SHALL 在背景立即開始五項全局資料檢查
- **AND** 系統 SHALL NOT 阻塞 App 首幀或既有 Citybus 路線查詢

#### Scenario: 每個資料日首次進入前台
- **WHEN** App 已有可用快照
- **AND** 香港時間已進入尚未成功檢查的新資料日
- **AND** 用戶令 App 進入前台
- **THEN** 系統 SHALL 在背景開始一次五項全局資料檢查
- **AND** 查詢 SHALL 繼續使用開始時捕獲的舊 active snapshot

#### Scenario: 香港時間早於資料日分界
- **WHEN** 香港時間早於當日 `05:15`
- **THEN** 系統 SHALL 把目前時間歸入上一個日曆日的資料日

#### Scenario: App 未啟動時不排程更新
- **WHEN** App 未運行或未由用戶帶到前台
- **THEN** 系統 SHALL NOT 為每日路線資料檢查建立定時鬧鐘、前台服務或獨立排程工作

#### Scenario: 五項來源全部成功
- **WHEN** 五項來源均成功下載或取得有效的 `304 Not Modified`
- **AND** 所有必要資料均成功解析及通過入庫校驗
- **THEN** 系統 SHALL 原子切換 active snapshot
- **AND** 系統 SHALL 以香港時間記錄本資料日的完整成功檢查時間

#### Scenario: 任一全局來源失敗
- **WHEN** 任一來源 timeout、返回失敗狀態、內容無效或未通過入庫校驗
- **THEN** 系統 SHALL NOT 發布部分更新的快照
- **AND** 系統 SHALL 保留舊 active snapshot 及上次完整成功時間
- **AND** 系統 SHALL 以有限退避允許後續重試而不得在同一前台 session 無限重試

#### Scenario: 自動與手動檢查同時發生
- **WHEN** 已有一項全局路線資料檢查正在進行
- **AND** 自動或手動入口再次要求檢查
- **THEN** 後來的要求 SHALL 觀察同一項 single-flight 工作
- **AND** 系統 SHALL NOT 發出第二組五項全局請求

### Requirement: 全局路線資料作為獨立可重建快照保存
系統 SHALL 把跨營運商靜態資料與映射 cache 保存於獨立、可重建且版本化的本機資料庫，不得與使用者行程、置頂或偏好資料共用破壞性生命週期。

#### Scenario: 發布新快照時仍有查詢讀取舊快照
- **WHEN** 一項查詢已捕獲舊 active snapshot ID
- **AND** 背景更新原子發布新 snapshot
- **THEN** 該查詢 SHALL 繼續以舊 snapshot 完成本次讀取
- **AND** 新查詢 SHALL 使用新 active snapshot

#### Scenario: staging 快照未完整提交
- **WHEN** App 進程在 staging 快照完整提交前結束
- **THEN** 系統 SHALL 在下次啟動繼續使用最近完整 active snapshot
- **AND** 未提交資料 SHALL NOT 成為可讀快照

#### Scenario: 可重建資料庫缺失或損壞
- **WHEN** 靜態路線資料庫缺失、schema 不兼容或確認損壞
- **THEN** 系統 SHALL 回退為 Citybus-only 路線與 ETA 行為
- **AND** 系統 SHALL 允許重新下載及重建靜態資料庫
- **AND** 系統 SHALL NOT 刪除或重設使用者行程、置頂及偏好

#### Scenario: Android 備份收集 App 資料
- **WHEN** Android cloud backup 或 device transfer 評估可備份檔案
- **THEN** 新增的可重建靜態路線資料庫 SHALL 被排除
- **AND** 此排除 SHALL NOT 改變既有使用者資料的備份契約

### Requirement: 靜態路線記錄在發布前完成必要校驗
系統 SHALL 在全局 staging snapshot 或 CTB route slice 發布前校驗參與匹配的 ID、站序、引用及座標，使 DP 只接收完整且不可變的有效站序。

#### Scenario: 有效 route-stop 資料入庫
- **WHEN** route、route-stop 及 stop 記錄具有非空必要 ID、正且唯一的同變體 sequence、可解析的合法 WGS84 座標及存在的 stop 引用
- **THEN** 系統 SHALL 允許該完整資料集進入可發布狀態

#### Scenario: 必要資料語義不完整
- **WHEN** 任一必要記錄缺失 ID、具有無效或重複 sequence、無效座標或引用不存在的 stop
- **THEN** 系統 SHALL 拒絕發布包含該不完整語義的全局快照或 CTB route slice
- **AND** 系統 SHALL 保留上一份完整資料

### Requirement: 聯營 CTB 路線站點按需每日驗證
系統 SHALL 只為 GTFS 聯營門禁命中的實際查詢路線按需取得 CTB 雙向 route-stop 及其去重 stop 資料，並以 stale-while-revalidate 方式復用完整舊 slice。

#### Scenario: 首次查詢未緩存的聯營路線
- **WHEN** 首程 route 通過 GTFS 聯營門禁
- **AND** 本機沒有該 CTB 路線的完整 route slice
- **THEN** 系統 SHALL 取得兩個方向的 CTB route-stop 及 N 個唯一 stop 記錄
- **AND** 系統 SHALL 在 `2 + N` 項懶載入完成前先交付 Citybus 核心結果
- **AND** 完整成功後系統 SHALL 漸進觸發該路線的匹配及跨營運商 ETA

#### Scenario: 新資料日已有舊 CTB route slice
- **WHEN** 該聯營路線已有完整舊 route slice
- **AND** 新香港資料日首次使用該路線
- **THEN** 系統 SHALL 立即復用舊 slice 及其仍有效的映射
- **AND** 系統 SHALL 在背景重新驗證該路線的 `2 + N` 項資料

#### Scenario: 同資料日重複使用路線或站點
- **WHEN** 同一資料日多個 consumer 請求相同 CTB 路線或相同 stop ID
- **THEN** 系統 SHALL 復用 route-level 與 stop-level single-flight 結果
- **AND** 系統 SHALL NOT 為相同資料重複發出不必要請求

#### Scenario: CTB route slice 只取得部分成功
- **WHEN** 雙向 route-stop 或任一必要 stop 請求失敗
- **THEN** 系統 SHALL NOT 用部分 route slice 覆蓋舊完整 slice
- **AND** 首程 SHALL 保持可使用 Citybus ETA

#### Scenario: 全局手動檢查完成
- **WHEN** 用戶完成一次五項全局路線資料手動檢查
- **THEN** 系統 SHALL NOT 因此遍歷已用路線、批量發出 CTB `2 + N` 請求或預先計算 DP

