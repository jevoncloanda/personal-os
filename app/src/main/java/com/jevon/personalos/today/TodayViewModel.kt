package com.jevon.personalos.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.data.Settings
import com.jevon.personalos.data.ThemeMode
import com.jevon.personalos.data.observeToday
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

enum class DataMode { DEMO, EMPTY_DEMO, PERSONAL }
sealed interface TodayState {
    data object Loading : TodayState
    data class Ready(val content: TodayContent) : TodayState
    data object Error : TodayState
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val app = application as PersonalOsApplication
    val mode = savedState.getStateFlow("data_mode", DataMode.PERSONAL)
    private val retryCount = MutableStateFlow(0)
    private val clock = flow { while (true) { emit(ZonedDateTime.now()); delay(30_000) } }
    val settingsError = MutableStateFlow(false)
    val settings = app.settings.settings.catch { settingsError.value = true; emit(Settings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val state: StateFlow<TodayState> = combine(mode, retryCount) { mode, _ -> mode }.flatMapLatest { selected ->
        flow<TodayState> {
            emit(TodayState.Loading)
            clock.flatMapLatest { now ->
                if (selected == DataMode.PERSONAL) app.database.observeToday(now)
                else flowOf((if (selected == DataMode.DEMO && DemoCatalog.available) DemoCatalog.snapshot(now.toLocalDate(), now.zone) else Snapshot()).forToday(now))
            }.collect { emit(TodayState.Ready(it)) }
        }.catch { emit(TodayState.Error) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState.Loading)

    fun setMode(value: DataMode) { if (DemoCatalog.available || value == DataMode.PERSONAL) savedState["data_mode"] = value }
    fun retry() { retryCount.value += 1 }
    fun setTheme(value: ThemeMode) = saveSetting { app.settings.setTheme(value) }
    fun setDynamicColor(value: Boolean) = saveSetting { app.settings.setDynamicColor(value) }
    private fun saveSetting(write: suspend () -> Unit) {
        viewModelScope.launch {
            try { write(); settingsError.value = false }
            catch (_: java.io.IOException) { settingsError.value = true }
        }
    }
}
