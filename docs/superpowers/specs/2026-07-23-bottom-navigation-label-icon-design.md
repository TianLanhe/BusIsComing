# 底部導航行程與路線標籤／圖標設計

日期：2026-07-23
狀態：已確認設計，尚未建立 OpenSpec change 或實作

## 背景

BusIsComing 目前的三個頂層 destination 為「常用／搜尋／設定」。第一項實際展示並查詢用戶已儲存的常用起終點行程，第二項讓用戶輸入一次性起終點並取得巴士路線方案。現有「常用」只描述使用頻率，沒有說明內容對象；「搜尋」則只描述一個通用動作，配套放大鏡亦無法表達點到點巴士路線查詢。

本設計只改善前兩個底部導航項的可辨識度，要求用戶能透過兩字標籤與圖標迅速預期頁面內容，不改變既有頁面流程、導航結構或資料模型。

## 設計原則

- 中文可見標籤固定為兩個字。
- 頂層導航以 destination 內容命名，不以頁內單次按鈕動作命名。
- 沿用專案既有術語：「行程」是用戶儲存的起終點配置；「路線」是查詢返回的乘車方案。
- 圖標補充標籤未能完整表達的資訊，但不依賴圖標單獨傳意。
- 沿用 Android Material 視覺語言、現有膠囊 active indicator、等寬三項佈局與深淺主題語意色。
- 圖標資源須隨 App 打包，不在 runtime 載入網路字型或遠端素材。

## 已確認方案

### 可見標籤與圖標

| Destination | 香港繁體 | 簡體中文 | English | Material Symbols |
|---|---|---|---|---|
| 已儲存行程 | `行程` | `行程` | `Journeys` | `bookmarks` |
| 一次性路線查詢 | `路線` | `路线` | `Routes` | `route` |
| 設定 | `設定` | `设置` | `Settings` | 沿用既有 `settings` |

底部導航的最終結構為：

```text
香港繁體： [ bookmarks 行程 ] [ route 路線 ] [ settings 設定 ]
簡體中文： [ bookmarks 行程 ] [ route 路线 ] [ settings 设置 ]
English：  [ bookmarks Journeys ] [ route Routes ] [ settings Settings ]
```

### 圖標語意

- `bookmarks` 使用複數書籤輪廓，表示頁面包含多條已儲存行程；相比單一 `bookmark`，它不會暗示只固定一個目的地或一條路線。
- `route` 以起點、路徑與終點構成，直接表達輸入起終點後取得乘車方案；相比通用放大鏡，它能排除文字搜尋、路線號搜尋或站點搜尋等歧義。
- `settings` 保持既有齒輪圖標，避免本次窄範圍改動引入無關視覺變化。

### 選中與未選中狀態

- 未選中使用 Material Symbols outline 版本。
- 選中使用同一 Material Symbol 官方 fill 版本，並繼續顯示既有膠囊 active indicator、選中色與粗體標籤。
- 不自製另一套選中圖形；outline／fill 均從同一官方 symbol 匯出為本地 Android Vector Drawable。
- 保持現有底部導航圖標槽位、量度、文字基線與觸控範圍；本設計不調整導航欄幾何。

## 無障礙文案

兩字限制只適用於可見標籤。TalkBack 的 `contentDescription` 使用完整語意，避免只朗讀「行程」或「路線」時缺少 destination 目的：

| Destination | 香港繁體 | 簡體中文 | English |
|---|---|---|---|
| 已儲存行程 | `已儲存行程` | `已保存行程` | `Saved journeys` |
| 一次性路線查詢 | `搜尋巴士路線` | `搜索公交路线` | `Find bus routes` |
| 設定 | `設定` | `设置` | `Settings` |

前兩個 destination 使用獨立的三語 `contentDescription` string resource；「設定」繼續使用既有 `@string/settings`。所有可見標籤與無障礙文案均透過資源提供，不在 XML 或 Kotlin 硬編碼。

## 候選方案比較

### 第一個 Tab

| 方案 | 圖標 | 優點 | 未採用原因 |
|---|---|---|---|
| `行程` | `bookmarks` | 精確描述已儲存起終點配置，複數圖形表示集合 | 已採用 |
| `常用` | `bookmark` | 改動最小，現有用戶熟悉 | 未說明常用內容的對象 |
| `收藏` | `star` | 跨 App 心智模型熟悉 | 容易被理解為收藏某條巴士路線、車站或地點 |
| `通勤` | `commute` | 交通 App 氣質明顯 | 排除休閒、週末與非工作／上學行程 |
| `快捷` | `bolt` | 突出快速查詢價值 | 像快捷功能集合，不像行程內容頁 |

### 第二個 Tab

| 方案 | 圖標 | 優點 | 未採用原因 |
|---|---|---|---|
| `路線` | `route` | 與「行程」同為名詞，圖形直接表達點到點方案 | 已採用 |
| `查找` | `map_search` | 動作直給，第一次使用容易猜 | 沒有說明查找地點、站點還是路線 |
| `規劃` | `alt_route` | 能表達多方案比較 | 語氣較重，容易令人預期日期與偏好等完整規劃能力 |
| `搜尋` | `search` | 最熟悉、遷移成本最低 | 標籤與放大鏡均過於通用 |
| `出發` | `assistant_direction` | 親切且有行動感 | 暗示立即出門，不涵蓋先查詢或稍後儲存的場景 |

## 三語與版面要求

- 香港繁體、簡體中文與英文採用獨立審校文案，不以繁體機械轉換簡體。
- 英文 `Journeys / Routes / Settings` 保持三項均為複數名詞，與中文 destination 命名方式一致。
- 在 360dp、font scale 1.0／1.3／2.0 下不得裁切、重疊或令 active indicator 改變量度。
- 語言、主題或 Activity 重建後，選中項、圖標狀態及 `contentDescription` 必須與目前 destination 一致。

## 實作範圍

### 包含

- 更新三語底部導航可見標籤與獨立無障礙文案。
- 以本地 Android Vector Drawable 提供 `bookmarks` 與 `route` 的 outline／fill 狀態。
- 更新 menu item 對應的 icon selector，但保留既有 item id 與 destination mapping。
- 更新或補充底部導航的資源、instrumentation、無障礙與視覺契約測試。

### 不包含

- 不改名 `TopLevelDestination.FREQUENT_ROUTES`、menu item id、Fragment、SQLite `route_configs` 或其他兼容保留的內部符號。
- 不改常用頁、搜尋頁的輸入、查詢、保存、排序、刷新或返回行為。
- 不因底欄改名而連帶改動頁面標題、按鈕、Toast、通知或其他既有 runtime 文案；若後續發現術語不一致，另行評估。
- 不建立新的導航 destination，不改第三個「設定」Tab 的語意或行為。

## 驗證

- 資源測試：三語 visible label 與 `contentDescription` 均存在且 mapping 正確。
- 導航 instrumentation：三個 destination 可正常切換，重建後恢復選中項，既有頁面狀態保持不變。
- 狀態驗證：`bookmarks`、`route`、`settings` 在選中與未選中時使用正確資源、顏色、文字字重及 active indicator。
- 無障礙：TalkBack 朗讀完整 destination 語意，不重複朗讀圖標與標籤。
- 視覺：繁體／簡體／英文 × 淺色／深色 × 360dp × font scale 1.0／1.3／2.0。
- 回歸：底部導航量度、文字安全空間及首次使用狀態不退化。
- 最終運行 `./gradlew build`；如有可用模擬器，再進行三語與深淺主題人工視覺確認。

## 研究依據

- Android 導航模式：<https://developer.android.com/design/ui/mobile/guides/layout-and-content/layout-and-nav-patterns>
- Material Symbols 與 Android Vector Drawable：<https://developers.google.com/fonts/docs/material_symbols>
- Apple Tab Bar／Tab View 命名原則：<https://developer.apple.com/design/human-interface-guidelines/tab-bars>、<https://developer.apple.com/design/human-interface-guidelines/tab-views>
- 城巴 Bookmark 與 Point-to-Point Search：<https://www.citybus.com.hk/en/uploadedfiles/app_guide/en.html>
- 九巴收藏與搜尋功能：<https://kmb.hk/storage/app1933.html>
- Transit 收藏地點與行程規劃：<https://help.transitapp.com/article/95-save-your-favorite-locations>
- 對照圖標庫：Material Symbols、Lucide、Phosphor Icons、Tabler Icons、Font Awesome。

## 設計預覽

本次討論的 HTML 預覽位於：

`/.superpowers/brainstorm/9262-1784746306/content/final-navigation-design.html`

`.superpowers` 為本機忽略目錄，預覽不加入 git。
