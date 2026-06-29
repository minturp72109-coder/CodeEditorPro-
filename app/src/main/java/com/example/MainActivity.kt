package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.AdManager
import com.example.ui.screens.MainScreen
import com.example.ui.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Google AdMob Interstitial Ads Manager
        AdManager.initialize(this)
        enableEdgeToEdge()
        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}
