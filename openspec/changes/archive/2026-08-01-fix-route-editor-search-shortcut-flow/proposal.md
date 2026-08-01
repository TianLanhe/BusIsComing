## Why

上一輪共用起終點編輯器與乘車碼 pinned shortcut 修正，在實機上造成新增／編輯行程版面回歸、搜尋候選距離仍可能缺失，以及 HyperOS 權限關閉時顯示不存在的確認提示。桌面乘車碼快捷方式亦會先初始化 BusIsComing 主頁才開啟支付工具，延遲了本應一按直達的操作，因此需要以明確頁面邊界與 Xiaomi 真機行為修正現行契約。

## What Changes

- 把新增、編輯及複製行程的起終點輸入區恢復到共用 `PlacePairEditorView` 前的版面與互動；除交換按鈕背景改為透明外，不改變原有 helper、Material 尾端定位、載入列、間距、候選及交換可見性。
- 讓搜尋頁保留獨立緊湊輸入器，修正定位與交換圖示的幾何居中，並把候選距離位置快照改為與目前位置地址填入／Geocoding 解耦的頁面級非阻塞狀態。
- 讓搜尋頁起點與終點候選都以手機目前定位顯示距離；定位失敗、無權限或 callback 過期時只省略距離，不阻塞輸入或改寫地點。
- 為 Xiaomi／Redmi／POCO 的 HyperOS／MIUI 增加窄範圍桌面快捷方式權限閘門、設定頁跳轉、通用設定回退及返回後一次性自動續辦；其他 OEM 暫不增加私有識別或跳轉。
- 移除把 `requestPinShortcut()` 返回 `true` 直接解讀為「請在系統視窗確認新增」的行為；只有成功 callback 或重新查詢確認 pinned 才宣告成功。
- 讓 pinned 與靜態乘車碼 shortcut 使用無界面轉發入口，跳過 `MainActivity` 初始化，同時完整復用既有 AlipayHK／支付寶安裝偵測、Scheme／HTTPS 候選順序與本地啟動失敗降級。
- 以 Pixel 模擬器驗證 Android 標準 pinned shortcut 行為，並以 Xiaomi 14 無線或 USB 偵錯驗證 HyperOS 權限關閉、設定返回、自動續辦與桌面直達流程。
- 不改變 Citybus／DATA.GOV.HK／Google API、路線查詢、排序、保存行程、資料庫、通知監控或支付 URI；不建立非官方 HyperOS AVD。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `route-place-selection`: 取消新增／編輯／複製行程與搜尋頁必須共用完整輸入器幾何的需求，恢復行程頁原版面，並把搜尋候選距離快照擴展到首次進入、恢復與手動定位成功流程。
- `app-settings-support`: 增加 Xiaomi／HyperOS 桌面快捷方式權限閘門、設定跳轉及一次性返回續辦，並修正 pinned request、成功與失敗的設定頁回饋語義。
- `transit-code-quick-launcher`: 讓桌面 pinned／靜態 shortcut 透過無界面入口直接執行既有支付候選鏈，不初始化主頁，且不改變候選優先順序與降級契約。

## Impact

- 主要影響 `ui/edit` 的 `RouteEditActivity` 與版面、`ui/common` 的地點輸入控制器／搜尋專用複合 View、`ui/main` 的 `SearchFragment`、`SettingsFragment`、shortcut manager、乘車碼入口及 `AndroidManifest.xml`／`shortcuts.xml`。
- 需要同步調整 `route-place-selection`、`app-settings-support` 與 `transit-code-quick-launcher` 現行規格及其 contract、JVM、instrumentation 測試，避免舊共用幾何與舊確認提示繼續被測試固化。
- 新增或修改的權限、等待、未新增與錯誤文案必須同時提供香港繁體、獨立簡體及自然英文，並在深淺色、`360dp` 及 font scale `1.0／1.3／2.0` 下驗證。
- 不新增第三方依賴、資料遷移或外部網路介面；Android 標準 Launcher 仍使用 `ShortcutManager`，Xiaomi 私有設定 Intent 必須先確認可解析並降級到通用應用詳情頁。
- 自動化驗證包括相關純邏輯／contract／instrumentation 測試及 `./gradlew build`；人工驗證包括 Pixel Launcher 與 Xiaomi 14 真機，Xiaomi 真機未完成前不得宣稱 HyperOS 專項流程驗證通過。
