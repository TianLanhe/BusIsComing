## 1. 行程軸模型與測試基線

- [x] 1.1 在 `data/model`／`data/location` 邊界新增 immutable 行程節點、可匹配邊、axis snapshot 及結構化位置結果，綁定穩定 identity 與 page generation，且不改動既有 Citybus／CSDI 原始模型。
- [x] 1.2 先建立直接路線、多段換乘、同站換乘、環線／平行道路、CSDI 多子 path／空隙及局部失敗的確定性 unit fixtures，確認不包含真實使用者坐標或秘密。
- [x] 1.3 先撰寫失敗測試覆蓋站點單調投影、相鄰巴士邊切分、步行累計距離、同站複合節點、局部不可匹配及任意完成順序，再實作 `RouteJourneyAxisBuilder` 令測試通過。
- [x] 1.4 加入 axis snapshot identity／generation、相同輸入不重建及 ETA-only 刷新不改變靜態行程軸的回歸測試。

## 2. 可靠位置匹配與穩定狀態

- [x] 2.1 先撰寫位置年齡 `20 秒`、accuracy 約 `75 米`、`max(30 米, accuracy)` 離軸門檻及 `max(20 米, accuracy / 2)` 候選差距的邊界 unit tests。
- [x] 2.2 先撰寫唯一節點、相鄰巴士站之間、步行 path 進度、非相鄰歧義、不可匹配邊及 path 空隙的失敗測試，再實作純 `RouteJourneyPositionMatcher`。
- [x] 2.3 先撰寫約 `15 米` 節點／邊滯回、首次任意位置、正反向相鄰移動、非相鄰跳動連續兩 fix 確認及確認失敗立即隱藏的狀態轉移測試，再完成穩定器。
- [x] 2.4 驗證不可靠、過期或低精度 fix 不保留灰色舊位置，相同確認結果不發出重複 UI 更新，舊 generation 不可覆蓋新狀態。

## 3. 前台位置來源、權限與頁面 controller

- [x] 3.1 新增可注入 `ForegroundLocationSource`，封裝 Fused Location Provider 的新鮮首 fix 與約 `10 秒／20 米` 持續更新，並以 fake source 測試開始、停止、取消及過期 callback。
- [x] 3.2 新增 `RouteDetailLocationController` 組合最新合格 fix、axis snapshot、匹配狀態及一次性 effects，保留現有一次性 `CurrentLocationCoordinator` 的地圖按鈕職責。
- [x] 3.3 先以測試覆蓋已有權限直接定位、未授權 Snackbar、action 後請求、永久拒絕開 App 設定、系統定位關閉入口、首 fix 約 `10 秒` timeout 及每頁提示去重，再接入 Activity Result／設定返回流程。
- [x] 3.4 將 controller 接入路線詳情前台／後台、關閉及 configuration change；驗證舊 generation 停止派送、頁面背景不持續更新、真正退出後不保存位置或互動狀態。

## 4. 摘要目前位置 pin

- [x] 4.1 先撰寫 presentation tests 覆蓋巴士站點 `i / E`、兩站間 `(i + 0.5) / E`、步行累計距離、同站換乘中心及行程端點映射，再將結構化位置加入摘要 presentation。
- [x] 4.2 以固定尺寸 vector／custom drawable 實作 `18dp × 22dp` 藍色水滴 pin，讓完整尖尾的尖端成為錨點，並加入 intrinsic bounds／比例不變形測試。
- [x] 4.3 在摘要水平行動鏈內容層加入不攔截點擊與 TalkBack 的 pin overlay，驗證 pin 隨內容捲動、分段觸控高度及焦點順序保持不變。
- [x] 4.4 實作首次可靠匹配及目標分段改變的摘要自動水平定位；以互動測試確認手動水平捲動後停止搶奪視口，但 pin 仍在正確內容坐標更新。

## 5. 詳細時間線視覺與 overlay

- [x] 5.1 更新 `RouteTimelineRailView` 的巴士軸為約 `10dp` 圓角實線，加入約 `10dp` 薄荷綠／白邊普通站點及約 `16dp` 白底、深化分段色外框、角色色圓心端點；保留步行細中性虛線並加入明暗模式幾何／對比測試。
- [x] 5.2 實作 RecyclerView 可見 anchor registry 與頂層位置 overlay；測試 child 到 RecyclerView 坐標轉換、回收／舊 identity、動態行高及必要 anchor 不可見時隱藏。
- [x] 5.3 以固定幾何實作 `38dp` halo、`26dp` 白色承托圓、外徑 `20dp` 藍色圓環及圓後約 `8dp` 纖細右側單尖尾，加入圓心與軸中心精確重合及無拉伸回歸測試。
- [x] 5.4 將 `AtNode` 對齊節點圓心、`BetweenNodes` 固定對齊軸段視覺中點、步行進度對齊可見步行軸；確認不使用道路距離、時間、ETA 或估算行高改寫詳細位置。

## 6. 自動展開、捲動所有權與無障礙

- [x] 6.1 先撰寫每 leg 只自動展開一次、步行不展開無關 leg、進入新 leg 可展開、其他 leg 狀態不變及不強制改變 bottom sheet 檔位的互動測試，再接入展開 effect。
- [x] 6.2 實作詳細時間線首次展開自動定位與預設跟隨；測試手動收合不再強制重開、手動縱向捲動停止跟隨、同頁重新進入全屏恢復跟隨，以及摘要 pin 不受詳細 anchor 隱藏影響。
- [x] 6.3 新增香港繁體、獨立簡體及自然英文的權限、設定、timeout 與目前位置語義資源，不翻譯第三方站名或路線資料。
- [x] 6.4 實作首次可靠位置及確認區域改變的 polite TalkBack announcement；測試節點／相鄰端點文案、相同 fix 去重、不可靠狀態不朗讀，以及指示器不成為按鈕或重複焦點。

## 7. 整合回歸與交付驗證

- [x] 7.1 加入路線詳情 instrumentation 測試，覆蓋頁面首開即定位、權限允許／拒絕／永久拒絕、系統定位關閉、前後台停止、configuration change、摘要先於詳情顯示 pin、局部幾何／CSDI 失敗及過期 generation。
- [x] 7.2 回歸地圖原生藍點與目前位置控件、相機 ownership、三個 bottom sheet 檔位、摘要分段點擊、途經站手動展開、動態 ETA 刷新、局部重試及返回來源上下文，確認新增狀態不重建靜態列表或標記已行經路段。
- [x] 7.3 依 `docs/localization-validation-matrix.md` 驗證約 360dp portrait 的三語、淺／深、font scale `1.0／1.3／2.0` 及 TalkBack；確認 pin、圓點、軸線、Snackbar、長站名與觸控／焦點不重疊。
- [x] 7.4 在裝置操作前定義 API、約 360dp、Google APIs／Play、portrait、語言、主題與 font scale 畫像，只啟動本任務擁有的適配 AVD；以 mock location 沿已知直達、換乘及反向樣本驗證站點、兩站之間、步行比例、歧義隱藏及跳動確認，完成後關閉本任務啟動的模擬器。
- [x] 7.5 以有效 Google Maps、真實 Citybus 詳情／`getlinep2p.php` 幾何及成功 CSDI paths 抽查直達與換乘資料前提，記錄任何未能執行的外部服務或設備門檻，不以 fixture 代替 live 結論。
- [x] 7.6 檢查 Manifest／程式碼未新增背景位置或 foreground service，未持久化或常規記錄原始坐標；運行定向 unit／instrumentation 測試、`./gradlew build` 及 `openspec validate add-route-detail-current-position --strict`，核對 git diff 只包含本 change 授權範圍。
