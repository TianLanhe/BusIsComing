package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFeatureContractTest {
    private val coordinatorKt =
        File("src/main/java/com/golink/busiscoming/data/location/CurrentLocationCoordinator.kt").readText()
    private val mainActivityKt =
        File("src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt").readText()
    private val routeEditActivityKt =
        File("src/main/java/com/golink/busiscoming/ui/edit/RouteEditActivity.kt").readText()
    private val searchFragmentKt =
        File("src/main/java/com/golink/busiscoming/ui/main/SearchFragment.kt").readText()
    private val placeInputControllerKt =
        File("src/main/java/com/golink/busiscoming/ui/common/PlaceInputController.kt").readText()
    private val systemLocationUtilsKt =
        File("src/main/java/com/golink/busiscoming/data/location/SystemLocationUtils.kt").readText()
    private val googleResolverKt =
        File("src/main/java/com/golink/busiscoming/data/location/GoogleReverseGeocodingPlaceNameResolver.kt").readText()
    private val routeEditLayoutXml =
        File("src/main/res/layout/activity_route_edit.xml").readText()
    private val placePairLayoutXml =
        File("src/main/res/layout/view_place_pair_editor.xml").readText()
    private val stringsXml =
        File("src/main/res/values/strings.xml").readText()

    @Test
    fun coordinatorKeepsLocationPolicyCentralized() {
        assertTrue(coordinatorKt.contains("CurrentLocationResult.NoPermission"))
        assertTrue(coordinatorKt.contains("SNAPSHOT_MAX_AGE_MS = 30_000L"))
        assertTrue(coordinatorKt.contains("LOCATION_TIMEOUT_MS = 3_000L"))
        assertTrue(coordinatorKt.contains("cachedSnapshot?.takeIf { isFresh(it) }"))
        assertTrue(coordinatorKt.contains("fusedLocationClient.lastLocation"))
        assertTrue(coordinatorKt.contains("getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY"))
        assertTrue(coordinatorKt.contains("pendingCallbacks"))
        assertTrue(coordinatorKt.contains("finish(CurrentLocationResult.Timeout)"))
        assertTrue(coordinatorKt.contains("CurrentLocationResult.Unavailable"))
        assertFalse(coordinatorKt.contains("LocationManager"))
    }

    @Test
    fun mainNearbyRouteSelectionDoesNotPersistOrOverrideManualChoice() {
        assertTrue(mainActivityKt.contains("routeConfigs.size < 2"))
        assertTrue(mainActivityKt.contains("manualRouteSelectionGeneration == generation"))
        assertTrue(mainActivityKt.contains("NearbyRouteSelectionPolicy.selectRoute"))
        assertTrue(mainActivityKt.contains("nearbySelectedRouteId = selectedRoute?.id"))
        assertTrue(mainActivityKt.contains("R.string.nearby"))
        assertTrue(mainActivityKt.contains("nearbySelectedRouteId = null"))
        assertTrue(mainActivityKt.contains("recordUsage: Boolean = true"))
        assertTrue(mainActivityKt.contains("recordUsage = false"))
    }

    @Test
    fun mainRanksSavedRoutesWithoutOverridingManualSelection() {
        assertTrue(mainActivityKt.contains("SavedRouteLocationSorter.sort"))
        assertTrue(mainActivityKt.contains("currentLocationSnapshot = result.snapshot"))
        assertTrue(mainActivityKt.contains("manualRouteSelectionGeneration == generation"))
        assertFalse(
            mainActivityKt.contains(
                "isFinishing || isDestroyed || manualRouteSelectionGeneration != generation"
            )
        )
    }

    @Test
    fun mainPersistsAndConsumesSavedRouteUsageSessionState() {
        assertTrue(mainActivityKt.contains("SavedRouteUsageSession("))
        assertTrue(mainActivityKt.contains("savedRouteUsageSession.consumeUsageRecord(route.id)"))
        assertTrue(mainActivityKt.contains("override fun onSaveInstanceState(outState: Bundle)"))
        assertTrue(mainActivityKt.contains("STATE_SELECTED_ROUTE_ID"))
        assertTrue(mainActivityKt.contains("STATE_RECORDED_USAGE_ROUTE_ID"))
    }

    @Test
    fun mainDoesNotExposeSavedRouteRankingKeys() {
        assertFalse(mainActivityKt.contains("text = route.usageCount"))
        assertFalse(mainActivityKt.contains("text = route.lastUsedAt"))
        assertFalse(mainActivityKt.contains("PlaceDistanceFormatter.compact"))
    }

    @Test
    fun permissionFallbacksAndSystemSettingsRecoveryAreExplicit() {
        assertTrue(mainActivityKt.contains("shownLocationFallbackToasts.add(type)"))
        assertTrue(mainActivityKt.contains("R.string.location_fallback_permission"))
        assertTrue(mainActivityKt.contains("R.string.location_fallback_unavailable"))
        assertTrue(mainActivityKt.contains("R.string.location_fallback_imprecise"))
        assertTrue(mainActivityKt.contains("locationPermissionStateStore.setAutoRequestDenied(true)"))
        assertTrue(routeEditActivityKt.contains("locationPermissionStateStore.setAutoRequestDenied(true)"))
        assertTrue(mainActivityKt.contains("promptLocationSettingsForCurrentPlace"))
        assertTrue(routeEditActivityKt.contains("promptLocationSettingsForCurrentOrigin"))
        assertTrue(systemLocationUtilsKt.contains("Settings.ACTION_LOCATION_SOURCE_SETTINGS"))
    }

    @Test
    fun currentPlaceOriginFlowsProtectExistingUserInput() {
        assertTrue(routeEditActivityKt.contains("if (isAuto && originTouchedByUser) return"))
        assertTrue(routeEditActivityKt.contains("currentPlaceGeneration != generation"))
        assertTrue(routeEditActivityKt.contains("GoogleReverseGeocodingPlaceNameResolver(this)"))
        assertTrue(routeEditActivityKt.contains("placeNameResolver.resolve(result.snapshot)"))
        assertTrue(routeEditActivityKt.contains("placeNameResolver.prefetch(result.snapshot)"))
        assertTrue(routeEditActivityKt.contains("name = nameResult.addressName"))
        assertTrue(routeEditActivityKt.contains("latitude = result.snapshot.latitude"))
        assertTrue(routeEditActivityKt.contains("CURRENT_PLACE_TOTAL_TIMEOUT_MS = 5_000L"))
        assertTrue(routeEditActivityKt.contains("if (isClone)"))
        assertTrue(routeEditActivityKt.contains("requestCandidateLocationSnapshotIfPermitted()"))
        assertTrue(routeEditActivityKt.contains("requestCurrentOrigin(isAuto = true)"))
        assertTrue(routeEditActivityKt.contains("R.string.current_location_manual_origin"))
        assertTrue(mainActivityKt.contains("GoogleReverseGeocodingPlaceNameResolver(this)"))
        assertTrue(mainActivityKt.contains("placeNameResolver.resolve(result.snapshot)"))
        assertTrue(mainActivityKt.contains("name = nameResult.addressName"))
        assertTrue(mainActivityKt.contains("latitude = result.snapshot.latitude"))
        assertTrue(mainActivityKt.contains("CURRENT_PLACE_TOTAL_TIMEOUT_MS = 5_000L"))
        assertTrue(searchFragmentKt.contains("beginAutoRequest"))
        assertTrue(searchFragmentKt.contains("SearchCurrentPlaceRequestState"))
        assertTrue(searchFragmentKt.contains("currentPlaceRequestState.finish(generation)"))
        assertTrue(searchFragmentKt.contains("originController?.setExternalLoading(true)"))
        assertTrue(searchFragmentKt.contains("invalidateCurrentPlaceRequest()"))
    }

    @Test
    fun googleReverseGeocodingContractIsScopedAndAttributionIsInputOnly() {
        assertTrue(googleResolverKt.contains("languageSnapshot.googleLanguageCode"))
        assertTrue(googleResolverKt.contains("languageSnapshot.googleRegionCode"))
        assertTrue(googleResolverKt.contains("X-Goog-Api-Key"))
        assertTrue(googleResolverKt.contains("X-Goog-FieldMask"))
        assertTrue(googleResolverKt.contains("X-Android-Package"))
        assertTrue(googleResolverKt.contains("X-Android-Cert"))
        assertTrue(googleResolverKt.contains("NAME_RESOLUTION_TIMEOUT_MS = 3_000L"))
        assertTrue(googleResolverKt.contains("CACHE_TTL_MS = 10 * 60 * 1000L"))
        assertTrue(googleResolverKt.contains("selectStreetAddress(results)"))
        assertTrue(googleResolverKt.contains("selectNonCoarseAddress(results)"))
        assertFalse(googleResolverKt.contains("MockPlaceNameResolver"))

        assertTrue(stringsXml.contains("地址由 Google Maps 提供"))
        assertFalse(routeEditLayoutXml.contains("PlacePairEditorView"))
        assertTrue(routeEditLayoutXml.contains("@+id/originAttributionText"))
        assertTrue(placePairLayoutXml.contains("@+id/placePairOriginAttribution"))
        assertTrue(placePairLayoutXml.contains("@string/google_maps_address_attribution"))
        assertTrue(routeEditActivityKt.contains("showOriginAttribution(nameResult.attribution)"))
        assertTrue(routeEditActivityKt.contains("hideOriginAttribution()"))
        assertTrue(searchFragmentKt.contains("placeEditor.originAttribution"))
        assertTrue(searchFragmentKt.contains("result.attribution == PlaceAttribution.GOOGLE_MAPS"))
    }

    @Test
    fun candidateDistancePresentationDoesNotChangeSelectionSemantics() {
        assertTrue(placeInputControllerKt.contains("setCurrentLocationSnapshot"))
        assertTrue(placeInputControllerKt.contains("notifyDataSetChanged()"))
        assertTrue(placeInputControllerKt.contains("GeoDistanceCalculator.distanceMeters"))
        assertTrue(placeInputControllerKt.contains("PlaceDistanceFormatter.compact"))
        assertTrue(placeInputControllerKt.contains("R.string.distance_from_current"))
        assertTrue(placeInputControllerKt.contains("rowView.contentDescription"))
        assertTrue(placeInputControllerKt.contains("input.setText(place.name, false)"))
        assertFalse(placeInputControllerKt.contains("sortBy"))
        assertFalse(placeInputControllerKt.contains("sortedBy"))
    }
}
