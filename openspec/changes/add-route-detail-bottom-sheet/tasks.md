## 1. P2P Metadata Model

- [x] 1.1 Add route detail query and plan models for `rawInfo`, `ginfo`, `lid`, `lang`, and all parsed bus legs.
- [x] 1.2 Refactor `CitybusRouteParser` so `showroutep2p(...)` parsing returns the full P2P plan while preserving the existing first-leg ETA behavior.
- [x] 1.3 Store detail query metadata on each `BusRouteOption` without changing existing route name, price, total duration, wait time, sorting, or aggregation behavior.
- [x] 1.4 Add parser unit tests for single-leg, multi-leg, missing detail metadata, `ginfo`, `lid`, and first-leg ETA compatibility.

## 2. Detail Fetching And Parsing

- [x] 2.1 Add a Citybus route detail repository/service that builds `getp2pstopinroute.php` URLs from `rawInfo`, `ginfo`, `lid`, and `lang`.
- [x] 2.2 Implement HTTP fetching with the existing Citybus request style and no DATA.GOV.HK detail fallback.
- [x] 2.3 Implement HTML parsing for `stopclick1(...)` station rows, extracting station name, stop id, station sequence, route variant, latitude, and longitude.
- [x] 2.4 Group parsed stations by the `rawInfo` bus leg order and classify each station as boarding, via, or alighting using `boardingSeq` and `alightingSeq`.
- [x] 2.5 Preserve multi-leg transfer boundaries and parse enough metadata to show each bus leg independently in the UI, including route number and reliable direction text when the interface returns it.
- [x] 2.6 Add station display-name normalization that uses only the first comma-separated segment while preserving the raw station name in the structured model.
- [x] 2.7 Add unit tests using real HTML fixtures for the verified `N118` single-leg sample and the `N8P -> N969` multi-leg sample provided by the user.
- [x] 2.8 Add failure tests for empty response, missing station rows, malformed `stopclick1(...)`, missing boarding station, and missing alighting station.

## 3. Detail Cache

- [x] 3.1 Add an in-process route detail cache keyed by complete `rawInfo + lang`.
- [x] 3.2 Cache only successfully parsed structured details with a 1 day TTL.
- [x] 3.3 Ensure failed, empty, or unparsable responses are not cached.
- [x] 3.4 Add cache tests for hit, expiry, language isolation, and failure-not-cached behavior.

## 4. Bottom Sheet UI

- [x] 4.1 Add route card click callbacks in `BusRouteAdapter` and wire them from `MainActivity`.
- [x] 4.2 Add a Material bottom sheet for route details with summary, loading, success, failure, retry, and close states.
- [x] 4.3 Add detail UI models and adapter/view binding for route legs, boarding station, alighting station, and via stations.
- [x] 4.4 Implement default folded state for every leg and independent expand/collapse for via stations.
- [x] 4.5 Hide the expand control when a leg has no via stations.
- [x] 4.6 Implement the confirmed route-detail timeline UI: per-leg colored thick vertical line, boarding/alighting endpoints, via stations between endpoints, and grey dotted transfer connectors.
- [x] 4.7 Display direction as `往 XX方向` only when reliable direction text is returned, and hide it when unavailable.
- [x] 4.8 Exclude out-of-scope UI elements from the detail sheet: operation hours, timetable, arrival time, walking navigation, favorite, screenshot, share, follow-route, and reminder actions.
- [x] 4.9 Keep the underlying result list and current sort state intact while the bottom sheet is open and after it is dismissed.
- [x] 4.10 Ignore stale detail responses when the bottom sheet is closed or a different route detail request becomes active.

## 5. Verification

- [x] 5.1 Add or update unit tests for route detail metadata, parser behavior, detail fetching, cache behavior, display-name normalization, direction visibility, and folded UI state logic.
- [x] 5.2 Run `./gradlew build`.
- [x] 5.3 If an Android device or emulator is available, verify tapping a route card opens the bottom sheet, shows loading, loads details, folds via stations by default, expands/collapses per leg, retries on simulated failure, and returns to the same route list state after dismissal.
- [x] 5.4 Run `openspec validate add-route-detail-bottom-sheet --strict`.
