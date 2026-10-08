## MODIFIED Requirements

### Requirement: 目前位置可作為起點
系統 SHALL 允許純新增路線與臨時查詢使用目前位置自動填入起點，並允許用戶透過起點輸入框內的定位按鈕手動改用目前位置。GMS 可用時系統 SHALL 保持既有 Google 定位行為；只有 GMS 明確不可用時才 SHALL 使用 Android 系統定位後備，且兩條路徑 SHALL 共用原有短時限及輸入保護。

#### Scenario: 純新增路線自動填入目前位置起點
- **WHEN** 用戶打開純新增路線頁面
- **AND** 起點沒有預填值
- **AND** 用戶尚未編輯或選擇起點
- **AND** 系統可取得目前位置並透過 Google reverse geocoding 解析地點名稱
- **THEN** 系統 SHALL 將起點設定為目前位置對應的 `Place`
- **AND** 該 `Place` SHALL 使用目前位置的原始 GPS 緯度與經度
- **AND** 輸入框 SHALL 顯示解析後的真實地址名稱
- **AND** 系統 SHALL NOT 自動聚焦終點、彈出鍵盤或發起路線查詢

#### Scenario: 臨時查詢自動填入目前位置起點
- **WHEN** 用戶打開臨時查詢底部彈層
- **AND** 起點尚未由用戶輸入或選擇
- **AND** 系統可取得目前位置並透過 Google reverse geocoding 解析地點名稱
- **THEN** 系統 SHALL 將臨時查詢起點設定為目前位置對應的 `Place`
- **AND** 系統 SHALL 保持終點由用戶手動輸入或選擇
- **AND** 系統 SHALL NOT 自動發起臨時查詢

#### Scenario: 編輯與複製路線不自動覆蓋起點
- **WHEN** 用戶打開編輯路線頁面或複製路線頁面
- **THEN** 系統 SHALL 保留既有或預填起點
- **AND** 系統 SHALL NOT 因目前位置自動覆蓋起點
- **AND** 系統 SHALL NOT 因目前位置起點功能自動請求定位權限

#### Scenario: 起點定位按鈕可手動使用目前位置
- **WHEN** 用戶在新增、編輯、複製路線或臨時查詢中點擊起點輸入框右側定位按鈕
- **THEN** 系統 SHALL 嘗試取得目前位置並透過 Google reverse geocoding 解析地點名稱
- **AND** 若尚未授權前台定位，系統 SHALL 可請求 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`
- **AND** 成功後系統 SHALL 以目前位置對應的 `Place` 替換起點
- **AND** 系統 SHALL NOT 為終點輸入框提供同等定位按鈕

#### Scenario: 起點定位按鈕不壓縮輸入框
- **WHEN** 新增、編輯、複製路線或臨時查詢顯示起點輸入框
- **THEN** 系統 SHALL 將定位按鈕放在起點輸入框內的 trailing／end icon 位置或等效內嵌位置
- **AND** 定位按鈕觸控目標 SHALL 至少為 48dp
- **AND** 定位按鈕 SHALL 提供無障礙描述 `使用我的位置`
- **AND** 系統 SHALL NOT 將定位按鈕做成會壓縮起點輸入框寬度的外部並排按鈕

#### Scenario: 定位成功後才替換既有起點
- **WHEN** 起點已有選定地點或輸入文字
- **AND** 用戶點擊起點定位按鈕
- **AND** 目前位置取得、名稱解析、`Place` 建立任一步驟尚未成功完成
- **THEN** 系統 SHALL 保留原起點或原輸入文字
- **AND** 系統 SHALL 僅在完整成功後替換起點

#### Scenario: 用戶操作使遲到定位結果失效
- **WHEN** 系統正在自動或手動取得目前位置作為起點
- **AND** 用戶在結果返回前編輯、清空或選擇其他起點
- **THEN** 系統 SHALL 將該次目前位置結果視為過期
- **AND** 系統 SHALL NOT 用遲到結果覆蓋用戶最新操作

#### Scenario: 目前位置起點使用真實地點名稱解析
- **WHEN** 系統成功取得目前 GPS 位置
- **AND** Google reverse geocoding resolver 成功解析地址名稱
- **THEN** 系統 SHALL 將目前位置解析為使用真實地址名稱的 `Place`
- **AND** 後續查詢與保存 SHALL 使用真實地址名稱搭配原始 GPS 緯度與經度
- **AND** 系統 SHALL NOT 使用固定名稱 `目前位置附近` 作為成功解析結果
- **AND** 系統 SHALL NOT 調用 Android `Geocoder`、香港政府 API 或其他非 Google reverse geocoding 服務

#### Scenario: 目前位置起點流程有明確超時
- **WHEN** 系統正在建立目前位置起點 `Place`
- **THEN** 定位階段 SHALL 最多等待 3 秒
- **AND** 地點名稱解析階段 SHALL 最多等待 3 秒
- **AND** 整體流程 SHALL 最多等待 5 秒
- **AND** 超時後返回失敗並套用對應的自動或手動失敗行為

#### Scenario: 自動目前位置失敗
- **WHEN** 純新增路線或臨時查詢的自動目前位置流程因未授權、拒絕、定位關閉、定位失敗、定位超時或名稱解析失敗而未能建立 `Place`
- **THEN** 起點 SHALL 保持空白
- **AND** 系統 SHALL 允許用戶手動輸入並從 Citybus 候選中選擇起點
- **AND** 系統 SHALL 顯示輕量 helper `暫時無法取得目前位置，請手動選擇起點`

#### Scenario: 自動定位拒絕狀態阻止後續自動彈窗
- **WHEN** 用戶已在主頁、純新增路線或臨時查詢的自動定位權限請求中拒絕授權
- **AND** 用戶再次打開純新增路線或臨時查詢
- **THEN** 系統 SHALL NOT 自動彈出定位權限請求
- **AND** 起點 SHALL 保持空白，等待用戶手動輸入、選擇或點擊起點定位按鈕

#### Scenario: 手動定位按鈕可在拒絕後恢復
- **WHEN** 用戶先前拒絕自動定位權限請求
- **AND** 用戶點擊起點定位按鈕
- **THEN** 系統 SHALL 將該操作視為明確授權意圖
- **AND** 若 Android 仍允許顯示權限對話框，系統 SHALL 可再次請求前台定位權限
- **AND** 若 Android 不再顯示權限對話框，系統 SHALL 提供前往系統設定的恢復路徑

#### Scenario: 手動目前位置失敗
- **WHEN** 用戶點擊起點定位按鈕
- **AND** 系統未能建立目前位置 `Place`
- **THEN** 系統 SHALL 保留原起點或原輸入文字
- **AND** 系統 SHALL 使用 Toast 或等效短提示說明失敗

#### Scenario: 不在候選列表中顯示目前位置固定項
- **WHEN** 起點或終點候選列表展開
- **THEN** 候選列表 SHALL 只顯示 Citybus 地點搜尋結果
- **AND** 系統 SHALL NOT 在候選列表頂部加入固定 `我的位置`、`目前位置附近`、loading、錯誤或重試項

#### Scenario: GMS 可用時不啟用後備
- **WHEN** GMS 正常可用，而目前位置請求成功、沒有 fix 或暫時失敗
- **THEN** 系統 SHALL 沿用既有 Fused 位置來源及原失敗回饋
- **AND** 系統 SHALL NOT 並行系統定位、改變權限時機或轉換地址來源

#### Scenario: GMS 明確不可用時取得目前位置
- **WHEN** GMS 缺失或停用等明確不可用狀態，且用戶具有適用前台權限
- **THEN** 系統 SHALL 嘗試裝置可用的 Android 系統定位，取得有效座標後沿用 Google HTTP 地址解析
- **AND** 自動及手動請求 SHALL 保持定位 3 秒、地址 3 秒及整體 5 秒上限
- **AND** 沒有可用供應者或逾時 SHALL 套用既有失敗行為，保留原輸入及手動選地點路徑
