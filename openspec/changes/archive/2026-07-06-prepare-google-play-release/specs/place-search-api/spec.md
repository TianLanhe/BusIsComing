## MODIFIED Requirements

### Requirement: 通过 Citybus 接口搜索地点

系統 SHALL 使用 Citybus HTTP 接口按用戶輸入關鍵詞搜索候選地點，且 SHALL NOT 為該請求設置靜態瀏覽器 header 或 cookie。

#### Scenario: 發起地點搜索請求
- **WHEN** 用戶輸入地點搜索關鍵詞
- **THEN** 系統請求 `https://mobile.citybus.com.hk/nwp3/bsearch_p3.php`
- **AND** 請求 SHALL 攜帶 `l=0`、`q=<關鍵詞>`、`limit=100`、`timestamp=<目前毫秒級時間戳>`

#### Scenario: 不攜帶瀏覽器 header 或 Cookie
- **WHEN** 系統發起 Citybus 地點搜索請求
- **THEN** 系統 SHALL NOT 顯式設置 `Cookie`
- **AND** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`X-Requested-With`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 使用靜態 session、ad、consent 或 tracking cookie

#### Scenario: Header 清理後驗證地點搜索一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `bsearch_p3.php` SHALL 使用 10 個有效關鍵詞樣本對比刪除 header/cookie 前後的響應
- **AND** 每個樣本 SHALL 返回 HTTP 200
- **AND** 每個樣本 SHALL 包含可解析地點候選或明確無結果格式
- **AND** 業務簽名或 body hash SHALL 一致
