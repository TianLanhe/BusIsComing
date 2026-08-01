## Why

通知欄監控目前只檢查 Android 通知權限，沒有辨識 App 或監控 notification channel 已被停用、降低重要性或禁止鎖屏展示的情況，導致用戶能啟動一個實際上無法在鎖屏查看的監控 session。另一方面，`f9350cb` 移除直接電池最佳化豁免後，部分設備在鎖屏、Doze 或廠商省電策略下更容易延遲 ETA 刷新與狀態語音；用戶已決定恢復修改前的直接豁免流程。

現有監控啟動底部面板亦以連續動態 View 堆疊所有設定，缺少清晰分組、短屏滾動與穩定的底部主操作；標題下方同時展示路線起終點及較長介紹，令主要設定顯得擁擠。步行時間的減號還會被「三種估算取最大值」規則抵消，導致用戶無法把顯示值調低。

## What Changes

- 在啟動通知欄監控前檢查 App 通知總開關、普通監控 channel 與緊急提醒 channel 的存在、重要性及鎖屏可見性，區分明確異常與平台無法完全確認的狀態。
- 對可定位的 channel 問題直接開啟該 channel 的系統設定；不可解析或啟動失敗時依序降級到 App 通知設定、App 詳情頁及本地化文字引導。
- 從系統設定返回後重新檢查，不依賴 settings Activity 的 result code；通知整體或普通監控 channel 已停用時不啟動無可見常駐通知的監控服務。
- 依 BusIsComing UI 風格重整監控啟動底部面板：標題區只保留標題，移除起終點副標題及「每分鐘更新／鎖屏／語音」介紹，使用清晰分組的步行時間卡、選項區、設定狀態行與固定主操作。
- 為面板加入可滾動內容、48dp 最小觸控目標、語意 surface／文字顏色、克制圓角及三語大字體適配，不改造其他底部面板。
- 修復步行時間不能減少：以距離估算作為基線，再套用場景分鐘與可正可負的手動調整；每次 `-`／`+` 直接改變顯示值 1 分鐘，最低為 1 分鐘。
- **BREAKING**：撤銷現行「不直接請求電池最佳化豁免」行為，恢復 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest 宣告、豁免狀態檢查、說明提示及 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 系統確認頁。
- 保留 exact alarm special access、受控 WakeLock、前台服務、通知 channel、TTS 音頻策略、多語言與既有降級流程；拒絕電池豁免不得阻止基本監控啟動。
- 新增或修改的 App 文案同步提供香港繁體、獨立簡體與自然英文。
- 非目標：不修改 Citybus／DATA.GOV.HK 查詢、ETA 解析、路線排序、通知內容格式、TTS 文案或自動停止邊界；不引入廠商私有電池設定 Intent。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `notification-monitor-reliability`: 增加監控 channel 健康檢查、已知鎖屏異常辨識、系統設定導航與失敗回退要求。
- `notification-bar-monitoring`: 重整監控啟動面板的資訊層級與自適應版面，移除標題下方副標題及介紹，修復步行分鐘減少操作；同時展示可操作的通知／鎖屏準備狀態，並在設定返回後重新檢查再決定是否啟動。
- `monitor-high-priority-scheduling`: 恢復直接電池最佳化豁免檢查、manifest 權限與系統確認頁，同時保留拒絕時的非阻塞降級。

## Impact

- 受影響代碼：`service/BusMonitorNotificationContract.kt`、新增或抽取的 notification channel 狀態／導航 helper、`service/BusMonitorSchedulingCapability.kt`、`data/model/BusMonitorModels.kt`、`ui/main/MainActivity.kt`、`ui/main/MonitorSettingsBottomSheet.kt` 與 `AndroidManifest.xml`。
- 受影響資源：監控面板 layout／style，以及繁體、簡體與英文的監控準備狀態、設定操作、回退引導、步行手動調整及電池豁免說明文案；刪除不再使用的三語 `monitor_explanation`。
- 受影響規格：`notification-monitor-reliability`、`notification-bar-monitoring`、`monitor-high-priority-scheduling`。
- 相容性：Android 8+ 優先進入指定 channel；不支援 channel 或對應 Settings Activity 的設備降級到 App 通知／詳情設定。電池豁免直接請求只在 Android 6+、尚未豁免且系統可解析時展示。
- 用戶與政策影響：直接電池豁免可提高部分設備的鎖屏刷新與語音及時性，但會增加耗電、敏感權限與 Google Play 審核／信任成本；必須在請求前清楚說明並允許拒絕。
- 測試：以純狀態判斷與可注入 navigator 單測覆蓋 App 關閉、channel 缺失／停用／鎖屏隱藏、直接跳轉、逐級回退、返回重查、電池已豁免／未豁免及 Intent 失敗；另以計算與 layout 契約覆蓋步行減少、1 分鐘下限、移除副標題／介紹、48dp 觸控與滾動版面。
- 人工驗證：需在任務自有模擬器或實機驗證兩個 channel 的鎖屏設定、通知總開關、exact alarm 與電池豁免先後順序、拒絕降級、三語及明暗模式；驗證後關閉由本任務啟動的模擬器。
