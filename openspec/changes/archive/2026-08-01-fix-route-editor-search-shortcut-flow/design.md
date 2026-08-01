## Context

上一輪實作把 `RouteEditActivity` 內原有的起終點 XML 改為完整共用 `PlacePairEditorView`。雖然 `PlaceInputController` 的搜尋、候選與距離邏輯適合復用，但整塊幾何共用也把行程頁原有 helper、Material 尾端定位圖示、載入列、間距及交換可見性一併改掉。搜尋頁則仍有工具圖示偏心，且 `CurrentLocationSnapshot` 只在部分定位／恢復路徑傳入 controller，首次進入的真實流程沒有被完整測試覆蓋。

桌面乘車碼方面，`TransitCodeShortcutManager` 目前把 `requestPinShortcut()` 返回 `true` 映射成「請在系統視窗確認新增」，但此返回值只代表 Launcher 接受請求。Xiaomi 14／HyperOS 在「桌面快捷方式」權限關閉時可能不顯示確認面板。現有 pinned 與靜態 shortcut 又都先開啟 `MainActivity`，待主頁初始化後才呼叫 `TransitCodePaymentLauncher`，形成可見延遲。

本 change 橫跨 `ui/edit`、`ui/common`、`ui/main`、資源及 manifest，但不改變 repository、外部 API、資料庫或支付候選資料。產品決策已確認：只做 Xiaomi／Redmi／POCO 專項適配，其他 OEM 保持 Android 標準流程。

## Goals / Non-Goals

**Goals:**

- 精確恢復新增／編輯／複製行程在 `62f1abf` 前的輸入版面與互動，只保留透明交換按鈕背景。
- 讓搜尋頁維持獨立緊湊幾何，修正工具居中，並在首次、恢復及手動定位流程可靠提供候選距離。
- 讓 Xiaomi 家族在無法可靠讀取 OEM 權限時仍能到達正確設定頁，返回後只自動續辦一次，且未 pinned 不宣告成功。
- 讓桌面 pinned／靜態 shortcut 不初始化主頁，直接復用現有 AlipayHK／支付寶候選鏈。
- 以結構化狀態、純邏輯測試、Pixel 模擬器及 Xiaomi 14 真機形成可重現驗證。

**Non-Goals:**

- 不新增 Huawei、Honor、OPPO、OnePlus、realme、vivo、iQOO、Samsung 或其他 OEM 私有適配。
- 不導入非官方 HyperOS AVD、第三方依賴或隱藏 AppOps 反射。
- 不改變 Citybus、DATA.GOV.HK、Google Geocoding、路線查詢、排序、保存或通知監控契約。
- 不改變支付 package、URI、安裝偵測、候選順序或「Android 接受 Intent 即停止降級」的語義。
- 不新增資料庫或已保存行程遷移。

## Decisions

### 1. 行程頁恢復獨立 XML，只復用控制邏輯

`activity_route_edit.xml` 恢復直接擁有起點、終點、helper、載入列、候選容器和交換按鈕。`RouteEditActivity` 重新綁定歷史 view id 與 Material end icon 行為；`PlaceInputController`、候選 adapter、定位協調器及 repository 繼續復用。

恢復值以歷史實作為準：輸入框至少 `56dp`、水平內距 `16dp`、欄位間距 `14dp`、候選間距 `6dp`、獨立 `18dp` 載入指示器與說明文字、`48dp` 交換觸控區，以及候選展開時隱藏交換按鈕。交換按鈕僅把背景換成透明／borderless ripple。

否決「為 `PlacePairEditorView` 增加 route/search 雙模式」：它會把大量頁面特例集中在同一複合 View，再次增加一頁改動誤傷另一頁的風險。亦不在本次抽取更細粒度的新 row 元件，因為恢復歷史 XML 的範圍更小且可直接用現有測試驗證。

### 2. 搜尋頁自己持有候選距離位置快照

`SearchFragment` 在 View 生命週期內持有最新有效 `CurrentLocationSnapshot`，並同時傳給兩個 `PlaceInputController`。首次建立搜尋頁時，只在已有前台定位權限且系統定位可用時發出一次非阻塞快照請求；此請求與自動填入起點／Reverse Geocoding 並行，兩者互不依賴。

手動定位成功會更新同一快照；View 重建或返回 Tab 時把仍有效快照重新套用。使用者輸入、交換、查詢、地址解析失敗都不取消距離用途的有效快照。View 銷毀、語言 generation 或新位置請求使舊 callback 過期時直接忽略。

搜尋專用 `PlacePairEditorView` 保留 `8dp` 欄位間距及既有候選上限，但定位與交換各使用固定 `48dp` 工具槽，`24dp` 可見圖示及 loading 在槽內幾何居中。

否決「只在地址 Geocoding 成功後傳 snapshot」：這正是距離缺失的來源。亦不僅為候選距離主動請求新權限；沒有既有定位權限時只省略距離。

### 3. Xiaomi 使用可信狀態加一次性 UNKNOWN 權限閘門

新增可注入、可單測的 Xiaomi shortcut 權限策略，輸出 `GRANTED`、`DENIED`、`UNKNOWN`。品牌判斷只接受正規化後的 Xiaomi、Redmi、POCO manufacturer／brand。策略不使用隱藏 API 或硬編碼 AppOps 編號。

狀態流程：

```text
已 pinned
└── 直接顯示已新增

Xiaomi 且 DENIED
└── 開啟 Xiaomi App 權限頁 → 返回後續辦一次

Xiaomi 且 UNKNOWN、尚未通過本機閘門
└── 首次先開啟權限頁 → 返回後續辦一次

Xiaomi 且 GRANTED／本機閘門已通過
└── 直接 requestPinShortcut

非 Xiaomi
└── 直接使用 Android 標準流程
```

Xiaomi 設定 Intent 使用 action／package 方式指向系統權限中心，先經 `resolveActivity()`；不可解析或啟動失敗時降級到 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`。返回續辦使用一次性 pending 狀態；旋轉或 `onResume` 重入不得重複請求。只有成功 callback 或重新查詢確認 pinned 後，才持久化「本機 Xiaomi 閘門已通過」。若曾通過後再次請求仍未 pinned，清除該標記並重新提供權限入口。

否決「永遠相信 `requestPinShortcut=true`」：它無法反映 HyperOS 權限。否決「以隱藏 AppOps 精確偵測」：系統版本相容性和 Play 發佈風險不可控。UNKNOWN 首次多一次設定頁跳轉是可接受取捨，且只限 Xiaomi 家族。

### 4. pinned 請求與設定頁只交換結構化狀態

`TransitCodeShortcutManager` 繼續負責 pinned 查詢、Launcher 支援檢查、請求及成功 callback；Xiaomi 權限策略和設定 navigator 作為獨立協作者。`SettingsFragment` 只把 `PINNED`、`NEEDS_PERMISSION`、`REQUESTED`、`UNSUPPORTED`、`FAILED` 等結果映射成三語 UI，不解析例外或 API 布林值。

`REQUESTED` 只表示等待系統處理，不再立即顯示「請在系統視窗確認新增」。成功 callback 或 `onResume` 查詢到 pinned 才顯示成功。取消沒有 callback，因此保持未新增且允許重試；Launcher 不支援時保留長按 App 圖示拖出靜態 shortcut 的指引。

### 5. 桌面快捷方式改用無界面轉發 Activity

新增 `TransitCodeShortcutActivity` 作為 pinned 與靜態 shortcut 的 explicit target。Activity 使用無內容、無預覽、不進最近任務的主題與 manifest 屬性，在 `onCreate()` 立即建立既有 `TransitCodePaymentLauncher`、執行一次候選鏈並 `finish()`。全部候選本地啟動失敗時才顯示既有三語失敗 Toast。

保留穩定 shortcut id，並同時更新 `shortcuts.xml` 與 pinned builder 的 intent，讓新安裝和 App 升級後的既有入口指向同一轉發 Activity。實作後需在 Pixel 與 Xiaomi Launcher 檢查既有 pinned copy 是否更新；若 Launcher 保留舊 intent，使用同一 id 的 shortcut 更新 API 作相容更新，不建立第二個可見圖示。

App 內按鈕與候車通知入口保持目前調用方式，避免擴大生命週期變更，但都繼續復用同一 `TransitCodePaymentLauncher` 候選規則。

否決「把支付 URI 直接寫入 shortcut」：這會固定建立當下的錢包與 URI，無法處理 AlipayHK／支付寶安裝變化及 HTTPS 降級。無界面 Activity 雖會啟動 BusIsComing process，但不初始化 `MainActivity`，可在保留動態候選鏈的前提下消除可見主頁延遲。

### 6. 驗證分層

- JVM／contract：歷史行程幾何、共享邊界、搜尋 snapshot 狀態、Xiaomi 品牌與閘門狀態、設定 Intent fallback、一次性續辦、shortcut intent target、支付候選順序。
- Instrumentation：行程頁 helper／定位／載入／交換／候選、搜尋工具居中與候選距離、無界面 Activity 不建立主頁、三語深淺色。
- Pixel 模擬器：標準確認面板、成功 callback、取消、移除後狀態。
- Xiaomi 14：權限關閉、設定跳轉、返回續辦一次、固定成功、桌面點擊直達支付工具且主頁不閃現。
- 最終執行 `./gradlew build`。Xiaomi 真機未完成時必須明確標記專項驗證未完成。

## Risks / Trade-offs

- [HyperOS 設定 Intent 在版本間變動] → 啟動前 `resolveActivity()`，捕獲例外並降級到通用 App 詳情頁；以 Xiaomi 14 真機驗證實際路徑。
- [UNKNOWN 首次閘門讓已授權 Xiaomi 用戶多一次設定跳轉] → 僅首個未成功固定流程發生；成功 callback 後持久化通過狀態。
- [Launcher 保留舊 pinned intent] → 保留穩定 id、同步更新靜態與 pinned 定義，必要時以 shortcut 更新 API 原位更新；不得建立重複圖示。
- [無界面 Activity 仍顯示啟動畫面或進入最近任務] → 使用無預覽／noHistory／excludeFromRecents 配置，並在 Pixel 與 Xiaomi 真機觀察驗證。
- [首次搜尋增加一次定位請求] → 只在已有權限及定位能力時每個有效 View generation 請求一次，不阻塞輸入、不新增權限提示。
- [恢復歷史 XML 造成測試 id 變更] → 同步恢復歷史 binding／contract 測試，刪除只為錯誤共用幾何存在的測試假設。

## Migration Plan

1. 先增加／更新測試，固定行程頁歷史契約、搜尋 snapshot、Xiaomi 閘門及 shortcut 轉發行為。
2. 恢復行程頁 XML／binding，保留搜尋專用 `PlacePairEditorView`，再完成距離 snapshot 修正。
3. 增加 Xiaomi 權限策略、設定 navigator、一次性狀態與三語文案。
4. 增加無界面 Activity，更新 manifest、靜態與 pinned shortcut intent，驗證 stable id 相容性。
5. 依序運行窄測試、完整 build、Pixel 模擬器及 Xiaomi 14 真機驗收。

此變更不需要資料遷移。若 Xiaomi 專項出現不可接受回歸，可回退 Xiaomi handler 與設定導航而保留標準 pinned 流程；若轉發 Activity 有 Launcher 相容問題，可暫時把 shortcut intent 回退到既有 `MainActivity`，支付候選資料不受影響。

## Open Questions

沒有未裁決的產品行為。Xiaomi 14 當前 HyperOS 版本實際可解析的權限中心 Activity 屬於裝置能力驗證項；無論結果為何，通用 App 詳情頁回退均為既定行為。
