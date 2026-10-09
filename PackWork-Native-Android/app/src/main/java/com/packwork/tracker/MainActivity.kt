package com.packwork.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.packwork.tracker.ui.PackWorkApp
import com.packwork.tracker.ui.PackWorkTheme
import com.packwork.tracker.ui.PackWorkViewModel

class MainActivity : ComponentActivity() {
    private val vm: PackWorkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PackWorkTheme { PackWorkApp(vm) }
        }
    }
}
