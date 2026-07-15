## ADDED Requirements

### Requirement: 使用正式 Android package identity
系統 SHALL 在 Google Play 上架前使用 `com.golink.busiscoming` 作為正式 Android App 身份，並避免 current code、tests 和 scripts 混用舊開發包名。

#### Scenario: Gradle 使用正式包名
- **WHEN** 生成 Android app artifact
- **THEN** Gradle `applicationId` SHALL 為 `com.golink.busiscoming`
- **AND** Gradle `namespace` SHALL 為 `com.golink.busiscoming`
- **AND** `versionCode` SHALL 保持 `1`
- **AND** `versionName` SHALL 保持 `1.0`

#### Scenario: Source 與測試 package 完整遷移
- **WHEN** 開發者查看 main source、unit tests 或 instrumentation tests
- **THEN** current Kotlin package 宣告與 import SHALL 使用 `com.golink.busiscoming`
- **AND** current source/test 目錄 SHALL 與該 package 對齊
- **AND** current tests SHALL NOT 期望運行時 package 為 `com.example.busiscoming`

#### Scenario: 腳本使用正式包名
- **WHEN** 執行倉庫內仍被維護的 adb、screenshot 或 instrumentation 腳本
- **THEN** 腳本 SHALL 使用 `com.golink.busiscoming` 作為 App package id
- **AND** 腳本 SHALL NOT 對 current build 使用 `com.example.busiscoming`

### Requirement: 不遷移舊開發包本機資料
系統 SHALL 將 `com.golink.busiscoming` 視為新的 Android App 身份，並 SHALL NOT 為舊開發包 `com.example.busiscoming` 實作本機資料遷移橋接。

#### Scenario: 新舊包資料隔離
- **WHEN** 裝置上曾安裝 `com.example.busiscoming` 開發包
- **AND** 用戶安裝 `com.golink.busiscoming`
- **THEN** 系統 SHALL NOT 嘗試讀取或導入舊包 SQLite、SharedPreferences 或 app-private files
- **AND** `com.golink.busiscoming` SHALL 使用自身 app-private storage

#### Scenario: 本地測試說明資料邊界
- **WHEN** 實作完成後回報驗證結果
- **THEN** 回報 SHALL 說明舊開發包資料不會自動遷移
- **AND** 若本機同時存在新舊包，測試命令 SHALL 明確使用 `com.golink.busiscoming`

### Requirement: 上架準備不改動權限與外部 API key
系統 SHALL 在本次上架準備變更中保持現有權限、前台服務類型、版本與 Google Geocoding API key 注入機制不變。

#### Scenario: 權限聲明保持現狀
- **WHEN** 本變更生成 Android manifest
- **THEN** manifest SHALL NOT 新增 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **AND** manifest SHALL 保留現有 `android.permission.SCHEDULE_EXACT_ALARM`
- **AND** manifest SHALL 保留現有 foreground service、dataSync、wake lock、通知與定位相關聲明

#### Scenario: Google Geocoding key 注入保持不變
- **WHEN** 系統建立 Google reverse geocoding request
- **THEN** API key 仍 SHALL 來自既有 `GOOGLE_GEOCODING_API_KEY` build config 注入機制
- **AND** 倉庫 SHALL NOT 提交 API key 或 Google Cloud Console restriction 配置
- **AND** request identity 中的 Android package SHALL 隨運行時 package 變為 `com.golink.busiscoming`
