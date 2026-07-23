package com.golink.busiscoming.data.update

import com.golink.busiscoming.data.model.UpdateCheckTrigger
import com.golink.busiscoming.data.model.UpdateSnapshot

data class UpdateReminderState(
    val deferredVersionCode: Long? = null,
    val deferredUntil: Long? = null,
    val skippedVersionCode: Long? = null
)

class UpdatePolicy(
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun isAutomaticCheckDue(lastAutoAttemptAt: Long?): Boolean {
        if (lastAutoAttemptAt == null) return true
        val elapsed = clock() - lastAutoAttemptAt
        return elapsed >= AUTO_CHECK_INTERVAL_MILLIS
    }

    fun hasReachedReminderAge(availableSinceAt: Long): Boolean {
        val now = clock()
        if (now < availableSinceAt) return false
        return now - availableSinceAt >= REMINDER_DELAY_DAYS * DAY_MILLIS
    }

    fun shouldPrompt(
        trigger: UpdateCheckTrigger,
        snapshot: UpdateSnapshot,
        reminder: UpdateReminderState
    ): Boolean {
        val versionCode = snapshot.availableVersionCode
        if (!snapshot.hasNewerVersion || versionCode == null) return false
        if (trigger == UpdateCheckTrigger.MANUAL) return true
        val availableSinceAt = snapshot.availableSinceAt ?: snapshot.firstSeenAt ?: return false
        if (!hasReachedReminderAge(availableSinceAt)) return false
        if (reminder.skippedVersionCode == versionCode) return false
        if (
            reminder.deferredVersionCode == versionCode &&
            reminder.deferredUntil?.let { clock() < it } == true
        ) {
            return false
        }
        return true
    }

    fun deferredUntil(): Long = clock() + DEFER_INTERVAL_MILLIS

    companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
        const val AUTO_CHECK_INTERVAL_MILLIS = DAY_MILLIS
        const val DEFER_INTERVAL_MILLIS = 3L * DAY_MILLIS
        const val REMINDER_DELAY_DAYS = 3L
    }
}
