package com.tans.tfiletransporter.ui.connection

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
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.logs.AndroidLog

class HotspotActivity : AppCompatActivity() {

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot)

        val tvStatus: TextView = findViewById(R.id.tv_status)
        val tvSsid: TextView = findViewById(R.id.tv_ssid)
        val tvPassword: TextView = findViewById(R.id.tv_password)
        val btnToggle: Button = findViewById(R.id.btn_toggle_hotspot)
        
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        btnToggle.setOnClickListener {
            if (reservation == null) {
                tvStatus.text = "Starting hotspot..."
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                        override fun onStarted(r: WifiManager.LocalOnlyHotspotReservation?) {
                            super.onStarted(r)
                            reservation = r
                            runOnUiThread {
                                tvStatus.text = "Hotspot is ACTIVE"
                                tvSsid.text = "Network Name (SSID): ${r?.wifiConfiguration?.SSID}"
                                tvPassword.text = "Password: ${r?.wifiConfiguration?.preSharedKey}"
                                btnToggle.text = "Stop Hotspot"
                            }
                        }

                        override fun onStopped() {
                            super.onStopped()
                            reservation = null
                            runOnUiThread {
                                tvStatus.text = "Hotspot is stopped."
                                tvSsid.text = ""
                                tvPassword.text = ""
                                btnToggle.text = "Start Hotspot"
                            }
                        }

                        override fun onFailed(reason: Int) {
                            super.onFailed(reason)
                            AndroidLog.e("HotspotActivity", "Failed to start hotspot: $reason")
                            runOnUiThread {
                                tvStatus.text = "Failed to start hotspot. Error code: $reason"
                            }
                        }
                    }, Handler(Looper.getMainLooper()))
                } else {
                    tvStatus.text = "LocalOnlyHotspot requires Android 8.0+"
                }
            } else {
                reservation?.close()
                reservation = null
                tvStatus.text = "Hotspot is stopped."
                tvSsid.text = ""
                tvPassword.text = ""
                btnToggle.text = "Start Hotspot"
            }
        }
        
        findViewById<View>(R.id.toolbar).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        reservation?.close()
    }
}
