# route-card-stop-preview Specification

## Purpose
TBD - created by archiving change add-route-card-stop-preview. Update Purpose after archive.
## Requirements
### Requirement: 解析路線卡片站點預覽元數據
系統 SHALL 從 Citybus 點到點候選路線的 `showroutep2p(...)` 中解析完整 P2P bus legs，用於推導路線卡片的上車站與下車站預覽。

#### Scenario: 解析單段路線 P2P plan
- **WHEN** 系統解析一條包含單段 `showroutep2p(...)` `rawInfo` 的候選路線
- **THEN** 系統 SHALL 解析該段 bus leg 的公司代碼、內部 route variant、公開路線號、上車站序、下車站序、方向代碼和 direction path
- **AND** 系統 SHALL 將該段 `boardingSeq` 作為卡片預覽上車站序
- **AND** 系統 SHALL 將該段 `alightingSeq` 作為卡片預覽下車站序

#### Scenario: 解析多段路線 P2P plan
- **WHEN** 系統解析一條包含兩段或更多 bus legs 的 `showroutep2p(...)` `rawInfo`
- **THEN** 系統 SHALL 按 `rawInfo` 中的順序解析所有 bus legs
- **AND** 系統 SHALL 使用第一段 bus leg 的 `boardingSeq` 作為卡片預覽上車站序
- **AND** 系統 SHALL 使用最後一段 bus leg 的 `alightingSeq` 作為卡片預覽下車站序

#### Scenario: 缺少可解析 P2P plan
- **WHEN** 候選路線可以解析路線、價格、耗時和步行距離，但缺少可解析的完整 P2P plan
- **THEN** 系統 SHALL 保留該路線結果
- **AND** 系統 SHALL 將該路線的站點預覽視為不可用

#### Scenario: 首程 ETA 復用完整 P2P plan
- **WHEN** 系統已成功解析完整 P2P plan
- **THEN** 系統 SHALL 從第一段 bus leg 派生首程 ETA 所需資料
- **AND** 系統 SHALL NOT 使用另一套不同規則重複解析 `showroutep2p(...)` 來生成首程 ETA 資料

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
系統 SHALL 在不阻塞路線列表首屏展示的前提下，於路線卡片中漸進展示成功解析的站點預覽，且站點預覽 SHALL 被限制在左側文本區內單行展示，並使用右側候車資訊區之前的最大可用寬度。

#### Scenario: 路線列表先於站點預覽展示
- **WHEN** 路線查詢成功並返回一條或多條路線
- **THEN** 系統 SHALL 先展示路線卡片主信息
- **AND** 系統 SHALL NOT 等待站點預覽補全完成後才展示路線列表

#### Scenario: 卡片展示成功站點預覽
- **WHEN** 某條路線的站點預覽補全成功
- **THEN** 該路線卡片 SHALL 在路線文字與底部信息區之間展示單行 `上車 A站  →  下車 B站`
- **AND** `A站` SHALL 為首程上車站顯示名
- **AND** `B站` SHALL 為末程下車站顯示名
- **AND** 站點預覽 SHALL 使用左側文本區可用寬度，不得侵入右側候車資訊區

#### Scenario: 站點預覽使用候車區前最大可用寬度
- **WHEN** 某條路線的站點預覽較長，且卡片右側展示主候車狀態、下一班摘要和通知鈴鐺
- **THEN** 站點預覽 SHALL 使用右側候車資訊區左邊界之前的最大可用單行寬度
- **AND** 站點預覽 SHALL NOT 提前縮短到明顯小於左側可用空間的寬度
- **AND** 站點預覽 SHALL NOT 與右側主候車狀態、下一班摘要或通知鈴鐺重疊

#### Scenario: 右側 padding 收斂後站點預覽延伸
- **WHEN** 卡片頂部右側視覺 padding 被收斂以對齊左側文字起點
- **THEN** 站點預覽 SHALL 可使用因此增加的左側文本可用寬度
- **AND** 站點預覽 SHALL 仍以候車文字欄左邊界作為避讓邊界
- **AND** 站點預覽 SHALL NOT 覆蓋、貼壓或遮擋主候車狀態與下一班摘要

#### Scenario: 站點預覽未完成或不可用
- **WHEN** 某條路線站點預覽尚未完成、缺少元數據或補全失敗
- **THEN** 該路線卡片 SHALL 隱藏站點預覽行
- **AND** 該路線卡片 SHALL 繼續展示路線、價格、耗時、候車與步行距離

#### Scenario: 長站名單行省略
- **WHEN** 上車站名或下車站名超過左側文本區單行可用寬度
- **THEN** 系統 SHALL 使用尾部省略、權重或同等策略保持預覽為單行可讀
- **AND** 站點預覽 SHALL NOT 換行展示
- **AND** 站點預覽 SHALL NOT 與路線、候車、下一班、鈴鐺、價格、耗時或步行距離文字重疊

#### Scenario: 大字體仍保持單行
- **WHEN** 用戶在系統字體縮放變大時查看包含站點預覽的結果卡片
- **THEN** 站點預覽 SHALL 仍保持單行展示
- **AND** 超出左側文本區的內容 SHALL 被省略
- **AND** 卡片頂部高度 SHALL NOT 因站點預覽換行而增加

#### Scenario: 舊查詢預覽結果被忽略
- **WHEN** 用戶在站點預覽補全期間發起新查詢、切換已保存路線、清空結果或離開主界面
- **THEN** 系統 SHALL 忽略舊查詢後續站點預覽回調
- **AND** 舊查詢結果 SHALL NOT 回填到新列表或已清空列表

### Requirement: 保留站點預覽造成的可見路線差異
系統 SHALL 在站點預覽成為卡片可見信息後，避免將首末站不同的候選路線錯誤合併。

#### Scenario: rawInfo 不同的候選路線不被合併
- **WHEN** 兩條候選路線的路線段、價格、耗時和步行距離相同，但可解析的 `rawInfo` 不同
- **THEN** 系統 SHALL 將它們保留為不同路線結果
- **AND** 系統 SHALL 允許它們分別生成站點預覽

#### Scenario: 首末站不同的候選路線不被合併
- **WHEN** 兩條候選路線的路線段、價格、耗時和步行距離相同，但推導出的上車站或下車站不同
- **THEN** 系統 SHALL 將它們保留為不同路線結果
- **AND** 系統 SHALL NOT 用其中一條路線的站點預覽覆蓋另一條路線

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

