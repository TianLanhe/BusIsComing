## 1. P2P Plan Model And Parser

- [x] 1.1 Add lightweight P2P route plan models for `rawInfo`, `lang`, and all parsed bus legs.
- [x] 1.2 Refactor `CitybusRouteParser` so `showroutep2p(...)` parsing returns the full P2P plan while preserving existing route, price, duration, walking distance, and first-leg ETA behavior.
- [x] 1.3 Derive first-leg ETA data from the first parsed P2P leg instead of parsing `showroutep2p(...)` with a separate rule path.
- [x] 1.4 Store route card preview metadata on each `BusRouteOption`, including enough data to resolve first-leg boarding and last-leg alighting stops.
- [x] 1.5 Add parser tests for single-leg plans, multi-leg plans, missing metadata, malformed leg fields, route variant to public route mapping, and first-leg ETA compatibility.

## 2. Stop Resolver And Caches

- [x] 2.1 Extract reusable Citybus stop resolution logic so ETA and route-card preview can share route-stop lookup behavior without changing current ETA semantics.
- [x] 2.2 Add route-stop cache keyed by company, public route, and direction path with 1 day TTL.
- [x] 2.3 Add DATA.GOV.HK `stop/{stop_id}` fetching and parsing for Traditional Chinese station names.
- [x] 2.4 Add stop name cache keyed by company, stop id, and language with 1 day TTL.
- [x] 2.5 Add preview cache keyed by complete `rawInfo + lang` with 1 day TTL.
- [x] 2.6 Ensure failed, empty, missing-seq, missing-name, or unparsable route-stop/stop/preview results are not cached.
- [x] 2.7 Add unit tests for route-stop URL construction, stop URL construction, station-name parsing, cache hit, cache expiry, language isolation, and failure-not-cached behavior.

## 3. Preview Resolution And Repository Flow

- [x] 3.1 Add a route-card stop preview resolver that maps first P2P leg boarding seq and last P2P leg alighting seq to display names.
- [x] 3.2 Collect unique route-stop and stop-name requests across all returned routes before issuing network calls.
- [x] 3.3 Limit stop-preview network concurrency and avoid one thread or request pipeline per route card.
- [x] 3.4 Start preview completion only after initial route results are ready, without blocking route list display.
- [x] 3.5 Add callbacks or update flow so `MainActivity` can receive per-route preview updates by stable route id.
- [x] 3.6 Ignore stale preview updates when the user starts a new query, changes the selected saved route, clears results, or leaves the main screen.
- [x] 3.7 Update route aggregation/deduplication so routes with different `rawInfo` or different derived first/last stops remain separate visible results.
- [x] 3.8 Add repository tests for request aggregation, concurrency limiting, progressive preview updates, stale update suppression, preview failure isolation, and deduplication by visible station difference.

## 4. Card UI And Binding

- [x] 4.1 Add route-card UI state for optional stop preview with boarding and alighting display names.
- [x] 4.2 Update `item_bus_route.xml` to include one stop-preview row between the route/ETA row and the price/duration/walking information area.
- [x] 4.3 Bind successful previews as `上車 A站  →  下車 B站`.
- [x] 4.4 Hide the stop-preview row while preview is pending, unavailable, failed, or missing metadata.
- [x] 4.5 Ensure long station names stay on one readable line with ellipsize or equivalent constraints and do not overlap route, ETA, price, duration, or walking text.
- [x] 4.6 Keep the stop-preview row compatible with `refine-route-result-cards` layout changes if that change is applied before or alongside this one.

## 5. Verification

- [x] 5.1 Add or update unit tests covering P2P full-plan parsing, preview derivation, resolver caching, failure handling, deduplication, and adapter binding text.
- [x] 5.2 Run `openspec validate add-route-card-stop-preview --strict`.
- [x] 5.3 Run `./gradlew build`.
- [x] 5.4 If an Android device or emulator is available, verify that route cards appear before previews, previews progressively fill in, unavailable previews stay hidden, long station names do not overlap, and old query previews do not update the new list.
