## MODIFIED Requirements

### Requirement: 使用 P2P stop map 推導預覽站點名稱
系統 SHALL 使用 Citybus P2P stop map 推導卡片預覽所需的上車站與下車站名稱，並 SHALL 保留當前語言回應中的合法站名字符。

#### Scenario: 通過 P2P stop map 推導首末站
- **WHEN** 系統成功取得某條路線 `rawInfo + lang` 對應的 P2P stop map
- **THEN** 系統 SHALL 使用第一段 bus leg 的 `routeVariant + boardingSeq` 查找上車站
- **AND** 系統 SHALL 使用最後一段 bus leg 的 `routeVariant + alightingSeq` 查找下車站
- **AND** 系統 SHALL 使用兩個站點的展示名生成站點預覽

#### Scenario: 英文站名包含撇號
- **WHEN** 英文 P2P stop map 的上車站或下車站包含由 JavaScript 轉義表示的英文撇號
- **THEN** 系統 SHALL 顯示包含還原撇號的上車站及下車站預覽
- **AND** 系統 SHALL NOT 因該字符隱藏整行站點預覽

#### Scenario: 預覽站點對齊 route variant
- **WHEN** P2P route variant 與公開 route-stop 站序不一致
- **THEN** 系統 SHALL 使用 P2P stop map 中對應 `routeVariant + seq` 的站點
- **AND** 系統 SHALL NOT 使用 DATA.GOV.HK `route-stop` 的公開 route seq 覆蓋站點預覽

#### Scenario: 任一站點推導失敗
- **WHEN** P2P stop map 不可用、上車站或下車站任一方不存在、站名缺失或解析失敗
- **THEN** 系統 SHALL 將該路線站點預覽視為不可用
- **AND** 系統 SHALL NOT 影響該路線的主卡片結果展示
