package com.jevon.personalos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jevon.personalos.today.TodayViewModel
import com.jevon.personalos.ui.PersonalOsApp
import com.jevon.personalos.ui.PersonalOsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val model: TodayViewModel = viewModel()
            val settings = model.settings.collectAsStateWithLifecycle().value
            PersonalOsTheme(settings) { PersonalOsApp(model, settings) }
        }
    }
}
