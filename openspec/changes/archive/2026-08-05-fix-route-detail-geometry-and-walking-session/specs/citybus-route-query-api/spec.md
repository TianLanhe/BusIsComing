## MODIFIED Requirements

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

## ADDED Requirements

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
