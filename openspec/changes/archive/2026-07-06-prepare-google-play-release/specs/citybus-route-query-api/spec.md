## MODIFIED Requirements

### Requirement: 通过 Citybus 接口查询点到点巴士路线

系統 SHALL 使用 Citybus 點到點路線查詢接口，根據已保存路線或臨時查詢的起點和終點經緯度查詢可選巴士路線，且 SHALL NOT 為該請求設置靜態瀏覽器 header 或 cookie。

#### Scenario: 發起路線查詢請求
- **WHEN** 用戶在主界面選擇已保存路線並點擊查詢
- **THEN** 系統請求 `https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php`
- **AND** 請求 SHALL 攜帶 `slat=<起點緯度>`、`slon=<起點經度>`、`elat=<終點緯度>`、`elon=<終點經度>`、`t=<查詢時間>`、`leg=2`、`m1=<查詢模式>`、`l=0`
- **AND** 系統 MAY 依既有策略加入 `ws=1.3` 或切換 `m1=T/F/W`

#### Scenario: 查詢時間使用香港時間
- **WHEN** 系統構造路線查詢請求
- **THEN** `t` 參數 MUST 使用目前香港時間並格式化為 `yyyy-MM-dd HH:mm`

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

#### Scenario: Header 清理後驗證路線語義一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `ppsearch_p3.php` SHALL 使用 10 個有效請求樣本對比刪除 header/cookie 前後的業務語義
- **AND** 每個樣本 SHALL 返回 HTTP 200 並包含可解析路線候選標記
- **AND** 路線卡片語義與 `showroutep2p(...)` 參數 SHALL 一致
- **AND** 驗證 SHALL 允許上游 `shareinfo` 動態時間戳造成完整 body hash 不一致
