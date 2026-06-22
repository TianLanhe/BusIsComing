package com.example.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailBottomSheetContractTest {
    private val source =
        File("src/main/java/com/example/busiscoming/ui/main/RouteDetailBottomSheet.kt").readText()

    @Test
    fun detailContentUsesBottomSheetAwareNestedScrollingContainer() {
        assertTrue(source.contains("import androidx.core.widget.NestedScrollView"))
        assertTrue(source.contains("val detailScroll: NestedScrollView"))
        assertTrue(source.contains("detailScroll = NestedScrollView(activity).apply"))
        assertTrue(source.contains("isNestedScrollingEnabled = true"))
        assertFalse(source.contains("import android.widget.ScrollView"))
    }
}
