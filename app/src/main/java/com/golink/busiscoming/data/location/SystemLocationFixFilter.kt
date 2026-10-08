package com.golink.busiscoming.data.location

/** 過期快照不可取消首 fix 逾時，晚到快照也不可覆蓋較新的即時位置。 */
class SystemLocationFixFilter(private val maxAgeMillis: Long) {
    private var latestElapsedMillis = Long.MIN_VALUE

    fun accept(elapsedMillis: Long, now: Long): Boolean {
        if (now - elapsedMillis !in 0..maxAgeMillis || elapsedMillis < latestElapsedMillis) return false
        latestElapsedMillis = elapsedMillis
        return true
    }
}
