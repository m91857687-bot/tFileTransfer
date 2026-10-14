package com.tans.tfiletransporter.ui.filemanager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ActivityFileManagerBinding
import com.tans.tfiletransporter.databinding.FragmentFileManagerPageBinding
import com.tans.tfiletransporter.databinding.ItemFileManagerGenericBinding
import com.tans.tfiletransporter.file.fileDateText
import com.tans.tfiletransporter.toSizeString
import com.tans.tfiletransporter.ui.connection.HotspotActivity
import com.tans.tfiletransporter.utils.dp2px
import com.tans.tfiletransporter.utils.openFile
import com.tans.tuiutils.mediastore.queryAudioFromMediaStore
import com.tans.tuiutils.mediastore.queryImageFromMediaStore
import com.tans.tuiutils.mediastore.queryVideoFromMediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileManagerBinding

    // Currently selected files (path -> File)
    val selectedFiles = mutableMapOf<String, File>()

    // Current search filter
    var searchQuery: String = ""
        private set

    // Current tab index (0: Apps, 1: Images, 2: Videos, 3: Audios, 4: Docs, 5: Storage)
    var currentTabPosition: Int = 0
        private set

    // Storage path tracking
    var currentStorageDir: File = Environment.getExternalStorageDirectory()

    private val pageAdapters = mutableMapOf<Int, FileManagerAdapter>()
    private val pageBindings = mutableMapOf<Int, FragmentFileManagerPageBinding>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val initialTab = intent.getIntExtra(EXTRA_INITIAL_TAB, 0)
        currentTabPosition = initialTab

        initToolbar()
        initStorageStats()
        initSearchBar()
        initTabsAndViewPager()
        initBottomActionBar()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabPosition == TAB_STORAGE && currentStorageDir != Environment.getExternalStorageDirectory()) {
                    currentStorageDir.parentFile?.let { parent ->
                        currentStorageDir = parent
                        refreshTab(TAB_STORAGE)
                        return
                    }
                }
                if (selectedFiles.isNotEmpty()) {
                    clearAllSelection()
                    return
                }
                finish()
            }
        })
    }

    private fun initToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.btnSearch.setOnClickListener {
            if (binding.searchBarLayout.visibility == View.VISIBLE) {
                binding.searchBarLayout.visibility = View.GONE
                binding.searchEt.setText("")
                searchQuery = ""
                notifyDataFiltered()
            } else {
                binding.searchBarLayout.visibility = View.VISIBLE
                binding.searchEt.requestFocus()
            }
        }

        binding.btnCreateFolder.setOnClickListener {
            showCreateFolderDialog()
        }
    }

    private fun initStorageStats() {
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            val percent = if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt() else 0
            binding.storageProgress.progress = percent
            binding.storageStatsTv.text = getString(
                R.string.storage_stats,
                usedBytes.toSizeString(),
                totalBytes.toSizeString()
            )
        } catch (e: Exception) {
            binding.storageCard.visibility = View.GONE
        }
    }

    private fun initSearchBar() {
        binding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                notifyDataFiltered()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.searchEt.setText("")
            searchQuery = ""
            notifyDataFiltered()
        }
    }

    private fun initTabsAndViewPager() {
        val tabTitles = listOf(
            R.string.tab_apps,
            R.string.tab_images,
            R.string.tab_videos,
            R.string.tab_audios,
            R.string.tab_documents,
            R.string.tab_storage
        )
        val tabIcons = listOf(
            R.drawable.ic_apps,
            R.drawable.ic_image,
            R.drawable.ic_movie,
            R.drawable.ic_audio,
            R.drawable.ic_document,
            R.drawable.ic_storage
        )

        binding.viewPager.adapter = object : RecyclerView.Adapter<PageViewHolder>() {
            override fun getItemCount(): Int = 6

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
                val pageBinding = FragmentFileManagerPageBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                return PageViewHolder(pageBinding)
            }

            override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
                val pageBinding = holder.binding
                pageBindings[position] = pageBinding
                val adapter = FileManagerAdapter(this@FileManagerActivity, position)
                pageAdapters[position] = adapter
                pageBinding.itemsRv.layoutManager = LinearLayoutManager(this@FileManagerActivity)
                pageBinding.itemsRv.adapter = adapter

                pageBinding.swipeRefresh.setColorSchemeResources(R.color.teal_200)
                pageBinding.swipeRefresh.setOnRefreshListener {
                    loadTabData(position)
                }

                pageBinding.btnUpDir.setOnClickListener {
                    currentStorageDir.parentFile?.let { parent ->
                        currentStorageDir = parent
                        loadTabData(TAB_STORAGE)
                    }
                }

                loadTabData(position)
            }
        }

        binding.viewPager.offscreenPageLimit = 6

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.setText(tabTitles[position])
            tab.setIcon(tabIcons[position])
        }.attach()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val pos = tab?.position ?: 0
                currentTabPosition = pos
                binding.btnCreateFolder.visibility = if (pos == TAB_STORAGE) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                loadTabData(tab?.position ?: 0)
            }
        })

        binding.viewPager.setCurrentItem(currentTabPosition, false)
        binding.btnCreateFolder.visibility = if (currentTabPosition == TAB_STORAGE) View.VISIBLE else View.GONE
    }

    private fun initBottomActionBar() {
        binding.btnSendSelected.setOnClickListener {
            if (selectedFiles.isEmpty()) return@setOnClickListener
            val paths = ArrayList(selectedFiles.keys)
            val intent = Intent(this, HotspotActivity::class.java).apply {
                putStringArrayListExtra(HotspotActivity.EXTRA_REQUEST_SHARE_FILES, paths)
                putExtra(HotspotActivity.EXTRA_AUTO_START, true)
            }
            startActivity(intent)
        }

        binding.btnSelectAll.setOnClickListener {
            val adapter = pageAdapters[currentTabPosition] ?: return@setOnClickListener
            for (item in adapter.filteredItems) {
                if (!item.isDirectory) {
                    selectedFiles[item.file.canonicalPath] = item.file
                }
            }
            updateBottomBar()
            adapter.notifyDataSetChanged()
        }

        binding.btnDeselectAll.setOnClickListener {
            clearAllSelection()
        }

        binding.btnDeleteSelected.setOnClickListener {
            if (selectedFiles.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_msg, selectedFiles.size))
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    val filesToDelete = selectedFiles.values.toList()
                    lifecycleScope.launch(Dispatchers.IO) {
                        for (f in filesToDelete) {
                            runCatching { f.delete() }
                        }
                        withContext(Dispatchers.Main) {
                            clearAllSelection()
                            loadTabData(currentTabPosition)
                            initStorageStats()
                            Toast.makeText(this@FileManagerActivity, "Items deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(R.string.dialog_negative, null)
                .show()
        }

        binding.btnShareSelected.setOnClickListener {
            if (selectedFiles.isEmpty()) return@setOnClickListener
            val uris = selectedFiles.values.mapNotNull { f ->
                runCatching {
                    FileProvider.getUriForFile(this, "${packageName}.provider", f)
                }.getOrNull()
            }
            if (uris.isNotEmpty()) {
                val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
            }
        }
    }

    fun onFileSelectionChanged() {
        updateBottomBar()
    }

    private fun updateBottomBar() {
        val count = selectedFiles.size
        if (count > 0) {
            val totalSize = selectedFiles.values.sumOf { it.length() }
            binding.tvSelectedCount.text = getString(
                R.string.request_share_files,
                count
            ) + " (${totalSize.toSizeString()})"
            binding.btnSendSelected.text = getString(R.string.send_selected, count)
            binding.bottomActionBar.visibility = View.VISIBLE
        } else {
            binding.bottomActionBar.visibility = View.GONE
        }
    }

    private fun clearAllSelection() {
        selectedFiles.clear()
        updateBottomBar()
        for (adapter in pageAdapters.values) {
            adapter.notifyDataSetChanged()
        }
    }

    fun loadTabData(position: Int) {
        val pageBinding = pageBindings[position] ?: return
        val adapter = pageAdapters[position] ?: return

        pageBinding.loadingProgress.visibility = View.VISIBLE
        pageBinding.emptyLayout.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val items = when (position) {
                TAB_APPS -> loadInstalledApps()
                TAB_IMAGES -> loadImages()
                TAB_VIDEOS -> loadVideos()
                TAB_AUDIOS -> loadAudios()
                TAB_DOCUMENTS -> loadDocuments()
                TAB_STORAGE -> loadStorageDirectory(currentStorageDir)
                else -> emptyList()
            }

            withContext(Dispatchers.Main) {
                pageBinding.loadingProgress.visibility = View.GONE
                pageBinding.swipeRefresh.isRefreshing = false

                if (position == TAB_STORAGE) {
                    pageBinding.breadcrumbLayout.visibility = View.VISIBLE
                    pageBinding.tvCurrentPath.text = currentStorageDir.path
                    pageBinding.btnUpDir.visibility =
                        if (currentStorageDir != Environment.getExternalStorageDirectory()) View.VISIBLE else View.GONE
                } else {
                    pageBinding.breadcrumbLayout.visibility = View.GONE
                }

                adapter.setOriginalItems(items)
                pageBinding.emptyLayout.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
            }
        }
    }

    fun refreshTab(position: Int) {
        loadTabData(position)
    }

    private fun notifyDataFiltered() {
        val adapter = pageAdapters[currentTabPosition] ?: return
        adapter.filter(searchQuery)
        val pageBinding = pageBindings[currentTabPosition]
        pageBinding?.emptyLayout?.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private fun loadInstalledApps(): List<FileManagerItem> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        return apps.filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM == 0) || it.sourceDir.startsWith("/data/app")
        }.mapNotNull { appInfo ->
            val file = File(appInfo.sourceDir)
            if (file.exists() && file.canRead()) {
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                val size = file.length()
                val date = file.lastModified()
                FileManagerItem(
                    name = label,
                    subtitle = "${appInfo.packageName} • ${size.toSizeString()}",
                    size = size,
                    lastModified = date,
                    file = file,
                    iconDrawable = icon,
                    isApp = true,
                    packageName = appInfo.packageName
                )
            } else {
                null
            }
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun loadImages(): List<FileManagerItem> {
        val mediaImages = this.queryImageFromMediaStore()
        return mediaImages.mapNotNull { img ->
            val f = img.file
            if (f != null && f.exists()) {
                FileManagerItem(
                    name = f.name,
                    subtitle = "${f.length().toSizeString()} • ${f.lastModified().fileDateText()}",
                    size = f.length(),
                    lastModified = f.lastModified(),
                    file = f,
                    uri = img.uri
                )
            } else null
        }.sortedByDescending { it.lastModified }
    }

    private fun loadVideos(): List<FileManagerItem> {
        val mediaVideos = this.queryVideoFromMediaStore()
        return mediaVideos.mapNotNull { vid ->
            val f = vid.file
            if (f != null && f.exists()) {
                FileManagerItem(
                    name = f.name,
                    subtitle = "${f.length().toSizeString()} • ${f.lastModified().fileDateText()}",
                    size = f.length(),
                    lastModified = f.lastModified(),
                    file = f,
                    uri = vid.uri
                )
            } else null
        }.sortedByDescending { it.lastModified }
    }

    private fun loadAudios(): List<FileManagerItem> {
        val mediaAudios = this.queryAudioFromMediaStore()
        return mediaAudios.mapNotNull { aud ->
            val f = aud.file
            if (f != null && f.exists()) {
                val artist = aud.artist.ifBlank { "Audio" }
                FileManagerItem(
                    name = f.name,
                    subtitle = "$artist • ${f.length().toSizeString()}",
                    size = f.length(),
                    lastModified = f.lastModified(),
                    file = f,
                    uri = aud.uri
                )
            } else null
        }.sortedByDescending { it.lastModified }
    }

    private fun loadDocuments(): List<FileManagerItem> {
        val docExtensions = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "zip", "rar", "7z", "apk", "epub", "csv"
        )
        val result = mutableListOf<FileManagerItem>()
        fun scanDir(dir: File, depth: Int) {
            if (depth > 4) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory && !child.name.startsWith(".")) {
                    scanDir(child, depth + 1)
                } else if (child.isFile) {
                    val ext = child.extension.lowercase(Locale.ROOT)
                    if (docExtensions.contains(ext)) {
                        result.add(
                            FileManagerItem(
                                name = child.name,
                                subtitle = "${child.length().toSizeString()} • ${child.lastModified().fileDateText()}",
                                size = child.length(),
                                lastModified = child.lastModified(),
                                file = child
                            )
                        )
                    }
                }
            }
        }
        val root = Environment.getExternalStorageDirectory()
        scanDir(root, 0)
        return result.sortedByDescending { it.lastModified }
    }

    private fun loadStorageDirectory(dir: File): List<FileManagerItem> {
        val files = dir.listFiles() ?: return emptyList()
        val dirsList = mutableListOf<FileManagerItem>()
        val filesList = mutableListOf<FileManagerItem>()

        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                val count = f.listFiles()?.size ?: 0
                dirsList.add(
                    FileManagerItem(
                        name = f.name,
                        subtitle = "$count items • ${f.lastModified().fileDateText()}",
                        size = 0L,
                        lastModified = f.lastModified(),
                        file = f,
                        isDirectory = true
                    )
                )
            } else {
                filesList.add(
                    FileManagerItem(
                        name = f.name,
                        subtitle = "${f.length().toSizeString()} • ${f.lastModified().fileDateText()}",
                        size = f.length(),
                        lastModified = f.lastModified(),
                        file = f,
                        isDirectory = false
                    )
                )
            }
        }
        dirsList.sortBy { it.name.lowercase(Locale.ROOT) }
        filesList.sortBy { it.name.lowercase(Locale.ROOT) }
        return dirsList + filesList
    }

    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.create_folder_hint)
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.create_folder)
            .setView(input)
            .setPositiveButton(R.string.dialog_positive) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newDir = File(currentStorageDir, name)
                    if (!newDir.exists()) {
                        newDir.mkdirs()
                        loadTabData(TAB_STORAGE)
                    } else {
                        Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }

    fun showItemMenu(item: FileManagerItem, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, R.string.action_open)
        if (!item.isDirectory) {
            popup.menu.add(0, 2, 1, R.string.action_send)
            popup.menu.add(0, 3, 2, R.string.action_share)
        }
        if (item.isApp) {
            popup.menu.add(0, 7, 3, getString(R.string.apk_extract_btn))
        }
        popup.menu.add(0, 4, 4, R.string.action_details)
        if (!item.isApp) {
            popup.menu.add(0, 5, 5, R.string.action_rename)
            popup.menu.add(0, 6, 6, R.string.action_delete)
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    if (item.isApp && item.packageName != null) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                        if (launchIntent != null) startActivity(launchIntent)
                        else openFile(item.file)
                    } else if (item.isDirectory) {
                        currentStorageDir = item.file
                        loadTabData(TAB_STORAGE)
                    } else {
                        openFile(item.file)
                    }
                    true
                }
                2 -> {
                    val intent = Intent(this, HotspotActivity::class.java).apply {
                        putStringArrayListExtra(
                            HotspotActivity.EXTRA_REQUEST_SHARE_FILES,
                            arrayListOf(item.file.canonicalPath)
                        )
                        putExtra(HotspotActivity.EXTRA_AUTO_START, true)
                    }
                    startActivity(intent)
                    true
                }
                3 -> {
                    val uri = FileProvider.getUriForFile(this, "${packageName}.provider", item.file)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(share, getString(R.string.action_share)))
                    true
                }
                7 -> {
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val outDir = File(downloads, "ShareLoad/Extracted_APKs")
                    if (!outDir.exists()) outDir.mkdirs()
                    val cleanName = item.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val destFile = File(outDir, "${cleanName}.apk")
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            item.file.copyTo(destFile, overwrite = true)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@FileManagerActivity, getString(R.string.apk_extracted_success, destFile.name), Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@FileManagerActivity, getString(R.string.apk_extract_failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    true
                }
                4 -> {
                    showDetailsDialog(item)
                    true
                }
                5 -> {
                    showRenameDialog(item)
                    true
                }
                6 -> {
                    showDeleteSingleDialog(item)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDetailsDialog(item: FileManagerItem) {
        val msg = """
            ${getString(R.string.file_details_path, item.file.path)}
            ${getString(R.string.file_details_size, item.file.length().toSizeString())}
            ${getString(R.string.file_details_modified, item.file.lastModified().fileDateText())}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage(msg)
            .setPositiveButton(R.string.dialog_positive, null)
            .show()
    }

    private fun showRenameDialog(item: FileManagerItem) {
        val input = EditText(this).apply {
            setText(item.file.name)
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.dialog_positive) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.file.name) {
                    val dest = File(item.file.parentFile, newName)
                    if (item.file.renameTo(dest)) {
                        loadTabData(currentTabPosition)
                    } else {
                        Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }

    private fun showDeleteSingleDialog(item: FileManagerItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_msg, 1))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                item.file.delete()
                selectedFiles.remove(item.file.canonicalPath)
                updateBottomBar()
                loadTabData(currentTabPosition)
                initStorageStats()
            }
            .setNegativeButton(R.string.dialog_negative, null)
            .show()
    }

    class PageViewHolder(val binding: FragmentFileManagerPageBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        const val TAB_APPS = 0
        const val TAB_IMAGES = 1
        const val TAB_VIDEOS = 2
        const val TAB_AUDIOS = 3
        const val TAB_DOCUMENTS = 4
        const val TAB_STORAGE = 5
    }
}

data class FileManagerItem(
    val name: String,
    val subtitle: String,
    val size: Long,
    val lastModified: Long,
    val file: File,
    val isDirectory: Boolean = false,
    val isApp: Boolean = false,
    val packageName: String? = null,
    val iconDrawable: Drawable? = null,
    val uri: Uri? = null
)

class FileManagerAdapter(
    private val activity: FileManagerActivity,
    private val tabPosition: Int
) : RecyclerView.Adapter<FileManagerAdapter.ViewHolder>() {

    private var originalItems = listOf<FileManagerItem>()
    var filteredItems = listOf<FileManagerItem>()
        private set

    fun setOriginalItems(items: List<FileManagerItem>) {
        originalItems = items
        filter(activity.searchQuery)
    }

    fun filter(query: String) {
        filteredItems = if (query.isBlank()) {
            originalItems
        } else {
            val q = query.lowercase(Locale.ROOT)
            originalItems.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                        it.subtitle.lowercase(Locale.ROOT).contains(q)
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = filteredItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemFileManagerGenericBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredItems[position]
        val b = holder.binding

        b.itemTitleTv.text = item.name
        b.itemSubtitleTv.text = item.subtitle

        if (item.isDirectory) {
            b.itemCb.visibility = View.GONE
            b.itemIconIv.setImageResource(R.drawable.folder_outline)
        } else {
            b.itemCb.visibility = View.VISIBLE
            val isSelected = activity.selectedFiles.containsKey(item.file.canonicalPath)
            b.itemCb.isChecked = isSelected

            b.itemCb.setOnClickListener {
                val path = item.file.canonicalPath
                if (b.itemCb.isChecked) {
                    activity.selectedFiles[path] = item.file
                } else {
                    activity.selectedFiles.remove(path)
                }
                activity.onFileSelectionChanged()
            }

            // Set Icon
            when {
                item.iconDrawable != null -> {
                    b.itemIconIv.setImageDrawable(item.iconDrawable)
                }
                item.uri != null -> {
                    Glide.with(holder.itemView.context)
                        .load(item.uri)
                        .placeholder(if (tabPosition == FileManagerActivity.TAB_VIDEOS) R.drawable.ic_movie else R.drawable.ic_image)
                        .into(b.itemIconIv)
                }
                tabPosition == FileManagerActivity.TAB_IMAGES -> {
                    Glide.with(holder.itemView.context)
                        .load(item.file)
                        .placeholder(R.drawable.ic_image)
                        .into(b.itemIconIv)
                }
                tabPosition == FileManagerActivity.TAB_VIDEOS -> {
                    Glide.with(holder.itemView.context)
                        .load(item.file)
                        .placeholder(R.drawable.ic_movie)
                        .into(b.itemIconIv)
                }
                tabPosition == FileManagerActivity.TAB_AUDIOS -> {
                    b.itemIconIv.setImageResource(R.drawable.ic_audio)
                }
                tabPosition == FileManagerActivity.TAB_DOCUMENTS -> {
                    b.itemIconIv.setImageResource(R.drawable.ic_document)
                }
                else -> {
                    b.itemIconIv.setImageResource(R.drawable.file_outline)
                }
            }
        }

        b.root.setOnClickListener {
            if (item.isDirectory) {
                activity.currentStorageDir = item.file
                activity.loadTabData(FileManagerActivity.TAB_STORAGE)
            } else if (activity.selectedFiles.isNotEmpty()) {
                val path = item.file.canonicalPath
                if (activity.selectedFiles.containsKey(path)) {
                    activity.selectedFiles.remove(path)
                    b.itemCb.isChecked = false
                } else {
                    activity.selectedFiles[path] = item.file
                    b.itemCb.isChecked = true
                }
                activity.onFileSelectionChanged()
            } else {
                if (item.isApp && item.packageName != null) {
                    val launchIntent = holder.itemView.context.packageManager.getLaunchIntentForPackage(item.packageName)
                    if (launchIntent != null) {
                        holder.itemView.context.startActivity(launchIntent)
                    } else {
                        holder.itemView.context.openFile(item.file)
                    }
                } else {
                    holder.itemView.context.openFile(item.file)
                }
            }
        }

        b.root.setOnLongClickListener {
            if (!item.isDirectory) {
                val path = item.file.canonicalPath
                if (activity.selectedFiles.containsKey(path)) {
                    activity.selectedFiles.remove(path)
                    b.itemCb.isChecked = false
                } else {
                    activity.selectedFiles[path] = item.file
                    b.itemCb.isChecked = true
                }
                activity.onFileSelectionChanged()
            } else {
                activity.showItemMenu(item, b.itemMenuBtn)
            }
            true
        }

        b.itemMenuBtn.setOnClickListener {
            activity.showItemMenu(item, it)
        }
    }

    class ViewHolder(val binding: ItemFileManagerGenericBinding) : RecyclerView.ViewHolder(binding.root)
}
