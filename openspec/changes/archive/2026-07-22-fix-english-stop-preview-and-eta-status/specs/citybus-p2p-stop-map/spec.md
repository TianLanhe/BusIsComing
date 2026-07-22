## MODIFIED Requirements

### Requirement: 解析 P2P stop map
系統 SHALL 從 `showstops2.php` 響應中的 `addstoponmap(...)` 調用解析結構化停站資料，並 SHALL 按 JavaScript 字串邊界及轉義語義解析調用範圍與參數。

#### Scenario: 解析單程 stop map
- **WHEN** `showstops2.php` 響應包含單段路線的 `addstoponmap(...)` 調用
- **THEN** 系統 SHALL 解析每個站點的 stop id、站序、route variant、方向、站名、經緯度和起終標記
- **AND** 系統 SHALL 使用逗號前第一段站名作為展示名
- **AND** 系統 SHALL 按 `routeVariant + seq` 定位該段站點

#### Scenario: 解析多程 stop map
- **WHEN** `showstops2.php` 響應包含兩段或更多 route variant 的站點
- **THEN** 系統 SHALL 按 `rawInfo` 的 bus leg 順序為站點分配 leg index
- **AND** 同名、同 seq 或同 stop id 的站點 SHALL 依據 leg index 和 route variant 分開保存

#### Scenario: 解析包含轉義英文撇號的站名
- **WHEN** `addstoponmap(...)` 的站名參數包含 `King\'s Road` 一類轉義單引號
- **THEN** 系統 SHALL 將該單引號視為字串內容而非參數結束
- **AND** 系統 SHALL 還原並保留展示名中的英文撇號
- **AND** 系統 SHALL 繼續解析該站點的完整 route variant、站序和 stop id

#### Scenario: 解析字串內逗號括號及反斜線
- **WHEN** `addstoponmap(...)` 的字串參數包含轉義反斜線、逗號或左右括號
- **THEN** 系統 SHALL 只在字串外切分參數和判斷函式調用結束
- **AND** 字串內容 SHALL NOT 導致後續欄位錯位或站點被捨棄

#### Scenario: 解析 8X 錯位樣例
- **WHEN** 系統解析 `8X-THR-1` 且 `showstops2.php` 響應包含 `seq=20` 的站點
- **THEN** 系統 SHALL 將 `seq=20` 解析為 stop id `001364` 和站名 `長康街`
- **AND** 系統 SHALL NOT 使用公開 `8X/outbound` 中 `seq=20` 的 `001280` 作為該 P2P 站點

#### Scenario: 無有效站點
- **WHEN** `showstops2.php` 返回空內容、缺少 `addstoponmap(...)` 或解析失敗
- **THEN** 系統 SHALL 將該 P2P stop map 視為不可用
