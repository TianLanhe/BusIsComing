# 設定與路線詳情五項精修再對齊設計

## 背景

設定頁「關於我們」、分享應用，以及路線詳情地圖控件與下車 marker 已完成一輪候選實作。本輪不因代碼已存在便視為設計成立，而是以目前產品能力、主 OpenSpec、實際 UI 與使用者最新確認重新核對五項需求。最新結論優先於先前候選文案與逐控件實作方式。

本設計只處理：關於我們文案、分享文案與兩個官方下載入口、三個圓形地圖控件的圖標居中、全覽路線圖標，以及下車 marker 的不透明表面。手機朝向精度不在本輪範圍內。

## 已確認結論

- 對外產品定位寫「為香港巴士通勤而設」，涉及實際查詢能力時明確寫 `Citybus`，不得暗示已支援九巴等其他營運商。
- 「關於我們」採兩段精簡文案：第一段表達定位、Citybus 路線、實時到站時間與出發時機；第二段補充常用行程、地圖詳情及通知欄監察。
- 分享內容採「一句核心價值＋Google Play 下載＋官方網站下載」；不再列出車費、車程、步行、自動刷新等完整功能清單。
- Google Play 商品頁列在官方網站下載頁之前；網站 URL 依目前 App 語言指向對應語言的 `#download` 區段。
- 返回、目前位置、全覽路線保持 `48dp` 圓形控件和幾何居中的 `24dp` 圖標，並抽取共用樣式避免三處屬性再次漂移。
- 全覽路線繼續使用 Lucide `Route`：端點與相連路徑直接表達路線，不使用掃描框、二維碼、泛用地圖或單純展開語義。
- 下車 marker 使用目前乘車段色的不透明實心圓、白色外框及白色 `log-out` 圖形；保持圓形站點體系，不另改方形或菱形。

## 目標

- 讓設定頁文案準確反映目前 App，但仍適合在小屏幕閱讀及分享至聊天 App。
- 清楚區分「香港巴士通勤」的產品定位與「Citybus 路線」的目前能力邊界。
- 讓兩個官方下載入口都能在目前 App 語言下分享，且不重複維護 Play 商品 URL。
- 以同一可驗證的布局規則固定三個地圖控件的圖標中心、尺寸及觸控面積。
- 讓全覽路線與下車點在明暗底圖上均能被快速理解，而不改變現有地圖互動或資料流。

## 非目標

- 不新增其他巴士營運商、商店、短連結、動態分享卡或圖片分享。
- 不把「關於我們」變成完整功能列表、版本紀錄、授權清單或教學頁。
- 不改變全覽路線的相機 bounds、目前位置流程、返回導航或 TalkBack 行為。
- 不改用 `FloatingActionButton`／`ImageButton`，不重新設計按鈕陰影、漣漪、位置或間距。
- 不重新設計上車、轉乘、查詢起終點及普通站 marker。
- 不處理手機朝向、感測器融合或方向箭頭精度。

## 設計一：關於我們使用精簡兩段式文案

頁面保留目前 App 圖標、名稱、動態版本號及官方網站入口。說明文字由目前候選的一段密集功能清單改為兩個自然段落，不增加小標題、功能 bullet、按鈕或額外連結。

三語文案如下：

| 語言 | 第一段 | 第二段 |
| --- | --- | --- |
| 香港繁體 | BusIsComing 為香港巴士通勤而設，助你比較 Citybus 路線與實時到站時間，更好掌握出發時機。 | 你亦可儲存常用行程、查看地圖詳情及啟用通知欄監察。 |
| 簡體中文 | BusIsComing 为香港公交通勤而设计，帮助你比较 Citybus 路线和实时到站时间，更好地掌握出发时机。 | 你还可以保存常用行程、查看地图详情并启用通知栏监控。 |
| English | BusIsComing is built for Hong Kong bus commuters. Compare Citybus routes and live arrivals to choose a better time to leave. | You can also save regular journeys, view route details on the map, and monitor arrivals from your notifications. |

英文按自然語意獨立審校，不要求與中文保持相同句數。`Citybus` 保留官方英文名稱；「行程」繼續使用 `journey`，不得因歷史 model 名稱改成 saved route。

## 設計二：分享應用只保留核心價值與兩個下載入口

分享仍使用 Android 系統分享面板及 `text/plain`。正文不得加入裝置、版本、追蹤參數、短網址或未確認能力。三語完整模板如下，其中 `%1$s` 為 Google Play 商品 HTTPS URL，`%2$s` 為目前 App 語言對應的官方網站下載頁：

### 香港繁體

```text
用 BusIsComing 比較 Citybus 路線與實時到站時間，掌握更合適的出發時機。

Google Play 下載：%1$s
官方網站下載：%2$s
```

### 簡體中文

```text
使用 BusIsComing 比较 Citybus 路线和实时到站时间，掌握更合适的出发时机。

Google Play 下载：%1$s
官方网站下载：%2$s
```

### English

```text
Use BusIsComing to compare Citybus routes and live arrivals, so you can choose a better time to leave.

Download on Google Play: %1$s
Download from the official website: %2$s
```

Google Play 使用既有更新／評分能力共用的正式商品 URL：

```text
https://play.google.com/store/apps/details?id=com.golink.busiscoming
```

官方網站使用 `LanguageSnapshot.effectiveLanguage` 對應的下載頁，不使用裸首頁：

- 香港繁體：`https://www.busiscoming.com/zh-hant/#download`
- 簡體中文：`https://www.busiscoming.com/zh-hans/#download`
- English：`https://www.busiscoming.com/en/#download`

若系統沒有可處理分享的 Activity，沿用既有本地化失敗 Toast；不得因第二個 URL 新增網絡請求或預先探測。

## 設計三：三個圓形地圖控件共用居中樣式

返回、目前位置及全覽路線使用同一個專案樣式，例如 `Widget.BusIsComing.RouteDetailMapControl`，共同固定：

- `48dp × 48dp` 外框與 `24dp` 圓角；
- `android:gravity="center"`；
- 四向 inset 與 padding 均為 `0dp`；
- `24dp × 24dp` 圖標盒；
- `iconPadding="0dp"` 及適合無文字 MaterialButton 的 icon gravity；
- 相同 surface、圖標色、漣漪與其他不影響位置的基礎外觀。

三個 layout 節點只保留各自的 id、圖標、content description、layout margin、位置及必要的個別 elevation。不得以每個圖標各自增加不一致 padding 或 translation 補償視覺偏移。

居中驗收以外框幾何中心與 `24dp` drawable viewport 中心為準。三個控件在 LTR／RTL、三語、明暗模式及常用 density 下均須保持同一中心；返回箭頭本身不因 RTL 自動改變既有導航語義。

## 設計四：全覽路線使用 Lucide Route

全覽路線圖標保留 Lucide `Route` 的 `24 × 24`、`2px` 圓角線條語言：兩個端點由一條可見彎曲路徑連接。它與現有 Lucide 圖標授權及路線詳情的 outline 視覺一致。

已排除候選：

- 掃描框／QR：代表掃碼或取景，與相機重置至完整路線無關；
- `Waypoints`：偏向多點規劃、分支或編輯；
- `Map`：只說明頁面有地圖，未表達操作對象是目前路線；
- `Maximize`：偏向全屏或展開，缺少路線語義。

圖標檔及 Lucide 授權記錄已符合本結論時，不為了產生額外 diff 重新描畫或更換來源。content description 繼續描述操作「全覽路線」，而非朗讀圖標名稱。

## 設計五：下車 marker 採不透明路線色實心圓

下車 marker 的外圍角色表面使用目前乘車段色且 alpha 為 `1.0`，再疊加不透明白色外框及等比白色 `log-out` 圖形。marker 中央不得透出 Google 底圖、道路、文字或巴士線。

上車與下車保持相同的圓形、路線色及白色外框體系，分別以 bus 與 `log-out` 圖形區分角色。這種一致性優先於改用另一幾何形狀；TalkBack 仍以結構化角色與站名描述上下車，不依賴顏色或圖形辨識。

多段路線的下車點取所屬乘車段色；同站轉乘仍沿用既有單一轉乘 marker，不疊放下車及下一段上車 marker。此次只改 marker surface，不改 stable id、z-index、anchor、選取、標籤碰撞或相機 bounds。

## 目前候選實作核對

| 項目 | 目前候選 | 依本設計是否需調整 |
| --- | --- | --- |
| 關於我們 | 一段式完整功能清單 | 需要，替換為已確認的兩段三語文案 |
| 分享連結 | Play 商品頁＋本地化網站下載頁 | 不需要，連結來源與順序正確 |
| 分享文案 | 羅列路線、ETA、車費、車程、步行、地圖、自動刷新及監察 | 需要，縮短為一句核心價值＋兩個入口 |
| 圓形控件 | 三個節點各自重複零 padding、居中與 24dp 屬性 | 需要，抽取共用樣式但不改視覺 |
| 全覽路線圖標 | Lucide `Route` | 不需要，已符合最終選擇 |
| 下車 marker | 路線色實心圓＋白色外框／圖形 | 原理不需改；補足不透明 alpha 與視覺驗收即可 |

## OpenSpec 與文件邊界

本輪沒有 active OpenSpec change。後續實施計劃須把目前未提交的主 spec 候選與本設計再對齊：

- `app-settings-support` 的分享 requirement 應要求自然、精簡的目前語言文案、Play 商品頁及本地化網站下載頁；不得把地圖、自動刷新、通知監察等完整列舉固化成每次分享都必須包含的合同。
- `route-detail-google-map` 保留 `48dp` 圓形控件、幾何居中的 `24dp` 圖標、Route 語義及不透明下車 marker 合同。

README 不需因這次短文案調整而重寫產品功能列表；Lucide 授權記錄只在來源或實際使用圖標集合改變時更新。

## 測試與驗收

### 文案與連結

- JVM／resource contract 檢查三語 about 兩段內容、分享模板的兩個格式參數及 Play／網站順序。
- 逐語言確認網站 URL 分別落到 `zh-hant`、`zh-hans`、`en` 的 `#download`。
- 啟動系統分享面板，確認文本中的兩個 HTTPS URL 完整、可點擊，換行未被字面轉義。
- 在 360dp、font scale `1.0／1.3／2.0` 下檢查關於頁不裁切、不遮擋網站入口，長內容可正常垂直捲動。

### 地圖控件

- layout／style 測試確認三個控件引用同一樣式且 drawable box 為 `24dp`。
- 實際 inflate 後量測圖標 bounds；水平及垂直中心相對 `48dp` 外框的偏差不得超過一個實體像素。
- 三語、明暗、LTR／RTL 與至少 360dp 畫面檢查三個控件位置、content description、觸控面積、漣漪及按下狀態沒有回歸。
- 全覽路線仍重置至可靠完整路線 bounds，不把遠離路線的裝置位置強制納入，也不改變使用者手勢後的相機所有權規則。

### Marker 與圖標

- drawable contract 固定 Lucide Route 的端點及相連路徑，不得回退掃描框。
- marker icon factory 測試確認下車分支先繪製 fill，再繪製 outline 與白色 glyph，且三者輸出 alpha 均為 `255`。
- 在淺色、深色及文字密集的 Google 底圖上並排檢查上車、下車、轉乘；下車中心不得透出底圖，bus／log-out 圖形仍能快速區分。
- 單段及多段路線都要檢查所屬乘車段色，同站轉乘不新增重疊 marker。

最後運行受影響單元測試、OpenSpec strict validation 及 `./gradlew build`。視覺驗收只使用本任務新啟動且符合 API、360dp、Google Play、語言與主題畫像的 AVD，完成後關閉該 AVD。

## 風險與緩解

- **精簡文案遺漏次要能力**：關於頁與分享不是功能列表；完整能力仍由主畫面、README 及商店頁承擔。
- **「香港巴士」被理解為全營運商支援**：產品定位與能力句分開，查詢能力明確寫 `Citybus`。
- **共用樣式改變現有外觀**：只抽取已驗證的共同屬性，個別位置與 elevation 保留在 layout，使用實際 inflate 和截圖比較。
- **實心上下車 marker 太相似**：保持不同且高對比的 bus／log-out 圖形，並由時間線、站名及 TalkBack 提供冗餘角色資訊。
- **Route 圖標被理解為路線規劃**：content description 明確為「全覽路線」，操作只執行既有相機 fit，不進入編輯或導航。

## 完成條件

- 三語「關於我們」與分享文本等同本設計，兩個下載 URL 與語言映射正確。
- 三個地圖控件共用同一樣式，48dp 外框內的 24dp 圖標通過量測及人工居中驗收。
- 全覽路線保留 Lucide Route 及授權記錄，沒有掃描／QR 語義。
- 下車 marker 在所有已驗收底圖上為完全不透明的路線色實心圓，白色外框與白色圖形清晰。
- 受影響測試、OpenSpec strict validation 及 `./gradlew build` 成功；未執行的真實裝置或分享目標驗證被如實記錄。
