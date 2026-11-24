package com.example.yst.detection

class ManualClock : TaskRunner {
    private var nowMs = 0L
    private var nextId = 0L
    private val tasks = mutableListOf<ScheduledTask>()

    private class ScheduledTask(
        val id: Long,
        val dueMs: Long,
        val action: () -> Unit,
        var cancelled: Boolean = false,
    )

    override fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable {
        val task = ScheduledTask(
            id = nextId++,
            dueMs = nowMs + delayMs,
            action = action,
        )
        tasks += task
        return Cancellable { task.cancelled = true }
    }

    fun advanceBy(milliseconds: Long) {
        advanceTo(nowMs + milliseconds)
    }

    fun advanceTo(targetMs: Long) {
        while (true) {
            val next = tasks
                .filter { !it.cancelled && it.dueMs <= targetMs }
                .minWithOrNull(compareBy<ScheduledTask> { it.dueMs }.thenBy { it.id })
                ?: break
            tasks.remove(next)
            nowMs = next.dueMs
            next.action()
        }
        nowMs = targetMs
    }
}
