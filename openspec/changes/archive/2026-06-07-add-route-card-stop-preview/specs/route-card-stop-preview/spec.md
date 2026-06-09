## ADDED Requirements

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

### Requirement: 使用公開 API 推導預覽站點名稱
系統 SHALL 使用 DATA.GOV.HK 城巴公開 API 推導卡片預覽所需的上車站與下車站名稱。

#### Scenario: 通過 route-stop 推導 stop id
- **WHEN** 系統獲得預覽站點的 company、公開 route、direction path 和 station seq
- **THEN** 系統 SHALL 請求 `https://rt.data.gov.hk/v2/transport/citybus/route-stop/{company}/{route}/{direction}`
- **AND** 系統 SHALL 在響應 data 中查找 `seq == station seq` 的記錄
- **AND** 系統 SHALL 使用該記錄的 `stop` 作為 stop id

#### Scenario: 通過 stop 查詢站名
- **WHEN** 系統獲得預覽站點 stop id
- **THEN** 系統 SHALL 請求 DATA.GOV.HK 城巴 `stop/{stop_id}` 站點資料
- **AND** 系統 SHALL 使用繁體中文站名作為卡片站點預覽名稱

#### Scenario: 成功建立站點預覽
- **WHEN** 系統成功解析同一路線的上車 stop id、下車 stop id 和對應站名
- **THEN** 系統 SHALL 為該路線生成站點預覽
- **AND** 站點預覽 SHALL 包含上車站顯示名與下車站顯示名

#### Scenario: 任一站點推導失敗
- **WHEN** 上車站或下車站任一方無法推導 stop id、查詢站名失敗、響應為空或解析失敗
- **THEN** 系統 SHALL 將該路線站點預覽視為不可用
- **AND** 系統 SHALL NOT 影響該路線的主卡片結果展示

### Requirement: 緩存與去重站點預覽請求
系統 SHALL 對站點預覽所需的 route-stop、stop name 和 preview 結果做進程內成功緩存，並在同一次查詢中去重網絡請求。

#### Scenario: route-stop 成功結果緩存 1 天
- **WHEN** 系統成功查詢某個 company、route 和 direction path 的 route-stop 資料
- **THEN** 系統 SHALL 在 App 進程內緩存該結果 1 天
- **AND** 1 天內再次解析相同 company、route 和 direction path 時 SHALL 優先使用緩存

#### Scenario: stop name 成功結果緩存 1 天
- **WHEN** 系統成功查詢某個 stop id 的站名
- **THEN** 系統 SHALL 按 company、stop id 和語言在 App 進程內緩存該站名 1 天
- **AND** 1 天內再次需要相同站名時 SHALL 優先使用緩存

#### Scenario: preview 成功結果緩存 1 天
- **WHEN** 系統成功生成某個 `rawInfo + lang` 對應的站點預覽
- **THEN** 系統 SHALL 在 App 進程內緩存該預覽 1 天
- **AND** 1 天內再次綁定相同 `rawInfo + lang` 的路線卡片時 SHALL 優先使用緩存

#### Scenario: 失敗結果不緩存
- **WHEN** route-stop 查詢失敗、stop 查詢失敗、響應為空、缺少 station seq、缺少站名或解析失敗
- **THEN** 系統 SHALL NOT 緩存該失敗結果
- **AND** 後續重新查詢或重試時 SHALL 重新嘗試生成站點預覽

#### Scenario: 同一次查詢聚合唯一請求
- **WHEN** 多條候選路線需要相同 company、route、direction path 或相同 stop id 的資料
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
- **THEN** 該路線卡片 SHALL 在路線文字與底部信息區之間展示單行 `上車 A站  →  下車 B站`
- **AND** `A站` SHALL 為首程上車站顯示名
- **AND** `B站` SHALL 為末程下車站顯示名

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
