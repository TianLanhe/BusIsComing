package com.golink.busiscoming

import com.golink.busiscoming.data.model.PedestrianCoordinate
import com.golink.busiscoming.data.model.PedestrianRoute
import com.golink.busiscoming.data.model.PedestrianRoutePath
import com.golink.busiscoming.data.repository.CsdiPedestrianFailureKind
import com.golink.busiscoming.data.repository.CsdiPedestrianRequest
import com.golink.busiscoming.data.repository.CsdiPedestrianResponse
import com.golink.busiscoming.data.repository.CsdiPedestrianRouteSource
import com.golink.busiscoming.data.repository.PedestrianCancellationToken
import com.golink.busiscoming.data.repository.PedestrianRequestPriority
import com.golink.busiscoming.data.repository.PedestrianRequestTrigger
import com.golink.busiscoming.data.repository.PedestrianRouteRuntime
import com.golink.busiscoming.data.repository.PedestrianRuntimeDiagnosticEvent
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PedestrianRouteRuntimeTest {
    @Test
    fun sameDirectedKeySharesOneFlightWhileReverseDirectionRemainsSeparate() {
        val attempts = AtomicInteger(0)
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val callbacks = CountDownLatch(3)
        val runtime = PedestrianRouteRuntime(
            source = source { request, _ ->
                attempts.incrementAndGet()
                started.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                success(request)
            },
            maxConcurrentAttempts = 2
        )
        try {
            val forward = request(0)
            val reverse = CsdiPedestrianRequest(forward.end, forward.start)
            runtime.subscribe(forward) { callbacks.countDown() }
            runtime.subscribe(forward) { callbacks.countDown() }
            runtime.subscribe(reverse) { callbacks.countDown() }

            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertEquals(2, attempts.get())
            release.countDown()
            assertTrue(callbacks.await(2, TimeUnit.SECONDS))
            assertEquals(2, attempts.get())
        } finally {
            release.countDown()
            runtime.close()
        }
    }

    @Test
    fun runtimeNeverExecutesMoreThanFiveAttemptsGlobally() {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val firstFive = CountDownLatch(5)
        val release = CountDownLatch(1)
        val callbacks = CountDownLatch(8)
        val runtime = PedestrianRouteRuntime(
            source = source { request, _ ->
                val current = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, current) }
                firstFive.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                active.decrementAndGet()
                success(request)
            }
        )
        try {
            repeat(8) { index -> runtime.subscribe(request(index)) { callbacks.countDown() } }
            assertTrue(firstFive.await(2, TimeUnit.SECONDS))
            assertEquals(5, maximum.get())
            release.countDown()
            assertTrue(callbacks.await(2, TimeUnit.SECONDS))
            assertEquals(5, maximum.get())
        } finally {
            release.countDown()
            runtime.close()
        }
    }

    @Test
    fun detailSubscriberPromotesQueuedSharedFlightWithoutPreemptingRunningWork() {
        val executionOrder = Collections.synchronizedList(mutableListOf<Int>())
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val completed = CountDownLatch(3)
        val runtime = PedestrianRouteRuntime(
            source = source { request, _ ->
                val index = request.start.latitude.toInt()
                executionOrder += index
                if (index == 10) {
                    firstStarted.countDown()
                    check(releaseFirst.await(2, TimeUnit.SECONDS))
                }
                success(request)
            },
            maxConcurrentAttempts = 1
        )
        try {
            runtime.subscribe(request(10)) { completed.countDown() }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            runtime.subscribe(request(11)) { completed.countDown() }
            runtime.subscribe(request(12)) { completed.countDown() }
            runtime.subscribe(request(12), priority = PedestrianRequestPriority.DETAIL) { }

            assertEquals(listOf(10), executionOrder)
            releaseFirst.countDown()
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(10, 12, 11), executionOrder)
        } finally {
            releaseFirst.countDown()
            runtime.close()
        }
    }

    @Test
    fun successCacheIsSynchronousAndExpiresAtomicallyWithoutCachingFailures() {
        var now = 1_000L
        val attempts = AtomicInteger(0)
        var response: CsdiPedestrianResponse = success(request(1))
        val runtime = PedestrianRouteRuntime(
            source = source { _, _ -> attempts.incrementAndGet(); response },
            clock = { now },
            successTtlMillis = 100L
        )
        try {
            val request = request(1)
            val first = CountDownLatch(1)
            runtime.subscribe(request) { first.countDown() }
            assertTrue(first.await(2, TimeUnit.SECONDS))

            var synchronous = false
            runtime.subscribe(request) { synchronous = true }
            assertTrue(synchronous)
            assertEquals(1, attempts.get())

            now += 100L
            val expired = CountDownLatch(1)
            runtime.subscribe(request) { expired.countDown() }
            assertTrue(expired.await(2, TimeUnit.SECONDS))
            assertEquals(2, attempts.get())

            response = CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_4XX)
            val failedRequest = request(2)
            repeat(2) {
                val failed = CountDownLatch(1)
                runtime.subscribe(failedRequest, trigger = PedestrianRequestTrigger.MANUAL) { failed.countDown() }
                assertTrue(failed.await(2, TimeUnit.SECONDS))
            }
            assertEquals(4, attempts.get())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun onlyTransientFailureRetriesOnceAfterConfiguredDelay() {
        val delays = mutableListOf<Long>()
        val responses = ArrayDeque(
            listOf(
                CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_5XX),
                success(request(1)),
                CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_4XX)
            )
        )
        val attempts = AtomicInteger(0)
        val runtime = PedestrianRouteRuntime(
            source = source { _, _ -> attempts.incrementAndGet(); responses.removeFirst() },
            retryDelay = { delays += it }
        )
        try {
            val first = CountDownLatch(1)
            runtime.subscribe(request(1)) { first.countDown() }
            assertTrue(first.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(300L), delays)
            assertEquals(2, attempts.get())

            val second = CountDownLatch(1)
            runtime.subscribe(request(2)) { second.countDown() }
            assertTrue(second.await(2, TimeUnit.SECONDS))
            assertEquals(3, attempts.get())
            assertEquals(listOf(300L), delays)
        } finally {
            runtime.close()
        }
    }

    @Test
    fun cancellingLastConsumerSuppressesCallbackAndCacheButOneConsumerCannotCancelAnother() {
        val attempts = AtomicInteger(0)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val callbacks = AtomicInteger(0)
        val runtime = PedestrianRouteRuntime(
            source = source { request, token ->
                attempts.incrementAndGet()
                started.countDown()
                while (!token.isCancelled && !release.await(10, TimeUnit.MILLISECONDS)) Unit
                success(request)
            },
            maxConcurrentAttempts = 1
        )
        try {
            val request = request(1)
            val cancelled = runtime.subscribe(request) { callbacks.addAndGet(100) }
            val survivor = CountDownLatch(1)
            runtime.subscribe(request) { callbacks.incrementAndGet(); survivor.countDown() }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            cancelled.close()
            release.countDown()
            assertTrue(survivor.await(2, TimeUnit.SECONDS))
            assertEquals(1, callbacks.get())

            val onlyStarted = CountDownLatch(1)
            val onlyRelease = CountDownLatch(1)
            val secondRuntime = PedestrianRouteRuntime(
                source = source { value, token ->
                    attempts.incrementAndGet()
                    onlyStarted.countDown()
                    while (!token.isCancelled && !onlyRelease.await(10, TimeUnit.MILLISECONDS)) Unit
                    success(value)
                },
                maxConcurrentAttempts = 1
            )
            try {
                val only = secondRuntime.subscribe(request(2)) { callbacks.addAndGet(1000) }
                assertTrue(onlyStarted.await(2, TimeUnit.SECONDS))
                only.close()
                onlyRelease.countDown()
                Thread.sleep(50)
                assertEquals(1, callbacks.get())

                val retried = CountDownLatch(1)
                secondRuntime.subscribe(request(2)) { retried.countDown() }
                assertTrue(retried.await(2, TimeUnit.SECONDS))
                assertEquals(3, attempts.get())
            } finally {
                onlyRelease.countDown()
                secondRuntime.close()
            }
        } finally {
            release.countDown()
            runtime.close()
        }
    }

    @Test
    fun automaticFailureBackoffIsFiveToThirtyMinutesManualAndReentryBypassAndSuccessClears() {
        var now = 0L
        val attempts = AtomicInteger(0)
        var response: CsdiPedestrianResponse = CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_4XX)
        val runtime = PedestrianRouteRuntime(
            source = source { _, _ -> attempts.incrementAndGet(); response },
            clock = { now },
            retryDelay = { }
        )
        try {
            val request = request(1)
            fun subscribe(trigger: PedestrianRequestTrigger): CsdiPedestrianResponse {
                var delivered: CsdiPedestrianResponse? = null
                val latch = CountDownLatch(1)
                runtime.subscribe(request, trigger = trigger) { delivered = it; latch.countDown() }
                assertTrue(latch.await(2, TimeUnit.SECONDS))
                return requireNotNull(delivered)
            }

            subscribe(PedestrianRequestTrigger.INITIAL)
            assertEquals(1, attempts.get())
            assertEquals(
                CsdiPedestrianFailureKind.BACKOFF,
                (subscribe(PedestrianRequestTrigger.AUTOMATIC) as CsdiPedestrianResponse.Failure).kind
            )
            assertEquals(1, attempts.get())

            subscribe(PedestrianRequestTrigger.MANUAL)
            subscribe(PedestrianRequestTrigger.REENTRY)
            assertEquals(3, attempts.get())

            now += 20 * 60_000L - 1L
            assertEquals(
                CsdiPedestrianFailureKind.BACKOFF,
                (subscribe(PedestrianRequestTrigger.AUTOMATIC) as CsdiPedestrianResponse.Failure).kind
            )
            now += 1L
            subscribe(PedestrianRequestTrigger.AUTOMATIC)
            assertEquals(4, attempts.get())

            response = success(request)
            subscribe(PedestrianRequestTrigger.MANUAL)
            assertEquals(5, attempts.get())
            runtime.clearSuccessForTest(request.key)
            response = CsdiPedestrianResponse.Failure(CsdiPedestrianFailureKind.HTTP_4XX)
            subscribe(PedestrianRequestTrigger.AUTOMATIC)
            assertEquals(6, attempts.get())
        } finally {
            runtime.close()
        }
    }

    @Test
    fun diagnosticsContainOnlyAnonymousOperationalFields() {
        val events = Collections.synchronizedList(mutableListOf<PedestrianRuntimeDiagnosticEvent>())
        val completed = CountDownLatch(1)
        val runtime = PedestrianRouteRuntime(
            source = source { request, _ -> success(request) },
            diagnosticObserver = events::add
        )
        try {
            runtime.subscribe(request(22)) { completed.countDown() }
            assertTrue(completed.await(2, TimeUnit.SECONDS))
            val text = events.joinToString("\n")
            assertTrue(events.isNotEmpty())
            assertFalse(text.contains("114."))
            assertFalse(text.contains("22."))
            assertFalse(text.contains("stops"))
            assertFalse(text.contains("Start"))
            assertFalse(text.contains("End"))
            assertFalse(text.contains("http"))
        } finally {
            runtime.close()
        }
    }

    private fun source(
        block: (CsdiPedestrianRequest, PedestrianCancellationToken) -> CsdiPedestrianResponse
    ) = object : CsdiPedestrianRouteSource {
        override fun solve(
            request: CsdiPedestrianRequest,
            cancellationToken: PedestrianCancellationToken
        ): CsdiPedestrianResponse = block(request, cancellationToken)
    }

    private fun request(index: Int): CsdiPedestrianRequest {
        val latitude = if (index >= 10) index.toDouble() else 22.30 + index * 0.001
        return CsdiPedestrianRequest(
            PedestrianCoordinate(latitude, 114.10 + index * 0.001),
            PedestrianCoordinate(latitude + 0.0005, 114.1005 + index * 0.001)
        )
    }

    private fun success(request: CsdiPedestrianRequest): CsdiPedestrianResponse.Success =
        CsdiPedestrianResponse.Success(
            PedestrianRoute(
                rawDistanceMeters = 100.5,
                rawTimeMinutes = 1.675,
                paths = listOf(PedestrianRoutePath(listOf(request.start, request.end)))
            )
        )
}
