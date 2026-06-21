## Context

主頁目前透過 `RouteConfigRepository.getAll()` 取得已保存常用路線，並依使用次數、最近使用時間、更新時間和 id 排序。`MainActivity.loadRouteConfigs()` 在有常用路線時選中前一次選中的路線，否則選中排序後第一條路線；`RouteShortcutSelector.visibleRoutes(...)` 控制首頁最多 3 張快捷卡，並在選中路線不在原始 Top 3 時臨時提升到第一張卡。

每條 `RouteConfig` 已持久化起點與終點的 `Place(name, latitude, longitude)`，因此依目前位置選擇最近起點不需要資料庫 schema 變更。Android 定位與 Citybus 地點座標都使用經緯度，功能可在本機以直線距離判斷最近起點，不需要調用 Citybus 查詢或步行路線服務。

本變更跨越權限、定位依賴、主頁生命週期、快捷卡 UI 和純邏輯選擇策略。`MainActivity` 已有通知權限請求使用 `ActivityCompat.requestPermissions(...)` 與 `onRequestPermissionsResult(...)`，本變更應沿用同一風格，避免同一 Activity 內並存兩套權限請求模式。

## Goals / Non-Goals

**Goals:**

- 主頁打開時，在常用路線至少 2 條的情況下，自動嘗試一次定位並選中起點最近的常用路線。
- 接受精確或粗略定位，但用位置精度與明顯領先規則降低誤選風險。
- 自動定位成功時，只在主頁快捷卡上以 `附近` 標籤說明該選中來源。
- 定位失敗、未授權、精度不足或 Google Play Services Location 不可用時，穩定回退到既有常用排序預設路線。
- 保持完整常用列表排序、使用統計、查詢流程和 Citybus API 行為不變。
- 將距離與精度判斷抽成可 JVM 單測的純邏輯，降低定位服務本身對測試的影響。

**Non-Goals:**

- 不新增手動「附近」或「重新定位」入口。
- 不在定位成功後自動發起巴士路線查詢。
- 不修改 `route_configs` schema，不改已保存路線資料格式。
- 不改 Citybus mobile、DATA.GOV.HK ETA 或任何巴士查詢 HTTP 參數。
- 不新增 `LocationManager` fallback。
- 不在完整「全部」路線列表顯示 `附近` 標籤。

## Decisions

### 1. 使用 FusedLocationProviderClient 取得一次目前位置

本變更新增 Google Play Services Location 依賴，使用 `FusedLocationProviderClient` 以 `PRIORITY_HIGH_ACCURACY` 請求一次目前位置，定位請求上限為 3 秒。

選擇原因：

- 這是主頁自動選路的核心體驗，定位品質比省下一個依賴更重要。
- Fused provider 比直接操作多個 `LocationManager` provider 更適合「取一次目前位置」場景。
- 若用戶只授權粗略位置，仍可取得模糊後的 `Location`，再交由精度規則判斷是否足夠。

替代方案：

- 使用 `LocationManager`：不新增依賴，但 minSdk 25 下需要處理較多 provider、舊 API 和一次性更新 fallback，實作與測試邊界更大。
- 只使用 last known location：快且省電，但可能為空或過舊，不適合作為自動選路的唯一依據。

### 2. 定位只在 MainActivity 每次建立後自動嘗試一次

`MainActivity` 建立後，如果常用路線數量至少 2 條，且本次 Activity 尚未嘗試自動定位，則啟動定位流程。`onResume()` 從管理頁返回時不重新定位。定位進行中界面先按既有常用排序顯示預設選中路線，不顯示額外 loading。

選擇原因：

- 保留主頁打開後立即有可查路線的現有體驗。
- 避免用戶從管理頁返回或切回前台時，選中路線被反覆自動改動。
- 若定位結果返回前用戶手動選擇了路線，丟棄該定位結果，尊重用戶最新操作。

替代方案：

- 每次 `onResume()` 都重新定位：會在返回主頁時覆蓋用戶選擇。
- 定位完成前不選中任何路線：破壞既有「打開即可查預設路線」體驗。

### 3. 用純策略類決定是否自動選中路線

新增純邏輯策略，例如 `NearbyRouteSelectionPolicy`，輸入目前位置座標、精度、按使用統計排序後的 `RouteConfig` 列表，輸出是否可自動選中以及選中的 route id。

距離計算使用目前位置到 `route.origin.latitude / route.origin.longitude` 的直線距離。若距離相同或非常接近，保留輸入列表順序作為 tie-breaker；由於輸入列表已由 repository 按常用排序排列，tie-breaker 自然保持既有排序語義。

精度規則：

- `accuracy <= 500m`：直接選擇最近起點。
- `accuracy > 500m`：只有最近起點比第二近起點至少近 `accuracy` 米，才選擇最近起點。
- 其他情況不自動選中，回退常用排序並提示精度不足。

選擇原因：

- 將可測規則從 Activity 生命週期和 Google Play Services callback 中分離。
- 粗略位置仍可在「明顯領先」時生效，但避免香港密集起點下因粗略位置誤選。
- 不需要地圖步行路線 API，也不誤用 Citybus 查詢結果中的起點到上車站步行距離。

替代方案：

- 只接受 `accuracy <= 500m`：可靠但粗略位置幾乎無效。
- 拿到任意經緯度就選最近：功能積極但誤選風險高。

### 4. 權限拒絕狀態持久化，Toast 進程內限頻

App 宣告並請求 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`。首次符合自動定位條件且沒有權限時，直接彈系統權限請求。若用戶拒絕，使用 `SharedPreferences` 記錄「不要再自動彈定位權限請求」。後續無權限時不再自動請求，只按常用排序回退。若用戶之後在系統設定重新授權，下次仍恢復自動定位。

失敗 Toast 分三類，且同一 App 進程內每類最多提示一次：

- `未允許定位，已按常用排序選擇路線`
- `暫時無法取得目前位置，已按常用排序選擇路線`
- `目前位置不夠精確，已按常用排序選擇路線`

選擇原因：

- 直接請求權限符合「主頁打開自動定位」目標。
- 拒絕後不反覆彈權限，保護用戶可控性。
- Toast 說明 fallback 原因，但限頻避免同一使用期間反覆打擾。

替代方案：

- 每次打開都請求權限：打擾感強。
- 只在進程內記住拒絕：冷啟動後仍可能再次自動彈權限。

### 5. `附近` 是短暫 UI 狀態，不改排序與統計

`MainActivity` 保存一個臨時狀態，例如 `nearbySelectedRouteId`。當 `selectedRoute?.id == nearbySelectedRouteId` 且該路線出現在首頁快捷卡中，卡片右上角顯示 `附近` 標籤。定位成功但最近路線剛好就是預設選中路線，也應顯示 `附近`，表示定位判斷已生效。

用戶手動從快捷卡或完整列表選擇任意路線時，清空 `nearbySelectedRouteId`。自動選中不調用查詢，不增加使用次數，不更新最近使用時間。若自動選中的路線不在原始 Top 3，沿用現有 `RouteShortcutSelector` 臨時提升規則。

選擇原因：

- 符合已確認的卡片標籤視覺方案。
- 完整列表維持原有簡潔樣式和真實使用排序。
- 將定位選中與使用統計明確分離，避免打開 App 就污染常用排序。

替代方案：

- 在標題下方顯示一行狀態文字：信息完整但佔用主頁高度。
- 在標題行顯示 chip：省空間但無法清楚表達失敗與成功語義。

## Risks / Trade-offs

- Google Play Services Location 不可用 → 回退常用排序並提示暫時無法取得目前位置；不維護第二套 `LocationManager` fallback。
- 粗略位置可能誤選 → 使用 `accuracy` 和明顯領先規則；精度不足時不自動選中。
- 定位 callback 晚於用戶手動選擇 → 使用本次 Activity 的定位嘗試狀態和手動選擇標記丟棄過期結果。
- 首次打開主頁直接請求定位權限可能打擾 → 僅在常用路線至少 2 條時請求；拒絕後持久化，不再反覆自動彈窗。
- `MainActivity` 已較大 → 將距離與選擇策略、Toast 限頻狀態抽成小型 policy/helper，Activity 只負責接線與 UI 狀態。
- 卡片右上角新增 `附近` 可能擠壓窄屏內容 → 標籤短且只在選中卡片顯示；實作需檢查文字截斷、字體縮放和觸控區域。

## Migration Plan

- 新增依賴與 manifest 權限後，不需要資料庫遷移。
- 用戶首次升級後，只有在常用路線至少 2 條且主頁建立時才進入定位流程。
- 已保存常用路線、使用統計、查詢結果和監控 session 不需要遷移。
- 若新定位流程失敗或不可用，App 行為回到既有常用排序預設選中。

## Open Questions

無。定位觸發時機、權限策略、精度規則、UI 標籤、Toast 文案、fallback、測試範圍均已在提案前確認。
