# Google Play 更新資格修正 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 刪除網站強制開關，阻止 Debug 構建產生假 Play 結果，並讓 `ERROR_APP_NOT_OWNED` 只有在網站證明存在較高版本時才形成可靠更新快照。

**Architecture:** `AppUpdateRuntime` 只負責把 `BuildConfig.DEBUG` 轉成可注入的 `playCheckSupported`，`AppUpdateCoordinator` 負責 Debug 短路、渠道決策與可靠快照，`PlayUpdateSource` 只封裝 Play Core。網站在一般無 Play 流程中仍是完整版本來源，但在 AppNotOwned 流程中只提供「有較高版本」的正向證據；結構化診斷事件透過可注入介面寫入本機 logcat。

**Tech Stack:** Kotlin、Android XML/AppCompat/Material Components、Google Play In-App Updates 2.1.0、SharedPreferences、JUnit、Android Gradle Plugin 9.2.1、OpenSpec。

## Global Constraints

- 保持 `applicationId=com.golink.busiscoming`、`minSdk=25`、`targetSdk=36`，不新增依賴或權限。
- `versionCode` 恢復並保持為 10；v10 基線由 Internal App Sharing／Play 正確簽名製品提供，不提交 APK 或 AAB。
- 刪除 `FORCE_WEBSITE_UPDATE_CHECK`、`DisabledPlayUpdateSource` 及 `forceWebsiteOnly`，但保留無 Play 非 Play 安裝的網站渠道。
- Debug 構建不得呼叫 Play package probe、Play source 或網站 source，不得產生可靠快照或小紅點。
- `UPDATE_AVAILABLE`／`UPDATE_NOT_AVAILABLE` 仍以 Play 為權威；Play 暫時失敗不得降級網站。
- AppNotOwned 的網站相等、較低、網絡失敗或非法 metadata 均回傳 `PLAY_APP_NOT_OWNED`，不得保存 `UP_TO_DATE`。
- 新增 App 可見文字必須同時提供香港繁體、獨立簡體與自然英文，不在 Kotlin／XML 硬編碼。
- 診斷不得記錄帳號、裝置識別、位置、使用者資料或完整外部響應，不新增遠端遙測。
- 保留既有可靠更新、defer、skip、小紅點、24 小時節流及 3 天提醒行為。
- 遵循 TDD：每個行為先寫失敗測試並確認失敗，再實作最小變更並確認通過。
- 最終必須運行 `./gradlew build`，並完成 IAS v10 → v11 flexible update 真實裝置流程後才關閉 TD-002。

---

## File Map

- `app/build.gradle.kts`：恢復 `versionCode=10`，刪除網站強制 BuildConfig 欄位。
- `app/src/main/java/com/golink/busiscoming/data/model/AppUpdateModels.kt`：新增 Debug 不支援失敗類型。
- `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateCoordinator.kt`：Debug 短路、AppNotOwned 網站正向證據、可靠失敗狀態與診斷事件。
- `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateRuntime.kt`：固定真實 Play 接線並注入構建資格與 logcat 診斷。
- `app/src/main/java/com/golink/busiscoming/data/update/PlayUpdateSource.kt`：刪除 disabled source、延後 listener 註冊並記錄 Play 原始結果。
- `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateDiagnostics.kt`：新增不含個人資料的結構化事件、介面、no-op 與 Android logcat 實作。
- `app/src/main/java/com/golink/busiscoming/ui/main/UpdateSettingsUiModel.kt`：區分手動失敗與自動保留摘要。
- `app/src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt`：手動不可驗證提示框及 Play 詳情頁操作。
- `app/src/main/res/values*/strings.xml`：三語失敗摘要、Dialog 與操作文案。
- `app/src/test/java/com/golink/busiscoming/AppUpdateCoordinatorTest.kt`：Debug、AppNotOwned、可靠快照與節流矩陣。
- `app/src/test/java/com/golink/busiscoming/AppUpdateDiagnosticsTest.kt`：結構化診斷與無敏感欄位測試。
- `app/src/test/java/com/golink/busiscoming/AppUpdateInfrastructureContractTest.kt`：刪除開關後的固定 Play 接線契約。
- `app/src/test/java/com/golink/busiscoming/UpdateSettingsUiModelTest.kt`：手動／自動失敗摘要優先級。
- `app/src/test/java/com/golink/busiscoming/AppUpdateUiContractTest.kt`：三語資源、可操作 Dialog 與 Play 兜底契約。
- `docs/app-update-check.md`、`docs/technical-debt.md`：更新目前行為、v11 發佈證據及 TD-002 關閉條件。
- `openspec/changes/add-app-update-check/{proposal.md,design.md,specs/app-update-check/spec.md,tasks.md}`：同步已批准的渠道與錯誤語義。

---

### Task 1: 刪除渠道開關並隔離 Debug 構建

**Files:**
- Modify: `app/build.gradle.kts:29-46`
- Modify: `app/src/main/java/com/golink/busiscoming/data/model/AppUpdateModels.kt:31-39`
- Modify: `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateCoordinator.kt:14-188`
- Modify: `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateRuntime.kt:8-33`
- Modify: `app/src/main/java/com/golink/busiscoming/data/update/PlayUpdateSource.kt:60-103`
- Create: `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateDiagnostics.kt`
- Test: `app/src/test/java/com/golink/busiscoming/AppUpdateCoordinatorTest.kt`
- Test: `app/src/test/java/com/golink/busiscoming/AppUpdateInfrastructureContractTest.kt`

**Interfaces:**
- Produces: `UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED`
- Produces: `AppUpdateCoordinator(installedVersionCode, stateStore, policy, playSource, websiteSource, playPackageProbe, installSourceReader, playCheckSupported, diagnostics, clock, callbackExecutor)` with `playCheckSupported: Boolean = true` and `diagnostics: AppUpdateDiagnostics = NoOpAppUpdateDiagnostics`.
- Produces: `AppUpdateDiagnosticEvent`、`AppUpdateDiagnostics`、`NoOpAppUpdateDiagnostics` 與 `LogcatAppUpdateDiagnostics`; Task 2 只負責接線與行為測試。

- [ ] **Step 1: Write failing coordinator tests for Debug short-circuit and throttling**

Add tests that inject `playCheckSupported = false`, counting fakes for the installer reader and Play package probe, and assert no source or probe executes:

```kotlin
@Test
fun debugBuildManualCheckFailsWithoutCallingAnyUpdateSource() {
    val play = FakePlayUpdateSource()
    val website = FakeWebsiteUpdateSource(
        WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
    )
    val probes = CountingUpdateEnvironment()
    val coordinator = coordinator(
        now = { 1_000_000_000L },
        play = play,
        website = website,
        playCheckSupported = false,
        environment = probes
    )

    assertTrue(coordinator.check(UpdateCheckTrigger.MANUAL))

    assertEquals(UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED,
        coordinator.currentState().lastFailure?.kind)
    assertEquals(0, play.checkCount)
    assertEquals(0, website.checkCount)
    assertEquals(0, probes.playPackageChecks)
    assertEquals(0, probes.installSourceReads)
    assertEquals(UpdateSnapshotState.NEVER_CHECKED,
        coordinator.currentState().snapshot.state)
}

@Test
fun debugBuildAutomaticFailureStillUsesTwentyFourHourThrottle() {
    var now = 1_000_000_000L
    val coordinator = coordinator(now = { now }, playCheckSupported = false)

    assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
    assertFalse(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
    now += UpdatePolicy.AUTO_CHECK_INTERVAL_MILLIS
    assertTrue(coordinator.check(UpdateCheckTrigger.AUTOMATIC))
}
```

Add this counting fake and let the test helper use it for both environment interfaces:

```kotlin
private class CountingUpdateEnvironment : PlayPackageProbe, InstallSourceReader {
    var playPackageChecks = 0
    var installSourceReads = 0

    override fun isPlayAvailable(): Boolean {
        playPackageChecks += 1
        return true
    }

    override fun installerPackageName(): String? {
        installSourceReads += 1
        return "com.android.vending"
    }
}
```

Extend the `coordinator(...)` helper with `playCheckSupported: Boolean = true`, `environment: CountingUpdateEnvironment? = null`, and `diagnostics: AppUpdateDiagnostics = NoOpAppUpdateDiagnostics`; use these exact production constructor arguments:

```kotlin
playPackageProbe = environment ?: object : PlayPackageProbe {
    override fun isPlayAvailable(): Boolean = playAvailable
},
installSourceReader = environment ?: object : InstallSourceReader {
    override fun installerPackageName(): String? = when (initialChannel) {
        InitialInstallChannel.PLAY -> "com.android.vending"
        InitialInstallChannel.NON_PLAY -> "com.android.packageinstaller"
        InitialInstallChannel.UNKNOWN_NON_PLAY -> null
    }
},
playCheckSupported = playCheckSupported,
diagnostics = diagnostics,
```

Delete the two website-only switch tests and replace their helper parameter `forceWebsiteOnly` with `playCheckSupported`.

- [ ] **Step 2: Write failing infrastructure tests proving the switch is gone**

Replace the current `BuildConfig.FORCE_WEBSITE_UPDATE_CHECK` assertions with:

```kotlin
@Test
fun runtimeAlwaysUsesPlaySourceAndInjectsDebugEligibility() {
    assertFalse(appBuild.contains("FORCE_WEBSITE_UPDATE_CHECK"))
    assertFalse(runtime.contains("DisabledPlayUpdateSource"))
    assertFalse(runtime.contains("forceWebsiteOnly"))
    assertTrue(runtime.contains("GooglePlayUpdateSource(applicationContext"))
    assertTrue(runtime.contains("playCheckSupported = !BuildConfig.DEBUG"))
}
```

- [ ] **Step 3: Run the focused tests and verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.golink.busiscoming.AppUpdateCoordinatorTest --tests com.golink.busiscoming.AppUpdateInfrastructureContractTest
```

Expected: FAIL because the new failure enum and `playCheckSupported` parameter do not exist and the switch is still present.

- [ ] **Step 4: Implement the minimal Debug eligibility and remove the switch**

Apply these model and coordinator changes:

```kotlin
enum class UpdateFailureKind {
    PLAY_UNAVAILABLE,
    PLAY_APP_NOT_OWNED,
    PLAY_DEBUG_BUILD_UNSUPPORTED,
    PLAY_TEMPORARY,
    NETWORK,
    INVALID_METADATA,
    EXTERNAL_ACTION,
    UNKNOWN
}
```

```kotlin
class AppUpdateCoordinator(
    private val installedVersionCode: Long,
    private val stateStore: UpdateStateStore,
    private val policy: UpdatePolicy,
    private val playSource: PlayUpdateSource,
    private val websiteSource: WebsiteUpdateSource,
    private val playPackageProbe: PlayPackageProbe,
    private val installSourceReader: InstallSourceReader,
    private val playCheckSupported: Boolean = true,
    private val diagnostics: AppUpdateDiagnostics = NoOpAppUpdateDiagnostics,
    private val clock: () -> Long = System::currentTimeMillis,
    private val callbackExecutor: Executor
) {
    init {
        val stored = stateStore.synchronizeInstalledVersion(installedVersionCode, clock())
        state = stored.toAppState()
        if (playCheckSupported) playSource.setDownloadedListener(::recordPlayDownloaded)
    }

    fun startFlexibleUpdate(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean =
        playCheckSupported && playSource.startFlexibleUpdate(activity, launcher)

    fun refreshPlayInstallStatus() {
        if (playCheckSupported) playSource.refreshInstallStatus()
    }

    fun completePlayUpdate(callback: (Boolean) -> Unit) {
        if (playCheckSupported) playSource.completeUpdate(callback) else callback(false)
    }
}
```

Inside the existing `check()` synchronized block, replace the initialization branch with exactly:

```kotlin
val initialized = if (playCheckSupported) {
    ensureInitialInstallChannel(stored)
} else {
    stored
}
```

After `publishCurrentState()` and the existing `if (attached) return true`, add:

```kotlin
if (!playCheckSupported) {
    completeFailure(UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED)
    return true
}
startChannelCheck()
return true
```

Move `manager.registerListener(installStateListener)` out of `GooglePlayUpdateSource.init`; register lazily in `setDownloadedListener()` so constructing the source in a Debug process does not call the Play listener API. Delete `DisabledPlayUpdateSource`.

Create the complete diagnostics boundary now so runtime wiring compiles independently:

```kotlin
sealed interface AppUpdateDiagnosticEvent {
    data class PlaySuccess(
        val updateAvailability: Int,
        val availableVersionCode: Int
    ) : AppUpdateDiagnosticEvent

    data class PlayFailure(val errorCode: Int?) : AppUpdateDiagnosticEvent

    data class ChannelDecision(
        val initialInstallChannel: InitialInstallChannel,
        val decision: UpdateChannelDecision
    ) : AppUpdateDiagnosticEvent

    data class CompletedFailure(val kind: UpdateFailureKind) : AppUpdateDiagnosticEvent
}

fun interface AppUpdateDiagnostics {
    fun record(event: AppUpdateDiagnosticEvent)
}

object NoOpAppUpdateDiagnostics : AppUpdateDiagnostics {
    override fun record(event: AppUpdateDiagnosticEvent) = Unit
}

object LogcatAppUpdateDiagnostics : AppUpdateDiagnostics {
    override fun record(event: AppUpdateDiagnosticEvent) {
        Log.i("AppUpdate", event.toString())
    }
}
```

In `AppUpdateRuntime`, construct a real source and inject eligibility:

```kotlin
val diagnostics = LogcatAppUpdateDiagnostics
val playSource = GooglePlayUpdateSource(applicationContext, diagnostics = diagnostics)
coordinator = AppUpdateCoordinator(
    installedVersionCode = installedVersionCode,
    stateStore = SharedPreferencesUpdateStateStore(
        applicationContext,
        installedVersionCode
    ),
    policy = UpdatePolicy(),
    playSource = playSource,
    websiteSource = HttpWebsiteUpdateSource(),
    playPackageProbe = AndroidPlayPackageProbe(applicationContext),
    installSourceReader = AndroidInstallSourceReader(applicationContext),
    playCheckSupported = !BuildConfig.DEBUG,
    diagnostics = diagnostics,
    callbackExecutor = { runnable -> mainHandler.post(runnable) }
)
```

Delete the BuildConfig field from `app/build.gradle.kts` and restore `versionCode = 10`.

- [ ] **Step 5: Run focused tests and verify success**

Run the Step 3 command.

Expected: PASS; the existing no-Play website tests must continue passing.

- [ ] **Step 6: Commit the isolated change**

```bash
git add app/build.gradle.kts app/src/main/java/com/golink/busiscoming/data/model/AppUpdateModels.kt app/src/main/java/com/golink/busiscoming/data/update/AppUpdateCoordinator.kt app/src/main/java/com/golink/busiscoming/data/update/AppUpdateRuntime.kt app/src/main/java/com/golink/busiscoming/data/update/PlayUpdateSource.kt app/src/main/java/com/golink/busiscoming/data/update/AppUpdateDiagnostics.kt app/src/test/java/com/golink/busiscoming/AppUpdateCoordinatorTest.kt app/src/test/java/com/golink/busiscoming/AppUpdateInfrastructureContractTest.kt
git commit -m "refactor: remove update channel switch"
```

---

### Task 2: 修正 AppNotOwned 證據語義並接入診斷

**Files:**
- Modify: `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateDiagnostics.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateCoordinator.kt:190-289`
- Modify: `app/src/main/java/com/golink/busiscoming/data/update/PlayUpdateSource.kt:90-122`
- Modify: `app/src/main/java/com/golink/busiscoming/data/update/AppUpdateRuntime.kt:12-31`
- Test: `app/src/test/java/com/golink/busiscoming/AppUpdateCoordinatorTest.kt`
- Create: `app/src/test/java/com/golink/busiscoming/AppUpdateDiagnosticsTest.kt`

**Interfaces:**
- Consumes: Task 1's `AppUpdateDiagnosticEvent`、`AppUpdateDiagnostics`、`playCheckSupported` and `PLAY_DEBUG_BUILD_UNSUPPORTED`
- Produces: Play source and coordinator calls that record every safe event.

- [ ] **Step 1: Write failing AppNotOwned matrix tests**

Add explicit tests for every non-positive website result:

```kotlin
@Test
fun appNotOwnedWithWebsiteUpToDateIsUnverifiableNotUpToDate() {
    val website = FakeWebsiteUpdateSource(
        WebsiteUpdateResult.UpToDate(
            UpdateSnapshot.upToDate(6L, UpdateChannel.WEBSITE, 1L)
        )
    )
    val coordinator = coordinator(
        now = { 1_000_000_000L },
        play = FakePlayUpdateSource(PlayUpdateResult.AppNotOwned),
        website = website
    )

    coordinator.check(UpdateCheckTrigger.MANUAL)

    assertEquals(UpdateFailureKind.PLAY_APP_NOT_OWNED,
        coordinator.currentState().lastFailure?.kind)
    assertEquals(UpdateSnapshotState.NEVER_CHECKED,
        coordinator.currentState().snapshot.state)
}

@Test
fun appNotOwnedKeepsRootFailureWhenWebsiteFails() {
    listOf(UpdateFailureKind.NETWORK, UpdateFailureKind.INVALID_METADATA).forEach { websiteKind ->
        val coordinator = coordinator(
            now = { 1_000_000_000L },
            play = FakePlayUpdateSource(PlayUpdateResult.AppNotOwned),
            website = FakeWebsiteUpdateSource(WebsiteUpdateResult.Failed(websiteKind))
        )
        coordinator.check(UpdateCheckTrigger.MANUAL)
        assertEquals(UpdateFailureKind.PLAY_APP_NOT_OWNED,
            coordinator.currentState().lastFailure?.kind)
    }
}
```

Extend the existing higher-version test to assert `snapshot.channel == PLAY`, `flexibleAllowed == false`, and `startFlexibleUpdate(...) == false`. Add a preloaded reliable update snapshot test that asserts the dot and version survive AppNotOwned failure.

- [ ] **Step 2: Write failing diagnostics tests**

Define a collecting fake and assert exact safe event fields:

```kotlin
@Test
fun playDiagnosticsExposeOnlyStatusVersionAndErrorCode() {
    val events = mutableListOf<AppUpdateDiagnosticEvent>()
    val diagnostics = AppUpdateDiagnostics(events::add)

    diagnostics.record(AppUpdateDiagnosticEvent.PlaySuccess(2, 11))
    diagnostics.record(AppUpdateDiagnosticEvent.PlayFailure(-10))

    assertEquals(
        listOf(
            AppUpdateDiagnosticEvent.PlaySuccess(2, 11),
            AppUpdateDiagnosticEvent.PlayFailure(-10)
        ),
        events
    )
    assertFalse(events.joinToString().contains("account", ignoreCase = true))
    assertFalse(events.joinToString().contains("device", ignoreCase = true))
}
```

In `AppUpdateCoordinatorTest`, extend the helper with a `diagnostics` parameter and add a test that cannot pass until coordinator wiring exists:

```kotlin
@Test
fun coordinatorRecordsChannelDecisionAndCompletedFailure() {
    val events = mutableListOf<AppUpdateDiagnosticEvent>()
    val coordinator = coordinator(
        now = { 1_000_000_000L },
        play = FakePlayUpdateSource(PlayUpdateResult.AppNotOwned),
        website = FakeWebsiteUpdateSource(
            WebsiteUpdateResult.Failed(UpdateFailureKind.NETWORK)
        ),
        diagnostics = AppUpdateDiagnostics(events::add)
    )

    coordinator.check(UpdateCheckTrigger.MANUAL)

    assertTrue(events.any {
        it == AppUpdateDiagnosticEvent.ChannelDecision(
            InitialInstallChannel.PLAY,
            UpdateChannelDecision.PLAY_WITH_WEBSITE_METADATA
        )
    })
    assertTrue(events.any {
        it == AppUpdateDiagnosticEvent.CompletedFailure(
            UpdateFailureKind.PLAY_APP_NOT_OWNED
        )
    })
}
```

- [ ] **Step 3: Run focused tests and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests com.golink.busiscoming.AppUpdateCoordinatorTest --tests com.golink.busiscoming.AppUpdateDiagnosticsTest
```

Expected: FAIL because UpToDate currently becomes reliable and coordinator does not record decision/failure events.

- [ ] **Step 4: Implement website positive-evidence semantics**

Change AppNotOwned handling to preserve a fallback failure:

```kotlin
UpdateChannelDecision.PLAY_WITH_WEBSITE_METADATA -> checkWebsite(
    resultChannel = UpdateChannel.PLAY,
    nonAvailableFailure = UpdateFailureKind.PLAY_APP_NOT_OWNED
)
```

```kotlin
private fun checkWebsite(
    resultChannel: UpdateChannel,
    nonAvailableFailure: UpdateFailureKind? = null
) {
    val checkedAt = clock()
    websiteSource.check(installedVersionCode, checkedAt) { result ->
        when (result) {
            is WebsiteUpdateResult.Available -> completeReliable(
                result.snapshot.copy(channel = resultChannel)
            )
            is WebsiteUpdateResult.UpToDate -> if (nonAvailableFailure == null) {
                completeReliable(result.snapshot.copy(channel = resultChannel))
            } else {
                completeFailure(nonAvailableFailure)
            }
            is WebsiteUpdateResult.Failed -> completeFailure(
                nonAvailableFailure ?: result.kind
            )
        }
    }
}
```

The ordinary no-Play website path calls the default argument and therefore keeps its reliable UpToDate semantics.

- [ ] **Step 5: Wire every structured diagnostic event**

Use the Task 1 diagnostics types. Record `PlaySuccess` and `PlayFailure` in `GooglePlayUpdateSource`; record the resolver `ChannelDecision` after every `UpdateChannelResolver.resolve(...)` call and `CompletedFailure` at the start of `completeFailure`. The exact source calls are:

```kotlin
diagnostics.record(
    AppUpdateDiagnosticEvent.PlaySuccess(
        updateAvailability = info.updateAvailability(),
        availableVersionCode = info.availableVersionCode()
    )
)
```

```kotlin
val errorCode = (error as? InstallException)?.errorCode
diagnostics.record(AppUpdateDiagnosticEvent.PlayFailure(errorCode))
```

```kotlin
diagnostics.record(
    AppUpdateDiagnosticEvent.ChannelDecision(initialChannel, decision)
)
```

Do not log metadata bodies, URLs beyond existing constants, stack traces, account data or package inventories.

- [ ] **Step 6: Run focused tests and verify success**

Run the Step 3 command.

Expected: PASS, including the existing website higher-version Play-channel test.

- [ ] **Step 7: Commit the isolated fix**

```bash
git add app/src/main/java/com/golink/busiscoming/data/update/AppUpdateDiagnostics.kt app/src/main/java/com/golink/busiscoming/data/update/AppUpdateCoordinator.kt app/src/main/java/com/golink/busiscoming/data/update/PlayUpdateSource.kt app/src/main/java/com/golink/busiscoming/data/update/AppUpdateRuntime.kt app/src/test/java/com/golink/busiscoming/AppUpdateCoordinatorTest.kt app/src/test/java/com/golink/busiscoming/AppUpdateDiagnosticsTest.kt
git commit -m "fix: preserve Play ownership uncertainty"
```

---

### Task 3: 提供手動不可驗證提示與正確摘要

**Files:**
- Modify: `app/src/main/java/com/golink/busiscoming/ui/main/UpdateSettingsUiModel.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt:185-218`
- Modify: `app/src/main/res/values/strings.xml:48-72`
- Modify: `app/src/main/res/values-b+zh+Hans/strings.xml:48-72`
- Modify: `app/src/main/res/values-en/strings.xml:48-72`
- Test: `app/src/test/java/com/golink/busiscoming/UpdateSettingsUiModelTest.kt`
- Test: `app/src/test/java/com/golink/busiscoming/AppUpdateUiContractTest.kt`

**Interfaces:**
- Consumes: `PLAY_DEBUG_BUILD_UNSUPPORTED` and `PLAY_APP_NOT_OWNED`
- Produces strings: `update_status_unverified`, `update_verification_failed_title`, `update_debug_build_unsupported_message`, `update_play_not_owned_message`, `update_action_open_play`, `update_action_cancel`
- Produces: `SettingsFragment.showPlayVerificationFailure(@StringRes messageRes: Int)`

- [ ] **Step 1: Write failing UI-model tests for manual and automatic failures**

```kotlin
@Test
fun manualUnverifiableFailureOverridesStaleUpToDateSummary() {
    val state = AppUpdateState(
        snapshot = UpdateSnapshot.upToDate(6L, UpdateChannel.PLAY, now),
        lastTrigger = UpdateCheckTrigger.MANUAL,
        lastFailure = UpdateFailure(UpdateFailureKind.PLAY_APP_NOT_OWNED)
    )
    assertEquals(
        R.string.update_status_unverified,
        UpdateSettingsUiModelFactory.create(state, now).summaryRes
    )
}

@Test
fun automaticFailureKeepsReliableUpToDateSummary() {
    val state = AppUpdateState(
        snapshot = UpdateSnapshot.upToDate(6L, UpdateChannel.PLAY, now),
        lastTrigger = UpdateCheckTrigger.AUTOMATIC,
        lastFailure = UpdateFailure(UpdateFailureKind.PLAY_APP_NOT_OWNED)
    )
    assertEquals(
        R.string.update_status_up_to_date,
        UpdateSettingsUiModelFactory.create(state, now).summaryRes
    )
}

@Test
fun automaticFailureKeepsNeverCheckedAndAvailableSummaries() {
    val neverChecked = AppUpdateState(
        snapshot = UpdateSnapshot.neverChecked(6L),
        lastTrigger = UpdateCheckTrigger.AUTOMATIC,
        lastFailure = UpdateFailure(UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED)
    )
    assertEquals(
        R.string.update_status_never_checked,
        UpdateSettingsUiModelFactory.create(neverChecked, now).summaryRes
    )

    val available = AppUpdateState(
        snapshot = availableSnapshot(),
        lastTrigger = UpdateCheckTrigger.AUTOMATIC,
        lastFailure = UpdateFailure(
            UpdateFailureKind.PLAY_APP_NOT_OWNED,
            retainedReliableSnapshot = true
        )
    )
    assertEquals(
        R.string.update_status_available,
        UpdateSettingsUiModelFactory.create(available, now).summaryRes
    )
    assertTrue(UpdateSettingsUiModelFactory.create(available, now).showDot)
}
```

Change the existing retained-failure assertion to set `lastTrigger = MANUAL`; a manually triggered failure over an `UPDATE_AVAILABLE` snapshot still uses `update_status_available_failed` and keeps the dot.

- [ ] **Step 2: Extend the three-language and Settings dialog contracts**

Add the six new string keys to `allUpdateStringsExistInThreeIndependentLocales()` and assert `SettingsFragment` contains `showPlayVerificationFailure`, `AppUpdateExternalActions.openPlayListing`, and both failure enum cases.

- [ ] **Step 3: Run focused UI tests and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests com.golink.busiscoming.UpdateSettingsUiModelTest --tests com.golink.busiscoming.AppUpdateUiContractTest
```

Expected: FAIL because the new resources and failure-priority behavior do not exist.

- [ ] **Step 4: Implement summary priority without hiding reliable updates**

Handle a manual retained update failure first; otherwise render the reliable snapshot without letting automatic failures replace it. Insert the manual unverifiable branch before `UP_TO_DATE`:

```kotlin
state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
    state.lastTrigger == UpdateCheckTrigger.MANUAL &&
    state.lastFailure != null -> R.string.update_status_available_failed
state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
    state.skippedVersionCode == state.snapshot.availableVersionCode ->
    R.string.update_status_available_skipped
state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE &&
    state.deferredVersionCode == state.snapshot.availableVersionCode &&
    state.deferredUntil?.let { now < it } == true ->
    R.string.update_status_available_deferred
state.snapshot.state == UpdateSnapshotState.UPDATE_AVAILABLE -> R.string.update_status_available
state.lastTrigger == UpdateCheckTrigger.MANUAL &&
    state.lastFailure?.kind in setOf(
        UpdateFailureKind.PLAY_APP_NOT_OWNED,
        UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED
    ) -> R.string.update_status_unverified
state.snapshot.state == UpdateSnapshotState.UP_TO_DATE ->
    R.string.update_status_up_to_date
state.snapshot.state == UpdateSnapshotState.NEVER_CHECKED &&
    state.lastTrigger == UpdateCheckTrigger.AUTOMATIC ->
    R.string.update_status_never_checked
```

Do not persist a replacement snapshot; this is presentation of the current process's `lastFailure` only.

- [ ] **Step 5: Add independently reviewed three-language copy**

Use these values:

```xml
<!-- values/strings.xml -->
<string name="update_status_unverified">暫時無法確認更新，點按重試</string>
<string name="update_verification_failed_title">無法確認 Google Play 更新</string>
<string name="update_debug_build_unsupported_message">目前偵錯版本無法驗證 Google Play 更新。請使用 Google Play 測試版本再試一次。</string>
<string name="update_play_not_owned_message">暫時無法透過 Google Play 確認更新。你可以前往 Google Play 查看。</string>
<string name="update_action_open_play">前往 Google Play</string>
<string name="update_action_cancel">取消</string>

<!-- values-b+zh+Hans/strings.xml -->
<string name="update_status_unverified">暂时无法确认更新，点按重试</string>
<string name="update_verification_failed_title">无法确认 Google Play 更新</string>
<string name="update_debug_build_unsupported_message">当前调试版本无法验证 Google Play 更新。请使用 Google Play 测试版本后重试。</string>
<string name="update_play_not_owned_message">暂时无法通过 Google Play 确认更新。你可以前往 Google Play 查看。</string>
<string name="update_action_open_play">前往 Google Play</string>
<string name="update_action_cancel">取消</string>

<!-- values-en/strings.xml -->
<string name="update_status_unverified">Unable to confirm updates. Tap to retry</string>
<string name="update_verification_failed_title">Google Play update unavailable</string>
<string name="update_debug_build_unsupported_message">This debug build can’t verify Google Play updates. Try again with a Google Play test build.</string>
<string name="update_play_not_owned_message">Google Play can’t confirm an update right now. You can check the app’s Google Play page.</string>
<string name="update_action_open_play">Open Google Play</string>
<string name="update_action_cancel">Cancel</string>
```

- [ ] **Step 6: Replace unverifiable Toasts with an actionable Dialog**

```kotlin
private fun showPlayVerificationFailure(@StringRes messageRes: Int) {
    val context = context ?: return
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.update_verification_failed_title)
        .setMessage(messageRes)
        .setNegativeButton(R.string.update_action_cancel, null)
        .setPositiveButton(R.string.update_action_open_play) { _, _ ->
            AppUpdateExternalActions.openPlayListing(context)
        }
        .show()
}
```

In `renderUpdateState`, only react after the manual request completes:

```kotlin
when (state.lastFailure?.kind) {
    UpdateFailureKind.PLAY_DEBUG_BUILD_UNSUPPORTED ->
        showPlayVerificationFailure(R.string.update_debug_build_unsupported_message)
    UpdateFailureKind.PLAY_APP_NOT_OWNED ->
        showPlayVerificationFailure(R.string.update_play_not_owned_message)
    UpdateFailureKind.PLAY_UNAVAILABLE ->
        Toast.makeText(requireContext(), R.string.update_play_unavailable, Toast.LENGTH_SHORT).show()
    null -> Unit
    else ->
        Toast.makeText(requireContext(), R.string.update_status_failed, Toast.LENGTH_SHORT).show()
}
```

Automatic failures never set `manualUpdateCheckRequested`, so they remain silent.

- [ ] **Step 7: Run focused UI tests and verify success**

Run the Step 3 command.

Expected: PASS in all three language resource contracts.

- [ ] **Step 8: Commit the UI change**

```bash
git add app/src/main/java/com/golink/busiscoming/ui/main/UpdateSettingsUiModel.kt app/src/main/java/com/golink/busiscoming/ui/main/SettingsFragment.kt app/src/main/res/values/strings.xml app/src/main/res/values-b+zh+Hans/strings.xml app/src/main/res/values-en/strings.xml app/src/test/java/com/golink/busiscoming/UpdateSettingsUiModelTest.kt app/src/test/java/com/golink/busiscoming/AppUpdateUiContractTest.kt
git commit -m "feat: explain unverifiable Play updates"
```

---

### Task 4: 同步 OpenSpec、發佈證據與技術債

**Files:**
- Modify: `openspec/changes/add-app-update-check/proposal.md`
- Modify: `openspec/changes/add-app-update-check/design.md`
- Modify: `openspec/changes/add-app-update-check/specs/app-update-check/spec.md`
- Modify: `openspec/changes/add-app-update-check/tasks.md`
- Modify: `docs/app-update-check.md`
- Modify: `docs/technical-debt.md`

**Interfaces:**
- Consumes: Tasks 1-3 verified runtime behavior and test names.
- Produces: Updated active OpenSpec requirements and remaining IAS hard gate.

- [ ] **Step 1: Remove the obsolete switch requirement and document Debug behavior**

Delete the entire OpenSpec requirement titled `系統保留本機網站渠道回退開關`. In the channel requirement add:

```markdown
#### Scenario: Debug 構建不宣稱 Play 已是最新
- **WHEN** 目前 App 為 debuggable 構建
- **AND** 系統發起自動或手動更新檢查
- **THEN** 系統 SHALL NOT 呼叫 Play package probe、Play 更新服務或網站 metadata
- **AND** 系統 SHALL NOT 保存可靠的已是最新或更新可用快照
- **AND** 手動檢查 SHALL 提供前往 Google Play 的受控提示
- **AND** 自動檢查 SHALL 保持靜默並保留 24 小時嘗試節流
```

- [ ] **Step 2: Make AppNotOwned positive-evidence semantics explicit**

Extend the existing AppNotOwned scenario with:

```markdown
- **AND** 網站 metadata 只有在 `versionCode` 高於目前 App 時 SHALL 形成可靠更新快照
- **AND** 網站版本相等、較低、請求失敗或 metadata 無效時 SHALL 回報 `PLAY_APP_NOT_OWNED`
- **AND** 系統 SHALL NOT 以這些非正向結果宣稱目前已是最新版本
```

Use this exact proposal statement and mirror it in the design decision section:

```markdown
- Google Play 上架後刪除本機網站強制開關；正常構建固定使用 Play 優先策略。網站渠道只保留給目前沒有可用官方 Play 的非 Play／未知非 Play 安裝，`ERROR_APP_NOT_OWNED` 只把網站較高版本當作正向證據。
```

- [ ] **Step 3: Add completed implementation tasks and preserve the real-device gate**

Remove obsolete tasks 3.6 and 3.7, then add these exact checked tasks in their responsibility sections:

```markdown
- [x] 3.6 Google Play 上架後刪除 `FORCE_WEBSITE_UPDATE_CHECK`、`DisabledPlayUpdateSource` 與 `forceWebsiteOnly` 平行接線；正常 runtime 固定建立 Play source，無 Play 非 Play 安裝的網站渠道保持不變。
- [x] 3.7 把 debuggable 構建短路為 `PLAY_DEBUG_BUILD_UNSUPPORTED`，不呼叫 installer／Play package／Play source／網站 source，手動提供 Play 恢復提示，自動失敗保留 24 小時節流。
- [x] 3.8 把 `ERROR_APP_NOT_OWNED` 的網站 metadata 限制為正向證據：只有較高版本形成 Play 渠道更新，相等、較低、網絡失敗或非法資料均回傳 `PLAY_APP_NOT_OWNED`。
- [x] 4.7 新增不含個人資料的結構化 `AppUpdate` 本機診斷，記錄 Play availability／versionCode／errorCode、初始渠道、渠道決策與失敗類型。
- [x] 5.6 新增 Debug 不支援與 AppNotOwned 的三語可操作 Dialog；手動失敗不顯示舊「已是最新」，自動失敗不覆蓋可靠摘要。
- [x] 6.6 以 JVM 與 UI 契約測試覆蓋 Debug 短路、AppNotOwned 網站矩陣、歷史快照保留、手動／自動摘要及 Play 詳情頁兜底。
```

Keep task 7.2 unchecked until the IAS sequence actually completes. Replace 7.4 with the verified v11 size/applicationId/signature evidence and replace 7.5 with the deterministic AppNotOwned matrix plus optional real-account evidence boundary; mark them checked only after Tasks 1-3 tests pass.

- [ ] **Step 4: Update operational documentation with current v11 evidence**

In `docs/app-update-check.md`, remove the website switch section and record:

```text
versionCode=11
versionName=1.0
sizeBytes=6094814
applicationId=com.golink.busiscoming
signing SHA-256=33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58
```

Rewrite TD-002 status and current impact to these exact statements:

```markdown
- **狀態**：網站強制開關已刪除，等待真實 IAS flexible flow 驗收後關閉
- **目前影響**：正常構建固定使用 Google Play 優先分流；Debug 構建不宣稱 Play 已是最新。v11 網站 signed universal APK 與 metadata 已完成發佈鏈驗證，目前只餘 IAS v10 → v11 flexible update 真實裝置門檻。
```

Do not mark TD-002 closed until Task 5's real-device flow passes.

- [ ] **Step 5: Scan documentation for stale switch references**

```bash
rg -n "FORCE_WEBSITE_UPDATE_CHECK|forceWebsiteOnly|DisabledPlayUpdateSource" app docs/app-update-check.md docs/technical-debt.md openspec/changes/add-app-update-check
```

Expected: no matches; the approved brainstorming design and implementation plan under ignored `docs/superpowers/` may describe the removed symbols as historical context and are intentionally outside this scan.

- [ ] **Step 6: Commit documentation and OpenSpec changes**

```bash
git add docs/app-update-check.md docs/technical-debt.md openspec/changes/add-app-update-check/proposal.md openspec/changes/add-app-update-check/design.md openspec/changes/add-app-update-check/specs/app-update-check/spec.md openspec/changes/add-app-update-check/tasks.md
git commit -m "docs: align Play update verification"
```

---

### Task 5: 完整構建、線上製品與 IAS 真機驗收

**Files:**
- Modify after successful evidence only: `openspec/changes/add-app-update-check/tasks.md`
- Modify after successful evidence only: `docs/technical-debt.md`

**Interfaces:**
- Consumes: all runtime, UI, diagnostics and documentation deliverables from Tasks 1-4.
- Produces: build evidence, online artifact evidence and completed IAS v10 → v11 evidence.

- [ ] **Step 1: Run the complete update-focused unit suite**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.golink.busiscoming.AppUpdate*' --tests com.golink.busiscoming.UpdateSettingsUiModelTest
```

Expected: PASS with no skipped update tests.

- [ ] **Step 2: Run the required full Android build**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`, covering Kotlin compilation, unit tests, lint and debug/release assembly.

- [ ] **Step 3: Revalidate the deployed metadata and APK**

Use a temporary directory outside the repository, then verify:

```bash
curl --fail --silent --show-error --dump-header - https://www.busiscoming.com/api/downloads/android/latest/metadata
curl --fail --location --silent --show-error --output /tmp/bus-update-v11/BusIsComing.apk https://www.busiscoming.com/api/downloads/android/latest
apkanalyzer manifest application-id /tmp/bus-update-v11/BusIsComing.apk
apkanalyzer manifest version-code /tmp/bus-update-v11/BusIsComing.apk
apkanalyzer manifest version-name /tmp/bus-update-v11/BusIsComing.apk
apksigner verify --print-certs /tmp/bus-update-v11/BusIsComing.apk
```

Expected: HTTP 200, `Cache-Control: no-store`, size `6094814`, application ID `com.golink.busiscoming`, versionCode 11, versionName 1.0 and signing SHA-256 `33:D0:...:8D:58`.

- [ ] **Step 4: Perform the official IAS v10 → v11 device sequence**

1. Use the tester account that has acquired the App from Play.
2. Install v10 through its Internal App Sharing URL.
3. Open the v11 Internal App Sharing URL but do not install it.
4. Open v10 from the launcher and tap「檢查更新」.
5. Confirm logcat contains a safe `PlaySuccess` event with available versionCode 11.
6. Start flexible update, cancel/return once, confirm the App remains usable, then retry.
7. Complete download and confirm「重新啟動並安裝」.
8. Install v11 and relaunch.
9. Confirm the old update snapshot, red dot, defer and skip state are cleared.
10. Exercise the Play listing fallback when flexible flow cannot start.

Expected: every step passes on the connected physical device; mock evidence cannot replace this sequence.

- [ ] **Step 5: Close TD-002 only after IAS evidence exists**

Mark OpenSpec task 7.2 checked and update TD-002 to `已關閉`, recording date `2026-08-03`, v10 → v11, Internal App Sharing, physical device and the completed flexible flow. If the external IAS setup is unavailable, leave both items open and report the exact missing link/account state instead of claiming completion.

- [ ] **Step 6: Run final repository checks**

```bash
git status --short
git diff --check
git diff --cached --stat
```

Expected: no build products; only intended evidence documentation may remain staged. Confirm `versionCode=10` and no `FORCE_WEBSITE_UPDATE_CHECK` references in active runtime or OpenSpec.

- [ ] **Step 7: Commit final verification evidence if changed**

```bash
git add docs/technical-debt.md openspec/changes/add-app-update-check/tasks.md
git commit -m "docs: record Play update validation"
```

Skip this commit when no evidence files changed. Do not amend or rewrite the earlier scoped commits.
