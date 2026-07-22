## 1. 契約與共用 UI 基礎

- [x] 1.1 先新增會失敗的 contract 測試，鎖定底部導覽 `64×32dp` indicator、`24dp` 圖示、標籤字重／間距及一般／大字體不重疊契約
- [x] 1.2 先新增會失敗的 contract 測試，鎖定唯一共用結果控制器的透明背景、`2dp/48dp/4dp/2dp` 密度與常用／搜尋引用關係
- [x] 1.3 先新增會失敗的 contract 測試，鎖定唯一共用起終點編輯器的 `56dp` 欄位、`8dp` 間距、兩個 `48dp` 工具區及 `52dp` 候選列
- [x] 1.4 實作共用 `RouteResultControls` layout／binder 與 `PlacePairEditorView`，讓新增、編輯、複製及搜尋可使用同一結構

## 2. 底部導覽與常用結果區

- [x] 2.1 依失敗測試調整 Material BottomNavigationView item 幾何，保留既有 indicator／圖示尺寸並建立約 `5dp` 標籤空隙
- [x] 2.2 將常用頁排序及摘要遷移至透明共用結果控制器，縮減 padding 並統一首張卡片 `6dp` 間距
- [x] 2.3 新增／更新常用頁與底部導覽 instrumentation 測試，驗證只有排序摘要吸頂、選中狀態持續且元件不重疊

## 3. 共用地點編輯器與搜尋頁

- [x] 3.1 將新增、編輯及複製行程遷移至 `PlacePairEditorView`，保持行程名稱、定位、Geocoding、驗證和保存行為
- [x] 3.2 將搜尋頁遷移至同一編輯器，定位／交換圖示保持置中、交換按鈕在候選展開時固定可見，候選最多顯示 3 項並展示距離
- [x] 3.3 將搜尋頁重構為 Coordinator/AppBar 單一滾動流程，讓輸入／保存／搜尋捲走，共用排序摘要吸頂且下拉刷新只在有效結果可用
- [x] 3.4 新增／更新地點輸入與搜尋 instrumentation 測試，覆蓋候選、距離、helper／error／attribution、交換、IME、保存及刷新資格

## 4. 搜尋候選距離快照

- [x] 4.1 先新增會失敗的搜尋恢復測試，驗證已有權限時靜默請求 snapshot，但不覆寫起點、不呼叫 Geocoding、不顯示 loading 或阻塞輸入
- [x] 4.2 實作具 View generation／生命週期保護的靜默 snapshot 流程，成功只更新兩個候選 controller，失敗則靜默省略距離
- [x] 4.3 驗證首次定位、手動定位、Tab 切換及 Fragment 重建後候選距離與過期 callback 行為

## 5. 乘車碼桌面快捷方式回饋

- [x] 5.1 先新增會失敗的 shortcut manager 與設定頁測試，覆蓋已固定、請求接受、成功 callback、不支援、API false、例外及取消後重試
- [x] 5.2 擴充 `TransitCodeShortcutManager` 的結構化狀態、pinned 檢查及成功 callback，保持正式 `OPEN_TRANSIT_CODE` intent 契約
- [x] 5.3 更新設定頁 `onResume` 狀態刷新與三語請求／成功／已存在／不支援／失敗回饋，不新增運行時權限
- [x] 5.4 在支援 pinned shortcut 的 launcher 驗證系統確認、成功、取消、重複點擊及移除後狀態；以測試替身覆蓋不支援分支

## 6. 回歸與交付驗證

- [x] 6.1 執行相關 JVM 與 instrumentation 測試，確認排序、query generation、保存、Geocoding、下拉刷新與乘車碼支付回退沒有回歸
- [x] 6.2 在繁體／簡體／英文、淺／深色、360dp 及字體縮放 `1.0／1.3／2.0` 驗證底欄、透明吸頂區、共用編輯器、候選距離與文字無重疊
- [x] 6.3 執行 `openspec validate fix-navigation-search-ui-followups`、`./gradlew build`、`git diff --check` 並確認 OpenSpec 所有 task 已完成
- [x] 6.4 檢查 `git status --short` 與提交範圍，依專案規則以英文 conventional commit 自動提交本次 change
