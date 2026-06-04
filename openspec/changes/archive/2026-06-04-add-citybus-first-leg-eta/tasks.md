## 1. Parser and Models

- [x] 1.1 Add a first-leg ETA query model that stores company, route variant, public route, boarding seq, alighting seq, bound, and direction path.
- [x] 1.2 Extend the route result model to represent wait time as an explicit state: loading, available minutes, or unavailable.
- [x] 1.3 Update `CitybusRouteParser` to parse each candidate route table together with its own `showroutep2p(...)` first string argument.
- [x] 1.4 Ensure multi-leg candidates use only the first bus leg for ETA metadata.
- [x] 1.5 Keep otherwise valid route candidates when first-leg metadata is missing, marking wait time as unavailable.
- [x] 1.6 Add parser unit tests for single-leg, multi-leg, missing first-leg metadata, and real HTML fixture candidates containing `showroutep2p(...)`.

## 2. ETA API and Caching

- [x] 2.1 Add a Citybus first-leg ETA component that can query DATA.GOV.HK `route-stop` and ETA endpoints.
- [x] 2.2 Implement route variant to public route conversion by taking the substring before the first `-`.
- [x] 2.3 Implement `O -> outbound` and `I -> inbound` direction path mapping with validation for unsupported values.
- [x] 2.4 Implement route-stop lookup by `(company, route, direction)` and `seq == boardingSeq`.
- [x] 2.5 Implement ETA lookup and filter by route, stop, dir, and seq.
- [x] 2.6 Compute wait minutes from ETA ISO time using current system time, returning `0` for non-positive remaining time and ceiling positive remaining seconds to minutes.
- [x] 2.7 Add in-process route-stop cache with 1 day TTL keyed by `(company, route, direction)`.
- [x] 2.8 Add unit tests for route-stop URL construction, ETA URL construction, response filtering, wait minute rounding, unavailable cases, cache hit, and cache expiry.

## 3. Progressive Query Flow

- [x] 3.1 Introduce a progressive route query API or callback interface that emits initial routes and per-route wait time updates.
- [x] 3.2 Keep `m1=T/F/W` route fetching, partial success, aggregation, deduplication, and default total-duration ordering unchanged.
- [x] 3.3 After aggregation, prioritize ETA completion for the first 5 total-duration-sorted routes.
- [x] 3.4 Display the complete route list when the first 5 ETA requests finish, fail, or the 5 second first-screen wait limit is reached.
- [x] 3.5 Continue ETA completion for remaining routes in the background after the initial list is shown.
- [x] 3.6 Deduplicate identical first-leg ETA requests within a single query and fan out the result to all matching routes.
- [x] 3.7 Limit ETA/network worker concurrency so route count growth does not create unbounded threads or requests.
- [x] 3.8 Ignore stale ETA updates after a newer query starts, the selected route changes, or the Activity is destroyed.
- [x] 3.9 Add repository-level tests for first-5 prioritization, 5 second timeout, background updates, duplicate first-leg fan-out, and ETA failure isolation; verify stale update suppression through Activity query sequence checks.

## 4. Main Screen UI and Sorting

- [x] 4.1 Change the result table header from `预计汽车到站时间\n(分钟)` to `候车时间\n(分钟)` in XML and runtime header updates.
- [x] 4.2 Update `BusRouteAdapter` to render available wait minutes as a number, loading as `...`, and unavailable as `-`.
- [x] 4.3 Keep initial query results sorted by total duration ascending.
- [x] 4.4 Update wait-time sorting so available minutes sort by value and `...` / `-` always sort after available values in both ascending and descending modes.
- [x] 4.5 Re-sort automatically on background ETA updates only when the current sort field is wait time.
- [x] 4.6 Preserve current total-duration ordering when background ETA updates arrive and the current sort field is not wait time.
- [x] 4.7 Add or update sorting and adapter tests for wait-time loading, available, unavailable, ascending, descending, and background update behavior.

## 5. Verification

- [x] 5.1 Run targeted unit tests for parser, ETA API helper, cache, repository progressive flow, sorter, and adapter-visible formatting.
- [x] 5.2 Run `./gradlew testDebugUnitTest`.
- [x] 5.3 Run `./gradlew build`.
- [x] 5.4 If an Android device or emulator is available, verify a saved route query shows `候车时间(分钟)`, first-screen routes with completed, loading, or unavailable ETA, and later row updates.
- [x] 5.5 Android emulator was available, so the no-device manual validation note was not needed.
