package com.tans.tfiletransporter.ui.connection

import android.Manifest
import android.content.Intent
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.ui.commomdialog.OptionalDialog
import com.tans.tfiletransporter.ui.commomdialog.showSettingsDialog
import com.tans.tfiletransporter.ui.connection.localconnetion.LocalNetworkConnectionFragment
import com.tans.tfiletransporter.ui.connection.wifip2pconnection.WifiP2pConnectionFragment
import com.tans.tfiletransporter.utils.uri2FileReal
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.dialog.showSimpleCancelableCoroutineResultDialogSuspend
import com.tans.tuiutils.permission.permissionsRequestSuspend
import com.tans.tuiutils.systembar.annotation.SystemBarStyle
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.io.File

@SystemBarStyle(statusBarThemeStyle = 1, navigationBarThemeStyle = 1)
class ConnectionActivity : BaseCoroutineStateActivity<ConnectionActivity.Companion.ConnectionActivityState>(
    defaultState = ConnectionActivityState()
) {
    override val layoutId: Int = R.layout.connection_activity

    private val wifiP2pFragment by lazyViewModelField("wifiP2pFragment") {
        WifiP2pConnectionFragment()
    }

    private val localNetworkFragment by lazyViewModelField("localNetworkFragment") {
        LocalNetworkConnectionFragment()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkIntentAction(intent)
    }

    override fun CoroutineScope.firstLaunchInitDataCoroutine() {
        onBackPressedDispatcher.addCallback {
            finish()
        }
    }

    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = ConnectionActivityBinding.bind(contentView)
        launch {
            val permissionsNeed = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_IMAGES)
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_AUDIO)
                    permissionsNeed.add(Manifest.permission.READ_MEDIA_VIDEO)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        permissionsNeed.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    }
                } else {
                    permissionsNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            } else {
                permissionsNeed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                permissionsNeed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsNeed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                permissionsNeed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                permissionsNeed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            runCatching {
                permissionsRequestSuspend(*permissionsNeed.toTypedArray())
            }.onSuccess { (_, denied) ->
                if (denied.isNotEmpty()) {
                    AndroidLog.e(TAG, "Contains denied permissions: $denied")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    val d = OptionalDialog(
                        title = getString(R.string.permission_request_title),
                        message = getString(R.string.permission_storage_request_content),
                        positiveButtonText = getString(R.string.dialog_positive),
                        negativeButtonText = getString(R.string.dialog_negative)
                    )
                    val grant = this@ConnectionActivity.supportFragmentManager.showSimpleCancelableCoroutineResultDialogSuspend(d)

                    if (grant == true) {
                        val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        i.data = android.net.Uri.fromParts("package", packageName, null)
                        startActivity(i)
                    }
                }
                
                // Check Location
                val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkLocationEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (!isGpsEnabled && !isNetworkLocationEnabled) {
                    val locDialog = OptionalDialog(
                        title = getString(R.string.prompt_enable_location_title),
                        message = getString(R.string.prompt_enable_location_content),
                        positiveButtonText = getString(R.string.dialog_positive),
                        negativeButtonText = getString(R.string.dialog_negative)
                    )
                    val grantLoc = this@ConnectionActivity.supportFragmentManager.showSimpleCancelableCoroutineResultDialogSuspend(locDialog)
                    if (grantLoc == true) {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                }
                
                // Check Wi-Fi
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (!wifiManager.isWifiEnabled) {
                    val wifiDialog = OptionalDialog(
                        title = getString(R.string.prompt_enable_wifi_title),
                        message = getString(R.string.prompt_enable_wifi_content),
                        positiveButtonText = getString(R.string.dialog_positive),
                        negativeButtonText = getString(R.string.dialog_negative)
                    )
                    val grantWifi = this@ConnectionActivity.supportFragmentManager.showSimpleCancelableCoroutineResultDialogSuspend(wifiDialog)
                    if (grantWifi == true) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startActivity(Intent(Settings.Panel.ACTION_WIFI))
                        } else {
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                    }
                }
            }.onFailure {
                AndroidLog.e(TAG, "Request permission error: ${it.message}", it)
            }
        }

        viewBinding.toolBar.menu.findItem(R.id.settings).setOnMenuItemClickListener {
            this@ConnectionActivity.supportFragmentManager.showSettingsDialog()
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.create_hotspot).setOnMenuItemClickListener {
            startActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.connection.HotspotActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.history).setOnMenuItemClickListener {
            startActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.history.HistoryActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.web_share).setOnMenuItemClickListener {
            startActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.webshare.WebShareActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.file_manager)?.setOnMenuItemClickListener {
            openFileManager(0)
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.cleaner)?.setOnMenuItemClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.cleaner.CleanerActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.apk_extractor)?.setOnMenuItemClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.apkextractor.ApkExtractorActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.network_speed)?.setOnMenuItemClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.networkspeed.NetworkSpeedActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.vault)?.setOnMenuItemClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.vault.VaultActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.cannon_war)?.setOnMenuItemClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.games.CannonWarActivity::class.java))
            true
        }

        viewBinding.toolBar.menu.findItem(R.id.clone_phone)?.isVisible = false

        viewBinding.toolBar.menu.findItem(R.id.instructions)?.setOnMenuItemClickListener {
            com.tans.tfiletransporter.ui.instructions.InstructionsDialog().show(supportFragmentManager, "InstructionsDialog")
            true
        }

        val tcWifiP2p = supportFragmentManager.beginTransaction()
        if (supportFragmentManager.findFragmentByTag(WIFI_P2P_CONNECTION_FRAGMENT_TAG) == null) {
            tcWifiP2p.add(R.id.wifi_p2p_fragment_container, wifiP2pFragment, WIFI_P2P_CONNECTION_FRAGMENT_TAG)
        }
        tcWifiP2p.setMaxLifecycle(wifiP2pFragment, Lifecycle.State.RESUMED)
        tcWifiP2p.commitAllowingStateLoss()

        val tcLocalNetwork = supportFragmentManager.beginTransaction()
        if (supportFragmentManager.findFragmentByTag(LOCAL_NETWORK_FRAGMENT_TAG) == null) {
            tcWifiP2p.add(R.id.local_network_fragment_container, localNetworkFragment, LOCAL_NETWORK_FRAGMENT_TAG)
        }
        tcLocalNetwork.setMaxLifecycle(localNetworkFragment, Lifecycle.State.RESUMED)
        tcLocalNetwork.commitAllowingStateLoss()

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.nestedScrollView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        renderStateNewCoroutine({ it.requestShareFiles }) { requestShareFiles ->
            if (requestShareFiles.isNotEmpty()) {
                viewBinding.requestShareLayout.visibility = View.VISIBLE
                viewBinding.requestShareTv.text = getString(R.string.request_share_files, requestShareFiles.size)
            } else {
                viewBinding.requestShareLayout.visibility = View.GONE
            }
        }
        viewBinding.dropRequestShareBt.clicks(this) {
            updateState { it.copy(requestShareFiles = emptyList()) }
        }

        viewBinding.cardFileManager.setOnClickListener { openFileManager(0) }
        viewBinding.cardCleaner.setOnClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.cleaner.CleanerActivity::class.java))
        }
        viewBinding.cardApkExtractor.setOnClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.apkextractor.ApkExtractorActivity::class.java))
        }
        viewBinding.cardNetworkSpeed.setOnClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.networkspeed.NetworkSpeedActivity::class.java))
        }
        viewBinding.cardVault.setOnClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.vault.VaultActivity::class.java))
        }
        viewBinding.cardCannonWar.setOnClickListener {
            safeStartActivity(Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.games.CannonWarActivity::class.java))
        }
        viewBinding.btnCategoryApps.setOnClickListener { openFileManager(0) }
        viewBinding.btnCategoryImages.setOnClickListener { openFileManager(1) }
        viewBinding.btnCategoryVideos.setOnClickListener { openFileManager(2) }
        viewBinding.btnCategoryAudios.setOnClickListener { openFileManager(3) }
        viewBinding.btnCategoryStorage.setOnClickListener { openFileManager(5) }
    }

    private fun openFileManager(initialTab: Int = 0) {
        val intent = Intent(this@ConnectionActivity, com.tans.tfiletransporter.ui.filemanager.FileManagerActivity::class.java).apply {
            putExtra(com.tans.tfiletransporter.ui.filemanager.FileManagerActivity.EXTRA_INITIAL_TAB, initialTab)
        }
        safeStartActivity(intent)
    }

    private fun safeStartActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Failed to start activity: ${e.message}", e)
            android.widget.Toast.makeText(this@ConnectionActivity, "Cannot open: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkIntentAction(intent)
    }

    fun consumeRequestShareFiles(): List<String> {
        val files = currentState().requestShareFiles
        if (files.isNotEmpty()) {
            updateState { it.copy(requestShareFiles = emptyList()) }
        }
        return files.map { it.canonicalPath }
    }

    @Suppress("DEPRECATION")
    private fun checkIntentAction(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            AndroidLog.d(TAG, "Receive ACTION_SEND uri: $uri")
            if (uri != null) {
                handleSharedUris(listOf(uri))
            }
        }
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList<Uri>()
            } else {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.mapNotNull { it } ?: emptyList<Uri>()
            }
            AndroidLog.d(TAG, "Receive ACTION_SEND_MULTIPLE uris: ${uris.joinToString { it.toString() }}")
            if (uris.isNotEmpty()) {
                handleSharedUris(uris)
            }
        }
    }

    private fun resolveUriToFile(uri: Uri): File? {
        val f = uri2FileReal(this@ConnectionActivity, uri)
        if (f?.isFile == true && f.canRead() && f.length() > 0L) {
            return f
        }
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) ?: -1
            cursor?.moveToFirst()
            val displayName = if (nameIndex >= 0) cursor?.getString(nameIndex) else null
            cursor?.close()
            val fileName = displayName ?: ("shared_${System.currentTimeMillis()}.dat")
            val cacheDir = File(cacheDir, "shared_export")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val destFile = File(cacheDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile else null
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Error resolving shared URI: ${e.message}", e)
            null
        }
    }

    private fun handleSharedUris(uris: List<Uri>) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val files = uris.mapNotNull {
                resolveUriToFile(it)
            }
            AndroidLog.d(TAG, "Handle shared files: $files")
            if (files.isNotEmpty()) {
                val filePaths = ArrayList(files.map { it.canonicalPath })
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    updateState {
                        it.copy(requestShareFiles = files)
                    }
                    android.widget.Toast.makeText(
                        this@ConnectionActivity,
                        getString(R.string.shared_files_barcode_ready),
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    // Automatically open sending QR Code (Hotspot) screen with the files attached!
                    val hotspotIntent = Intent(this@ConnectionActivity, HotspotActivity::class.java).apply {
                        putStringArrayListExtra(HotspotActivity.EXTRA_REQUEST_SHARE_FILES, filePaths)
                        putExtra(HotspotActivity.EXTRA_AUTO_START, true)
                    }
                    startActivity(hotspotIntent)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ConnectionActivity"

        private const val WIFI_P2P_CONNECTION_FRAGMENT_TAG = "WIFI_P2P_CONNECTION_FRAGMENT_TAG"
        private const val LOCAL_NETWORK_FRAGMENT_TAG = "LOCAL_NETWORK_FRAGMENT_TAG"

        data class ConnectionActivityState(
            val requestShareFiles: List<File> = emptyList()
        )
    }
}