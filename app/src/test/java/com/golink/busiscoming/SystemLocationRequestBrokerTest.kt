package com.golink.busiscoming

import com.golink.busiscoming.data.location.*
import org.junit.Assert.*
import org.junit.Test

class SystemLocationRequestBrokerTest {
    @Test fun `從請求開始三秒即失敗並清理來源`() {
        val fixture = Fixture()
        val results = mutableListOf<CurrentLocationResult>()
        fixture.broker.request(results::add)
        fixture.advance(2_999)
        assertTrue(results.isEmpty())
        fixture.advance(1)
        assertEquals(listOf(CurrentLocationResult.Timeout), results)
        assertEquals(1, fixture.closed)
    }

    @Test fun `舊請求晚到不得完成下一輪也不得污染快照`() {
        val f = Fixture()
        f.broker.request {}
        val old = f.callbacks.single()
        f.advance(3_000)
        val received = mutableListOf<CurrentLocationResult>()
        f.broker.request(received::add)
        old(f.success())
        assertTrue(received.isEmpty())
        f.callbacks.last()(f.success(23.0))
        assertEquals(23.0, (received.single() as CurrentLocationResult.Success).snapshot.latitude, 0.0)
        assertEquals(2, f.closed)
    }

    @Test fun `合併需求取消自身不取消其他人且最後取消才停止`() {
        val f = Fixture()
        val cancelled = mutableListOf<CurrentLocationResult>()
        val kept = mutableListOf<CurrentLocationResult>()
        val first = f.broker.request(cancelled::add)
        val second = f.broker.request(kept::add)
        first.close()
        assertEquals(0, f.closed)
        assertEquals(1, f.callbacks.size)
        f.callbacks.single()(f.success())
        assertTrue(cancelled.isEmpty())
        assertEquals(1, kept.size)
        second.close()
        assertEquals(1, f.closed)
        f.advance(31_000)
        f.broker.request {}.close()
        assertEquals(2, f.closed)
    }

    @Test fun `新鮮快照三十秒可共用過期及未來座標都不接受`() {
        val f = Fixture()
        f.broker.request {}
        f.callbacks.single()(f.success())
        f.advance(30_000)
        var value: CurrentLocationResult? = null
        f.broker.request { value = it }
        assertTrue(value is CurrentLocationResult.Success)
        assertEquals(1, f.callbacks.size)
        f.advance(1)
        value = null
        f.broker.request { value = it }
        f.callbacks.last()(CurrentLocationResult.Success(CurrentLocationSnapshot(22.0, 114.0, 10f, f.now + 1)))
        assertNull(value)
        f.advance(3_000)
        assertEquals(CurrentLocationResult.Timeout, value)
    }

    @Test fun `同步失敗與同步成功都只交付一次並釋放來源`() {
        var closed = 0
        var timeoutCancelled = 0
        val results = mutableListOf<CurrentLocationResult>()
        val broker = SystemLocationRequestBroker(
            source = { callback ->
                callback(CurrentLocationResult.Unavailable)
                callback(CurrentLocationResult.Timeout)
                AutoCloseable { closed++ }
            }, schedule = { _, _ -> AutoCloseable { timeoutCancelled++ } }, now = { 0 })
        broker.request(results::add)
        assertEquals(listOf(CurrentLocationResult.Unavailable), results)
        assertEquals(1, closed)
        assertEquals(1, timeoutCancelled)
    }

    @Test fun `離開前台立即釋放來源且晚到結果不再交付`() {
        val fixture = Fixture()
        val results = mutableListOf<CurrentLocationResult>()
        fixture.broker.request(results::add)
        fixture.broker.onBackground()
        assertEquals(1, fixture.closed)
        fixture.callbacks.single()(fixture.success())
        fixture.advance(3_000)
        assertEquals(listOf(CurrentLocationResult.Unavailable), results)
    }

    private class Fixture {
        var now = 10_000L
        var closed = 0
        val callbacks = mutableListOf<(CurrentLocationResult) -> Unit>()
        private val tasks = mutableListOf<Pair<Long, () -> Unit>>()
        val broker = SystemLocationRequestBroker(
            source = { callback -> callbacks += callback; AutoCloseable { closed++ } },
            schedule = { delay, action ->
                val task = now + delay to action
                tasks += task
                AutoCloseable { tasks.remove(task) }
            }, now = { now })
        fun success(latitude: Double = 22.0) = CurrentLocationResult.Success(CurrentLocationSnapshot(latitude, 114.0, 10f, now))
        fun advance(millis: Long) {
            now += millis
            tasks.filter { it.first <= now }.toList().forEach { tasks.remove(it); it.second() }
        }
    }
}
