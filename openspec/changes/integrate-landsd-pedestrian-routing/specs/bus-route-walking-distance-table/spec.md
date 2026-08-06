## MODIFIED Requirements

### Requirement: 结果卡片展示聚合路线补充信息

系統 SHALL 在路線結果卡片中展示聚合後的價格、耗時、等候狀態和漸進步行距離信息，且步行資料更新 SHALL NOT 改寫 Citybus 總耗時、預計到達或 ETA。

#### Scenario: 步行距離正在查詢
- **WHEN** 路線卡片具有尚未全部完成或最終失敗的必要 CSDI 步行段
- **THEN** 步行人物圖示旁 SHALL 依目前語言顯示 `查詢中…`、`查询中…` 或 `Checking…`
- **AND** 系統 SHALL NOT 同時顯示舊 Citybus 距離、來源或「約」字樣

#### Scenario: 成功展示 CSDI 總步行距離
- **WHEN** 路線的所有必要非同站步行段均取得 CSDI 成功結果
- **THEN** 結果卡片 SHALL 展示各段原始米數先相加再向上取整的總距離及米單位
- **AND** 結果卡片 SHALL NOT 展示資料來源或「約」字樣

#### Scenario: 成功快取在首幀可用
- **WHEN** 路線組合與全部必要 CSDI 分段均命中有效成功快取
- **THEN** 結果卡片 SHALL 首次綁定時直接展示 CSDI 總步行距離
- **AND** 結果卡片 SHALL NOT 先閃現查詢中文案

#### Scenario: 任一必要分段最終失敗
- **WHEN** 路線任一必要 CSDI 步行段在受控重試後最終失敗或端點不可可靠確定
- **THEN** 結果卡片 SHALL 立即整體回退展示原 Citybus 總步行距離及米單位
- **AND** 系統 SHALL NOT 把 CSDI 成功段與 Citybus 分段混合計算卡片總數
- **AND** 回退結果 SHALL NOT 展示資料來源或「約」字樣

#### Scenario: 展示免費價格累計結果
- **WHEN** 查詢結果包含免費路線段
- **THEN** 結果卡片 SHALL 展示免費路線段按 0 HKD 累計後的總價格

#### Scenario: 展示全免費路線價格
- **WHEN** 查詢結果中某條候選路線總價格為 0 HKD
- **THEN** 結果卡片 SHALL 將價格展示為用戶可理解的免費或 HK$ 0.0 狀態

#### Scenario: 聚合信息位於卡片底部信息區
- **WHEN** 系統展示路線結果卡片
- **THEN** 價格、耗時和步行距離 SHALL 作為分隔線下方的信息區展示
- **AND** 信息區 SHALL NOT 展示轉乘次數
- **AND** 這些字段 SHALL NOT 擠壓路線和等候狀態的核心閱讀空間

#### Scenario: 步行資料更新不改變時間信息
- **WHEN** 卡片的步行狀態由查詢中更新為 CSDI 成功或 Citybus 回退
- **THEN** 卡片既有 Citybus 總耗時、預計到達與 ETA SHALL 保持其原始權威及格式
- **AND** 系統 SHALL NOT 把 CSDI 分段時間加入卡片總耗時

#### Scenario: 等候狀態顏色區分
- **WHEN** 結果卡片展示可用 ETA
- **THEN** `等候 X 分鐘` SHALL 使用主綠色或等效主狀態色
- **WHEN** 結果卡片展示無可用車輛狀態
- **THEN** `暫無車輛` SHALL 使用灰色
- **AND** `暫無車輛` SHALL NOT 使用橙色

### Requirement: 查询结果完整排序通过显式控件触发

系統 SHALL 通過結果區域的顯式排序控件支持按路線、價格、耗時、候車時間和步行距離排序。

#### Scenario: 默認按耗時升序排序
- **WHEN** 查詢成功並展示聚合結果
- **THEN** 系統 SHALL 默認按耗時分鐘數升序展示
- **AND** 排序控件 SHALL 展示當前排序字段為耗時且方向為升序

#### Scenario: 按路線中轉次數排序
- **WHEN** 用戶選擇路線排序
- **THEN** 系統 SHALL 按中轉次數升序排序；再次選擇同一排序字段時按中轉次數降序排序

#### Scenario: 按價格排序
- **WHEN** 用戶選擇價格排序
- **THEN** 系統 SHALL 按總價格數值升序排序；再次選擇同一排序字段時按總價格數值降序排序

#### Scenario: 按耗時排序
- **WHEN** 用戶選擇耗時排序
- **THEN** 系統 SHALL 按預計路線耗時升序排序；再次選擇同一排序字段時按預計路線耗時降序排序

#### Scenario: 按候車時間排序
- **WHEN** 用戶選擇候車時間排序
- **THEN** 系統 SHALL 按預計車輛到站候車時間升序排序；再次點擊同一排序字段時按預計車輛到站候車時間降序排序

#### Scenario: 按步行距離排序
- **WHEN** 用戶選擇步行距離排序
- **THEN** 系統 SHALL 先把 CSDI 成功或 Citybus 回退的數值結果按米數升序排序，再把查詢中結果置後
- **WHEN** 用戶再次選擇步行距離排序
- **THEN** 系統 SHALL 把數值結果按米數降序排序，且查詢中結果仍 SHALL 置後

#### Scenario: 步行數值漸進更新
- **WHEN** 目前按步行距離排序且一張卡片由查詢中更新為 CSDI 成功或 Citybus 回退
- **THEN** 系統 SHALL 按目前方向重新放置該數值卡片
- **AND** 相同數值及仍在查詢中的卡片 SHALL 以本次查詢初始次序保持穩定

#### Scenario: 非步行排序不因步行更新移動
- **WHEN** 目前排序字段不是步行距離且卡片步行狀態更新
- **THEN** 系統 SHALL 更新該卡片顯示而不因新步行數值重排結果

#### Scenario: 展示當前排序方向
- **WHEN** 用戶對任一排序字段應用排序
- **THEN** 排序控件 SHALL 顯示當前排序字段和升序或降序方向
- **AND** 未選中的排序字段 SHALL NOT 被展示為當前排序狀態
