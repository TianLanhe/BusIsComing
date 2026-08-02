## ADDED Requirements

### Requirement: 路線卡片以語意矢量圖示標示耗時與步行
系統 SHALL 在路線結果卡片的輔助資訊區以已確認的矢量鬧鐘及步行人物圖示分別標示耗時與步行距離，同時保持既有數值來源、排序及完整無障礙語義。

#### Scenario: 卡片展示耗時圖示
- **WHEN** 系統展示路線結果卡片的總耗時
- **THEN** 系統 SHALL 在分鐘數前展示細線小鬧鐘矢量圖示
- **AND** 圖示 SHALL 使用次要文字語意色
- **AND** 系統 SHALL 繼續使用既有 `durationMinutes` 數值

#### Scenario: 卡片展示步行圖示
- **WHEN** 系統展示路線結果卡片的步行距離
- **THEN** 系統 SHALL 在距離前展示已確認的四塊獨立實心輪廓步行人物矢量圖示
- **AND** App SHALL NOT 直接打包或縮放低解像度參考點陣圖作為該圖示
- **AND** 系統 SHALL 繼續使用既有 `walkingDistanceMeters` 數值

#### Scenario: 卡片步行距離與排序保持不變
- **WHEN** 詳情接口可取得額外換乘步行距離
- **THEN** 路線卡片 SHALL NOT 因此預取詳情、回填完整步行合計或改變既有步行排序
- **AND** 卡片 SHALL 保持 `ppsearch_p3.php` 解析得到的步行距離語義

#### Scenario: 輔助技術讀取圖示指標
- **WHEN** TalkBack 聚焦路線卡片的輔助資訊區
- **THEN** 系統 SHALL 讀出目前語言的完整「耗時 N 分鐘」與「步行 N 米」語義
- **AND** 鬧鐘與步行圖示 SHALL NOT 被當作無名稱裝飾或重複朗讀

#### Scenario: 不同模式與尺寸保持清晰
- **WHEN** 路線卡片在淺色、深色、約 360dp 寬度或大字體下顯示
- **THEN** 圖示 SHALL 使用模式對應 tint 並保持清晰邊界
- **AND** 圖示、數值與相鄰價格 SHALL NOT 重疊或超出卡片
