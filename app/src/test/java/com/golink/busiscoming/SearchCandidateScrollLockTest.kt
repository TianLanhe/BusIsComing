package com.golink.busiscoming

import com.golink.busiscoming.ui.main.SearchCandidateScrollLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCandidateScrollLockTest {
    @Test
    fun locksOuterScrollUntilBothCandidateListsAreClosedThenRestoresOriginalFlags() {
        val lock = SearchCandidateScrollLock()

        val originOpen = lock.update(
            originCandidatesVisible = true,
            destinationCandidatesVisible = false,
            currentScrollFlags = 5
        )
        assertTrue(originOpen.outerScrollLocked)
        assertEquals(0, originOpen.scrollFlagsToApply)

        val destinationAlsoOpen = lock.update(
            originCandidatesVisible = true,
            destinationCandidatesVisible = true,
            currentScrollFlags = 0
        )
        assertTrue(destinationAlsoOpen.outerScrollLocked)
        assertNull(destinationAlsoOpen.scrollFlagsToApply)

        val destinationRemainsOpen = lock.update(
            originCandidatesVisible = false,
            destinationCandidatesVisible = true,
            currentScrollFlags = 0
        )
        assertTrue(destinationRemainsOpen.outerScrollLocked)
        assertNull(destinationRemainsOpen.scrollFlagsToApply)

        val allClosed = lock.update(
            originCandidatesVisible = false,
            destinationCandidatesVisible = false,
            currentScrollFlags = 0
        )
        assertFalse(allClosed.outerScrollLocked)
        assertEquals(5, allClosed.scrollFlagsToApply)
    }

    @Test
    fun doesNotReplaceExistingAppBarFlagsWhenNoCandidateWasVisible() {
        val state = SearchCandidateScrollLock().update(
            originCandidatesVisible = false,
            destinationCandidatesVisible = false,
            currentScrollFlags = 7
        )

        assertFalse(state.outerScrollLocked)
        assertNull(state.scrollFlagsToApply)
    }
}
