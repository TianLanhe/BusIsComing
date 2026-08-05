# citybus-route-query-api Specification

## Purpose
TBD - created by archiving change citybus-route-query. Update Purpose after archive.
## Requirements

### Requirement: 通过 Citybus 接口查询点到点巴士路线
系統 SHALL 使用 Citybus 點到點路線查詢接口，根據常用 destination 的已保存行程或搜尋 destination 的一次性起終點及目前 App 語言查詢可選巴士路線；系統 SHALL NOT 發送靜態、瀏覽器、廣告或追蹤 Cookie，但 SHALL 擷取 Citybus 回應為本次搜尋建立的必要短期 session。

#### Scenario: 發起路線查詢請求
- **WHEN** 用戶在常用 destination 查詢已保存行程，或在搜尋 destination 查詢一次性起終點
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

#### Scenario: 初始查詢不攜帶瀏覽器 header 或 Cookie
- **WHEN** 系統發起 `ppsearch_p3.php` 點到點查詢
- **THEN** 系統 SHALL NOT 顯式設置 `Cookie`
- **AND** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 使用預置 session、ad、consent 或 tracking cookie

#### Scenario: 只擷取 Citybus 必要搜尋 session
- **WHEN** `ppsearch_p3.php` 回應包含一個或多個 `Set-Cookie`
- **THEN** 系統 SHALL 只擷取 Citybus 同源的 `PHPSESSID`
- **AND** 系統 SHALL 丟棄廣告、consent、tracking 或未知 Cookie
- **AND** 原始 session 值 SHALL NOT 進入路線診斷日誌

#### Scenario: 路線請求日誌脫敏
- **WHEN** debug build 記錄 Citybus 路線查詢診斷
- **THEN** 日誌 SHALL NOT 輸出完整 cURL、完整 URL、完整 query string、headers 或 cookies
- **AND** 日誌 SHALL NOT 包含完整 `slat`、`slon`、`elat`、`elon`、`rawInfo`、查詢時間、`PHPSESSID` 或 session reference
- **AND** release build SHALL NOT 輸出路線查詢診斷日誌

#### Scenario: 三語路線請求語義驗證
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `ppsearch_p3.php` SHALL 覆蓋繁體、簡體及英文的有效請求樣本
- **AND** 每個樣本 SHALL 返回 HTTP 200 並包含目前語言的可解析候選標記
- **AND** 路線、車費、總耗時、步行距離、`rawInfo`、`lid`、`showroutep2p(...)` 與 session 擷取語義 SHALL 正確
- **AND** 驗證 SHALL 允許上游動態時間造成完整 body hash 不一致

#### Scenario: Header 清理後驗證路線語義一致
- **WHEN** 實作完成後比較最小請求與瀏覽器樣本
- **THEN** `ppsearch_p3.php` SHALL 使用 10 個有效請求樣本對比刪除靜態 header／預置 cookie 前後的業務語義
- **AND** 每個樣本 SHALL 返回 HTTP 200 並包含可解析路線候選標記
- **AND** 路線卡片語義與 `showroutep2p(...)` 參數 SHALL 一致
- **AND** 驗證 SHALL 允許上游 `shareinfo` 動態時間戳及新建 `PHPSESSID` 造成完整 body hash 不一致

### Requirement: Citybus 搜尋會話按 m1 隔離並與候選詳情關聯
系統 SHALL 分別保存 `m1=T/F/W` 回應建立的短期 Citybus session，並讓每個候選詳情使用產生其 `lid` 的同一 session；系統 SHALL NOT 把 session 身分當作路線業務身分。

#### Scenario: 三個 m1 回傳不同 session
- **WHEN** 並行 `m1=T/F/W` 回應各自提供不同 `PHPSESSID`
- **THEN** 系統 SHALL 建立三個互不覆蓋的 session context
- **AND** 任一模式的詳情請求 SHALL NOT 使用另一模式的 `PHPSESSID`

#### Scenario: 候選保存不透明 session reference
- **WHEN** 系統從某一模式成功解析候選的 `rawInfo`、`ginfo`、`lid` 與乘車段
- **THEN** 該候選 SHALL 保存指向同一模式 session context 的不透明 reference
- **AND** 候選模型 SHALL NOT 暴露或持久化原始 `PHPSESSID`

#### Scenario: 聚合去重保留完整關聯
- **WHEN** 多個 m1 結果被判定為同一可見候選而只保留一個代表
- **THEN** 系統 SHALL 一併保留同一原始候選的 session reference 與 `lid`
- **AND** 系統 SHALL NOT 將一個模式的 `lid` 與另一模式的 session 組合

#### Scenario: session registry 生命週期
- **WHEN** session context 超過短期 TTL、所屬查詢被取代或 App 進程結束
- **THEN** 系統 SHALL 使該 session reference 失效並清理原始 `PHPSESSID`
- **AND** 系統 SHALL NOT 將 session 寫入 SQLite、檔案、備份或分析資料

### Requirement: Citybus 詳情會話失效後只恢復一次
系統 SHALL 在候選的 session 缺失或失效時，以原搜尋語義受控重建一次 session，且 SHALL NOT 直接把新 session 中相同 `lid` 視為同一路線。

#### Scenario: 進程內 session 仍有效
- **WHEN** 用戶開啟詳情且候選 session reference 仍可解析
- **THEN** 系統 SHALL 直接使用該 session 與原 `lid`
- **AND** 系統 SHALL NOT 額外重做點到點搜尋

#### Scenario: session 缺失或上游回傳會話空資料
- **WHEN** session reference 無法解析，或詳情回應符合 session 缺失而站點仍可解析的空 timetable／空步行形態
- **THEN** 系統 SHALL 使用原起終點、目前語言及原 m1 重做一次 `ppsearch_p3.php`
- **AND** 恢復查詢 SHALL 使用新的香港查詢時間
- **AND** 恢復查詢 SHALL NOT 更新來源列表、排序或常用行程使用次數

#### Scenario: 恢復結果匹配原候選
- **WHEN** 恢復查詢返回一個乘車段 route variant、上下車站序及路線鏈均與原候選一致的結果
- **THEN** 系統 SHALL 使用該結果自己的新 session reference 與 `lid` 重試一次詳情
- **AND** 系統 SHALL NOT 沿用原 session 的 `lid`

#### Scenario: 恢復無匹配或再次失敗
- **WHEN** 恢復查詢沒有可靠匹配候選，或重試後仍缺少有效 session 資料
- **THEN** 系統 SHALL 停止自動恢復
- **AND** 詳情 SHALL 降級展示可用站點、已知欄位與不完整步行狀態
- **AND** 系統 SHALL NOT 進入循環搜尋

#### Scenario: 恢復期間查詢已過期
- **WHEN** 恢復搜尋或詳情重試尚未完成而頁面被銷毀、語言版本改變或新的詳情 generation 取代舊請求
- **THEN** 系統 SHALL 取消舊工作或忽略舊回應
- **AND** 舊 session SHALL NOT 覆蓋目前候選關聯

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
