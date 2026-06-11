## ADDED Requirements

### Requirement: 查詢 Citybus P2P stop map
系統 SHALL 使用 Citybus P2P `rawInfo` 和語言參數查詢與點到點 route variant 對齊的停站資料。

#### Scenario: 構造 showstops2 請求
- **WHEN** 系統獲得候選路線的完整 `rawInfo` 和 `lang`
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/showstops2.php`
- **AND** 請求 SHALL 攜帶 `r=<rawInfo>` 和 `l=<lang>`

#### Scenario: 缺少 rawInfo 時不查詢
- **WHEN** 候選路線缺少完整 `rawInfo`
- **THEN** 系統 SHALL NOT 發起 `showstops2.php` 請求
- **AND** 依賴 P2P stop map 的站點預覽和 ETA stopId SHALL 視為不可用

### Requirement: 解析 P2P stop map
系統 SHALL 從 `showstops2.php` 響應中的 `addstoponmap(...)` 調用解析結構化停站資料。

#### Scenario: 解析單程 stop map
- **WHEN** `showstops2.php` 響應包含單段路線的 `addstoponmap(...)` 調用
- **THEN** 系統 SHALL 解析每個站點的 stop id、站序、route variant、方向、站名、經緯度和起終標記
- **AND** 系統 SHALL 使用逗號前第一段站名作為展示名
- **AND** 系統 SHALL 按 `routeVariant + seq` 定位該段站點

#### Scenario: 解析多程 stop map
- **WHEN** `showstops2.php` 響應包含兩段或更多 route variant 的站點
- **THEN** 系統 SHALL 按 `rawInfo` 的 bus leg 順序為站點分配 leg index
- **AND** 同名、同 seq 或同 stop id 的站點 SHALL 依據 leg index 和 route variant 分開保存

#### Scenario: 解析 8X 錯位樣例
- **WHEN** 系統解析 `8X-THR-1` 且 `showstops2.php` 響應包含 `seq=20` 的站點
- **THEN** 系統 SHALL 將 `seq=20` 解析為 stop id `001364` 和站名 `長康街`
- **AND** 系統 SHALL NOT 使用公開 `8X/outbound` 中 `seq=20` 的 `001280` 作為該 P2P 站點

#### Scenario: 無有效站點
- **WHEN** `showstops2.php` 返回空內容、缺少 `addstoponmap(...)` 或解析失敗
- **THEN** 系統 SHALL 將該 P2P stop map 視為不可用

### Requirement: 緩存與去重 P2P stop map
系統 SHALL 對成功解析的 P2P stop map 進行進程內緩存並在同一次查詢中去重請求。

#### Scenario: 成功結果緩存 1 天
- **WHEN** 系統成功解析某個 `rawInfo + lang` 對應的 P2P stop map
- **THEN** 系統 SHALL 在 App 進程內緩存該結果 1 天
- **AND** 1 天內再次需要相同 `rawInfo + lang` 時 SHALL 優先使用緩存

#### Scenario: 失敗結果不緩存
- **WHEN** `showstops2.php` 請求失敗、返回空內容或解析失敗
- **THEN** 系統 SHALL NOT 緩存該失敗結果
- **AND** 後續重新查詢時 SHALL 重新嘗試解析

#### Scenario: 同一次查詢去重
- **WHEN** 多條候選路線共享相同 `rawInfo + lang`
- **THEN** 系統 SHALL 只發起一次對應 `showstops2.php` 請求
- **AND** 系統 SHALL 將成功結果應用到所有共享該 key 的路線

### Requirement: P2P stop map 不使用公開 route-stop 兜底
系統 SHALL 避免在 P2P stop map 不可用時使用 DATA.GOV.HK `route-stop` 重建 route variant 停站。

#### Scenario: showstops2 不可用時不回退 route-stop
- **WHEN** P2P stop map 查詢或解析不可用
- **THEN** 系統 SHALL NOT 調用 DATA.GOV.HK `route-stop/{company}/{route}/{direction}` 作為運行時 fallback
- **AND** 依賴該 stop map 的卡片站點和 ETA stopId SHALL 視為不可用

#### Scenario: route-stop 方案僅作留檔
- **WHEN** 開發者查看 Citybus ETA 推導文檔
- **THEN** 文檔 SHALL 保留 `route-stop/{company}/{route}/{directionPath}` 推導方式作為歷史方案和觀察參考
- **AND** 文檔 SHALL 明確標註該方式不是新運行時 fallback
