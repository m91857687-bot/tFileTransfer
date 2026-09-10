import re

with open("app/src/main/java/com/tans/tfiletransporter/ui/connection/HotspotActivity.kt", "r") as f:
    content = f.read()

# Replace the content of HotspotActivity
new_content = """package com.tans.tfiletransporter.ui.connection

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.logs.AndroidLog
import kotlinx.coroutines.launch

class HotspotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot)
        val tvStatus: TextView = findViewById(R.id.tv_status)
        val tvSsid: TextView = findViewById(R.id.tv_ssid)
        val tvPassword: TextView = findViewById(R.id.tv_password)
        val btnToggle: Button = findViewById(R.id.btn_toggle_hotspot)
        
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        lifecycleScope.launch {
            HotspotManager.hotspotState.collect { state ->
                when (state) {
                    is HotspotState.Stopped -> {
                        tvStatus.text = "Hotspot is stopped."
                        tvSsid.text = ""
                        tvPassword.text = ""
                        btnToggle.text = "Start Hotspot"
                    }
                    is HotspotState.Starting -> {
                        tvStatus.text = "Starting hotspot..."
                        btnToggle.text = "Starting..."
                    }
                    is HotspotState.Started -> {
                        tvStatus.text = "Hotspot is ACTIVE"
                        tvSsid.text = "Network Name (SSID): ${state.ssid}"
                        tvPassword.text = "Password: ${state.pass}"
                        btnToggle.text = "Stop Hotspot"
                    }
                    is HotspotState.Error -> {
                        tvStatus.text = "Failed to start hotspot. Error code: ${state.reason}"
                        btnToggle.text = "Start Hotspot"
                    }
                }
            }
        }

        btnToggle.setOnClickListener {
            if (HotspotManager.reservation == null) {
                HotspotManager.updateState(HotspotState.Starting)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                            override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation?) {
                                super.onStarted(r)
                                HotspotManager.reservation = r
                                val ssid = r?.wifiConfiguration?.SSID ?: ""
                                val pass = r?.wifiConfiguration?.preSharedKey ?: ""
                                HotspotManager.updateState(HotspotState.Started(ssid, pass))
                            }
                            override fun onStopped() {
                                super.onStopped()
                                HotspotManager.reservation = null
                                HotspotManager.updateState(HotspotState.Stopped)
                            }
                            override fun onFailed(reason: Int) {
                                super.onFailed(reason)
                                AndroidLog.e("HotspotActivity", "Failed to start hotspot: $reason")
                                HotspotManager.updateState(HotspotState.Error(reason))
                            }
                        }, Handler(Looper.getMainLooper()))
                    } catch (e: Exception) {
                         AndroidLog.e("HotspotActivity", "Exception: ${e.message}")
                         HotspotManager.updateState(HotspotState.Error(-1))
                    }
                } else {
                    HotspotManager.updateState(HotspotState.Error(-2))
                    tvStatus.text = "LocalOnlyHotspot requires Android 8.0+"
                }
            } else {
                HotspotManager.reservation?.close()
                HotspotManager.reservation = null
                HotspotManager.updateState(HotspotState.Stopped)
            }
        }
        
        findViewById<View>(R.id.toolbar).setOnClickListener { finish() }
    }
}
"""

with open("app/src/main/java/com/tans/tfiletransporter/ui/connection/HotspotActivity.kt", "w") as f:
    f.write(new_content)
