package com.tans.tfiletransporter.ui.connection

import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object HotspotManager {
    var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    
    private val _hotspotState = MutableStateFlow<HotspotState>(HotspotState.Stopped)
    val hotspotState: StateFlow<HotspotState> = _hotspotState

    fun updateState(state: HotspotState) {
        _hotspotState.value = state
    }
}

sealed class HotspotState {
    object Stopped : HotspotState()
    object Starting : HotspotState()
    data class Started(val ssid: String, val pass: String) : HotspotState()
    data class Error(val reason: Int) : HotspotState()
}
