## Context

`add-notification-bar-monitoring` 已建立短時通知欄監控：用戶從結果卡片鈴鐺入口啟動監控，前台服務每分鐘刷新首程 ETA，通知展示出門狀態並在狀態切換時使用 TextToSpeech 播報。實測後有三個缺口：鎖屏頁面沒有展示監控通知、App 退前台或鎖屏後刷新不再按預期執行、狀態切換語音沒有可靠播報。

現有 `BusMonitorService` 使用 `Handler.postDelayed(60_000)` 驅動刷新。這對 App 前台或進程活躍時足夠，但不能作為鎖屏、Doze 或進程被系統回收後的可靠喚醒機制。Android 官方文件也指出 Doze 會限制網絡、CPU、standard alarms、jobs 和 WorkManager，`setAndAllowWhileIdle()` / `setExactAndAllowWhileIdle()` 可在 Doze 中觸發但存在頻率和耗電限制。因此本變更只承諾「每分鐘嘗試更新」，不承諾系統在所有省電狀態下精確每 60 秒完成網絡刷新。

卡片 UI 方面，當前 `item_bus_route.xml` 在第一行右側放置候車兩行和鈴鐺入口，候車區和 48dp 鈴鐺觸控區會共同壓縮路線號寬度；第二行站點預覽右側空間沒有被利用。用戶已確認最終採用右側跨兩行候車資訊塊，且鈴鐺只移動位置，不改樣式。

## Goals / Non-Goals

**Goals:**

- 將結果卡片右側候車摘要重排為跨路線號和站點預覽兩行的資訊塊，減少對第一行寬度的擠壓。
- 保持鈴鐺現有低權重樣式，只改位置和約束。
- 讓監控通知在通知欄和鎖屏頁面可見，並展示完整路線與候車狀態。
- 讓 App 退前台或鎖屏後，監控仍能在系統允許時每分鐘嘗試刷新 ETA。
- 讓監控 session 可持久化和恢復，不把刷新生命週期綁死在 Activity 或單一進程內存狀態。
- 提升 TTS 播報可靠性，包含語言 fallback、試聽入口、播報失敗降級和狀態切換去重。
- 明確 Android 系統限制下的產品邊界和驗證方式。

**Non-Goals:**

- 不新增全天候通勤計劃、自動定時啟動監控、桌面小組件或快捷方式。
- 不承諾 Doze 深度休眠下仍精確每 60 秒完成網絡請求；此狀態下只能在系統允許的窗口中嘗試恢復。
- 不請求電池優化白名單作為默認方案。
- 不使用全屏提醒、鬧鐘樣式提醒或繞過用戶鎖屏隱私設定。
- 不重新設計鈴鐺圖標視覺樣式。

## Decisions

### 1. 卡片使用右側跨兩行候車資訊塊

建議將 `item_bus_route.xml` 改為 `ConstraintLayout` 或等效約束佈局：

```text
┌────────────────────────────────────────────┐
│ A12              ┌ 等候 7 分鐘        🔔 │
│ 樂軒臺 → 興華... │ 下一班 27 分鐘 ›     │
├────────────────────────────────────────────┤
│ HK$ 47.1 · 耗時 11 分鐘 · 步行 257 米      │
└────────────────────────────────────────────┘
```

左側 `routeName` 和 `stopPreview` 垂直排列；右側 `waitBlock` 的 top 約束到 `routeName` top，bottom 約束到 `stopPreview` bottom，形成類似合併單元格的視覺。`waitBlock` 內部包含主候車文案、下一班摘要和鈴鐺圖示。站點預覽使用 `0dp` 彈性寬度和單行省略，避免長站名擠壓右側核心候車資訊。

理由：

- 候車主信息、下一班和鈴鐺仍屬於同一功能區，語義清楚。
- 第二行空間被利用，不再把右側所有內容壓在第一行。
- 鈴鐺保持原樣式，只需移動到 wait block 內的右側。

替代方案：

- 將下一班和鈴鐺直接放到站點預覽行右側：簡單，但候車資訊會被拆散，鈴鐺和主候車狀態的語義距離變遠。
- 長按卡片或滑動露出監控：節省空間，但發現性和無障礙較差。

### 2. 使用新 notification channel 承載鎖屏可見通知

現有 channel 建立後，importance 和部分可見性行為不能可靠透過同一 id 修改。應新增 v2 channel，例如 `bus_monitor_status_v2` 和必要的 alert channel，並在通知上設置 public lockscreen visibility。常駐監控通知應能在鎖屏展示完整路線和候車內容，但仍尊重用戶在系統通知設定中對鎖屏內容的最終控制。

理由：

- 解決既有 low importance channel 無法升級的問題。
- 鎖屏可見是本功能的核心預期，不應依賴系統默認值。
- 不需要全屏 intent 或鬧鐘式打斷，只需要可查看的 ongoing notification。

替代方案：

- 沿用現有 channel：可能被舊 importance 和用戶鎖定設定影響，無法穩定修復。
- 使用高優先級或全屏通知：過度打擾，和短時候車監控不匹配。

### 3. 前台 tick + 系統調度的混合刷新策略

刷新策略分兩層：

```text
BusMonitorService
      │
      ├─ App/服務活躍：Handler 60s tick
      │
      ├─ 退前台或鎖屏：AlarmManager 安排下一次喚醒
      │
      ├─ 刷新開始：防重入，短時間完成 ETA request
      │
      └─ 刷新結束：更新通知、保存 session、安排下一次嘗試
```

實作時應優先使用不增加特殊權限的 `setAndAllowWhileIdle()` 或普通 alarm；只有在實測證明普通方案不能達到可接受的短時監控體驗時，才評估 `setExactAndAllowWhileIdle()` 和 `SCHEDULE_EXACT_ALARM` 的權限成本。Doze 深度休眠下 Android 對 idle alarms 存在頻率限制，因此文案和狀態應保持「每分鐘嘗試更新」，並展示最後成功更新時間與資料延遲。

理由：

- `Handler.postDelayed` 不應承擔喚醒設備和恢復進程的責任。
- `WorkManager` 的週期性粒度不適合分鐘級候車監控。
- AlarmManager 能更接近用戶主動啟動的短時、時間敏感任務，但需要接受系統限制。

替代方案：

- 只用前台服務 + Handler：前台測試通過，但退前台和鎖屏不可靠。
- WorkManager periodic work：最小週期和 Doze 行為不符合分鐘級監控。
- 直接請求忽略電池優化：權限和政策成本高，不適合作為默認方案。

### 4. 監控 session 持久化

監控開始後保存最小 session：

- route name / display title
- `FirstLegEtaQuery`
- walking minutes
- voice enabled
- last status
- last successful notification text
- last successful ETA timestamp
- next stop deadline 或 fallback deadline
- consecutive failure count
- session start time 和 expiry

服務重建或 alarm receiver 被觸發時，應從持久化 session 恢復必要資料。如果 session 已過停止條件或過最大監控時長，應清理 session 並移除通知。

理由：

- 退前台、鎖屏或系統回收進程後不能依賴內存變量。
- 通知文案和語音去重需要知道上一狀態。
- 失敗降級需要最後一次成功資料。

替代方案：

- 只保留內存 session：簡單，但正是目前可靠性缺口來源之一。
- 把整個 route option 序列化：資料過大且含 UI 派生字段，不利於穩定恢復。

### 5. TTS 可用性檢查、試聽與 fallback

監控設定面板保留語音開關，並新增 `試聽語音` 入口。服務或語音 helper 初始化 TTS 時應檢查初始化結果和 `setLanguage` 返回值，按順序嘗試繁中、香港或通用中文 fallback。語音不可用時：

- 面板提示「此設備暫時無法播放語音提醒」或等效文案。
- 開始監控仍可繼續，通知更新不受影響。
- 狀態切換時不因 TTS 失敗阻塞服務。

播報觸發應基於狀態機變更，並保存上次已播報狀態。同一狀態不重複播報；若 ETA 刷新失敗但本地倒計時足以推導狀態跨過門檻，可在不違背資料延遲提示的前提下觸發一次保守播報。

理由：

- 不同模擬器和真機的 TTS 語音包可用性差異很大。
- 用戶在出門前需要知道語音是否真的會響。
- TTS 是輔助提醒，不應降低通知監控主流程可靠性。

替代方案：

- 只保留現有默認 TTS：實作簡單，但失敗不可見。
- 使用自帶音頻文件：可控但文案動態分鐘數難處理，且增加資源和語言維護成本。

## Risks / Trade-offs

- [Risk] Doze 深度休眠下 Android 可能限制網絡和 idle alarm 頻率，無法真正每 60 秒完成 ETA 網絡請求。→ Mitigation：文案使用「每分鐘嘗試更新」，通知展示最後更新時間和資料延遲；測試中加入 Doze/鎖屏驗證並記錄實際限制。
- [Risk] 新 channel id 會讓部分用戶需要重新授權或看到新的通知分類。→ Mitigation：保持 channel 名稱清晰，僅遷移監控通知，不影響其他通知。
- [Risk] AlarmManager 或 WakeLock 使用不當會增加耗電。→ Mitigation：只在用戶手動啟動的短時 session 中使用；每次刷新超時、失敗保護和自動停止。
- [Risk] 持久化 session 恢復後 ETA query 已過期或路線資料變化。→ Mitigation：恢復時先檢查停止條件和最大時長；刷新失敗時保留最後成功狀態並允許停止。
- [Risk] 右側候車資訊塊在極窄屏或大字體下仍可能擠壓站名。→ Mitigation：站點預覽單行省略，右側 wait block 設定穩定寬度和最大寬度，必要時讓底部信息行換到兩行。
- [Risk] 鎖屏完整內容可能暴露用戶通勤路線。→ Mitigation：本次用戶已確認允許完整展示；仍尊重系統鎖屏通知隱私設定。
- [Risk] 語音播報受勿擾、媒體音量、藍牙音頻或系統 TTS 設定影響。→ Mitigation：提供試聽入口、失敗提示和通知降級，不承諾繞過系統音頻策略。
