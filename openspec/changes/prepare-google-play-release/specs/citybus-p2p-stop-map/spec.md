## MODIFIED Requirements

### Requirement: 查詢 Citybus P2P stop map
系統 SHALL 使用 Citybus P2P `rawInfo` 和語言參數查詢與點到點 route variant 對齊的停站資料，且 SHALL NOT 為該請求設置靜態瀏覽器 header。

#### Scenario: 構造 showstops2 請求
- **WHEN** 系統獲得候選路線的完整 `rawInfo` 和 `lang`
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/showstops2.php`
- **AND** 請求 SHALL 攜帶 `r=<rawInfo>` 和 `l=<lang>`

#### Scenario: showstops2 不攜帶瀏覽器 header
- **WHEN** 系統發起 `showstops2.php` 請求
- **THEN** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 顯式設置 `Cookie`

#### Scenario: 缺少 rawInfo 時不查詢
- **WHEN** 候選路線缺少完整 `rawInfo`
- **THEN** 系統 SHALL NOT 發起 `showstops2.php` 請求
- **AND** 依賴 P2P stop map 的站點預覽和 ETA stopId SHALL 視為不可用

#### Scenario: Header 清理後驗證 stop map 一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `showstops2.php` SHALL 使用 10 個有效 `rawInfo + lang` 樣本對比刪除 header 前後的響應
- **AND** 每個樣本 SHALL 返回 HTTP 200
- **AND** 每個樣本 SHALL 包含可解析 `addstoponmap(...)` 停站資料
- **AND** 業務簽名或 body hash SHALL 一致
