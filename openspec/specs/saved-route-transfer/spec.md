# saved-route-transfer Specification

## Purpose
定義使用者已保存常用行程的匯出、匯入、資料驗證、衝突處理與失敗回復行為，讓資料可移轉且不損壞既有本機行程。

## Requirements

### Requirement: 設定頁提供常用路線傳輸入口

系統 SHALL 在設定頁以獨立 `路線資料` 分組承載常用路線匯入與匯出，並使用專用二級頁完成所有操作。

#### Scenario: 設定頁顯示單一入口
- **WHEN** 用戶打開 `設定` 頁
- **THEN** 系統 SHALL 在 `偏好` 與 `支援` 之間顯示 `路線資料` 分組
- **AND** 該分組 SHALL 只顯示一個 `匯入與匯出常用路線` item

#### Scenario: 打開路線傳輸頁
- **WHEN** 用戶點擊 `匯入與匯出常用路線`
- **THEN** 系統 SHALL 打開標題為 `路線匯入與匯出` 的專用二級頁
- **AND** 頁面 SHALL 顯示 `匯入路線` 與 `匯出全部路線` 兩個操作區
- **AND** 頁面 SHALL 顯示目前常用路線數量

#### Scenario: 從路線傳輸頁返回
- **WHEN** 用戶點擊頁面返回入口或系統返回
- **THEN** 系統 SHALL 關閉路線傳輸頁並返回 `設定`
- **AND** 系統 SHALL NOT 因返回操作新增、修改或刪除任何常用路線

#### Scenario: 無常用路線時的操作狀態
- **WHEN** 用戶打開路線傳輸頁且目前沒有常用路線
- **THEN** `匯入路線` SHALL 保持可用
- **AND** `匯出全部路線` SHALL 保持可見但禁用
- **AND** 頁面 SHALL 說明目前沒有可匯出的常用路線

### Requirement: 全部常用路線可匯出為 bicroutes 文件

系統 SHALL 將匯出當下的全部常用路線保存為單一 `.bicroutes` 文件，且 SHALL NOT 提供部分路線選擇或自動打開分享面板。

#### Scenario: 每次匯出前提示地點隱私
- **WHEN** 用戶在存在常用路線時點擊 `全部匯出`
- **THEN** 系統 SHALL 顯示 `檔案包含全部常用路線及地點資料，請只分享給信任的人。`
- **AND** 提示 SHALL 要求用戶確認後才打開系統保存位置選擇器
- **AND** 系統 SHALL NOT 提供永久略過此提示的選項

#### Scenario: 取消匯出隱私確認
- **WHEN** 用戶取消匯出隱私確認
- **THEN** 系統 SHALL 保持停留在路線傳輸頁
- **AND** 系統 SHALL NOT 打開保存位置選擇器或建立文件

#### Scenario: 選擇匯出文件位置
- **WHEN** 用戶確認隱私提示
- **THEN** 系統 SHALL 使用 Android 系統文件選擇器讓用戶指定保存位置
- **AND** 建議檔名 SHALL 為 `BusIsComing-routes-YYYYMMDD-HHmm.bicroutes`
- **AND** 文件 MIME SHALL 為 `application/octet-stream`
- **AND** 系統 SHALL NOT 要求外部儲存權限或永久 URI 存取權

#### Scenario: 取消保存位置選擇
- **WHEN** 用戶取消系統保存位置選擇器
- **THEN** 系統 SHALL 靜默返回路線傳輸頁
- **AND** 系統 SHALL NOT 顯示錯誤或修改常用路線

#### Scenario: 成功匯出全部路線
- **WHEN** 用戶選擇可寫入位置且系統成功寫入文件
- **THEN** 文件 SHALL 包含匯出當下全部常用路線
- **AND** 系統 SHALL 停留在路線傳輸頁
- **AND** 系統 SHALL 顯示實際匯出的路線數量

#### Scenario: 匯出寫入失敗
- **WHEN** 系統無法編碼或完整寫入所選位置
- **THEN** 系統 SHALL 顯示匯出失敗提示
- **AND** 系統 SHALL 關閉文件輸出並盡力移除不完整文件
- **AND** 系統 SHALL NOT 修改任何常用路線

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

### Requirement: 匯入文件須在預覽前完整校驗

系統 SHALL 在顯示匯入預覽或寫入 SQLite 前完整讀取並校驗文件；任一非法欄位 SHALL 使整份文件失敗。

#### Scenario: 選擇候選匯入文件
- **WHEN** 用戶點擊 `選擇檔案並預覽`
- **THEN** 系統 SHALL 使用 Android 系統文件選擇器讓用戶選擇候選文件
- **AND** 系統 SHALL 接受文件供應器對自訂副檔名使用的通用 MIME
- **AND** 系統 SHALL NOT 要求外部儲存權限或永久 URI 存取權

#### Scenario: 取消候選文件選擇
- **WHEN** 用戶取消系統文件選擇器
- **THEN** 系統 SHALL 靜默返回路線傳輸頁
- **AND** 系統 SHALL NOT 顯示錯誤或修改常用路線

#### Scenario: 校驗可取得的文件名稱
- **WHEN** 文件供應器可提供候選文件顯示名稱
- **THEN** 文件名稱 SHALL 以 `.bicroutes` 結尾
- **AND** 不符合副檔名的文件 SHALL 被整份拒絕

#### Scenario: 文件供應器不提供名稱
- **WHEN** 文件供應器無法提供候選文件顯示名稱
- **THEN** 系統 SHALL 以文件內 `format`、`version` 與完整 schema 校驗作為信任依據
- **AND** 系統 SHALL NOT 僅因缺少顯示名稱而拒絕合法內容

#### Scenario: 限制候選文件大小
- **WHEN** 系統讀取候選文件
- **THEN** 系統 SHALL 以最多 `2 MiB + 1 byte` 的有界方式讀取
- **AND** 超過 2 MiB 的文件 SHALL 被整份拒絕
- **AND** 系統 SHALL 顯示文件大小超出限制的提示

#### Scenario: 校驗版本一結構
- **WHEN** 系統解析候選文件
- **THEN** `format` SHALL 為 `com.golink.busiscoming.routes`
- **AND** `version` SHALL 為整數 `1`
- **AND** `format`、`version`、`exportedAt` 與 `routes` SHALL 全部存在且類型正確
- **AND** 版本 1 出現未知外層、路線或地點欄位時 SHALL 被整份拒絕

#### Scenario: 拒絕不支援版本
- **WHEN** 候選文件具有正確格式標記但版本不是 `1`
- **THEN** 系統 SHALL 拒絕整份文件
- **AND** 系統 SHALL 提示 `檔案版本暫不支援，請先更新應用。`
- **AND** 系統 SHALL NOT 顯示預覽或修改常用路線

#### Scenario: 限制候選路線數量
- **WHEN** 系統解析 `routes`
- **THEN** `routes` SHALL 包含 1 至 500 個項目
- **AND** 0 條或超過 500 條的文件 SHALL 被整份拒絕
- **AND** 0 條文件 SHALL NOT 可用於清空現有路線

#### Scenario: 校驗單條路線資料
- **WHEN** 系統校驗候選文件中的一條路線
- **THEN** 路線名稱 trim 後 SHALL NOT 為空
- **AND** 起點與終點地點名稱 SHALL NOT 為空
- **AND** 緯度及經度 SHALL 為有限數字
- **AND** 緯度 SHALL 位於 `-90..90`，經度 SHALL 位於 `-180..180`
- **AND** 起點與終點 SHALL NOT 完全相同

#### Scenario: 任一非法路線使整份文件失敗
- **WHEN** 候選文件中至少一條路線缺少必要欄位、類型錯誤或資料不合法
- **THEN** 系統 SHALL 拒絕整份文件
- **AND** 系統 SHALL 顯示文件內容損壞或不合法的提示
- **AND** 系統 SHALL NOT 匯入其餘有效路線或修改現有資料

### Requirement: 合法匯入文件須顯示完整影響預覽

系統 SHALL 在所有校驗通過後建立只讀匯入預覽，讓用戶在寫入前理解完整文件內容及兩種模式的影響。

#### Scenario: 顯示合法文件預覽
- **WHEN** 候選文件通過全部校驗
- **THEN** 系統 SHALL 顯示檔名或可用的文件識別、唯一有效路線數及完整路線名稱清單
- **AND** 路線名稱清單 SHALL 可滾動
- **AND** 系統 SHALL NOT 在顯示預覽時修改任何現有路線

#### Scenario: 預覽文件內完全重複項目
- **WHEN** 文件內有多條路線的 trim 後名稱、起點及終點全部相同
- **THEN** 系統 SHALL 在預覽及後續匯入中只保留第一條
- **AND** 其餘項目 SHALL 計入文件內重複跳過數
- **AND** 文件內重複 SHALL NOT 被視為非法欄位

#### Scenario: 顯示合併與取代影響
- **WHEN** 系統顯示匯入預覽
- **THEN** 預覽 SHALL 顯示合併預計新增及跳過的路線數
- **AND** 預覽 SHALL 顯示取代將刪除的現有路線數及將匯入的唯一路線數
- **AND** 預覽 SHALL 同時提供 `合併匯入` 與 `取代現有路線`

#### Scenario: 從預覽返回不匯入
- **WHEN** 用戶從匯入預覽返回上一頁
- **THEN** 系統 SHALL 返回路線傳輸操作首頁
- **AND** 系統 SHALL NOT 新增、修改或刪除任何常用路線

### Requirement: 合併匯入須保留現有資料與統計

系統 SHALL 以三要素完全一致判定重複，並在單一 SQLite transaction 中只新增不重複路線。

#### Scenario: 判定完全重複路線
- **WHEN** 匯入路線的名稱 trim 後與現有路線相同，且起點 `Place` 與終點 `Place` 均相同
- **THEN** 系統 SHALL 將該路線視為完全重複
- **AND** 系統 SHALL NOT 更新、覆蓋或重新建立該現有路線

#### Scenario: 相同部分資料不視為完全重複
- **WHEN** 匯入路線只有名稱相同，或只有起終點相同
- **THEN** 系統 SHALL 將該路線視為不同路線
- **AND** 系統 SHALL 允許在合併時新增該路線

#### Scenario: 合併新增並跳過重複
- **WHEN** 用戶在預覽點擊 `合併匯入`
- **THEN** 系統 SHALL 在單一 transaction 內重新判定當下重複資料
- **AND** 系統 SHALL 只插入不重複路線
- **AND** 既有路線 SHALL 保留原 id、使用次數及最近使用時間
- **AND** 新路線 SHALL 使用新 id、使用次數 `0` 及最近使用時間 `null`

#### Scenario: 合併結果全部重複
- **WHEN** 合併 transaction 判定所有候選路線均與當下資料重複
- **THEN** 系統 SHALL 視為正常完成
- **AND** 系統 SHALL 顯示 `沒有新增路線，已跳過 X 條重複路線。`

#### Scenario: 合併完成使用實際交易結果
- **WHEN** 預覽後現有路線在提交前發生變化
- **THEN** 系統 SHALL 以 transaction 內重新判定的實際新增及跳過數完成匯入
- **AND** 完成摘要 SHALL 使用實際結果而非過期預覽估計值

#### Scenario: 合併交易失敗完整回滾
- **WHEN** 合併 transaction 中任一插入失敗
- **THEN** 系統 SHALL 回滾本次全部新增
- **AND** 交易前的現有路線及使用統計 SHALL 保持不變
- **AND** 系統 SHALL 顯示匯入失敗且現有路線未變的提示

### Requirement: 取代匯入須經二次確認並保持原子性

系統 SHALL 在使用者明確確認後，以單一 SQLite transaction 將現有路線完整取代為文件內去重後的路線。

#### Scenario: 點擊取代顯示危險確認
- **WHEN** 用戶在預覽點擊 `取代現有路線`
- **THEN** 系統 SHALL 顯示危險確認對話框
- **AND** 對話框 SHALL 明確顯示 `現有 X 條路線將永久刪除，並改為匯入 Y 條路線。此操作無法復原。`
- **AND** 對話框 SHALL 提供 `取消` 與 `確認取代`

#### Scenario: 取消取代確認
- **WHEN** 用戶在危險確認中點擊 `取消` 或返回
- **THEN** 系統 SHALL 關閉確認並保留匯入預覽
- **AND** 系統 SHALL NOT 新增、修改或刪除任何常用路線

#### Scenario: 成功取代全部路線
- **WHEN** 用戶點擊 `確認取代` 且 transaction 成功
- **THEN** 系統 SHALL 在同一 transaction 中刪除全部現有路線並插入文件內去重後的全部路線
- **AND** 新路線 SHALL 使用新 id、使用次數 `0` 及最近使用時間 `null`
- **AND** 系統 SHALL 返回路線傳輸操作首頁並顯示實際刪除及匯入數量

#### Scenario: 取代交易失敗完整回滾
- **WHEN** 取代 transaction 在刪除或任一插入步驟失敗
- **THEN** 系統 SHALL 回滾刪除及全部插入
- **AND** 交易前的全部現有路線及使用統計 SHALL 完整保留
- **AND** 系統 SHALL 顯示匯入失敗且現有路線未變的提示

### Requirement: 路線傳輸保持非阻塞、可恢復與可存取

系統 SHALL 在不阻塞主線程的情況下處理文件及資料庫操作，並在生命週期、字體縮放及輔助技術下保持可控。

#### Scenario: 背景處理期間防止重複操作
- **WHEN** 系統正在讀取、解析、編碼、寫入或執行匯入 transaction
- **THEN** 系統 SHALL 在背景執行該工作
- **AND** 頁面 SHALL 顯示輕量處理中狀態
- **AND** 會重複啟動同一工作的操作 SHALL 暫時禁用

#### Scenario: 頁面銷毀後忽略舊結果
- **WHEN** 路線傳輸頁在背景工作完成前被銷毀
- **THEN** 舊頁面 SHALL NOT 接收完成或失敗的 UI 更新
- **AND** 系統 SHALL NOT 因舊 callback 崩潰或重複提交匯入

#### Scenario: 設定變更後重新建立預覽
- **WHEN** 頁面在合法文件預覽期間因 configuration change 重新建立
- **THEN** 系統 SHALL 保存候選 URI、檔名與目前階段
- **AND** 系統 SHALL 重新讀取及校驗文件以建立預覽
- **AND** 系統 SHALL NOT 將最多 500 條路線資料保存到 Bundle

#### Scenario: 恢復時候選 URI 已失效
- **WHEN** 頁面重新建立但候選 URI 已無法讀取
- **THEN** 系統 SHALL 安全返回路線傳輸操作首頁
- **AND** 系統 SHALL 提示用戶重新選擇文件
- **AND** 現有常用路線 SHALL 保持不變

#### Scenario: 返回主頁重新載入匯入結果
- **WHEN** 匯入成功後用戶返回主頁
- **THEN** 主頁 SHALL 重新載入最新常用路線
- **AND** 若先前選中路線已被取代刪除，主頁 SHALL 安全選擇仍存在的路線或顯示無路線狀態

#### Scenario: 大字體與長路線名稱保持可操作
- **WHEN** 用戶使用放大字體或文件包含長路線名稱
- **THEN** 操作卡、預覽清單、摘要及按鈕 SHALL NOT 重疊
- **AND** 預覽清單 SHALL 保持可滾動
- **AND** 主要操作 SHALL 具備至少 48dp 觸控高度

#### Scenario: TalkBack 讀出路線傳輸狀態
- **WHEN** 用戶使用 TalkBack 瀏覽路線傳輸頁及預覽
- **THEN** 系統 SHALL 讀出檔名、路線數、合併／取代影響、危險操作及完成摘要
- **AND** `取代現有路線` SHALL 使用文字與語義表達風險，不得只依靠顏色

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
