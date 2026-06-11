## ADDED Requirements

### Requirement: 使用 P2P stop map 推導預覽站點名稱
系統 SHALL 使用 Citybus P2P stop map 推導卡片預覽所需的上車站與下車站名稱。

#### Scenario: 通過 P2P stop map 推導首末站
- **WHEN** 系統成功取得某條路線 `rawInfo + lang` 對應的 P2P stop map
- **THEN** 系統 SHALL 使用第一段 bus leg 的 `routeVariant + boardingSeq` 查找上車站
- **AND** 系統 SHALL 使用最後一段 bus leg 的 `routeVariant + alightingSeq` 查找下車站
- **AND** 系統 SHALL 使用兩個站點的展示名生成站點預覽

#### Scenario: 預覽站點對齊 route variant
- **WHEN** P2P route variant 與公開 route-stop 站序不一致
- **THEN** 系統 SHALL 使用 P2P stop map 中對應 `routeVariant + seq` 的站點
- **AND** 系統 SHALL NOT 使用 DATA.GOV.HK `route-stop` 的公開 route seq 覆蓋站點預覽

#### Scenario: 任一站點推導失敗
- **WHEN** P2P stop map 不可用、上車站或下車站任一方不存在、站名缺失或解析失敗
- **THEN** 系統 SHALL 將該路線站點預覽視為不可用
- **AND** 系統 SHALL NOT 影響該路線的主卡片結果展示

## MODIFIED Requirements

### Requirement: 緩存與去重站點預覽請求
系統 SHALL 對站點預覽所需的 P2P stop map 和 preview 結果做進程內成功緩存，並在同一次查詢中去重網絡請求。

#### Scenario: P2P stop map 成功結果緩存 1 天
- **WHEN** 系統成功解析某個 `rawInfo + lang` 的 P2P stop map
- **THEN** 系統 SHALL 在 App 進程內緩存該結果 1 天
- **AND** 1 天內再次解析相同 `rawInfo + lang` 時 SHALL 優先使用緩存

#### Scenario: preview 成功結果緩存 1 天
- **WHEN** 系統成功生成某個 `rawInfo + lang` 對應的站點預覽
- **THEN** 系統 SHALL 在 App 進程內緩存該預覽 1 天
- **AND** 1 天內再次綁定相同 `rawInfo + lang` 的路線卡片時 SHALL 優先使用緩存

#### Scenario: 失敗結果不緩存
- **WHEN** P2P stop map 查詢失敗、響應為空、缺少 station seq、缺少站名或解析失敗
- **THEN** 系統 SHALL NOT 緩存該失敗結果
- **AND** 後續重新查詢或重試時 SHALL 重新嘗試生成站點預覽

#### Scenario: 同一次查詢聚合唯一請求
- **WHEN** 多條候選路線需要相同 `rawInfo + lang` 的 P2P stop map
- **THEN** 系統 SHALL 將相同資料請求去重
- **AND** 系統 SHALL 將一次成功結果應用到所有依賴該資料的路線卡片

#### Scenario: 預覽補全並發受限
- **WHEN** 系統需要為多條路線補全站點預覽
- **THEN** 系統 SHALL 使用有上限的並發執行方式
- **AND** 系統 SHALL NOT 為每張路線卡片建立無界線程或無界網絡請求

### Requirement: 路線卡片漸進展示站點預覽
系統 SHALL 在不阻塞路線列表首屏展示的前提下，於路線卡片中漸進展示成功解析的站點預覽。

#### Scenario: 路線列表先於站點預覽展示
- **WHEN** 路線查詢成功並返回一條或多條路線
- **THEN** 系統 SHALL 先展示路線卡片主信息
- **AND** 系統 SHALL NOT 等待站點預覽補全完成後才展示路線列表

#### Scenario: 卡片展示成功站點預覽
- **WHEN** 某條路線的站點預覽補全成功
- **THEN** 該路線卡片 SHALL 在路線文字與底部信息區之間展示只包含站名的單行站點預覽
- **AND** 預覽 SHALL 包含首程上車站顯示名與末程下車站顯示名
- **AND** 預覽 SHALL NOT 展示 `上車` 或 `下車` 字樣

#### Scenario: 站點預覽未完成或不可用
- **WHEN** 某條路線站點預覽尚未完成、缺少元數據或補全失敗
- **THEN** 該路線卡片 SHALL 隱藏站點預覽行
- **AND** 該路線卡片 SHALL 繼續展示路線、價格、耗時、候車與步行距離

#### Scenario: 超長站名保持卡片可讀
- **WHEN** 上車站名或下車站名超過卡片單行可用寬度
- **THEN** 系統 SHALL 使用省略、權重或同等策略保持預覽為單行可讀
- **AND** 站點預覽 SHALL NOT 與路線、候車、價格、耗時或步行距離文字重疊

#### Scenario: 舊查詢預覽結果被忽略
- **WHEN** 用戶在站點預覽補全期間發起新查詢、切換已保存路線、清空結果或離開主界面
- **THEN** 系統 SHALL 忽略舊查詢後續站點預覽回調
- **AND** 舊查詢結果 SHALL NOT 回填到新列表或已清空列表

## REMOVED Requirements

### Requirement: 使用公開 API 推導預覽站點名稱
**Reason**: DATA.GOV.HK `route-stop` 只按公開路線號和方向提供靜態站序，不能區分 Citybus P2P 內部 route variant，已在 `8X-THR-1` 樣例中造成下車站錯位。
**Migration**: 使用 `citybus-p2p-stop-map` 中的 `showstops2.php?r=<rawInfo>&l=<lang>` 解析結果推導卡片預覽站點。
