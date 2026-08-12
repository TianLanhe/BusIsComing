## Context

目前 `RouteDetailActivity` 同時協調 Google Map、persistent bottom sheet、詳情／幾何／步行分段漸進載入與動態 ETA；摘要由 formatter／presentation 組成水平行動鏈，詳細內容由 RecyclerView、adapter 與逐行 `RouteTimelineRailView` 呈現。Google Maps My Location layer 可在已有權限時顯示原生藍點，但不向 App 提供行程匹配資料；現有 `CurrentLocationCoordinator` 只支援使用者點擊地圖控件後的一次性位置取得。

Citybus 詳情模型已提供每段完整有序站點、站序、坐標及穩定 identity；`getlinep2p.php` repository 提供經端點驗證的有序道路幾何；成功 CSDI 分段提供一個或多個有序 path。這些資料可以建立完整行程軸，但三個來源會以任意順序到達且可能局部失敗。新增能力因此必須與現有 generation、部分降級、頁面生命週期和使用者捲動所有權共同運作，不能把匹配邏輯散落在 Activity 或 adapter。

UI 遵循 `docs/ui-style-guide.md`；三語、深淺色、大字體與驗收矩陣遵循 `docs/localization-guidelines.md` 及 `docs/localization-validation-matrix.md`。位置只用於目前前台頁面，不保存、不改寫查詢端點，也不提供巴士車輛追蹤或逐步導航。

## Goals / Non-Goals

**Goals:**

- 在路線詳情首次進入前台時取得新鮮位置，並以約 `10 秒／20 米` 的省電節奏更新。
- 以完整有序行程軸可靠判定裝置靠近哪個節點、位於哪個相鄰節點之間，或位於可靠步行 path 的哪個距離比例。
- 讓摘要 pin 與詳細時間線指示器共用同一結構化匹配結果，但使用各自清楚且已確認的視覺映射。
- 在位置、詳情、道路幾何及步行 path 任意完成順序或局部失敗時保持增量、可取消及不污染其他內容。
- 尊重權限、前後台、手動收合／捲動、三語、主題與 TalkBack，並提供確定性測試 seam。

**Non-Goals:**

- 不追蹤或推算巴士車輛位置，不標記已步行／已乘坐路段，不提供導航、到站提醒或行程完成判定。
- 不申請背景位置、不增加 foreground service、不保存位置 fix、軌跡或上次匹配結果。
- 不修改 Citybus／CSDI 接口、站點 parser、路線查詢、排序、ETA、資料庫或匯入匯出格式。
- 不以站點直線、計劃時間、ETA、速度或跨資料域的統一比例補足缺失幾何。
- 不藉此全面重構 `RouteDetailActivity` 或合併現有一次性地圖定位能力。

## Decisions

### 1. 以獨立前台位置來源和頁面 controller 隔離 Activity

新增可注入的 `ForegroundLocationSource`，封裝 Fused Location Provider 的持續更新；頁面開始時先要求新鮮 fix，之後使用約 `10 秒` 目標間隔及 `20 米` 最小位移。新增 `RouteDetailLocationController` 管理權限、位置來源、page generation、首 fix timeout、最新 fix、行程軸版本、匹配狀態及 UI effect。Activity 只轉交生命週期和渲染狀態，adapter 不接觸權限或位置 API。

頁面進入前台即啟動流程：已有前台位置權限時直接取位；未授權時只顯示一次帶「開啟」action 的 Snackbar，不自動彈系統對話框。action 在仍可顯示系統權限 UI 時請求權限；永久拒絕時開啟 App 系統設定頁。系統定位關閉時每次頁面會話只提示一次並提供系統位置設定入口。約 `10 秒` 尚無首 fix 時提示一次「暫時無法取得位置」但繼續等待。

configuration change 沿用同一詳情頁會話的「已提示」與互動所有權狀態，舊 controller 停止接收 callback，新 controller 以新 generation 恢復。真正離開頁面後不保存這些狀態。現有 `CurrentLocationCoordinator` 繼續服務地圖目前位置按鈕；Google 原生藍點與 App 行程匹配分別消費權限，但互不作為對方資料來源。

**否決方案：**只使用 Maps My Location layer。該 layer 適合顯示原生藍點，但不能提供本功能所需的結構化位置資料。把持續定位直接加入 Activity 亦會加劇生命週期與 callback 交錯。

### 2. 建立具局部可靠性的結構化 `JourneyAxis`

`RouteJourneyAxisBuilder` 以穩定 identity 建立有序節點與邊：

- 節點包括查詢起點、每段巴士的上下車／途經站、同站換乘複合節點及查詢終點。
- 巴士邊只連接同一乘車段的相鄰有序站點。
- 步行邊代表起點、異站換乘及終點步行；每個成功 CSDI 子 path 保留原序與實際空隙。
- 同站換乘合併為單一複合節點，不虛構零長步行邊。

完整行程軸可在部分邊不可匹配時仍存在。巴士段只有在完整站序通過既有門禁，且所有站點可按站序單調投影到已驗證道路幾何後才可匹配。builder 以各站投影里程切分相鄰站點子幾何；任一關鍵投影距離過大、次序倒退、環線／平行路段造成不可裁決歧義時，整個乘車段標記為不可匹配，不改用站點直線。

步行段只使用成功 CSDI 回應的實際有序子 paths。位置可投影到某一實際子 path；子 path 之間的空隙不建立候選。摘要步行比例使用前序子 path 累計長度加目前 path 投影里程，再除以全部實際 path 累計長度。同站換乘命中複合節點時，摘要 pin 位於對應換乘塊中心。

builder 隨詳情、幾何與 CSDI domain 增量重算 immutable axis snapshot，並綁定 page generation 與結構 identity。ETA 或其他動態資料刷新不重建行程軸；相同 axis snapshot 與相同匹配結果不觸發 RecyclerView 更新。

**否決方案：**把所有坐標壓成一條全域 polyline。巴士站序和步行距離具有不同語義，且 path 空隙、換乘及局部失敗會被錯誤連接。只找最近站點亦無法可靠表達兩站之間。

### 3. 使用純函式匹配器及保守門檻

`RouteJourneyPositionMatcher` 只接收 axis snapshot、位置 fix 與前一個已確認狀態，輸出 `AtNode`、`BetweenNodes`、`WalkingProgress` 或 `Unreliable`。位置輸入先通過以下門禁：

- fix 年齡不超過 `20 秒`；
- accuracy 必須存在且不超過約 `75 米`；
- 最近候選距離不超過 `max(30 米, accuracy)`；
- 最近候選相對不屬於同一本地相鄰關係的次近候選，至少領先 `max(20 米, accuracy / 2)`，否則視為歧義。

匹配先評估可辨識節點。唯一節點候選輸出 `AtNode`；兩個相鄰節點同時競爭時改在兩者的可靠邊上投影。巴士邊輸出相鄰節點 identity，不輸出地理比例；步行邊輸出以實際 path 累計距離計算的 `[0, 1]` 比例。非相鄰候選、不可匹配邊或沒有可靠候選時輸出 `Unreliable`。

節點與相鄰邊的切換使用約 `15 米` 本地滯回，避免定位噪聲令指示器反覆跳動。首次可靠 fix 可匹配行程任意位置，正向與反向移動同等接受。若新候選跨越非相鄰區域，必須有連續兩個新鮮 fix 指向同一新區域才確認；第一個 fix 不顯示灰色或過期替代狀態。任何歧義、過期、低精度或離軸 fix 立即輸出 `Unreliable` 並隱藏目前指示。

**否決方案：**保留最後一次位置並變灰。這會讓使用者把過期狀態當成目前位置；可靠性優先於視覺連續。用時間、ETA 或速度猜測進度亦會混淆規劃與即時追蹤。

### 4. 摘要與詳細時間線採用同一狀態、不同映射

摘要 pin 是行動鏈內容層內的固定 `18dp × 22dp` 藍色水滴 pin，完整尖尾的尖端是唯一進度錨點。pin 位於目前摘要分段上方並隨水平內容捲動，不攔截分段點擊：

- 巴士段有 `E` 個相鄰站點邊時，第 `i` 個站點位置為 `i / E`，第 `i` 與 `i+1` 站之間為 `(i + 0.5) / E`。
- 步行段使用 matcher 輸出的累計距離比例。
- 同站換乘使用換乘塊中心；查詢起終點分別映射到首尾步行段端點。

摘要在首次可靠匹配及之後目標分段改變時自動把目標 pin 移入可見區；使用者手動水平捲動後，本次頁面不再搶奪摘要視口，pin 仍在正確內容坐標更新並可捲出畫面。

詳細時間線由 RecyclerView 上層 overlay 繪製位置指示器。adapter／rail view 只發布目前可見節點與軸段的穩定 anchor；overlay 把 child 坐標轉換到 RecyclerView 坐標，避免指示器受單行裁切。指示器以 `38dp` 柔和 halo、`26dp` 白色承托圓、外徑 `20dp` 藍色圓環與白色圓心構成；圓心數學上與軸中心重合。右側尖尾在圓環後方繪製，基部與突出量約 `8dp`，保持纖細單尖且不表達方向。

巴士軸使用 `10dp` 圓角實線。普通站點使用直徑約 `10dp` 的高對比薄荷綠圓點及 `2dp` 白色隔離邊界；上下車端點使用約 `16dp` 白色底、`3dp` 深化分段色外框及 `4dp` 角色色圓心。步行連接保留較細中性虛線。`AtNode` 對齊節點圓心；`BetweenNodes` 固定對齊相鄰節點軸段的視覺中點，不按道路距離拉動；步行進度按可見步行軸段比例定位。

**否決方案：**把詳細指示器按道路距離放在兩站間任意垂直位置。列表站點間距由內容高度決定，並非地圖距離；固定軸段中點能提供穩定、誠實的「兩站之間」語義。

### 5. 自動展開與捲動使用一次性 effect 及明確所有權

當第一個可靠狀態落入含途經站的巴士段時，controller 對該 leg 發出本次頁面唯一一次自動展開 effect，其他 leg 保持原狀，並在 RecyclerView 完成 layout 後把指示器捲入可見區。步行命中不展開無關乘車段。進入另一乘車段時，新 leg 可各自自動展開一次。

使用者手動收合曾自動展開的 leg 後，該 leg 本次頁面不再強制展開；若目標依賴已隱藏途經站，詳細指示器隱藏，摘要 pin 繼續更新。使用者手動縱向捲動後停止詳細自動跟隨；同一頁重新進入全屏時可恢復詳細跟隨。overlay 只在所需 anchors 同時可見且 axis identity 一致時繪製，不能以估算行高補畫。

自動展開、捲動、TalkBack announcement 與 Snackbar 都是綁定 page generation 的一次性 effect；RecyclerView layout 或異步資料完成後才消費。過期 generation、已銷毀頁面或舊結構 identity 的 effect 一律忽略。

### 6. 可見狀態、失敗與無障礙分離

頁面 UI state 分開表達定位流程（未啟動、等待權限、等待 fix、可用、不可可靠匹配）與行程位置 presentation。若位置先於行程資料到達，controller 只保留本頁最新合格 fix，待 axis snapshot 更新後重算；若行程資料先到達則等待 fix。任何一邊的新 generation 都不能被舊 callback 覆蓋。

普通低精度、離軸、歧義及後續短暫丟失採靜默隱藏。未授權、永久拒絕、系統定位關閉與初次 fix timeout 依前述規則提供一次性恢復入口。幾何或 CSDI 局部失敗只令對應 edge 不可匹配，其他已可靠行程位置仍可使用。

位置指示器不成為可聚焦按鈕，也不增加重複 TalkBack 節點。首次可靠匹配及已確認目標區域改變時，以 polite announcement 朗讀本地化狀態：節點使用「目前位置，靠近〈站名〉」，相鄰節點使用「目前位置，〈A〉與〈B〉之間」；步行使用對應端點名稱。相同狀態的每次 fix 不重複朗讀，`Unreliable` 不朗讀。視覺同時依靠形狀、位置、圓環與描述，不只依賴藍色。

## Risks / Trade-offs

- **[Citybus 幾何與站點投影在環線或平行道路上歧義]** → 整段標記為不可匹配，保留地圖與文字時間線，不採最近直線猜測；以環線、交叉及多段 fixture 覆蓋。
- **[近似定位或都市峽谷令指示器經常隱藏]** → 接受 accuracy 不高於約 `75 米` 的 fix，候選距離隨 accuracy 放寬，但仍要求候選差距；以可靠性優先並對一般精度問題靜默處理。
- **[持續定位增加耗電]** → 只在詳情前台啟動，使用約 `10 秒／20 米` 省電參數，相同匹配結果不刷新列表，退到後台立即停止。
- **[RecyclerView 動態高度造成 overlay 偏移]** → 由已 layout 的 child 發布實際 anchor 並逐 frame 合併位置，不保存估算坐標；缺少任一必要 anchor 時不畫。
- **[自動展開／捲動與使用者操作衝突]** → 每 leg 只自動展開一次，分開記錄摘要水平及詳細垂直所有權，手動操作立即優先。
- **[權限提示造成進頁打擾]** → 不自動彈系統對話框，只顯示一次可忽略 Snackbar；拒絕後不在同一頁反覆提示。
- **[與 `align-settings-route-detail-polish` 的地圖 delta 重疊]** → 本 change 不修改其圓形控件及 marker 契約；apply／archive 前以目前主 spec 與兩個 delta 共同驗證，MODIFIED requirement 保留所有既有 scenarios。

## Migration Plan

1. 先加入純資料模型、axis builder、matcher 與測試，不接入 UI。
2. 加入可注入位置來源及 controller，以 feature 接線方式接入既有詳情頁生命週期。
3. 先接摘要 pin，再接詳細 overlay、rail／node 視覺及自動展開／捲動 effect。
4. 補齊三語、深淺色、TalkBack、權限與 configuration change 驗證，最後執行完整 build 與任務自有模擬器驗證。
5. 本變更沒有資料遷移；若需要回退，可移除 controller 與兩個 presentation overlay，既有地圖藍點、詳情資料、快取及 ETA 路徑保持相容。

## Open Questions

無。業務範圍、匹配可靠性、權限、生命週期、UI 幾何、捲動所有權、失敗處理及驗證方式均已確認。
