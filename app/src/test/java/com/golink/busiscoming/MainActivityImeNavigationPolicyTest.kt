package com.golink.busiscoming

import com.golink.busiscoming.ui.main.MainActivityImeNavigationPolicy
import com.golink.busiscoming.ui.main.MainActivityImeNavigationSnapshot
import com.golink.busiscoming.ui.main.MainActivityImeNavigationTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityImeNavigationPolicyTest {
    @Test
    fun `repeated visible Insets keep the first snapshot for exact restoration`() {
        val policy = MainActivityImeNavigationPolicy()
        val original = MainActivityImeNavigationSnapshot(
            isEnabled = true,
            isClickable = false,
            importantForAccessibility = 4,
            menuItemEnabledStates = listOf(true, false, true)
        )
        val guarded = MainActivityImeNavigationSnapshot(
            isEnabled = false,
            isClickable = false,
            importantForAccessibility = 8,
            menuItemEnabledStates = listOf(false, false, false)
        )

        assertEquals(
            MainActivityImeNavigationTransition.ApplyGuard,
            policy.update(imeVisible = true, current = original)
        )
        assertNull(policy.update(imeVisible = true, current = guarded))
        assertEquals(
            MainActivityImeNavigationTransition.Restore(original),
            policy.update(imeVisible = false, current = guarded)
        )
        assertNull(policy.update(imeVisible = false, current = original))
    }

    @Test
    fun `a later IME session captures a fresh navigation snapshot`() {
        val policy = MainActivityImeNavigationPolicy()
        val first = MainActivityImeNavigationSnapshot(
            isEnabled = true,
            isClickable = true,
            importantForAccessibility = 0,
            menuItemEnabledStates = listOf(true, true, false)
        )
        val later = MainActivityImeNavigationSnapshot(
            isEnabled = false,
            isClickable = true,
            importantForAccessibility = 2,
            menuItemEnabledStates = listOf(false, true, true)
        )

        policy.update(imeVisible = true, current = first)
        policy.update(imeVisible = false, current = first)
        policy.update(imeVisible = true, current = later)

        assertEquals(
            MainActivityImeNavigationTransition.Restore(later),
            policy.update(imeVisible = false, current = first)
        )
    }
}
