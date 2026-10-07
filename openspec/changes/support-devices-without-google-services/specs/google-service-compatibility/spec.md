## ADDED Requirements

### Requirement: 正常 Google 環境保持既有行為
系統 SHALL 在 GMS 與 Google Play 商店正常可用時，保持本 change 實作前的 Google 地圖、Fused 定位、設備方向、權限時機、資料來源、更新、語音及背景監控行為；無 GMS 後備 SHALL NOT 取代、並行執行或改變正常路徑。

#### Scenario: 正常裝置啟動與使用核心功能
- **WHEN** 裝置的 GMS 與 Google Play 商店正常可用，且用戶啟動 App、搜尋地點、查詢或開啟詳情
- **THEN** 系統 SHALL 沿用原來源及既有 UI，包含方向圖層、相機、行程位置與權限互動
- **AND** 系統 SHALL NOT 新增降級提示、系統定位訂閱或後備網站更新請求；既有 Play 版本補名與帳號未擁有 App 的 metadata 請求 SHALL 保持原契約

#### Scenario: 正常 Google 請求暫時失敗
- **WHEN** GMS 環境仍可用，但定位無 fix、權限拒絕、系統定位關閉、Google HTTP 逾時、地圖載入失敗或單次 SDK 任務失敗
- **THEN** 系統 SHALL 保持原有對應失敗／重試行為
- **AND** 系統 SHALL NOT 僅依此失敗把整個環境改判為無 GMS 或啟用系統定位後備

#### Scenario: Play 正常更新路徑
- **WHEN** Google Play 商店可用並返回更新資格、有／無更新、帳號未擁有 App 或暫時失敗
- **THEN** 系統 SHALL 保持既有渠道、metadata 使用範圍、提示節流及 flexible update 行為
- **AND** 無 GMS 相容處理 SHALL NOT 擴大正常 Play 分支的網站 APK 使用範圍

### Requirement: Google 能力缺失不得阻止核心功能
系統 SHALL 允許在沒有 GMS 與 Google Play 商店的受支援 Android 裝置上安裝及啟動，並 SHALL 讓單一 Google 能力缺失只影響依賴該能力的功能。

#### Scenario: 完全沒有 GMS 與 Play 的冷啟動
- **WHEN** 用戶在未安裝 GMS 及 Google Play 商店的裝置首次安裝或重新啟動正式 App
- **THEN** 系統 SHALL 顯示可操作的主要頁面
- **AND** 系統 SHALL NOT 因初始化 Google 能力而崩潰、退出或要求先安裝／啟用 Google 才能進入

#### Scenario: 無 GMS 的核心查詢與資料操作
- **WHEN** 無 GMS 裝置有可用網絡，且用戶手動選擇起終點、查詢路線、查看 ETA、保存／編輯行程或置頂
- **THEN** 系統 SHALL 使用原有真實來源與本機資料能力完成操作
- **AND** Google 能力缺失 SHALL NOT 清除既有資料或改用 fixture

#### Scenario: 超級省電同時限制其他能力
- **WHEN** 超級省電令 Google、定位、網絡或背景能力部分不可用
- **THEN** 系統 SHALL 讓主要頁面可進入，並對受限制功能使用可解釋的失敗、停止與恢復狀態
- **AND** 系統 SHALL NOT 把失敗冒充成功或以停用省電模式作為進入 App 的必要條件

### Requirement: 能力判斷獨立且可恢復
系統 SHALL 分別判斷 GMS、Google Play 商店及系統定位；只有明確的能力缺失或停用等不支援結果 SHALL 啟用相應後備，未知與暫時請求失敗 SHALL 保持受控失敗與重試。

#### Scenario: 只有商店缺失
- **WHEN** Play 商店缺失或停用，但所需 GMS 能力經檢查仍可用
- **THEN** 系統 SHALL 保留 Google 地圖與 Fused 定位路徑
- **AND** 只有更新及其他直接依賴商店的功能 SHALL 按各自契約處理

#### Scenario: 只有 GMS 不可用
- **WHEN** GMS 明確不可用但 Play 商店仍可用
- **THEN** 系統 SHALL 對定位與地圖使用已約定後備
- **AND** 更新 SHALL 仍依 Play 自身的可用性及回應判斷，不只依 GMS 狀態轉網站

#### Scenario: 狀態未能判定
- **WHEN** 套件探測出錯、GMS 更新中或結果不確定
- **THEN** 系統 SHALL 保留主要功能可操作，並對未能安全啟動的可選能力回傳受控狀態
- **AND** 系統 SHALL NOT 把不確定狀態保存為永久無 GMS 或擅自切換資料／更新來源

#### Scenario: 從設定返回或恢復前台
- **WHEN** App 再次進入前台或從系統設定返回，且 GMS／商店能力已改變
- **THEN** 系統 SHALL 在相關能力入口重新判斷，取消舊來源並使用新能力狀態
- **AND** 舊 callback SHALL NOT 覆蓋新來源結果、重建已離開頁面或重複訂閱
- **AND** 已保存行程、查詢輸入、語言及有效結果 SHALL 保留

#### Scenario: 正常能力沒有改變
- **WHEN** 用戶返回前台而 Google 能力保持正常
- **THEN** 系統 SHALL 延續既有恢復行為
- **AND** 系統 SHALL NOT 因能力探測額外重設相機、結果、表單或權限提示

### Requirement: 無 GMS 使用系統定位後備並保持快速失敗
系統 SHALL 僅在 GMS 明確不可用時使用裝置提供的 Android 系統定位，並 SHALL 保持既有位置資料品質、使用者可控性及各流程生命週期。

#### Scenario: 系統定位供應者可用
- **WHEN** GMS 明確不可用、已有適用前台權限且設備提供可用的系統定位供應者
- **THEN** 系統 SHALL 嘗試使用該來源取得真實座標，供目前位置起點、附近行程、候選距離及文字詳情位置使用
- **AND** 系統 SHALL 保留新鮮度、精度與匹配規則，不以查詢端點冒充手機位置

#### Scenario: 一次性目前位置快速失敗
- **WHEN** 無 GMS 的一次性位置請求未能在 3 秒內取得有效結果，或供應者不存在、權限不足、系統定位停用
- **THEN** 系統 SHALL 返回既有失敗狀態並停止本輪未被其他有效需求持有的工作
- **AND** 起點地址解析 SHALL 仍受單階段 3 秒及整體 5 秒限制，且 SHALL NOT 因改用後備重新起算定位期限
- **AND** 自動及手動請求 SHALL NOT 延長至 15 秒

#### Scenario: 詳情文字位置使用持續來源
- **WHEN** 無 GMS 的文字詳情位於前台且使用者允許定位
- **THEN** 系統 SHALL 向既有行程位置匹配流程提供合格系統位置，保留其既有更新、首 fix 提示及可靠性語義
- **AND** 頁面離開前台或關閉後 SHALL 停止訂閱
- **AND** 系統 SHALL NOT 新增背景定位權限、位置軌跡或無地圖的方向感測訂閱

#### Scenario: 成功取消逾時與遲到位置
- **WHEN** 一次性請求已成功、取消、逾時、被新需求取代，或其 UI owner 已失效
- **THEN** 對應過期位置 SHALL NOT 覆蓋使用者輸入或完成下一輪新請求
- **AND** 系統 SHALL 解除該 consumer，並在沒有有效 consumer 時清理底層訂閱

### Requirement: 地址語音與提示沿用既有獨立來源
系統 SHALL 讓 Google HTTP 地址解析與系統 TTS 依各自能力運作，並 SHALL 使用既有三語、主題、無障礙及失敗／恢復規則。

#### Scenario: 無 GMS 但 Google HTTP 可達
- **WHEN** 系統定位成功且 Google HTTP 地址服務可用
- **THEN** 系統 SHALL 使用目前 LanguageSnapshot 取得真實地址，保留原始座標及 Google attribution
- **AND** 系統 SHALL NOT 僅因沒有 GMS 而拒絕地址解析或更換地址來源

#### Scenario: 地址解析失敗
- **WHEN** Google HTTP 名稱解析失敗或逾時
- **THEN** 系統 SHALL 保留原輸入並提供既有手動選地點路徑
- **AND** 系統 SHALL NOT 跨語言重試、使用虛構地址或任意預設座標

#### Scenario: 系統語音引擎可用或缺失
- **WHEN** 用戶在無 GMS 裝置啟用監控語音
- **THEN** 系統 SHALL 使用裝置可用且語言相容的既有 TTS 引擎
- **AND** 引擎或語言不可用時 SHALL 按既有原因及去重規則提示，監控在系統允許下繼續但不播報
- **AND** 系統 SHALL NOT 強制安裝 Google 語音或改變正常 GMS 裝置的語音設定

#### Scenario: 降級資訊可讀且不阻擋
- **WHEN** 系統呈現無 GMS 的可選功能不可用狀態
- **THEN** App 自有文字 SHALL 提供香港繁體、獨立簡體與自然英文，支援明暗及大字體／TalkBack
- **AND** 提示 SHALL NOT 反覆搶奪焦點或要求完成 Google 安裝才能使用核心功能
