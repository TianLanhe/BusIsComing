## 1. Data Model And Storage

- [x] 1.1 Extend `RouteConfig` with `usageCount` and nullable `lastUsedAt`.
- [x] 1.2 Add `usage_count` and `last_used_at` columns to `RouteConfigDbHelper` and bump the database version.
- [x] 1.3 Implement database upgrade from the current place-based schema while preserving existing routes and initializing usage statistics.
- [x] 1.4 Keep the existing old text-route upgrade path valid for databases without place coordinates.
- [x] 1.5 Update `RouteConfigRepository` insert, update, read, duplicate-check, and column mapping logic for usage statistics.
- [x] 1.6 Add repository API to record a route usage event by incrementing `usage_count` and updating `last_used_at`.
- [x] 1.7 Update repository ordering so main-page route reads sort by `usage_count DESC`, `last_used_at DESC`, `updated_at DESC`, and `id DESC`.
- [x] 1.8 Update or add instrumentation tests covering insert defaults, usage recording, usage sorting, and database upgrade column preservation.

## 2. Shared Place Input Behavior

- [x] 2.1 Extract reusable place input controller logic from `RouteEditActivity` without changing existing route edit behavior.
- [x] 2.2 Preserve debounce, candidate limit, loading state, stale-result ignoring, selected-place clearing, and search error behavior in the extracted controller.
- [x] 2.3 Support setting selected places, raw text, and swapping two place input states through the shared controller.
- [x] 2.4 Refactor `RouteEditActivity` to use the shared place input controller and keep existing tests passing.
- [x] 2.5 Add focused tests or instrumentation coverage for the shared controller behavior where practical.

## 3. Main Route Selection UI

- [x] 3.1 Replace the main-page route dropdown with a single `常用路線` shortcut section.
- [x] 3.2 Render 1 to 2 saved routes as expanded equal-width cards that fill the available width.
- [x] 3.3 Render 3 or more saved routes as the first three sorted shortcut cards plus a separate right-side expand arrow.
- [x] 3.4 Highlight the currently selected route card and clear stale query results when the user selects another route.
- [x] 3.5 Ensure route cards and route list items show only route name and path, without usage count, recent-use time, rank, or sorting explanation.
- [x] 3.6 Keep the `查詢` button as the primary action and use the currently selected saved route as the default query source.
- [x] 3.7 Update the no-saved-route state to provide both route creation and temporary origin/destination query entry points.

## 4. Route Picker And Temporary Query

- [x] 4.1 Implement the expand-arrow interaction to open the full saved-route picker.
- [x] 4.2 Show all saved routes in the picker using the same usage-based ordering as the shortcut cards.
- [x] 4.3 Add `查詢臨時起點和終點` as the last, visually secondary row in the full route picker.
- [x] 4.4 Implement the temporary query bottom sheet with origin and destination autocomplete fields.
- [x] 4.5 Add the temporary query swap icon button with at least 48dp touch target and `交換起點和終點` accessibility description.
- [x] 4.6 Validate temporary origin and destination before querying or saving, including missing candidate selection and identical places.
- [x] 4.7 Use temporary origin and destination to run a route query without automatically saving the route.
- [x] 4.8 Implement `保存為常用` from the temporary query flow, requiring a route name and saving through `RouteConfigRepository`.
- [x] 4.9 After saving a temporary query as a route, refresh the saved-route list and make the new saved route selectable.

## 5. Usage Recording And Query Flow

- [x] 5.1 Record usage when a saved route is used to start a query, independent of Citybus network success.
- [x] 5.2 Do not record usage for temporary queries.
- [x] 5.3 Do not increment usage just because a temporary query is saved as a route.
- [x] 5.4 Preserve progressive route query behavior, ETA updates, result sorting, loading state, failure state, and no-result state.
- [x] 5.5 Ensure cancelling or switching route selection invalidates in-flight query callbacks as before.

## 6. Verification

- [x] 6.1 Add or update unit tests for route usage sorting and query-source selection behavior.
- [x] 6.2 Add or update UI/instrumentation tests for saved-route card selection, route picker opening, and temporary query validation where feasible.
- [x] 6.3 Run `openspec validate improve-main-route-selection --strict`.
- [x] 6.4 Run `./gradlew build`.
- [x] 6.5 If an emulator is available, manually verify saved-route quick selection, full picker, temporary query, save-as-common route, and usage-based reordering.
