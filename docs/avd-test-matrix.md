# Android AVD 測試矩陣

## 目的與範圍

本文件定義 BusIsComing 的常駐 Android Virtual Device（AVD）矩陣、可切換測試狀態、磁碟控制及驗收方式。目標是在不建立笛卡兒積式大量裝置的前提下，覆蓋最低系統、目前主要系統、未來系統、Google 能力、手機版面、輸入法、導覽列、權限、網絡及生命週期風險。

App 目前使用 `minSdk 25`、`targetSdk 36`、`compileSdk 36.1`。手機直向為常駐硬門檻；320dp、橫向及 600dp 平板只作按需 smoke test，不宣稱正式平板或折疊屏最佳化。

## 常駐 AVD

| AVD | 系統映像 | 固定版面角色 | Google 能力 | 主要驗證 |
|---|---|---|---|---|
| `BIC_Min_API25_NoPlay_Compact` | API 25 Google APIs ARM64 | Pixel 2、360 × 640dp、三鍵導覽 | 有 Google Play Services、無 Play Store | 最低版本、舊 Insets、舊 LatinIME、舊通知／主題分支、緊湊高度 |
| `BIC_Main_API36_1_Play_360` | API 36.1 Google Play ARM64 | Pixel 9、360 × 800dp、手勢導覽 | 有 Google Play Services 與 Play Store | 主要全量回歸、真實 Google Maps／定位、通知權限、精確鬧鐘、三語與大字體 |
| `BIC_Main_API36_1_NoPlay_Wide` | API 36.1 Google APIs ARM64 | Pixel 9、約 411dp 寬、手勢導覽 | 有 Google Play Services、無 Play Store | API 30+ 安裝來源、網站更新降級、較寬手機、有地圖但無商店 |
| `BIC_Future_API37_1_Play_16K` | API 37.1 Google Play 16KB ARM64 | Pixel 8、約 393dp 寬、手勢導覽 | 有 Google Play Services 與 Play Store | 未來系統、16KB page size、未來 Google 能力及不同 WindowInsets |

所有 AVD 均使用 ARM64、不建立額外虛擬 SD 卡、不保存帳號或長期 App 資料、停用 Quick Boot／快照保存並預設冷啟動。每次任務只能啟動一個未被其他任務佔用的 AVD；任務結束必須主動關閉本次啟動的 emulator。

## 不另建 AVD 的可切換狀態

- 版面：主要硬門檻為 360 × 640dp、360 × 800dp、約 393–411dp；同一 AVD 透過 `wm size`／`wm density` 暫時切換 320dp 窄屏、橫向及 600dp smoke，完成後執行 reset。
- 字體與外觀：繁體中文、簡體中文、英文 × 淺色、深色、跟隨系統 × font scale 1.0／1.3／2.0，由測試逐一切換並恢復。
- Google 降級：API 36.1 No Play 可暫時停用 Google Play Services，驗證 Maps／Play 定位不可用時保留完整文字詳情；完成後以 Wipe Data 還原，不把停用狀態保存為 AVD 基線。
- 網絡：正常代理、離線、超時及代理中斷由測試環境切換。真實 Google Maps 驗證須同時配置 emulator host proxy 與 Android guest global proxy；Citybus、Maps、Geocoding 和 ETA 的失敗應彼此獨立降級。
- 權限與系統狀態：位置未詢問、拒絕、永久拒絕、已授權、系統定位關閉、通知權限、精確鬧鐘及電池最佳化均在主要 API 36.1 Play AVD 內切換。
- 生命週期：冷 App 進程、configuration change、背景／前台、真正退出重入、進程死亡、鎖屏、Doze 與重啟使用同一角色 AVD 驗證。

Play Store 存在不代表 App 是從 Google Play 安裝。真正的 Play 應用內更新驗收仍須從內部測試軌道安裝；`adb install` 只驗證非 Play 安裝及失敗降級。

## 一次性清理與重建

現有 AVD 不保存需保留的帳號、App 資料或設定，因此刪除以下五台並以本文件四台常駐 AVD 重建：

- `BusIsComing_API_25`
- `Codex_Pin_QA_API_25`
- `Pixel_9_API_36`
- `Pixel_9_API_36_1`
- `Pixel_8`

系統映像處理：

- 保留 API 25 Google APIs ARM64。
- 保留 API 36.1 Google Play ARM64。
- 保留 API 37.1 Google Play 16KB ARM64。
- 刪除不再需要的 API 36 Google Play ARM64。
- 安裝 API 36.1 Google APIs ARM64，供現代無 Play Store AVD 使用。

執行順序必須先保留回退能力：先確認沒有 emulator 執行，安裝 API 36.1 Google APIs 映像，以新名稱建立並逐台驗證四台新 AVD；只有四台全部通過結構、啟動及 App smoke 後，才刪除五台舊 AVD 與 API 36 Google Play 映像，最後重新核對磁碟及清單。新名稱與舊名稱不衝突，因此不需要為建立新矩陣提前銷毀舊裝置。

刪除前必須再次確認沒有 emulator 正在執行，並以完整 AVD 名稱及完整 system-image package id 操作。不得使用未解析的 glob、廣泛目錄或遞迴刪除。AVD 及 system image 均可重新下載／重建，但舊 AVD 內部資料在本次操作後不可復原。

## 磁碟控制

重建前 `~/.android/avd` 約 22GB，主要來自 Quick Boot snapshots、長期增長的 `userdata-qemu.img.qcow2` 及虛擬 SD 卡。重建後四台乾淨 AVD 預計合計約 3–5GB；系統映像約 16GB，API 36 Play 被 API 36.1 Google APIs 等量替換，總映像空間大致不變。

每次啟動使用無快照模式；一般 App 清除可用 `pm clear`，需要回到乾淨裝置基線時使用 AVD Wipe Data。`pm clear` 不保證縮小 qcow2 實際檔案；AVD 再次明顯膨脹時，以可重建為前提執行 Wipe Data 或刪除重建，不進行高風險的映像就地壓縮。

## 建立後驗收

每台 AVD 必須逐一啟動及關閉，不佔用其他任務的 emulator，並記錄以下自動化證據：

1. AVD 名稱、Android release／SDK、ARM64 ABI、邏輯尺寸及導覽模式符合表格。
2. Play Store 與 Google Play Services 的安裝／可用狀態符合角色。
3. API 37.1 回報實際 16KB page size。
4. Debug APK 與 instrumentation APK 可安裝，App 主 Activity 可解析並冷啟動。
5. API 36.1 Play 可載入真實 Google Maps；代理配置只留在驗證環境，不進入 App 或 git。
6. API 36.1 No Play 的 Play 探測為不可用，Maps／Google Play Services 仍可使用；停用 GMS 時全文降級可用。
7. API 25 可執行最低版本 smoke，三鍵導覽、舊 IME 及緊湊畫面沒有阻塞核心查詢。
8. API 37.1 可完成未來系統 smoke，App 沒有啟動崩潰或明顯版面阻塞。
9. 驗證結束後 `adb devices` 不保留本次啟動的 emulator，四台常駐 AVD 均可由 SDK emulator 列出。

若任何新 AVD 無法啟動或角色與映像不符，停止後續刪除／清理步驟；保留已下載 system image，修正該 AVD 後重新驗收，不以另一角色裝置冒充通過。
