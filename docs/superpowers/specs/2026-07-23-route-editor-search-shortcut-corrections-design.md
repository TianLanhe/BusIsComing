# 行程編輯器、搜尋候選與乘車碼快捷方式修正設計

日期：2026-07-23

## 背景

`2026-07-22-navigation-search-ui-followup-design.md` 將新增／編輯行程與搜尋頁改為共用完整的 `PlacePairEditorView` 幾何，並把 Android `requestPinShortcut()` 返回 `true` 直接映射成「請在系統視窗確認新增」。實機檢查顯示這兩項決策造成了新的問題：

- 新增行程原有的常駐輔助文案、Material 尾端定位圖示、欄位間距、載入列、交換按鈕位置及候選展開行為被不必要地改變。
- 搜尋頁的定位與交換圖示沒有視覺居中，首次進入時候選距離仍可能缺失。
- Xiaomi 14／HyperOS 關閉「桌面快捷方式」權限時，App 仍提示使用者到不存在的系統確認視窗完成新增。
- 已建立的桌面乘車碼快捷方式先啟動 `MainActivity`，待主頁初始化後才開啟支付工具，造成可見延遲。

本設計修正上述回歸。若與 `2026-07-22-navigation-search-ui-followup-design.md` 的「新增、編輯與搜尋共用 PlacePairEditor」、搜尋候選距離或桌面乘車碼快捷方式章節衝突，以本文件為準。其他已完成的底部導覽、結果控制器與吸頂設計不變。

## 目標

- 把新增／編輯／複製行程恢復到 `62f1abf` 之前的版面與互動，只保留交換按鈕透明背景。
- 搜尋頁保留自己的緊湊輸入器，修正工具圖示居中，並在首次進入、恢復及重新定位後可靠顯示候選距離。
- 在 Xiaomi／Redmi／POCO 的 HyperOS／MIUI 上辨識或恢復「桌面快捷方式」權限流程，避免把請求接受誤報為成功或可確認狀態。
- 讓桌面乘車碼快捷方式跳過主頁初始化，同時完整保留 AlipayHK／支付寶偵測、Scheme／HTTPS 降級及失敗回饋。
- 以 Pixel 模擬器驗證標準 Android 行為，並以 Xiaomi 14 真機驗證 HyperOS 專項流程。

## 非目標

- 本次不為 Huawei、Honor、OPPO、OnePlus、realme、vivo、iQOO、Samsung 或其他品牌新增 OEM 私有權限偵測或設定跳轉。
- 不建立或導入非官方 HyperOS Android Studio 系統映像。
- 不改變 AlipayHK／支付寶的既有優先順序、URI、安裝偵測或降級語義。
- 不改變搜尋頁查詢、保存常用行程、結果排序、吸頂、Google Geocoding 或 Citybus 候選資料契約。
- 不改變資料庫、已保存行程、匯入匯出或監控 session 格式。

## 已確認方案

### 1. 恢復行程頁的版面所有權

新增、編輯及複製行程重新由 `activity_route_edit.xml` 定義起終點輸入區，不再 inflate 共用的 `PlacePairEditorView`。這是精確回復，不是重新設計。

必須恢復的歷史行為：

- 起點與終點使用原有 Material outlined input、`56dp` 最小輸入高度及 `16dp` 水平內距。
- 起點定位操作恢復為 `TextInputLayout` 尾端圖示，由 Material 元件負責垂直居中及觸控狀態。
- `place_search_helper` 恢復為欄位常駐輔助文案，不因焦點或空值被共享元件清除。
- 搜尋中狀態恢復為欄位下方獨立列：`18dp` 進度指示器加三語「正在匹配地點」文字。
- 起終點基礎間距恢復為 `14dp`，候選容器與所屬欄位間距恢復為 `6dp`。
- 交換按鈕恢復原來的 `48dp` 觸控區、位置、內距，以及任一候選清單展開時隱藏的行為。
- 候選數量、自適應高度、Google attribution、錯誤與已選 Place 行為均保持改動前契約。

唯一保留的新視覺變化：交換按鈕背景使用透明／borderless ripple，不保留舊實心或描邊 chip 背景。透明背景不得改變按鈕位置、觸控尺寸或可見性規則。

`PlaceInputController`、候選 adapter、定位協調器與 repository 仍可由兩個頁面共同使用。復用邊界是控制邏輯與資料模型，不是完整頁面幾何。

### 2. 搜尋頁保留獨立緊湊輸入器

搜尋頁可繼續使用 `PlacePairEditorView`，但它只服務搜尋頁，不再被視為行程頁版面的唯一來源。

- 起點定位和起終點交換各自擁有穩定的 `48dp` 觸控區。
- 可見圖示保持 `24dp`，必須在觸控區內水平及垂直居中。
- 定位 loading 與定位圖示共用同一工具槽，但兩種狀態的幾何中心一致，切換時不得推移欄位。
- 搜尋頁既有 `8dp` 欄位間距、候選上限及保存／查詢按鈕位置保持不變。
- helper、錯誤、無結果與 Google attribution 只屬於對應欄位，不移到整個起終點輸入器下方。

### 3. 候選距離使用獨立的頁面位置快照

起點與終點候選距離都以手機目前定位為基準，不以已選起點、終點或交換後欄位為基準。

搜尋頁的位置快照不再只是「自動填入目前位置並完成反向 Geocoding」的副作用：

1. 首次建立搜尋頁時，只要已有前台定位權限且系統定位可用，就非阻塞請求一次 `CurrentLocationSnapshot`。
2. 此請求與自動填入起點／Google Geocoding 並行；地址解析成功、失敗或被使用者輸入作廢，都不影響候選距離快照。
3. 取得 snapshot 後，頁面保存最新有效快照並同時傳給起點、終點兩個 `PlaceInputController`。
4. 手動定位成功時更新此快照；Fragment View 重建或返回搜尋 Tab 時，把仍有效的快照重新傳給新 controller。
5. 使用者輸入、選候選、交換和查詢均不等待 snapshot。請求失敗時候選仍正常顯示名稱，只省略距離，不顯示 `0 米` 或錯誤 Toast。
6. Fragment View 銷毀、語言 generation 改變或新請求取代舊請求後，過期 callback 不得更新目前 adapter。

候選 adapter 繼續使用既有 `GeoDistanceCalculator` 及 `PlaceDistanceFormatter.compact`，距離顯示與完整無障礙描述必須同步更新。

### 4. Xiaomi／HyperOS 桌面快捷方式權限

Android `requestPinShortcut()` 返回 `true` 只表示目前 Launcher 支援並接受請求，不表示使用者已確認或 shortcut 已固定。HyperOS 另有「桌面快捷方式」權限開關，因此設定頁必須區分 pinned 狀態、OEM 權限狀態與請求狀態。

建立窄範圍的 `XiaomiShortcutPermissionHandler`。Android 沒有讀取 HyperOS 此開關的標準公開 API，因此 handler 只接受可信系統結果，不使用反射、隱藏 AppOps 編號或假定所有 Xiaomi 版本回傳相同權限值。

- 僅在 `Build.MANUFACTURER`／`Build.BRAND` 明確屬於 Xiaomi、Redmi 或 POCO 時啟用。
- handler 輸出 `GRANTED`、`DENIED` 或 `UNKNOWN`；只有可信系統結果才能輸出前兩者，無法確認時必須輸出 `UNKNOWN`。
- `DENIED` 時，點擊設定列直接開啟 BusIsComing 的 Xiaomi 應用權限編輯頁，不先發出 pinned request。
- `UNKNOWN` 且 App 尚未成功完成過本機 Xiaomi shortcut 權限閘門時，首次點擊同樣先進入 Xiaomi 權限頁。這個一次性前置步驟避免依賴未公開偵測 API；只有真正 pinned 成功後才把本機閘門標記為已通過。
- `GRANTED` 或本機閘門已通過時，直接執行 Android 標準 pinned request。若其後返回仍未 pinned，清除本機閘門並重新提供權限入口，處理使用者日後關閉權限的情況。
- Xiaomi 私有設定 Intent 必須先經 `PackageManager.resolveActivity()`；不可解析、拋出例外或系統版本不相容時，降級到 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`。
- 從設定頁返回時，只有存在由使用者本次操作建立的 pending flag 才自動重試一次 pinned request。重試後立即清除 flag，避免 `onResume` 迴圈。
- 返回後仍未 pinned 時，設定列顯示可操作的「檢查桌面快捷方式權限」狀態；不得顯示成功。

非 Xiaomi 裝置本次只使用標準 Android 流程。不得增加其他 OEM 品牌表、私有 Activity class name 或未驗證跳轉。

設定列狀態：

```text
已固定               → 已新增至主畫面
Xiaomi 權限明確拒絕  → 需要權限；點擊前往系統設定
請求已發出           → 等待系統確認，不宣稱成功
返回後仍未固定       → 未新增；可重試或檢查權限
Launcher 不支援      → 顯示長按 App 圖示拖出靜態快捷項的指引
API 例外／返回 false → 顯示可重試失敗狀態
```

成功文案只能在成功 callback 到達或重新查詢確認 shortcut 已 pinned 後顯示。使用者取消系統面板不會收到取消 callback，因此只保持未新增狀態，不誤報成功或錯誤。

### 5. 無界面乘車碼轉發入口

目前 pinned shortcut 與靜態 shortcut 都把 `OPEN_TRANSIT_CODE` 指向 `MainActivity`；主頁完成 Fragment、repository 與導覽初始化後才呼叫支付工具，這是可見延遲的來源。

新增一個專用、無內容畫面的轉發 Activity，職責只有：

1. 在 `onCreate()` 立即建立並呼叫既有 `TransitCodePaymentLauncher`。
2. 依現有安裝狀態和順序嘗試支付目標。
3. 全部目標失敗時顯示既有三語失敗訊息。
4. 發出外部 Intent 或顯示失敗後立即 `finish()`，不留在最近任務，不建立 BusIsComing 主頁 back stack。

桌面 pinned shortcut 與長按 App 圖示的靜態「乘車碼」快捷項都改指向此轉發入口。App 內既有乘車碼按鈕及候車通知入口保持目前由既有 Activity／service 呼叫支付 launcher 的方式，避免擴大本次改動。

轉發入口必須直接復用 `TransitCodePaymentLauncher`，不得複製或固定單一支付 URI。既有契約保持：

- 同時安裝：AlipayHK Scheme → AlipayHK HTTPS → 支付寶 Scheme → 支付寶 HTTPS。
- 只安裝 AlipayHK：AlipayHK Scheme → AlipayHK HTTPS。
- 只安裝支付寶：支付寶 Scheme → 支付寶 HTTPS。
- 都未安裝：AlipayHK HTTPS。

「啟動成功」仍定義為 Android 接受外部 Intent。外部支付 App 已開啟後的頁內載入失敗無法由 BusIsComing 觀察，因此不觸發下一個降級目標。

## 元件與責任邊界

```text
RouteEditActivity
├── activity_route_edit.xml（恢復歷史幾何）
└── PlaceInputController（共用控制邏輯）

SearchFragment
├── PlacePairEditorView（搜尋專用幾何）
├── CurrentLocationSnapshot（頁面級狀態）
└── 兩個 PlaceInputController（共用控制邏輯）

SettingsFragment
└── TransitCodeShortcutManager
    ├── Android 標準 pinned shortcut 狀態與請求
    └── XiaomiShortcutPermissionHandler（僅 Xiaomi 家族）

桌面 shortcut
└── TransitCodeShortcutActivity（無界面）
    └── TransitCodePaymentLauncher（既有支付選擇與降級鏈）
```

每個邊界只負責一件事：行程頁擁有自己的視覺契約，搜尋頁擁有候選距離快照，shortcut manager 處理固定與權限狀態，無界面 Activity 只轉發支付入口。

## 生命週期與錯誤處理

- Xiaomi 設定返回的 pending flag 必須保存到 Fragment／Activity 可恢復狀態或等價的一次性狀態中，旋轉或重建不得觸發多次自動請求。
- 自動重試前先重新查詢 pinned 狀態；已固定時只刷新 UI，不再次發出請求。
- 任何 OEM Intent、權限探測與 ShortcutManager 呼叫都捕獲可預期例外並映射為結構化結果，不讓 `SettingsFragment` 解析例外字串。
- 搜尋頁靜默定位失敗不顯示欄位錯誤；使用者主動定位仍沿用既有權限、定位設定、逾時及 Geocoding 錯誤流程。
- 無界面轉發 Activity 不處理路線狀態、Fragment 或主頁導覽；支付啟動完全失敗時才短暫顯示 Toast。

## 多語言、深色與無障礙

- 新增或修改的「需要權限」「檢查權限」「等待系統確認」「未新增」及設定路徑文案同步提供香港繁體、獨立簡體及自然英文。
- 不在 Kotlin 或 XML 硬編碼 App 可見文案。
- 新增行程恢復版與搜尋版在深淺色下保持相同幾何，只使用既有語意色。
- 定位與交換觸控目標不得小於 `48dp`，並保留三語 `contentDescription`。
- 候選距離需加入候選 row 的完整無障礙描述；視覺地點名稱可單行尾部省略。

## 驗證方案

### 純邏輯與 contract 測試

- 驗證新增／編輯行程不再 inflate `PlacePairEditorView`，並恢復常駐 helper、Material 尾端定位、歷史間距、載入列及候選展開時隱藏交換按鈕。
- 驗證新增行程交換按鈕只有背景改為透明，`48dp` 觸控範圍及位置不變。
- 驗證搜尋頁工具槽為 `48dp`、可見圖示為 `24dp` 且幾何中心一致。
- 驗證首次進入、View 重建及手動定位成功均把同一目前位置 snapshot 傳給兩個候選 controller；失敗不阻塞輸入。
- 驗證候選距離以手機目前定位為基準，沒有 snapshot 時省略距離。
- 驗證 Xiaomi／Redmi／POCO 品牌映射、`GRANTED／DENIED／UNKNOWN`、UNKNOWN 首次權限閘門、成功後略過閘門、設定 Intent 不可解析、通用設定回退與一次性自動重試。
- 驗證非 Xiaomi 裝置不執行 OEM 私有跳轉。
- 驗證 shortcut 轉發入口復用既有 AlipayHK／支付寶安裝組合及 Scheme／HTTPS 降級順序。

### 模擬器與 instrumentation

- Pixel Launcher：驗證標準系統確認面板、使用者確認後成功 callback、取消後維持未固定、移除後狀態恢復。
- 驗證點擊桌面 shortcut 不建立 `MainActivity`，轉發 Activity 不留在最近任務，測試替身可記錄支付 launcher 只執行一次。
- 新增／編輯行程逐一驗證起點、終點、helper、定位、loading、交換、候選、error 及 attribution 恢復歷史版面。
- 搜尋頁驗證兩個工具圖示居中，首次進入和恢復後的候選列包含格式化距離。
- 驗證繁體、簡體、英文；淺色、深色；`360dp`；font scale `1.0／1.3／2.0`。

### Xiaomi 14 真機

Android Studio AVD 只能模擬硬體尺寸及 Android 系統映像，不能提供 Xiaomi Launcher、權限中心或 HyperOS 行為；本項必須使用真機或 Xiaomi TestIt 遠程真機。

真機驗收流程：

1. 透過 USB 或 HyperOS 無線偵錯連接 Xiaomi 14，使用 `adb devices -l` 確認。
2. 在「桌面快捷方式管理」關閉 BusIsComing 權限。
3. 點擊 App 設定列，確認進入正確的 BusIsComing 權限頁且不顯示虛假成功／確認提示。
4. 開啟權限並返回，確認只自動發起一次 pinned request。
5. 接受系統固定後，確認 callback 與設定列都顯示已新增。
6. 點擊桌面乘車碼圖示，確認 BusIsComing 主頁不閃現，直接進入已安裝支付工具的對應頁面。
7. 覆蓋至少「只安裝支付寶」的真實路徑；其餘安裝組合由自動化測試覆蓋。

## 完成標準

- 新增／編輯／複製行程除透明交換按鈕外，與指定歷史版面及互動一致。
- 搜尋頁定位與交換圖示視覺居中，首次進入及恢復後候選距離可見且不阻塞輸入。
- Xiaomi 14 權限關閉時可到達正確設定頁，返回後只自動繼續一次，未 pinned 不誤報成功。
- 桌面快捷方式不載入主頁，並保持現有完整支付工具降級鏈。
- 非 Xiaomi 裝置維持 Android 標準流程，沒有新增其他 OEM 私有行為。
- 相關自動化、Pixel 模擬器驗證、Xiaomi 14 真機驗證及 `./gradlew build` 全部通過。

## 參考資料

- Android pinned shortcut：<https://developer.android.com/reference/kotlin/android/content/pm/ShortcutManager.html>
- Android shortcut intent：<https://developer.android.com/reference/android/content/pm/ShortcutInfo.Builder>
- Xiaomi 桌面快捷方式管理：<https://www.mi.com/global/support/faq/details/KA-508068/>
- Xiaomi TestIt 雲測：<https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1523>
