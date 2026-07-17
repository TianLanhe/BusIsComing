# 三語、外觀與無障礙視覺驗收記錄

## 驗收環境

- 日期：2026-07-17
- API 36.1：Pixel 9 模擬器，1080 × 2400、density 480，等效約 360dp portrait。
- API 37：Pixel 8 模擬器，1080 × 2400、density 480，等效約 360dp portrait。
- Android 7.1／API 25：`BusIsComing_API_25` 模擬器；完整 connected suite 為 41 tests、0 failed、0 ignored。
- 語言：繁體中文、簡體中文、English。
- 外觀：固定淺色、固定深色；instrumentation 另覆蓋跟隨系統與固定模式互相覆寫的矩陣。
- 字體：1.0、1.3、2.0。

## 截圖索引

檔名使用 `{語言}-{外觀}-{字體}-{畫面}.png`，其中 `tc`、`sc`、`en` 分別表示繁體、簡體及英文；`home`、`search`、`settings`、`edit` 分別表示常用、搜尋、設定及編輯畫面。`api37-` 前綴是 API 37 複核，其餘是 API 36.1。

本目錄保留以下矩陣證據：

- API 36.1：三語 × 淺／深色 × font scale 1.0 的頂層畫面；三語深色的 font scale 1.3／2.0 高風險畫面；英文 2.0 編輯頁。
- API 37：繁體淺色 1.0 的常用／設定，以及英文深色 2.0 的常用／搜尋／設定。
- `*-fixed.png`：視覺檢查發現問題後，以最新 APK 重拍的回歸證據；沒有 `fixed` 後綴的同名舊圖只保留作前後比較，不代表最終 UI。

## 發現及修正

1. 英文 2.0 字號下，路線卡的站點預覽原本最多兩行，終點可能不完整。已改為最多三行並移除核心站點文字的省略；`en-dark-font2-home-fixed.png` 顯示起點與終點均完整。
2. 起點／終點輸入欄原本把操作說明放在長 hint，英文 2.0 字號會省略。已把 hint 縮短為欄位名稱，將選擇候選的要求移到可換行 helper text；`en-dark-font2-search-fixed.png` 及 `api37-en-dark-font2-search-fixed.png` 均完整顯示。
3. 路線名稱的長範例 hint 在英文 2.0 字號會省略。已改為短欄位名稱並將例子移到可換行 helper text；畫面保持可捲動，Save action 在 2.0 字號下仍可到達。
4. API 37 初次截圖使用了較舊安裝包，未顯示新增 helper text。重新 assemble、覆蓋安裝並以 UI hierarchy 驗證兩個 helper 均存在後，保存 `api37-en-dark-font2-search-fixed.png` 作最終證據。

## 等效 accessibility 驗收

本輪使用 Espresso、UI Automator hierarchy 與 layout contract 作 TalkBack 等效檢查：

- 三個頂層 destination、設定列、輸入、排序、保存與通知 action 均可聚焦及點擊，核心 action 最小觸控區為 48dp。
- 地點候選提供完整三語 `contentDescription`，距離狀態不依靠視覺分隔符朗讀。
- 路線詳情、ETA、監控與保存 Dialog／Bottom Sheet 可展示並捲動，詳情長內容可捲到末端再返回頂端。
- compact 內容只允許受控省略；核心站點方向、錯誤原因、狀態及操作不得以省略代替完整語義。
- 系統檔案選擇器與第三方頁面屬系統／第三方邊界；App 內的返回、取消及 Intent 失敗提示保持可操作。

TalkBack package 在近期模擬器上可用，但未在本輪開啟服務；上述自動化與 hierarchy 是本 change 所採用的等效 accessibility 證據。

## 自動化結果

- API 36.1 與 API 37：每台發現 41 tests，Google 真實驗收的 2 個 cases 因未啟用 hard-gate 參數而按設計跳過，其餘 39 cases／台通過，0 failed。
- API 25：41 tests、0 failed、0 ignored；由於舊平台不提供跟隨系統深色 configuration，覆蓋跟隨系統淺色、固定淺色及固定深色。
- 視覺矩陣之外，connected tests 覆蓋頂層導航、語言／外觀連續切換、搜尋草稿、查詢／刷新／保存、編輯候選、管理、匯入匯出、路線詳情、ETA Dialog、監控設定與通知。

## 真實 Google 硬門檻

Google Geocoding v4 真實三語 instrumentation 已使用有效 key、目前 debug package／certificate identity 及同一香港座標通過 `zh-Hant`、`zh-Hans`、`en` 請求；三語均返回非 plus code 地址，英文地址包含拉丁文字，新增路線的目前位置流程亦填入真實地址並顯示 Google 歸因。Google 可能按本地文字或最接近翻譯回退，因此香港座標的繁簡原文可相同；App 保持上游原文，不作機器轉換。
