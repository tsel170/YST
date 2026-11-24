package com.example.yst.audio

import android.os.Handler
import com.example.yst.detection.Cancellable
import com.example.yst.detection.TaskRunner

class HandlerTaskRunner(
    private val handler: Handler,
) : TaskRunner {
    override fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMs)
        return Cancellable { handler.removeCallbacks(runnable) }
    }
}
