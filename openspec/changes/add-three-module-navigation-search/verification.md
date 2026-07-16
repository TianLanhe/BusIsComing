# 驗證記錄

## 測試環境

- 日期：2026-07-17
- 裝置：Android Emulator `sdk_gphone16k_arm64`
- Android：17（API 37）
- 畫面：1080 × 2400，手勢導航
- App：debug APK，套件 `com.golink.busiscoming`

## 人工驗證結果

| 項目 | 結果 | 驗證內容 |
| --- | --- | --- |
| 三個 destination | 通過 | 冷啟動顯示「常用」，可切換「搜尋」與「設定」；頂層頁不出現返回按鈕，底部導航圖示與文字沒有重疊。 |
| 旋轉 | 通過 | 在「設定」與「搜尋」分別旋轉至橫屏，當前 destination 保持選中；內容位於底部導航上方且可捲動。 |
| 字體縮放 | 通過 | 將系統字體設為 1.3 倍，檢查常用首次引導、搜尋表單與設定列表；輸入框保持最少 56dp 高度，文字、按鈕與底部導航沒有重疊。 |
| TalkBack | 通過 | 實際啟用 TalkBack，確認搜尋標題與輸入元件可取得焦點；目前位置、交換起終點及三個底部導航項目具可讀名稱。 |
| 定位權限拒絕 | 通過 | 拒絕定位權限後，搜尋起點顯示「暫時無法取得目前位置，請手動選擇起點」，仍可手動輸入及選擇候選地點。 |
| 無網路 | 通過 | 關閉 Wi-Fi 與行動數據後輸入地點，顯示「地點搜尋失敗，請稍後重試」，表單保持可編輯。 |
| 查詢失敗 | 通過 | 先選擇「地區:中環」與「柴灣翠灣街18號」，再斷網發起查詢；結果摘要保留並顯示「搜尋失敗，請稍後重試」。 |
| 結果刷新 | 通過 | 恢復網路取得真實 Citybus 結果後執行下拉刷新；刷新指示器正常顯示及消失，更新時間由 00:42 更新至 00:43，路線卡保持可用。 |
| 路線管理返回 | 通過 | 將搜尋結果保存為常用後進入「路線管理」，返回時回到「常用」destination，保存的路線及選中狀態仍在。 |

## 版面與捲動補充

- 搜尋候選列表展開時可獨立捲動，且停用外層下拉刷新，避免手勢衝突。
- 搜尋結果列表由外層 `SwipeRefreshLayout` 與 `NestedScrollView` 承接，沒有固定高度或雙重捲動。
- 設定頁在 1.3 倍字體下可捲動至「關於我們」與「隱私政策」。
- 交換按鈕與主要輸入、查詢操作均維持至少 48dp 觸控範圍。

本次所需設備能力均可取得，沒有未完成的設備驗證項目。

## 自動化驗證

- `./gradlew testDebugUnitTest`：262 個 JVM 測試通過，0 失敗。
- `./gradlew connectedDebugAndroidTest`：38 個預設 instrumentation 測試完成，0 失敗；其中 1 個真實 Google API 驗收依既有設計為 opt-in 跳過項。
- `./gradlew build`：通過 Kotlin／Java 編譯、JVM 測試、lint、debug 及 release assemble。
- `openspec validate add-three-module-navigation-search --strict`：通過。

真實 Google API 驗收不屬於預設套件。配置 `GOOGLE_GEOCODING_API_KEY` 後，需同時傳入 `runGoogleApiAcceptance=true` 才會執行；顯式啟用但缺少 key 時仍會以清晰錯誤失敗。
