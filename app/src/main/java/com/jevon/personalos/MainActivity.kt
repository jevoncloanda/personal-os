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
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import com.jevon.personalos.schedule.RecordTarget
import com.jevon.personalos.alerts.recordTarget

class MainActivity : ComponentActivity() {
    private var record by mutableStateOf<RecordTarget?>(null)
    private fun readTarget(intent:Intent?)=intent?.recordTarget()
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);record=readTarget(intent)}
    override fun onResume(){super.onResume();lifecycleScope.launch{(application as PersonalOsApplication).alerts.reconcile()}}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        record=readTarget(intent)
        setContent {
            val model: TodayViewModel = viewModel()
            val settings = model.settings.collectAsStateWithLifecycle().value
            PersonalOsTheme(settings) { PersonalOsApp(model, settings,record){record=null} }
        }
    }
}
