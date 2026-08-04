<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="96" alt="BusIsComing app icon">

# BusIsComing

一個為香港巴士通勤而設的 Android App：保存常用行程、比較 Citybus 點到點路線，並在出門前監控首程巴士 ETA。

[官方網站](https://www.busiscoming.com/)

</div>

## 主要功能

- **常用行程**：新增、編輯、複製、刪除常用起終點，按目前位置、使用次數及最近使用情況排列。
- **臨時搜尋**：不保存行程也可選擇起終點、查詢路線，查詢成功後可另存為常用行程。
- **即時路線結果**：比較路線、HK$ 車費、總耗時、步行距離、上下車站預覽及首程 ETA；支援五種排序與下拉刷新。
- **結果置頂**：在本次查詢置頂路線，或為常用行程保存長期置頂；一般排序只作用於未置頂結果。
- **ETA 與詳情**：查看最多三班到站時間、上下車站、方向、途經站及換乘段。
- **通知欄監控**：短時前台服務定期刷新 ETA，顯示準備出門、立即出門或可能遲到，並提供刷新、停止及語音播報。
- **行程匯入匯出**：透過 Android 系統文件選擇器匯出 `.bicroutes`，或預覽後合併／取代本機行程。
- **乘車碼快捷方式**：由桌面快捷方式開啟 AlipayHK／支付寶乘車碼候選鏈，不在常用頁佔用固定入口。
- **更新檢查**：正常版本採 Google Play 優先分流，必要時使用官方網站 metadata；支援自動／手動檢查、稍後提醒、略過版本及 Play flexible update。
- **三語與主題**：支援跟隨系統、香港繁體、簡體、English，以及跟隨系統、淺色、深色外觀。

## 快速開始

1. 使用支援 Android Gradle Plugin 9.x 的 Android Studio 打開專案根目錄。
2. 如需把目前座標解析為 Google 地址，在不提交到版本控制的 `local.properties` 設定：

   ```properties
   GOOGLE_GEOCODING_API_KEY=your_api_key
   ```

   也可使用同名環境變數。API key 應限制 Android package、簽名憑證及所需 API。

3. 構建及安裝：

   ```bash
   ./gradlew build
   ./gradlew :app:installDebug
   ```

> [!IMPORTANT]
> 生產路徑使用真實 Citybus、DATA.GOV.HK 與已配置的 Google 服務。Fixture 只用於 parser 回歸，不能替代生產 HTTP、真實三語或 Play 資格驗證。

## 技術概覽

| 類別 | 技術 |
| --- | --- |
| 語言與 UI | Kotlin、XML、AppCompat、Material Components |
| 本地化 | Android resources、AppCompat per-app locales |
| 清單與刷新 | RecyclerView、SwipeRefreshLayout |
| 本機資料 | SQLiteOpenHelper、SharedPreferences、SavedState |
| 網絡與解析 | HTTPS、jsoup、輕量 JSON 解析 |
| 定位 | Google Play services Location、Google Geocoding v4 |
| 更新 | Google Play In-App Updates、官方網站 metadata |
| 背景能力 | Foreground Service、NotificationCompat、AlarmManager、TextToSpeech |
| 測試 | JUnit、AndroidX Test、Espresso、Citybus fixture、真實服務驗證 |
| 構建 | Gradle Kotlin DSL、Android Gradle Plugin 9.2.1 |

目前 `compileSdk` 為 36.1、`minSdk` 為 25、`targetSdk` 為 36，Java target 為 11；易變版本的權威來源始終是 Gradle 配置。

## 架構摘要

BusIsComing 採用輕量 Repository 分層。常用頁與搜尋頁是獨立 query owner；基礎路線結果先展示，站點預覽與 ETA 在背景漸進補齊。UI 負責展示、輸入與生命週期協調，網絡、SQLite、解析和查詢編排留在 data 層，前台監控、通知、調度及 TTS 留在 service。

| 目錄 | 職責 |
| --- | --- |
| `data/local` | SQLite schema、行程／置頂資料與本機偏好 |
| `data/localization` | 實際 locale、provider mapping、TTS 語言與版本 snapshot |
| `data/location` | 目前位置、距離、附近行程決策與 Google 地址解析 |
| `data/model` | 行程、路線、地點、ETA、置頂、更新及監控狀態 |
| `data/repository` | Citybus／ETA 查詢、parser、詳情、cache 與本機 repository |
| `data/transfer` | `.bicroutes` codec、讀取、預覽及匯入計劃 |
| `data/update` | Play／網站渠道、更新策略、狀態存儲與外部操作 |
| `service` | 前台監控、通知、排程、session、TTS 與 formatter |
| `ui/common` | 共用地點輸入、結果控制、WindowInsets 及短文案工具 |
| `ui/main` | 三個頂層 destination、查詢結果、詳情、置頂及快捷入口 |
| `ui/edit`、`ui/manage` | 行程新增／編輯及管理 |
| `ui/navigation`、`ui/settings` | 頂層導航狀態及次級設定頁 |

完整邊界、資料流、持久化與生命週期見 [架構](docs/architecture.md)。

## 外部資料

| 來源 | 用途 |
| --- | --- |
| Citybus `bsearch_p3.php` | 地點候選 |
| Citybus `ppsearch_p3.php` | 點到點候選路線 |
| Citybus `showstops2.php` | P2P route variant、站序及 stop id 對齊 |
| Citybus `getp2pstopinroute.php` | 上下車、途經站及換乘詳情 |
| DATA.GOV.HK Citybus ETA | 首程即時到站資料 |
| Google Geocoding v4 | 目前座標的地址名稱 |
| Google Play／官方網站 | 更新資格、版本展示及下載入口 |

接口參數、語言和失敗邊界集中記錄在 [Citybus 路線查詢與 ETA](docs/citybus-route-query-and-eta.md)、[本地化指南](docs/localization-guidelines.md)及[應用程式更新檢查](docs/app-update-check.md)。

## 本機資料與私隱

常用行程與長期路線置頂保存在 SQLite；語言、外觀、更新和監控 session 等狀態使用各自的偏好存儲；畫面重建需要的臨時查詢與本次置頂使用 SavedState 或頁面狀態。Android 自動備份目前仍缺少明確 include／exclude 策略，已記入[技術債](docs/technical-debt.md)。

`.bicroutes` 是未加密、版本化的 UTF-8 JSON，只包含行程名稱、起終點地點名稱與精確座標；不包含使用次數、最近使用時間、查詢結果、ETA、置頂或監控 session。

> [!WARNING]
> 匯出文件包含精確座標，請只交給信任的人。匯入匯出使用系統文件選擇器，不要求外部儲存權限。

## 驗證

```bash
./gradlew testDebugUnitTest
./gradlew build
./gradlew connectedDebugAndroidTest   # 需要本任務自行啟動的合適裝置
openspec validate --all --strict
```

UI 驗收覆蓋三語、淺／深色、約 360dp portrait、font scale 1.0／1.3／2.0、TalkBack、Dialog、Bottom Sheet 與通知。詳細條件見[三語與外觀驗收矩陣](docs/localization-validation-matrix.md)。

## 文件導航

| 文件 | 內容 |
| --- | --- |
| [架構](docs/architecture.md) | 畫面、模組、資料流、狀態與生命週期 |
| [行程與查詢工作流](docs/journey-query-workflow.md) | 行程、地點、搜尋、結果、置頂與資料遷移 |
| [Citybus 路線查詢與 ETA](docs/citybus-route-query-and-eta.md) | 外部接口、P2P 解析、站點對齊、詳情與 ETA |
| [通知欄監控設計](docs/monitoring-design.md) | 步行估算、狀態、排程、通知、TTS 與 session |
| [UI／UX 風格指南](docs/ui-style-guide.md) | 體驗定位、設計原則、視覺語言、互動模式與無障礙 |
| [本地化指南](docs/localization-guidelines.md) | 三語、術語、動態資料與 TTS 語言 |
| [三語與外觀驗收矩陣](docs/localization-validation-matrix.md) | 可重複執行的 UI 驗收條件 |
| [應用程式更新檢查](docs/app-update-check.md) | 更新流程、渠道、網站契約與發佈鏈 |
| [乘車碼快捷方式](docs/transit-code-launcher.md) | 桌面入口、支付應用候選鏈及兼容行為 |
| [技術債](docs/technical-debt.md) | 已確認並主動延期的問題及關閉條件 |
| [文件治理](docs/documentation-governance.md) | 文件職責及 OpenSpec 歸檔後同步契約 |

功能的可觀察 requirements 位於 `openspec/specs/`；進行中與歷史變更位於 `openspec/changes/`。專案文件、OpenSpec 人類可讀內容及中文註解使用繁體；App runtime 自有文案必須同時提供自然繁體、獨立簡體與英文。
