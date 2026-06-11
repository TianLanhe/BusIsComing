## ADDED Requirements

### Requirement: 結果排序控件保持緊湊展示
系統 SHALL 在查詢結果區直接展示排序控件，不在排序項上方展示獨立標題文字。

#### Scenario: 展示排序控件
- **WHEN** 用戶查詢出一條或多條巴士路線結果且排序控件可見
- **THEN** 系統 SHALL 直接展示可點擊排序項
- **AND** 系統 SHALL NOT 在排序項上方展示獨立的 `排序` 標題文字
- **AND** 排序項 SHALL 保留字段文案和當前排序方向

#### Scenario: 切換排序
- **WHEN** 用戶點擊任一排序項
- **THEN** 系統 SHALL 按既有排序規則切換排序字段或排序方向
- **AND** 系統 SHALL NOT 因移除 `排序` 標題改變排序結果
