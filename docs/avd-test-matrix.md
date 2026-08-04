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

重建前 `~/.android/avd` 約 22GB，主要來自 Quick Boot snapshots、長期增長的 `userdata-qemu.img.qcow2` 及虛擬 SD 卡。重建後四台乾淨 AVD 預計合計約 3–5GB。實際 API 36.1 Google APIs 映像比刪除的 API 36 Play 映像大約 2GB，因此 system image 空間會增加，但 AVD 可寫層的縮減仍會帶來顯著淨回收。

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

## 實際執行記錄（2026-08-05）

### 工具鏈與建立方式

- 先執行 `./gradlew assembleDebug assembleDebugAndroidTest`，構建成功後才開始 AVD 操作。
- API 36.1 Google APIs ARM64 由 SDK Manager 安裝，`system.img`、ABI、tag 及 `AndroidVersion.ApiLevel=36.1` 均驗證通過。
- Homebrew Android Command-line Tools 由 `14742923` 升級至 `15859902`（CLI 22.0）。舊 `avdmanager` 仍不能把 36.1／37.1 system image package id 作為建立參數，因此 API 25 由 `avdmanager` 建立，其餘 AVD 以已安裝的官方 system image 與 Pixel hardware profile 寫入明確 `.ini`／`config.ini`；每台隨後均以 `-wipe-data -no-snapshot` 成功冷啟動，避免把未啟動的手工定義視為完成品。
- 所有測試均顯式使用本次啟動的 `emulator-5556`；驗證期間在線的使用者無線 ADB 真機沒有被安裝、清除或執行測試。

### 角色化驗證結果

| AVD | 實測平台與版面 | Google／Play 狀態 | 自動化結果 |
|---|---|---|---|
| `BIC_Min_API25_NoPlay_Compact` | Android 7.1.1／API 25、ARM64、1080 × 1920、480dpi，即 360 × 640dp | GMS 存在；只有 LicenseChecker，沒有 Play Store 商店界面 | App 與 instrumentation APK 安裝及冷啟動成功；`TopLevelNavigationInstrumentedTest` 6／6 通過 |
| `BIC_Main_API36_1_Play_360` | Android 16／API 36.1 映像、ARM64、1080 × 2400、480dpi，即 360 × 800dp；4KB page | GMS 與 Play Store 均存在 | 真實 `N118` 高縮放測試 1／1 通過，確認 Google watermark／底圖載入 callback、道路幾何、站點、示意步行線及圖例已移除 |
| `BIC_Main_API36_1_NoPlay_Wide` | Android 16／API 36.1 映像、ARM64、1080 × 2400、420dpi，約 411dp 寬；4KB page | GMS 存在；`market://` 無可解析 Play Store Activity | 無 Play 更新分流測試 1／1、`TopLevelNavigationInstrumentedTest` 6／6 通過 |
| `BIC_Future_API37_1_Play_16K` | Android 17／API 37.1 映像、ARM64、1080 × 2400、440dpi，約 393dp 寬；實測 page size `16384` | GMS 與 Play Store 均存在 | App 與 instrumentation APK 安裝及冷啟動成功；`TopLevelNavigationInstrumentedTest` 6／6 通過 |

真實地圖截圖在執行時輸出至 `/tmp/bic-avd-matrix/`，只作本機驗證證據，不進入 git。截圖可見 Google 底圖與道路上的 `N118` 線段，且沒有舊圖例卡片。

### 清理與最終狀態

- 四台新 AVD 全部通過後，才使用 Android CLI 按完整名稱刪除 `BusIsComing_API_25`、`Codex_Pin_QA_API_25`、`Pixel_8`、`Pixel_9_API_36`、`Pixel_9_API_36_1`；舊 AVD 內資料不可復原。
- 精確移除 `system-images;android-36;google_apis_playstore;arm64-v8a`，四個批准 system image 的 `system.img` 均仍可讀。
- `~/.android/avd` 由約 22GB 降至 3.6GB；四台分別約 382MB、1.2GB、1.0GB、1.0GB。system image 合計約 17.9GB，較重建前增加約 2GB，整體淨回收約 16GB。
- SDK emulator 最終只列出四個 `BIC_*` 名稱；本次啟動的 emulator 全部已關閉，`adb devices` 沒有殘留 `emulator-*`。
