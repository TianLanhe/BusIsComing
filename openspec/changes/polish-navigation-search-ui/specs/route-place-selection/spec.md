## MODIFIED Requirements

### Requirement: 臨時查詢起點和終點通過候選地點選擇
系統 SHALL 在搜尋 destination 的連體路線輸入器中，透過可輸入控件和欄位級內嵌候選列表選擇一次性查詢的起點和終點地點。

#### Scenario: 搜尋輸入器建立起終點關係
- **WHEN** 用戶打開搜尋頁
- **THEN** 起點和終點 SHALL 以上下排列、具有分隔與順序提示的連體輸入區展示
- **AND** 交換圖示 SHALL 位於兩個輸入欄位右側的固定操作區
- **AND** 起點及終點輸入欄位 SHALL 各自保持 `56dp` 基礎高度

#### Scenario: 搜尋輸入關鍵字觸發搜尋
- **WHEN** 用戶在搜尋頁的起點或終點輸入框輸入至少 1 個字符
- **THEN** 系統 SHALL 在 debounce 後發起 Citybus 地點搜尋
- **AND** 新輸入 SHALL 取消該欄位舊的 loading、無結果、失敗和過期候選回饋

#### Scenario: 搜尋展示欄位級候選地點
- **WHEN** 搜尋頁地點搜尋返回候選結果
- **THEN** 系統 SHALL 在對應輸入框正下方展示 Citybus 候選地點
- **AND** 起點候選 SHALL 位於起點與終點輸入框之間
- **AND** 終點候選 SHALL 位於終點輸入框下方
- **AND** 候選列表寬度 SHALL 與左側輸入欄對齊，且 SHALL NOT 延伸至交換操作區下方
- **AND** 系統 SHALL NOT 以 bottom sheet 或 overlay 展開狀態作為候選列表可見的前提

#### Scenario: 搜尋只展示目前欄位候選
- **WHEN** 一個搜尋欄位的候選列表展開
- **THEN** 另一個搜尋欄位的候選列表 SHALL 關閉
- **AND** 同一時間 SHALL NOT 顯示兩個候選列表

#### Scenario: 搜尋選擇候選地點
- **WHEN** 用戶從搜尋頁內嵌候選列表中選擇一個地點
- **THEN** 系統 SHALL 將該地點設為對應已選 Place
- **AND** 系統 SHALL 在輸入框顯示該地點名稱
- **AND** 系統 SHALL 關閉該欄位候選並清除不再適用的 helper 或錯誤

#### Scenario: 搜尋修改已選地點文本
- **WHEN** 用戶已在搜尋頁選擇候選地點後又手動修改輸入框文本
- **THEN** 系統 SHALL 清除該輸入框對應的已選 Place
- **AND** 系統 SHALL 要求用戶重新從候選列表確認地點
- **AND** 系統 SHALL 清除該欄位的 Google attribution 歸屬

#### Scenario: 搜尋欄位顯示載入與錯誤
- **WHEN** 起點或終點地點搜尋正在進行、沒有結果或失敗
- **THEN** 載入、無結果或錯誤狀態 SHALL 只歸屬目前欄位
- **AND** 載入狀態 SHALL 使用欄位尾端固定工具槽，不得另佔整行或壓縮輸入文字
- **AND** 無結果與錯誤 SHALL 顯示於對應輸入框下方，不得移到整個輸入器底部
- **AND** 另一個欄位的已選 Place、文字和狀態 SHALL 保持不變

### Requirement: 臨時查詢支持交換起點和終點
系統 SHALL 在搜尋頁右側提供固定圖示按鈕，交換一次性查詢的起點、終點及其欄位級狀態。

#### Scenario: 交換已選地點
- **WHEN** 用戶在搜尋頁已確認一個或兩個欄位的 Place 並點擊交換控件
- **THEN** 系統 SHALL 交換兩個欄位的已選 Place 與輸入文本
- **AND** 系統 SHALL 不發起新的 Citybus 地點搜尋或路線查詢
- **AND** 交換後的已選 Place SHALL 保持有效

#### Scenario: 交換未確認輸入文本
- **WHEN** 搜尋起點或終點存在未從候選列表確認的輸入文本，且用戶點擊交換控件
- **THEN** 系統 SHALL 交換兩個欄位目前顯示的文本
- **AND** 未確認文本在交換後 SHALL 繼續保持未確認狀態
- **AND** 系統 SHALL NOT 把未確認文本視為有效 Place

#### Scenario: 交換欄位級狀態
- **WHEN** 用戶點擊交換控件
- **THEN** 系統 SHALL 關閉兩個候選列表
- **AND** 系統 SHALL 交換對應的 Google attribution 歸屬
- **AND** 系統 SHALL 清除已不適用的 loading、helper 和錯誤回饋

#### Scenario: 交換按鈕保持固定
- **WHEN** 起點或終點候選列表展開或關閉
- **THEN** 搜尋交換控件 SHALL 保持在輸入欄位右側的固定位置
- **AND** 控件 SHALL NOT 因候選高度改變而上下跳動或被隱藏

#### Scenario: 交換控件可觸達
- **WHEN** 用戶查看或通過無障礙服務訪問搜尋交換控件
- **THEN** 控件 SHALL 提供對應 locale 的「交換起點和終點」無障礙描述
- **AND** 控件 SHALL 具有不小於 `48dp` 的可觸控區域

### Requirement: 地點候選列表避開輸入法並保持可操作
系統 SHALL 在新增、編輯、複製行程及搜尋頁中，將目前輸入框的地點候選限制於輸入法上方可視區，並保持列表可滾動和可點選；搜尋頁 SHALL 使用獨立的三項可見上限。

#### Scenario: 輸入框保持可讀與可操作尺寸
- **WHEN** 系統展示新增、編輯、複製行程或搜尋頁的起點和終點輸入框
- **THEN** 輸入框 SHALL 保持 `56dp` 最小觸控高度、`16sp` 單行尾端省略文字、內邊距、`12sp` 浮動標籤和穩定文字基線
- **AND** 輸入框 SHALL NOT 顯示會誤導為固定選項下拉菜單的箭頭
- **AND** 搜尋頁兩個欄位 SHALL 使用相同寬度的尾端工具槽

#### Scenario: 只展示目前聚焦欄位的候選
- **WHEN** 起點或終點輸入框取得焦點並返回候選結果
- **THEN** 系統 SHALL 只展示目前聚焦輸入框的候選列表
- **AND** 另一個輸入框的候選列表 SHALL 保持關閉

#### Scenario: 行程表單候選高度依輸入法可視區調整
- **WHEN** 新增、編輯或複製行程頁的輸入法顯示且地點候選列表需要展示
- **THEN** 系統 SHALL 根據候選列表實際頂部至輸入法頂部的剩餘空間動態限制候選列表高度
- **AND** 系統 SHALL 在必要時以最小幅度滾動外層表單後再計算剩餘空間
- **AND** 在 1080 × 2400、字體縮放 1.0 的主驗收配置 SHALL 展示 4 至 6 個完整候選項目
- **AND** 在 1080 × 1920、字體縮放 1.15 的替代驗收配置 SHALL 至少展示 3 個完整候選項目
- **AND** 系統 SHALL NOT 展示超過 6 個完整候選項目

#### Scenario: 搜尋頁最多展示三項候選
- **WHEN** 搜尋頁地點候選列表需要展示
- **THEN** 系統 SHALL 將可見高度限制為最多 3 個完整候選項目
- **AND** 候選數量超過 3 個時列表 SHALL 在自身範圍內滾動
- **AND** IME 上沿和安全距離不足時系統 SHALL 容許少於 3 個完整項目
- **AND** 搜尋頁上限 SHALL NOT 改變新增、編輯或複製行程頁的候選高度策略

#### Scenario: 候選列表使用專案一致的表面樣式
- **WHEN** 系統展示有結果的地點候選列表
- **THEN** 候選列表 SHALL 使用與輸入欄同寬的實體圓角表面、淺色描邊、`2dp` elevation 及項目分隔
- **AND** 每個候選項目 SHALL 使用 `52dp` 基礎高度並提供按壓反饋
- **AND** 候選地點名稱和既有距離資訊 SHALL 保持單行、省略及既有可讀層級

#### Scenario: 候選緊跟目前輸入框
- **WHEN** 起點或終點候選列表顯示
- **THEN** 候選列表 SHALL 緊跟目前聚焦輸入框下方
- **AND** 位於其後的另一個輸入框或後續內容 SHALL 自然向下移動
- **AND** 搜尋頁交換按鈕 SHALL 保持可見和固定

#### Scenario: 候選超出可視高度
- **WHEN** 候選數量超出候選列表可視高度
- **THEN** 用戶 SHALL 能夠在候選列表內獨立滾動
- **AND** 外層頁面 SHALL NOT 因候選列表內滾動而意外關閉或切換 destination

#### Scenario: 行程編輯表單協調巢狀滾動
- **WHEN** 新增、編輯或複製行程頁面的候選列表到達滾動邊界
- **THEN** 剩餘縱向手勢 SHALL 能夠自然傳遞給外層 `NestedScrollView`
- **AND** 外層表單 SHALL NOT 在候選仍可滾動時搶占手勢

#### Scenario: 一般表單自動帶入可視區
- **WHEN** 新增、編輯或複製行程頁面的目前輸入框與至少 3 個候選項目無法同時顯示
- **THEN** 系統 SHALL 以最小必要幅度滾動外層表單，將目前輸入框及至少 3 個候選項目帶入輸入法上方可視區
- **AND** 候選列表關閉後系統 SHALL NOT 強制恢復先前的表單滾動位置

#### Scenario: 搜尋頁關閉候選後恢復自然高度
- **WHEN** 搜尋頁的候選列表被關閉
- **THEN** 對應候選區 SHALL 收起
- **AND** 另一個輸入框及後續內容 SHALL 恢復由既有內容決定的位置
- **AND** 交換按鈕 SHALL 保持原固定位置

#### Scenario: 點擊空白處關閉候選
- **WHEN** 候選列表顯示且用戶點擊表單空白處
- **THEN** 系統 SHALL 關閉候選列表
- **AND** 系統 SHALL 保留目前輸入文字

#### Scenario: 第一次返回只關閉候選
- **WHEN** 候選列表顯示且用戶第一次按系統返回
- **THEN** 系統 SHALL 關閉候選列表並保留目前輸入文字
- **AND** 系統 SHALL NOT 關閉目前 Activity 或切換頂層 destination

#### Scenario: 候選已關閉時沿用返回行為
- **WHEN** 候選列表已關閉且用戶按系統返回
- **THEN** 系統 SHALL 沿用目前頁面既有的返回行為

#### Scenario: 保持既有搜尋狀態與請求行為
- **WHEN** 地點搜尋正在進行、沒有結果或失敗
- **THEN** 系統 SHALL 保持既有 debounce、過期結果忽略、選擇校驗、保存及查詢行為
- **AND** 本 change SHALL NOT 修改 `bsearch_p3.php` 的參數、語言 mapping、repository 或解析結果

### Requirement: Google 地址 attribution 僅在起點輸入上下文顯示
系統 SHALL 在 Google reverse geocoding 地址實際顯示於輸入框時，於對應輸入上下文顯示來源小字；行程新增／編輯／複製頁仍只在起點顯示，搜尋頁則 SHALL 讓 attribution 跟隨 Google 地址所在欄位交換及恢復，且 SHALL NOT 將來源寫入地點名稱或持久化資料。

#### Scenario: Google 地址成功填入起點後顯示 attribution
- **WHEN** Google reverse geocoding 解析成功或 Google 地址名稱 cache 命中
- **AND** 系統將目前位置 Place 填入起點輸入框
- **THEN** 起點輸入框下方 SHALL 顯示對應 locale 的 Google Maps attribution
- **AND** 該小字 SHALL 使用獨立 view，而非 `TextInputLayout.helperText`
- **AND** 該小字 SHALL NOT 加入 `Place.name`

#### Scenario: 搜尋交換後 attribution 跟隨至終點
- **WHEN** 搜尋頁起點顯示 Google 地址及 attribution
- **AND** 用戶交換起點和終點
- **THEN** Google 地址 SHALL 移至終點輸入框
- **AND** attribution SHALL 顯示於終點輸入框下方
- **AND** 起點 SHALL 不再顯示該 attribution

#### Scenario: 搜尋重建後恢復 attribution 歸屬
- **WHEN** 搜尋頁包含 Google 地址並因旋轉、語言、主題或系統回收而重建
- **THEN** 系統 SHALL 將 attribution 恢復至 Google 地址實際所在欄位
- **AND** 系統 SHALL NOT 因重建把 attribution 固定恢復到起點

#### Scenario: 靜默預熱不顯示 attribution
- **WHEN** 複製或編輯頁正在靜默預熱 Google 地址名稱 cache
- **THEN** 系統 SHALL NOT 顯示 Google Maps attribution
- **AND** 系統 SHALL NOT 改變起點輸入框內容

#### Scenario: 用戶改變欄位後隱藏 attribution
- **WHEN** 一個搜尋欄位目前顯示 Google attribution
- **AND** 用戶手動編輯、清空該欄位或選擇 Citybus 候選地
- **THEN** 系統 SHALL 隱藏該欄位 attribution
- **AND** 另一個欄位的值和 attribution 狀態 SHALL 保持不變

#### Scenario: Google 解析失敗不顯示 attribution
- **WHEN** 目前位置取得或 Google reverse geocoding 名稱解析失敗
- **THEN** 系統 SHALL NOT 顯示 Google attribution
- **AND** 系統 SHALL 使用既有欄位 helper 或手動 Toast 表示目前位置失敗

#### Scenario: 非輸入上下文不展示地點來源
- **WHEN** 系統在行程管理、常用卡片、路線結果、保存行程對話框或其他非地點輸入上下文展示地點名稱
- **THEN** 系統 SHALL NOT 顯示 Google attribution
- **AND** 系統 SHALL NOT 因 Google 地址來源改變行程預設名稱或保存資料
