## MODIFIED Requirements

### Requirement: 目前位置可作為起點
系統 SHALL 允許純新增路線與臨時查詢使用目前位置自動填入起點，並允許用戶透過起點輸入框內的定位按鈕手動改用目前位置。

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

## ADDED Requirements

### Requirement: 複製與編輯頁靜默預熱目前位置地址
系統 SHALL 在複製與編輯路線頁面中，於不打擾用戶且不覆蓋現有起點的前提下，預熱目前位置 snapshot 和 Google 地址名稱 cache。

#### Scenario: 已有定位權限時靜默預熱
- **WHEN** 用戶打開複製路線頁面或編輯路線頁面
- **AND** App 已具備前台定位權限
- **AND** 系統定位已開啟
- **THEN** 系統 SHALL 嘗試取得或復用目前位置 snapshot
- **AND** 系統 SHALL 將 snapshot 提供給起點與終點候選距離展示
- **AND** 系統 SHALL 使用該 snapshot 靜默預熱 Google 地址名稱 cache
- **AND** 系統 SHALL NOT 填入或替換起點輸入框
- **AND** 系統 SHALL NOT 顯示 Google Maps attribution
- **AND** 系統 SHALL NOT 顯示 helper、Toast 或錯誤提示

#### Scenario: 無定位權限或定位未開啟時不打擾
- **WHEN** 用戶打開複製路線頁面或編輯路線頁面
- **AND** App 沒有前台定位權限或系統定位未開啟
- **THEN** 系統 SHALL NOT 為靜默預熱請求定位權限
- **AND** 系統 SHALL NOT 跳轉系統定位設定
- **AND** 系統 SHALL NOT 顯示靜默預熱失敗提示
- **AND** 系統 SHALL 保留既有或預填起點

#### Scenario: 每個頁面會話最多一次自動預熱
- **WHEN** 複製路線頁面或編輯路線頁面已經為本次頁面會話發起過靜默 Google 地址預熱
- **THEN** 系統 SHALL NOT 在同一頁面會話中自動重複發起靜默 Google 地址預熱
- **AND** 用戶之後點擊起點定位按鈕時 SHALL 仍可明確觸發目前位置名稱解析

#### Scenario: 預熱結果供後續定位按鈕使用
- **WHEN** 複製或編輯頁的靜默預熱已成功寫入 Google 地址名稱 cache
- **AND** 用戶點擊起點定位按鈕
- **AND** 當次目前位置 snapshot 與 cache key 匹配且 cache 未過期
- **THEN** 系統 SHALL 使用 cached 地址名稱填入起點
- **AND** 系統 SHALL 使用當次目前位置 snapshot 的原始 GPS 座標建立 `Place`
- **AND** 系統 SHALL 顯示 Google Maps attribution

### Requirement: Google 地址 attribution 僅在起點輸入上下文顯示
系統 SHALL 在 Google reverse geocoding 地址實際顯示於起點輸入框時，於該輸入上下文顯示來源小字，且 SHALL NOT 將來源寫入地點名稱或持久化資料。

#### Scenario: Google 地址成功填入後顯示 attribution
- **WHEN** Google reverse geocoding 解析成功或 Google 地址名稱 cache 命中
- **AND** 系統將目前位置 `Place` 填入起點輸入框
- **THEN** 起點輸入框下方 SHALL 顯示獨立小字 `地址由 Google Maps 提供`
- **AND** 該小字 SHALL 位於起點候選 loading 和候選列表上方
- **AND** 該小字 SHALL NOT 使用 `TextInputLayout.helperText` 承載

#### Scenario: 靜默預熱不顯示 attribution
- **WHEN** 複製或編輯頁正在靜默預熱 Google 地址名稱 cache
- **THEN** 系統 SHALL NOT 顯示 `地址由 Google Maps 提供`
- **AND** 系統 SHALL NOT 改變起點輸入框內容

#### Scenario: 用戶改變起點後隱藏 attribution
- **WHEN** 起點輸入框目前顯示 Google 地址 attribution
- **AND** 用戶手動編輯、清空起點或選擇 Citybus 候選地
- **THEN** 系統 SHALL 隱藏 Google 地址 attribution
- **AND** 系統 SHALL NOT 將 attribution 文字加入 `Place.name`

#### Scenario: Google 解析失敗不顯示 attribution
- **WHEN** 目前位置取得或 Google reverse geocoding 名稱解析失敗
- **THEN** 系統 SHALL NOT 顯示 Google 地址 attribution
- **AND** 系統 SHALL 使用既有自動 helper 或手動 Toast 表示目前位置失敗

#### Scenario: 非輸入上下文不展示地點來源
- **WHEN** 系統在管理路線、主頁卡片、路線結果、保存臨時查詢彈窗或其他非起點輸入上下文展示地點名稱
- **THEN** 系統 SHALL NOT 顯示 `地址由 Google Maps 提供`
- **AND** 系統 SHALL NOT 因 Google 地址來源改變路線預設名稱或保存資料
