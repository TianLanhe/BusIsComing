## MODIFIED Requirements

### Requirement: 臨時查詢起點和終點通過候選地點選擇
系統 SHALL 在搜尋 destination 的表單中，透過可輸入控件和表單內嵌候選列表選擇一次性查詢的起點和終點地點。

#### Scenario: 搜尋輸入關鍵字觸發搜尋
- **WHEN** 用戶在搜尋頁的起點或終點輸入框輸入至少 1 個字符
- **THEN** 系統 SHALL 在 debounce 後發起 Citybus 地點搜尋

#### Scenario: 搜尋展示候選地點
- **WHEN** 搜尋頁地點搜尋返回候選結果
- **THEN** 系統 SHALL 在對應輸入框下方展示 Citybus 候選地點
- **AND** 系統 SHALL 不以 bottom sheet 展開狀態作為候選列表可見的前提

#### Scenario: 搜尋選擇候選地點
- **WHEN** 用戶從搜尋頁內嵌候選列表中選擇一個地點
- **THEN** 系統 SHALL 將該地點設為對應已選 Place
- **AND** 系統 SHALL 在輸入框顯示該地點名稱

#### Scenario: 搜尋修改已選地點文本
- **WHEN** 用戶已在搜尋頁選擇候選地點後又手動修改輸入框文本
- **THEN** 系統 SHALL 清除該輸入框對應的已選 Place
- **AND** 系統 SHALL 要求用戶重新從候選列表確認地點

### Requirement: 臨時查詢校驗起點和終點
系統 SHALL 在搜尋頁發起一次性查詢或保存為常用路線前校驗起點和終點均有效。

#### Scenario: 未選擇起點
- **WHEN** 用戶未從候選列表選擇搜尋起點並嘗試查詢或保存
- **THEN** 系統 SHALL 在起點輸入框顯示校驗錯誤
- **AND** 系統 SHALL 不發起查詢或保存

#### Scenario: 未選擇終點
- **WHEN** 用戶未從候選列表選擇搜尋終點並嘗試查詢或保存
- **THEN** 系統 SHALL 在終點輸入框顯示校驗錯誤
- **AND** 系統 SHALL 不發起查詢或保存

#### Scenario: 起點終點相同
- **WHEN** 用戶在搜尋頁選擇的起點和終點名稱、緯度、經度均完全相同
- **THEN** 系統 SHALL 顯示既有起終點不可相同的校驗錯誤
- **AND** 系統 SHALL 不發起查詢或保存

### Requirement: 臨時查詢支持交換起點和終點
系統 SHALL 在搜尋頁提供圖示按鈕交換一次性查詢的起點和終點。

#### Scenario: 交換已選地點
- **WHEN** 用戶在搜尋頁已從候選列表確認起點和終點，且點擊交換控件
- **THEN** 系統 SHALL 交換兩個已選 Place、輸入文本與候選選擇狀態
- **AND** 系統 SHALL 不發起新的 Citybus 地點搜尋或路線查詢

#### Scenario: 交換未確認輸入文本
- **WHEN** 搜尋起點或終點存在未從候選列表確認的輸入文本，且用戶點擊交換控件
- **THEN** 系統 SHALL 沿用既有校驗規則拒絕交換或清楚提示需先確認候選
- **AND** 系統 SHALL 不把未確認文本視為有效 Place

#### Scenario: 交換控件可觸達
- **WHEN** 用戶查看或通過無障礙服務訪問搜尋交換控件
- **THEN** 控件 SHALL 提供 `交換起點和終點` 或等效繁體中文描述
- **AND** 控件 SHALL 具有不小於 48dp 的可觸控區域
