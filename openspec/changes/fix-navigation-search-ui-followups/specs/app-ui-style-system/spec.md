## ADDED Requirements

### Requirement: 底部導覽選中膠囊與標籤保持清楚分離
系統 SHALL 在三個頂層 destination 的底部導覽中保留穩定的選中膠囊、圖示和文字層級，且 SHALL NOT 讓選中背景與標籤重疊。

#### Scenario: 一般字體顯示選中項
- **WHEN** 系統以字體縮放 1.0 顯示底部導覽選中項
- **THEN** 選中膠囊 SHALL 保持約 `64×32dp`
- **AND** 圖示 SHALL 保持約 `24dp`
- **AND** 膠囊底緣與標籤頂緣 SHALL 保留約 `5dp` 的可見空隙
- **AND** 選中標籤 SHALL 使用 `13sp` 粗體，未選中標籤 SHALL 使用 `12sp` 正常字重
- **AND** 選中狀態 SHALL NOT 改變三個 Tab 的寬度或導覽總高度

#### Scenario: 大字體顯示底部導覽
- **WHEN** 系統以字體縮放 1.3 或 2.0 顯示底部導覽
- **THEN** 導覽列 SHALL 允許增加必要高度以完整容納圖示、膠囊與標籤
- **AND** 膠囊、圖示及標籤 SHALL NOT 互相重疊或被裁切
- **AND** 每個 Tab SHALL 保持至少 `48dp` 可操作觸控範圍

#### Scenario: 切換深淺色與選中項
- **WHEN** 用戶切換底部導覽 destination 或 App 深淺色模式
- **THEN** 系統 SHALL 保持相同膠囊與標籤幾何
- **AND** 系統 SHALL 只使用對應主題的語意色更新選中與未選中狀態
- **AND** destination 切換完成後目前選中項 SHALL 持續可辨識

### Requirement: 吸頂結果控制器沿用頁面背景
系統 SHALL 讓常用與搜尋頁的吸頂排序／摘要控制器使用透明背景並沿用目前頁面背景色，使結果區在淺色及深色模式保持連續。

#### Scenario: 淺色模式顯示結果控制器
- **WHEN** 常用或搜尋頁在淺色模式顯示吸頂結果控制器
- **THEN** 控制器 SHALL 直接顯示頁面 `app_page_background`
- **AND** 控制器 SHALL NOT 顯示白色或其他固定 surface 色矩形背景

#### Scenario: 深色模式顯示結果控制器
- **WHEN** 常用或搜尋頁在深色模式顯示吸頂結果控制器
- **THEN** 控制器 SHALL 使用同一透明結構
- **AND** 排序、摘要、選中及停用狀態 SHALL 使用既有 `bus_*` 語意色保持可讀對比
