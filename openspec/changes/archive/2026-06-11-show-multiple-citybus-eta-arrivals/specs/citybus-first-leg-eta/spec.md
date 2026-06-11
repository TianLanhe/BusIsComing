## MODIFIED Requirements

### Requirement: 推导首程站点并查询 ETA

系統 SHALL 使用首程 bus leg 與 P2P stop map 推導上車站點，呼叫 DATA.GOV.HK 城巴公開 ETA API，並從同一次 ETA 響應中取得最多 3 筆首程到站班次；當 ETA 響應中的 `seq` 與首程上車站序不一致但 `route`、`stop` 和 `dir` 已匹配時，系統 SHALL 使用保守降級策略避免錯誤丟棄有效 ETA。

#### Scenario: 透過 P2P stop map 推導 stop_id
- **WHEN** 系統獲得首程 company、route variant、公開 route、boardingSeq、direction code、rawInfo 和 lang
- **THEN** 系統 SHALL 使用 `showstops2.php?r=<rawInfo>&l=<lang>` 的 P2P stop map 查找首程上車站
- **AND** 系統 SHALL 使用該 P2P stop map 記錄的 `stop_id` 作為 ETA 查詢站點
- **AND** 系統 SHALL NOT 使用公開 `route-stop/{company}/{route}/{direction}` 作為運行時 stop_id fallback

#### Scenario: 查詢 ETA 並優先使用嚴格匹配
- **WHEN** 系統獲得首程 company、stop_id、公開 route、原始 direction code 和 boardingSeq
- **THEN** 系統 SHALL 請求 `https://rt.data.gov.hk/v2/transport/citybus/eta/{company}/{stop_id}/{route}`
- **AND** 系統 SHALL 優先使用 `route`、`stop`、`dir` 和 `seq` 均匹配首程資訊且 `eta` 非空可解析的 ETA 記錄
- **AND** 系統 SHALL 從嚴格匹配記錄中保留最多 3 筆 ETA 班次

#### Scenario: 嚴格匹配缺失時降級匹配 ETA
- **WHEN** ETA 響應中沒有 `route`、`stop`、`dir` 和 `seq` 均匹配且 `eta` 非空可解析的記錄
- **AND** ETA 響應中存在 `route`、`stop` 和 `dir` 均匹配且 `eta` 非空可解析的記錄
- **THEN** 系統 SHALL 使用這些降級匹配記錄
- **AND** 系統 SHALL 從降級匹配記錄中保留最多 3 筆 ETA 班次

#### Scenario: 降級匹配仍要求路線站點和方向一致
- **WHEN** ETA 響應中的記錄 `seq` 與 boardingSeq 不一致
- **AND** 該記錄的 `route`、`stop` 或 `dir` 任一字段不匹配首程資訊
- **THEN** 系統 SHALL NOT 使用該記錄計算或展示首程候車班次

#### Scenario: ETA 班次排序
- **WHEN** 系統取得 1 筆或更多匹配 ETA 記錄
- **THEN** 系統 SHALL 優先按 `eta_seq` 升序排列班次
- **AND** 若 `eta_seq` 缺失或不可解析，系統 SHALL 使用 `eta` 時間升序作為兜底排序
- **AND** 系統 SHALL 最多保留排序後的前 3 筆班次

#### Scenario: 計算每班候車分鐘數
- **WHEN** 系統獲得匹配 ETA 的 ISO 時間
- **THEN** 系統 SHALL 用 ETA 時間減當前系統時間計算剩餘時間
- **AND** 剩餘時間小於等於 0 秒時該班候車分鐘數 SHALL 為 `0`
- **AND** 剩餘時間大於 0 秒時該班候車分鐘數 SHALL 按分鐘向上取整

#### Scenario: 保留 ETA 展示資料
- **WHEN** 系統解析匹配 ETA 記錄
- **THEN** 每筆可展示班次 SHALL 保留班次序號、候車分鐘數、ETA 時間、`HH:mm` 到達時刻和可用的 `dest_tc`
- **AND** 若 `rmk_tc` 非空，系統 SHALL 保留該備註供班次面板展示
- **AND** 系統 SHALL 保留 `generated_timestamp` 或 `data_timestamp` 供更新時間展示

#### Scenario: ETA 不可用
- **WHEN** P2P stop map 找不到上車站點、ETA 響應沒有嚴格匹配或降級匹配的非空可解析記錄、網絡請求失敗或響應解析失敗
- **THEN** 系統 SHALL 將該路線候車時間標記為不可用

### Requirement: 候车时间排序

系統 SHALL 支持按候車時間排序，排序比較值 SHALL 使用每條路線首程第 1 班 ETA 的候車分鐘數，並在 ETA 漸進更新期間保持排序結果可理解。

#### Scenario: 默認仍按總耗時升序
- **WHEN** 用戶完成一次路線查詢且系統展示初始結果列表
- **THEN** 系統 SHALL 默認按總耗時分鐘數升序展示
- **AND** 系統 SHALL NOT 因後台 ETA 更新改變默認總耗時排序

#### Scenario: 按候車時間升序排序
- **WHEN** 用戶選擇候車時間排序並切換到升序
- **THEN** 系統 SHALL 將已有可用第 1 班候車時間的路線按分鐘數升序排序
- **AND** 第 2 班或第 3 班候車時間 SHALL NOT 參與排序比較
- **AND** 候車時間為查詢中或暫無車輛的路線 SHALL 排在可用候車時間之後

#### Scenario: 按候車時間降序排序
- **WHEN** 用戶再次選擇候車時間排序並切換到降序
- **THEN** 系統 SHALL 將已有可用第 1 班候車時間的路線按分鐘數降序排序
- **AND** 第 2 班或第 3 班候車時間 SHALL NOT 參與排序比較
- **AND** 候車時間為查詢中或暫無車輛的路線 SHALL 仍排在可用候車時間之後

#### Scenario: 候車時間排序中後台更新
- **WHEN** 當前排序字段是候車時間且後台 ETA 更新某條路線的班次資料
- **THEN** 系統 SHALL 使用該路線最新第 1 班候車分鐘數重新排序並刷新列表
- **AND** 系統 SHALL NOT 因第 2 班或第 3 班變化單獨改變排序
