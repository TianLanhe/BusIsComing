## ADDED Requirements

### Requirement: 行程與搜尋頁復用地點控制邏輯並保留獨立版面契約
系統 SHALL 讓新增、編輯及複製行程恢復既有行程輸入版面，讓搜尋頁保留獨立緊湊輸入器，並只在地點搜尋、候選選擇、距離計算、定位協調及資料模型層復用共同邏輯。

#### Scenario: 行程頁顯示歷史起終點版面
- **WHEN** 用戶開啟新增、編輯或複製行程頁且候選清單未展開
- **THEN** 起點與終點 SHALL 各使用至少 `56dp` 高的 Material outlined input
- **AND** 輸入文字 SHALL 保持 `16dp` 水平內距
- **AND** 兩個欄位 SHALL 保持約 `14dp` 基礎間距
- **AND** 欄位 SHALL 常駐顯示既有三語地點選擇輔助文案

#### Scenario: 行程頁定位工具保持 Material 尾端行為
- **WHEN** 行程頁顯示起點定位工具
- **THEN** 定位圖示 SHALL 使用起點 `TextInputLayout` 的 Material 尾端圖示位置
- **AND** 圖示 SHALL 在輸入框內保持垂直居中
- **AND** 定位工具 SHALL 保持可辨識的無障礙描述及至少 `48dp` 觸控能力

#### Scenario: 行程頁顯示地點搜尋載入狀態
- **WHEN** 行程頁任一欄位正在匹配地點
- **THEN** 系統 SHALL 在該欄位下方顯示獨立的約 `18dp` 進度指示器及三語匹配文字
- **AND** 系統 SHALL NOT 以定位尾端工具槽取代該載入列
- **AND** 另一欄位的輸入、已選地點及 helper SHALL 保持不變

#### Scenario: 行程頁候選保持歷史間距
- **WHEN** 行程頁顯示起點或終點候選
- **THEN** 候選容器 SHALL 與所屬欄位保持約 `6dp` 間距
- **AND** 候選數量、自適應高度、Google attribution、錯誤及已選 Place 行為 SHALL 保持既有行程頁契約

#### Scenario: 行程頁交換按鈕只改變背景
- **WHEN** 行程頁沒有展開任何候選清單
- **THEN** 交換按鈕 SHALL 保持歷史位置、內距及至少 `48dp` 觸控範圍
- **AND** 按鈕背景 SHALL 使用透明／borderless ripple
- **AND** 系統 SHALL NOT 因透明背景改變按鈕的幾何中心或欄位寬度

#### Scenario: 行程頁候選展開時隱藏交換按鈕
- **WHEN** 行程頁任一地點候選清單展開
- **THEN** 交換按鈕 SHALL 依歷史行為隱藏
- **AND** 候選清單 SHALL NOT 與交換按鈕重疊

#### Scenario: 搜尋頁保留獨立緊湊輸入器
- **WHEN** 用戶開啟搜尋頁
- **THEN** 搜尋頁 SHALL 保留既有約 `8dp` 欄位間距、候選上限及保存／查詢按鈕位置
- **AND** 起點定位與交換操作 SHALL 各使用穩定的 `48dp` 工具槽
- **AND** `24dp` 可見圖示或 loading SHALL 在所屬工具槽內水平及垂直居中
- **AND** 搜尋頁 SHALL NOT 強制行程頁採用相同幾何

#### Scenario: 兩個頁面沿用相同候選與定位資料契約
- **WHEN** 行程頁或搜尋頁執行地點搜尋、選擇候選、計算距離或使用目前位置
- **THEN** 系統 SHALL 沿用共同的地點控制器、候選資料模型、距離 formatter 及定位協調契約
- **AND** 系統 SHALL NOT 因版面分離而改變 Citybus 地點搜尋、Google attribution、Place 座標或保存資料格式

## MODIFIED Requirements

### Requirement: 搜尋恢復流程靜默補取候選距離快照
系統 SHALL 在搜尋頁首次建立、恢復已有內容、返回搜尋 Tab 或手動定位成功時維護獨立的目前位置快照，並在已有前台定位權限及定位能力可用時非阻塞提供給起點與終點候選距離展示。

#### Scenario: 首次進入搜尋頁請求距離快照
- **WHEN** 搜尋頁在目前主畫面實例首次建立
- **AND** App 已有粗略或精確前台定位權限且系統定位可用
- **THEN** 系統 SHALL 非阻塞請求一次手機目前位置快照
- **AND** 起點與終點輸入、交換、保存及搜尋 SHALL 保持可操作
- **AND** 系統 SHALL NOT 僅為候選距離再次請求定位權限

#### Scenario: 距離快照與自動填入起點並行
- **WHEN** 搜尋頁同時執行首次目前位置地址填入與候選距離快照流程
- **THEN** 兩個流程 SHALL 共用可用的原始目前座標或等價位置結果
- **AND** Reverse Geocoding 成功、失敗或被使用者起點操作作廢 SHALL NOT 清除仍有效的候選距離快照
- **AND** 距離流程 SHALL NOT 改寫起點名稱或觸發額外 Geocoding

#### Scenario: 恢復已有搜尋內容
- **WHEN** 搜尋頁恢復已有起點、終點文字或已提交查詢上下文
- **AND** App 已有前台定位權限且系統定位可用
- **THEN** 系統 SHALL 非阻塞取得或重新套用目前位置快照
- **AND** 系統 SHALL NOT 改寫已恢復的起點或終點
- **AND** 系統 SHALL NOT 顯示欄位 loading 或阻止輸入、交換、保存或搜尋

#### Scenario: 手動定位更新候選距離快照
- **WHEN** 用戶手動定位起點且系統取得有效目前位置
- **THEN** 系統 SHALL 更新頁面持有的候選距離快照
- **AND** 起點與終點候選 SHALL 使用同一最新手機位置基準
- **AND** 候選距離 SHALL NOT 以已選起點或終點座標作為基準

#### Scenario: 距離快照成功
- **WHEN** 搜尋頁取得有效目前位置快照
- **THEN** 起點與終點候選 SHALL 在右側顯示定位圖示與格式化距離
- **AND** 系統 SHALL NOT 改變 Citybus 候選順序、目前焦點、滾動位置或已選地點
- **AND** 候選無障礙描述 SHALL 包含完整地點名稱及距離

#### Scenario: 距離快照不可用
- **WHEN** 沒有前台定位權限、定位服務不可用、請求失敗、逾時或返回空值
- **THEN** 候選 SHALL 繼續顯示地點名稱並允許選擇
- **AND** 系統 SHALL 靜默省略距離且 SHALL NOT 顯示 `0 米`
- **AND** 系統 SHALL NOT 顯示 Toast、helper、錯誤卡或強制跳轉設定

#### Scenario: 過期快照返回
- **WHEN** 搜尋頁 View 已銷毀、語言或頁面 generation 已改變，或新位置請求已取代舊請求後舊 callback 才返回
- **THEN** 系統 SHALL 忽略舊 callback
- **AND** 舊 callback SHALL NOT 更新新編輯器、重新打開候選或改寫任何輸入

## REMOVED Requirements

### Requirement: 行程與搜尋頁共用起終點編輯器結構
**Reason**: 完整幾何共用改變了新增／編輯／複製行程已確認的 helper、Material 尾端定位、載入列、間距及交換可見性，超出原需求並造成實機回歸。

**Migration**: 行程頁恢復自己的歷史 XML 與互動；搜尋頁保留搜尋專用複合 View。兩頁改以共同地點控制器、候選資料與定位協調邏輯維持行為一致。
