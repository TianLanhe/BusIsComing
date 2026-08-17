## 1. 資料契約與純邏輯基礎

- [x] 1.1 先為營運商、香港資料日、靜態 route／route-stop／stop、snapshot identity、語義 fingerprint、匹配結果及跨營運商 ETA 狀態建立失敗測試，再加入結構化 model 與純函式，使 CTB／KMB／LWB、`05:15` 分界及穩定 identity 可被單元驗證。
- [x] 1.2 先加入 GTFS `routes.txt`、KMB route／route-stop／stop／ETA 及 CTB route／route-stop／stop 的真實裁剪 fixture 與 parser 回歸，再實作必要欄位、`co`、bound、service type、sequence、WGS84 座標、引用完整性及未知／無效記錄處理。
- [x] 1.3 實作只包含 DP 語義欄位的穩定 fingerprint，加入 metadata、原始順序不變／語義欄位改變及 KMB／LWB operator 差異測試。
- [x] 1.4 實作 Haversine、`G=100m` 雙邊 DP、回溯站對、正規化 cost、全候選穩定排序及 `T=46` 門禁；以 118、S1／R8、環線、gap、同 cost 及門禁邊界 golden tests 證明不使用站名、預篩選、站對門禁、第二名差值或置信度。

## 2. 獨立靜態資料庫與原子快照

- [x] 2.1 建立獨立可重建 SQLite schema，保存 active／staging snapshot、五來源 metadata、GTFS 聯營 route、KMB／LWB route-stop-stop、CTB route slice、fingerprint、`MATCHED`／`NO_MATCH` 與站點對應；加入建庫、版本不兼容及重建測試而不修改既有使用者資料庫 schema。
- [x] 2.2 實作 staging 寫入、發布前整體校驗、單交易 active pointer 切換、查詢 snapshot capture 與舊 snapshot 延後清理，加入部分來源失敗、提交中斷、並發 reader 及損壞回退 Citybus-only 測試。
- [x] 2.3 在 Android backup／device-transfer 規則中只排除新增可重建資料庫，加入資源／配置測試確認不順帶改變既有使用者資料備份契約。
- [x] 2.4 實作 route-level cache 精準失效、算法／`G/T` 全量失效與 DP 寫回前 snapshot／slice 二次核對，加入舊計算丟棄、最多重算一次及暫時性失敗不寫 `NO_MATCH` 的競態測試。

## 3. 每日全局更新與設定入口

- [x] 3.1 建立集中網絡 source／repository，完成五個全局 GET、條件請求或有效 `304`、timeout、有限退避、流式／有界解析及可復現請求測試，確保 HTTP 與解析不進入 UI。
- [x] 3.2 實作 App 級前台生命週期協調器與全局 single-flight：無快照立即檢查、每香港資料日首次前台檢查、查詢 stale-while-revalidate、App 未啟動不排程，並以 clock／並發測試覆蓋自動與手動競態。
- [x] 3.3 在設定頁 `路線資料` 分組加入「路線資料庫更新檢查」，展示未完成、最近完整成功香港時間、檢查中、已是最新、已更新及失敗保留舊資料狀態，並讓手動操作繞過資料日門禁但附著到既有 single-flight。
- [x] 3.4 補齊設定列香港繁體、獨立簡體、自然英文、日期時間及無障礙資源，加入 Fragment 銷毀／重建、過期 callback、重複點擊與手動檢查不觸發 CTB `2 + N` 或 DP 的測試。

## 4. CTB 路線懶載入、聯營門禁與映射 cache

- [x] 4.1 實作只接受 GTFS `KMB+CTB`／`LWB+CTB` 的聯營 route gate，加入非聯營、缺失快照、KMB 聯營及 LWB 聯營測試，確認未命中時不發 CTB route-stop 懶請求。
- [x] 4.2 實作每 route 的 CTB 雙方向 route-stop 加 N 個去重 stop 懶載入、route／stop single-flight、同資料日復用及完整 slice 原子發布；加入首次 `2 + N`、跨方向重複 stop、部分失敗與舊 slice 保留測試。
- [x] 4.3 實作新資料日 CTB route slice stale-while-revalidate，只有完整成功且語義 fingerprint 改變時失效並重算該 route，加入查詢不等待驗證及非語義更新不重算測試。
- [x] 4.4 將全候選 DP 與持久化 cache 接入 lazy route 流程，保存 winner co／bound／service type、raw／normalized cost、版本與對角站點 mapping；以 118 KMB 及 S1／R8 LWB fixture 驗證可復用 winner。
- [x] 4.5 以運行時 Citybus P2P stop map 的 boarding／alighting CTB stop ID 查找同一 winner 映射，加入兩端完整且 `boardingSeq < alightingSeq`、特別班次缺站、gap、不唯一及反向順序測試，禁止最近站／同名站／另一變體猜測。

## 5. KMB／LWB ETA 與共享聚合接口

- [x] 5.1 先以 fixture 測試 KMB Data API ETA parser／source，再實作由 winner stop 建立 URL 及對 response 的 route、dir、service type、`co` 嚴格過濾，確認 `/kmb/` endpoint 中 `co=LWB` 保存為 LWB 且未知 co 被忽略。
- [x] 5.2 擴展 ETA model 保存 operator、來源 sequence、目的地、備註與來源 timestamp；實作 CTB＋KMB／LWB 全量穩定合併、完全同時不跨營運商去重、合併後重編號及移除聚合層前三班截斷的單元測試。
- [x] 5.3 實作 `Available`、`NoArrivals`、`Unavailable` 及未啟用原因的部分失敗矩陣，加入單方有班次、雙方空、一方空另一方失敗、首次映射尚未完成與晚到結果測試。
- [x] 5.4 建立 App 級共享跨營運商首程 ETA 接口／factory，替換路線查詢、全屏詳情、前台自動刷新及 `BusMonitorService` 各自直接建立 Citybus-only service 的生產接線，並保持可注入 fake／clock／executor。
- [x] 5.5 加入 consumer 一致性與生命週期測試：同一首程使用相同合併排序，卡片／詳情漸進更新，監控使用合併後第一／第二班，route result、generation、語言或 snapshot 過期結果不覆蓋目前狀態。
- [x] 5.6 驗證 Citybus-only、GTFS 未命中、映射失敗及靜態 DB 故障時的既有路線查詢、詳情、自動刷新與通知監控回歸，確認路線卡、通知與 TTS 不新增營運商文案。

## 6. ETA 詳情完整列表與品牌 UI

- [x] 6.1 先更新 Bottom Sheet presenter／view-model 測試，再移除 `take(3)`，使用固定標題／方向／保守最舊來源更新時間及可滾動完整班次列表；一次 render 同步班序、operator、分鐘、時刻與備註。
- [x] 6.2 為 CTB、KMB、LWB 加入結構化文字膠囊及三語標籤，分別使用 `#ECCF00/#004891`、`#E60012/#FFFFFF`、`#F15622/#17211F`，並加入顏色 token、文字存在、未知 operator 不猜測及對比度回歸測試。
- [x] 6.3 補齊每列完整無障礙描述與香港繁體／簡體／英文 ETA 詳情資源，驗證路線卡尺寸與卡片數量完全不變且只在 ETA Bottom Sheet 顯示營運商。
- [x] 6.4 加入 Bottom Sheet instrumentation／截圖式布局驗證，覆蓋 CTB＋KMB、CTB＋LWB、超過三班、長方向／備註、淺色／深色、360dp 及 font scale 1.0／1.3／2.0，確認標題可見、列表可滾動、核心文字不裁切。

## 7. 真實 App 實證與可重放證據

- [x] 7.1 加入以 instrumentation argument 明確 opt-in 的真實聯營 ETA witness runner，使用生產 HTTP、parser、SQLite、DP 與 UI，以受限並發、候選輪轉及退避動態搜尋 CTB＋KMB 或 CTB＋LWB 同時有班次的站點。
- [x] 7.2 實作獨立小型 oracle 與證據輸出，從同一批原始響應核對 UI 全部行數、operator、排序及絕對時間，保存路線／方向、兩方 stop ID、co／service type、DP cost、版本、請求時間、去敏 URL、原始 JSON、UI hierarchy 及截圖。
- [x] 7.3 將成功 live witness 轉為帶來源時間、hash 與固定 clock 的去敏 fixture，加入日常 unit／instrumentation 重放；沒有雙方班次時只報 skipped／inconclusive，不把 fixture 或單方 ETA 誤報為 live 成功。
- [x] 7.4 在本任務自行啟動且符合畫像的模擬器上執行真實 118 P2P 冒煙與動態雙營運商 witness；記錄實際證據或誠實標記尚未取得 live 見證，完成後關閉本任務啟動的模擬器。

## 8. 文件、總驗證與提交

- [x] 8.1 新增 `docs/cross-operator-route-stop-matching.md`，記錄官方來源、資料 schema、GTFS gate、DP 公式與 `G=100/T=46` 校準、cache／失效、P2P runtime gate、118／S1／R8 例子、已知限制、實證方法及未來多營運商擴展。
- [x] 8.2 同步 `docs/architecture.md`、`docs/citybus-route-query-and-eta.md`、`docs/localization-guidelines.md` 與 README 文件導航，明確區分上游回應、App 映射、ETA 聚合及 UI 展示。
- [x] 8.3 在 `docs/technical-debt.md` 登記把官方資料更新、DP／cache 及結果分發遷移服務端的技術債，包含目前代價、延期邊界、建議方案與 API 版本、SLA、離線回退、灰度回滾、合規、監控及 golden corpus 一致性的關閉條件。
- [x] 8.4 運行所有新增定向 unit／instrumentation 測試、既有受影響回歸、三語／主題／無障礙驗證、`./gradlew build`、OpenSpec strict validation 及 `git diff --check`，逐項記錄 live witness 與任何未執行驗證的原因和風險。
- [x] 8.5 核對 `tasks.md` 勾選、工作區與提交範圍，保留無關使用者改動，依 AGENTS.md 以簡潔英文 conventional commit 提交完成的 apply 實作。

## 9. 真實 KMB ETA 響應契約修復

- [x] 9.1 以不含 `stop` 的真實 KMB ETA 響應結構建立回歸測試，先確認舊解析器錯誤返回空班次，再以 URL 綁定站點身份的最小修復令測試通過。
- [x] 9.2 修正 OpenSpec、長期文件及真實 witness oracle，保留 `co + route + dir + service_type` 嚴格過濾且不再假設 response 含有 `stop`。
- [x] 9.3 完成定向測試、全量 build、OpenSpec strict validation，並在本任務自有模擬器以真實 HTTP／SQLite／DP／UI 取得雙營運商見證及截圖；沒有當前雙方班次時如實標記 inconclusive。
