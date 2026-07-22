# citybus-route-query-api Specification

## Purpose
TBD - created by archiving change citybus-route-query. Update Purpose after archive.
## Requirements

### Requirement: 通过 Citybus 接口查询点到点巴士路线
系統 SHALL 使用 Citybus 點到點路線查詢接口，根據常用 destination 的已保存路線或搜尋 destination 的一次性起終點及目前 App 語言查詢可選巴士路線，且 SHALL NOT 為該請求設置靜態瀏覽器 header 或 cookie。

#### Scenario: 發起路線查詢請求
- **WHEN** 用戶在常用 destination 查詢已保存路線，或在搜尋 destination 查詢一次性起終點
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php`
- **AND** 請求 SHALL 攜帶 `slat=<起點緯度>`、`slon=<起點經度>`、`elat=<終點緯度>`、`elon=<終點經度>`、`t=<查詢時間>`、`ws=1.3`、`leg=2`、`m1=<查詢模式>`
- **AND** 繁體、簡體、英文 SHALL 分別攜帶 `l=0`、`l=2`、`l=1`
- **AND** 系統 SHALL 依既有策略切換 `m1=T/F/W` 並聚合結果

#### Scenario: 查詢時間使用香港時間
- **WHEN** 系統構造路線查詢請求
- **THEN** `t` 參數 MUST 使用目前香港時間並格式化為 `yyyy-MM-dd HH:mm`

#### Scenario: 保留具業務語義的參數
- **WHEN** 系統構造 Citybus 路線查詢請求
- **THEN** 系統 SHALL 保留 `t`、`ws=1.3`、`leg=2`、`m1` 及目前語言 `l`
- **AND** 系統 SHALL NOT 因單次樣本未顯示差異而移除上述參數

#### Scenario: 不攜帶瀏覽器 header 或 Cookie
- **WHEN** 系統發起 Citybus 路線查詢請求
- **THEN** 系統 SHALL NOT 顯式設置 `Cookie`
- **AND** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 使用靜態 session、ad、consent 或 tracking cookie

#### Scenario: 路線請求日誌脫敏
- **WHEN** debug build 記錄 Citybus 路線查詢診斷
- **THEN** 日誌 SHALL NOT 輸出完整 cURL、完整 URL、完整 query string、headers 或 cookies
- **AND** 日誌 SHALL NOT 包含完整 `slat`、`slon`、`elat`、`elon`、`rawInfo` 或查詢時間
- **AND** release build SHALL NOT 輸出路線查詢診斷日誌

#### Scenario: 三語路線請求語義驗證
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `ppsearch_p3.php` SHALL 覆蓋繁體、簡體及英文的有效請求樣本
- **AND** 每個樣本 SHALL 返回 HTTP 200 並包含目前語言的可解析候選標記
- **AND** 路線、車費、總耗時、步行距離、`rawInfo` 與 `showroutep2p(...)` 業務語義 SHALL 正確
- **AND** 驗證 SHALL 允許上游動態時間造成完整 body hash 不一致

#### Scenario: Header 清理後驗證路線語義一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `ppsearch_p3.php` SHALL 使用 10 個有效請求樣本對比刪除 header/cookie 前後的業務語義
- **AND** 每個樣本 SHALL 返回 HTTP 200 並包含可解析路線候選標記
- **AND** 路線卡片語義與 `showroutep2p(...)` 參數 SHALL 一致
- **AND** 驗證 SHALL 允許上游 `shareinfo` 動態時間戳造成完整 body hash 不一致

### Requirement: 解析 Citybus 路线查询 HTML
系統 SHALL 從繁體、簡體或英文 Citybus HTML 的 `routelist2` 區域解析可選巴士路線列表。

#### Scenario: 定位路線列表容器
- **WHEN** 任一支援語言的 Citybus 路線查詢返回 HTML
- **THEN** 系統 SHALL 定位 `id` 去除首尾空格後等於 `routelist2` 的元素作為路線列表容器

#### Scenario: 解析多段巴士路線
- **WHEN** `routelist2` 中某個候選路線 table 表示多段巴士路線
- **THEN** 系統 SHALL 提取每段巴士路線號並用 `→` 連接為展示路線名稱

#### Scenario: 累計路線總車費
- **WHEN** 任一支援語言的候選路線包含一段或多段巴士車費
- **THEN** 系統 SHALL 將每段車費累計為該候選路線的總車費，單位為 HKD

#### Scenario: 解析三語預計總耗時
- **WHEN** 候選路線包含目前上游語言的預計總耗時標籤
- **THEN** 系統 SHALL 將分鐘數解析為預計路線總耗時
- **AND** 系統 SHALL 支援 Citybus 繁體、簡體及英文等價標籤

#### Scenario: 解析三語步行距離
- **WHEN** 候選路線包含目前上游語言的步行距離標籤
- **THEN** 系統 SHALL 解析步行距離米數
- **AND** 系統 SHALL 支援 Citybus 繁體、簡體及英文等價標籤

#### Scenario: 預計汽車到站時間階段性取值
- **WHEN** 系統成功解析候選路線的預計路線總耗時
- **THEN** 系統 SHALL 將預計汽車到站時間分鐘數設置為預計路線總耗時分鐘數

#### Scenario: 定位路线列表容器
- **WHEN** Citybus 路线查询返回 HTML
- **THEN** 系统 SHALL 定位 `id` 去除首尾空格后等于 `routelist2` 的元素作为路线列表容器

#### Scenario: 解析多段巴士路线
- **WHEN** `routelist2` 中某个候选路线 table 表示多段巴士路线
- **THEN** 系统 SHALL 提取每段巴士路线号并用 `→` 连接为展示路线名称

#### Scenario: 累计路线总价格
- **WHEN** 候选路线包含一段或多段巴士价格
- **THEN** 系统 SHALL 将每段价格累计为该候选路线的总价格，单位为 HKD

#### Scenario: 解析预计路线总耗时
- **WHEN** 候选路线包含 `預計N分鐘` 或等价总耗时信息
- **THEN** 系统 SHALL 将 `N` 解析为预计路线总耗时分钟数

#### Scenario: 预计汽车到站时间阶段性取值
- **WHEN** 系统成功解析候选路线的预计路线总耗时
- **THEN** 系统 SHALL 将预计汽车到站时间分钟数设置为预计路线总耗时分钟数

### Requirement: 处理路线查询结果状态

系统 SHALL 区分路线查询成功、无结果和失败状态。

#### Scenario: 路线查询成功且有结果
- **WHEN** Citybus 返回可解析的候选路线
- **THEN** 系统 SHALL 返回所有有效候选路线结果

#### Scenario: 路线查询成功但无可用路线
- **WHEN** Citybus 返回的 `routelist2` 存在但没有任何有效候选路线
- **THEN** 系统 SHALL 返回空路线结果列表

#### Scenario: 路线查询失败
- **WHEN** 路线查询网络请求失败、HTTP 状态码非 2xx、HTML 缺少 `routelist2` 或 HTML 格式无法解析
- **THEN** 系统 SHALL 将其视为路线查询失败

### Requirement: Citybus 路線結果受語言版本保護
系統 SHALL 只聚合、cache 及展示與目前查詢 generation 和語言版本一致的 Citybus 路線結果。

#### Scenario: 路線查詢期間切換語言
- **WHEN** `m1=T/F/W` 任一舊語言查詢仍在進行且用戶切換語言
- **THEN** 系統 SHALL 取消或忽略該 generation 的全部模式結果
- **AND** 系統 SHALL NOT 將舊語言模式結果與新語言結果聚合

#### Scenario: 目前語言路線解析失敗
- **WHEN** 目前語言 Citybus HTML 缺少可解析結構或格式錯誤
- **THEN** 系統 SHALL 以目前 App 語言回報路線查詢失敗
- **AND** 系統 SHALL NOT 改用 `l=0` 靜默重試

### Requirement: Citybus 詳情參數只在證明安全後精簡
系統 SHALL 預設使用 `info`、`ginfo`、`lid`、`l` 請求 `getp2pstopinroute.php`，並只在真實三語語義等價獲充分證明後移除個別參數。

#### Scenario: 預設建立路線詳情請求
- **WHEN** 系統需要取得 Citybus P2P 路線詳情
- **THEN** 請求 SHALL 攜帶 `info`、`ginfo`、`lid` 及目前語言 `l`
- **AND** 系統 SHALL NOT 預先假定 `ginfo` 或 `lid` 無作用

#### Scenario: 驗證候選參數可移除
- **WHEN** 實作嘗試移除 `ginfo` 或 `lid`
- **THEN** 系統 SHALL 逐項執行繁體、簡體、英文及單程、轉乘的真實 A/B 驗證
- **AND** 精簡請求 SHALL 穩定返回並成功解析
- **AND** 完整路線段、方向、上下車站及途經站點 SHALL 與基準請求語義一致

#### Scenario: 任一樣本受影響
- **WHEN** 移除候選參數令任一語言或路線樣本的返回、解析或 App 使用語義不同
- **THEN** 系統 SHALL 保留該參數
- **AND** 驗證 SHALL 記錄脫敏的可重現證據
