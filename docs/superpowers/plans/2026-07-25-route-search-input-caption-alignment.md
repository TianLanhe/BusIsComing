# 路線頁輸入提示與工具居中 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 僅在路線頁把地點提示移到輸入框上邊框，按狀態互斥顯示三語短文案，並讓定位、載入及起終點交換工具按實際輸入區坐標精確居中。

**Architecture:** 共用 `PlaceInputController` 新增可選、結構化的訊息輸出；未提供時繼續使用原有 helper/error 路徑，確保行程新增、編輯及複製頁不變。路線頁以純 Kotlin 狀態機決定唯一提示，再由 `SearchFieldCaptionRenderer` 更新 TextInputLayout 的常駐折疊 hint；`PlacePairEditorView` 只負責按實際 View 坐標對齊工具槽。

**Tech Stack:** Kotlin、Android XML、AppCompat、Material Components `TextInputLayout`、JUnit、AndroidX Test／Espresso。

## Global Constraints

- 改動只適用於路線頁；不得改變 `activity_route_edit.xml` 或 `RouteEditActivity` 的輸入框展示與互動。
- App runtime 新增文案必須同時提供香港繁體、獨立簡體及自然英文。
- 同一欄位只顯示一個狀態，優先級固定為：校驗錯誤 > 搜尋失敗／無匹配 > 自動定位失敗 > Google Maps attribution > 從清單選擇 > 無狀態。
- 定位／載入工具中心與起點輸入區中心、交換工具中心與折疊態兩欄中心平均值的 Y 誤差不得超過 `1dp`。
- 候選清單開合不得令交換工具跳動；不得用固定 `120dp`、裝置特判或人工光學偏移。
- 可見文案只可在設計文件指定的完整與緊湊版本間切換；不得縮字、裁切或恢復輸入框下方 helper。
- 保留現有未提交的 `app/build.gradle.kts` 修改，不得把它加入本變更提交。
- 模擬器驗證只可啟動驗證開始前未運行的 AVD；完成後必須關閉本次啟動的模擬器。

---

### Task 1: 建立路線欄位提示狀態模型與三語資源

**Files:**
- Create: `app/src/main/java/com/golink/busiscoming/ui/common/PlaceInputMessage.kt`
- Create: `app/src/main/java/com/golink/busiscoming/ui/main/SearchFieldCaptionState.kt`
- Create: `app/src/test/java/com/golink/busiscoming/SearchFieldCaptionStateTest.kt`
- Modify: `app/src/main/res/values/strings_runtime.xml`
- Modify: `app/src/main/res/values-b+zh+Hans/strings_runtime.xml`
- Modify: `app/src/main/res/values-en/strings_runtime.xml`

**Interfaces:**
- Produces: `enum class PlaceInputMessage { NONE, INSTRUCTION, NO_MATCHES, SEARCH_FAILED }`
- Produces: `enum class SearchFieldValidation { MISSING_PLACE, SAME_AS_ORIGIN }`
- Produces: `enum class SearchFieldCaptionStatus`
- Produces: `SearchFieldCaptionState.onPlaceInputMessage(message)`, `setGoogleMaps(Boolean)`, `setLocationFailure(Boolean)`, `setValidation(SearchFieldValidation?)`, `visibleStatus()`

- [x] **Step 1: 寫入狀態優先級失敗測試**

```kotlin
class SearchFieldCaptionStateTest {
    @Test
    fun `caption priority is validation search location google instruction then none`() {
        val state = SearchFieldCaptionState()
        assertEquals(SearchFieldCaptionStatus.INSTRUCTION, state.visibleStatus())

        state.setGoogleMaps(true)
        assertEquals(SearchFieldCaptionStatus.GOOGLE_MAPS, state.visibleStatus())

        state.setLocationFailure(true)
        assertEquals(SearchFieldCaptionStatus.LOCATION_FAILURE, state.visibleStatus())

        state.onPlaceInputMessage(PlaceInputMessage.NO_MATCHES)
        assertEquals(SearchFieldCaptionStatus.NO_MATCHES, state.visibleStatus())

        state.setValidation(SearchFieldValidation.MISSING_PLACE)
        assertEquals(SearchFieldCaptionStatus.MISSING_PLACE, state.visibleStatus())

        state.setValidation(null)
        state.onPlaceInputMessage(PlaceInputMessage.NONE)
        state.setLocationFailure(false)
        state.setGoogleMaps(false)
        assertNull(state.visibleStatus())
    }
}
```

- [x] **Step 2: 執行測試並確認因類別尚不存在而失敗**

Run:

```bash
./gradlew testDebugUnitTest --tests com.golink.busiscoming.SearchFieldCaptionStateTest
```

Expected: `compileDebugUnitTestKotlin` 失敗，指出 `SearchFieldCaptionState`／`PlaceInputMessage` 尚未定義。

- [x] **Step 3: 實作最小結構化訊息與狀態機**

```kotlin
enum class PlaceInputMessage {
    NONE,
    INSTRUCTION,
    NO_MATCHES,
    SEARCH_FAILED
}
```

```kotlin
internal enum class SearchFieldValidation {
    MISSING_PLACE,
    SAME_AS_ORIGIN
}

internal enum class SearchFieldCaptionStatus {
    INSTRUCTION,
    GOOGLE_MAPS,
    NO_MATCHES,
    SEARCH_FAILED,
    LOCATION_FAILURE,
    MISSING_PLACE,
    SAME_AS_ORIGIN;

    val isError: Boolean
        get() = this == SEARCH_FAILED ||
            this == MISSING_PLACE ||
            this == SAME_AS_ORIGIN
}

internal class SearchFieldCaptionState {
    private var inputMessage = PlaceInputMessage.INSTRUCTION
    private var usesGoogleMaps = false
    private var locationFailure = false
    private var validation: SearchFieldValidation? = null

    fun onPlaceInputMessage(message: PlaceInputMessage) {
        inputMessage = message
        locationFailure = false
        validation = null
    }

    fun setGoogleMaps(value: Boolean) {
        usesGoogleMaps = value
    }

    fun setLocationFailure(value: Boolean) {
        locationFailure = value
    }

    fun setValidation(value: SearchFieldValidation?) {
        validation = value
    }

    fun visibleStatus(): SearchFieldCaptionStatus? =
        when (validation) {
            SearchFieldValidation.MISSING_PLACE -> SearchFieldCaptionStatus.MISSING_PLACE
            SearchFieldValidation.SAME_AS_ORIGIN -> SearchFieldCaptionStatus.SAME_AS_ORIGIN
            null -> when (inputMessage) {
                PlaceInputMessage.SEARCH_FAILED -> SearchFieldCaptionStatus.SEARCH_FAILED
                PlaceInputMessage.NO_MATCHES -> SearchFieldCaptionStatus.NO_MATCHES
                else -> when {
                    locationFailure -> SearchFieldCaptionStatus.LOCATION_FAILURE
                    usesGoogleMaps -> SearchFieldCaptionStatus.GOOGLE_MAPS
                    inputMessage == PlaceInputMessage.INSTRUCTION ->
                        SearchFieldCaptionStatus.INSTRUCTION
                    else -> null
                }
            }
        }
}
```

- [x] **Step 4: 新增完整與緊湊三語資源**

新增 `search_field_origin_label`、`search_field_destination_label`，以及下表完整／
`_compact` 成對資源：

| Resource | 香港繁體 | 簡體中文 | English |
| --- | --- | --- | --- |
| `search_field_origin_label` | 起點 | 起点 | From |
| `search_field_destination_label` | 終點 | 终点 | To |
| `search_field_choose_from_list` | 從清單選擇 | 从列表选择 | Choose from list |
| `search_field_choose_from_list_compact` | 選擇地點 | 选择地点 | Select |
| `search_field_google_maps_address` | 地址由 Google Maps 提供 | 地址由 Google Maps 提供 | Google Maps address |
| `search_field_google_maps_address_compact` | Google 地址 | Google 地址 | By Google |
| `search_field_no_matches` | 找不到配對地點 | 没有匹配地点 | No matches |
| `search_field_no_matches_compact` | 無配對 | 无匹配 | No match |
| `search_field_search_failed` | 搜尋失敗 | 搜索失败 | Search failed |
| `search_field_search_failed_compact` | 搜尋失敗 | 搜索失败 | Failed |
| `search_field_location_failure` | 定位失敗，請手動選擇 | 定位失败，请手动选择 | Location unavailable |
| `search_field_location_failure_compact` | 定位失敗 | 定位失败 | No location |
| `search_field_choose_place` | 請選擇地點 | 请选择地点 | Choose a place |
| `search_field_choose_place_compact` | 選擇地點 | 选择地点 | Select |
| `search_field_same_as_origin` | 不能與起點相同 | 不能与起点相同 | Must differ from start |
| `search_field_same_as_origin_compact` | 與起點相同 | 与起点相同 | Same start |

- [x] **Step 5: 執行狀態測試並確認通過**

Run:

```bash
./gradlew testDebugUnitTest --tests com.golink.busiscoming.SearchFieldCaptionStateTest
```

Expected: `BUILD SUCCESSFUL`。

- [x] **Step 6: 提交狀態模型與資源**

```bash
git add app/src/main/java/com/golink/busiscoming/ui/common/PlaceInputMessage.kt \
  app/src/main/java/com/golink/busiscoming/ui/main/SearchFieldCaptionState.kt \
  app/src/test/java/com/golink/busiscoming/SearchFieldCaptionStateTest.kt \
  app/src/main/res/values/strings_runtime.xml \
  app/src/main/res/values-b+zh+Hans/strings_runtime.xml \
  app/src/main/res/values-en/strings_runtime.xml
git commit -m "feat: model route search field captions"
```

### Task 2: 讓共用地點控制器可選擇輸出結構化訊息

**Files:**
- Modify: `app/src/main/java/com/golink/busiscoming/ui/common/PlaceInputController.kt`
- Modify: `app/src/androidTest/java/com/golink/busiscoming/PlaceInputControllerInstrumentedTest.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/PlaceInputInlineCandidatesContractTest.kt`

**Interfaces:**
- Consumes: `PlaceInputMessage`
- Produces: optional constructor parameter `onMessageChanged: ((PlaceInputMessage) -> Unit)? = null`
- Preserves: without `onMessageChanged`, `clearMessages()` restores the historical helper and `setError()` uses TextInputLayout error.

- [x] **Step 1: 新增訊息輸出與舊頁不變的失敗測試**

在 `PlaceInputControllerInstrumentedTest` 建立帶 `onMessageChanged` 的控制器與可返回
空結果、失敗和成功結果的 repository，依序斷言：

```kotlin
assertEquals(PlaceInputMessage.NONE, messages.last())
assertEquals(PlaceInputMessage.NO_MATCHES, messages.last())
assertEquals(PlaceInputMessage.SEARCH_FAILED, messages.last())
assertEquals(PlaceInputMessage.INSTRUCTION, messages.last())
```

保留並重跑既有 `routeEditKeepsHistoricalInputGeometryAndMaterialLocationTool`，確認未提供
callback 時兩個行程編輯欄仍顯示 `place_search_helper`。

- [x] **Step 2: 在專用未啟動 AVD 上執行測試並確認 callback 缺失造成失敗**

先重新確認 `Pixel_9_API_36_1` 未運行且 `emulator-5556` 未被佔用；若被其他工作啟動，
按用戶要求等待它關閉，最長兩小時。確認可用後：

```bash
route_ui_qa_serial=emulator-5556
/Users/hezhenyu/Library/Android/sdk/emulator/emulator \
  @Pixel_9_API_36_1 -port 5556 -no-snapshot-save -no-boot-anim \
  > /tmp/busiscoming-route-ui-emulator.log 2>&1 &
adb -s "$route_ui_qa_serial" wait-for-device
adb -s "$route_ui_qa_serial" shell getprop sys.boot_completed
```

最後一條命令輸出 `1` 後才執行測試。後續 instrumentation 只使用
`emulator-5556`；驗證前已存在的 `emulator-5554` 不得用於本變更。

Run:

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.PlaceInputControllerInstrumentedTest
```

Expected: 新測試在編譯或斷言階段失敗；既有行程編輯測試仍通過。

- [x] **Step 3: 實作可選輸出且保留舊路徑**

在 constructor 末端加入：

```kotlin
private val onMessageChanged: ((PlaceInputMessage) -> Unit)? = null
```

集中以下小方法，避免在搜尋 callback 散落分支：

```kotlin
private fun showInstruction() {
    onMessageChanged?.invoke(PlaceInputMessage.INSTRUCTION)
        ?: run { inputLayout.helperText = defaultInstructionText }
}

private fun showNoMatches() {
    onMessageChanged?.invoke(PlaceInputMessage.NO_MATCHES)
        ?: run { inputLayout.helperText = input.context.getString(R.string.place_search_empty) }
}

private fun showSearchFailed() {
    onMessageChanged?.invoke(PlaceInputMessage.SEARCH_FAILED)
        ?: run {
            inputLayout.helperText = null
            inputLayout.error = input.context.getString(R.string.place_search_failed)
        }
}

private fun clearAfterSelection() {
    if (onMessageChanged == null) {
        clearMessages()
    } else {
        inputLayout.error = null
        inputLayout.helperText = null
        onMessageChanged.invoke(PlaceInputMessage.NONE)
    }
}
```

`setSelectedPlace()` 使用 `clearAfterSelection()`；使用者編輯、raw text、成功候選、
無結果及搜尋失敗分別輸出 `INSTRUCTION`、`NO_MATCHES` 或 `SEARCH_FAILED`。

- [x] **Step 4: 重跑 controller instrumentation 與本地契約測試**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests com.golink.busiscoming.PlaceInputInlineCandidatesContractTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.PlaceInputControllerInstrumentedTest
```

Expected: 兩條命令均 `BUILD SUCCESSFUL`，既有行程編輯 helper 斷言不變。

- [x] **Step 5: 提交共用控制器接口**

```bash
git add app/src/main/java/com/golink/busiscoming/ui/common/PlaceInputController.kt \
  app/src/androidTest/java/com/golink/busiscoming/PlaceInputControllerInstrumentedTest.kt \
  app/src/test/java/com/golink/busiscoming/PlaceInputInlineCandidatesContractTest.kt
git commit -m "feat: expose place input status messages"
```

### Task 3: 在路線頁渲染上邊框提示並移除下方小字

**Files:**
- Create: `app/src/main/java/com/golink/busiscoming/ui/main/SearchFieldCaptionRenderer.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/common/PlacePairEditorView.kt`
- Modify: `app/src/main/res/layout/view_place_pair_editor.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/test/java/com/golink/busiscoming/NavigationSearchUiPolishContractTest.kt`
- Modify: `app/src/androidTest/java/com/golink/busiscoming/SearchDestinationInstrumentedTest.kt`
- Modify: `app/src/androidTest/java/com/golink/busiscoming/GoogleReverseGeocodingCurrentPlaceInstrumentedTest.kt`

**Interfaces:**
- Consumes: `SearchFieldCaptionState` and `PlaceInputMessage`
- Produces: `SearchFieldCaptionRenderer.onPlaceInputMessage`, `setGoogleMaps`, `setLocationFailure`, `setValidation`
- Produces: `PlacePairEditorView.originCaptionRenderer()` and `destinationCaptionRenderer()` are not exposed; `SearchFragment` owns renderer instances.

- [x] **Step 1: 寫入路線頁提示位置與狀態的失敗測試**

更新本地契約測試，斷言：

```kotlin
assertFalse(placePairLayout.contains("placePairOriginAttribution"))
assertFalse(placePairLayout.contains("placePairDestinationAttribution"))
assertTrue(placePairLayout.contains("app:expandedHintEnabled=\"false\""))
assertTrue(placePairLayout.contains("@style/TextAppearance.BusIsComing.SearchFieldCaption"))
assertTrue(searchFragment.contains("SearchFieldCaptionRenderer"))
```

更新 `SearchDestinationInstrumentedTest`，驗證初始、定位失敗、普通選定、Google 地址、
交換和校驗狀態的 `TextInputLayout.hint`：

```kotlin
assertEquals("起點 · 從清單選擇", originLayout.hint.toString())
assertEquals("起點 · 定位失敗，請手動選擇", originLayout.hint.toString())
assertEquals("起點", originLayout.hint.toString())
assertEquals("起點 · 地址由 Google Maps 提供", originLayout.hint.toString())
assertEquals("終點 · 不能與起點相同", destinationLayout.hint.toString())
```

- [x] **Step 2: 執行本地與 instrumentation 測試並確認舊布局造成失敗**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests com.golink.busiscoming.NavigationSearchUiPolishContractTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.SearchDestinationInstrumentedTest
```

Expected: 契約測試因 attribution TextView 仍存在而失敗；instrumentation 因 hint 尚未包含狀態而失敗。

- [x] **Step 3: 實作 renderer**

`SearchFieldCaptionRenderer` 接收 `TextInputLayout`、輸入 View、欄位 label 資源和
以下完整／緊湊 resource mapping：

```kotlin
private fun textResources(status: SearchFieldCaptionStatus): Pair<Int, Int> =
    when (status) {
        SearchFieldCaptionStatus.INSTRUCTION ->
            R.string.search_field_choose_from_list to
                R.string.search_field_choose_from_list_compact
        SearchFieldCaptionStatus.GOOGLE_MAPS ->
            R.string.search_field_google_maps_address to
                R.string.search_field_google_maps_address_compact
        SearchFieldCaptionStatus.NO_MATCHES ->
            R.string.search_field_no_matches to R.string.search_field_no_matches_compact
        SearchFieldCaptionStatus.SEARCH_FAILED ->
            R.string.search_field_search_failed to R.string.search_field_search_failed_compact
        SearchFieldCaptionStatus.LOCATION_FAILURE ->
            R.string.search_field_location_failure to
                R.string.search_field_location_failure_compact
        SearchFieldCaptionStatus.MISSING_PLACE ->
            R.string.search_field_choose_place to R.string.search_field_choose_place_compact
        SearchFieldCaptionStatus.SAME_AS_ORIGIN ->
            R.string.search_field_same_as_origin to
                R.string.search_field_same_as_origin_compact
    }
```

每次狀態改變：

```kotlin
fun render() {
    val status = state.visibleStatus()
    val fullStatus = status?.let(::fullText)
    val compactStatus = status?.let(::compactText)
    val fullHint = combine(label, fullStatus)
    val compactHint = combine(label, compactStatus)
    inputLayout.hint = if (fits(fullHint)) fullHint else compactHint
    inputLayout.contentDescription = accessibilityDescription(label, input.text, fullStatus)
    applyErrorColors(status?.isError == true)
}
```

`fits()` 使用與 TextInputLayout 相同 `TextAppearance.BusIsComing.SearchFieldCaption` 的
實際 `TextPaint.measureText()` 和目前已量度 box 可用寬度；layout 尚未完成時 post
一次重渲染。錯誤狀態使用 `bus_danger` hint／stroke，其他狀態恢復
`search_input_stroke` 和次要提示色。

- [x] **Step 4: 接入 SearchFragment 並移除下方 attribution**

- 建立起點／終點 renderer，再把 `renderer::onPlaceInputMessage` 傳給兩個 controller。
- `renderAttribution()` 改成對 renderer 呼叫 `setGoogleMaps`。
- 自動定位失敗改成 `originRenderer.setLocationFailure(true)`。
- `query()` 把 `RouteConfigValidationError` 映射為 `MISSING_PLACE` 或
  `SAME_AS_ORIGIN`，不再讓路線頁使用 TextInputLayout 的 error 指示列。
- 使用者輸入、重新選定、交換及成功定位沿用狀態機清除失效狀態。
- 從 `PlacePairEditorView` 和 XML 移除兩個 attribution TextView；候選清單仍位於各自
  TextInputLayout 之後。

- [x] **Step 5: 重跑提示狀態、Google attribution 與行程編輯隔離測試**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests com.golink.busiscoming.NavigationSearchUiPolishContractTest \
  --tests com.golink.busiscoming.SearchFieldCaptionStateTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.SearchDestinationInstrumentedTest,com.golink.busiscoming.GoogleReverseGeocodingCurrentPlaceInstrumentedTest,com.golink.busiscoming.PlaceInputControllerInstrumentedTest
```

Expected: 所有指定測試通過，Google 歸因跟隨交換／重建，RouteEdit helper 維持原狀。

- [x] **Step 6: 提交路線頁提示渲染**

```bash
git add app/src/main/java/com/golink/busiscoming/ui/main/SearchFieldCaptionRenderer.kt \
  app/src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt \
  app/src/main/java/com/golink/busiscoming/ui/common/PlacePairEditorView.kt \
  app/src/main/res/layout/view_place_pair_editor.xml \
  app/src/main/res/values/themes.xml \
  app/src/test/java/com/golink/busiscoming/NavigationSearchUiPolishContractTest.kt \
  app/src/androidTest/java/com/golink/busiscoming/SearchDestinationInstrumentedTest.kt \
  app/src/androidTest/java/com/golink/busiscoming/GoogleReverseGeocodingCurrentPlaceInstrumentedTest.kt
git commit -m "feat: show route search status on field borders"
```

### Task 4: 按實際坐標精確對齊定位、載入與交換工具

**Files:**
- Create: `app/src/main/java/com/golink/busiscoming/ui/common/PlacePairToolAlignment.kt`
- Create: `app/src/test/java/com/golink/busiscoming/PlacePairToolAlignmentTest.kt`
- Modify: `app/src/main/java/com/golink/busiscoming/ui/common/PlacePairEditorView.kt`
- Modify: `app/src/main/res/layout/view_place_pair_editor.xml`
- Modify: `app/src/androidTest/java/com/golink/busiscoming/SearchDestinationInstrumentedTest.kt`
- Modify: `app/src/test/java/com/golink/busiscoming/NavigationSearchUiPolishContractTest.kt`

**Interfaces:**
- Produces: `PlacePairToolAlignment.centeredTop(inputTop, inputBottom, toolHeight)`
- Produces: `PlacePairToolAlignment.swapTop(originCenter, destinationCenter, originCandidateOccupiedHeight, toolHeight)`
- `PlacePairEditorView` 在 layout 或候選 visibility／尺寸改變後重新套用 translationY。

- [x] **Step 1: 寫入幾何公式失敗測試**

```kotlin
@Test
fun `swap center removes the visible origin candidate displacement`() {
    assertEquals(
        46,
        PlacePairToolAlignment.swapTop(
            originCenter = 36,
            destinationCenter = 184,
            originCandidateOccupiedHeight = 80,
            toolHeight = 48
        )
    )
}
```

新增 instrumentation 斷言：

```kotlin
assertTrue(abs(originInput.centerYOnScreen() - originTool.centerYOnScreen()) <= dp(activity, 1))
assertTrue(abs(originInput.centerYOnScreen() - originLoading.centerYOnScreen()) <= dp(activity, 1))
assertTrue(abs(expectedSwapCenter - swap.centerYOnScreen()) <= dp(activity, 1))
assertTrue(abs(swapCenterBeforeCandidates - swapCenterAfterCandidates) <= dp(activity, 1))
```

- [x] **Step 2: 執行幾何 unit test 與 instrumentation 並確認失敗**

Run:

```bash
./gradlew testDebugUnitTest --tests com.golink.busiscoming.PlacePairToolAlignmentTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.SearchDestinationInstrumentedTest
```

Expected: unit test 因 policy 尚未存在而失敗；instrumentation 顯示固定 `56dp`／`120dp`
容器中心與實際輸入區中心不一致。

- [x] **Step 3: 實作純幾何 policy**

```kotlin
internal object PlacePairToolAlignment {
    fun centeredTop(inputTop: Int, inputBottom: Int, toolHeight: Int): Int =
        (inputTop + inputBottom - toolHeight) / 2

    fun swapTop(
        originCenter: Int,
        destinationCenter: Int,
        originCandidateOccupiedHeight: Int,
        toolHeight: Int
    ): Int {
        val collapsedDestinationCenter = destinationCenter - originCandidateOccupiedHeight
        return (originCenter + collapsedDestinationCenter) / 2 - toolHeight / 2
    }
}
```

- [x] **Step 4: 讓 PlacePairEditorView 套用實際 View 坐標**

- XML 把 `placePairOriginToolSlot`、`placePairDestinationToolSlot`、
  `placePairSwapSlot` 的高度統一為 `48dp`，刪除 `120dp`。
- `PlacePairEditorView` 用 `offsetDescendantRectToMyCoords()` 取得兩個
  `MaterialAutoCompleteTextView` 的實際矩形。
- 起點與終點工具槽 translationY 使用 `centeredTop()`。
- 起點候選可見時，以實際 `measuredHeight + topMargin + bottomMargin` 作
  `originCandidateOccupiedHeight`，交換槽使用 `swapTop()`。
- root、輸入欄、候選 visibility 或尺寸改變時 post 重算；工具 visibility 切換不得
  改變槽位置。

- [x] **Step 5: 重跑幾何、候選開合與布局契約測試**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests com.golink.busiscoming.PlacePairToolAlignmentTest \
  --tests com.golink.busiscoming.NavigationSearchUiPolishContractTest
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.SearchDestinationInstrumentedTest
```

Expected: 幾何誤差均不超過 `1dp`，候選清單開合前後交換按鈕中心不變。

- [x] **Step 6: 提交工具居中修復**

```bash
git add app/src/main/java/com/golink/busiscoming/ui/common/PlacePairToolAlignment.kt \
  app/src/test/java/com/golink/busiscoming/PlacePairToolAlignmentTest.kt \
  app/src/main/java/com/golink/busiscoming/ui/common/PlacePairEditorView.kt \
  app/src/main/res/layout/view_place_pair_editor.xml \
  app/src/androidTest/java/com/golink/busiscoming/SearchDestinationInstrumentedTest.kt \
  app/src/test/java/com/golink/busiscoming/NavigationSearchUiPolishContractTest.kt
git commit -m "fix: center route search field tools"
```

### Task 5: 完成多語、大字體、明暗主題與全量回歸驗證

**Files:**
- Modify if a gap is found: tests and implementation files already listed above
- Modify: `docs/superpowers/plans/2026-07-25-route-search-input-caption-alignment.md`

**Interfaces:**
- Consumes: completed caption renderer and geometry alignment.
- Produces: checked plan tasks and fresh build／instrumentation evidence.

- [x] **Step 1: 在專用模擬器驗證可見矩陣**

依次切換香港繁體、簡體、英文；淺色、深色；font scale 1.0、1.3、2.0。在 360dp
寬度驗證空白、普通選定、Google 地址、無匹配、搜尋失敗、定位失敗、未選定、
相同地點與候選展開狀態。完整文案放不下時必須切到預定緊湊文案，仍不得裁切。

- [x] **Step 2: 執行完整 instrumentation suite**

Run:

```bash
adb -s "$route_ui_qa_serial" shell am instrument -w -r \
  -e notClass \
  com.golink.busiscoming.AppUpdateInstrumentedTest,com.golink.busiscoming.AppUpdateVisualMatrixInstrumentedTest,com.golink.busiscoming.RouteSearchInputVisualMatrixInstrumentedTest \
  com.golink.busiscoming.test/com.golink.busiscoming.BusIsComingTestRunner
```

Expected: `OK (61 tests)`。使用序號限定的 `adb am instrument`，避免 Gradle
`connectedDebugAndroidTest` 同時使用驗證開始前已運行的其他裝置。既有
`AppUpdateInstrumentedTest` 硬性要求設備沒有 Google Play，但本次專用 AVD 包含並
啟用了 `com.android.vending`，故按環境前置條件排除；該模組不在本變更範圍內。
兩個視覺矩陣類需要專用 runner 參數，分開執行。

- [x] **Step 3: 關閉本次啟動的模擬器並確認釋放**

Run:

```bash
adb -s "$route_ui_qa_serial" emu kill
adb devices
```

Expected: 執行期間記錄於 `route_ui_qa_serial` 的本次序號消失；不得關閉驗證開始前
已存在的其他裝置。

- [x] **Step 4: 執行完整專案 build**

Run:

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`，涵蓋編譯、unit tests、lint 與 debug／release assemble。

- [x] **Step 5: 檢查需求、差異與工作區隔離**

Run:

```bash
git diff f9ce847 --check
git diff f9ce847 --stat
git status --short
```

Expected: 沒有 whitespace error；變更僅涉及計劃列出的檔案；工作區只剩原有
`app/build.gradle.kts` 未提交修改。

- [x] **Step 6: 更新計劃勾選並提交驗證記錄**

完成記錄：

- `RouteSearchInputVisualMatrixInstrumentedTest` 在 360dp、font scale
  1.0／1.3／2.0、香港繁體／簡體／英文、淺色／深色共 18 個組合全部通過。
- 排除上述環境限定更新類與需專用參數的矩陣類後，裝置回歸 `OK (61 tests)`；
  其中 2 項真實 Google API 驗收按既有 `runGoogleApiAcceptance` 開關正常跳過。
- `SearchDestinationInstrumentedTest` 共 12 項通過，定位及交換工具實測中心誤差不超過
  `1dp`，候選清單開合不令交換工具跳動。
- `./gradlew build` 通過，包含 408 項本地單元測試、lint 及 debug／release 組裝。
- 本次啟動的 `emulator-5556` 已關閉；驗證開始前存在的 `emulator-5554` 保持運行。

把本文件全部 task checkbox 更新為 `[x]`，然後：

```bash
git add -f docs/superpowers/plans/2026-07-25-route-search-input-caption-alignment.md
git commit -m "docs: complete route search input plan"
```
