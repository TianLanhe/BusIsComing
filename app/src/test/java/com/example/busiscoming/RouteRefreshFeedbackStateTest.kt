package com.example.busiscoming

import com.example.busiscoming.ui.main.RouteRefreshFinishAction
import com.example.busiscoming.ui.main.RouteRefreshFeedbackState
import com.example.busiscoming.ui.main.RouteRefreshResult
import com.example.busiscoming.ui.main.RouteRefreshVisualState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRefreshFeedbackStateTest {
    @Test
    fun blocksDuplicateRefreshThroughSuccessConfirmation() {
        val state = RouteRefreshFeedbackState()

        assertTrue(state.start(generation = 3))
        assertEquals(RouteRefreshVisualState.REFRESHING, state.visualState)
        assertTrue(state.blocksQueries)
        assertFalse(state.start(generation = 4))

        assertTrue(state.succeed(generation = 3, result = RouteRefreshResult.NON_EMPTY))
        assertEquals(RouteRefreshVisualState.SUCCESS, state.visualState)
        assertTrue(state.blocksQueries)

        assertEquals(RouteRefreshFinishAction.KEEP_RESULTS, state.finishSuccess(generation = 3))
        assertEquals(RouteRefreshVisualState.IDLE, state.visualState)
        assertFalse(state.blocksQueries)
    }

    @Test
    fun emptyRefreshDefersEmptyStateUntilSuccessConfirmationFinishes() {
        val state = RouteRefreshFeedbackState()

        assertTrue(state.start(generation = 7))
        assertTrue(state.succeed(generation = 7, result = RouteRefreshResult.EMPTY))
        assertEquals(RouteRefreshVisualState.SUCCESS, state.visualState)

        assertEquals(RouteRefreshFinishAction.SHOW_EMPTY_RESULTS, state.finishSuccess(generation = 7))
        assertEquals(RouteRefreshVisualState.IDLE, state.visualState)
    }

    @Test
    fun failureEndsVisualStateWithoutSuccessConfirmation() {
        val state = RouteRefreshFeedbackState()

        assertTrue(state.start(generation = 11))
        assertTrue(state.fail(generation = 11))
        assertEquals(RouteRefreshVisualState.IDLE, state.visualState)
        assertFalse(state.blocksQueries)
        assertNull(state.finishSuccess(generation = 11))
    }

    @Test
    fun staleGenerationCannotCompleteOrResetNewState() {
        val state = RouteRefreshFeedbackState()

        assertTrue(state.start(generation = 13))
        assertFalse(state.succeed(generation = 12, result = RouteRefreshResult.NON_EMPTY))
        assertEquals(RouteRefreshVisualState.REFRESHING, state.visualState)

        state.cancel()
        assertEquals(RouteRefreshVisualState.IDLE, state.visualState)
        assertFalse(state.fail(generation = 13))
        assertNull(state.finishSuccess(generation = 13))
    }
}
