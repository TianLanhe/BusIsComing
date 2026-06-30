## MODIFIED Requirements

### Requirement: 按需查询 Citybus P2P 路线详情
系統 SHALL 在用戶點擊路線卡片後，使用該路線的 P2P 詳情元數據按需請求 Citybus 路線詳情，且 SHALL NOT 為該請求設置靜態瀏覽器 header。

#### Scenario: 構造詳情請求
- **WHEN** 系統獲得路線詳情查詢元數據 `rawInfo`、`ginfo`、`lid` 和 `lang`
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php`
- **AND** 請求 SHALL 攜帶 `info=<rawInfo>`、`ginfo=<ginfo>`、`lid=<lid>` 和 `l=<lang>`

#### Scenario: 詳情請求不攜帶瀏覽器 header
- **WHEN** 系統發起 `getp2pstopinroute.php` 請求
- **THEN** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 顯式設置 `Cookie`

#### Scenario: 點擊後展示載入狀態
- **WHEN** 用戶點擊路線卡片且詳情請求尚未完成
- **THEN** 底部彈層 SHALL 展示路線詳情載入狀態
- **AND** 載入狀態 SHALL NOT 清空主界面已有路線結果

#### Scenario: 詳情接口不使用公共 API 兜底
- **WHEN** Citybus P2P 詳情請求失敗、超時、返回空內容或解析失敗
- **THEN** 系統 SHALL 展示詳情失敗狀態
- **AND** 系統 SHALL NOT 調用 DATA.GOV.HK route-stop 或 stop 接口重建路線詳情

#### Scenario: Header 清理後驗證詳情一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `getp2pstopinroute.php` SHALL 使用 10 個有效詳情樣本對比刪除 header 前後的響應
- **AND** 每個樣本 SHALL 返回 HTTP 200
- **AND** 每個樣本 SHALL 包含可解析路線詳情標記
- **AND** 業務簽名或 body hash SHALL 一致
