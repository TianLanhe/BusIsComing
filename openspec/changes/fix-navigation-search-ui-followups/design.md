## Context

目前常用頁已使用 `CoordinatorLayout`／`AppBarLayout` 讓結果排序及摘要吸頂，搜尋頁仍由單一 `NestedScrollView` 包含輸入與結果，兩頁亦各自保存重複的排序／摘要版面。新增／編輯行程與搜尋頁雖共用 `PlaceInputController`，但輸入框、定位工具、交換按鈕和候選容器仍由不同 XML 組裝，因此幾何與狀態容易漂移。

`PlaceInputController` 已能在收到 `CurrentLocationSnapshot` 後計算候選距離；問題集中在搜尋頁恢復已有輸入而跳過自動定位時，controller 沒有 snapshot。設定頁目前呼叫 `requestPinShortcut` 時沒有成功 callback，且請求被系統接受後不顯示任何狀態，導致使用者誤以為點擊無效。

本 change 橫跨 `ui/main`、`ui/edit`、`ui/common`、設定頁、shortcut manager、XML／style 及三語資源，但不改變 repository、資料格式或外部 API。

## Goals / Non-Goals

**Goals:**

- 修正底部導覽選中膠囊與標籤重疊，保持既有 Google Play 式層級。
- 讓常用與搜尋頁使用同一透明、緊湊且可吸頂的結果控制器。
- 讓新增、編輯、複製與搜尋真正共用同一份起終點編輯器幾何。
- 在搜尋恢復流程中非阻塞補齊候選距離，且不改寫輸入。
- 讓 pinned shortcut 的請求、成功、已存在、不支援和失敗均有準確回饋。

**Non-Goals:**

- 不改變 Citybus 地點／路線查詢、Google Geocoding、ETA 或支付工具回退語義。
- 不調整路線結果卡片內容、站名單行寬度分配或詳情彈層。
- 不新增資料庫、偏好或已保存行程遷移。
- 不增加定位、通知或 pinned shortcut 的虛構運行時權限。
- 不把搜尋頁改成地圖主導頁面。

## Decisions

### 1. 以 Material BottomNavigationView 的 item 幾何修正重疊

保留 `64×32dp` active indicator、`24dp` icon、選中 `13sp Bold` 與未選中 `12sp Regular`。item 內容最小高度使用 `64dp`，標籤底部 padding 使用 `6dp`，讓 indicator 底緣與標籤頂緣保留約 `5dp` 的視覺空隙，並讓字形在 item 底緣內保留至少 `2dp` 安全空間。此處使用真實量測空間而非 `translationY` 越界平移；大字體仍允許量測高度增加，避免裁切或重疊。

選擇沿用 Material indicator，而不是自行繪製背景或把 icon 與文字拆成自訂導覽列，原因是現有 destination、狀態恢復、Ripple、無障礙和 WindowInsets 均由 `BottomNavigationView` 穩定處理。否決縮小 indicator，因為使用者已確認保留 Google Play 式膠囊尺寸。

### 2. 共用 XML-backed RouteResultControls

建立單一 include layout 與輕量 binder，承載五個排序按鈕、結果摘要和更新時間。常用與搜尋頁只傳入目前排序狀態、摘要文字和點擊 callback，組件不持有 repository 或 query generation。

控制器使用透明背景、外層上下各 `2dp`、`48dp` 排序觸控行、排序至摘要 `4dp`，總高約 `76dp`；第一張結果卡前間距統一為 `6dp`。這能比目前約 `98dp` 的控制區加首卡間距節省約 `16dp`，同時不犧牲排序觸控目標。

選擇共用 include／binder，而不是複製 style，因為摘要 visibility、排序 selected state 和間距需保持同一契約。否決使用固定 surface 背景，因為它會在淡綠頁面及深色背景上形成不必要的白色／深色矩形斷層。

### 3. 搜尋頁採用與常用頁一致的單一滾動 owner

搜尋頁改為 `CoordinatorLayout`：可捲走的說明、`PlacePairEditorView`、保存與查詢按鈕位於 `AppBarLayout` 的 scroll 區；`RouteResultControls` 位於其後並保持吸頂；`SwipeRefreshLayout`／`RecyclerView` 作為主要結果滾動 owner。

候選列表仍由編輯器在 IME 上方限制高度，最多顯示 3 個完整候選，不再把整頁包成多層競爭手勢的可滾動容器。下拉刷新只在有有效結果、候選關閉且結果列表位於頂部時啟用。

選擇 Coordinator 模式是為了與常用頁共享已驗證的吸頂語義。否決只在既有 `NestedScrollView` 中增加 sticky 模擬，因為它會產生雙重滾動位置與刷新手勢衝突。

### 4. 建立 PlacePairEditorView，但保留 PlaceInputController

建立 XML-backed `PlacePairEditorView`，只負責 inflate 唯一一份起點、終點、候選、helper／error／attribution、定位工具槽與交換按鈕的版面。既有 `PlaceInputController` 繼續負責 debounce、generation、候選選擇、距離計算和欄位狀態，Activity／Fragment 繼續負責 repository、定位、Geocoding、保存和查詢。

共用幾何為：兩個最小 `56dp` outlined input、基礎間距 `8dp`、起點尾端 `48dp` 定位／進度工具槽、右側固定 `48dp` 交換觸控區、候選列約 `52dp`。候選展開只推動後續輸入欄，不移動或隱藏交換按鈕；交換按鈕的視覺中心固定在兩個收合輸入框中線。

搜尋頁候選最多 3 個完整項目；行程頁保留 3 至 6 項自適應策略。行程名稱和搜尋頁「儲存為常用行程」均留在組件外，避免把頁面專屬業務塞入共用 View。

選擇保留 controller 而不是重新實作一套完整狀態元件，可降低 callback 作廢與候選選擇回歸風險。否決只用 `<include>`，因為頁面仍會各自維護工具槽與 visibility 協調，無法真正消除漂移。

### 5. 恢復搜尋狀態時靜默請求候選定位快照

搜尋頁若恢復已有起點、使用者文字或已提交上下文，且已有前台定位權限與可用定位服務，會在該 View generation 內靜默請求一次 snapshot。成功後只呼叫兩個 `PlaceInputController.setCurrentLocationSnapshot`；不改寫起點、不觸發 Geocoding、不顯示欄位 loading 或錯誤。

使用者輸入、交換、保存及查詢均不等待 snapshot。失敗或逾時只省略候選距離。Fragment View 銷毀或 generation 變更後回來的 callback 被忽略。

選擇沿用目前位置 provider 的快照共用／超時能力，而不是另建定位來源。否決把已恢復起點重新定位成目前位置，因為這會破壞使用者上下文。

### 6. pinned shortcut 由 manager 回傳結構化狀態

`TransitCodeShortcutManager` 負責檢查 pinned 狀態、launcher 支援能力、發出帶成功 callback 的請求及將例外映射為結構化結果。設定頁只把結果映射為三語狀態／Toast，並在 `onResume` 重新查詢，處理系統確認視窗返回、成功固定與桌面移除。

流程為：已存在時顯示「已新增至主畫面」；請求被接受時提示使用者在系統視窗確認；成功 callback 後顯示成功並刷新；取消沒有 callback，不誤報失敗；不支援時指引長按 App 圖示並拖曳靜態「乘車碼」；返回 false 或例外時顯示可重試錯誤。

選擇保留 pinned shortcut 語義，不把設定列改為直接開啟乘車碼，因為該列的價值是建立桌面入口。Android 沒有對應 runtime permission，因此不增加權限請求。

## Risks / Trade-offs

- [Material 元件不同版本對 item padding 的量測略有差異] → 使用 instrumentation 讀取實際 indicator／label bounds，並在 1.0、1.3、2.0 字體縮放下做截圖驗證。
- [共用複合 View 取代兩套 XML 可能遺漏既有 id 或狀態] → 先以 contract／instrumentation 測試鎖定欄位、候選、交換、定位及保存行為，再逐頁切換。
- [Coordinator 與候選 RecyclerView 產生巢狀滾動競爭] → 候選使用明確最大高度與現有 nested scrolling 邊界，搜尋結果 RecyclerView 保持唯一頁面級主要滾動 owner。
- [靜默定位 callback 更新已銷毀 View] → 綁定 View generation 並在 `onDestroyView` 作廢，callback 只更新仍相同的 controller。
- [launcher 不提供取消 callback] → 只在成功 callback 後宣告成功；取消後靠 `onResume` 檢查並保持未新增狀態。
- [部分 launcher 無法可靠列舉 pinned shortcut] → manager 對平台能力與例外採保守結果，UI 提供可重試或靜態 shortcut 替代指引，不假報成功。

## Migration Plan

1. 先加入失敗的 contract／狀態測試，鎖定共用 layout、尺寸、透明背景、snapshot 和 shortcut 結果。
2. 加入共用 `RouteResultControls` 與 `PlacePairEditorView`，先遷移行程頁，再遷移搜尋頁，移除失效的重複 view id／style。
3. 重構搜尋頁 Coordinator 結構與靜默 snapshot，保留既有 query state／generation。
4. 擴充 shortcut manager 與設定頁狀態，補齊三語資源。
5. 執行單元、instrumentation、全量 build 與多語深淺色畫面驗收。

回退時可還原共用 layout 的頁面引用及 shortcut 結果映射；本 change 無資料遷移，不影響既有行程或查詢資料。

## Open Questions

無。底部導覽尺寸、結果控制密度、候選數量、交換按鈕行為、靜默定位與快捷方式回饋均已在前置設計討論中確認。
