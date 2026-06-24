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
        assertTrue(routeEditActivityKt.contains("MockPlaceNameResolver.resolve"))
        assertTrue(routeEditActivityKt.contains("if (isClone)"))
        assertTrue(routeEditActivityKt.contains("requestCandidateLocationSnapshotIfPermitted()"))
        assertTrue(routeEditActivityKt.contains("requestCurrentOrigin(isAuto = true)"))
        assertTrue(routeEditActivityKt.contains("暫時無法取得目前位置，請手動選擇起點"))
        assertTrue(temporarySheetKt.contains("originTouchedByUser = false"))
        assertTrue(temporarySheetKt.contains("requestCurrentOriginIfNeeded(isAuto = true)"))
        assertTrue(temporarySheetKt.contains("if (isAuto && originTouchedByUser) return"))
        assertTrue(temporarySheetKt.contains("currentPlaceGeneration != generation"))
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
