package com.tans.tfiletransporter.ui.apkextractor

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ActivityApkExtractorBinding
import com.tans.tfiletransporter.databinding.ItemApkAppBinding
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.ui.connection.HotspotActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ApkExtractorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApkExtractorBinding
    private val allApps = mutableListOf<AppItem>()
    private val displayedApps = mutableListOf<AppItem>()
    private lateinit var appAdapter: AppAdapter

    private var searchQuery: String = ""
    private var userAppsOnly: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApkExtractorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolBar.setNavigationOnClickListener {
            finish()
        }

        appAdapter = AppAdapter(displayedApps)
        binding.rvApps.apply {
            layoutManager = LinearLayoutManager(this@ApkExtractorActivity)
            adapter = appAdapter
        }

        binding.etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                binding.ivClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                filterApps()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.ivClearSearch.setOnClickListener {
            binding.etSearchApps.text?.clear()
        }

        binding.cbUserAppsOnly.setOnCheckedChangeListener { _, isChecked ->
            userAppsOnly = isChecked
            filterApps()
        }

        binding.btnBatchExtract.setOnClickListener {
            val selected = displayedApps.filter { it.isSelected }
            if (selected.isNotEmpty()) {
                batchExtractApks(selected)
            }
        }

        binding.btnBatchSend.setOnClickListener {
            val selected = displayedApps.filter { it.isSelected }
            if (selected.isNotEmpty()) {
                batchSendApks(selected)
            }
        }

        loadApps()
    }

    private fun loadApps() {
        binding.pbLoading.visibility = View.VISIBLE
        binding.rvApps.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }

            val list = mutableListOf<AppItem>()
            for (pkg in packages) {
                val appInfo = pkg.applicationInfo ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = pm.getApplicationLabel(appInfo).toString()
                val sourceDir = appInfo.sourceDir
                val file = File(sourceDir)
                val size = if (file.exists()) file.length() else 0L
                val versionName = pkg.versionName ?: "1.0"

                list.add(
                    AppItem(
                        appInfo = appInfo,
                        name = label,
                        packageName = pkg.packageName,
                        versionName = versionName,
                        size = size,
                        sourceDir = sourceDir,
                        isSystemApp = isSystem
                    )
                )
            }

            list.sortBy { it.name.lowercase(Locale.ROOT) }

            withContext(Dispatchers.Main) {
                allApps.clear()
                allApps.addAll(list)
                binding.pbLoading.visibility = View.GONE
                binding.rvApps.visibility = View.VISIBLE
                filterApps()
            }
        }
    }

    private fun filterApps() {
        displayedApps.clear()
        val filtered = allApps.filter { item ->
            val matchUser = if (userAppsOnly) !item.isSystemApp else true
            val matchQuery = if (searchQuery.isNotEmpty()) {
                item.name.contains(searchQuery, ignoreCase = true) ||
                        item.packageName.contains(searchQuery, ignoreCase = true)
            } else true
            matchUser && matchQuery
        }
        displayedApps.addAll(filtered)
        binding.tvAppsCount.text = "${displayedApps.size} apps"
        appAdapter.notifyDataSetChanged()
        updateBatchActionBar()
    }

    private fun updateBatchActionBar() {
        val selectedCount = displayedApps.count { it.isSelected }
        if (selectedCount > 0) {
            binding.cardBottomActions.visibility = View.VISIBLE
            binding.tvSelectedSummary.text = "Selected: $selectedCount app(s)"
            binding.btnBatchExtract.text = getString(R.string.apk_extract_all, selectedCount)
        } else {
            binding.cardBottomActions.visibility = View.GONE
        }
    }

    private fun getOutputApkDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloads, "ShareLoad/Extracted_APKs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun extractSingleApk(item: AppItem, showSuccessToast: Boolean = true, onComplete: ((File?) -> Unit)? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outDir = getOutputApkDir()
                val cleanName = item.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val destFile = File(outDir, "${cleanName}_v${item.versionName}.apk")
                File(item.sourceDir).copyTo(destFile, overwrite = true)

                withContext(Dispatchers.Main) {
                    if (showSuccessToast) {
                        Toast.makeText(
                            this@ApkExtractorActivity,
                            getString(R.string.apk_extracted_success, destFile.name),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    onComplete?.invoke(destFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ApkExtractorActivity, getString(R.string.apk_extract_failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke(null)
                }
            }
        }
    }

    private fun batchExtractApks(items: List<AppItem>) {
        binding.pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = getOutputApkDir()
            var successCount = 0
            for (item in items) {
                try {
                    val cleanName = item.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val destFile = File(outDir, "${cleanName}_v${item.versionName}.apk")
                    File(item.sourceDir).copyTo(destFile, overwrite = true)
                    successCount++
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                binding.pbLoading.visibility = View.GONE
                Toast.makeText(
                    this@ApkExtractorActivity,
                    "Extracted $successCount app(s) to Downloads/ShareLoad/Extracted_APKs/",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun sendViaBarcode(item: AppItem) {
        extractSingleApk(item, showSuccessToast = false) { destFile ->
            if (destFile != null && destFile.exists()) {
                val hotspotIntent = Intent(this@ApkExtractorActivity, HotspotActivity::class.java).apply {
                    putStringArrayListExtra(HotspotActivity.EXTRA_REQUEST_SHARE_FILES, arrayListOf(destFile.canonicalPath))
                    putExtra(HotspotActivity.EXTRA_AUTO_START, true)
                }
                startActivity(hotspotIntent)
            }
        }
    }

    private fun batchSendApks(items: List<AppItem>) {
        binding.pbLoading.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val outDir = getOutputApkDir()
            val paths = ArrayList<String>()
            for (item in items) {
                try {
                    val cleanName = item.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val destFile = File(outDir, "${cleanName}_v${item.versionName}.apk")
                    File(item.sourceDir).copyTo(destFile, overwrite = true)
                    paths.add(destFile.canonicalPath)
                } catch (_: Exception) {}
            }
            withContext(Dispatchers.Main) {
                binding.pbLoading.visibility = View.GONE
                if (paths.isNotEmpty()) {
                    val hotspotIntent = Intent(this@ApkExtractorActivity, HotspotActivity::class.java).apply {
                        putStringArrayListExtra(HotspotActivity.EXTRA_REQUEST_SHARE_FILES, paths)
                        putExtra(HotspotActivity.EXTRA_AUTO_START, true)
                    }
                    startActivity(hotspotIntent)
                }
            }
        }
    }

    private fun shareApkExternal(item: AppItem) {
        extractSingleApk(item, showSuccessToast = false) { destFile ->
            if (destFile != null && destFile.exists()) {
                val uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    destFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share APK"))
            }
        }
    }

    data class AppItem(
        val appInfo: ApplicationInfo,
        val name: String,
        val packageName: String,
        val versionName: String,
        val size: Long,
        val sourceDir: String,
        val isSystemApp: Boolean,
        var isSelected: Boolean = false
    )

    inner class AppAdapter(private val list: List<AppItem>) :
        RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

        inner class AppViewHolder(val binding: ItemApkAppBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val b = ItemApkAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return AppViewHolder(b)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = list[position]
            val b = holder.binding

            b.tvAppName.text = item.name
            b.tvAppPackage.text = item.packageName
            b.tvAppVersion.text = "v${item.versionName}"
            b.tvAppSize.text = item.size.toSizeString()

            Glide.with(this@ApkExtractorActivity)
                .load(item.appInfo)
                .placeholder(R.drawable.ic_apk)
                .into(b.ivAppIcon)

            b.cbSelectApp.setOnCheckedChangeListener(null)
            b.cbSelectApp.isChecked = item.isSelected
            b.cbSelectApp.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                updateBatchActionBar()
            }

            b.btnExtractApk.setOnClickListener {
                extractSingleApk(item)
            }

            b.btnSendBarcode.setOnClickListener {
                sendViaBarcode(item)
            }

            b.ivMenuApp.setOnClickListener { v ->
                val popup = PopupMenu(this@ApkExtractorActivity, v)
                popup.menu.add(0, 1, 0, "Share via Other Apps")
                popup.menu.add(0, 2, 1, "Open App")
                popup.menu.add(0, 3, 2, "App Details in Settings")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> shareApkExternal(item)
                        2 -> {
                            val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                            } else {
                                Toast.makeText(this@ApkExtractorActivity, "Cannot launch app", Toast.LENGTH_SHORT).show()
                            }
                        }
                        3 -> {
                            val i = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", item.packageName, null)
                            }
                            startActivity(i)
                        }
                    }
                    true
                }
                popup.show()
            }

            b.root.setOnClickListener {
                b.cbSelectApp.isChecked = !b.cbSelectApp.isChecked
            }
        }
    }
}
