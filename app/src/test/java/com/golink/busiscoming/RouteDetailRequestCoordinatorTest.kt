package com.golink.busiscoming

import com.golink.busiscoming.data.model.P2pRouteDetailQuery
import com.golink.busiscoming.data.model.P2pRouteLeg
import com.golink.busiscoming.data.model.P2pRoutePlan
import com.golink.busiscoming.data.repository.RouteDetailRequestIdentity
import com.golink.busiscoming.data.repository.SingleFlightRequestCoordinator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteDetailRequestCoordinatorTest {
    private val executors = mutableListOf<java.util.concurrent.ExecutorService>()

    @After
    fun tearDown() {
        executors.forEach { it.shutdownNow() }
    }

    @Test
    fun sameIdentitySharesOneWorkWhileDifferentIdentityStartsSeparately() {
        val executor = Executors.newFixedThreadPool(2).also(executors::add)
        val coordinator = SingleFlightRequestCoordinator<String, String>(executor)
        val release = CountDownLatch(1)
        val callbacks = CountDownLatch(3)
        val workCount = AtomicInteger()
        val values = mutableListOf<String>()

        fun request(key: String, value: String) {
            coordinator.request(
                key = key,
                work = {
                    workCount.incrementAndGet()
                    release.await(2, TimeUnit.SECONDS)
                    value
                },
                callback = { result ->
                    synchronized(values) { values += result.getOrThrow() }
                    callbacks.countDown()
                }
            )
        }

        request("same", "shared")
        request("same", "ignored-work")
        request("different", "separate")
        assertTrue(awaitCount(workCount, 2))
        release.countDown()

        assertTrue(callbacks.await(2, TimeUnit.SECONDS))
        assertEquals(2, workCount.get())
        assertEquals(listOf("separate", "shared", "shared").sorted(), values.sorted())
    }

    @Test
    fun cancellingOneConsumerDoesNotCancelRemainingConsumer() {
        val executor = Executors.newSingleThreadExecutor().also(executors::add)
        val coordinator = SingleFlightRequestCoordinator<String, String>(executor)
        val release = CountDownLatch(1)
        val activeCallback = CountDownLatch(1)
        val cancelledCallbacks = AtomicInteger()

        val cancelled = coordinator.request("same", { release.await(); "ok" }) {
            cancelledCallbacks.incrementAndGet()
        }
        coordinator.request("same", { error("must share") }) {
            assertEquals("ok", it.getOrThrow())
            activeCallback.countDown()
        }

        cancelled.cancel()
        release.countDown()

        assertTrue(activeCallback.await(2, TimeUnit.SECONDS))
        assertEquals(0, cancelledCallbacks.get())
    }

    @Test
    fun cancellingLastQueuedConsumerPreventsWorkFromStarting() {
        val executor = Executors.newSingleThreadExecutor().also(executors::add)
        val blocker = CountDownLatch(1)
        executor.submit { blocker.await() }
        val coordinator = SingleFlightRequestCoordinator<String, String>(executor)
        val workCount = AtomicInteger()

        val handle = coordinator.request("queued", {
            workCount.incrementAndGet()
            "unexpected"
        }) { error("cancelled consumer must not receive callback") }

        handle.cancel()
        blocker.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        assertEquals(0, workCount.get())
    }

    @Test
    fun failureEndsFlightSoRetryStartsFreshWork() {
        val executor = Executors.newSingleThreadExecutor().also(executors::add)
        val coordinator = SingleFlightRequestCoordinator<String, String>(executor)
        val workCount = AtomicInteger()
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)

        coordinator.request("key", {
            workCount.incrementAndGet()
            error("first failure")
        }) {
            assertTrue(it.isFailure)
            first.countDown()
        }
        assertTrue(first.await(2, TimeUnit.SECONDS))
        coordinator.request("key", {
            workCount.incrementAndGet()
            "recovered"
        }) {
            assertEquals("recovered", it.getOrThrow())
            second.countDown()
        }

        assertTrue(second.await(2, TimeUnit.SECONDS))
        assertEquals(2, workCount.get())
    }

    @Test
    fun requestIdentityIncludesAllQueryContextWithoutExposingItInText() {
        val original = query()
        val identity = RouteDetailRequestIdentity.from(original)

        assertNotEquals(identity, RouteDetailRequestIdentity.from(original.copy(listId = "other")))
        assertNotEquals(identity, RouteDetailRequestIdentity.from(original.copy(lang = "1")))
        assertNotEquals(identity, RouteDetailRequestIdentity.from(original.copy(sessionRef = "other-session")))
        assertFalse(identity.toString().contains(original.rawInfo))
        assertFalse(identity.toString().contains(requireNotNull(original.sessionRef)))
    }

    private fun query() = P2pRouteDetailQuery(
        rawInfo = "sensitive-raw-info",
        generalInfo = "12:00|*|30",
        listId = "lid",
        lang = "0",
        plan = P2pRoutePlan(
            rawInfo = "raw-plan",
            lang = "0",
            legs = listOf(P2pRouteLeg("CTB", "8X-A", "8X", 1, 3, "O", "path"))
        ),
        sessionRef = "opaque-session"
    )

    private fun awaitCount(counter: AtomicInteger, expected: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (counter.get() < expected && System.nanoTime() < deadline) Thread.yield()
        return counter.get() == expected
    }
}
