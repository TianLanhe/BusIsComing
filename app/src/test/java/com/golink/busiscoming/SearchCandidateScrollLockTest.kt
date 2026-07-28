package com.golink.busiscoming

import com.golink.busiscoming.ui.main.SearchCandidateScrollLock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCandidateScrollLockTest {
    @Test
    fun remainsLockedUntilBothCandidateListsAreClosedWithoutChangingAppBarFlags() {
        val lock = SearchCandidateScrollLock()

        val originOpen = lock.update(
            originCandidatesVisible = true,
            destinationCandidatesVisible = false
        )
        assertTrue(originOpen.outerScrollLocked)

        val destinationAlsoOpen = lock.update(
            originCandidatesVisible = true,
            destinationCandidatesVisible = true
        )
        assertTrue(destinationAlsoOpen.outerScrollLocked)

        val destinationRemainsOpen = lock.update(
            originCandidatesVisible = false,
            destinationCandidatesVisible = true
        )
        assertTrue(destinationRemainsOpen.outerScrollLocked)

        val allClosed = lock.update(
            originCandidatesVisible = false,
            destinationCandidatesVisible = false
        )
        assertFalse(allClosed.outerScrollLocked)
    }

    @Test
    fun doesNotReplaceExistingAppBarFlagsWhenNoCandidateWasVisible() {
        val state = SearchCandidateScrollLock().update(
            originCandidatesVisible = false,
            destinationCandidatesVisible = false
        )

        assertFalse(state.outerScrollLocked)
    }

    @Test
    fun resetReleasesTheStableLockForViewLifecycleTeardown() {
        val lock = SearchCandidateScrollLock()
        lock.update(originCandidatesVisible = false, destinationCandidatesVisible = true)

        lock.reset()

        assertFalse(lock.isOuterScrollLocked())
    }
}
