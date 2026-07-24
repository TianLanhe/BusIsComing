package com.golink.busiscoming.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePromptLayoutPolicyTest {
    @Test
    fun resolvesWidthAndFontScaleBoundaries() {
        val cases = listOf(
            Triple(359, 1.0f, UpdatePromptLayoutMode.VERTICAL),
            Triple(360, 1.99f, UpdatePromptLayoutMode.HORIZONTAL),
            Triple(360, 2.0f, UpdatePromptLayoutMode.VERTICAL),
            Triple(411, 1.3f, UpdatePromptLayoutMode.HORIZONTAL)
        )

        cases.forEach { (screenWidthDp, fontScale, expected) ->
            assertEquals(
                "screenWidthDp=$screenWidthDp fontScale=$fontScale",
                expected,
                UpdatePromptLayoutPolicy.resolve(screenWidthDp, fontScale)
            )
        }
    }
}
