## ADDED Requirements

### Requirement: 路線卡片區分 ETA 空結果與技術故障
系統 SHALL 在路線結果卡片中以當前 App 語言分開展示暫無車輛與候車暫不可用，並 SHALL 使用精簡的候車載入文案。

#### Scenario: 展示暫無車輛
- **WHEN** 路線 ETA 查詢成功但沒有匹配的有效班次
- **THEN** 繁體卡片 SHALL 顯示 `暫無車輛`
- **AND** 簡體卡片 SHALL 顯示 `暂无车辆`
- **AND** 英文卡片 SHALL 顯示 `No live arrivals`

#### Scenario: 展示候車暫不可用
- **WHEN** 路線 ETA 因首程資料、stop map、上車站、網絡或回應解析等技術原因不可用
- **THEN** 繁體卡片 SHALL 顯示 `候車暫不可用`
- **AND** 簡體卡片 SHALL 顯示 `候车暂不可用`
- **AND** 英文卡片 SHALL 顯示 `Arrivals unavailable`
- **AND** 卡片 SHALL NOT 將該狀態顯示為暫無車輛

#### Scenario: 展示候車載入狀態
- **WHEN** 首程 ETA 正在查詢
- **THEN** 繁體卡片 SHALL 顯示 `候車查詢中`
- **AND** 簡體卡片 SHALL 顯示 `候车查询中`
- **AND** 英文卡片 SHALL 顯示 `Checking arrivals`

#### Scenario: 非可用狀態維持卡片可讀性
- **WHEN** 卡片以任一支援語言顯示暫無車輛或候車暫不可用
- **THEN** 文案 SHALL 保持在既有右側候車資訊區內可理解
- **AND** 系統 SHALL NOT 以縮小核心字體或侵入左側路線／站點預覽區作為適配方式
