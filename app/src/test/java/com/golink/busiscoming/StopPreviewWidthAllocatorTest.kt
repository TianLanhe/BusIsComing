package com.golink.busiscoming

import com.golink.busiscoming.ui.main.StopPreviewWidthAllocator
import org.junit.Assert.assertEquals
import org.junit.Test

class StopPreviewWidthAllocatorTest {
    @Test
    fun `short station names keep their natural widths`() {
        val allocation = StopPreviewWidthAllocator.allocate(
            availableWidth = 200,
            originDesiredWidth = 48,
            destinationDesiredWidth = 64
        )

        assertEquals(48, allocation.originWidth)
        assertEquals(64, allocation.destinationWidth)
    }

    @Test
    fun `short origin stays complete while destination uses the remainder`() {
        val allocation = StopPreviewWidthAllocator.allocate(
            availableWidth = 200,
            originDesiredWidth = 48,
            destinationDesiredWidth = 260
        )

        assertEquals(48, allocation.originWidth)
        assertEquals(152, allocation.destinationWidth)
    }

    @Test
    fun `short destination stays complete while origin uses the remainder`() {
        val allocation = StopPreviewWidthAllocator.allocate(
            availableWidth = 200,
            originDesiredWidth = 260,
            destinationDesiredWidth = 52
        )

        assertEquals(148, allocation.originWidth)
        assertEquals(52, allocation.destinationWidth)
    }

    @Test
    fun `two long names split proportionally within the thirty two sixty eight limits`() {
        val allocation = StopPreviewWidthAllocator.allocate(
            availableWidth = 200,
            originDesiredWidth = 300,
            destinationDesiredWidth = 200
        )

        assertEquals(120, allocation.originWidth)
        assertEquals(80, allocation.destinationWidth)
    }

    @Test
    fun `extreme long word cannot take more than sixty eight percent`() {
        val allocation = StopPreviewWidthAllocator.allocate(
            availableWidth = 200,
            originDesiredWidth = 2_000,
            destinationDesiredWidth = 200
        )

        assertEquals(136, allocation.originWidth)
        assertEquals(64, allocation.destinationWidth)
    }
}
