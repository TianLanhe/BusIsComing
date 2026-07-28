## MODIFIED Requirements

### Requirement: bicroutes 版本一協議只包含可分享路線定義

系統 SHALL 使用未加密 UTF-8 JSON 作為 `.bicroutes` 版本 1 內容，並 SHALL 嚴格區分可分享行程定義與本機狀態，包括本次及長期路線結果置頂偏好。

#### Scenario: 匯出版本一外層欄位
- **WHEN** 系統建立 `.bicroutes` 版本 1 文件
- **THEN** 文件 SHALL 只包含必要外層欄位 `format`、`version`、`exportedAt` 與 `routes`
- **AND** `format` SHALL 為 `com.golink.busiscoming.routes`
- **AND** `version` SHALL 為整數 `1`
- **AND** `exportedAt` SHALL 為 UTC ISO-8601 時間

#### Scenario: 匯出單條路線欄位
- **WHEN** 系統將一條常用行程寫入版本 1 文件
- **THEN** 該行程 SHALL 只包含 `name`、`origin` 與 `destination`
- **AND** `origin` 與 `destination` SHALL 各自只包含 `name`、`latitude` 與 `longitude`

#### Scenario: 文件不包含本機狀態
- **WHEN** 系統完成 `.bicroutes` 文件編碼
- **THEN** 文件 SHALL NOT 包含 SQLite id、建立時間、更新時間、使用次數或最近使用時間
- **AND** 文件 SHALL NOT 包含查詢結果、ETA、定位、排序快取、通知監控 session、本次置頂、長期置頂、路線指紋或置頂 token

#### Scenario: 文件內容不加密
- **WHEN** 系統完成 `.bicroutes` 文件編碼
- **THEN** 文件內容 SHALL 保持未加密 JSON
- **AND** 系統 SHALL NOT 自動上傳或自動分享該文件

## ADDED Requirements

### Requirement: 匯入與取代不轉移置頂偏好
系統 SHALL 讓 `.bicroutes` 匯入所建立的新行程從空置頂狀態開始，並 SHALL 在 transaction 取代行程時以相同原子性處理被刪除行程的長期置頂。

#### Scenario: 合併匯入新增行程
- **WHEN** 合併匯入 transaction 建立一條新行程
- **THEN** 新行程 SHALL 不包含本次或長期置頂
- **AND** 系統 SHALL NOT 依名稱、起終點或路線號從既有行程複製置頂

#### Scenario: 合併匯入跳過重複行程
- **WHEN** 合併匯入判定候選行程與既有行程完全重複並跳過
- **THEN** 既有行程 id 及其全部本次／長期置頂 SHALL 保持不變

#### Scenario: 取代匯入成功
- **WHEN** 用戶確認取代且 transaction 成功
- **THEN** 系統 SHALL 刪除全部被取代行程及其關聯長期置頂
- **AND** 系統 SHALL 清除目前 task 中被取代行程的本次置頂
- **AND** 所有新匯入行程 SHALL 不包含本次或長期置頂

#### Scenario: 取代匯入失敗回滾
- **WHEN** 取代 transaction 在刪除或插入任一步驟失敗
- **THEN** 系統 SHALL 回滾行程及關聯長期置頂的全部變更
- **AND** transaction 前的行程、使用統計及長期置頂 SHALL 完整保留
- **AND** 目前 task 的本次置頂 SHALL NOT 因失敗 transaction 被清除

#### Scenario: 匯入現行版本一文件
- **WHEN** 用戶匯入由舊版 App 建立且符合 `.bicroutes` 版本 1 schema 的文件
- **THEN** 系統 SHALL 繼續按既有校驗、預覽、合併及取代規則處理
- **AND** 系統 SHALL NOT 要求文件包含置頂資料或升級格式版本
