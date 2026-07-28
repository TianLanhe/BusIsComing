package com.golink.busiscoming

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.model.PinLevel
import com.golink.busiscoming.data.model.RouteFingerprintResolution
import com.golink.busiscoming.ui.main.BusRouteCardActions
import com.golink.busiscoming.ui.main.BusRouteCardBinder
import com.golink.busiscoming.ui.main.FirstRunRoutePreview
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.RouteCardItem
import com.golink.busiscoming.ui.main.RoutePinAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutePinAccessibilityInstrumentedTest {
    @Test
    fun pinStateAndCustomActionsAreExposedAndReboundWithoutStaleActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.mainRoot)
                val card = LayoutInflater.from(activity)
                    .inflate(R.layout.item_bus_route, root, false)
                root.addView(card)
                val binder = BusRouteCardBinder(card)
                var performedAction: RoutePinAction? = null
                val actions = BusRouteCardActions(
                    routeClick = {},
                    pinAction = { _, action -> performedAction = action }
                )

                binder.bind(item(PinLevel.UNPINNED), actions)
                assertEquals(null, ViewCompat.getStateDescription(card))
                val temporaryActionId = actionId(
                    card,
                    activity.getString(R.string.route_pin_action_temporary)
                )
                assertTrue(card.performAccessibilityAction(temporaryActionId, null))
                assertEquals(RoutePinAction.PIN_TEMPORARY, performedAction)

                performedAction = null
                binder.bind(item(PinLevel.TEMPORARY), actions)
                assertEquals(
                    activity.getString(R.string.route_pin_state_temporary),
                    ViewCompat.getStateDescription(card)
                )
                assertFalse(
                    actionLabels(card).contains(
                        activity.getString(R.string.route_pin_action_temporary)
                    )
                )
                assertTrue(
                    actionLabels(card).containsAll(
                        listOf(
                            activity.getString(R.string.route_pin_action_persistent),
                            activity.getString(R.string.route_pin_action_cancel)
                        )
                    )
                )
                val persistentActionId = actionId(
                    card,
                    activity.getString(R.string.route_pin_action_persistent)
                )
                assertTrue(card.performAccessibilityAction(persistentActionId, null))
                assertEquals(RoutePinAction.PIN_PERSISTENT, performedAction)

                performedAction = null
                binder.bind(item(PinLevel.PERSISTENT), actions)
                assertEquals(
                    activity.getString(R.string.route_pin_state_persistent),
                    ViewCompat.getStateDescription(card)
                )
                assertEquals(
                    listOf(activity.getString(R.string.route_pin_action_cancel)),
                    actionLabels(card)
                )
                val cancelActionId = actionId(
                    card,
                    activity.getString(R.string.route_pin_action_cancel)
                )
                assertTrue(card.performAccessibilityAction(cancelActionId, null))
                assertEquals(RoutePinAction.CANCEL, performedAction)
                assertEquals(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO,
                    card.findViewById<View>(R.id.busPersistentPinBookmark)
                        .importantForAccessibility
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    private fun item(level: PinLevel): RouteCardItem {
        return RouteCardItem(
            route = FirstRunRoutePreview.route(),
            fingerprintResolution = RouteFingerprintResolution.Eligible("v1|route"),
            pinLevel = level,
            pinnedAt = if (level == PinLevel.UNPINNED) null else 1L,
            stableId = "route:v1|route"
        )
    }

    private fun actionLabels(card: View): List<String> {
        val node = card.createAccessibilityNodeInfo()
        return node.actionList.mapNotNull { it.label?.toString() }
    }

    private fun actionId(card: View, label: String): Int {
        val node = card.createAccessibilityNodeInfo()
        return node.actionList.single { it.label?.toString() == label }.id
    }
}
