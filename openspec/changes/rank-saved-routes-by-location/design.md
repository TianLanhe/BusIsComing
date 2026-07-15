## Context

`RouteConfigRepository.getAll()` 目前在 SQLite 查詢中依 `usage_count`、`last_used_at`、`updated_at` 與 id 排列路線；`MainActivity.loadRouteConfigs()` 直接使用此順序，並由 `RouteShortcutSelector` 取前 3 條。主頁自動定位成功後只透過 `NearbyRouteSelectionPolicy` 選中可信的最近路線，不會重排路線清單；使用者手動選擇會使延遲定位結果整體失效。

目前每次已保存路線的非刷新查詢都會呼叫 `RouteConfigRepository.recordUsage()`。下拉刷新已傳入 `recordUsage = false`，但重複按下 `查詢` 仍會累加。`MainActivity` 沒有保存「本次連續選中路線已計數」的會話狀態，因此不能區分首次使用和重複操作。

本變更不修改 Citybus／DATA.GOV.HK、路線資料庫 schema、定位權限流程或畫面版型。排序採用現有 `GeoDistanceCalculator` 的整數米直線距離；成功位置僅留在目前 `MainActivity` 會話，供列表重排和從管理頁返回後重用。

## Goals / Non-Goals

**Goals:**

- 成功取得位置後，以起點直線距離、使用統計及既有穩定兜底順序排列快捷卡和完整列表。
- 定位無法使用時完整保留既有使用統計排序與選中降級行為。
- 保持低精度位置可排序，但只在既有精度規則成立時自動選中和顯示 `附近`。
- 在一次首頁會話內，讓同一條連續選中的保存路線只記錄首次明確查詢；支援旋轉螢幕恢復。
- 讓返回管理頁、新增、編輯或刪除路線後可用本會話最近成功位置重新排列，而不額外請求定位。

**Non-Goals:**

- 不持續定位、不顯示距離、排名或使用統計，也不新增排序控制項。
- 不變更 SQLite schema、歷史統計數值、Citybus 查詢、ETA、路線結果排序或臨時查詢流程。
- 不以步行距離、道路距離或定位精度調整距離排序結果。
- 不把臨時查詢當作已保存路線的使用，也不讓它重置已保存路線的計數資格。

## Decisions

### 1. 以純排序策略在 UI 層組合定位與持久化排序

`RouteConfigRepository.getAll()` 繼續提供既有的使用統計基準順序，保持 repository 不依賴 Android 位置或畫面生命週期。新增可 JVM 單測的純排序策略（位於既有 `data/location` 或等效 domain 邊界），輸入 `CurrentLocationSnapshot?` 與已按基準順序取得的 `RouteConfig` 列表：

1. 無可用位置時原樣返回輸入列表。
2. 有位置時以 `GeoDistanceCalculator.distanceMeters()` 的整數米結果升序排列。
3. 距離相同時以 `usageCount` 降序、`lastUsedAt` 降序排列；仍相同時保留 repository 已提供的 `updated_at`、id 基準順序。

保留輸入順序作為最後兜底可避免為 UI 排序擴大 `RouteConfig` 或資料庫 schema。`RouteShortcutSelector` 和完整列表都只消費同一份已排序 `routeConfigs`，故選中路線被提升至快捷卡時仍維持既有的 Top 3 行為。

替代方案：把位置傳入 `RouteConfigRepository` 並改寫 SQL 排序，會令資料存取層依賴短暫定位資料且難以處理無位置降級；或只重排快捷卡，會令「全部」列表與首頁不一致，均不採用。

### 2. 定位成功與自動選中分離處理

`MainActivity` 在收到 `CurrentLocationResult.Success` 後儲存一份本 Activity 會話的最新快照，並以它重排當前路線資料。自動選中仍透過 `NearbyRouteSelectionPolicy` 與既有 `manualRouteSelectionGeneration` 判定：手動選路後，延遲位置結果不得覆蓋選中狀態或顯示 `附近`，但仍須重排路線。

低精度位置不經額外門檻即可排序；`NearbyRouteSelectionPolicy` 的 500 米及明顯領先規則只限制自動選中。定位未授權、服務關閉、失敗、超時或空結果時，不寫入快照、不重排，繼續用 repository 的使用統計基準順序。從管理頁返回時，`loadRouteConfigs()` 對最新資料復用本會話快照，不再發起定位。

替代方案：沿用「手動選擇即丟棄整個定位結果」會使列表不能反映已成功的位置；或把低精度位置完全丟棄會與已確認的排序行為不符，均不採用。

### 3. 以保存的首頁會話狀態去重使用計數

主頁維護「目前連續選中保存路線是否已記錄」的狀態，鍵值為路線 id。已保存路線的首次非刷新 `查詢` 成功進入查詢流程時才呼叫 `recordUsage()`；其後同一路線重複點擊 `查詢` 和下拉刷新均不呼叫。用戶切換到另一條已保存路線時清除前一條的資格，日後切回可再次首次計數；選中同一條卡片的無操作點擊與臨時查詢不改變資格。

此狀態及目前選中保存路線 id 透過 `savedInstanceState` 保存與還原，避免設定變更後重複計數。它不寫入 SQLite 或跨冷啟動保留，因此重新開啟 App 時首次查詢仍可正常計數。因現有下拉刷新已在 `RouteResultsRefreshPolicy` 層阻止計數，實作需把新資格判斷與該既有規則組合，而非改變刷新語義。

替代方案：以固定時間窗口去重會把「切換後立即切回」錯判為同一次使用；把最後計數 id 持久化到資料庫會使冷啟動後無法重新計數，均不採用。

### 4. 不改變可見資訊與外部邊界

常用路線卡片、完整列表及 TalkBack 描述維持只展示路線名稱和 `起點 -> 終點`；排序鍵不外露。定位仍使用既有前台權限與一次性 `CurrentLocationCoordinator`，不新增網絡請求、權限、服務或資料庫遷移。所有位置距離均在本機計算。

替代方案：在卡片加入距離／使用次數可解釋排序，但會改變既有緊湊 UI 和需求範圍，故不採用。

## Risks / Trade-offs

- [位置快照過時或定位失敗] → 只在本 Activity 最近一次成功快照存在時重排；無快照完整回退至既有基準排序，且不增加背景定位。
- [手動選擇與非同步定位競態] → 將列表重排與自動選中分離，generation 僅保護選中與 `附近` 標籤。
- [並列值造成列表跳動] → 使用整數米距離和明確的使用／時間／既有基準兜底，排序器以純單測覆蓋。
- [旋轉螢幕重複計數] → 保存選中路線與已計數資格；還原時只在 id 仍有效且一致時視為已計數。
- [管理頁變更資料] → 返回時先重新讀取資料庫，再套用會話快照；被刪除的選中或已計數 id 自然忽略。

## Migration Plan

無資料遷移。更新 App 後，既有路線與歷史使用統計保持不變；下一次成功定位才會採用距離優先排序。若定位不可用或此功能需回退，移除主頁位置排序和會話資格判斷即可恢復既有 repository 排序，無需資料修復。

## Open Questions

無；定位失敗降級、低精度排序、並列兜底、會話邊界及管理頁返回行為均已確認。
