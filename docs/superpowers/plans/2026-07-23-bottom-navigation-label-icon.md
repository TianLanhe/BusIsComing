# 底部導航行程與路線標籤／圖標實作計劃

> **供 agentic workers 使用：** 必須使用 `superpowers:subagent-driven-development`（推薦）或 `superpowers:executing-plans`，逐項執行本計劃。所有步驟使用 checkbox（`- [ ]`）追蹤。

**目標：** 把底部導航前兩項由「常用／搜尋」改成三語一致的「行程／路線」，並以 Material Symbols `bookmarks`／`route` outline 與 fill 本地向量圖標準確表達已儲存行程和點到點路線方案。

**架構：** 保留既有 `BottomNavigationView`、menu item id、`TopLevelDestination` mapping、24dp 圖標槽位與 active indicator，只替換 Android resource。用一個 JVM 資源契約測試鎖定三語文案、menu mapping、selector 和官方 path data；既有 instrumentation 負責驗證三語 runtime 文案、無障礙描述、選中狀態與導航量度。

**技術棧：** Android XML resources、Material Components `BottomNavigationView`、Android Vector Drawable／selector、Kotlin JUnit4 contract tests、AndroidX Test／Espresso instrumentation。

## 全域限制

- 香港繁體與簡體中文的可見底欄標籤固定為兩個字。
- 可見標籤固定為 `行程／路線／設定`、`行程／路线／设置`、`Journeys / Routes / Settings`。
- 前兩項 `contentDescription` 固定為 `已儲存行程／搜尋巴士路線`、`已保存行程／搜索公交路线`、`Saved journeys / Find bus routes`。
- `bookmarks` 與 `route` 必須使用 Google Material Symbols Outlined 24px 官方 outline／fill path data，匯出為本地 Android Vector Drawable；runtime 不得載入網路字型或遠端素材。
- 保留 `navigation_frequent_routes`、`navigation_search`、`navigation_settings`、`TopLevelDestination.FREQUENT_ROUTES`、`TopLevelDestination.SEARCH` 和 `TopLevelDestination.SETTINGS`。
- 保留現有 24dp 圖標尺寸、64×32dp active indicator、等寬三項、13sp active label、12sp inactive label及既有深淺主題 tint。
- 新增或修改的 App 可見／無障礙文案必須同時提供香港繁體、獨立審校簡體和自然英文，不得在 XML 或 Kotlin 硬編碼。
- 不修改 Fragment、查詢流程、資料模型、SQLite、頁面標題、按鈕、Toast、通知或其他非底欄文案。
- Android 實作完成後必須運行 `./gradlew build`；有連接裝置時必須運行指定 instrumentation。

---

## 檔案結構

- Create: `app/src/test/java/com/golink/busiscoming/BottomNavigationDestinationContractTest.kt`
  - 唯一責任：鎖定底欄三語 destination 文案、menu resource mapping、selector 配對及 Material Symbols path data。
- Modify: `app/src/main/res/values/strings_runtime.xml`
  - 香港繁體可見標籤與完整無障礙文案。
- Modify: `app/src/main/res/values-b+zh+Hans/strings_runtime.xml`
  - 簡體中文可見標籤與完整無障礙文案。
- Modify: `app/src/main/res/values-en/strings_runtime.xml`
  - 英文可見標籤與完整無障礙文案。
- Modify: `app/src/main/res/menu/top_level_navigation.xml`
  - 保留三個 item id，改用新的標籤、無障礙字串和既有 state selector 名稱。
- Modify: `app/src/androidTest/java/com/golink/busiscoming/AppLanguageAndThemeSettingsInstrumentedTest.kt`
  - 在既有三語／深淺切換流程中驗證 bottom menu title 與 `contentDescription`。
- Create: `app/src/main/res/drawable/ic_nav_journeys_outline.xml`
- Create: `app/src/main/res/drawable/ic_nav_journeys_filled.xml`
- Create: `app/src/main/res/drawable/ic_nav_routes_outline.xml`
- Create: `app/src/main/res/drawable/ic_nav_routes_filled.xml`
  - 四個檔案只保存官方 24px outline／fill path data，顏色繼續由 BottomNavigationView tint 控制。
- Modify: `app/src/main/res/drawable/ic_nav_frequent_routes_state.xml`
- Modify: `app/src/main/res/drawable/ic_nav_search_state.xml`
  - checked item 使用 fill；default item 使用 outline。
- Delete: `app/src/main/res/drawable/ic_nav_frequent_routes.xml`
- Delete: `app/src/main/res/drawable/ic_nav_search.xml`
  - selector 改用新向量後刪除不再引用的舊「清單／放大鏡」圖標。

---

### Task 1：改用「行程／路線」三語 destination 文案

**Files:**
- Create: `app/src/test/java/com/golink/busiscoming/BottomNavigationDestinationContractTest.kt`
- Modify: `app/src/main/res/values/strings_runtime.xml:3-4`
- Modify: `app/src/main/res/values-b+zh+Hans/strings_runtime.xml:3`
- Modify: `app/src/main/res/values-en/strings_runtime.xml:3`
- Modify: `app/src/main/res/menu/top_level_navigation.xml:3-17`
- Modify: `app/src/androidTest/java/com/golink/busiscoming/AppLanguageAndThemeSettingsInstrumentedTest.kt:46-145`

**Interfaces:**
- Consumes: 既有 menu item id `navigation_frequent_routes`、`navigation_search`、`navigation_settings`；既有 `AppLanguageRepository`／`AppThemePreferenceStore` 切換流程。
- Produces: `R.string.nav_journeys`、`R.string.nav_routes`、`R.string.nav_journeys_content_description`、`R.string.nav_routes_content_description`，供 menu XML 與 instrumentation 使用。

- [ ] **Step 1：新增會失敗的底欄 destination 資源契約測試**

Create `app/src/test/java/com/golink/busiscoming/BottomNavigationDestinationContractTest.kt`：

```kotlin
package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavigationDestinationContractTest {
    private val menu = File("src/main/res/menu/top_level_navigation.xml").readText()
    private val traditional = values(File("src/main/res/values"))
    private val simplified = values(File("src/main/res/values-b+zh+Hans"))
    private val english = values(File("src/main/res/values-en"))

    @Test
    fun `bottom navigation names destinations and exposes full accessibility copy`() {
        assertMenuItem(
            id = "navigation_frequent_routes",
            title = "@string/nav_journeys",
            description = "@string/nav_journeys_content_description",
            icon = "@drawable/ic_nav_frequent_routes_state"
        )
        assertMenuItem(
            id = "navigation_search",
            title = "@string/nav_routes",
            description = "@string/nav_routes_content_description",
            icon = "@drawable/ic_nav_search_state"
        )
        assertMenuItem(
            id = "navigation_settings",
            title = "@string/settings",
            description = "@string/settings",
            icon = "@drawable/ic_nav_settings_state"
        )

        assertCopy(
            traditional,
            mapOf(
                "nav_journeys" to "行程",
                "nav_routes" to "路線",
                "nav_journeys_content_description" to "已儲存行程",
                "nav_routes_content_description" to "搜尋巴士路線"
            )
        )
        assertCopy(
            simplified,
            mapOf(
                "nav_journeys" to "行程",
                "nav_routes" to "路线",
                "nav_journeys_content_description" to "已保存行程",
                "nav_routes_content_description" to "搜索公交路线"
            )
        )
        assertCopy(
            english,
            mapOf(
                "nav_journeys" to "Journeys",
                "nav_routes" to "Routes",
                "nav_journeys_content_description" to "Saved journeys",
                "nav_routes_content_description" to "Find bus routes"
            )
        )

        listOf(traditional, simplified, english).forEach { localized ->
            assertFalse(localized.containsKey("nav_frequent"))
            assertFalse(localized.containsKey("nav_search"))
        }
    }

    private fun assertMenuItem(
        id: String,
        title: String,
        description: String,
        icon: String
    ) {
        val item = menu.substringAfter("android:id=\"@+id/$id\"").substringBefore("/>")
        assertTrue("Missing title $title for $id", item.contains("android:title=\"$title\""))
        assertTrue(
            "Missing content description $description for $id",
            item.contains("android:contentDescription=\"$description\"")
        )
        assertTrue("Missing icon $icon for $id", item.contains("android:icon=\"$icon\""))
    }

    private fun assertCopy(actual: Map<String, String>, expected: Map<String, String>) {
        expected.forEach { (key, value) ->
            assertEquals("Unexpected copy for $key", value, actual.getValue(key))
        }
    }

    private fun values(directory: File): Map<String, String> =
        Regex(
            "<string\\s+name=\"([^\"]+)\"(?:\\s+[^>]*)?>(.*?)</string>",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
            .findAll(
                directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && it.extension == "xml" }
                    .joinToString("\n") { it.readText() }
            )
            .associate { it.groupValues[1] to it.groupValues[2] }
}
```

- [ ] **Step 2：運行新契約測試並確認紅燈**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.golink.busiscoming.BottomNavigationDestinationContractTest \
  --no-daemon
```

Expected: `FAILED`；第一個失敗應指出 menu 仍引用 `@string/nav_frequent`／`@string/nav_search`，或 `nav_journeys` key 尚不存在。

- [ ] **Step 3：替換三語 runtime string resource**

在三個 `strings_runtime.xml` 中刪除 `nav_frequent`、`nav_search`，並在檔案開頭加入以下四個 key。

`app/src/main/res/values/strings_runtime.xml`：

```xml
<string name="nav_journeys">行程</string>
<string name="nav_routes">路線</string>
<string name="nav_journeys_content_description">已儲存行程</string>
<string name="nav_routes_content_description">搜尋巴士路線</string>
```

`app/src/main/res/values-b+zh+Hans/strings_runtime.xml`：

```xml
<string name="nav_journeys">行程</string>
<string name="nav_routes">路线</string>
<string name="nav_journeys_content_description">已保存行程</string>
<string name="nav_routes_content_description">搜索公交路线</string>
```

`app/src/main/res/values-en/strings_runtime.xml`：

```xml
<string name="nav_journeys">Journeys</string>
<string name="nav_routes">Routes</string>
<string name="nav_journeys_content_description">Saved journeys</string>
<string name="nav_routes_content_description">Find bus routes</string>
```

- [ ] **Step 4：把 menu title 與無障礙文案分開**

Replace `app/src/main/res/menu/top_level_navigation.xml` with：

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/navigation_frequent_routes"
        android:contentDescription="@string/nav_journeys_content_description"
        android:icon="@drawable/ic_nav_frequent_routes_state"
        android:title="@string/nav_journeys" />
    <item
        android:id="@+id/navigation_search"
        android:contentDescription="@string/nav_routes_content_description"
        android:icon="@drawable/ic_nav_search_state"
        android:title="@string/nav_routes" />
    <item
        android:id="@+id/navigation_settings"
        android:contentDescription="@string/settings"
        android:icon="@drawable/ic_nav_settings_state"
        android:title="@string/settings" />
</menu>
```

- [ ] **Step 5：在既有三語／主題 instrumentation 中驗證 runtime menu copy**

Add import to `AppLanguageAndThemeSettingsInstrumentedTest.kt`：

```kotlin
import com.google.android.material.bottomnavigation.BottomNavigationView
```

在第一次 `waitForSettingsValues("淺色模式", "繁體中文")` 後加入：

```kotlin
scenario.onActivity { activity ->
    assertBottomNavigationCopy(
        activity,
        journeys = "行程",
        routes = "路線",
        settings = "設定",
        journeysDescription = "已儲存行程",
        routesDescription = "搜尋巴士路線"
    )
}
```

在 `waitForSettingsValues("深色模式", "简体中文")` 後加入：

```kotlin
scenario.onActivity { activity ->
    assertBottomNavigationCopy(
        activity,
        journeys = "行程",
        routes = "路线",
        settings = "设置",
        journeysDescription = "已保存行程",
        routesDescription = "搜索公交路线"
    )
}
```

在 `waitForSettingsValues("Light", "English")` 後加入：

```kotlin
scenario.onActivity { activity ->
    assertBottomNavigationCopy(
        activity,
        journeys = "Journeys",
        routes = "Routes",
        settings = "Settings",
        journeysDescription = "Saved journeys",
        routesDescription = "Find bus routes"
    )
}
```

在 `waitForSettingsValues` helper 之前加入：

```kotlin
private fun assertBottomNavigationCopy(
    activity: MainActivity,
    journeys: String,
    routes: String,
    settings: String,
    journeysDescription: String,
    routesDescription: String
) {
    val menu = activity.findViewById<BottomNavigationView>(R.id.topLevelNav).menu
    val journeysItem = menu.findItem(R.id.navigation_frequent_routes)
    val routesItem = menu.findItem(R.id.navigation_search)
    val settingsItem = menu.findItem(R.id.navigation_settings)

    assertEquals(journeys, journeysItem.title.toString())
    assertEquals(journeysDescription, journeysItem.contentDescription.toString())
    assertEquals(routes, routesItem.title.toString())
    assertEquals(routesDescription, routesItem.contentDescription.toString())
    assertEquals(settings, settingsItem.title.toString())
    assertEquals(settings, settingsItem.contentDescription.toString())
}
```

- [ ] **Step 6：運行資源與本地化單元測試**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.golink.busiscoming.BottomNavigationDestinationContractTest \
  --tests com.golink.busiscoming.LocaleResourceContractTest \
  --tests com.golink.busiscoming.JourneyRouteTerminologyContractTest \
  --tests com.golink.busiscoming.NavigationSearchUiPolishContractTest \
  --no-daemon
```

Expected: `BUILD SUCCESSFUL`；新契約及三個既有契約全部通過。

- [ ] **Step 7：編譯並運行三語／主題 instrumentation**

Run:

```bash
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
adb devices
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.AppLanguageAndThemeSettingsInstrumentedTest \
  --no-daemon
```

Expected: compile 與 connected test 均為 `BUILD SUCCESSFUL`；`adb devices` 至少有一個狀態為 `device` 的 Android 裝置。若沒有裝置，本步驟不得標記完成，並須明確報告缺少裝置驗證。

- [ ] **Step 8：提交三語 destination 文案**

```bash
git add \
  app/src/test/java/com/golink/busiscoming/BottomNavigationDestinationContractTest.kt \
  app/src/main/res/values/strings_runtime.xml \
  app/src/main/res/values-b+zh+Hans/strings_runtime.xml \
  app/src/main/res/values-en/strings_runtime.xml \
  app/src/main/res/menu/top_level_navigation.xml \
  app/src/androidTest/java/com/golink/busiscoming/AppLanguageAndThemeSettingsInstrumentedTest.kt
git diff --cached --check
git commit -m "feat: rename bottom navigation destinations"
```

Expected: commit 只包含上述六個檔案。

---

### Task 2：替換 bookmarks／route outline 與 fill 圖標

**Files:**
- Modify: `app/src/test/java/com/golink/busiscoming/BottomNavigationDestinationContractTest.kt`
- Create: `app/src/main/res/drawable/ic_nav_journeys_outline.xml`
- Create: `app/src/main/res/drawable/ic_nav_journeys_filled.xml`
- Create: `app/src/main/res/drawable/ic_nav_routes_outline.xml`
- Create: `app/src/main/res/drawable/ic_nav_routes_filled.xml`
- Modify: `app/src/main/res/drawable/ic_nav_frequent_routes_state.xml`
- Modify: `app/src/main/res/drawable/ic_nav_search_state.xml`
- Delete: `app/src/main/res/drawable/ic_nav_frequent_routes.xml`
- Delete: `app/src/main/res/drawable/ic_nav_search.xml`

**Interfaces:**
- Consumes: Task 1 保留的 selector resource 名稱 `ic_nav_frequent_routes_state`、`ic_nav_search_state`，以及既有 `BottomNavigationView` item tint。
- Produces: 四個 24dp 本地向量資源；checked/default selector mapping。menu XML、Kotlin destination mapping 和 layout 不需要改動。

- [ ] **Step 1：為 selector 與官方 path data 新增會失敗的契約**

Add this test and helpers inside `BottomNavigationDestinationContractTest`：

```kotlin
@Test
fun `journeys and routes use official outline and fill state pairs`() {
    assertSelector(
        name = "ic_nav_frequent_routes_state",
        checked = "ic_nav_journeys_filled",
        unchecked = "ic_nav_journeys_outline"
    )
    assertSelector(
        name = "ic_nav_search_state",
        checked = "ic_nav_routes_filled",
        unchecked = "ic_nav_routes_outline"
    )

    assertVector("ic_nav_journeys_outline", BOOKMARKS_OUTLINE)
    assertVector("ic_nav_journeys_filled", BOOKMARKS_FILLED)
    assertVector("ic_nav_routes_outline", ROUTE_OUTLINE)
    assertVector("ic_nav_routes_filled", ROUTE_FILLED)

    assertFalse(File("src/main/res/drawable/ic_nav_frequent_routes.xml").exists())
    assertFalse(File("src/main/res/drawable/ic_nav_search.xml").exists())
}

private fun assertSelector(name: String, checked: String, unchecked: String) {
    val selector = File("src/main/res/drawable/$name.xml").readText()
    val checkedItem = selector.substringAfter("@drawable/$checked").substringBefore("/>")
    assertTrue(checkedItem.contains("android:state_checked=\"true\""))
    assertTrue(selector.contains("<item android:drawable=\"@drawable/$unchecked\" />"))
}

private fun assertVector(name: String, pathData: String) {
    val vector = File("src/main/res/drawable/$name.xml").readText()
    assertTrue(vector.contains("android:width=\"24dp\""))
    assertTrue(vector.contains("android:height=\"24dp\""))
    assertTrue(vector.contains("android:viewportWidth=\"960\""))
    assertTrue(vector.contains("android:viewportHeight=\"960\""))
    assertTrue(vector.contains("android:translateY=\"960\""))
    assertTrue(vector.contains("android:pathData=\"$pathData\""))
}

private companion object {
    const val BOOKMARKS_OUTLINE =
        "M120-40v-640q0-33 23.5-56.5T200-760h400q33 0 56.5 23.5T680-680v640L400-160 120-40Zm80-122 200-86 200 86v-518H200v518Zm560 2v-680H240v-80h520q33 0 56.5 23.5T840-840v680h-80ZM200-680h400-400Z"
    const val BOOKMARKS_FILLED =
        "M120-40v-640q0-33 23.5-56.5T200-760h400q33 0 56.5 23.5T680-680v640L400-160 120-40Zm640-120v-680H240v-80h520q33 0 56.5 23.5T840-840v680h-80Z"
    const val ROUTE_OUTLINE =
        "M360-120q-66 0-113-47t-47-113v-327q-35-13-57.5-43.5T120-720q0-50 35-85t85-35q50 0 85 35t35 85q0 39-22.5 69.5T280-607v327q0 33 23.5 56.5T360-200q33 0 56.5-23.5T440-280v-400q0-66 47-113t113-47q66 0 113 47t47 113v327q35 13 57.5 43.5T840-240q0 50-35 85t-85 35q-50 0-85-35t-35-85q0-39 22.5-70t57.5-43v-327q0-33-23.5-56.5T600-760q-33 0-56.5 23.5T520-680v400q0 66-47 113t-113 47ZM240-680q17 0 28.5-11.5T280-720q0-17-11.5-28.5T240-760q-17 0-28.5 11.5T200-720q0 17 11.5 28.5T240-680Zm480 480q17 0 28.5-11.5T760-240q0-17-11.5-28.5T720-280q-17 0-28.5 11.5T680-240q0 17 11.5 28.5T720-200ZM240-720Zm480 480Z"
    const val ROUTE_FILLED =
        "M360-120q-66 0-113-47t-47-113v-327q-35-13-57.5-43.5T120-720q0-50 35-85t85-35q50 0 85 35t35 85q0 39-22.5 69.5T280-607v327q0 33 23.5 56.5T360-200q33 0 56.5-23.5T440-280v-400q0-66 47-113t113-47q66 0 113 47t47 113v327q35 13 57.5 43.5T840-240q0 50-35 85t-85 35q-50 0-85-35t-35-85q0-39 22.5-70t57.5-43v-327q0-33-23.5-56.5T600-760q-33 0-56.5 23.5T520-680v400q0 66-47 113t-113 47Z"
}
```

- [ ] **Step 2：運行圖標契約並確認紅燈**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.golink.busiscoming.BottomNavigationDestinationContractTest \
  --no-daemon
```

Expected: `FAILED`；新向量檔案不存在，或現有 selector 仍引用 `ic_nav_frequent_routes`／`ic_nav_search`。

- [ ] **Step 3：建立 journeys outline／fill Vector Drawable**

Create `app/src/main/res/drawable/ic_nav_journeys_outline.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="@color/bus_text_primary"
            android:pathData="M120-40v-640q0-33 23.5-56.5T200-760h400q33 0 56.5 23.5T680-680v640L400-160 120-40Zm80-122 200-86 200 86v-518H200v518Zm560 2v-680H240v-80h520q33 0 56.5 23.5T840-840v680h-80ZM200-680h400-400Z" />
    </group>
</vector>
```

Create `app/src/main/res/drawable/ic_nav_journeys_filled.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="@color/bus_text_primary"
            android:pathData="M120-40v-640q0-33 23.5-56.5T200-760h400q33 0 56.5 23.5T680-680v640L400-160 120-40Zm640-120v-680H240v-80h520q33 0 56.5 23.5T840-840v680h-80Z" />
    </group>
</vector>
```

- [ ] **Step 4：建立 routes outline／fill Vector Drawable**

Create `app/src/main/res/drawable/ic_nav_routes_outline.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="@color/bus_text_primary"
            android:pathData="M360-120q-66 0-113-47t-47-113v-327q-35-13-57.5-43.5T120-720q0-50 35-85t85-35q50 0 85 35t35 85q0 39-22.5 69.5T280-607v327q0 33 23.5 56.5T360-200q33 0 56.5-23.5T440-280v-400q0-66 47-113t113-47q66 0 113 47t47 113v327q35 13 57.5 43.5T840-240q0 50-35 85t-85 35q-50 0-85-35t-35-85q0-39 22.5-70t57.5-43v-327q0-33-23.5-56.5T600-760q-33 0-56.5 23.5T520-680v400q0 66-47 113t-113 47ZM240-680q17 0 28.5-11.5T280-720q0-17-11.5-28.5T240-760q-17 0-28.5 11.5T200-720q0 17 11.5 28.5T240-680Zm480 480q17 0 28.5-11.5T760-240q0-17-11.5-28.5T720-280q-17 0-28.5 11.5T680-240q0 17 11.5 28.5T720-200ZM240-720Zm480 480Z" />
    </group>
</vector>
```

Create `app/src/main/res/drawable/ic_nav_routes_filled.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="@color/bus_text_primary"
            android:pathData="M360-120q-66 0-113-47t-47-113v-327q-35-13-57.5-43.5T120-720q0-50 35-85t85-35q50 0 85 35t35 85q0 39-22.5 69.5T280-607v327q0 33 23.5 56.5T360-200q33 0 56.5-23.5T440-280v-400q0-66 47-113t113-47q66 0 113 47t47 113v327q35 13 57.5 43.5T840-240q0 50-35 85t-85 35q-50 0-85-35t-35-85q0-39 22.5-70t57.5-43v-327q0-33-23.5-56.5T600-760q-33 0-56.5 23.5T520-680v400q0 66-47 113t-113 47Z" />
    </group>
</vector>
```

- [ ] **Step 5：更新 selector 並刪除舊圖標**

Replace `ic_nav_frequent_routes_state.xml` with：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:drawable="@drawable/ic_nav_journeys_filled"
        android:state_checked="true" />
    <item android:drawable="@drawable/ic_nav_journeys_outline" />
</selector>
```

Replace `ic_nav_search_state.xml` with：

```xml
<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:drawable="@drawable/ic_nav_routes_filled"
        android:state_checked="true" />
    <item android:drawable="@drawable/ic_nav_routes_outline" />
</selector>
```

Delete the now-unreferenced files：

```text
app/src/main/res/drawable/ic_nav_frequent_routes.xml
app/src/main/res/drawable/ic_nav_search.xml
```

- [ ] **Step 6：運行圖標、導航與資源測試**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests com.golink.busiscoming.BottomNavigationDestinationContractTest \
  --tests com.golink.busiscoming.NavigationSearchUiPolishContractTest \
  --tests com.golink.busiscoming.FixedColorContractTest \
  --no-daemon
```

Expected: `BUILD SUCCESSFUL`；selector、path data、24dp slot 與語意色契約均通過。

- [ ] **Step 7：在裝置驗證選中態與大字體量度**

Run:

```bash
adb devices
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.golink.busiscoming.TopLevelNavigationInstrumentedTest \
  --no-daemon
```

Expected: `BUILD SUCCESSFUL`；三個 destination 切換、Activity recreation、24dp icon slot、等寬 item、active indicator 與 font scale `1.0／1.3／2.0` 全部通過。若沒有裝置，本步驟不得標記完成，並須明確報告缺少裝置驗證。

- [ ] **Step 8：人工核對三語與深淺主題**

在 App 設定頁依序選擇下列六種組合，每次返回底欄並切換「行程／路線／設定」：

```text
繁體中文 × 淺色
繁體中文 × 深色
简体中文 × 浅色
简体中文 × 深色
English × Light
English × Dark
```

Expected for every combination:

```text
未選中：outline symbol + 未選中色 + 12sp label
選中：fill symbol + 膠囊 indicator + 選中色 + 13sp bold label
三項等寬；圖標、indicator、標籤不重疊；英文 Journeys / Routes / Settings 不裁切
TalkBack：Saved journeys / Find bus routes / Settings 或對應中文完整描述
```

- [ ] **Step 9：提交新圖標**

```bash
git add \
  app/src/test/java/com/golink/busiscoming/BottomNavigationDestinationContractTest.kt \
  app/src/main/res/drawable/ic_nav_journeys_outline.xml \
  app/src/main/res/drawable/ic_nav_journeys_filled.xml \
  app/src/main/res/drawable/ic_nav_routes_outline.xml \
  app/src/main/res/drawable/ic_nav_routes_filled.xml \
  app/src/main/res/drawable/ic_nav_frequent_routes_state.xml \
  app/src/main/res/drawable/ic_nav_search_state.xml \
  app/src/main/res/drawable/ic_nav_frequent_routes.xml \
  app/src/main/res/drawable/ic_nav_search.xml
git diff --cached --check
git commit -m "feat: update bottom navigation icons"
```

Expected: commit 只包含 contract test、四個新 vector、兩個 selector 和兩個舊 vector 的刪除。

---

## 最終品質門檻

- [ ] 運行完整構建：

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL`，包括 Kotlin 編譯、unit tests、lint 及 debug／release assemble。

- [ ] 檢查最終提交範圍：

```bash
git status --short
git log -3 --oneline
```

Expected: 工作區沒有本 change 的未提交檔案；最近提交包含：

```text
feat: rename bottom navigation destinations
feat: update bottom navigation icons
```

- [ ] 交付時明確報告：單元測試、instrumentation、`./gradlew build`、三語／深淺人工核對各自是否完成；不得把未執行的裝置或視覺檢查描述為已通過。
