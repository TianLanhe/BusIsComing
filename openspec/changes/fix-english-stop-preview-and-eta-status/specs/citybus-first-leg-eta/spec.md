## ADDED Requirements

### Requirement: 區分首程 ETA 空結果與技術故障
系統 SHALL 將成功取得有效 ETA 資料結構但沒有匹配班次的結果標記為暫無車輛，並 SHALL 將無法完成有效 ETA 查詢的技術故障標記為候車暫不可用。

#### Scenario: 有效 ETA 回應沒有匹配班次
- **WHEN** ETA 請求成功且回應包含可辨識的 `data` 陣列
- **AND** 回應中沒有符合嚴格或降級匹配規則的非空可解析 ETA 記錄
- **THEN** 系統 SHALL 將候車狀態標記為暫無車輛
- **AND** 系統 SHALL NOT 將其標記為技術故障

#### Scenario: P2P stop map 請求失敗
- **WHEN** 系統因網絡或上游錯誤無法取得 P2P stop map
- **THEN** 系統 SHALL 將候車狀態標記為候車暫不可用
- **AND** 系統 SHALL 保留 stop map 請求失敗的結構化原因

#### Scenario: P2P stop map 回應無效
- **WHEN** `showstops2.php` 回應為空、缺少有效 `addstoponmap(...)` 或無法解析
- **THEN** 系統 SHALL 將候車狀態標記為候車暫不可用
- **AND** 系統 SHALL 保留 stop map 回應無效的結構化原因

#### Scenario: 找不到首程上車站
- **WHEN** 系統成功解析 P2P stop map 但無法以首程 `routeVariant + boardingSeq` 找到上車站
- **THEN** 系統 SHALL 將候車狀態標記為候車暫不可用
- **AND** 系統 SHALL 保留上車站缺失的結構化原因

#### Scenario: ETA 請求失敗
- **WHEN** 系統已取得 stop id 但 ETA 網絡請求失敗
- **THEN** 系統 SHALL 將候車狀態標記為候車暫不可用
- **AND** 系統 SHALL 保留 ETA 請求失敗的結構化原因

#### Scenario: ETA 回應資料結構無效
- **WHEN** ETA 請求返回的內容缺少可辨識的 `data` 陣列或無法視為有效 ETA 回應
- **THEN** 系統 SHALL 將候車狀態標記為候車暫不可用
- **AND** 系統 SHALL 保留 ETA 回應無效的結構化原因

#### Scenario: 缺少可解析首程資料
- **WHEN** 候選路線缺少發起首程 ETA 所需的元數據
- **THEN** 系統 SHALL 將候車狀態標記為候車暫不可用
- **AND** 系統 SHALL 保留首程元數據缺失的結構化原因

#### Scenario: 英文轉義站名可繼續查詢 ETA
- **WHEN** 英文 P2P stop map 的首程上車站名包含轉義撇號且 route variant、站序及 stop id 有效
- **THEN** 系統 SHALL 使用解析出的 stop id 發起首程 ETA 請求
- **AND** 系統 SHALL NOT 因站名中的撇號把候車狀態誤標為暫無車輛或候車暫不可用
