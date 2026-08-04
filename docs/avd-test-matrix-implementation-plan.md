# Android AVD 測試矩陣執行計劃

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立並驗證四台規範化 BusIsComing AVD，通過後刪除五台重複／膨脹舊 AVD 及 API 36 Play 映像。

**Architecture:** 把共用 system image 與各 AVD 可寫層分開管理。先安裝缺少映像並以新名稱建立四台裝置，逐台冷啟動、安裝及驗收；只有新矩陣全部通過後才刪除舊資產，最後回寫實際磁碟與驗證結果。

**Tech Stack:** Android SDK emulator 36.6.11、`sdkmanager`／`avdmanager`、ADB、Gradle、Google Play／Google APIs ARM64 system images。

## Global Constraints

- 平台邊界為 `minSdk 25`、`targetSdk 36`、`compileSdk 36.1`。
- 常駐矩陣只保證手機直向；320dp、橫向及 600dp 平板為按需 smoke。
- 只能啟動本次任務自行啟動的 emulator；每台逐一執行並在驗證後關閉。
- 不修改或提交使用者既有 `app/build.gradle.kts`。
- 不輸出、保存或提交 Maps API key、Cookie、session 或代理憑證。
- 所有刪除使用完整 AVD 名稱或完整 system-image package id；新矩陣未全部通過前不得刪除舊資產。

---

### Task 1: 鎖定前置狀態並構建驗收 APK

**Files:**
- Read: `docs/avd-test-matrix.md`
- Build: `app/build/outputs/apk/debug/app-debug.apk`
- Build: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

**Interfaces:**
- Consumes: 已批准的四台新 AVD 名稱及五台舊 AVD 名稱。
- Produces: 無執行中 emulator 的前置條件、重建前磁碟基準及兩個 APK。

- [ ] **Step 1: 確認沒有執行中 emulator**

```bash
adb devices -l
```

Expected: 清單沒有 `emulator-*`；如有來源不明裝置，停止操作且不接管。

- [ ] **Step 2: 保存重建前清單與磁碟基準**

```bash
/Users/hezhenyu/Library/Android/sdk/emulator/emulator -list-avds
du -sh /Users/hezhenyu/.android/avd
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-25/google_apis/arm64-v8a
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-36/google_apis_playstore/arm64-v8a
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-36.1/google_apis_playstore/arm64-v8a
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-37.1/google_apis_playstore_ps16k/arm64-v8a
```

Expected: 五台舊 AVD 均存在，AVD 約 22GB，四個既有映像路徑可讀。

- [ ] **Step 3: 構建最新 APK**

```bash
./gradlew assembleDebug assembleDebugAndroidTest
```

Expected: `BUILD SUCCESSFUL` 且兩個 APK 存在。

### Task 2: 安裝 API 36.1 Google APIs 映像

**Files:**
- Create externally: `/Users/hezhenyu/Library/Android/sdk/system-images/android-36.1/google_apis/arm64-v8a/`

**Interfaces:**
- Consumes: SDK root `/Users/hezhenyu/Library/Android/sdk`。
- Produces: `system-images;android-36.1;google_apis;arm64-v8a`。

- [ ] **Step 1: 安裝精確 package id**

```bash
yes | /opt/homebrew/bin/sdkmanager --sdk_root=/Users/hezhenyu/Library/Android/sdk "system-images;android-36.1;google_apis;arm64-v8a"
```

Expected: exit 0；如 minor API XML schema 無法登記 package，保留所有舊 AVD 並先修復 command-line tools。

- [ ] **Step 2: 驗證映像實體內容**

```bash
test -s /Users/hezhenyu/Library/Android/sdk/system-images/android-36.1/google_apis/arm64-v8a/system.img
test -f /Users/hezhenyu/Library/Android/sdk/system-images/android-36.1/google_apis/arm64-v8a/source.properties
rg "AndroidVersion.ApiLevel=36.1|SystemImage.TagId=google_apis|SystemImage.Abi=arm64-v8a" /Users/hezhenyu/Library/Android/sdk/system-images/android-36.1/google_apis/arm64-v8a/source.properties
```

Expected: 三個屬性全部匹配，`system.img` 非空。

### Task 3: 建立四台新 AVD 並套用乾淨基線

**Files:**
- Create externally: `/Users/hezhenyu/.android/avd/BIC_Min_API25_NoPlay_Compact.avd`
- Create externally: `/Users/hezhenyu/.android/avd/BIC_Main_API36_1_Play_360.avd`
- Create externally: `/Users/hezhenyu/.android/avd/BIC_Main_API36_1_NoPlay_Wide.avd`
- Create externally: `/Users/hezhenyu/.android/avd/BIC_Future_API37_1_Play_16K.avd`

**Interfaces:**
- Consumes: 四個 ARM64 system images 及 `pixel_2`／`pixel_9`／`pixel_8` profiles。
- Produces: 四個新名稱 AVD；舊 AVD 保持不變。

- [ ] **Step 1: 以官方 AVD manager 建立四台裝置**

```bash
printf 'no\n' | /opt/homebrew/bin/avdmanager create avd --force --name BIC_Min_API25_NoPlay_Compact --package "system-images;android-25;google_apis;arm64-v8a" --device pixel_2
printf 'no\n' | /opt/homebrew/bin/avdmanager create avd --force --name BIC_Main_API36_1_Play_360 --package "system-images;android-36.1;google_apis_playstore;arm64-v8a" --device pixel_9
printf 'no\n' | /opt/homebrew/bin/avdmanager create avd --force --name BIC_Main_API36_1_NoPlay_Wide --package "system-images;android-36.1;google_apis;arm64-v8a" --device pixel_9
printf 'no\n' | /opt/homebrew/bin/avdmanager create avd --force --name BIC_Future_API37_1_Play_16K --package "system-images;android-37.1;google_apis_playstore_ps16k;arm64-v8a" --device pixel_8
```

Expected: SDK emulator `-list-avds` 列出四個新名稱。如 `avdmanager` 無法解析 36.1／37.1，停止並先修復 command-line tools，不手工拼接不完整 AVD。

- [ ] **Step 2: 以 `apply_patch` 修改四個 `config.ini` 的精確鍵**

All four:

```text
hw.sdCard=no
fastboot.forceColdBoot=yes
fastboot.forceFastBoot=no
fastboot.forceChosenSnapshotBoot=no
snapshot.present=no
disk.dataPartition.size=6G
```

Role-specific:

```text
BIC_Min_API25_NoPlay_Compact: 1080×1920, density 480, PlayStore.enabled=no
BIC_Main_API36_1_Play_360: 1080×2400, density 480, PlayStore.enabled=true
BIC_Main_API36_1_NoPlay_Wide: 1080×2400, density 420, PlayStore.enabled=no
BIC_Future_API37_1_Play_16K: 1080×2400, density 440, PlayStore.enabled=true
```

Expected: 每個 `image.sysdir.1` 符合角色，第一次 boot 前沒有 `sdcard.img`。

### Task 4: 逐台冷啟動、安裝及角色驗收

**Files:**
- Read: `app/build/outputs/apk/debug/app-debug.apk`
- Read: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Temporary evidence: `/tmp/bic-avd-matrix/`

**Interfaces:**
- Consumes: 一次一個精確 AVD 名稱；Google Maps 案例使用 host `127.0.0.1:7890` 與 guest `10.0.2.2:7890`。
- Produces: 平台／顯示／Google package 證據、截圖、smoke 結果及乾淨關機。

- [ ] **Step 1: 逐台以冷啟動、不保存快照方式執行**

For each approved name, run one emulator and wait for `sys.boot_completed=1` before continuing:

```bash
/Users/hezhenyu/Library/Android/sdk/emulator/emulator -avd BIC_Min_API25_NoPlay_Compact -no-window -no-snapshot -wipe-data -no-boot-anim -gpu swiftshader_indirect
/Users/hezhenyu/Library/Android/sdk/emulator/emulator -avd BIC_Main_API36_1_Play_360 -no-window -no-snapshot -wipe-data -no-boot-anim -gpu swiftshader_indirect -http-proxy http://127.0.0.1:7890
/Users/hezhenyu/Library/Android/sdk/emulator/emulator -avd BIC_Main_API36_1_NoPlay_Wide -no-window -no-snapshot -wipe-data -no-boot-anim -gpu swiftshader_indirect
/Users/hezhenyu/Library/Android/sdk/emulator/emulator -avd BIC_Future_API37_1_Play_16K -no-window -no-snapshot -wipe-data -no-boot-anim -gpu swiftshader_indirect
```

Expected: 每台在有界等待內完成 boot；任何一台失敗時只關閉本次 instance 並停止清理舊資產。

- [ ] **Step 2: 每台收集平台、顯示及 Google 角色**

Resolve the only running serial into `BIC_SERIAL`, then run:

```bash
BIC_SERIAL=$(adb devices | awk '/^emulator-/{print $1; exit}')
test -n "$BIC_SERIAL"
adb -s "$BIC_SERIAL" shell getprop ro.build.version.release
adb -s "$BIC_SERIAL" shell getprop ro.build.version.sdk
adb -s "$BIC_SERIAL" shell getprop ro.product.cpu.abilist
adb -s "$BIC_SERIAL" shell wm size
adb -s "$BIC_SERIAL" shell wm density
adb -s "$BIC_SERIAL" shell getconf PAGESIZE
adb -s "$BIC_SERIAL" shell pm list packages com.google.android.gms
adb -s "$BIC_SERIAL" shell pm list packages com.android.vending
```

Expected: API、ARM64、尺寸及 GMS／Play Store state 符合矩陣；API 37.1 page size 為 `16384`。

- [ ] **Step 3: 安裝兩個 APK、解析並啟動主頁**

```bash
mkdir -p /tmp/bic-avd-matrix
adb -s "$BIC_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$BIC_SERIAL" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s "$BIC_SERIAL" shell cmd package resolve-activity --brief com.golink.busiscoming
adb -s "$BIC_SERIAL" shell am start -W -n com.golink.busiscoming/.ui.main.MainActivity
adb -s "$BIC_SERIAL" exec-out screencap -p > "/tmp/bic-avd-matrix/${BIC_SERIAL}.png"
```

Expected: install、resolve 及 start 全部成功，沒有 crash。

- [ ] **Step 4: 執行角色 smoke**

API 25、API 36.1 No Play、API 37.1:

```bash
adb -s "$BIC_SERIAL" shell am instrument -w -r -e class 'com.golink.busiscoming.TopLevelNavigationInstrumentedTest' com.golink.busiscoming.test/com.golink.busiscoming.BusIsComingTestRunner
```

API 36.1 No Play additionally:

```bash
adb -s "$BIC_SERIAL" shell am instrument -w -r -e class 'com.golink.busiscoming.AppUpdateInstrumentedTest#noPlayDeviceRoutesOnlyNonPlayInstallsToWebsiteWithoutInstallPermission' com.golink.busiscoming.test/com.golink.busiscoming.BusIsComingTestRunner
```

API 36.1 Play additionally, after `settings put global http_proxy 10.0.2.2:7890`:

```bash
adb -s "$BIC_SERIAL" shell am instrument -w -r -e runRealRouteMap true -e class 'com.golink.busiscoming.RouteDetailRealServiceInstrumentedTest#realN118GeometryAlignsWithGoogleRoadAtHighZoom' com.golink.busiscoming.test/com.golink.busiscoming.BusIsComingTestRunner
```

Expected: selected suites report `OK`; real map must not pass if `OnMapLoadedCallback` times out。

- [ ] **Step 5: 關閉 task-owned instance**

```bash
adb -s "$BIC_SERIAL" emu kill
adb devices -l
```

Expected: serial disappears before starting the next AVD。

### Task 5: 全部新角色通過後刪除舊資產

**Files:**
- Delete externally: five exact old AVD names。
- Delete externally via sdkmanager: `system-images;android-36;google_apis_playstore;arm64-v8a`。

**Interfaces:**
- Consumes: Task 4 全部通過及空 `adb devices` 清單。
- Produces: 只保留四台批准 AVD 及四個角色映像。

- [ ] **Step 1: 再次確認九台名稱與無執行中 emulator**

```bash
adb devices -l
/Users/hezhenyu/Library/Android/sdk/emulator/emulator -list-avds
```

Expected: no running emulator；四新、五舊名稱全部存在。

- [ ] **Step 2: 逐個刪除五台舊 AVD**

```bash
/opt/homebrew/bin/avdmanager delete avd --name BusIsComing_API_25
/opt/homebrew/bin/avdmanager delete avd --name Codex_Pin_QA_API_25
/opt/homebrew/bin/avdmanager delete avd --name Pixel_9_API_36
/opt/homebrew/bin/avdmanager delete avd --name Pixel_9_API_36_1
/opt/homebrew/bin/avdmanager delete avd --name Pixel_8
```

Expected: each exits 0；SDK emulator still lists all four new names and no old name。

- [ ] **Step 3: 卸載精確 API 36 Play 映像**

```bash
/opt/homebrew/bin/sdkmanager --sdk_root=/Users/hezhenyu/Library/Android/sdk --uninstall "system-images;android-36;google_apis_playstore;arm64-v8a"
```

Expected: exact API 36 path disappears；API 36.1 and 37.1 remain intact。

### Task 6: 最終稽核、回寫結果與提交

**Files:**
- Modify: `docs/avd-test-matrix.md`

**Interfaces:**
- Consumes: final AVD list、角色屬性、smoke results、disk sizes。
- Produces: reviewable final record and documentation-only commit。

- [ ] **Step 1: 核對最終外部狀態**

```bash
adb devices -l
/Users/hezhenyu/Library/Android/sdk/emulator/emulator -list-avds
du -sh /Users/hezhenyu/.android/avd
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-25/google_apis/arm64-v8a
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-36.1/google_apis/arm64-v8a
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-36.1/google_apis_playstore/arm64-v8a
du -sh /Users/hezhenyu/Library/Android/sdk/system-images/android-37.1/google_apis_playstore_ps16k/arm64-v8a
```

Expected: no emulator running；exactly four approved AVD names；four approved image paths readable；actual size recorded。

- [ ] **Step 2: 回寫實際結果並檢查 git 範圍**

Append date、sizes、properties、Google package state、page size、smoke commands and result to `docs/avd-test-matrix.md` without secrets。

```bash
git diff --check
git status --short
```

Expected: result document plus pre-existing `app/build.gradle.kts` only；no AVD or build product enters git。

- [ ] **Step 3: 提交驗證記錄**

```bash
git add docs/avd-test-matrix.md
git diff --cached --stat
git commit -m "docs: record Android AVD matrix validation"
```

Expected: documentation-only commit；`app/build.gradle.kts` remains unstaged。
