# BusIsComing 實作計劃

## 常用頁路線置頂

本功能依 `openspec/changes/add-frequent-route-result-pinning` 實作，主要工作順序如下：

1. 建立版本化嚴格路線指紋、三態置頂模型、嚴格遞增 token 及純列表投影。
2. 將 `RouteQueryState` 分離為原始結果與既有排序結果，保持搜尋頁排序全部、常用頁只排序未置頂。
3. 將 SQLite 升至 v4，新增 `route_result_pins`、複合主鍵、降序索引、外鍵與 repository。
4. 以專用單線程 executor 並行載入長期置頂，讓常用頁首次列表等待路線與置頂兩者。
5. 將共用路線 Adapter 遷移為 ListAdapter，加入路線卡與普通排序分隔列兩種 item。
6. 接入 ItemTouchHelper、約 40% 門檻、無 fling 捷徑、觸控排除、回彈、視口錨點與 TalkBack action。
7. 接入樂觀長期寫入／取消、撤銷、mutation generation 及失敗恢復。
8. 讓行程編輯、刪除、複製、合併／取代匯入及 `.bicroutes` v1 遵守置頂作用域與 transaction 邊界。
9. 完成三語、明暗色、窄屏、大字體、生命週期、SQLite migration、手勢及完整構建驗證。

## 驗證重點

- JVM：fingerprint、session、projector、排序、DiffUtil identity、滑動 policy、mutation coordinator、viewport anchor、資源 parity 及 transfer codec。
- Instrumentation：v1/v2/v3→v4、新裝 v4、外鍵級聯、更新與匯入 transaction 回滾、Activity recreation、觸控排除及既有長列表捲動。
- 模擬器人工流程：普通→本次→長期、反向取消、Snackbar 升級／撤銷、長期右滑回彈、排序／ETA／刷新、三語、淺深色、大字體與 TalkBack。
- 最終執行 `./gradlew build`，確認 compile、unit test、lint 及 debug/release assemble。

## 本次驗證記錄（2026-07-29）

- 以真實 Citybus 常用行程結果人工驗證普通→本次→長期、反向直接取消、Snackbar、長期右滑回彈、排序、ETA、刷新及 dormant 長期置頂重新匹配；置頂與取消時未自動捲回列表頂部。
- 在單獨啟動、此前未運行的 `Pixel_9_API_36_1` AVD 執行相關 instrumentation，23 個測試全部通過，涵蓋 v1／v2／v3→v4、新裝 v4、外鍵級聯、transaction 回滾、生命週期、手勢命中區、長列表及無障礙節點；驗證完成後已關閉 AVD。
- 人工檢查繁體、簡體、英文的淺色與深色，以及約 360dp、font scale 1.0／1.3／2.0；角標、分隔列與核心文字均可辨識，超長站點預覽按既有規則省略，頁面保持可捲動。
- 未逐字聆聽 TalkBack 的實際語音；已由 instrumentation 驗證三種狀態描述、自訂 action、action 執行、狀態切換後舊 action 移除，以及裝飾性書籤不獨立朗讀。
- 讀寫失敗降級與 Snackbar 撤銷的競態主要由 JVM／instrumentation 自動化覆蓋；人工流程已確認提示及主要狀態轉換。
