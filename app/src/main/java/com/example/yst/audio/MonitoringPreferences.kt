package com.example.yst.audio

import android.content.Context

interface MonitoringPreferences {
    var enabled: Boolean
    var retryIntervalMs: Long
}

class MemoryMonitoringPreferences(
    override var enabled: Boolean = false,
    override var retryIntervalMs: Long = DEFAULT_RETRY_MS,
) : MonitoringPreferences

class SharedMonitoringPreferences(
    context: Context,
) : MonitoringPreferences {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    override var retryIntervalMs: Long
        get() = preferences.getLong(KEY_RETRY, DEFAULT_RETRY_MS)
        set(value) {
            preferences.edit().putLong(KEY_RETRY, value).apply()
        }

    private companion object {
        const val NAME = "voice_trigger_monitoring"
        const val KEY_ENABLED = "enabled"
        const val KEY_RETRY = "retry_interval_ms"
    }
}

const val DEFAULT_RETRY_MS = 3_000L
