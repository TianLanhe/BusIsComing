package com.golink.busiscoming

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRefreshNoticeContractTest {
    private val notice = File("src/main/res/layout/view_auto_refresh_notice.xml").readText()
    private val frequent = File("src/main/res/layout/fragment_frequent_routes.xml").readText()
    private val search = File("src/main/res/layout/fragment_search.xml").readText()

    @Test
    fun noticeIsAnInlineCardWithoutOverlayOrDismissChrome() {
        assertTrue(notice.contains("MaterialCardView"))
        assertTrue(notice.contains("app:strokeWidth=\"1dp\""))
        assertTrue(notice.contains("app:cardCornerRadius=\"14dp\""))
        assertTrue(notice.contains("android:layout_height=\"3dp\""))
        assertTrue(notice.contains("android:accessibilityLiveRegion=\"polite\""))
        assertFalse(notice.contains("<ImageView"))
        assertFalse(notice.contains("close"))
        assertFalse(notice.contains("dismiss"))
        assertFalse(notice.contains("Snackbar"))
        assertFalse(notice.contains("BottomSheet"))
    }

    @Test
    fun bothResultEntrancesPlaceNoticeBeforeStickyControls() {
        assertAppearsInOrder(
            frequent,
            "@+id/queryControls",
            "@layout/view_auto_refresh_notice",
            "@+id/stickyResultControls"
        )
        assertAppearsInOrder(
            search,
            "@+id/searchTripContext",
            "@layout/view_auto_refresh_notice",
            "@+id/searchRouteResultControls"
        )
    }

    private fun assertAppearsInOrder(source: String, vararg fragments: String) {
        var cursor = -1
        fragments.forEach { fragment ->
            val next = source.indexOf(fragment, cursor + 1)
            assertTrue("Missing or out-of-order fragment: $fragment", next > cursor)
            cursor = next
        }
    }
}
