## 1. P2P Stop Map 資料源

- [x] 1.1 新增 P2P stop map 資料模型，包含 `rawInfo`、`lang`、leg index、route variant、公開路線號、方向、seq、stop id、原始站名、展示站名、經緯度和起終標記。
- [x] 1.2 新增 `showstops2.php?r=<rawInfo>&l=<lang>` HTTP 客戶端或 repository 方法，沿用現有 Citybus 網絡層錯誤處理與日誌風格。
- [x] 1.3 實作 `addstoponmap(...)` 解析器，支援單程、多程、站名逗號前第一段裁切、缺字段和空響應處理。
- [x] 1.4 實作按 `rawInfo + lang` 的成功結果 1 天進程內快取，失敗、空響應或解析失敗不快取。
- [x] 1.5 實作同一次查詢內 `rawInfo + lang` 去重，避免多張卡片或 ETA 補全重複請求同一份 stop map。
- [x] 1.6 新增 showstops2 單程、多程與 `8X-THR-1 seq=20 -> 001364 長康街` fixture 單元測試。

## 2. 路線卡片站點預覽

- [x] 2.1 將 `RouteCardStopPreviewResolver` 改為使用 P2P stop map 查找第一段上車站與最後一段下車站。
- [x] 2.2 移除卡片站點預覽運行時對 DATA.GOV.HK `route-stop` 和 `stop` 查站名的依賴，不新增 route-stop fallback。
- [x] 2.3 調整卡片站點預覽 UI 文案，只展示站名與方向箭頭，不展示 `上車` 或 `下車` 字樣。
- [x] 2.4 保持站點預覽初始隱藏、成功後漸進回填、失敗或資料缺失時隱藏，且不得影響主卡片展示和默認總耗時排序。
- [x] 2.5 新增卡片預覽 resolver 和 adapter 綁定測試，覆蓋成功、缺 rawInfo、解析失敗、8X 錯位和舊查詢回填被忽略。

## 3. 首程 ETA StopId 對齊

- [x] 3.1 將 `CitybusFirstLegEtaService` 或其上游 stopId resolver 改為從 P2P stop map 的第一段 `routeVariant + boardingSeq` 取得 stop id。
- [x] 3.2 保留 ETA 查詢 URL `eta/{company}/{stop_id}/{route}`，其中 `stop_id` 來自 P2P stop map、`route` 使用公開路線號。
- [x] 3.3 保留 ETA 記錄匹配規則：優先 `route + stop + dir + seq`，嚴格匹配缺失時降級為 `route + stop + dir`。
- [x] 3.4 移除首程 ETA stopId 運行時對 DATA.GOV.HK `route-stop/{company}/{route}/{direction}` 的依賴，不新增 route-stop fallback。
- [x] 3.5 實作 ETA 與 stop map 補全的限流與去重，優先補全總耗時最低前 5 條路線，剩餘路線後台更新。
- [x] 3.6 新增 ETA 單元測試，覆蓋 P2P stop id 成功、showstops2 不可用顯示 `-`、無 route-stop fallback、嚴格匹配與降級匹配。

## 4. 文檔與診斷

- [x] 4.1 更新 `docs/citybus-eta-from-ppsearch.md`，將主流程改為 `ppsearch_p3.php -> showstops2.php -> P2P stop map -> ETA`。
- [x] 4.2 在文檔中保留 `route-stop/{company}/{route}/{directionPath}` 推導方式作為歷史方案和觀察參考，明確標註它不是新運行時 fallback。
- [x] 4.3 在文檔中記錄 `8X-THR-1 seq=20` 與公開 `8X/outbound seq=20` 的錯位案例與原因。
- [x] 4.4 補充 showstops2 與底部詳情 `getp2pstopinroute.php` 不一致時的診斷策略：卡片和 ETA 用 stop map，詳情用詳情接口，差異只記錄不提示用戶。

## 5. 驗證

- [x] 5.1 運行相關 parser、resolver、ETA 和 UI 綁定單元測試。
- [x] 5.2 運行 `./gradlew build`。
- [x] 5.3 使用當前真實網絡在模擬器或真機驗證路線查詢，截圖確認卡片站點、候車時間和底部詳情展示正常。
- [x] 5.4 運行 `openspec validate fix-p2p-stop-sequence-alignment --strict` 並確認 change 可用於 apply。
