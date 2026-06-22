package com.example.busiscoming

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.busiscoming.data.model.Place
import com.example.busiscoming.data.repository.PlaceSearchRepository
import com.example.busiscoming.ui.common.PlaceInputController
import com.example.busiscoming.ui.edit.RouteEditActivity
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaceInputControllerInstrumentedTest {
    @Test
    fun inlineCandidatesLimitResultsIgnoreStaleSearchAndRemainScrollable() {
        ActivityScenario.launch(RouteEditActivity::class.java).use { scenario ->
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
                val insets = checkNotNull(ViewCompat.getRootWindowInsets(candidateList))
                val rowHeight = dp(activity, 48)
                val visibleHeight = candidateList.rootView.height -
                    insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val allowedHeight = maxOf((visibleHeight * 0.4f).toInt(), rowHeight * 3)
                assertTrue(candidateList.layoutParams.height >= rowHeight * 3)
                assertTrue(candidateList.layoutParams.height <= allowedHeight)
                candidateList.requestRectangleOnScreen(
                    android.graphics.Rect(0, 0, candidateList.width, candidateList.height),
                    true
                )
                saveScreenshot("inline-place-candidates")

                @Suppress("UNCHECKED_CAST")
                val adapter = candidateList.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
                val holder = adapter.createViewHolder(candidateList, 0)
                adapter.bindViewHolder(holder, 0)
                assertEquals("新候選1", (holder.itemView as TextView).text.toString())
                holder.itemView.performClick()
                assertEquals("新候選1", controller.selectedPlace?.name)
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

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
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
