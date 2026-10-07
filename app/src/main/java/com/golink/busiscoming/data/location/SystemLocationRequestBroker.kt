package com.golink.busiscoming.data.location

/** 無 GMS 的共享一次性請求；每輪身份、consumer 及截止時間都由本物件持有。 */
class SystemLocationRequestBroker(
    private val source: (callback: (CurrentLocationResult) -> Unit) -> AutoCloseable,
    private val schedule: (Long, () -> Unit) -> AutoCloseable,
    private val now: () -> Long
) : AutoCloseable {
    private class Request {
        val consumers = linkedMapOf<Long, (CurrentLocationResult) -> Unit>()
        var source: AutoCloseable? = null
        var timeout: AutoCloseable? = null
    }
    private var active: Request? = null
    private var nextConsumer = 0L
    private var cached: CurrentLocationSnapshot? = null

    fun request(callback: (CurrentLocationResult) -> Unit): AutoCloseable {
        cached?.takeIf { now() - it.elapsedRealtimeMillis in 0..30_000L }?.let {
            callback(CurrentLocationResult.Success(it))
            return AutoCloseable {}
        }
        val request = active ?: Request().also { active = it }
        val consumer = ++nextConsumer
        request.consumers[consumer] = callback
        if (request.timeout == null) {
            request.timeout = schedule(3_000L) { finish(request, CurrentLocationResult.Timeout) }
            val subscription = try {
                source { result ->
                    if (result is CurrentLocationResult.Success &&
                        now() - result.snapshot.elapsedRealtimeMillis !in 0..30_000L) return@source
                    finish(request, result)
                }
            } catch (_: RuntimeException) {
                finish(request, CurrentLocationResult.Unavailable)
                AutoCloseable {}
            }
            if (active === request) request.source = subscription else subscription.close()
        }
        return AutoCloseable {
            request.consumers.remove(consumer)
            if (active === request && request.consumers.isEmpty()) close()
        }
    }

    private fun finish(request: Request, result: CurrentLocationResult) {
        if (active !== request) return
        val callbacks = request.consumers.values.toList()
        close()
        if (result is CurrentLocationResult.Success) cached = result.snapshot
        callbacks.forEach { it(result) }
    }

    override fun close() {
        val previous = active ?: return
        active = null
        previous.consumers.clear()
        previous.timeout?.close()
        previous.source?.close()
    }

    fun invalidate() {
        close()
        cached = null
    }

    fun onBackground() {
        active?.let { finish(it, CurrentLocationResult.Unavailable) }
    }
}
