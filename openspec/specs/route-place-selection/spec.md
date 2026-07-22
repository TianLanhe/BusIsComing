# route-place-selection Specification

## Purpose
定义路线新增和编辑页面中的地点选择交互，确保起点和终点必须来自 Citybus 候选地点，用户修改文本后必须重新选择，并在保存前完成必填与相同地点校验。
## Requirements

### Requirement: 起点和终点通过候选地点选择

系統 SHALL 在路線新增、編輯和複製頁面中，透過可輸入控件和表單內嵌候選列表選擇起點和終點地點。

#### Scenario: 輸入關鍵字觸發搜尋
- **WHEN** 用戶在起點或終點輸入框輸入至少 1 個字元
- **THEN** 系統在 debounce 後發起地點搜尋

#### Scenario: 展示最多 100 條候選地點
- **WHEN** 地點搜尋返回候選結果
- **THEN** 系統在目前聚焦輸入框下方的內嵌候選列表中展示 API 返回的候選地點，最多 100 條
- **AND** 系統 SHALL NOT 使用會與輸入法重疊的獨立下拉視窗展示候選結果

#### Scenario: 選擇候選地點
- **WHEN** 用戶從內嵌候選列表中選擇一個地點
- **THEN** 系統記錄該地點的名稱、緯度和經度，並在輸入框中展示地點名稱
- **AND** 如果另一端尚未選擇，系統 SHALL 保留輸入法並自動聚焦另一端輸入框
- **AND** 如果另一端已有文字，且保存的候選仍對應該文字與最新 search generation，系統 SHALL 立即重新顯示該端候選
- **AND** 如果起點和終點都已選擇，系統 SHALL NOT 自動提交、收起輸入法或切換焦點

### Requirement: 修改文本后必须重新选择地点

系统 SHALL 在用户修改起点或终点输入框文本后清空之前选择的地点。

#### Scenario: 修改已选择地点文本
- **WHEN** 用户已经选择候选地点后又手动修改输入框文本
- **THEN** 系统清空对应已选择地点，并要求用户重新从候选中选择

### Requirement: 保存路线前校验地点选择

系统 SHALL 在保存路线前校验路线名称、起点地点和终点地点均有效。

#### Scenario: 未选择起点候选
- **WHEN** 用户填写了起点文本但没有从候选中选择起点地点
- **THEN** 系统阻止保存并提示必须选择起点地点

#### Scenario: 未选择终点候选
- **WHEN** 用户填写了终点文本但没有从候选中选择终点地点
- **THEN** 系统阻止保存并提示必须选择终点地点

#### Scenario: 起点终点完全相同
- **WHEN** 用户选择的起点和终点名称、纬度、经度均完全相同
- **THEN** 系统阻止保存并提示起点和终点不能相同

#### Scenario: 保存有效路线
- **WHEN** 用户填写路线名称并选择不同的起点和终点候选地点
- **THEN** 系统允许保存路线配置

### Requirement: 地点搜索展示非阻塞加载反馈
系统 SHALL 在路线新增和编辑页面中，为起点和终点地点搜索展示独立的非阻塞加载动画和状态文案。

#### Scenario: 起点搜索进行中
- **WHEN** 用户在起点输入框输入关键词并触发地点搜索
- **THEN** 起点输入区域显示小型加载动画和 `正在匹配地点...` 文案

#### Scenario: 终点搜索进行中
- **WHEN** 用户在终点输入框输入关键词并触发地点搜索
- **THEN** 终点输入区域显示小型加载动画和 `正在匹配地点...` 文案

#### Scenario: 搜索反馈不阻塞输入
- **WHEN** 起点或终点正在搜索地点
- **THEN** 用户仍可继续输入、删除文本或切换到另一个输入框

#### Scenario: 新输入取消旧搜索反馈
- **WHEN** 用户在旧搜索返回前继续修改同一输入框关键词
- **THEN** 系统取消或忽略旧搜索结果，并让加载反馈对应最新关键词

#### Scenario: 搜索结束隐藏加载动画
- **WHEN** 地点搜索成功、无结果或失败
- **THEN** 系统隐藏对应输入区域的加载动画，并展示候选列表、无结果文案或失败错误

### Requirement: 支持交换起点和终点
系统 SHALL 在路线编辑页提供弯曲双向箭头图标按钮，让用户交换起点和终点。

#### Scenario: 交换控件位于起终点输入框右侧
- **WHEN** 用户打开路线编辑页
- **THEN** 交换控件 SHALL 以弯曲双向箭头图标按钮展示在起点和终点输入框右侧中线附近
- **AND** 交换控件 SHALL NOT 使用整行文字按钮展示

#### Scenario: 交换控件可触达且可理解
- **WHEN** 用户查看或通过无障碍服务访问交换控件
- **THEN** 交换控件 SHALL 提供不小于 48dp 的可点击区域
- **AND** 交换控件 SHALL 提供“交换起点和终点”的无障碍说明

#### Scenario: 交换两个已选择地点
- **WHEN** 起点和终点都已从候选列表中选择地点，且用户点击交换控件
- **THEN** 系统交换起点和终点的地点信息与输入框显示文本

#### Scenario: 交换未确认输入文本
- **WHEN** 起点或终点存在未从候选列表确认的输入文本，且用户点击交换控件
- **THEN** 系统交换输入框文本，并清空对应已选择地点状态，保存前仍要求用户从候选列表选择有效地点

#### Scenario: 交换后仍执行保存校验
- **WHEN** 用户交换起点和终点后点击保存
- **THEN** 系统继续校验路线名称、起点地点、终点地点以及起终点不能相同

#### Scenario: 交换反馈不阻塞输入
- **WHEN** 用户点击交换控件
- **THEN** 系统 SHALL 提供 150ms 到 250ms 的轻量动画或视觉反馈
- **AND** 反馈 SHALL NOT 阻塞继续输入、选择地点或保存路线

### Requirement: 地点输入提示更易理解
系统 SHALL 在路线编辑页提供面向用户任务的地点输入提示，而不是只展示字段名称。

#### Scenario: 起点输入提示
- **WHEN** 用户查看起点输入框
- **THEN** 系统提示用户输入起点关键词并从匹配列表中选择

#### Scenario: 终点输入提示
- **WHEN** 用户查看终点输入框
- **THEN** 系统提示用户输入终点关键词并从匹配列表中选择

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

#### Scenario: 搜尋展示候選地點
- **WHEN** 搜尋頁地點搜尋返回候選結果
- **THEN** 系統 SHALL 在對應輸入框下方展示 Citybus 候選地點
- **AND** 系統 SHALL 不以 bottom sheet 展開狀態作為候選列表可見的前提

#### Scenario: 臨時查詢輸入關鍵字觸發搜尋
- **WHEN** 用戶在臨時查詢的起點或終點輸入框輸入至少 1 個字符
- **THEN** 系統 SHALL 在 debounce 後發起 Citybus 地點搜尋

#### Scenario: 臨時查詢展示候選地點
- **WHEN** 臨時查詢地點搜尋返回候選結果
- **THEN** 系統 SHALL 在目前聚焦輸入框下方的內嵌候選列表中展示接口返回的候選地點，最多 100 條
- **AND** 系統 SHALL 自動將臨時查詢底部彈層展開至接近全螢幕

#### Scenario: 臨時查詢選擇候選地點
- **WHEN** 用戶從臨時查詢內嵌候選列表中選擇一個地點
- **THEN** 系統 SHALL 記錄該地點的名稱、緯度和經度
- **AND** 系統 SHALL 在對應輸入框中展示地點名稱
- **AND** 如果另一端尚未選擇，系統 SHALL 保留輸入法並自動聚焦另一端輸入框
- **AND** 如果另一端已有文字，且保存的候選仍對應該文字與最新 search generation，系統 SHALL 立即重新顯示該端候選
- **AND** 如果起點和終點都已選擇，系統 SHALL NOT 自動提交、收起輸入法或切換焦點

#### Scenario: 臨時查詢修改已選地點文本
- **WHEN** 用戶已在臨時查詢中選擇候選地點後又手動修改輸入框文本
- **THEN** 系統 SHALL 清空對應已選地點
- **AND** 系統 SHALL 要求用戶重新從候選列表選擇有效地點後才能查詢或保存

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

#### Scenario: 臨時查詢未選擇起點
- **WHEN** 用戶未從候選列表選擇臨時起點並嘗試查詢或保存
- **THEN** 系統 SHALL 阻止操作並提示必須選擇起點地點

#### Scenario: 臨時查詢未選擇終點
- **WHEN** 用戶未從候選列表選擇臨時終點並嘗試查詢或保存
- **THEN** 系統 SHALL 阻止操作並提示必須選擇終點地點

#### Scenario: 臨時查詢起點終點相同
- **WHEN** 用戶在臨時查詢中選擇的起點和終點名稱、緯度、經度均完全相同
- **THEN** 系統 SHALL 阻止查詢或保存
- **AND** 系統 SHALL 提示起點和終點不能相同

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

#### Scenario: 交換臨時查詢已選地點
- **WHEN** 臨時查詢起點和終點都已從候選列表中選擇地點，且用戶點擊交換控件
- **THEN** 系統 SHALL 交換起點和終點的地點資訊與輸入框顯示文本

#### Scenario: 交換臨時查詢未確認輸入文本
- **WHEN** 臨時查詢起點或終點存在未從候選列表確認的輸入文本，且用戶點擊交換控件
- **THEN** 系統 SHALL 交換輸入框文本
- **AND** 系統 SHALL 清空對應已選地點狀態

#### Scenario: 臨時查詢交換控件可觸達
- **WHEN** 用戶查看或通過無障礙服務訪問臨時查詢交換控件
- **THEN** 交換控件 SHALL 提供不小於 48dp 的可點擊區域
- **AND** 交換控件 SHALL 提供 `交換起點和終點` 的無障礙說明

### Requirement: 地點候選列表避開輸入法並保持可操作
系統 SHALL 在新增、編輯、複製行程及搜尋頁中，將目前輸入框的地點候選限制於輸入法上方可視區，並保持列表可滾動和可點選。

#### Scenario: 輸入框保持可讀與可操作尺寸
- **WHEN** 系統展示新增、編輯、複製行程或搜尋頁的起點和終點輸入框
- **THEN** 輸入框 SHALL 保持 `56dp` 最小觸控高度、`16sp` 單行尾端省略文字、內邊距、浮動標籤和文字基線
- **AND** 輸入框 SHALL NOT 顯示會誤導為固定選項下拉菜單的箭頭
- **AND** 輸入欄 SHALL 保持既有橫向寬度及交換按鈕預留區

#### Scenario: 只展示目前聚焦欄位的候選
- **WHEN** 起點或終點輸入框取得焦點並返回候選結果
- **THEN** 系統 SHALL 只展示目前聚焦輸入框的候選列表
- **AND** 另一個輸入框的候選列表 SHALL 保持關閉

#### Scenario: 候選高度依輸入法可視區調整
- **WHEN** 輸入法顯示且地點候選列表需要展示
- **THEN** 系統 SHALL 根據候選列表實際頂部至輸入法頂部的剩餘空間動態限制候選列表高度
- **AND** 系統 SHALL 在必要時以最小幅度滾動外層表單後再計算剩餘空間
- **AND** 新增、編輯及複製行程在 `1080×2400`、字體縮放 1.0 的主驗收配置 SHALL 展示 4 至 6 個完整候選項目
- **AND** 新增、編輯及複製行程在 `1080×1920`、字體縮放 1.15 的替代驗收配置 SHALL 至少展示 3 個完整候選項目
- **AND** 新增、編輯及複製行程 SHALL NOT 展示超過 6 個完整候選項目
- **AND** 搜尋頁 SHALL NOT 展示超過 3 個完整候選項目

#### Scenario: 候選列表使用專案一致的卡片樣式
- **WHEN** 系統展示有結果的地點候選列表
- **THEN** 候選列表 SHALL 使用與輸入框同寬的主題 surface 圓角卡片、淺色描邊、2dp elevation 及項目分隔
- **AND** 每個候選項目 SHALL 約為 `52dp` 高並提供按壓反饋
- **AND** 候選項目 SHALL 在左側顯示 `16sp` 單行省略的地點名稱
- **AND** 目前位置快照可用時，候選項目 SHALL 在右側顯示定位圖示與格式化距離
- **AND** 目前位置快照不可用時，候選項目 SHALL 靜默省略距離區域

#### Scenario: 候選緊跟目前輸入框
- **WHEN** 起點或終點候選列表顯示
- **THEN** 候選列表 SHALL 緊跟目前聚焦輸入框下方
- **AND** 位於其後的另一個輸入框 SHALL 自然向下移動
- **AND** 交換按鈕 SHALL 保持可見及可操作
- **AND** 交換按鈕 SHALL 維持在兩個收合輸入框的固定垂直中線，不因候選展開而移動或與候選重疊

#### Scenario: 候選超出可視高度
- **WHEN** 候選數量超出候選列表可視高度
- **THEN** 用戶 SHALL 能夠在候選列表內獨立滾動
- **AND** 外層頁面 SHALL NOT 因候選列表內滾動而意外關閉

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
- **THEN** 起終點編輯器 SHALL 恢復由既有內容決定的自然高度
- **AND** 結果列表的滾動位置 SHALL NOT 被重設

#### Scenario: 點擊空白處關閉候選
- **WHEN** 候選列表顯示且用戶點擊表單空白處
- **THEN** 系統 SHALL 關閉候選列表
- **AND** 系統 SHALL 保留目前輸入文字

#### Scenario: 第一次返回只關閉候選
- **WHEN** 候選列表顯示且用戶第一次按系統返回
- **THEN** 系統 SHALL 關閉候選列表並保留目前輸入文字
- **AND** 系統 SHALL NOT 關閉目前 Activity 或搜尋 destination

#### Scenario: 候選已關閉時沿用返回行為
- **WHEN** 候選列表已關閉且用戶按系統返回
- **THEN** 系統 SHALL 沿用目前頁面的既有返回行為

#### Scenario: 保持既有搜尋狀態與文案
- **WHEN** 地點搜尋正在進行、沒有結果或失敗
- **THEN** 系統 SHALL 保持既有 loading、無結果及失敗文案和欄位級顯示位置
- **AND** 系統 SHALL 保持既有 debounce、過期結果忽略、選擇校驗、交換、保存及查詢行為

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

#### Scenario: 保持既有搜尋狀態與請求行為
- **WHEN** 地點搜尋正在進行、沒有結果或失敗
- **THEN** 系統 SHALL 保持既有 debounce、過期結果忽略、選擇校驗、保存及查詢行為
- **AND** 本 change SHALL NOT 修改 `bsearch_p3.php` 的參數、語言 mapping、repository 或解析結果

#### Scenario: 路線編輯表單協調巢狀滾動
- **WHEN** 新增、編輯或複製路線頁面的候選列表到達滾動邊界
- **THEN** 剩餘縱向手勢 SHALL 能夠自然傳遞給外層 `NestedScrollView`
- **AND** 外層表單 SHALL NOT 在候選仍可滾動時搶占手勢

#### Scenario: 臨時查詢關閉候選後恢復自然高度
- **WHEN** 臨時查詢底部彈層的候選列表被關閉
- **THEN** 底部彈層 SHALL 恢復由既有內容決定的自然高度

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

### Requirement: 地點候選顯示與目前位置的直線距離
系統 SHALL 在新增、編輯、複製路線及臨時查詢的地點候選列表中，於已有前台定位權限或其他目前位置流程已取得位置快照時顯示目前位置到候選地點的直線距離。

#### Scenario: 已有精確定位權限
- **WHEN** 用戶打開新增、編輯或複製路線頁面，或打開臨時查詢底部彈層
- **AND** App 已取得精確前台定位權限
- **THEN** 系統 SHALL 在該頁面或彈層會話中取得一次位置
- **AND** 地點候選 SHALL 在位置可用時顯示與目前位置的直線距離

#### Scenario: 只有粗略定位權限
- **WHEN** App 只有粗略前台定位權限
- **AND** 系統成功取得粗略位置
- **THEN** 地點候選 SHALL 顯示與該位置的直線距離
- **AND** 系統 SHALL NOT 因位置精度大於 500 米而隱藏候選距離
- **AND** 系統 SHALL NOT 在距離前增加 `約` 字樣

#### Scenario: 編輯或複製路線沒有定位權限
- **WHEN** 用戶打開編輯或複製路線頁面
- **AND** App 沒有粗略或精確前台定位權限
- **AND** 用戶沒有點擊起點定位按鈕
- **THEN** 系統 SHALL NOT 僅為候選距離主動請求定位權限
- **AND** 系統 SHALL NOT 顯示定位權限提示
- **AND** 候選列表 SHALL 正常展示並允許選擇
- **AND** 候選項 SHALL 靜默省略距離區域

#### Scenario: 候選距離沒有獨立權限入口
- **WHEN** 候選列表需要展示地點搜尋結果
- **AND** App 沒有粗略或精確前台定位權限
- **AND** 當前互動不是純新增路線自動目前位置、臨時查詢自動目前位置或起點定位按鈕
- **THEN** 系統 SHALL NOT 僅為顯示候選距離請求定位權限
- **AND** 系統 SHALL NOT 因候選距離顯示定位權限提示
- **AND** 候選列表 SHALL 正常展示並允許選擇
- **AND** 候選項 SHALL 靜默省略距離區域

#### Scenario: 使用最近位置快照
- **WHEN** 路線表單或臨時查詢需要取得位置
- **AND** 系統已有最近 30 秒內成功取得的位置快照
- **THEN** 系統 SHALL 直接復用該位置快照
- **AND** 系統 SHALL NOT 為該次頁面或彈層會話發起另一個底層定位請求

#### Scenario: 最近位置快照過期
- **WHEN** 路線表單或臨時查詢需要取得位置
- **AND** 系統沒有最近 30 秒內的位置快照
- **THEN** 系統 SHALL 嘗試取得一次新位置
- **AND** 該次定位 SHALL 在 3 秒後超時
- **AND** 同時發生的多個位置需求 SHALL 共用同一個進行中的底層定位請求

#### Scenario: 定位失敗或超時
- **WHEN** 系統無法取得位置、定位結果為空、定位能力不可用或定位在 3 秒內未完成
- **THEN** 候選列表 SHALL 正常展示並允許選擇
- **AND** 候選項 SHALL 靜默省略距離區域
- **AND** 系統 SHALL NOT 顯示距離 placeholder、helper、Toast 或錯誤卡

#### Scenario: 頁面關閉後位置才返回
- **WHEN** 路線頁面已銷毀或臨時查詢底部彈層已關閉
- **AND** 先前發起的位置請求稍後返回
- **THEN** 系統 SHALL 忽略該位置對已關閉 UI 的更新
- **AND** 系統 SHALL NOT 重新打開候選列表或彈層

### Requirement: 候選距離使用一致的格式與單行視覺
系統 SHALL 在候選項右側使用定位圖示與緊湊距離文字展示直線距離，同時保持地點候選的原始順序、單行高度及可操作性。

#### Scenario: 顯示米距離
- **WHEN** 候選地點的直線距離四捨五入後小於 1000 米
- **THEN** 系統 SHALL 顯示四捨五入的整數米
- **AND** 數字與單位之間 SHALL NOT 加空格
- **AND** 例如 368.4 米 SHALL 顯示為 `368m`

#### Scenario: 四捨五入後切換為公里
- **WHEN** 候選地點的直線距離四捨五入後大於或等於 1000 米
- **THEN** 系統 SHALL 顯示保留一位小數的公里距離
- **AND** 數字與單位之間 SHALL NOT 加空格
- **AND** 例如 999.6 米 SHALL 顯示為 `1.0km`

#### Scenario: 顯示一般公里距離
- **WHEN** 候選地點的直線距離為 1000 米以上
- **THEN** 系統 SHALL 顯示保留一位小數的公里距離
- **AND** 例如 1249.6 米 SHALL 顯示為 `1.3km`

#### Scenario: 候選項顯示名稱與距離
- **WHEN** 候選地點與位置均可用
- **THEN** 候選項 SHALL 在左側顯示 16sp 單行地點名稱
- **AND** 候選項 SHALL 在右側顯示綠色線框定位圖示與 13sp 次要色距離
- **AND** 定位圖示 SHALL 只位於距離前方
- **AND** 地點名稱前 SHALL NOT 顯示圓點、圖示或其他裝飾
- **AND** 距離區域 SHALL 完整顯示且不換行
- **AND** 過長地點名稱 SHALL 在尾端省略

#### Scenario: 保持候選行高與可見數量
- **WHEN** 候選項顯示距離
- **THEN** 每個候選項 SHALL 保持約 52dp 單行高度
- **AND** 系統 SHALL NOT 因距離改為雙行候選
- **AND** 在既有主驗收配置下 SHALL 繼續顯示 4 至 6 個完整候選項目

#### Scenario: 候選距離提供無障礙語義
- **WHEN** 候選項顯示定位圖示與距離
- **THEN** 定位圖示 SHALL 視為裝飾而不重複朗讀
- **AND** 候選項的無障礙描述 SHALL 包含地點名稱及完整距離語義
- **AND** 可見文字 `368m` 的距離語義 SHALL 可被理解為「距離目前位置 368 米」

### Requirement: 候選距離更新不改變搜尋與選擇行為
系統 SHALL 讓地點搜尋結果優先展示，並在位置稍後返回時原位補充距離，不改變候選排序、滾動位置或選定後的輸入內容。

#### Scenario: 候選先於位置返回
- **WHEN** Citybus 地點候選已返回
- **AND** 目前位置仍在取得中
- **THEN** 系統 SHALL 立即展示候選地點名稱
- **AND** 系統 SHALL NOT 等待定位完成才展示候選

#### Scenario: 位置稍後返回
- **WHEN** 候選列表已顯示
- **AND** 系統稍後成功取得目前位置
- **THEN** 系統 SHALL 在既有候選項中原位補充距離
- **AND** 系統 SHALL NOT 關閉或重新打開候選列表
- **AND** 系統 SHALL NOT 改變候選列表的目前滾動位置
- **AND** 系統 SHALL NOT 產生可見的整體列表閃爍

#### Scenario: 距離不改變候選順序
- **WHEN** 候選地點顯示不同距離
- **THEN** 系統 SHALL 保持 Citybus 搜尋結果的原始順序
- **AND** 系統 SHALL NOT 依距離重新排序、過濾或自動選擇候選

#### Scenario: 選定候選後只保留名稱
- **WHEN** 用戶選擇一個顯示距離的候選地點
- **THEN** 系統 SHALL 記錄該地點的名稱、緯度和經度
- **AND** 輸入框 SHALL 只顯示地點名稱
- **AND** 系統 SHALL NOT 把候選距離寫入 `Place`、路線配置或資料庫

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

#### Scenario: Google 地址成功填入後顯示 attribution
- **WHEN** Google reverse geocoding 解析成功或 Google 地址名稱 cache 命中
- **AND** 系統將目前位置 `Place` 填入起點輸入框
- **THEN** 起點輸入框下方 SHALL 顯示獨立小字 `地址由 Google Maps 提供`
- **AND** 該小字 SHALL 位於起點候選 loading 和候選列表上方
- **AND** 該小字 SHALL NOT 使用 `TextInputLayout.helperText` 承載

#### Scenario: 用戶改變起點後隱藏 attribution
- **WHEN** 起點輸入框目前顯示 Google 地址 attribution
- **AND** 用戶手動編輯、清空起點或選擇 Citybus 候選地
- **THEN** 系統 SHALL 隱藏 Google 地址 attribution
- **AND** 系統 SHALL NOT 將 attribution 文字加入 `Place.name`

### Requirement: 臨時查詢支持從結果上下文預填編輯
系統 SHALL 允許用戶從臨時查詢結果上下文進入臨時查詢底部彈層，並以目前臨時查詢起點和終點預填，讓用戶修改後繼續發起臨時查詢。

#### Scenario: 從臨時結果預填起點和終點
- **WHEN** 用戶從臨時查詢結果上下文點擊 `編輯`
- **THEN** 系統 SHALL 打開臨時查詢底部彈層
- **AND** 起點輸入框 SHALL 顯示目前臨時查詢起點名稱
- **AND** 終點輸入框 SHALL 顯示目前臨時查詢終點名稱
- **AND** 系統 SHALL 將目前臨時查詢起點和終點保留為已選擇的有效地點
- **AND** 用戶可直接點擊 `使用此路線查詢` 發起同一組起終點的臨時查詢

#### Scenario: 預填編輯後修改並重新查詢
- **WHEN** 用戶從臨時結果進入預填編輯彈層
- **AND** 用戶修改起點或終點並從候選列表選擇有效地點
- **AND** 用戶點擊 `使用此路線查詢`
- **THEN** 系統 SHALL 使用修改後的起點和終點發起新的臨時查詢
- **AND** 新查詢 SHALL 替換目前臨時查詢上下文
- **AND** 系統 SHALL NOT 將該臨時查詢自動保存為常用路線
- **AND** 系統 SHALL NOT 更新任何已保存常用路線的使用次數或最近使用時間

#### Scenario: 預填編輯未提交時保留原結果
- **WHEN** 用戶從臨時結果進入預填編輯彈層
- **AND** 用戶按系統返回、關閉彈層或點擊彈層外部而未點擊 `使用此路線查詢`
- **THEN** 系統 SHALL 保留原臨時查詢上下文、原查詢結果、排序狀態和更新時間
- **AND** 系統 SHALL NOT 發起新的路線查詢
- **AND** 系統 SHALL NOT 自動保存臨時查詢

#### Scenario: 預填編輯沿用臨時查詢校驗
- **WHEN** 用戶從臨時結果進入預填編輯彈層
- **AND** 用戶清空、手動改寫但未重新選擇候選地點，或選擇相同起終點後嘗試查詢或保存
- **THEN** 系統 SHALL 沿用臨時查詢起點和終點校驗規則
- **AND** 系統 SHALL 阻止無效查詢或保存
- **AND** 系統 SHALL 顯示對應欄位錯誤提示

#### Scenario: 普通臨時查詢入口不使用舊上下文
- **WHEN** 用戶從首次引導頁、常用路線列表或其他普通臨時查詢入口打開臨時查詢底部彈層
- **THEN** 系統 SHALL NOT 自動帶入上一次臨時查詢起點和終點
- **AND** 系統 SHALL 沿用既有空表單和目前位置自動填入起點行為

#### Scenario: 預填編輯不被自動目前位置覆蓋
- **WHEN** 用戶從臨時結果進入預填編輯彈層且目前臨時查詢已有起點
- **THEN** 系統 SHALL NOT 使用自動目前位置流程覆蓋預填起點
- **AND** 用戶仍可透過起點輸入框內的定位按鈕手動改用目前位置

### Requirement: 搜尋頁輸入器提供清晰欄位焦點與輔助狀態
系統 SHALL 在搜尋頁移除頁面大標題，保留短功能說明，並讓起終點輸入、欄位級輔助文案與右側交換控制保持清晰而互不重疊。

#### Scenario: 搜尋頁頂部保持精簡
- **WHEN** 用戶打開搜尋頁
- **THEN** 系統 SHALL NOT 顯示「搜尋／搜索／Search」頁面大標題
- **AND** 系統 SHALL 在輸入器上方保留簡短功能說明

#### Scenario: 輸入文字與工具保持間距
- **WHEN** 用戶在起點或終點輸入框輸入
- **THEN** 文字 SHALL 使用 16sp 及 16dp 起始內距
- **AND** 欄位末端 SHALL 保留 52dp 工具空間
- **AND** 文字、光標、定位／清除工具 SHALL NOT 互相重疊

#### Scenario: 焦點與光標在深淺色可辨識
- **WHEN** 任一搜尋輸入框獲得焦點
- **THEN** 約 2dp 光標與焦點外框 SHALL 使用高對比主題強調色
- **AND** 淺色與深色模式 SHALL 均可清楚辨識目前輸入欄位

#### Scenario: 輔助文案歸屬目前欄位
- **WHEN** 欄位為空並取得焦點、正在輸入、沒有候選或發生錯誤
- **THEN** 對應輔助文案 SHALL 顯示在該輸入框下方
- **AND** 另一欄位 SHALL NOT 顯示該狀態文案
- **AND** 有效地點被選中後 SHALL 隱藏一般操作提示

#### Scenario: 交換控制保持獨立可用
- **WHEN** 搜尋頁顯示兩個輸入框
- **THEN** 交換按鈕 SHALL 位於兩個輸入框右側並垂直置中
- **AND** 交換按鈕 SHALL 保持至少 48dp 觸控範圍

### Requirement: 搜尋頁首次非阻塞填入目前位置地址
系統 SHALL 在每個主畫面實例首次進入搜尋頁且沒有可恢復起點時，非阻塞執行定位與 Google Reverse Geocoding，並只在整個流程成功後以具體地址名稱和原始經緯度建立起點。

#### Scenario: 符合條件時自動填入具體地址
- **WHEN** 用戶在一個主畫面實例首次進入搜尋頁
- **AND** 搜尋頁沒有已選起點、使用者起點文字或已提交查詢
- **THEN** 系統 SHALL 在背景取得手機位置並使用目前語言執行 Google Reverse Geocoding
- **AND** 成功後 SHALL 填入具體地址名稱、原始經緯度及必要 attribution
- **AND** 系統 SHALL NOT 使用「我的位置」特殊占位值

#### Scenario: 定位期間輸入保持可用
- **WHEN** 自動定位或 Geocoding 正在進行
- **THEN** 起點與終點輸入 SHALL 保持可編輯
- **AND** 只有起點定位工具 SHALL 以小型進度表示等待
- **AND** 搜尋按鈕可用性 SHALL 只由兩端是否為有效選中地點決定

#### Scenario: 終點操作不取消起點定位
- **WHEN** 自動定位進行中且用戶輸入或選擇終點
- **THEN** 起點自動定位 SHALL 繼續
- **AND** 有效成功回調 SHALL 可填入起點而不改變已選終點

#### Scenario: 起點操作使舊回調失效
- **WHEN** 自動定位進行中且用戶輸入或選擇起點，或交換起終點
- **THEN** 系統 SHALL 立即停止顯示起點等待狀態
- **AND** 已發出的舊定位或 Geocoding 回調 SHALL NOT 覆蓋目前輸入與選擇

#### Scenario: 頁面或語言狀態使舊回調失效
- **WHEN** 自動定位進行中且搜尋頁離開可見狀態或 App 實際語言改變
- **THEN** 舊回調 SHALL NOT 更新搜尋頁
- **AND** 後續新請求 SHALL 使用新的語言 snapshot

#### Scenario: 自動流程失敗不建立半完成地點
- **WHEN** 權限、定位服務、逾時、定位或 Reverse Geocoding 任一步失敗
- **THEN** 起點 SHALL 保持空白或保留使用者目前內容且可編輯
- **AND** 系統 SHALL NOT 建立只有座標或沒有具體名稱的選中起點
- **AND** 起點輔助文案 SHALL 提示手動選擇或點擊定位工具重試
- **AND** 系統 SHALL NOT 因自動失敗彈出 Toast 或強制跳轉設定

#### Scenario: 手動重試沿用既有恢復流程
- **WHEN** 用戶點擊起點定位工具手動重試
- **THEN** 系統 SHALL 沿用新增行程既有權限、定位設定、timeout、cache、Geocoding 與失敗提示流程

#### Scenario: 恢復狀態不被再次覆蓋
- **WHEN** 搜尋頁因畫面重建恢復了起點、使用者文字或已提交查詢
- **THEN** 系統 SHALL NOT 再次自動定位並覆蓋該狀態

### Requirement: 行程與搜尋頁共用起終點編輯器結構
系統 SHALL 在新增、編輯、複製行程及搜尋頁共用同一起終點編輯器結構，讓輸入框、定位工具、交換按鈕、候選、helper、error 與 attribution 使用一致幾何及狀態位置。

#### Scenario: 顯示收合的起終點編輯器
- **WHEN** 頁面沒有展開地點候選
- **THEN** 起點與終點輸入框 SHALL 各保持至少 `56dp` 高
- **AND** 兩個輸入框之間 SHALL 保持約 `8dp` 基礎間距
- **AND** 起點定位圖示或進度 SHALL 使用同一個 `48dp` 尾端工具槽並保持置中
- **AND** 交換按鈕 SHALL 使用右側獨立 `48dp` 觸控區並保持置中

#### Scenario: 顯示欄位級輔助狀態
- **WHEN** 起點或終點顯示 helper、錯誤、無結果或 Google attribution
- **THEN** 該狀態 SHALL 緊跟所屬欄位或候選容器
- **AND** 狀態 SHALL NOT 被放置在整個起終點編輯器下方而失去欄位歸屬
- **AND** 另一欄位的輸入與已選地點 SHALL 不受影響

#### Scenario: 保留頁面專屬操作
- **WHEN** 系統在行程頁或搜尋頁使用共用編輯器
- **THEN** 行程名稱 SHALL 只在新增、編輯及複製頁顯示
- **AND** `儲存為常用行程` SHALL 只在搜尋頁顯示於左側輸入欄下方
- **AND** 這些頁面專屬操作 SHALL NOT 改變共用交換工具區寬度

### Requirement: 搜尋恢復流程靜默補取候選距離快照
系統 SHALL 在搜尋頁恢復已有起點、使用者文字或已提交上下文而不應自動覆寫起點時，於既有前台定位權限及定位能力可用的前提下靜默取得候選距離位置快照。

#### Scenario: 恢復已有搜尋內容
- **WHEN** 搜尋頁恢復已有起點、終點文字或已提交查詢上下文
- **AND** App 已有粗略或精確前台定位權限且系統定位可用
- **THEN** 系統 SHALL 非阻塞請求一次目前位置快照
- **AND** 系統 SHALL NOT 改寫已恢復的起點或終點
- **AND** 系統 SHALL NOT 因該請求呼叫 Geocoding、顯示欄位 loading 或阻止輸入、交換、保存或搜尋

#### Scenario: 靜默快照成功
- **WHEN** 搜尋恢復流程取得有效位置快照
- **THEN** 起點與終點候選 SHALL 在可見時原位補充距離
- **AND** 系統 SHALL NOT 改變候選順序、滾動位置、目前焦點或已選地點

#### Scenario: 靜默快照失敗
- **WHEN** 靜默位置請求失敗、逾時或返回空值
- **THEN** 候選 SHALL 繼續顯示地點名稱並允許選擇
- **AND** 系統 SHALL 靜默省略距離
- **AND** 系統 SHALL NOT 顯示 Toast、helper 或錯誤卡

#### Scenario: 過期快照返回
- **WHEN** 搜尋頁 View 已銷毀、重新建立或目前 generation 已改變後舊位置請求才返回
- **THEN** 系統 SHALL 忽略舊 callback
- **AND** 舊 callback SHALL NOT 更新新編輯器、重新打開候選或改寫任何輸入
