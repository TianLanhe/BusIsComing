## 1. 導航與畫面基礎

- [x] 1.1 檢查並補齊 Fragment 所需 AndroidX 依賴與 version catalog，新增底部導航文字、圖示、content description 及選中色資源，不引入 Navigation Component、Compose 或 Google Maps SDK。
- [x] 1.2 將 `MainActivity` 改為頂層宿主，建立包含 `FragmentContainerView` 與 `BottomNavigationView` 的 XML 佈局，套用既有 WindowInsets 與淡綠根背景，並讓冷啟動預設選中「常用」。
- [x] 1.3 實作三個固定頂層 Fragment 的建立、非破壞性切換、選中 tab 保存與恢復；驗證切換不清空其他 destination 的視圖狀態。
- [x] 1.4 將現有設定內容與 `SettingsActivity` 點擊行為抽取為可重用設定 Fragment，移除頂層返回按鈕與主頁設定快捷入口，同時保留分享、回饋、隱私、Toast 與 About Activity 行為。

## 2. 共用查詢狀態與生命週期

- [x] 2.1 從 `MainActivity` 抽取 owner 無關的路線查詢協調與 `RouteQueryState`，承接基礎結果、ETA／站點預覽增量更新、排序、刷新、更新時間與錯誤狀態，且不改變 repository 的 Citybus／ETA 契約。
- [x] 2.2 為每個查詢 owner 建立 generation、取消與生命週期失效規則，確保切換常用路線、編輯搜尋、切換 destination 或銷毀宿主後，遲到回呼不會覆蓋新 state。
- [x] 2.3 調整結果卡 Adapter、ETA 彈層、路線詳情彈層與監控入口，使其可由常用與搜尋兩個 Fragment 使用，且不改變可監控路線、票價、耗時、步行距離或 ETA 展示語義。
- [x] 2.4 為共用查詢 state、排序、刷新 generation 與過期回呼增加 JVM 單元測試。

## 3. 遷移常用路線流程

- [x] 3.1 建立常用路線 Fragment 與 XML，遷移常用快捷卡、完整列表、選中狀態、查詢、排序、刷新、結果狀態與首次引導頁，保持既有常用路線資料庫與使用排序行為。
- [x] 3.2 將乘車碼、路線管理、目前位置自動選路與權限降級遷移到常用 Fragment，驗證返回路線管理後會刷新常用路線且不重複錯誤地重新定位。
- [x] 3.3 從常用 Fragment 移除臨時查詢按鈕、完整列表中的臨時入口、臨時上下文條、編輯／保存臨時操作與相關空狀態分支；首次引導的一次性查詢操作改為切換至搜尋 tab。

## 4. 建立搜尋 destination

- [x] 4.1 建立搜尋 Fragment 與 XML 表單，使用 `PlaceInputController`、既有 Citybus 地點搜尋、候選列表、候選距離、目前位置 attribution、起終點校驗及 48dp 交換圖示，取代 `TemporaryRouteBottomSheet` 的輸入 UI。
- [x] 4.2 實作搜尋查詢、載入、無結果、失敗與成功狀態；結果區以 `起點 → 終點` 摘要、編輯與 `存為常用` 操作承接上下文，並重用共用結果卡、排序、ETA、詳情、監控與下拉刷新能力。
- [x] 4.3 將搜尋保存成功後的常用路線資料刷新通知回傳給常用 Fragment，但保持目前搜尋結果、排序和 tab 不變；保留名稱重複與空名稱校驗對話框行為。
- [x] 4.4 在所有入口完成遷移後刪除 `TemporaryRouteBottomSheet`、主頁臨時上下文與不再使用的資源、string、view id、回呼與 executor；確認搜尋頁第一階段不顯示任何地圖功能或占位。

## 5. 次級導航、狀態恢復與 UI 回歸

- [x] 5.1 確保從常用進入路線管理或編輯、從設定進入關於後，返回會回到原 destination；次級頁不顯示或複製底部導航。
- [x] 5.2 保存並恢復選中 tab、常用選中路線、搜尋起終點與排序；對無法安全持久化的結果 state 採取明確降級，且不得發起未經用戶操作的重複查詢。
- [x] 5.3 檢查常用、搜尋與設定的 WindowInsets、巢狀捲動、候選列表、下拉刷新、窄屏和字體縮放，修正文字重疊、觸控區與可見狀態問題。

## 6. 自動化與人工驗證

- [x] 6.1 新增或更新 instrumentation 測試，覆蓋冷啟動常用、三 tab 切換與狀態保留、常用首次引導切換搜尋、設定頂層頁無返回按鈕，以及次級頁返回。
- [x] 6.2 新增或更新搜尋流程 instrumentation 測試，覆蓋 Citybus 候選選擇、目前位置失敗降級、交換、校驗、查詢、摘要編輯、保存為常用、排序、刷新、ETA、詳情、監控與過期回呼不覆蓋新查詢。
- [x] 6.3 回歸現有 Citybus fixture 與 repository 測試，確認 `ppsearch_p3.php`、`showstops2.php`、ETA、路線排序及停止預覽沒有因 UI 遷移改變；不以 fixture 取代生產 HTTP。
- [x] 6.4 在模擬器或真機手動驗證三個 destination、旋轉、字體縮放、TalkBack、定位權限拒絕、無網路、查詢失敗、結果刷新與路線管理返回；記錄無法取得的設備驗證。
- [x] 6.5 執行 `./gradlew build`，檢查 `git status --short` 與 staged 範圍，完成後依專案規則提交此 change 的實作。
