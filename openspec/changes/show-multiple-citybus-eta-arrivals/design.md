## Context

主頁路線結果卡片目前以右側候車狀態突出首程最近一班車，`WaitTimeState.Available` 只保存單一分鐘數。`CitybusFirstLegEtaService` 已能從 ETA API 響應中解析多條記錄，但只取最近一條計算候車分鐘數。

Citybus ETA API 的一次響應可能包含 `eta_seq = 1..3` 的多班到站資料。用戶在日常通勤決策中不只關心「下一班多久到」，也關心「錯過下一班後要等多久」。因此需要在不降低列表掃讀效率的前提下，補充第 2 班與完整班次入口。

本變更以前置完成的 `fix-p2p-stop-sequence-alignment` 為基礎：首程 ETA 的 `stop_id` 仍來自 `showstops2.php` P2P stop map，不恢復運行時 `route-stop` 推導，也不新增額外 ETA 網絡請求。

## Goals / Non-Goals

**Goals:**
- 在同一次 ETA API 響應中保留最多 3 筆匹配班次。
- 卡片保持簡潔，突出第 1 班，只有存在第 2 班時展示 `下一班 X 分鐘 ›`。
- 透過點擊候車區打開底部面板查看第 1-3 班、分鐘數、具體時刻和備註。
- 保持卡片其他區域點擊打開路線詳情。
- 候車排序仍按第 1 班候車分鐘數。
- 在不增加網絡請求、不定時自動刷新前提下完成交互。

**Non-Goals:**
- 不展示或查詢轉乘段 ETA。
- 不在卡片展示第 3 班或 `rmk_tc`。
- 不新增側滑、長按或卡片內展開交互。
- 不新增背景定時刷新或輪詢。
- 不改變路線結果默認總耗時排序。

## Decisions

### 1. 將 `WaitTimeState.Available` 擴展為多筆 arrivals

`WaitTimeState.Available` 應保存 `arrivals: List<EtaArrival>`，並提供第 1 班候車分鐘數作為兼容排序和主展示的比較值。`EtaArrival` 建議包含：

- `sequence`: ETA 班次序號，優先來自 `eta_seq`。
- `minutes`: 對當前時間向上取整後的候車分鐘數。
- `etaMillis`: 原始 ETA 時間戳，用於排序和格式化。
- `arrivalTimeText`: 面板展示用 `HH:mm`。
- `destination`: 優先使用 `dest_tc`。
- `remark`: 非空 `rmk_tc`。
- `dataTimestampMillis`: 優先使用 response `generated_timestamp`，缺失時使用 record `data_timestamp`。

保留單一 `minutes` 派生值，而不是讓 UI 自行讀取 `arrivals[0]`，可降低排序器和既有 formatter 的改動風險。

### 2. ETA 匹配先分組，再取最多 3 筆

匹配規則沿用既有語義：

```text
1. 優先 route + stop + dir + seq 嚴格匹配。
2. 嚴格匹配為空時，降級 route + stop + dir。
3. 降級仍不得使用 route、stop 或 dir 任一不一致的記錄。
```

變更點是匹配結果不再 `minOrNull()`，而是：

```text
matchedRecords
  .filter eta 非空可解析
  .sortWith(eta_seq 升序，缺失時 etaMillis 升序)
  .dedupeBy eta_seq 或 etaMillis
  .take(3)
```

若 `eta_seq` 缺失或重複，使用 `etaMillis` 升序兜底，避免第三方資料異常導致 UI 順序混亂。

### 3. 卡片候車區是一個獨立點擊目標

卡片整體仍維持 `onRouteClick` 打開路線詳情。候車區需要獨立處理點擊：

- 2 班及以上：候車區可點擊，展示第二行 `下一班 X 分鐘 ›`。
- 0 或 1 班：候車區不可點擊，不展示箭頭。
- 點擊候車區時需消費事件，避免同時觸發卡片詳情。

為了可訪問性，候車區可點擊時應提供清楚的 content description，例如 `查看 N8P 首程候車班次`。

### 4. 使用輕量 `BottomSheetDialog` 展示 ETA 班次

專案現有路線詳情和臨時查詢均使用 Material bottom sheet 模式。ETA 班次面板也使用同一模式，但比路線詳情更輕：

```text
首程 N8P 候車時間
樂軒臺 往 灣仔(港灣道)        更新 01:02

第1班    9分鐘      01:11
第2班   24分鐘      01:26
第3班   44分鐘      01:46
         特別班次備註
```

面板不重複展示價格、總耗時、步行距離或完整途經站點。這些仍屬於路線卡片與路線詳情底部彈層。

### 5. 面板副標題的方向來源

副標題使用 `上車站 往 dest_tc`。若 ETA arrival 缺少 `dest_tc`，使用卡片站點預覽的下車站名；若仍缺失，僅展示上車站或路線方向，不阻塞面板打開。

### 6. 更新時間不觸發自動刷新

面板的 `更新 HH:mm` 只表達該次 ETA 資料時間。來源優先順序：

```text
response.generated_timestamp
record.data_timestamp
本機接收時間
```

不做定時刷新。用戶重新查詢路線才會刷新 ETA。若面板打開期間同一路線的後台 ETA 更新回來，UI 可同步刷新面板內容；若 routeId 已不存在或不是當前查詢 generation，仍按既有舊查詢忽略規則處理。

## Risks / Trade-offs

- [Risk] 卡片右側候車區新增第二行可能擠壓路線文字或站點預覽。→ Mitigation：候車區保持右側固定最小寬度，第二行使用較小次級文字；窄屏時路線文字單行省略，卡片高度允許微增。
- [Risk] 使用 `eta_seq` 排序但資料缺失或重複。→ Mitigation：以 `etaMillis` 作為兜底排序與去重依據，最多展示 3 筆。
- [Risk] 使用者可能不理解卡片只展示首程 ETA。→ Mitigation：卡片保持簡潔，面板標題明確寫 `首程 <路線> 候車時間`。
- [Risk] `rmk_tc` 可能很長。→ Mitigation：只在面板對應班次下方顯示，使用次級文字並允許最多兩行省略。
- [Risk] 面板和路線詳情都由卡片觸發，事件容易互相干擾。→ Mitigation：候車區作獨立點擊目標並消費事件；卡片其他區域保持原路線詳情入口。
- [Risk] 現有測試大量假設 `WaitTimeState.Available(minutes)`。→ Mitigation：提供向後兼容建構或 helper，分階段更新排序、formatter、repository 和 ETA service 測試。
