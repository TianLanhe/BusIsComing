## Why

目前常用頁的固定控制區佔用過多首屏空間、路線卡片長站名會改變高度、底部導航選中態會擠壓標籤，而搜尋頁又重複展示起終點摘要並缺少穩定可見的輸入焦點。乘車碼同時佔據常用頁空間，卻未提供真正接近乘車時機的快速入口，因此需要在不改變查詢與既有資料的前提下重新整理這些介面層級。

## What Changes

- 常用頁把「常用行程／全部／管理」恢復為同一水平列，收緊空白，並建立單一捲動體系；常用行程和查詢控制隨頁面捲走，只有排序項與結果摘要吸頂。
- 路線卡片在既有左側站名區內，以固定箭頭和三語自適應寬度展示單行上落車站；不改變右側 ETA／通知區或把站名延伸至整卡寬度。
- 底部導航採用固定 24dp 圖示、64×32dp 選中膠囊及穩定的選中標籤樣式，動畫結束後仍持續標示目前 destination。
- 搜尋頁移除「搜尋／搜索」大標題，修正輸入左內距、焦點光標、輔助文案和深淺色對比。
- 搜尋頁移除重複的臨時結果摘要與編輯動作；「存為常用行程」改放在輸入框列內，並只在目前起終點已有相符的非空結果時顯示。
- 搜尋結果的排序項、路線數量和更新時間與常用頁使用相同順序和樣式。
- 搜尋頁首次進入時沿用新增行程的非阻塞「定位＋Google Reverse Geocoding」流程；定位中仍可輸入，使用者修改起點或交換後舊回調不得覆蓋畫面。
- 乘車碼完全移出常用頁，改由長按 App 圖示的靜態 shortcut、設定頁添加的 pinned shortcut，以及候車通知中的情境化 action 提供入口。
- 所有變更同步覆蓋繁體、簡體、英文、淺色、深色、窄屏與大字體；不修改 SQLite schema、Citybus／DATA.GOV.HK／Google 接口或既有行程資料。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `app-chrome-layout`: 修改底部導航持續選中態的尺寸、膠囊背景、標籤與大字體行為。
- `app-settings-support`: 在設定頁提供添加與管理乘車碼桌面快捷方式的入口。
- `app-ui-style-system`: 明確三語、深淺色、窄屏及大字體下的新導航、輸入、站名與快捷入口視覺契約。
- `main-route-selection`: 修改常用行程標題列、頁面密度及非吸頂控制的捲動行為。
- `notification-bar-monitoring`: 在候車監控通知加入不會停止監控的乘車碼 action。
- `route-card-stop-preview`: 修改站名預覽為既有左側寬度內的三語自適應單行展示。
- `route-place-selection`: 修改搜尋頁輸入間距、焦點、輔助狀態與首次非阻塞自動定位行為。
- `route-query-results-layout`: 修改常用頁吸頂區、搜尋結果摘要、保存入口及兩頁一致的排序／摘要層級。
- `transit-code-quick-launcher`: 把正式乘車碼啟動鏈擴展至靜態 shortcut、pinned shortcut 與通知 action，並移除常用頁入口。

## Impact

- 主要影響 `ui/main` 的 `MainActivity`、`FrequentRoutesFragment`、`SearchFragment`、`BusRouteCardBinder`、頂層導航與搜尋／常用頁 XML，並需要少量共用狀態與佈局策略以隔離捲動、站名寬度和定位 generation。
- `SettingsFragment`、設定頁資源及 Android shortcut metadata 需要新增桌面快捷方式管理入口；`BusMonitorService` 通知 action 需要復用同一乘車碼 intent action。
- `TransitCodePaymentLauncher` 的 provider 候選鏈、AlipayHK／Alipay package visibility 和既有失敗提示保持不變；不新增支付 provider 或保存支付偏好。
- Google Geocoding v4、Citybus 與 DATA.GOV.HK 的請求參數和返回解析不變；搜尋頁只復用現有 `CurrentLocationCoordinator`、`GoogleReverseGeocodingPlaceNameResolver`、語言 snapshot、timeout 與 cache。
- 不涉及資料庫或已保存行程遷移。需要更新既有 UI contract／unit tests，新增 shortcut、通知 action、非阻塞定位及自適應站名測試，並以三語×深淺色、360dp、font scale 1.0／1.3／2.0 和 Android 模擬器進行人工驗收。
