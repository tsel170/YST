package com.example.yst

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yst.ui.MainScreen
import com.example.yst.ui.MainViewModelFactory
import com.example.yst.ui.theme.VoiceTriggerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            VoiceTriggerTheme {
                MainScreen(viewModel = viewModel(factory = MainViewModelFactory(application)))
            }
        }
    }
}
