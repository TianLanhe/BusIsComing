## 1. Result Table Layout

- [x] 1.1 Remove the fixed `760dp` result table and row widths so the table, header, empty state, and result rows use the available screen width.
- [x] 1.2 Keep the existing header labels while applying shared column weights to the header and result rows.
- [x] 1.3 Update result row height, padding, max lines, and text sizing so normal phone screens show about 5 to 7 rows without text overlap.
- [x] 1.4 Verify the pre-query header, empty-result state, loading state, failure state, and populated result list all align to the same width.

## 2. Main Route Selector and Entry Button

- [x] 2.1 Update the selected route folded display to show route name on the first line and `origin -> destination` on the second line.
- [x] 2.2 Keep the route selector dropdown list behavior and item presentation unchanged.
- [x] 2.3 Apply a non-white, clearly clickable style to the main screen “管理路线” button while preserving visual separation from the “查询” primary button.

## 3. Route Management Actions

- [x] 3.1 Add a “克隆” action to each route item in the route management list.
- [x] 3.2 Wire the clone action to open `RouteEditActivity` in add mode with the original route data passed as prefill extras.
- [x] 3.3 Prefill cloned routes with the original origin and destination and a route name ending in `（副本）`.
- [x] 3.4 Add repository-level duplicate-route lookup or validation that compares trimmed route name, origin place, and destination place while excluding the currently edited route id.
- [x] 3.5 Block saving when a new, cloned, or edited route would duplicate another saved route, and show a clear “路线已存在” style error message.

## 4. Route Edit Page Interactions

- [x] 4.1 Add a visible in-page return button to the route edit screen that finishes the activity without saving.
- [x] 4.2 Center the route edit form content within the available page area while preserving scroll access on small screens and when the keyboard is open.
- [x] 4.3 Add an origin/destination swap button and implement the selected-place and unconfirmed-text swap behavior.
- [x] 4.4 Replace the route name, origin, and destination hints with the confirmed friendly guidance copy.
- [x] 4.5 Confirm existing validation still blocks blank route name, missing selected places, and identical origin/destination.

## 5. Place Search Loading Feedback

- [x] 5.1 Add independent loading indicator views for origin and destination search without covering the text inputs or dropdown controls.
- [x] 5.2 Show `正在匹配地点...` and the loading animation while the latest keyword is waiting for debounce or being searched.
- [x] 5.3 Hide loading feedback when a search succeeds, returns no candidates, fails, is cancelled, or the keyword becomes too short.
- [x] 5.4 Ensure users can continue typing during loading and stale search results are ignored.

## 6. Verification

- [x] 6.1 Run `./gradlew build` and fix any compile, test, or lint failures.
- [x] 6.2 Run `adb devices`; if no device or emulator is connected, document that manual visual validation was not completed.
- [x] 6.3 If a device or emulator is available, verify the main query flow, result table alignment, route selector folded display, route cloning, duplicate save prevention, swap button, back button, and place-search loading feedback.
