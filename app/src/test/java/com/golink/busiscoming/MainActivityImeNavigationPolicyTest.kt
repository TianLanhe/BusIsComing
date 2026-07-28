package com.golink.busiscoming

import com.golink.busiscoming.ui.main.MainActivityImeNavigationPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityImeNavigationPolicyTest {
    @Test
    fun `visible IME makes the covered navigation unavailable to touch and accessibility`() {
        val policy = MainActivityImeNavigationPolicy.resolve(imeVisible = true)

        assertFalse(policy.isEnabled)
        assertFalse(policy.isClickable)
        assertTrue(policy.hidesDescendantsFromAccessibility)
    }

    @Test
    fun `hidden IME restores navigation interaction and accessibility`() {
        val policy = MainActivityImeNavigationPolicy.resolve(imeVisible = false)

        assertTrue(policy.isEnabled)
        assertTrue(policy.isClickable)
        assertFalse(policy.hidesDescendantsFromAccessibility)
    }
}
