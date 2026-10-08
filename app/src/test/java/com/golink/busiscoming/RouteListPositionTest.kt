package com.golink.busiscoming

import com.golink.busiscoming.ui.main.RouteListPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteListPositionTest {
    @Test fun `重排與分隔行都只按實際序號恢復`() {
        assertEquals(RouteListPosition(8, -20), RouteListPosition(8, -20).within(30))
    }
    @Test fun `縮短只截到最後有效項而空列表丟棄快照`() {
        assertEquals(RouteListPosition(3, -20), RouteListPosition(8, -20).within(4))
        assertNull(RouteListPosition(8, -20).within(0))
        assertNull(RouteListPosition(-1, 0).within(30))
    }
    @Test fun `短卡片仍露出一個像素並保留有效正負偏移`() {
        assertEquals(-9, RouteListPosition(8, -20).offsetForHeight(10))
        assertEquals(-20, RouteListPosition(8, -20).offsetForHeight(100))
        assertEquals(15, RouteListPosition(8, 15).offsetForHeight(10))
        assertEquals(0, RouteListPosition(8, -20).offsetForHeight(0))
    }
}
