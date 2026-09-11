package com.tans.tfiletransporter.ui.connection

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.ui.qrcodescan.ScanQrCodeActivity
import com.tans.tfiletransporter.ui.filetransport.FileTransportActivity
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanServer
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanServerObserver
import com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanState
import com.tans.tfiletransporter.transferproto.qrscanconn.model.QRCodeShare
import com.tans.tfiletransporter.transferproto.qrscanconn.startQRCodeScanServerSuspend
import com.tans.tfiletransporter.transferproto.qrscanconn.startQRCodeScanClientSuspend
import com.tans.tfiletransporter.transferproto.qrscanconn.requestFileTransferSuspend
import com.tans.tfiletransporter.transferproto.broadcastconn.model.RemoteDevice
import com.tans.tfiletransporter.netty.getBroadcastAddress
import com.tans.tfiletransporter.netty.findLocalAddressV4
import com.tans.tfiletransporter.ui.connection.localconnetion.LocalNetworkConnectionFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.glxn.qrgen.android.QRCode
import java.net.InetAddress
import com.tans.tfiletransporter.file.LOCAL_DEVICE

class HotspotActivity : AppCompatActivity() {

    private var qrcodeServer: QRCodeScanServer? = null

    private val scanQrLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val resultData = result.data
            if (resultData != null) {
                val scanResultStrings = ScanQrCodeActivity.getResult(resultData)
                val wifiQrStr = scanResultStrings.firstOrNull { it.startsWith("WIFI:") }
                if (wifiQrStr != null) {
                    handleWifiQrCode(wifiQrStr)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hotspot)
        val tvStatus: TextView = findViewById(R.id.tv_status)
        val tvSsid: TextView = findViewById(R.id.tv_ssid)
        val tvPassword: TextView = findViewById(R.id.tv_password)
        val btnToggle: Button = findViewById(R.id.btn_toggle_hotspot)
        val ivQrCode: ImageView = findViewById(R.id.iv_qr_code)
        
        val btnScanQr: Button = findViewById(R.id.btn_scan_qr)

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
                            startAppServerForHotspot()
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
                                stopAppServerForHotspot()
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
                stopAppServerForHotspot()
            }
        }
        
        btnScanQr.setOnClickListener {
            scanQrLauncher.launch(Intent(this, ScanQrCodeActivity::class.java))
        }
        
        findViewById<View>(R.id.toolbar).setOnClickListener { finish() }
    }

    private fun startAppServerForHotspot() {
        lifecycleScope.launch(Dispatchers.IO) {
            stopAppServerForHotspot()
            val localAddress = findLocalAddressV4().firstOrNull() ?: return@launch
            qrcodeServer = QRCodeScanServer(log = AndroidLog)
            qrcodeServer?.addObserver(object : QRCodeScanServerObserver {
                override fun requestTransferFile(remoteDevice: RemoteDevice) {
                    AndroidLog.d("HotspotActivity", "Receive request: $remoteDevice")
                    startActivity(
                        FileTransportActivity.getIntent(
                            context = this@HotspotActivity,
                            localAddress = localAddress,
                            remoteAddress = remoteDevice.remoteAddress.address,
                            remoteDeviceInfo = remoteDevice.deviceName,
                            isServer = true,
                            requestShareFiles = emptyList()
                        )
                    )
                }

                override fun onNewState(state: QRCodeScanState) {
                    AndroidLog.d("HotspotActivity", "Qrcode server state: $state")
                }
            })
            runCatching {
                qrcodeServer?.startQRCodeScanServerSuspend(localAddress = localAddress)
            }
        }
    }

    private fun stopAppServerForHotspot() {
        qrcodeServer?.closeConnectionIfActive()
        qrcodeServer = null
    }

    private fun handleWifiQrCode(qrString: String) {
        // WIFI:T:WPA;S:MyNetwork;P:MyPassword;;
        val ssidMatch = Regex("S:([^;]+);").find(qrString)
        val passMatch = Regex("P:([^;]+);").find(qrString)
        val ssid = ssidMatch?.groupValues?.get(1) ?: return
        val pass = passMatch?.groupValues?.get(1) ?: ""
        
        Toast.makeText(this, "Connecting to Hotspot: $ssid", Toast.LENGTH_LONG).show()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(pass)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    runOnUiThread { Toast.makeText(this@HotspotActivity, "Connected to Hotspot!", Toast.LENGTH_SHORT).show() }
                    
                    // Connected to Hotspot, now find the server (Gateway)
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                            val dhcp = wifiManager.dhcpInfo
                            val gatewayAddress = InetAddress.getByAddress(
                                byteArrayOf(
                                    (dhcp.gateway and 0xff).toByte(),
                                    (dhcp.gateway shr 8 and 0xff).toByte(),
                                    (dhcp.gateway shr 16 and 0xff).toByte(),
                                    (dhcp.gateway shr 24 and 0xff).toByte()
                                )
                            )
                            val localAddress = findLocalAddressV4().firstOrNull() ?: return@launch
                            
                            val qrcodeClient = com.tans.tfiletransporter.transferproto.qrscanconn.QRCodeScanClient(log = AndroidLog)
                            kotlinx.coroutines.delay(1000)
                            runCatching {
                                qrcodeClient.startQRCodeScanClientSuspend(serverAddress = gatewayAddress)
                            }.onSuccess {
                                val transferResult = runCatching {
                                    qrcodeClient.requestFileTransferSuspend(
                                        targetAddress = gatewayAddress,
                                        deviceName = LOCAL_DEVICE
                                    )
                                }.getOrNull()
                                
                                if (transferResult != null) {
                                    startActivity(
                                        FileTransportActivity.getIntent(
                                            context = this@HotspotActivity,
                                            localAddress = localAddress,
                                            remoteAddress = gatewayAddress,
                                            remoteDeviceInfo = "Hotspot Server",
                                            isServer = false,
                                            requestShareFiles = emptyList()
                                        )
                                    )
                                }
                                qrcodeClient.closeConnectionIfActive()
                            }
                        } catch (e: Exception) {
                            AndroidLog.e("HotspotActivity", "Error finding server: ${e.message}")
                        }
                    }
                }
            })
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAppServerForHotspot()
    }
}
