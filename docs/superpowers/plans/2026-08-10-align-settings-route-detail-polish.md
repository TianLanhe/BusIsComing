# Settings and Route Detail Polish Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 依最新確認結論交付精簡三語設定文案、雙下載入口、共用居中地圖控件、Lucide Route 圖標及不透明下車 marker，並讓未歸檔合同只存在於 `align-settings-route-detail-polish` delta specs。

**Architecture:** 設定文案仍由 Android locale resources 提供，分享協調重用 `AppUpdateLinks` 的 Play／網站 URL 權威；路線詳情以單一 MaterialButton style 固定三個控件的幾何屬性，既有 renderer 只調整下車 bitmap 繪製。OpenSpec 主 spec 保持已生效基線，proposal／design／delta specs／tasks 描述待歸檔行為。

**Tech Stack:** Kotlin、Android XML resources、Material Components、Google Maps marker bitmap、JUnit 4、AndroidX instrumentation、OpenSpec 1.6、Gradle 9.4.1。

## Global Constraints

- 產品定位使用「香港巴士通勤」，目前查詢能力明確寫 `Citybus`，不得暗示支援其他營運商。
- App 自有文字同時提供香港繁體、獨立簡體及自然英文；「行程」不得誤寫成保存路線。
- 分享格式參數固定 `%1$s = Google Play`、`%2$s = 本地化官方網站 #download`。
- 三個地圖控件保持 `48dp` 圓形、幾何居中的 `24dp` 圖標、既有位置／content description／點擊行為。
- 下車 marker 的路線色表面、對比白色外框與白色 `log-out` glyph alpha 均為 `255`。
- 不改 Citybus、ETA、CSDI、Google Maps 資料流、相機 bounds、目前位置、marker stable id 或其他 marker 角色。
- 只使用本任務新啟動且 API 36、360dp、Google Play 能力符合的 AVD；完成後關閉。
- 生效主 spec 不提前同步本 change；歸檔時才合併 delta specs。

---

### Task 1: 建立乾淨候選基線與提交 OpenSpec 制品

**Files:**
- Preserve: `docs/superpowers/specs/2026-08-10-settings-route-detail-five-point-alignment-design.md`
- Preserve: `openspec/changes/align-settings-route-detail-polish/proposal.md`
- Preserve: `openspec/changes/align-settings-route-detail-polish/design.md`
- Preserve: `openspec/changes/align-settings-route-detail-polish/specs/app-settings-support/spec.md`
- Preserve: `openspec/changes/align-settings-route-detail-polish/specs/route-detail-google-map/spec.md`
- Preserve: `openspec/changes/align-settings-route-detail-polish/tasks.md`
- Restore to `HEAD`: `app/src/main/java/com/golink/busiscoming/ui/main/GoogleRouteMapRenderer.kt`
- Restore to `HEAD`: `app/src/main/java/com/golink/busiscoming/ui/settings/AppSupportActions.kt`
- Restore to `HEAD`: `app/src/main/res/drawable/ic_route_overview.xml`
- Restore to `HEAD`: `app/src/main/res/layout/activity_route_detail.xml`
- Restore to `HEAD`: `app/src/main/res/raw/lucide_license.txt`
- Restore to `HEAD`: `app/src/main/res/values/strings.xml`
- Restore to `HEAD`: `app/src/main/res/values/strings_runtime.xml`
- Restore to `HEAD`: `app/src/main/res/values-b+zh+Hans/strings.xml`
- Restore to `HEAD`: `app/src/main/res/values-b+zh+Hans/strings_runtime.xml`
- Restore to `HEAD`: `app/src/main/res/values-en/strings.xml`
- Restore to `HEAD`: `app/src/main/res/values-en/strings_runtime.xml`
- Restore to `HEAD`: `app/src/test/java/com/golink/busiscoming/AppSettingsSupportContractTest.kt`
- Restore to `HEAD`: `app/src/test/java/com/golink/busiscoming/RouteDetailLayoutContractTest.kt`
- Restore to `HEAD`: `openspec/specs/app-settings-support/spec.md`
- Restore to `HEAD`: `openspec/specs/route-detail-google-map/spec.md`

**Interfaces:**
- Consumes: 已提交設計 `e166612` 與 change `align-settings-route-detail-polish`。
- Produces: 沒有探索候選 code/spec diff 的 TDD 起點；apply 行為只由 delta specs 驅動。

- [ ] **Step 1: 核對候選 diff 的精確來源**

Run:

```bash
git status --short
git diff -- app/src/main openspec/specs app/src/test
```

Expected: 只看到本輪已知的設定、路線詳情、測試及兩個主 spec 候選修改；change artifacts 為新增檔案。

- [ ] **Step 2: 用 `apply_patch` 逐個反向套用候選 hunk**

對上方列出的每個候選檔案逐個以 `git diff --` 加該精確路徑取得 hunk，將新增行移除、舊行恢復。不得使用 `git checkout --`、`git restore` 或覆蓋其他未列出檔案。完成後：

```bash
git diff --exit-code -- app/src/main app/src/test openspec/specs
```

Expected: exit 0；只剩 `openspec/changes/align-settings-route-detail-polish/` 與被忽略的 implementation plan 未提交。

- [ ] **Step 3: 驗證 OpenSpec 制品仍完整**

Run:

```bash
openspec status --change align-settings-route-detail-polish
openspec validate align-settings-route-detail-polish --strict --no-interactive
```

Expected: 4/4 artifacts complete；change valid。

- [ ] **Step 4: 提交 apply-ready 制品**

```bash
git add openspec/changes/align-settings-route-detail-polish
git add -f docs/superpowers/plans/2026-08-10-align-settings-route-detail-polish.md
git diff --cached --check
git commit -m "docs(openspec): propose settings and map polish alignment"
```

### Task 2: 以 RED→GREEN 更新關於我們與分享

**Files:**
- Modify: `app/src/test/java/com/golink/busiscoming/AppSettingsSupportContractTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-b+zh+Hans/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values/strings_runtime.xml`
- Modify: `app/src/main/res/values-b+zh+Hans/strings_runtime.xml`
- Modify: `app/src/main/res/values-en/strings_runtime.xml`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/settings/AppSupportActions.kt`

**Interfaces:**
- Consumes: `AppUpdateLinks.PLAY_HTTPS_URL: String`、`AppUpdateLinks.websiteDownloadPage(AppLanguage): String`、`AppLanguageRepository.snapshot().effectiveLanguage`。
- Produces: `AppSupportActions.websiteDownloadUrl(Context): String`；`shareText(Context)` 以 Play、網站順序格式化兩參數模板。

- [ ] **Step 1: 寫入會捕捉舊文案與單 URL 行為的 failing tests**

在 `AppSettingsSupportContractTest` 增加三語 about／share resource 讀取，使用手寫 literal 檢查：

```kotlin
private val localizedAboutStrings = listOf(
    File("src/main/res/values/strings.xml").readText() to
        "BusIsComing 為香港巴士通勤而設，助你比較 Citybus 路線與實時到站時間，更好掌握出發時機。\\n\\n你亦可儲存常用行程、查看地圖詳情及啟用通知欄監察。",
    File("src/main/res/values-b+zh+Hans/strings.xml").readText() to
        "BusIsComing 为香港公交通勤而设计，帮助你比较 Citybus 路线和实时到站时间，更好地掌握出发时机。\\n\\n你还可以保存常用行程、查看地图详情并启用通知栏监控。",
    File("src/main/res/values-en/strings.xml").readText() to
        "BusIsComing is built for Hong Kong bus commuters. Compare Citybus routes and live arrivals to choose a better time to leave.\\n\\nYou can also save regular journeys, view route details on the map, and monitor arrivals from your notifications."
)
```

逐一抽取 `about_description` 並 `assertEquals(expected, actual)`；分享模板分别断言一句核心价值、`%1$s`、`%2$s`、Play 标签先于网站标签，并断言不含候选长文案中的车费／步行／自动刷新列举。

- [ ] **Step 2: 運行 RED 並確認失敗原因**

Run:

```bash
./gradlew testDebugUnitTest --tests com.golink.busiscoming.AppSettingsSupportContractTest
```

Expected: FAIL；差異指向舊 `about_description`、舊單一網站 URL 或缺少 `websiteDownloadUrl`，而非編譯／語法錯誤。

- [ ] **Step 3: 實作最小三語資源與 URL 協調**

使用下列結構更新資源（簡體、英文採設計中已確認對應文案）：

```xml
<string name="about_description">BusIsComing 為香港巴士通勤而設，助你比較 Citybus 路線與實時到站時間，更好掌握出發時機。\n\n你亦可儲存常用行程、查看地圖詳情及啟用通知欄監察。</string>
<string name="share_copy">用 BusIsComing 比較 Citybus 路線與實時到站時間，掌握更合適的出發時機。\n\nGoogle Play 下載：%1$s\n官方網站下載：%2$s</string>
```

`AppSupportActions` 最小實作：

```kotlin
fun websiteDownloadUrl(context: Context): String =
    AppUpdateLinks.websiteDownloadPage(
        AppLanguageRepository(context).snapshot().effectiveLanguage
    )

fun shareText(context: Context): String = context.getString(
    R.string.share_copy,
    AppUpdateLinks.PLAY_HTTPS_URL,
    websiteDownloadUrl(context)
)
```

- [ ] **Step 4: 運行 GREEN 與相關設定測試**

```bash
./gradlew testDebugUnitTest --tests com.golink.busiscoming.AppSettingsSupportContractTest
```

Expected: PASS，沒有 Android resource formatting error。

- [ ] **Step 5: 勾選 OpenSpec 1.2、2.1–2.3 並提交**

```bash
git add app/src/main/java/com/golink/busiscoming/ui/settings/AppSupportActions.kt app/src/main/res/values app/src/main/res/values-b+zh+Hans app/src/main/res/values-en app/src/test/java/com/golink/busiscoming/AppSettingsSupportContractTest.kt openspec/changes/align-settings-route-detail-polish/tasks.md
git diff --cached --check
git commit -m "feat: refine app introduction and sharing"
```

### Task 3: 以 RED→GREEN 統一路線詳情地圖視覺

**Files:**
- Modify: `app/src/test/java/com/golink/busiscoming/RouteDetailLayoutContractTest.kt`
- Modify: `app/src/androidTest/java/com/golink/busiscoming/RouteDetailActivityTest.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/layout/activity_route_detail.xml`
- Modify: `app/src/main/res/drawable/ic_route_overview.xml`
- Modify: `app/src/main/res/raw/lucide_license.txt`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/main/GoogleRouteMapRenderer.kt`

**Interfaces:**
- Consumes: MaterialButton `iconSize`／padding／gravity，`RouteMapRenderPalette` 的 full-alpha route colors 與 `route_map_marker_outline`。
- Produces: `Widget.BusIsComing.RouteDetailMapControl`；Lucide Route vector；ALIGHTING fill→outline→white glyph 繪製順序。

- [ ] **Step 1: 寫 failing JVM contract tests**

新增 `themesSource` 與 `overviewIconSource`，讓測試要求：

```kotlin
val style = themesSource
    .substringAfter("<style name=\"Widget.BusIsComing.RouteDetailMapControl\"")
    .substringBefore("</style>")
assertTrue(style.contains("Widget.MaterialComponents.Button.Icon"))
assertTrue(style.contains("android:layout_width\">48dp"))
assertTrue(style.contains("android:layout_height\">48dp"))
assertTrue(style.contains("android:gravity\">center"))
assertTrue(style.contains("android:padding\">0dp"))
assertTrue(style.contains("iconSize\">24dp"))
```

三个按钮块须引用 `@style/Widget.BusIsComing.RouteDetailMapControl`，且不再各自重复 padding／iconSize。Route vector 须包含两端圆形与连接路径而不含旧扫描框 path。下车分支须包含 fill、outline 及 `palette.markerOutlineColor` glyph，并排除仅 `Paint.Style.STROKE` 的空心实现。

- [ ] **Step 2: 運行 JVM RED**

```bash
./gradlew testDebugUnitTest --tests com.golink.busiscoming.RouteDetailLayoutContractTest
```

Expected: FAIL；缺少共用 style、layout 仍使用旧 parent、overview 仍是扫描框、下车仍是空心圆环。

- [ ] **Step 3: 寫 instrumentation 屬性測試並確認可編譯**

在 `RouteDetailActivityTest` 使用既有 `intent(routeWithDetailQuery())` 啟動頁面，對 `routeDetailFloatingBack`、`routeDetailLocation`、`routeDetailOverview` 逐一斷言：

```kotlin
assertEquals((48f * density).toInt(), button.width)
assertEquals((48f * density).toInt(), button.height)
assertEquals((24f * density).toInt(), button.iconSize)
assertEquals(0, button.paddingLeft)
assertEquals(0, button.paddingRight)
assertEquals(Gravity.CENTER, button.gravity and Gravity.CENTER)
```

先運行 `compileDebugAndroidTestKotlin` 確認測試本身可編譯；舊實作的新行為差異已由 Step 2 的 JVM RED 證明，裝置只在實作後用於 GREEN，避免為相同失敗重複啟動 AVD。

- [ ] **Step 4: 實作共用 style、Route vector 與不透明 marker**

在 `themes.xml` 加入：

```xml
<style name="Widget.BusIsComing.RouteDetailMapControl"
    parent="Widget.MaterialComponents.Button.Icon">
    <item name="android:layout_width">48dp</item>
    <item name="android:layout_height">48dp</item>
    <item name="android:gravity">center</item>
    <item name="android:insetLeft">0dp</item>
    <item name="android:insetTop">0dp</item>
    <item name="android:insetRight">0dp</item>
    <item name="android:insetBottom">0dp</item>
    <item name="android:padding">0dp</item>
    <item name="backgroundTint">@color/bus_card_surface</item>
    <item name="cornerRadius">24dp</item>
    <item name="iconGravity">textStart</item>
    <item name="iconPadding">0dp</item>
    <item name="iconSize">24dp</item>
    <item name="iconTint">@color/bus_text_primary</item>
</style>
```

三个按钮改用该 style。`ic_route_overview.xml` 使用已确认 path：

```xml
android:pathData="M6,16a3,3 0,1 0,0 6a3,3 0,1 0,0 -6M9,19h8.5a3.5,3.5 0,0 0,0 -7h-11a3.5,3.5 0,0 1,0 -7H15M18,2a3,3 0,1 0,0 6a3,3 0,1 0,0 -6"
```

ALIGHTING 分支使用完整繪製順序：

```kotlin
canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, fill)
canvas.drawCircle(size / 2f, size / 2f, (right - left) / 2f, outline)
drawVector(
    canvas,
    R.drawable.ic_route_map_log_out,
    palette.markerOutlineColor,
    size,
    0.58f
)
```

Lucide license header 改為泛稱已使用的 Lucide icons，不聲稱只使用 `log-out`。

- [ ] **Step 5: 運行 GREEN、勾選 OpenSpec 1.3、3.1–3.4 並提交**

```bash
./gradlew testDebugUnitTest --tests com.golink.busiscoming.RouteDetailLayoutContractTest
ANDROID_SERIAL="$TASK_AVD_SERIAL" ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.RouteDetailActivityTest#mapControlsUseCenteredTwentyFourDpIcons
git add app/src/main app/src/test app/src/androidTest openspec/changes/align-settings-route-detail-polish/tasks.md
git diff --cached --check
git commit -m "feat: align route detail map affordances"
```

Expected: 两个定向命令均 PASS。

### Task 4: OpenSpec、自动化验证、完整构建与交付提交

**Files:**
- Modify: `openspec/changes/align-settings-route-detail-polish/tasks.md`
- Verify unchanged: `openspec/specs/app-settings-support/spec.md`
- Verify unchanged: `openspec/specs/route-detail-google-map/spec.md`

**Interfaces:**
- Consumes: Tasks 2–3 的 committed implementation 與既有 unit／instrumentation contracts。
- Produces: strict-valid、build-green、定向 instrumentation 通過的 apply-ready change；不建立截圖產物。

- [ ] **Step 1: 驗證主 spec 邊界與 OpenSpec**

```bash
git diff --exit-code -- openspec/specs/app-settings-support/spec.md openspec/specs/route-detail-google-map/spec.md
openspec validate align-settings-route-detail-polish --strict --no-interactive
openspec validate --all --strict --no-interactive
```

Expected: 主 spec 无 diff；change valid；全仓全部通过。

- [ ] **Step 2: 執行任務自有 AVD 定向 instrumentation**

只啟動任務開始時關閉且核驗為 `BIC_Main_API36_1_Play_360` 的 AVD，對唯一新 serial 顯式運行 `mapControlsUseCenteredTwentyFourDpIcons`。測試讀取三個實際 MaterialButton 的 `48dp` 尺寸、`24dp` iconSize、零 padding 及 center gravity；完成後關閉本任務設備。依使用者最新指示不建立或保存截圖。

- [ ] **Step 3: 執行完整驗證**

```bash
./gradlew testDebugUnitTest
./gradlew build
openspec instructions apply --change align-settings-route-detail-polish --json
```

Expected: Gradle commands exit 0；OpenSpec apply progress 只剩最终审计／提交 checkbox 或已全部完成。

- [ ] **Step 4: 更新 tasks、审计 diff 并创建最终提交**

逐项把 `tasks.md` 的已完成 checkbox 改为 `[x]`，然后：

```bash
git status --short
git diff --check
git diff --stat HEAD
git add openspec/changes/align-settings-route-detail-polish/tasks.md
git diff --cached --stat
git commit -m "test: verify settings and route detail polish"
```

- [ ] **Step 5: 运行完成门槛**

```bash
./gradlew build
openspec validate --all --strict --no-interactive
openspec status --change align-settings-route-detail-polish
git status --short
```

Expected: build exit 0；OpenSpec 全部 valid；change tasks 全部完成；工作树没有本 change 未提交文件。
