package com.golink.busiscoming

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.golink.busiscoming.data.location.CurrentLocationSnapshot
import com.golink.busiscoming.data.model.Place
import com.golink.busiscoming.data.repository.PlaceSearchRepository
import com.golink.busiscoming.ui.common.PlaceInputController
import com.golink.busiscoming.ui.edit.RouteEditActivity
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors
import java.io.FileInputStream
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceInputControllerInstrumentedTest {
    @Test
    fun routeEditKeepsHistoricalInputGeometryAndMaterialLocationTool() {
        ActivityScenario.launch<RouteEditActivity>(prefilledRouteEditIntent()).use { scenario ->
            scenario.onActivity { activity ->
                val originLayout = activity.findViewById<TextInputLayout>(R.id.originInputLayout)
                val destinationLayout =
                    activity.findViewById<TextInputLayout>(R.id.destinationInputLayout)
                val originInput =
                    activity.findViewById<MaterialAutoCompleteTextView>(R.id.originInput)
                val destinationInput =
                    activity.findViewById<MaterialAutoCompleteTextView>(R.id.destinationInput)
                val originLoading = activity.findViewById<LinearLayout>(R.id.originSearchLoading)
                val destinationLoading =
                    activity.findViewById<LinearLayout>(R.id.destinationSearchLoading)
                val originCandidates =
                    activity.findViewById<RecyclerView>(R.id.originCandidateList)
                val swap = activity.findViewById<View>(R.id.swapPlacesButton)

                assertTrue(originInput.height >= xmlDp(activity, 56))
                assertTrue(destinationInput.height >= xmlDp(activity, 56))
                assertEquals(xmlDp(activity, 16), originInput.paddingStart)
                assertEquals(xmlDp(activity, 16), originInput.paddingEnd)
                assertEquals(
                    activity.getString(R.string.place_search_helper),
                    originLayout.helperText
                )
                assertEquals(
                    activity.getString(R.string.place_search_helper),
                    destinationLayout.helperText
                )
                assertEquals(TextInputLayout.END_ICON_CUSTOM, originLayout.endIconMode)
                assertTrue(originLayout.endIconDrawable != null)
                assertEquals(
                    activity.getString(R.string.use_my_location),
                    originLayout.endIconContentDescription
                )
                assertEquals(xmlDp(activity, 14), destinationLayout.marginTop())
                assertEquals(xmlDp(activity, 6), originCandidates.marginTop())
                assertEquals(xmlDp(activity, 18), originLoading.getChildAt(0).layoutParams.width)
                assertEquals(xmlDp(activity, 18), destinationLoading.getChildAt(0).layoutParams.width)
                assertEquals(xmlDp(activity, 48), swap.layoutParams.width)
                assertEquals(xmlDp(activity, 48), swap.layoutParams.height)
                assertTrue(swap.background != null)
            }
        }
    }

    @Test
    fun inlineCandidatesLimitResultsIgnoreStaleSearchAndRemainScrollable() {
        ActivityScenario.launch<RouteEditActivity>(prefilledRouteEditIntent()).use { scenario ->
            val executor = Executors.newSingleThreadExecutor()
            lateinit var controller: PlaceInputController
            lateinit var input: MaterialAutoCompleteTextView
            lateinit var candidateList: RecyclerView

            scenario.onActivity { activity ->
                val root = activity.findViewById<ViewGroup>(R.id.routeEditContent)
                val inputLayout = TextInputLayout(activity).apply {
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                }
                input = MaterialAutoCompleteTextView(activity).apply {
                    id = R.id.instrumentedPlaceInput
                }
                inputLayout.addView(input)
                val loading = View(activity)
                candidateList = RecyclerView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                root.addView(inputLayout)
                root.addView(loading)
                root.addView(candidateList)

                controller = PlaceInputController(
                    context = activity,
                    input = input,
                    inputLayout = inputLayout,
                    loadingView = loading,
                    candidateList = candidateList,
                    placeSearchRepository = object : PlaceSearchRepository {
                        override fun searchPlaces(keyword: String): List<Place> {
                            return if (keyword == "舊") {
                                Thread.sleep(400)
                                listOf(place("舊候選"))
                            } else {
                                (1..120).map { index -> place("新候選$index") }
                            }
                        }
                    },
                    mainHandler = Handler(Looper.getMainLooper()),
                    searchExecutor = executor,
                    isActive = { true }
                )
            }

            onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.instrumentedPlaceInput))
                .perform(scrollTo(), click())
            scenario.onActivity {
                input.setText("舊")
            }
            Thread.sleep(350)
            scenario.onActivity {
                input.setText("新")
            }
            waitUntil {
                var ready = false
                scenario.onActivity {
                    ready = candidateList.visibility == View.VISIBLE &&
                        candidateList.adapter?.itemCount == 100
                }
                ready
            }

            scenario.onActivity { activity ->
                assertEquals(100, candidateList.adapter?.itemCount)
                assertTrue(candidateList.isNestedScrollingEnabled)
                assertTrue(candidateList.background != null)
                val rowHeight = dp(activity, 52)
                val visibleRows = candidateList.layoutParams.height / rowHeight
                assertEquals(0, candidateList.layoutParams.height % rowHeight)
                assertTrue(visibleRows in 3..6)
                candidateList.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, candidateList.width, candidateList.height),
                    true
                )
                saveScreenshot("inline-place-candidates")

                @Suppress("UNCHECKED_CAST")
                val adapter = candidateList.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
                val holder = adapter.createViewHolder(candidateList, 0)
                adapter.bindViewHolder(holder, 0)
                val row = holder.itemView as LinearLayout
                assertEquals(rowHeight, row.layoutParams.height)
                assertTrue(row.background != null)
                val nameView = row.getChildAt(0) as TextView
                val distanceContainer = row.getChildAt(1) as LinearLayout
                assertEquals("新候選1", nameView.text.toString())
                assertEquals(
                    TextView(activity).apply { textSize = 16f }.textSize,
                    nameView.textSize,
                    0.1f
                )
                assertEquals(View.GONE, distanceContainer.visibility)

                controller.setCurrentLocationSnapshot(
                    CurrentLocationSnapshot(
                        latitude = 22.3,
                        longitude = 114.2,
                        accuracyMeters = 20f,
                        elapsedRealtimeMillis = android.os.SystemClock.elapsedRealtime()
                    )
                )
                adapter.bindViewHolder(holder, 0)
                val distanceIcon = distanceContainer.getChildAt(0) as ImageView
                val distanceView = distanceContainer.getChildAt(1) as TextView
                assertEquals(View.VISIBLE, distanceContainer.visibility)
                assertEquals(dp(activity, 14), distanceIcon.layoutParams.width)
                assertEquals("0m", distanceView.text.toString())
                assertEquals(
                    TextView(activity).apply { textSize = 13f }.textSize,
                    distanceView.textSize,
                    0.1f
                )
                assertTrue(row.contentDescription.toString().contains("距離目前位置 0 米"))
                assertFalse(row.contentDescription.toString().contains("•"))
                holder.itemView.performClick()
                assertEquals("新候選1", controller.selectedPlace?.name)
                assertEquals("新候選1", input.text.toString())
            }

            scenario.onActivity {
                controller.dispose()
            }
            executor.shutdownNow()
        }
    }

    private fun waitUntil(timeoutMillis: Long = 3_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        assertTrue("Timed out waiting for candidate results", condition())
    }

    private fun place(name: String): Place {
        return Place(name = name, latitude = 22.3, longitude = 114.2)
    }

    private fun prefilledRouteEditIntent(): Intent {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Intent(context, RouteEditActivity::class.java).apply {
            putExtra(RouteEditActivity.EXTRA_PREFILL_NAME, "測試路線")
            putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_NAME, "測試起點")
            putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_LATITUDE, 22.3)
            putExtra(RouteEditActivity.EXTRA_PREFILL_ORIGIN_LONGITUDE, 114.2)
            putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_NAME, "測試終點")
            putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_LATITUDE, 22.4)
            putExtra(RouteEditActivity.EXTRA_PREFILL_DESTINATION_LONGITUDE, 114.3)
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun xmlDp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }

    private fun View.marginTop(): Int {
        return (layoutParams as ViewGroup.MarginLayoutParams).topMargin
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val command = instrumentation.uiAutomation.executeShellCommand(
            "screencap -p /sdcard/Download/BusIsComing-$name.png"
        )
        FileInputStream(command.fileDescriptor).use { input ->
            input.readBytes()
        }
    }
}
