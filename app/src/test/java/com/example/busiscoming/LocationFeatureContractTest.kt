package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFeatureContractTest {
    private val coordinatorKt =
        File("src/main/java/com/example/busiscoming/data/location/CurrentLocationCoordinator.kt").readText()
    private val mainActivityKt =
        File("src/main/java/com/example/busiscoming/ui/main/MainActivity.kt").readText()
    private val routeEditActivityKt =
        File("src/main/java/com/example/busiscoming/ui/edit/RouteEditActivity.kt").readText()
    private val temporarySheetKt =
        File("src/main/java/com/example/busiscoming/ui/main/TemporaryRouteBottomSheet.kt").readText()
    private val placeInputControllerKt =
        File("src/main/java/com/example/busiscoming/ui/common/PlaceInputController.kt").readText()
    private val systemLocationUtilsKt =
        File("src/main/java/com/example/busiscoming/data/location/SystemLocationUtils.kt").readText()
    private val googleResolverKt =
        File("src/main/java/com/example/busiscoming/data/location/GoogleReverseGeocodingPlaceNameResolver.kt").readText()
    private val routeEditLayoutXml =
        File("src/main/res/layout/activity_route_edit.xml").readText()
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
        assertTrue(mainActivityKt.contains("manualRouteSelectionGeneration != generation"))
        assertTrue(mainActivityKt.contains("NearbyRouteSelectionPolicy.selectRoute"))
        assertTrue(mainActivityKt.contains("nearbySelectedRouteId = selectedRoute?.id"))
        assertTrue(mainActivityKt.contains("text = \"附近\""))
        assertTrue(mainActivityKt.contains("nearbySelectedRouteId = null"))
        assertTrue(mainActivityKt.contains("recordUsage: Boolean = true"))
        assertTrue(mainActivityKt.contains("recordUsage = false"))
    }

    @Test
    fun permissionFallbacksAndSystemSettingsRecoveryAreExplicit() {
        assertTrue(mainActivityKt.contains("shownLocationFallbackToasts.add(type)"))
        assertTrue(mainActivityKt.contains("未允許定位，已按常用排序選擇路線"))
        assertTrue(mainActivityKt.contains("暫時無法取得目前位置，已按常用排序選擇路線"))
        assertTrue(mainActivityKt.contains("目前位置不夠精確，已按常用排序選擇路線"))
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
        assertTrue(routeEditActivityKt.contains("暫時無法取得目前位置，請手動選擇起點"))
        assertTrue(mainActivityKt.contains("GoogleReverseGeocodingPlaceNameResolver(this)"))
        assertTrue(mainActivityKt.contains("placeNameResolver.resolve(result.snapshot)"))
        assertTrue(mainActivityKt.contains("name = nameResult.addressName"))
        assertTrue(mainActivityKt.contains("latitude = result.snapshot.latitude"))
        assertTrue(mainActivityKt.contains("CURRENT_PLACE_TOTAL_TIMEOUT_MS = 5_000L"))
        assertTrue(temporarySheetKt.contains("originTouchedByUser = false"))
        assertTrue(temporarySheetKt.contains("requestCurrentOriginIfNeeded(isAuto = true)"))
        assertTrue(temporarySheetKt.contains("if (isAuto && originTouchedByUser) return"))
        assertTrue(temporarySheetKt.contains("currentPlaceGeneration != generation"))
    }

    @Test
    fun googleReverseGeocodingContractIsScopedAndAttributionIsInputOnly() {
        assertTrue(googleResolverKt.contains("DEFAULT_LANGUAGE_CODE = \"zh-Hant\""))
        assertTrue(googleResolverKt.contains("REGION_CODE = \"HK\""))
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
        assertTrue(routeEditLayoutXml.contains("@+id/originAttributionText"))
        assertTrue(routeEditLayoutXml.contains("@string/google_maps_address_attribution"))
        assertTrue(routeEditActivityKt.contains("showOriginAttribution(nameResult.attribution)"))
        assertTrue(routeEditActivityKt.contains("hideOriginAttribution()"))
        assertTrue(temporarySheetKt.contains("attributionText()"))
        assertTrue(temporarySheetKt.contains("showOriginAttribution(result.attribution)"))
        assertTrue(temporarySheetKt.contains("hideOriginAttribution()"))
    }

    @Test
    fun candidateDistancePresentationDoesNotChangeSelectionSemantics() {
        assertTrue(placeInputControllerKt.contains("setCurrentLocationSnapshot"))
        assertTrue(placeInputControllerKt.contains("notifyDataSetChanged()"))
        assertTrue(placeInputControllerKt.contains("GeoDistanceCalculator.distanceMeters"))
        assertTrue(placeInputControllerKt.contains("PlaceDistanceFormatter.compact"))
        assertTrue(placeInputControllerKt.contains("PlaceDistanceFormatter.accessibility"))
        assertTrue(placeInputControllerKt.contains("rowView.contentDescription"))
        assertTrue(placeInputControllerKt.contains("input.setText(place.name, false)"))
        assertFalse(placeInputControllerKt.contains("sortBy"))
        assertFalse(placeInputControllerKt.contains("sortedBy"))
    }
}
