## ADDED Requirements

### Requirement: 幾何 candidate 經目前 consumer 端點校驗後才發布
系統 SHALL 將尚未以目前 consumer 可靠上下車端點校驗的 geometry candidate 保持為內部狀態，且 SHALL NOT 在校驗成功前把該 candidate 發布為可渲染成功內容。

#### Scenario: candidate 先於詳情端點到達
- **WHEN** 某 geometry key 的共享 candidate 已完成但目前 consumer 尚無可靠上下車端點
- **THEN** 系統 SHALL 暫存該 candidate 並等待端點
- **AND** 地圖 SHALL NOT 繪製該 candidate 或把該分段標記為成功
- **AND** 系統 SHALL NOT 因等待端點而重發相同 geometry key

#### Scenario: 晚到端點校驗成功
- **WHEN** 可靠詳情端點稍後到達且與暫存 candidate 位於可接受距離
- **THEN** 系統 SHALL 把該 geometry key 發布為可渲染成功並增量加入路線線條
- **AND** 系統 SHALL 復用既有 candidate 而不重複請求 `getlinep2p.php`

#### Scenario: 晚到端點校驗失敗
- **WHEN** 可靠詳情端點稍後到達且與暫存 candidate 明顯不一致
- **THEN** 系統 SHALL 將該 geometry key 標記為局部失敗並依既有 cache 契約移除錯誤 candidate
- **AND** 地圖 SHALL 從未顯示該不可靠路線線條
- **AND** 其他已驗證幾何與可靠站點 SHALL 保持不變

#### Scenario: 多個 consumer 獨立發布
- **WHEN** 多個 consumer 共用同一 in-flight candidate 但具有不同端點或 generation
- **THEN** 每個 consumer SHALL 以自己的可靠端點與目前 generation 決定是否發布
- **AND** 一個 consumer 的成功或失敗 SHALL NOT 直接決定另一 consumer 的可渲染狀態

#### Scenario: 過期失敗晚於新成功
- **WHEN** 某 geometry key 的新 generation 已發布校驗成功內容
- **AND** 舊 generation 的端點失敗、timeout 或取消 callback 稍後到達
- **THEN** 系統 SHALL 忽略舊 callback
- **AND** 地圖 SHALL 保留新 generation 的成功路線線條
