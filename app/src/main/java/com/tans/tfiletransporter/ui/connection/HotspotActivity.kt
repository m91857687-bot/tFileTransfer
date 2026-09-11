package com.tans.tfiletransporter.ui.connection

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.logs.AndroidLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.glxn.qrgen.android.QRCode

class HotspotActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot)
        val tvStatus: TextView = findViewById(R.id.tv_status)
        val tvSsid: TextView = findViewById(R.id.tv_ssid)
        val tvPassword: TextView = findViewById(R.id.tv_password)
        val btnToggle: Button = findViewById(R.id.btn_toggle_hotspot)
        val ivQrCode: ImageView = findViewById(R.id.iv_qr_code)
        
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        lifecycleScope.launch {
            HotspotManager.hotspotState.collect { state ->
                when (state) {
                    is HotspotState.Stopped -> {
                        tvStatus.text = "Hotspot is stopped."
                        tvSsid.text = ""
                        tvPassword.text = ""
                        btnToggle.text = "Start Hotspot"
                        ivQrCode.visibility = View.GONE
                    }
                    is HotspotState.Starting -> {
                        tvStatus.text = "Starting hotspot..."
                        btnToggle.text = "Starting..."
                        ivQrCode.visibility = View.GONE
                    }
                    is HotspotState.Started -> {
                        tvStatus.text = "Hotspot is ACTIVE"
                        tvSsid.text = "Network Name (SSID): ${state.ssid}"
                        tvPassword.text = "Password: ${state.pass}"
                        btnToggle.text = "Stop Hotspot"
                        try {
                            val qrString = "WIFI:T:WPA;S:${state.ssid};P:${state.pass};;"
                            val bitmap = withContext(Dispatchers.IO) {
                                QRCode.from(qrString).withSize(400, 400).bitmap()
                            }
                            ivQrCode.setImageBitmap(bitmap)
                            ivQrCode.visibility = View.VISIBLE
                        } catch (e: Exception) {
                            AndroidLog.e("HotspotActivity", "Error generating QR: ${e.message}")
                        }
                    }
                    is HotspotState.Error -> {
                        tvStatus.text = "Failed to start hotspot. Error code: ${state.reason}"
                        btnToggle.text = "Start Hotspot"
                        ivQrCode.visibility = View.GONE
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
