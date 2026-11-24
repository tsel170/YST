package com.example.yst.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.yst.audio.AudioMonitor
import com.example.yst.audio.HandlerTaskRunner

class MainViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
            error("Unknown ViewModel: ${modelClass.name}")
        }
        val handler = Handler(Looper.getMainLooper())
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(
            audioCapture = AudioMonitor(application),
            taskRunner = HandlerTaskRunner(handler),
            deliver = { block -> handler.post(block) },
        ) as T
    }
}
