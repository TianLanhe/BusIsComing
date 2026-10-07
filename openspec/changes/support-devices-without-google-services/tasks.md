## 1. 鎖定正常行為與診斷證據

- [ ] 1.1 以 `c8143fe` 與目前代碼核對正常 Google 基線：地圖 renderer／方向圖層、文字位置、Fused 更新參數、權限與恢復、更新全分支、TTS 及監控。對照三個未歸檔地圖 changes，明列其有效行為及三方合併範圍，不回退為主 spec 舊原生藍點。
- [ ] 1.2 整理已提供的商店安裝、超級省電彈窗及 installer check 開啟證據；可取得時記錄故障 release 的啟動邊界、套件能力及必要 logcat。維持自動保護為待驗證根因，對照驗收由第 7 組完成。
- [ ] 1.3 建立正常與降級來源路由回歸：GMS 可用時系統定位後備呼叫數為零；Play 正常時比較原 source、資格、網站 metadata 邊界及 flexible 行為，作為後续各步驗收門檻。

## 2. 能力模型與可選 SDK 邊界

- [ ] 2.1 在 data 層加入可注入的獨立 GMS 能力判斷，沿用／擴展既有商店 detector；覆蓋可用、明確缺失／停用、未知、更新中及暫時 API 失敗，對應 `google-service-compatibility` 的能力 requirements。
- [ ] 2.2 於 `AppUpdateRuntime`／`GooglePlayUpdateSource` 工廠隔離可選 SDK 建立，守住檢查、註冊、resume 狀態、完成更新及 UI 操作全部入口；可預期同步與非同步失敗轉結構化結果，正常分支原樣委派。
- [ ] 2.3 接入前台／設定返回能力重判、來源取消及 generation 管理；測試狀態不變不重建，狀態改變只切換受影響能力，舊 callback 不覆蓋新結果。

## 3. 無 GMS 系統定位

- [ ] 3.1 為一次性定位及 `ForegroundLocationSource` 加入 API 25 相容的 `LocationManager` 實作與能力 factory；權限及實際 provider 決定 network／GNSS 可用性，正常 GMS 仍使用現有 Fused。
- [ ] 3.2 在無 GMS 一次性路徑實作開始時計算的 3 秒定位截止、原 3 秒地址／5 秒整體上限、30 秒快照與合併需求；不為切換 provider 重設期限，不改正常 Google 分支的既有等待／更新參數。
- [ ] 3.3 覆蓋成功、無 provider、權限拒絕、近似權限、定位關閉、無 fix、逾時、取消、owner 銷毀及舊 request 結果；驗證 listener 清理、單次交付、不污染下一輪及保護原輸入。
- [ ] 3.4 接入附近行程、候選距離與目前位置起點；保持 Google HTTP、LanguageSnapshot、cache／attribution、真實地址及手動選地點規則，對應 `route-place-selection` 全部保留及新增 scenarios。
- [ ] 3.5 接入文字詳情的前台系統位置來源，保留既有首 fix 提示、更新／位移參數、精度與匹配政策；無地圖不建立 heading 訂閱，離開前台立即釋放。

## 4. 地圖降級與正常版面保護

- [ ] 4.1 調整 `RouteDetailActivity` 與必要 layout holder，在能力判斷後才建立 MapView；審計 XML inflate、onCreate、getMapAsync、全部 lifecycle 及恢復入口，測試無 GMS 分支不呼叫 Google 地圖 factory。
- [ ] 4.2 無 GMS 直接呈現全屏文字詳情、非阻擋提示及返回，保留站點／換乘／步行／ETA／可靠文字位置；僅隱藏地圖專用控件，沿用缺失內容重試。
- [ ] 4.3 測試無 GMS 重建、能力恢復、前後台及晚到 callback；正常裝置比對原地圖首次展示、香港首幀、相機所有權、方向、站點互動、三段詳情窗與定位權限，對應 map delta 全部 scenarios。
- [ ] 4.4 審查必要 runtime 文案的繁／簡／英、Google／CSDI attribution、明暗、大字體及 TalkBack；驗證無 GMS 的系統 TTS／無引擎提示與通知監控仍沿用既有規則，不加入新語音供應者。

## 5. 更新渠道的限定後備

- [ ] 5.1 將更新 policy 的 Boolean 能力輸入調整為可區分明確缺失、停用與 `UNUSABLE`／未知的結果；明確缺失／停用時允許所有初始來源使用官網，初始渠道持久化保持不變。
- [ ] 5.2 完整回歸 Play 有／無更新、`ERROR_APP_NOT_OWNED`、暫時失敗、versionName 補名、Debug 短路、24 小時節流、72 小時提示、稍後／略過及 flexible flow；正常分支對比不得出現新增網站 APK 跳轉。
- [ ] 5.3 測試 Play／非 Play／未知初始來源 × 商店四態，涵蓋 probe 例外、恢復 Play、舊網站 callback、按鈕操作時能力改變及既有快照保留；驗證官網仍使用原 HTTPS metadata 契約與三語瀏覽器下載頁。

## 6. 工程與裝置驗證

- [ ] 6.1 執行新增及相關既有 unit／instrumentation 測試，將四份 delta 的 requirements／scenarios 對應到測試或第 7 組實機證據；正常 Google 行為相等是硬性完成條件。
- [ ] 6.2 用任務持有、符合 design 畫像的 AOSP API 36 裝置驗證真正無 GMS 的首次安裝、冷啟動、查詢／行程／置頂、文字詳情、定位失敗／成功、TTS、更新及重建；另驗證 API 25 相容路徑。
- [ ] 6.3 用任務持有的 Google Play API 36 畫像比較正常基線，獨立覆蓋只停用商店、只停用 GMS、兩者停用及恢復；三語、明暗、font scale 1.0／2.0、權限與無 fix 覆蓋按 design 記錄。不能以停用商店代替真正無 GMS 驗證。
- [ ] 6.4 執行 `./gradlew build` 並記錄結果；關閉本任務啟動的全部 AVD。裝置不可用或畫像不符時對應驗收保持未完成。

## 7. 發佈產物與受影響實機驗收

- [ ] 7.1 在實作及工程驗證完成後，核對 Play Console installer check 設定並準備不含該檢查的 release；保留 Play app signing key 及 application ID，核對可升級 versionCode。此項需實際外部設定／產物證據，本機代碼變更不能替代。
- [ ] 7.2 取得正式候選 Play 簽署 APK，記錄版本、保護設定、簽名相容及產物匹配結果；依舊版／新版、一般／超級省電／恢復一般模式在受影響手機對照啟動，排除仍有啟動攔截。若仍失敗，依日誌繼續診斷並更新 artifacts。
- [ ] 7.3 在有正常 GMS／Play 及真實更新資格的環境驗證 Play 安裝啟動與 flexible update；在無 GMS 環境驗證同簽名 APK 安裝升級及行程／置頂／偏好保留。
- [ ] 7.4 核對 Play 目標地區同版本 100% 發佈後的網站 APK／metadata／下載響應一致性與無 installer check 產物，依原發佈順序提供網站更新；不能將本次 HTTP 403 或未取得包當作通過。
- [ ] 7.5 核對所有證據、tasks 勾選、程式與 OpenSpec 範圍並按 AGENTS.md 提交；任何必要 release／實機驗收缺口均保持未完成且明確報告，不宣稱無 GMS 相容已全部完成。
