# 監控設定可靠性恢復實作計劃

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 恢復通知 channel 診斷與直接電池最佳化豁免，重整監控啟動面板，並修復步行時間無法減少。

**Architecture:** 把 Android framework 讀取與 Intent 啟動封裝在小型 manager／navigator，把 channel 健康、啟動順序及步行時間計算保留為純 policy，讓本地 JUnit 覆蓋決策。`MainActivity` 只保存一次啟動嘗試並按 policy 逐步協調通知、exact alarm、電池豁免與前台服務；Bottom Sheet 負責顯示及收集設定。

**Tech Stack:** Kotlin、Android SDK 25–36、XML、Material Components、AndroidX Core、JUnit 4、AndroidX Test／Espresso、OpenSpec。

## Global Constraints

- 不提高或遷移 `bus_monitor_status_v2`／`bus_monitor_alert_v2` 的預設 importance。
- 只把 App 通知關閉或 status channel 缺失／停用視為 blocking；warning／unknown 仍允許監控。
- 設定頁按 channel、App 通知、App 詳情順序回退，任何解析或啟動例外不得崩潰。
- 啟動順序固定為通知權限與 channel、exact alarm、電池豁免、前台服務；每項在單次嘗試最多提示一次。
- 拒絕或無法開啟電池豁免不得阻止基本監控。
- 面板標題區只保留標題，不展示路線副標題或 `monitor_explanation`。
- 步行時間最低 1 分鐘；手動偏移在步速及場景重算後保留。
- 新增或修改的 runtime 文案必須同時提供香港繁體、獨立簡體及自然英文。
- 所有核心操作有效觸控目標至少 48dp，並驗證 360dp、font scale 1.0／1.3／2.0、淺色／深色。
- 不使用或接管已開啟的模擬器；只啟動本任務自己的新實例，完成後關閉；若 AVD 被佔用則等待。

---

### Task 1: 以失敗測試固定純決策與步行計算

**Files:**
- Create: `app/src/test/java/com/golink/busiscoming/BusMonitorNotificationHealthPolicyTest.kt`
- Create: `app/src/test/java/com/golink/busiscoming/BusMonitorStartPolicyTest.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/BusMonitorSchedulingPolicyTest.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/BusMonitorModelsTest.kt`
- Create: `app/src/main/java/com/golink/busiscoming/service/BusMonitorNotificationHealth.kt`
- Create: `app/src/main/java/com/golink/busiscoming/ui/main/BusMonitorStartPolicy.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/service/BusMonitorSchedulingCapability.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/data/model/BusMonitorModels.kt`

**Interfaces:**
- Produces: `MonitorNotificationChannelSnapshot`, `MonitorNotificationSnapshot`, `MonitorNotificationHealth`, `MonitorNotificationHealthPolicy.evaluate(snapshot)`.
- Produces: `MonitorStartCapabilities`, `MonitorStartAttempt`, `MonitorStartStep`, `BusMonitorStartPolicy.nextStep(...)`.
- Produces: `WalkingTimeCalculator.estimate(..., manualAdjustmentMinutes: Int, ...)` and `WalkingTimeEstimate.manualAdjustmentMinutes`.
- Produces: `BusMonitorSchedulingCapability.shouldRequestBatteryOptimizationExemption(sdkInt, isIgnoring)` and `batteryOptimizationPackageUri(packageName)`.

- [ ] **Step 1: 寫 channel 健康 policy 的失敗測試**

```kotlin
@Test fun disabledStatusChannelBlocksAndTargetsStatusSettings() {
    val health = MonitorNotificationHealthPolicy.evaluate(
        MonitorNotificationSnapshot(
            sdkInt = 36,
            appNotificationsEnabled = true,
            status = MonitorNotificationChannelSnapshot(true, NotificationManager.IMPORTANCE_NONE, Notification.VISIBILITY_PUBLIC),
            alert = MonitorNotificationChannelSnapshot(true, NotificationManager.IMPORTANCE_DEFAULT, Notification.VISIBILITY_PUBLIC)
        )
    )
    assertEquals(MonitorNotificationSeverity.BLOCKING, health.severity)
    assertEquals(BusMonitorNotificationContract.STATUS_CHANNEL_ID, health.recommendedChannelId)
}
```

以獨立案例覆蓋 App 總開關關閉、status 缺失、alert 缺失／停用／低於 default、任一 channel 為 secret、正常 ready、Android 8 以下 unknown。

- [ ] **Step 2: 寫啟動順序與單次提示的失敗測試**

```kotlin
@Test fun advancesInNotificationExactBatteryServiceOrder() {
    val capabilities = MonitorStartCapabilities(
        notificationBlocking = true,
        canScheduleExactAlarm = false,
        ignoringBatteryOptimizations = false
    )
    assertEquals(MonitorStartStep.NOTIFICATION_SETTINGS, BusMonitorStartPolicy.nextStep(capabilities, MonitorStartAttempt()))
    assertEquals(MonitorStartStep.BLOCKED, BusMonitorStartPolicy.nextStep(capabilities, MonitorStartAttempt(notificationSettingsAttempted = true)))
}
```

另以非 blocking snapshot 驗證 exact alarm 已提示後轉到 battery、battery 已提示後轉到 service。

- [ ] **Step 3: 寫電池能力與 package URI 的失敗測試**

```kotlin
@Test fun requestsBatteryExemptionOnlyOnMarshmallowPlusWhenMissing() {
    assertFalse(BusMonitorSchedulingCapability.shouldRequestBatteryOptimizationExemption(22, false))
    assertTrue(BusMonitorSchedulingCapability.shouldRequestBatteryOptimizationExemption(23, false))
    assertFalse(BusMonitorSchedulingCapability.shouldRequestBatteryOptimizationExemption(36, true))
    assertEquals("package:com.golink.busiscoming", BusMonitorSchedulingCapability.batteryOptimizationPackageUri("com.golink.busiscoming"))
}
```

- [ ] **Step 4: 寫步行正負偏移與下限的失敗測試**

```kotlin
@Test fun manualNegativeAdjustmentCanReduceBelowDistanceEstimate() {
    val estimate = WalkingTimeCalculator.estimate(
        interfaceDistanceMeters = 420,
        straightLineDistanceMeters = 350,
        manualAdjustmentMinutes = -2,
        speedPreset = WalkingSpeedPreset.NORMAL,
        modifiers = emptySet()
    )
    assertEquals(6, estimate.interfaceDistanceMinutes)
    assertEquals(-2, estimate.manualAdjustmentMinutes)
    assertEquals(4, estimate.finalMinutes)
}
```

另驗證 `-99` 被限制到 1、正偏移、雨天重算及固定場景分鐘均與偏移同時生效。

- [ ] **Step 5: 運行測試並確認正確紅燈**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.golink.busiscoming.BusMonitorNotificationHealthPolicyTest' --tests 'com.golink.busiscoming.BusMonitorStartPolicyTest' --tests 'com.golink.busiscoming.BusMonitorSchedulingPolicyTest' --tests 'com.golink.busiscoming.BusMonitorModelsTest'
```

Expected: FAIL，原因是新 policy／欄位／函數尚不存在或舊 walking max 規則返回錯誤值。

- [ ] **Step 6: 實作最小純 policy 與計算公式**

```kotlin
val distanceBaseline = listOfNotNull(interfaceMinutes, straightLineMinutes, 1).maxOrNull() ?: 1
val finalMinutes = (distanceBaseline + extraMinutes + manualAdjustmentMinutes).coerceAtLeast(1)
```

`MonitorNotificationHealthPolicy` 先判斷 App／status blocking，再判斷 alert 及 secret warning，最後依 SDK 返回 ready／unknown。`BusMonitorStartPolicy` 對已嘗試但仍 blocking 的通知返回 `BLOCKED`，對已嘗試的 exact／battery 直接進入下一步。

- [ ] **Step 7: 重跑窄測試並確認綠燈**

Run: 與 Step 5 相同。

Expected: PASS。

- [ ] **Step 8: 更新 OpenSpec 任務 1.1、1.3、1.4、1.5、4.1 並提交**

```bash
git add app/src/main app/src/test openspec/changes/restore-monitor-notification-and-battery-settings/tasks.md
git commit -m "feat: add monitor readiness policies"
```

---

### Task 2: 實作 channel manager 與安全設定導航

**Files:**
- Create: `app/src/main/java/com/golink/busiscoming/service/BusMonitorNotificationChannelManager.kt`
- Create: `app/src/main/java/com/golink/busiscoming/ui/main/MonitorNotificationSettingsNavigator.kt`
- Create: `app/src/test/java/com/golink/busiscoming/MonitorNotificationSettingsNavigatorTest.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/service/BusMonitorService.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/BusMonitorNotificationContractTest.kt`

**Interfaces:**
- Consumes: `MonitorNotificationHealthPolicy.evaluate` and channel constants from Task 1.
- Produces: `BusMonitorNotificationChannelManager.ensureChannels()` and `readHealth()`.
- Produces: `MonitorNotificationSettingsRequest`, `MonitorNotificationSettingsNavigationResult`, `MonitorNotificationSettingsNavigator.open(channelId)`.

- [ ] **Step 1: 寫 navigator 回退的失敗測試**

```kotlin
@Test fun fallsBackFromChannelToAppNotificationsThenDetails() {
    val started = mutableListOf<MonitorNotificationSettingsRequest>()
    val navigator = MonitorNotificationSettingsNavigator(
        packageName = "com.golink.busiscoming",
        sdkInt = 36,
        canOpen = { it.kind != MonitorNotificationSettingsKind.CHANNEL },
        openRequest = { started += it }
    )
    assertEquals(MonitorNotificationSettingsNavigationResult.APP_NOTIFICATIONS, navigator.open("bus_monitor_status_v2"))
    assertEquals(MonitorNotificationSettingsKind.APP_NOTIFICATIONS, started.single().kind)
}
```

另覆蓋 channel 成功、App 詳情成功、resolver 例外、starter 例外與全部失敗返回 `MANUAL_GUIDANCE`。

- [ ] **Step 2: 運行 navigator 測試並確認紅燈**

```bash
./gradlew testDebugUnitTest --tests 'com.golink.busiscoming.MonitorNotificationSettingsNavigatorTest'
```

Expected: FAIL，navigator 尚不存在。

- [ ] **Step 3: 實作 request planner、navigator 與 Android Intent adapter**

```kotlin
data class MonitorNotificationSettingsRequest(
    val kind: MonitorNotificationSettingsKind,
    val action: String,
    val packageName: String,
    val channelId: String? = null,
    val dataUri: String? = null
)
```

Android adapter 對 channel request 加入 `Settings.EXTRA_APP_PACKAGE` 與 `Settings.EXTRA_CHANNEL_ID`；App 詳情使用 `package:` URI。`canOpen` 與 `openRequest` 都捕獲 `RuntimeException`。

- [ ] **Step 4: 實作 channel manager 並讓 service 共用**

```kotlin
class BusMonitorNotificationChannelManager(private val context: Context) {
    fun ensureChannels()
    fun readHealth(): MonitorNotificationHealth
}
```

manager 使用 `NotificationManagerCompat.from(context).areNotificationsEnabled()`，Android 8+ 建立及讀取兩個 channel；`BusMonitorService` 刪除私有 `ensureNotificationChannel()`，改呼叫 manager。

- [ ] **Step 5: 重跑 navigator 與通知契約測試**

```bash
./gradlew testDebugUnitTest --tests 'com.golink.busiscoming.MonitorNotificationSettingsNavigatorTest' --tests 'com.golink.busiscoming.BusMonitorNotificationContractTest'
```

Expected: PASS。

- [ ] **Step 6: 更新 OpenSpec 任務 1.2、2.1–2.4 並提交**

```bash
git add app/src/main app/src/test openspec/changes/restore-monitor-notification-and-battery-settings/tasks.md
git commit -m "feat: add monitor channel diagnostics"
```

---

### Task 3: 以 TDD 重整 Bottom Sheet 並修復步行 stepper

**Files:**
- Create: `app/src/main/res/layout/bottom_sheet_monitor_settings.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-v26/themes.xml`
- Modify: `app/src/main/res/values/strings_runtime.xml`
- Modify: `app/src/main/res/values-b+zh+Hans/strings_runtime.xml`
- Modify: `app/src/main/res/values-en/strings_runtime.xml`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/main/MonitorSettingsBottomSheet.kt`
- Create: `app/src/test/java/com/golink/busiscoming/MonitorSettingsLayoutContractTest.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/BusMonitorNotificationContractTest.kt`

**Interfaces:**
- Consumes: `MonitorNotificationHealth` and revised walking calculator.
- Produces: `MonitorSettingsBottomSheet.show(inputs, health)`, `updateNotificationHealth(health)`, `dismissAfterStart()`, `isShowing`.
- Produces callbacks: `onStart(MonitorSettingsResult)` and `onOpenNotificationSettings(MonitorNotificationHealth)`.

- [ ] **Step 1: 寫 XML 結構與文案移除的失敗契約測試**

測試用 XML parser 驗證：layout 存在；`NestedScrollView` 包含設定內容；`monitor_start_button` 不在 scroll 內；加減／設定／開始操作具有至少 48dp；存在 MaterialCardView 及 `bus_*` style；layout 不引用 route subtitle 或 `monitor_explanation`；三語資源不再定義 `monitor_explanation`。

- [ ] **Step 2: 運行 layout 與通知契約測試並確認紅燈**

```bash
./gradlew testDebugUnitTest --tests 'com.golink.busiscoming.MonitorSettingsLayoutContractTest' --tests 'com.golink.busiscoming.BusMonitorNotificationContractTest'
```

Expected: FAIL，layout／新介面尚不存在且舊 disclosure 契約仍要求 `monitor_explanation`。

- [ ] **Step 3: 建立單頁可滾動 layout 與穩定 style**

layout 以直向 root 包含 `NestedScrollView`（`layout_height=0dp`、`layout_weight=1`）和 scroll 外的 action container；步行卡使用約 8dp 圓角、`bus_surface_variant`／`bus_divider`；所有核心操作 `minWidth`／`minHeight` 至少 48dp。

- [ ] **Step 4: 改寫 Bottom Sheet 綁定與 inset**

```kotlin
private var manualAdjustmentMinutes = 0

private fun adjustManualMinutes(delta: Int) {
    val current = currentEstimate().finalMinutes
    if (delta < 0 && current <= 1) return
    manualAdjustmentMinutes += delta
    refreshEstimate()
}
```

使用 `ViewCompat.setOnApplyWindowInsetsListener` 把 navigation bar bottom inset 加到 action container 原始 padding。開始按鈕只回調，不自行 dismiss；真正啟動服務後由 Activity 呼叫 `dismissAfterStart()`。

- [ ] **Step 5: 更新三語文案及估算來源**

刪除 `monitor_explanation`；新增監控準備、就緒／需設定／請確認、前往設定／手動指引、步行增加／減少 content description、手動 `+N`／`−N` 來源等繁中／簡中／英文資源。

- [ ] **Step 6: 重跑 layout、通知、步行測試**

```bash
./gradlew testDebugUnitTest --tests 'com.golink.busiscoming.MonitorSettingsLayoutContractTest' --tests 'com.golink.busiscoming.BusMonitorNotificationContractTest' --tests 'com.golink.busiscoming.BusMonitorModelsTest'
```

Expected: PASS。

- [ ] **Step 7: 更新 OpenSpec 任務 1.6、3.1–3.5、4.2–4.3 並提交**

```bash
git add app/src/main app/src/test openspec/changes/restore-monitor-notification-and-battery-settings/tasks.md
git commit -m "feat: refine monitor settings sheet"
```

---

### Task 4: 串接返回重檢、順序協調與電池豁免

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/service/BusMonitorSchedulingCapability.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/BusMonitorNotificationContractTest.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/BusMonitorSchedulingPolicyTest.kt`
- Create: `app/src/test/java/com/golink/busiscoming/MonitorStartIntegrationContractTest.kt`

**Interfaces:**
- Consumes: channel manager, settings navigator, start policy and Bottom Sheet APIs from Tasks 1–3.
- `PendingMonitorStart` adds `MonitorStartAttempt` and `awaitingStep: MonitorStartStep?`.
- Produces: `advanceMonitorStart(start)` and `resumePendingMonitorStartAfterSettings()` in `MainActivity`.

- [ ] **Step 1: 寫 manifest 與 Activity 協調的失敗契約測試**

測試 AndroidManifest 宣告 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；Activity 使用 channel manager 健康結果、保存 attempt、在 `onResume` 重讀能力；啟動服務只存在於 `START_SERVICE` 分支；移除舊的 fire-and-forget `promptHighPriorityMonitorSettingsIfNeeded()`。

- [ ] **Step 2: 運行協調契約測試並確認紅燈**

```bash
./gradlew testDebugUnitTest --tests 'com.golink.busiscoming.MonitorStartIntegrationContractTest' --tests 'com.golink.busiscoming.BusMonitorNotificationContractTest' --tests 'com.golink.busiscoming.BusMonitorSchedulingPolicyTest'
```

Expected: FAIL，manifest 權限與新協調流程尚未接入。

- [ ] **Step 3: 恢復電池豁免 framework 能力**

```kotlin
fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

fun batteryOptimizationSettingsIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) null else
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse(batteryOptimizationPackageUri(context.packageName)))
```

Manifest 恢復對應 uses-permission。

- [ ] **Step 4: 實作 Activity 單次啟動協調**

`advanceMonitorStart` 先處理 runtime 通知權限，再讀 channel health 並詢問 `BusMonitorStartPolicy.nextStep`：

```kotlin
when (step) {
    NOTIFICATION_SETTINGS -> openNotificationSettings(start)
    BLOCKED -> showBlockingStateWithoutLoop()
    EXACT_ALARM -> openExactAlarmSettingsOnce(start)
    BATTERY_OPTIMIZATION -> explainAndRequestBatteryExemptionOnce(start)
    START_SERVICE -> startMonitorServiceAndClearPending(start)
}
```

任何 Intent 先 resolve，再捕獲 `ActivityNotFoundException`、`SecurityException`、`RuntimeException`。notification manual guidance 保持 blocking；exact／battery 啟動失敗標記已嘗試並降級繼續。

- [ ] **Step 5: 在 `onResume` 重檢而非依賴 result code**

僅當 `pendingMonitorStart.awaitingStep != null` 時清除 awaiting 並再次 `advanceMonitorStart`；面板正在顯示時總是刷新 channel health。通知設定修復後繼續 exact／battery；仍 blocking 則留在面板，不重開設定頁。

- [ ] **Step 6: 加入本地化電池說明與拒絕流程**

以 `MaterialAlertDialogBuilder` 先說明鎖屏刷新／語音及時性與耗電，再由肯定操作打開直接豁免頁；取消／拒絕把 battery 標記為已嘗試並繼續服務。

- [ ] **Step 7: 重跑協調與全部窄測試**

```bash
./gradlew testDebugUnitTest --tests 'com.golink.busiscoming.MonitorStartIntegrationContractTest' --tests 'com.golink.busiscoming.BusMonitorNotificationHealthPolicyTest' --tests 'com.golink.busiscoming.MonitorNotificationSettingsNavigatorTest' --tests 'com.golink.busiscoming.BusMonitorStartPolicyTest' --tests 'com.golink.busiscoming.BusMonitorSchedulingPolicyTest' --tests 'com.golink.busiscoming.BusMonitorModelsTest' --tests 'com.golink.busiscoming.MonitorSettingsLayoutContractTest'
```

Expected: PASS。

- [ ] **Step 8: 更新 OpenSpec 任務 3.6–3.8、5.1–5.5 並提交**

```bash
git add app/src/main app/src/test openspec/changes/restore-monitor-notification-and-battery-settings/tasks.md
git commit -m "feat: restore monitor system settings flow"
```

---

### Task 5: 完整自動化驗證與 OpenSpec 收尾

**Files:**
- Modify: `openspec/changes/restore-monitor-notification-and-battery-settings/tasks.md`

- [ ] **Step 1: 運行所有本地單元測試**

```bash
./gradlew testDebugUnitTest
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 運行 OpenSpec strict validation**

```bash
openspec validate restore-monitor-notification-and-battery-settings --strict
```

Expected: valid。

- [ ] **Step 3: 運行完整 Android build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL，包含 compile、unit tests、lint 及 debug／release assemble。

- [ ] **Step 4: 標記已完成的自動化任務**

把 6.1–6.3 改為 `[x]`；6.4 保留到設備驗證；6.5 保留到最終提交。

---

### Task 6: 只用本任務模擬器做人工驗證並提交

**Files:**
- Modify: `openspec/changes/restore-monitor-notification-and-battery-settings/tasks.md`

- [ ] **Step 1: 載入 Android emulator QA skill 並檢查佔用**

列出 `adb devices`、運行中的 emulator 與 AVD；任何已開啟實例都視為他人所有，不連接、不安裝、不操作。

- [ ] **Step 2: 啟動未被佔用的本任務新模擬器**

使用獨立 serial／port 記錄本任務擁有權；若沒有合適 AVD，最多每 60 秒回報並等待現有使用者釋放，之後由本任務自行啟動。

- [ ] **Step 3: 安裝並驗證核心流程**

驗證：繁中／簡中／英文、淺色／深色、360dp 與大字體；標題無副標題及介紹；步行 `−`／`+` 和 1 分鐘下限；notification app/channel blocking 與 warning；設定返回重檢；exact alarm 後才電池豁免；電池拒絕仍可開始監控。

- [ ] **Step 4: 關閉本任務模擬器並確認釋放**

只對記錄的 task-owned serial 執行 `adb -s <serial> emu kill`，再用 `adb devices` 確認消失；不關閉其他 serial。

- [ ] **Step 5: 更新任務、檢查範圍並提交**

```bash
git status --short
git diff --check
git diff --cached --stat
git commit -m "feat: restore monitor settings reliability"
```

所有 31 項完成後再回報 OpenSpec `31/31`，並提示可進行 archive。
