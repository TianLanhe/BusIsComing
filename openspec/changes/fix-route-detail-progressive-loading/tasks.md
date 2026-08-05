## 1. 站數語義與站序完整性

- [ ] 1.1 在 `RouteDetailLaunchArgsTest`、model／formatter tests 加入一段相鄰上下車計 1、兩段相鄰換乘計 2、多個途經站加下車站且不計上車站的失敗回歸。
- [ ] 1.2 新增缺失中間 seq、重複 seq、端點不一致、非法 stop id／坐標及可選欄位缺失的 deterministic parser／validator fixtures 與測試。
- [ ] 1.3 實作獨立站序完整性驗證結果，讓完整連續站序成為發布時間線、計算站數、寫入結構快取及幾何端點校驗的門禁。
- [ ] 1.4 將摘要站數改為結構化 Loading／Available／Unavailable 狀態，移除 plan 差值或空集合直接格式化為 `0 站` 的 UI 路徑。
- [ ] 1.5 擴充詳情 repository 的受控恢復：站序首次殘缺時最多恢復／重試一次，仍殘缺時回傳局部結構錯誤且不發布或快取。

## 2. 進程快取與詳情 single-flight

- [ ] 2.1 為結構／步行快取加入跨 repository consumer 命中、24 小時過期、語言／端點隔離、完整性門禁、原子替換及較差結果不可覆蓋的單元測試。
- [ ] 2.2 重構 domain cache value，確保站點結構只保存穩定欄位、步行 cache 只保存完整必要距離，預計時間、分段票價、ETA、session、UI 狀態與派生站數均不進入一天快取。
- [ ] 2.3 由 `RouteDetailRuntime` 提供可注入的進程級 cache 擁有者，收斂／移除保存整個 `ParsedRouteDetail` 的長期快取路徑，並驗證返回後重新進入立即取得可靠結構。
- [ ] 2.4 先以測試覆蓋相同 request identity 共用工作、不同 identity 隔離、單一 consumer 離開、最後 consumer 取消及失敗後新 flight。
- [ ] 2.5 實作進程級詳情 request coordinator，以完整 query／語言／plan／recovery／opaque session identity 做 single-flight，且不得在日誌暴露 Cookie、PHPSESSID 或完整敏感 query。
- [ ] 2.6 將詳情載入接入 cache-first 且動態網絡並發刷新流程，確保網絡成功只補充新鮮時間／票價，網絡失敗不清空快取結構。

## 3. 並發頁面狀態與 reducer

- [ ] 3.1 定義不持有 View／GoogleMap 的不可變頁面狀態與事件，涵蓋 launch summary、站數、結構、動態詳情、Map／相機、每個 geometry key、ETA、互動及生命週期。
- [ ] 3.2 先建立純 reducer 測試，排列 Map、cache、network detail、ETA 與多段 geometry 的完成順序，覆蓋 stale generation、舊錯誤晚到、品質單調及局部重試。
- [ ] 3.3 實作主線程唯一 reducer，以 page generation、domain generation 與 stable key 拒絕過期事件，並以 `Refreshing(previous)` 保留最近成功內容。
- [ ] 3.4 將 `RouteDetailActivity` 的詳情、ETA、幾何、失敗與計數欄位逐域改為發送事件／渲染狀態 diff，保持 Map、詳情、各段幾何與 ETA 真正同時啟動。
- [ ] 3.5 讓 adapter、marker 與 polyline 以 stable id 增量更新，測試其他資料域加入時保留展開分段、選中站點、列表位置與既有 overlay。
- [ ] 3.6 串接 Activity 銷毀、語言改變、configuration change 與 process recreation，驗證舊 callback 無法覆蓋新頁面並恢復可序列化互動狀態。

## 4. 幾何 candidate 發布門禁

- [ ] 4.1 擴充 `RouteGeometryLoadCoordinatorTest`，覆蓋 candidate 先到但端點未到時不可渲染、晚校驗成功無重請求、晚校驗失敗從未閃現、多 consumer 獨立及舊失敗晚於新成功。
- [ ] 4.2 調整 geometry coordinator／consumer 狀態，讓未核對 candidate 僅保存在內部，只有目前 consumer 的可靠端點與 generation 通過後才發布 Success。
- [ ] 4.3 將每個 geometry key 的成功、局部失敗與手動重試接入 page reducer，確認單段失敗不移除其他成功線段、marker 或可靠站點。

## 5. 香港首幀與相機所有權

- [ ] 5.1 為相機策略加入保存相機優先、首次香港預設、所有 geometry 終態後一次全覽、局部失敗結束等待及使用者手勢鎖定的純邏輯測試。
- [ ] 5.2 在 MapView 建立參數中集中配置香港中心約 `22.3193, 114.1694` 與城市級 zoom，確保首個可見底圖不經 `(0, 0)` 再修正。
- [ ] 5.3 實作 PAGE／USER 相機所有權，以 `REASON_GESTURE` 判定使用者接管，並避免程式相機動畫被誤判為手勢。
- [ ] 5.4 修改首次全覽條件：可靠站序可用且所有預期 geometry key 已到成功／失敗終態時最多平滑 fit 一次，bounds 使用查詢端點、可靠站點及成功幾何。
- [ ] 5.5 驗證 bottom sheet padding、adapter／ETA 更新、局部 geometry 完成及 configuration change 不重置鏡頭；全覽、目前位置與站點控件仍可由使用者主動移動相機。

## 6. 三語 UI、局部失敗與可觀測性

- [ ] 6.1 為香港繁體、獨立簡體與自然英文新增站數載入／不可用、動態詳情刷新及必要局部錯誤文案，保持 placeholder、TalkBack 與可翻譯 key 一致。
- [ ] 6.2 更新摘要、時間線與局部重試 UI，確保 Map、詳情結構、動態詳情、每段幾何或 ETA 單獨失敗時只影響對應區域並保留返回操作。
- [ ] 6.3 加入不含敏感資料的 debug／結構化診斷，覆蓋 cache hit／miss／expired、single-flight join、站序校驗原因、stale callback 拒絕及相機所有權轉移。
- [ ] 6.4 以資源與 UI 測試覆蓋三語站數 Loading／Available／Unavailable、局部刷新／錯誤及 TalkBack 語義，確認 360dp 與 font scale 1.3／2.0 不裁切核心摘要或重試入口。

## 7. 完整驗證與交付核對

- [ ] 7.1 執行受影響 parser、repository、cache、single-flight、reducer、formatter、geometry coordinator、map presentation／camera policy 的 focused unit tests 並記錄結果。
- [ ] 7.2 以不保存 PHPSESSID 的繁體、簡體、英文 live 樣本抽查單段、多段及相鄰站序連續性；若發現合法缺號，先更新 artifacts 與 fixture 再調整驗證器。
- [ ] 7.3 執行 `./gradlew build`，修正所有受影響 unit／instrumentation／lint／resource 失敗。
- [ ] 7.4 定義並只啟動本任務擁有、具 Google Maps 能力的目標模擬器，驗證香港首幀、慢網下各域漸進加入、返回重入 cache、局部失敗／重試及使用者手勢後不再自動搶鏡頭。
- [ ] 7.5 在任務自有模擬器完成人工三語、淺色／深色、font scale 與 TalkBack 抽查，保存必要且不含敏感資料的驗收證據，完成後關閉本任務啟動的全部模擬器。
- [ ] 7.6 重新執行 OpenSpec 嚴格驗證，核對 `tasks.md` 勾選、`git status --short`、暫存差異與提交範圍，只提交本 change 的實作與 artifacts。
