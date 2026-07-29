package com.golink.busiscoming

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.golink.busiscoming.ui.main.MainActivity
import com.golink.busiscoming.ui.main.RouteListViewportController
import com.golink.busiscoming.ui.main.RoutePinSwipeLabelRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutePinSwipeVisualInstrumentedTest {
    @Test
    fun swipeRendererDrawsOnlyTextAndLeavesThePageBackgroundTransparent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val card = View(activity).apply { layout(0, 0, 240, 100) }
                val bitmap = Bitmap.createBitmap(240, 100, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 24f
                }

                RoutePinSwipeLabelRenderer.draw(
                    canvas = Canvas(bitmap),
                    cardView = card,
                    label = "置頂",
                    deltaX = 80f,
                    edgePadding = 16f,
                    labelPaint = paint
                )

                assertEquals(Color.TRANSPARENT, bitmap.getPixel(70, 10))
                assertTrue(
                    (0 until bitmap.width).any { x ->
                        (0 until bitmap.height).any { y ->
                            Color.alpha(bitmap.getPixel(x, y)) > 0
                        }
                    }
                )
            }
        }
    }

    @Test
    fun revealPinnedTopAlignsTheFirstCardAfterMoveAnimationsFinish() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var list: RecyclerView
            scenario.onActivity { activity ->
                list = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity)
                    adapter = FixedHeightAdapter(30)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        600
                    )
                }
                activity.findViewById<ViewGroup>(R.id.mainRoot).addView(list)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                (list.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(2, 0)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                (list.adapter as FixedHeightAdapter).move(2, 0)
                RouteListViewportController.revealPinnedTopAfterAnimations(
                    list,
                    animate = true
                )
            }
            SystemClock.sleep(1_000)

            scenario.onActivity {
                val layoutManager = list.layoutManager as LinearLayoutManager
                assertEquals(0, layoutManager.findFirstVisibleItemPosition())
                assertEquals(list.paddingTop, layoutManager.findViewByPosition(0)?.top)
            }
        }
    }

    private class FixedHeightAdapter(
        count: Int
    ) : RecyclerView.Adapter<FixedHeightAdapter.Holder>() {
        private val items = (0 until count).toMutableList()

        init {
            setHasStableIds(true)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(View(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    120
                )
            })
        }

        override fun onBindViewHolder(holder: Holder, position: Int) = Unit

        override fun getItemCount(): Int = items.size

        override fun getItemId(position: Int): Long = items[position].toLong()

        fun move(from: Int, to: Int) {
            items.add(to, items.removeAt(from))
            notifyItemMoved(from, to)
        }

        class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)
    }
}
